package io.harness.service.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.delegate.SendTaskProgressRequest;
import io.harness.delegate.SendTaskStatusRequest;
import io.harness.delegate.TaskProgressRequest;
import io.harness.service.intfc.DelegateCallbackRegistry;
import io.harness.service.intfc.DelegateCallbackService;
import io.harness.service.intfc.TaskProgressService;

import com.google.inject.Inject;

public class TaskProgressServiceImpl implements TaskProgressService {
  @Inject private DelegateCallbackRegistry delegateCallbackRegistry;

  @Override
  public void sendTaskStatus(SendTaskStatusRequest request) {
    // todo
  }

  @Override
  public void sendTaskProgress(SendTaskProgressRequest request) {
    DelegateCallbackService delegateCallbackService =
        delegateCallbackRegistry.obtainDelegateCallbackService(request.getCallbackToken().getToken());
    if (delegateCallbackService == null) {
      return;
    }
    delegateCallbackService.publishTaskProgressResponse(
        request.getTaskId().getId(), generateUuid(), request.getTaskResponseData().toByteArray());
  }

  @Override
  public void taskProgress(TaskProgressRequest request) {
    // TODO
  }
}
