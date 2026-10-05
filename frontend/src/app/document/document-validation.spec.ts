import { HttpErrorResponse } from '@angular/common/http';
import { DocumentFileValidator, MAX_DOCUMENT_SIZE_BYTES } from './document-file-validator';
import { documentListMessage, mapDocumentError } from './document-error.mapper';
import { DocumentContentErrorCode, DocumentResponse } from './document.models';

describe('DocumentFileValidator', () => {
  const validator = new DocumentFileValidator();

  it('accepts a non-empty file with name, type and size at the limit', () => {
    const file = createFile('document.pdf', 'application/pdf', MAX_DOCUMENT_SIZE_BYTES);

    expect(validator.validate(file)).toEqual({ valid: true, file });
  });

  it.each([
    ['missing file', null, 'DOCUMENT_CONTENT_MISSING'],
    ['empty file', createFile('empty.pdf', 'application/pdf', 0), 'DOCUMENT_CONTENT_EMPTY'],
    ['missing filename', createFile('', 'application/pdf', 1), 'DOCUMENT_FILENAME_MISSING'],
    ['missing content type', createFile('document.pdf', '', 1), 'DOCUMENT_CONTENT_TYPE_MISSING'],
    [
      'file above the limit',
      createFile('large.pdf', 'application/pdf', MAX_DOCUMENT_SIZE_BYTES + 1),
      'DOCUMENT_CONTENT_TOO_LARGE',
    ],
  ])('rejects %s locally', (_description, file, errorCode) => {
    const result = validator.validate(file);

    expect(result.valid).toBe(false);
    if (!result.valid) {
      expect(result.error.errorCode).toBe(errorCode);
      expect(result.error.invalidField).toBe('file');
      expect(result.error.message).toBeTruthy();
    }
  });
});

describe('mapDocumentError', () => {
  const validationCodes: DocumentContentErrorCode[] = [
    'DOCUMENT_CONTENT_MISSING',
    'DOCUMENT_CONTENT_EMPTY',
    'DOCUMENT_FILENAME_MISSING',
    'DOCUMENT_CONTENT_TYPE_MISSING',
    'DOCUMENT_CONTENT_TYPE_MISMATCH',
    'DOCUMENT_CONTENT_TYPE_UNKNOWN',
    'DOCUMENT_CONTENT_TOO_LARGE',
    'DOCUMENT_METADATA_NOT_ALLOWED',
    'DOCUMENT_CONTENT_READ_FAILED',
  ];

  it.each(validationCodes)('maps %s to a stable validation message', (errorCode) => {
    const error = new HttpErrorResponse({
      error: { errorCode, invalidField: 'file' },
      status: 400,
      statusText: 'Bad Request',
    });

    expect(mapDocumentError(error)).toMatchObject({
      kind: 'validation',
      errorCode,
      invalidField: 'file',
      canRetryManually: false,
    });
    expect(mapDocumentError(error).message).not.toBe('Ocorreu uma falha operacional.');
  });

  it('uses the operational fallback for an unknown response code', () => {
    const error = new HttpErrorResponse({
      error: { errorCode: 'DOCUMENT_NEW_CODE' },
      status: 400,
      statusText: 'Bad Request',
    });

    expect(mapDocumentError(error)).toMatchObject({
      kind: 'operational',
      message: 'Não foi possível concluir a operação.',
      canRetryManually: true,
    });
  });

  it('keeps a 404 distinct from a pending document', () => {
    const result = mapDocumentError(
      new HttpErrorResponse({ status: 404, statusText: 'Not Found' }),
    );

    expect(result).toMatchObject({
      kind: 'not-found',
      message: 'Documento não encontrado.',
      canRetryManually: false,
    });
    expect(result.message).not.toContain('PENDING');
  });

  it.each([
    [0, 'Não foi possível conectar ao backend.'],
    [408, 'A solicitação excedeu o tempo limite.'],
    [504, 'A solicitação excedeu o tempo limite.'],
    [500, 'Não foi possível concluir a operação.'],
  ])('maps operational HTTP status %s', (status, message) => {
    expect(mapDocumentError(new HttpErrorResponse({ status, statusText: 'Error' }))).toMatchObject({
      kind: 'operational',
      status,
      message,
      canRetryManually: true,
    });
  });

  it('maps every list failure to the approved list message', () => {
    expect(mapDocumentError(new Error('backend failed'), 'list')).toMatchObject({
      kind: 'operational',
      message: 'Não foi possível exibir itens listados',
      canRetryManually: true,
    });
    expect(mapDocumentError(new HttpErrorResponse({ status: 500 }), 'list').message).toBe(
      'Não foi possível exibir itens listados',
    );
  });

  it('produces the approved empty-list message only for an empty collection', () => {
    const document: DocumentResponse = {
      id: 'doc-123',
      originalFilename: 'document.pdf',
      contentType: 'application/pdf',
      sizeBytes: 42,
      status: 'COMPLETED',
      createdAt: '2026-09-19T20:00:00Z',
      updatedAt: '2026-09-19T20:00:00Z',
    };

    expect(documentListMessage([])).toBe('Nenhum documento encontrado');
    expect(documentListMessage([document])).toBeNull();
  });
});

function createFile(name: string, type: string, size: number): File {
  const file = new File(['content'], name, { type });
  Object.defineProperty(file, 'size', { value: size });
  return file;
}
