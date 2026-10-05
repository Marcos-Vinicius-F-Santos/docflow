package com.dockflow.dockflow.document.storage.minio;

import com.dockflow.dockflow.document.storage.DocumentStorage;
import com.dockflow.dockflow.document.storage.DocumentStorageException;
import com.dockflow.dockflow.document.storage.DocumentStorageException.FailureType;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;

public class MinioDocumentStorage implements DocumentStorage {

    private final MinioClient client;
    private final String bucket;

    public MinioDocumentStorage(MinioClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    public DocumentStorage.StoredObject store(
        String objectKey,
        InputStream content,
        long size,
        String contentType
    ) {
        try {
            client.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(content, size, -1)
                    .contentType(contentType)
                    .build()
            );

            return new DocumentStorage.StoredObject(objectKey, size, contentType);
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
    public DocumentStorage.StoredContent open(String objectKey) {
        try {
            StatObjectResponse metadata = client.statObject(
                StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build()
            );
            InputStream content = client.getObject(
                GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build()
            );
            return new DocumentStorage.StoredContent(
                content,
                metadata.size(),
                metadata.contentType()
            );
        } catch (ErrorResponseException exception) {
            throw translateRead(exception);
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
                "Object content could not be opened",
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

    public boolean exists(String objectKey) {
        try {
            client.statObject(
                StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build()
            );
            return true;
        } catch (ErrorResponseException exception) {
            if (exception.response().code() == 404) {
                return false;
            }
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

    public void delete(String objectKey) {
        try {
            client.removeObject(
                io.minio.RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
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

    private DocumentStorageException translateRead(ErrorResponseException exception) {
        if (exception.response().code() == 404) {
            return new DocumentStorageException(
                FailureType.NOT_FOUND,
                "Object content was not found",
                exception
            );
        }
        return translate(exception);
    }
}
