package com.dockflow.dockflow.document.application;

import com.dockflow.dockflow.document.messaging.rabbitmq.DocumentLifecycleEvent;
import com.dockflow.dockflow.document.messaging.rabbitmq.DocumentNotificationTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DocumentNotificationChannelDispatcherTest {

    @Mock
    private DocumentNotificationDispatcher webSocketChannel;

    @Mock
    private DocumentNotificationDispatcher emailChannel;

    @Test
    void dispatchesEveryEventToBothIndependentChannels() {
        DocumentLifecycleEvent event = DocumentNotificationTestFixtures.lifecycleEvents().get(0);

        new DocumentNotificationChannelDispatcher(List.of(webSocketChannel, emailChannel)).dispatch(event);

        verify(webSocketChannel).dispatch(event);
        verify(emailChannel).dispatch(event);
    }

    @Test
    void isolatesOneChannelFailureAndContinuesWithTheOther() {
        DocumentLifecycleEvent event = DocumentNotificationTestFixtures.lifecycleEvents().get(1);
        doThrow(new RuntimeException("WebSocket unavailable")).when(webSocketChannel).dispatch(event);

        new DocumentNotificationChannelDispatcher(List.of(webSocketChannel, emailChannel)).dispatch(event);

        verify(webSocketChannel).dispatch(event);
        verify(emailChannel).dispatch(event);
    }
}
