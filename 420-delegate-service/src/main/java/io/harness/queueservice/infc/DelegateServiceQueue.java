package io.harness.queueservice.infc;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.DEL)
public interface DelegateServiceQueue<T> {
  void enqueue(T object);
  <T> Object dequeue();
  String acknowledge(String itemId, String accountId);
}
