package io.harness.service.intfc;

import io.harness.delegate.SendTaskProgressRequest;
import io.harness.delegate.SendTaskStatusRequest;
import io.harness.delegate.TaskProgressRequest;

public interface TaskProgressService {
  void sendTaskStatus(SendTaskStatusRequest request);
  void sendTaskProgress(SendTaskProgressRequest request);
  void taskProgress(TaskProgressRequest request);
}
