package com.dockflow.dockflow.document.storage.minio;

import com.dockflow.dockflow.document.storage.DocumentStorage;
import com.dockflow.dockflow.document.storage.DocumentStorageException;
import com.dockflow.dockflow.document.storage.DocumentStorageException.FailureType;
import io.minio.ErrorResponseException;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
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

public class MinioDocumentStorage {

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
            ErrorResponse response = exception.errorResponse();
            if (response.statusCode() == 404) {
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
        int statusCode = exception.errorResponse().statusCode();
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
}
