/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.task.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.TaskExecutionStage;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadataType;
import io.harness.delegate.task.stepstatus.artifact.DockerArtifactDescriptor;
import io.harness.delegate.task.stepstatus.artifact.DockerArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.FileArtifactDescriptor;
import io.harness.delegate.task.stepstatus.artifact.FileArtifactMetadata;
import io.harness.grpc.DelegateServiceGrpcAgentClient;
import io.harness.serializer.KryoSerializer;
import io.harness.task.converters.ResponseDataConverterRegistry;
import io.harness.task.service.SendTaskProgressRequest;
import io.harness.task.service.SendTaskProgressResponse;
import io.harness.task.service.SendTaskStatusRequest;
import io.harness.task.service.SendTaskStatusResponse;
import io.harness.task.service.TaskProgressRequest;
import io.harness.task.service.TaskProgressResponse;
import io.harness.task.service.TaskServiceGrpc;
import io.harness.task.service.TaskStatusData;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CI)
public class TaskServiceImpl extends TaskServiceGrpc.TaskServiceImplBase {
  private final DelegateServiceGrpcAgentClient delegateServiceGrpcAgentClient;
  private final KryoSerializer kryoSerializer;
  private final ResponseDataConverterRegistry responseDataConverterRegistry;

  @Inject
  public TaskServiceImpl(DelegateServiceGrpcAgentClient delegateServiceGrpcAgentClient, KryoSerializer kryoSerializer,
      ResponseDataConverterRegistry responseDataConverterRegistry) {
    this.delegateServiceGrpcAgentClient = delegateServiceGrpcAgentClient;
    this.kryoSerializer = kryoSerializer;
    this.responseDataConverterRegistry = responseDataConverterRegistry;
  }

