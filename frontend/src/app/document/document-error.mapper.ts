import { HttpErrorResponse } from '@angular/common/http';
import { DocumentContentErrorCode, ProblemDetail } from './document.models';

export type DocumentApiErrorKind = 'validation' | 'not-found' | 'operational';

export interface DocumentApiError {
  kind: DocumentApiErrorKind;
  status?: number;
  errorCode?: string;
  invalidField?: string;
  message: string;
  canRetryManually: boolean;
}

const validationMessages: Record<DocumentContentErrorCode, string> = {
  DOCUMENT_CONTENT_MISSING: 'Nenhum arquivo foi enviado.',
  DOCUMENT_CONTENT_EMPTY: 'O arquivo enviado está vazio.',
  DOCUMENT_FILENAME_MISSING: 'O arquivo enviado não possui nome.',
  DOCUMENT_CONTENT_TYPE_MISSING: 'O arquivo enviado não possui tipo informado.',
  DOCUMENT_CONTENT_TYPE_MISMATCH: 'O tipo informado não corresponde ao conteúdo do arquivo.',
  DOCUMENT_CONTENT_TYPE_UNKNOWN: 'Não foi possível identificar o tipo do arquivo.',
  DOCUMENT_CONTENT_TOO_LARGE: 'O arquivo excede o limite de 50 MiB.',
  DOCUMENT_METADATA_NOT_ALLOWED: 'A requisição deve conter somente o arquivo.',
  DOCUMENT_CONTENT_READ_FAILED: 'Não foi possível ler o conteúdo do arquivo.',
};

export function mapDocumentError(error: unknown): DocumentApiError {
  if (!(error instanceof HttpErrorResponse)) {
    return operationalError();
  }

  if (error.status === 404) {
    return {
      kind: 'not-found',
      status: error.status,
      message: 'Documento não encontrado.',
      canRetryManually: false,
    };
  }

  const problemDetail = toProblemDetail(error.error);
  const errorCode = problemDetail?.errorCode;

  if (isDocumentContentErrorCode(errorCode)) {
    return {
      kind: 'validation',
      status: error.status,
      errorCode,
      invalidField: problemDetail?.invalidField,
      message: validationMessages[errorCode],
      canRetryManually: false,
    };
  }

  if (error.status === 0) {
    return operationalError('Não foi possível conectar ao backend.', error.status);
  }

  if (error.status === 408 || error.status === 504) {
    return operationalError('A solicitação excedeu o tempo limite.', error.status);
  }

  return operationalError('Não foi possível concluir a operação.', error.status);
}

function isDocumentContentErrorCode(value: unknown): value is DocumentContentErrorCode {
  return typeof value === 'string' && value in validationMessages;
}

function toProblemDetail(value: unknown): ProblemDetail | undefined {
  if (typeof value === 'string') {
    try {
      return toProblemDetail(JSON.parse(value));
    } catch {
      return undefined;
    }
  }

  if (value !== null && typeof value === 'object') {
    return value as ProblemDetail;
  }

  return undefined;
}

function operationalError(
  message = 'Ocorreu uma falha operacional.',
  status?: number,
): DocumentApiError {
  return {
    kind: 'operational',
    status,
    message,
    canRetryManually: true,
  };
}
