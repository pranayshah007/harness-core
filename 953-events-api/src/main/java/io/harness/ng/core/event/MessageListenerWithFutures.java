package io.harness.ng.core.event;

import io.harness.eventsframework.consumer.Message;

import java.util.concurrent.CompletableFuture;

public interface MessageListenerWithFutures {
  CompletableFuture<HandleResult> handleMessageWithFuture(Message message);
}