  @Override
  public void taskProgress(TaskProgressRequest request, StreamObserver<TaskProgressResponse> responseObserver) {
    log.info("Received fetchParkedTaskStatus call, accountId:{}, taskId:{}", request.getAccountId().getId(),
        request.getTaskId().getId());
    try {
      TaskExecutionStage taskExecutionStage =
          delegateServiceGrpcAgentClient.taskProgress(request.getAccountId(), request.getTaskId());
      responseObserver.onNext(TaskProgressResponse.newBuilder().setCurrentStage(taskExecutionStage).build());
      responseObserver.onCompleted();
    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing taskProgress request.", ex);
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void sendTaskStatus(SendTaskStatusRequest request, StreamObserver<SendTaskStatusResponse> responseObserver) {
    log.info("Received sendTaskStatus call, accountId:{}, taskId:{}, callbackToken:{}", request.getAccountId().getId(),
        request.getTaskId().getId(), request.getCallbackToken().getToken());
    try {
      TaskStatusData taskStatusData = request.getTaskStatusData();
      if (taskStatusData.hasStepStatus()) {
        io.harness.task.service.StepStatus stepStatus = taskStatusData.getStepStatus();
        ArtifactMetadata artifactMetadata = buildArtifactMetadata(stepStatus);
        StepStatusTaskResponseData responseData =
            StepStatusTaskResponseData.builder()
                .stepStatus(io.harness.delegate.task.stepstatus.StepStatus.builder()
                                .numberOfRetries(stepStatus.getNumRetries())
                                .totalTimeTakenInMillis(Duration.ofSeconds(stepStatus.getTotalTimeTaken().getSeconds())
                                                            .plusNanos(stepStatus.getTotalTimeTaken().getNanos())
                                                            .toMillis())
                                .stepExecutionStatus(io.harness.delegate.task.stepstatus.StepExecutionStatus.valueOf(
                                    stepStatus.getStepExecutionStatus().name()))
                                .output(io.harness.delegate.task.stepstatus.StepMapOutput.builder()
                                            .map(stepStatus.getStepOutput().getOutputMap())
                                            .build())
                                .artifactMetadata(artifactMetadata)
                                .error(stepStatus.getErrorMessage())
                                .build())
                .build();
        boolean success = delegateServiceGrpcAgentClient.sendTaskStatus(request.getAccountId(), request.getTaskId(),
            request.getCallbackToken(), kryoSerializer.asDeflatedBytes(responseData));
        if (success) {
          log.info(
              "Successfully updated task status in sendTaskSTatus call,  accountId:{}, taskId:{}, callbackToken:{}, status: {}",
              request.getAccountId().getId(), request.getTaskId().getId(), request.getCallbackToken().getToken(),
              stepStatus);
        } else {
          log.error(
              "Failed to update task status in sendTaskStatus call,  accountId:{}, taskId:{}, callbackToken:{}, status: {}",
              request.getAccountId().getId(), request.getTaskId().getId(), request.getCallbackToken().getToken(),
              stepStatus);
        }

        responseObserver.onNext(SendTaskStatusResponse.newBuilder().setSuccess(success).build());
      } else {
        log.warn("Step Status is not set in sendTaskStatus call, accountId: {}, taskId: {}, callbackToken: {}",
            request.getAccountId().getId(), request.getTaskId().getId(), request.getCallbackToken().getToken());
        responseObserver.onNext(SendTaskStatusResponse.newBuilder().setSuccess(false).build());
      }
      responseObserver.onCompleted();

    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing getTaskResults request.", ex);
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  @VisibleForTesting
  ArtifactMetadata buildArtifactMetadata(io.harness.task.service.StepStatus stepStatus) {
    ArtifactMetadata artifactMetadata = null;
    if (stepStatus.hasArtifact()) {
      if (stepStatus.getArtifact().hasDockerArtifact()) {
        String registry = stepStatus.getArtifact().getDockerArtifact().getRegistryType();
        String registryUrl = stepStatus.getArtifact().getDockerArtifact().getRegistryUrl();
        List<DockerArtifactDescriptor> dockerArtifactDescriptorList =
            stepStatus.getArtifact()
                .getDockerArtifact()
                .getDockerImagesList()
                .stream()
                .map(
                    img -> DockerArtifactDescriptor.builder().imageName(img.getImage()).digest(img.getDigest()).build())
                .collect(Collectors.toList());
        DockerArtifactMetadata dockerArtifactMetadata = DockerArtifactMetadata.builder()
                                                            .registryType(registry)
                                                            .registryUrl(registryUrl)
                                                            .dockerArtifacts(dockerArtifactDescriptorList)
                                                            .build();

        artifactMetadata = ArtifactMetadata.builder()
                               .type(ArtifactMetadataType.DOCKER_ARTIFACT_METADATA)
                               .spec(dockerArtifactMetadata)
                               .build();
      }
      if (stepStatus.getArtifact().hasFileArtifact()) {
        List<FileArtifactDescriptor> fileArtifactDescriptors =
            stepStatus.getArtifact()
                .getFileArtifact()
                .getFileArtifactsList()
                .stream()
                .map(file -> FileArtifactDescriptor.builder().name(file.getName()).url(file.getUrl()).build())
                .collect(Collectors.toList());
        FileArtifactMetadata fileArtifactMetadata =
            FileArtifactMetadata.builder().fileArtifactDescriptors(fileArtifactDescriptors).build();
        artifactMetadata = ArtifactMetadata.builder()
                               .type(ArtifactMetadataType.FILE_ARTIFACT_METADATA)
                               .spec(fileArtifactMetadata)
                               .build();
      }
    }
    return artifactMetadata;
  }

  @Override
  public void sendTaskProgress(
      SendTaskProgressRequest request, StreamObserver<SendTaskProgressResponse> responseObserver) {
    log.info("Received sendTaskStatus call, accountId:{}, taskId:{}, callbackToken:{}", request.getAccountId().getId(),
        request.getTaskId().getId(), request.getCallbackToken().getToken());
    try {
      delegateServiceGrpcAgentClient.sendTaskProgressUpdate(request.getAccountId(), request.getTaskId(),
          request.getCallbackToken(), request.getTaskResponseData().getKryoResultsData().toByteArray());
      responseObserver.onNext(SendTaskProgressResponse.newBuilder().setSuccess(true).build());
      responseObserver.onCompleted();
    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing sendTaskProgress request.", ex);
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }
}
