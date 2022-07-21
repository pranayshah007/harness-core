package io.harness.service.intfc;

import io.harness.delegate.SendTaskProgressRequest;
import io.harness.delegate.SendTaskProgressResponse;
import io.harness.delegate.SendTaskStatusRequest;
import io.harness.delegate.SendTaskStatusResponse;
import io.harness.delegate.TaskProgressRequest;
import io.harness.delegate.TaskProgressResponse;

public interface TaskProgressService {
  SendTaskStatusResponse sendTaskStatus(SendTaskStatusRequest request);
  SendTaskProgressResponse sendTaskProgress(SendTaskProgressRequest request);
  TaskProgressResponse taskProgress(TaskProgressRequest request);
}
