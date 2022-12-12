package io.harness.queueservice.infc;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.io.IOException;

@OwnedBy(HarnessTeam.DEL)
public interface DelegateServiceQueue<T> {
  void enqueue(T object) throws IOException;
  <T> Object dequeue() throws IOException;
  String acknowledge(String itemId, String accountId) throws IOException;
}
