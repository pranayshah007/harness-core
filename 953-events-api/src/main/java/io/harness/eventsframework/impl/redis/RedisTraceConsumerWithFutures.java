package io.harness.eventsframework.impl.redis;

import io.harness.eventsframework.consumer.Message;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.event.HandleResult;

import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class RedisTraceConsumerWithFutures extends RedisTraceConsumer {
  protected boolean handleMessage(Message message) {
    throw new UnsupportedOperationException("handle message is not supported");
  }

  protected boolean processMessage(Message message) {
    throw new UnsupportedOperationException("handle message is not supported");
  }

  protected CompletableFuture<HandleResult> handleMessageWithFutures(Message message) {
    try (AutoLogContext autoLogContext = new AutoLogContext(
             message.getMessage().getMetadataMap(), AutoLogContext.OverrideBehavior.OVERRIDE_NESTS)) {
      return processMessageWithFutures(message);
    } catch (Exception ex) {
      // This is not evicted from events framework so that it can be processed
      // by other consumer if the error is a runtime error
      log.error(String.format("Error occurred in processing message with id %s", message.getId()), ex);
      return CompletableFuture.completedFuture(HandleResult.builder().messageId(message.getId()).build());
    }
  }

  protected CompletableFuture<HandleResult> processMessageWithFutures(Message message) {
    throw new UnsupportedOperationException("Handle Message with futures is not supported");
  }
}
