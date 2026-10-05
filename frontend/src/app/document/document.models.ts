export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface DocumentResponse {
  id: string;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  status: DocumentStatus;
  createdAt: string;
  updatedAt: string;
}

export type DocumentOperation = 'list' | 'download' | 'delete' | 'general';

export interface DocumentListState {
  documents: DocumentResponse[];
  errorMessage: string | null;
}

export const DOCUMENT_LIST_EMPTY_MESSAGE = 'Nenhum documento encontrado';
export const DOCUMENT_LIST_ERROR_MESSAGE = 'Não foi possível exibir itens listados';

export type DocumentContentErrorCode =
  | 'DOCUMENT_CONTENT_MISSING'
  | 'DOCUMENT_CONTENT_EMPTY'
  | 'DOCUMENT_FILENAME_MISSING'
  | 'DOCUMENT_CONTENT_TYPE_MISSING'
  | 'DOCUMENT_CONTENT_TYPE_MISMATCH'
  | 'DOCUMENT_CONTENT_TYPE_UNKNOWN'
  | 'DOCUMENT_CONTENT_TOO_LARGE'
  | 'DOCUMENT_METADATA_NOT_ALLOWED'
  | 'DOCUMENT_CONTENT_READ_FAILED';

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errorCode?: DocumentContentErrorCode | string;
  invalidField?: string;
  [property: string]: unknown;
}

export type LocalDocumentValidationCode =
  | 'DOCUMENT_CONTENT_MISSING'
  | 'DOCUMENT_CONTENT_EMPTY'
  | 'DOCUMENT_FILENAME_MISSING'
  | 'DOCUMENT_CONTENT_TYPE_MISSING'
  | 'DOCUMENT_CONTENT_TOO_LARGE';

export interface DocumentFileValidationError {
  errorCode: LocalDocumentValidationCode;
  invalidField: 'file';
  message: string;
}

export type DocumentFileValidationResult =
  | { valid: true; file: File }
  | { valid: false; error: DocumentFileValidationError };
