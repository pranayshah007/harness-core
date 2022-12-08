package io.harness.queueservice.infc;

import java.io.IOException;

public interface DelegateServiceQueue<T> {
  void enqueue(T object) throws IOException;
  <T> Object dequeue() throws IOException;
  String acknowledge(String itemId, String accountId) throws IOException;
}
