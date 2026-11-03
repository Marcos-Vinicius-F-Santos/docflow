package com.dockflow.dockflow.document.controller;

import com.dockflow.dockflow.document.application.logging.ApplicationLogPageResponse;
import com.dockflow.dockflow.document.application.logging.ApplicationLogQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/logs")
public class ApplicationLogController {

    private final ApplicationLogQueryService queryService;

    public ApplicationLogController(ApplicationLogQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ApplicationLogPageResponse findLogs(
        @RequestParam(required = false) String page,
        @RequestParam(required = false) String size,
        @RequestParam(required = false) String level,
        @RequestParam(required = false) String service,
        @RequestParam(required = false) String component,
        @RequestParam(required = false) String operation,
        @RequestParam(required = false) String documentId,
        @RequestParam(required = false) String correlationId,
        @RequestParam(required = false) String occurredFrom,
        @RequestParam(required = false) String occurredTo
    ) {
        return queryService.find(
            page, size, level, service, component, operation,
            documentId, correlationId, occurredFrom, occurredTo
        );
    }
}
