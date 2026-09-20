package com.dockflow.dockflow.document.adapter.out.storage.minio;

import com.dockflow.dockflow.document.application.DocumentContentValidator;
import com.dockflow.dockflow.document.adapter.in.web.exception.DocumentContentErrorCode;
import com.dockflow.dockflow.document.adapter.in.web.exception.DocumentContentValidationException;
import com.dockflow.dockflow.document.port.out.storage.DocumentStaging;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingReader;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingRemover;
import com.dockflow.dockflow.document.storage.DocumentStorageException;
import com.dockflow.dockflow.document.storage.DocumentStorageException.FailureType;
import io.minio.RemoveObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import org.apache.commons.io.input.CountingInputStream;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * MinIO adapter boundary for document staging.
 *
 * <p>This adapter keeps the existing MinIO client and the staging bucket
 * isolated from the final document-storage bucket.</p>
 */
public class MinioDocumentStaging implements DocumentStaging, DocumentStagingReader, DocumentStagingRemover {

    public static final String DEFAULT_BUCKET = "docflow-staging";
    private static final long UNKNOWN_OBJECT_SIZE = -1L;
    private static final long MINIO_PART_SIZE_BYTES = 10 * 1024 * 1024L;

    private final MinioClient client;
    private final String bucket;

    public MinioDocumentStaging(MinioClient client) {
        this(client, DEFAULT_BUCKET);
    }

    public MinioDocumentStaging(MinioClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    public MinioClient client() {
        return client;
    }

    public String bucket() {
        return bucket;
    }

    public String objectKey(UUID documentId) {
        return "staging/" + documentId;
    }

    @Override
    public StagedObject stage(UUID documentId, java.io.InputStream content, String contentType) {
        String objectKey = objectKey(documentId);

        try (CountingInputStream counted = new CountingInputStream(content);
             InputStream limited = new MaximumSizeInputStream(counted, DocumentContentValidator.MAX_SIZE_BYTES)) {
            try {
                client.putObject(
                    PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .stream(limited, UNKNOWN_OBJECT_SIZE, MINIO_PART_SIZE_BYTES)
                        .contentType(contentType)
                        .build()
                );
            } catch (DocumentContentValidationException exception) {
                deleteAfterRejectedUpload(objectKey);
                throw exception;
            }
            long sizeBytes = counted.getByteCount();
            if (sizeBytes > DocumentContentValidator.MAX_SIZE_BYTES) {
                deleteAfterRejectedUpload(objectKey);
                throw new DocumentContentValidationException(
                    DocumentContentErrorCode.DOCUMENT_CONTENT_TOO_LARGE,
                    "The file exceeds the maximum size of "
                        + DocumentContentValidator.MAX_SIZE_BYTES + " bytes"
                );
            }
            if (sizeBytes == 0) {
                deleteAfterRejectedUpload(objectKey);
                throw new DocumentContentValidationException(
                    DocumentContentErrorCode.DOCUMENT_CONTENT_EMPTY,
                    "The file content cannot be empty"
                );
            }

            return new StagedObject(objectKey, sizeBytes, contentType);
        } catch (DocumentContentValidationException exception) {
            throw exception;
        } catch (ErrorResponseException exception) {
            throw translate(exception);
        } catch (InsufficientDataException | InvalidResponseException | InternalException |
                 ServerException | XmlParserException exception) {
            throw new DocumentStorageException(
                FailureType.UNAVAILABLE,
                "Object storage is unavailable",
                exception
            );
        } catch (IOException exception) {
            throw new DocumentStorageException(
                FailureType.RESULT_UNKNOWN,
                "Object storage result could not be confirmed",
                exception
            );
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new DocumentStorageException(
                FailureType.REJECTED,
                "Object storage rejected the operation",
                exception
            );
        }
    }

    @Override
    public DocumentStagingReader.StagedContent open(String contentReference) {
        try {
            StatObjectResponse metadata = client.statObject(
                StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(contentReference)
                    .build()
            );
            java.io.InputStream content = client.getObject(
                GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(contentReference)
                    .build()
            );
            return new DocumentStagingReader.StagedContent(
                content,
                metadata.size(),
                metadata.contentType()
            );
        } catch (ErrorResponseException exception) {
            throw translate(exception);
        } catch (InsufficientDataException | InvalidResponseException | InternalException |
                 ServerException | XmlParserException exception) {
            throw new DocumentStorageException(
                FailureType.UNAVAILABLE,
                "Object storage is unavailable",
                exception
            );
        } catch (IOException exception) {
            throw new DocumentStorageException(
                FailureType.RESULT_UNKNOWN,
                "Staged content could not be opened",
                exception
            );
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new DocumentStorageException(
                FailureType.REJECTED,
                "Object storage rejected the operation",
                exception
            );
        }
    }

    @Override
    public void delete(String contentReference) {
        try {
            client.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(contentReference)
                    .build()
            );
        } catch (ErrorResponseException exception) {
            throw translate(exception);
        } catch (InsufficientDataException | InvalidResponseException | InternalException |
                 ServerException | XmlParserException exception) {
            throw new DocumentStorageException(
                FailureType.UNAVAILABLE,
                "Object storage is unavailable",
                exception
            );
        } catch (IOException exception) {
            throw new DocumentStorageException(
                FailureType.RESULT_UNKNOWN,
                "Staged content could not be removed",
                exception
            );
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new DocumentStorageException(
                FailureType.REJECTED,
                "Object storage rejected the operation",
                exception
            );
        }
    }

    private void deleteAfterRejectedUpload(String objectKey) {
        try {
            client.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build()
            );
        } catch (Exception exception) {
            throw new DocumentStorageException(
                FailureType.RESULT_UNKNOWN,
                "Rejected staging object could not be removed",
                exception
            );
        }
    }

    private DocumentStorageException translate(ErrorResponseException exception) {
        int statusCode = exception.response().code();
        FailureType type = statusCode >= 500 || statusCode == 429
            ? FailureType.UNAVAILABLE
            : FailureType.REJECTED;

        return new DocumentStorageException(
            type,
            type == FailureType.UNAVAILABLE
                ? "Object storage is unavailable"
                : "Object storage rejected the operation",
            exception
        );
    }

    private static final class MaximumSizeInputStream extends FilterInputStream {

        private final CountingInputStream counted;
        private final long maximumSize;

        private MaximumSizeInputStream(CountingInputStream counted, long maximumSize) {
            super(counted);
            this.counted = counted;
            this.maximumSize = maximumSize;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            ensureWithinLimit();
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, length);
            ensureWithinLimit();
            return read;
        }

        private void ensureWithinLimit() {
            if (counted.getByteCount() > maximumSize) {
                throw new DocumentContentValidationException(
                    DocumentContentErrorCode.DOCUMENT_CONTENT_TOO_LARGE,
                    "The file exceeds the maximum size of " + maximumSize + " bytes"
                );
            }
        }
    }
}
