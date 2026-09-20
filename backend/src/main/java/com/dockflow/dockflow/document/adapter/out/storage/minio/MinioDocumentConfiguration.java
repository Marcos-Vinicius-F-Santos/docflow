package com.dockflow.dockflow.document.adapter.out.storage.minio;

import com.dockflow.dockflow.document.storage.DocumentStorage;
import com.dockflow.dockflow.document.storage.minio.MinioDocumentStorage;
import io.minio.MinioClient;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** Wires the existing MinIO client to the final-storage and staging ports. */
@Configuration(proxyBeanMethods = false)
public class MinioDocumentConfiguration {

    @Bean
    OkHttpClient minioHttpClient(
        @Value("${docflow.storage.minio.connect-timeout:PT3S}") Duration connectTimeout,
        @Value("${docflow.storage.minio.read-timeout:PT3S}") Duration readTimeout,
        @Value("${docflow.storage.minio.write-timeout:PT3S}") Duration writeTimeout,
        @Value("${docflow.processing.timeout:PT3S}") Duration processingTimeout
    ) {
        return new OkHttpClient.Builder()
            .connectTimeout(connectTimeout)
            .readTimeout(readTimeout)
            .writeTimeout(writeTimeout)
            .callTimeout(processingTimeout)
            .build();
    }

    @Bean
    MinioClient minioClient(
        @Value("${docflow.storage.minio.endpoint}") String endpoint,
        @Value("${docflow.storage.minio.access-key}") String accessKey,
        @Value("${docflow.storage.minio.secret-key}") String secretKey,
        OkHttpClient minioHttpClient
    ) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("docflow.storage.minio.endpoint must not be empty");
        }
        if (accessKey == null || accessKey.isBlank()) {
            throw new IllegalArgumentException("docflow.storage.minio.access-key must not be empty");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("docflow.storage.minio.secret-key must not be empty");
        }

        MinioClient.Builder builder = MinioClient.builder()
            .endpoint(endpoint)
            .httpClient(minioHttpClient);
        builder.credentials(accessKey, secretKey);
        return builder.build();
    }

    @Bean
    MinioDocumentStaging documentStaging(
        MinioClient client,
        @Value("${docflow.storage.staging-bucket:docflow-staging}") String bucket
    ) {
        return new MinioDocumentStaging(client, bucket);
    }

    @Bean
    DocumentStorage documentStorage(
        MinioClient client,
        @Value("${docflow.storage.bucket:docflow-documents}") String bucket
    ) {
        return new MinioDocumentStorage(client, bucket);
    }
}
