package io.harness.service.impl;

import io.harness.beans.DelegateTask;
import io.harness.delegate.SendTaskProgressRequest;
import io.harness.delegate.SendTaskProgressResponse;
import io.harness.delegate.SendTaskStatusRequest;
import io.harness.delegate.SendTaskStatusResponse;
import io.harness.delegate.TaskExecutionStage;
import io.harness.delegate.TaskProgressRequest;
import io.harness.delegate.TaskProgressResponse;
import io.harness.delegate.beans.DelegateProgressData;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.grpc.DelegateTaskGrpcUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.service.intfc.DelegateCallbackRegistry;
import io.harness.service.intfc.DelegateTaskService;
import io.harness.service.intfc.TaskProgressService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class TaskProgressServiceImpl implements TaskProgressService {
  private DelegateCallbackRegistry delegateCallbackRegistry;
  private KryoSerializer kryoSerializer;
  private DelegateTaskService delegateTaskService;

  @Inject
  public TaskProgressServiceImpl(KryoSerializer kryoSerializer, DelegateCallbackRegistry delegateCallbackRegistry,
      DelegateTaskService delegateTaskService) {
    this.kryoSerializer = kryoSerializer;
    this.delegateCallbackRegistry = delegateCallbackRegistry;
    this.delegateTaskService = delegateTaskService;
  }

  @Override
  public SendTaskStatusResponse sendTaskStatus(SendTaskStatusRequest request) {
    try {
      DelegateTaskResponse delegateTaskResponse =
          DelegateTaskResponse.builder()
              .responseCode(DelegateTaskResponse.ResponseCode.OK)
              .accountId(request.getAccountId().getId())
              .response((DelegateResponseData) kryoSerializer.asInflatedObject(
                  request.getTaskResponseData().getKryoResultsData().toByteArray()))
              .build();
      delegateTaskService.processDelegateResponse(
          request.getAccountId().getId(), null, request.getTaskId().getId(), delegateTaskResponse);
      return SendTaskStatusResponse.newBuilder().setSuccess(true).build();

    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing send parked task status request.", ex);
    }
    return null;
  }

  @Override
  public SendTaskProgressResponse sendTaskProgress(SendTaskProgressRequest request) {
    try {
      delegateTaskService.publishTaskProgressResponse(request.getAccountId().getId(),
          request.getCallbackToken().getToken(), request.getTaskId().getId(),
          (DelegateProgressData) kryoSerializer.asInflatedObject(
              request.getTaskResponseData().getKryoResultsData().toByteArray()));
      return SendTaskProgressResponse.newBuilder().setSuccess(true).build();
    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing send task progress status request.", ex);
    }
    return null;
  }

  @Override
  public TaskProgressResponse taskProgress(TaskProgressRequest request) {
    try {
      Optional<DelegateTask> delegateTaskOptional =
          delegateTaskService.fetchDelegateTask(request.getAccountId().getId(), request.getTaskId().getId());

      if (delegateTaskOptional.isPresent()) {
        return TaskProgressResponse.newBuilder()
            .setCurrentlyAtStage(
                DelegateTaskGrpcUtils.mapTaskStatusToTaskExecutionStage(delegateTaskOptional.get().getStatus()))
            .build();
      }
      return TaskProgressResponse.newBuilder().setCurrentlyAtStage(TaskExecutionStage.TYPE_UNSPECIFIED).build();

    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing task progress request.", ex);
    }
    return null;
  }
}
