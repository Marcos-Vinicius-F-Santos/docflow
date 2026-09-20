import {
  DocumentContentErrorCode,
  DocumentFileValidationError,
  DocumentFileValidationResult,
} from './document.models';

export const MAX_DOCUMENT_SIZE_BYTES = 52_428_800;

const localValidationMessages: Record<
  Exclude<
    DocumentContentErrorCode,
    | 'DOCUMENT_CONTENT_TYPE_MISMATCH'
    | 'DOCUMENT_CONTENT_TYPE_UNKNOWN'
    | 'DOCUMENT_METADATA_NOT_ALLOWED'
    | 'DOCUMENT_CONTENT_READ_FAILED'
  >,
  string
> = {
  DOCUMENT_CONTENT_MISSING: 'Selecione um arquivo.',
  DOCUMENT_CONTENT_EMPTY: 'O arquivo está vazio.',
  DOCUMENT_FILENAME_MISSING: 'O arquivo precisa ter um nome.',
  DOCUMENT_CONTENT_TYPE_MISSING: 'O arquivo precisa informar um tipo.',
  DOCUMENT_CONTENT_TOO_LARGE: 'O arquivo excede o limite de 50 MiB.',
};

export class DocumentFileValidator {
  validate(file: File | null | undefined): DocumentFileValidationResult {
    if (!file) {
      return this.invalid('DOCUMENT_CONTENT_MISSING');
    }

    if (file.size === 0) {
      return this.invalid('DOCUMENT_CONTENT_EMPTY');
    }

    if (!file.name.trim()) {
      return this.invalid('DOCUMENT_FILENAME_MISSING');
    }

    if (!file.type.trim()) {
      return this.invalid('DOCUMENT_CONTENT_TYPE_MISSING');
    }

    if (file.size > MAX_DOCUMENT_SIZE_BYTES) {
      return this.invalid('DOCUMENT_CONTENT_TOO_LARGE');
    }

    return { valid: true, file };
  }

  private invalid(errorCode: keyof typeof localValidationMessages | 'DOCUMENT_CONTENT_MISSING'): {
    valid: false;
    error: DocumentFileValidationError;
  } {
    return {
      valid: false,
      error: {
        errorCode,
        invalidField: 'file',
        message: localValidationMessages[errorCode],
      },
    };
  }
}
