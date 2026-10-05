import { DocumentResponse } from './document.models';

interface DocumentPageContractFixture {
  content: DocumentResponse[];
  page: number;
  size: 10;
  totalElements: number;
  totalPages: number;
}

interface BatchDeleteResultContractFixture {
  documentId: string;
  status: 'DELETED' | 'FAILED' | 'NOT_FOUND';
  errorCode?: string;
  message?: string;
}

const documentPageContractFixture: DocumentPageContractFixture = {
  content: [
    {
      id: '00000000-0000-0000-0000-000000000001',
      originalFilename: 'Report.PDF',
      contentType: 'application/pdf',
      sizeBytes: 1024,
      status: 'COMPLETED',
      createdAt: '2026-09-20T09:00:00Z',
      updatedAt: '2026-09-20T09:05:00Z',
    },
    {
      id: '00000000-0000-0000-0000-000000000002',
      originalFilename: 'processing.txt',
      contentType: 'text/plain',
      sizeBytes: 2048,
      status: 'PROCESSING',
      createdAt: '2026-09-19T09:00:00Z',
      updatedAt: '2026-09-20T08:00:00Z',
    },
    {
      id: '00000000-0000-0000-0000-000000000003',
      originalFilename: 'pending.docx',
      contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      sizeBytes: 3072,
      status: 'PENDING',
      createdAt: '2026-09-18T09:00:00Z',
      updatedAt: '2026-09-18T09:00:00Z',
    },
    {
      id: '00000000-0000-0000-0000-000000000004',
      originalFilename: 'failed.csv',
      contentType: 'text/csv',
      sizeBytes: 4096,
      status: 'FAILED',
      createdAt: '2026-09-17T09:00:00Z',
      updatedAt: '2026-09-17T09:10:00Z',
    },
  ],
  page: 1,
  size: 10,
  totalElements: 4,
  totalPages: 1,
};

const batchDeleteResultContractFixture: BatchDeleteResultContractFixture[] = [
  {
    documentId: '00000000-0000-0000-0000-000000000001',
    status: 'DELETED',
  },
  {
    documentId: '00000000-0000-0000-0000-000000000002',
    status: 'FAILED',
    errorCode: 'DOCUMENT_STORAGE_UNAVAILABLE',
    message: 'Não foi possível excluir este documento.',
  },
  {
    documentId: '00000000-0000-0000-0000-000000000003',
    status: 'NOT_FOUND',
    errorCode: 'DOCUMENT_NOT_FOUND',
    message: 'Documento não encontrado.',
  },
];

const batchDownloadContractFixture = {
  selectedIds: documentPageContractFixture.content.map((document) => document.id),
  includedStatuses: ['COMPLETED'] as const,
  skippedStatuses: ['PENDING', 'PROCESSING', 'FAILED'] as const,
  contentType: 'application/zip',
  contentDisposition: 'attachment; filename="documents.zip"',
  cacheControl: 'no-store',
};

describe('document feature contract fixtures', () => {
  it('covers the seven document fields and all public statuses', () => {
    expect(documentPageContractFixture.size).toBe(10);
    expect(documentPageContractFixture.content).toHaveLength(4);
    expect(new Set(documentPageContractFixture.content.map((document) => document.status))).toEqual(
      new Set(['PENDING', 'PROCESSING', 'COMPLETED', 'FAILED']),
    );

    for (const document of documentPageContractFixture.content) {
      expect(Object.keys(document)).toEqual([
        'id',
        'originalFilename',
        'contentType',
        'sizeBytes',
        'status',
        'createdAt',
        'updatedAt',
      ]);
    }
  });

  it('captures the batch download ZIP contract and skipped statuses', () => {
    expect(batchDownloadContractFixture.includedStatuses).toEqual(['COMPLETED']);
    expect(batchDownloadContractFixture.skippedStatuses).toEqual([
      'PENDING',
      'PROCESSING',
      'FAILED',
    ]);
    expect(batchDownloadContractFixture.contentType).toBe('application/zip');
    expect(batchDownloadContractFixture.contentDisposition).toContain('documents.zip');
    expect(batchDownloadContractFixture.cacheControl).toBe('no-store');
  });

  it('captures total and partial batch deletion results', () => {
    expect(batchDeleteResultContractFixture.map((result) => result.status)).toEqual([
      'DELETED',
      'FAILED',
      'NOT_FOUND',
    ]);
    expect(batchDeleteResultContractFixture[1].errorCode).toBe('DOCUMENT_STORAGE_UNAVAILABLE');
    expect(batchDeleteResultContractFixture[2].errorCode).toBe('DOCUMENT_NOT_FOUND');
  });
});
