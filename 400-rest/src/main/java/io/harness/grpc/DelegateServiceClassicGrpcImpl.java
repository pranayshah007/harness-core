/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.grpc;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.delegate.AbortExpireTaskRequest;
import io.harness.delegate.AbortTaskResponse;
import io.harness.delegate.CreatePerpetualTaskRequestClassic;
import io.harness.delegate.CreatePerpetualTaskResponseClassic;
import io.harness.delegate.DelegateClassicTaskRequest;
import io.harness.delegate.DelegateTaskGrpc;
import io.harness.delegate.ExecuteTaskResponse;
import io.harness.delegate.ExpireTaskResponse;
import io.harness.delegate.QueueTaskResponse;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.serializer.KryoSerializer;

import software.wings.service.intfc.DelegateTaskServiceClassic;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateServiceClassicGrpcImpl extends DelegateTaskGrpc.DelegateTaskImplBase {
  @Inject private KryoSerializer kryoSerializer;

  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private DelegateTaskServiceClassic delegateTaskServiceClassic;
  @Inject private PerpetualTaskService perpetualTaskService;

  @Override
  public void queueTaskV2(DelegateClassicTaskRequest request, StreamObserver<QueueTaskResponse> responseObserver) {
    try {
      DelegateTask task =
          (DelegateTask) referenceFalseKryoSerializer.asInflatedObject(request.getDelegateTaskKryo().toByteArray());

      delegateTaskServiceClassic.queueTaskV2(task);

      responseObserver.onNext(QueueTaskResponse.newBuilder().setUuid(task.getUuid()).build());
      responseObserver.onCompleted();

    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing queue task request.", ex);
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void executeTaskV2(DelegateClassicTaskRequest request, StreamObserver<ExecuteTaskResponse> responseObserver) {
    try {
      DelegateTask task =
          (DelegateTask) referenceFalseKryoSerializer.asInflatedObject(request.getDelegateTaskKryo().toByteArray());
      DelegateResponseData delegateResponseData = delegateTaskServiceClassic.executeTaskV2(task);
      responseObserver.onNext(
          ExecuteTaskResponse.newBuilder()
              .setDelegateTaskResponseKryo(ByteString.copyFrom(kryoSerializer.asDeflatedBytes(delegateResponseData)))
              .build());
      responseObserver.onCompleted();

    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing execute task request.", ex);
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void abortTaskV2(AbortExpireTaskRequest request, StreamObserver<AbortTaskResponse> responseObserver) {
    try {
      String accountId = request.getAccountId();
      String delegateTaskId = request.getDelegateTaskId();

      DelegateTask delegateTask = delegateTaskServiceClassic.abortTaskV2(accountId, delegateTaskId);
      responseObserver.onNext(
          AbortTaskResponse.newBuilder()
              .setDelegateTaskKryo(ByteString.copyFrom(referenceFalseKryoSerializer.asDeflatedBytes(delegateTask)))
              .build());
      responseObserver.onCompleted();

    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing abort task request.", ex);
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void expireTaskV2(AbortExpireTaskRequest request, StreamObserver<ExpireTaskResponse> responseObserver) {
    try {
      String accountId = request.getAccountId();
      String delegateTaskId = request.getDelegateTaskId();

      String message = delegateTaskServiceClassic.expireTaskV2(accountId, delegateTaskId);
      responseObserver.onNext(ExpireTaskResponse.newBuilder().setMessage(message).build());
      responseObserver.onCompleted();

    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing expire task request.", ex);
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void createPerpetualTaskClassicV2(
      CreatePerpetualTaskRequestClassic request, StreamObserver<CreatePerpetualTaskResponseClassic> responseObserver) {
    try {
      String perpetualTaskType = request.getPerpetualTaskType();
      String accountId = request.getAccountId();
      PerpetualTaskClientContext clientContext =
          (PerpetualTaskClientContext) referenceFalseKryoSerializer.asInflatedObject(
              request.getClientContextKryo().toByteArray());
      PerpetualTaskSchedule schedule = (PerpetualTaskSchedule) referenceFalseKryoSerializer.asInflatedObject(
          request.getPerpetualTaskScheduleKryo().toByteArray());
      boolean allowDuplicate = request.getAllowDuplicate();
      String taskDescription = request.getTaskDescription();
      String taskId = perpetualTaskService.createPerpetualTaskInternal(
          perpetualTaskType, accountId, clientContext, schedule, allowDuplicate, taskDescription);
      responseObserver.onNext(CreatePerpetualTaskResponseClassic.newBuilder().setMessage(taskId).build());
      responseObserver.onCompleted();
    } catch (Exception ex) {
      log.error("Unexpected error occurred while creating perpetual task classic request.", ex);
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }
}
