package io.harness.queueservice.infc;

import java.io.IOException;

public interface DelegateServiceQueue<T> {
  void enqueue(T object);
  <T> Object dequeue() throws IOException;
}
