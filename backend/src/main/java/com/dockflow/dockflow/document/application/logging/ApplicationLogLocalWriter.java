package com.dockflow.dockflow.document.application.logging;

/** Local sink that must remain available when remote log delivery is not. */
public interface ApplicationLogLocalWriter {

    void write(ApplicationLogEvent event);
}
