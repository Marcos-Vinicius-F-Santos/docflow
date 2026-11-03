package com.dockflow.dockflow.document.application.logging;

import java.util.List;

public record ApplicationLogPageResponse(
    List<ApplicationLogResponse> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
) {
}
