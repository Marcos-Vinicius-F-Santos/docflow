package com.dockflow.dockflow.document.observability;

import com.dockflow.dockflow.document.DocumentRepository;
import com.dockflow.dockflow.document.DocumentStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** Low-cardinality application metrics used by the Grafana Cloud dashboard. */
@Component
public class DocumentMetrics {

    private final MeterRegistry registry;

    public DocumentMetrics(MeterRegistry registry, DocumentRepository documentRepository) {
        this.registry = registry;
        Gauge.builder("docflow_documents_state", documentRepository,
                repository -> repository.countByStatus(DocumentStatus.PENDING))
            .description("Documents currently pending processing")
            .tag("state", DocumentStatus.PENDING.name())
            .register(registry);
        Gauge.builder("docflow_documents_state", documentRepository,
                repository -> repository.countByStatus(DocumentStatus.PROCESSING))
            .description("Documents currently being processed")
            .tag("state", DocumentStatus.PROCESSING.name())
            .register(registry);
        Gauge.builder("docflow_documents_state", documentRepository,
                repository -> repository.countByStatus(DocumentStatus.COMPLETED))
            .description("Documents completed")
            .tag("state", DocumentStatus.COMPLETED.name())
            .register(registry);
        Gauge.builder("docflow_documents_state", documentRepository,
                repository -> repository.countByStatus(DocumentStatus.FAILED))
            .description("Documents failed")
            .tag("state", DocumentStatus.FAILED.name())
            .register(registry);
        Gauge.builder("docflow_documents_result_unknown", documentRepository,
                DocumentRepository::countResultUnknown)
            .description("Processing documents waiting for reconciliation")
            .register(registry);
    }

    public void upload(String outcome) {
        counter("docflow_document_uploads_total", "outcome", outcome).increment();
    }

    public void processing(String outcome) {
        counter("docflow_document_processing_total", "outcome", outcome).increment();
    }

    public void retry(String outcome) {
        counter("docflow_document_retries_total", "outcome", outcome).increment();
    }

    public void storage(String operation, String outcome) {
        counter("docflow_storage_operations_total", "operation", operation, "outcome", outcome).increment();
    }

    public void rabbit(String queue, String outcome) {
        counter("docflow_rabbit_messages_total", "queue", queue, "outcome", outcome).increment();
    }

    public Timer.Sample startProcessingTimer() {
        return Timer.start(registry);
    }

    public void stopProcessingTimer(Timer.Sample sample, String outcome) {
        sample.stop(Timer.builder("docflow_document_processing_duration_seconds")
            .description("Document processing duration")
            .publishPercentiles(0.5, 0.95)
            .tag("outcome", outcome)
            .register(registry));
    }

    private Counter counter(String name, String... tags) {
        Counter.Builder builder = Counter.builder(name).description("DocFlow operational metric");
        for (int index = 0; index < tags.length; index += 2) {
            builder.tag(tags[index], tags[index + 1]);
        }
        return builder.register(registry);
    }
}
