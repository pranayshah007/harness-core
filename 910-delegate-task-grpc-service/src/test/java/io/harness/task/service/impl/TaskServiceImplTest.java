/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.task.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.delegate.task.stepstatus.artifact.ArtifactMetadataType.DOCKER_ARTIFACT_METADATA;
import static io.harness.delegate.task.stepstatus.artifact.ArtifactMetadataType.FILE_ARTIFACT_METADATA;
import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.rule.OwnerRule.SANJA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.callback.DelegateCallbackToken;
import io.harness.category.element.UnitTests;
import io.harness.delegate.AccountId;
import io.harness.delegate.TaskExecutionStage;
import io.harness.delegate.TaskId;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.DockerArtifactDescriptor;
import io.harness.delegate.task.stepstatus.artifact.DockerArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.FileArtifactDescriptor;
import io.harness.delegate.task.stepstatus.artifact.FileArtifactMetadata;
import io.harness.grpc.DelegateServiceGrpcAgentClient;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.task.TaskServiceTestBase;
import io.harness.task.TaskServiceTestHelper;
import io.harness.task.converters.ResponseDataConverterRegistry;
import io.harness.task.service.SendTaskProgressRequest;
import io.harness.task.service.SendTaskProgressResponse;
import io.harness.task.service.SendTaskStatusRequest;
import io.harness.task.service.SendTaskStatusResponse;
import io.harness.task.service.StepStatus;
import io.harness.task.service.TaskProgressRequest;
import io.harness.task.service.TaskProgressResponse;
import io.harness.task.service.TaskServiceGrpc;
import io.harness.task.service.TaskStatusData;

import com.google.inject.Inject;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(CI)
public class TaskServiceImplTest extends TaskServiceTestBase {
  @Rule public GrpcCleanupRule grpcCleanupRule = new GrpcCleanupRule();
  @Mock private DelegateServiceGrpcAgentClient delegateServiceGrpcAgentClient;
  @Inject KryoSerializer kryoSerializer;
  @Inject ResponseDataConverterRegistry registry;

  @Inject TaskServiceTestHelper taskServiceTestHelper;

  private TaskServiceGrpc.TaskServiceBlockingStub taskServiceBlockingStub;
  private Server testInProcessServer;
  private AccountId accountId;
  private TaskId taskId;
  private DelegateCallbackToken delegateCallbackToken;
  private TaskServiceImpl taskService;

  @Before
  public void doSetup() throws IOException {
    TaskServiceTestHelper.registerConverters(registry);
    taskService = new TaskServiceImpl(delegateServiceGrpcAgentClient, kryoSerializer, registry);

    String serverName = InProcessServerBuilder.generateName();
    testInProcessServer = grpcCleanupRule.register(
        InProcessServerBuilder.forName(serverName).directExecutor().addService(taskService).build().start());
    taskServiceBlockingStub = TaskServiceGrpc.newBlockingStub(
        grpcCleanupRule.register(InProcessChannelBuilder.forName(serverName).directExecutor().build()));

    accountId = AccountId.newBuilder().setId("accountId").build();
    taskId = TaskId.newBuilder().setId("taskId").build();
    delegateCallbackToken = DelegateCallbackToken.newBuilder().setToken("driverId").build();
  }

  @After
  public void doCleanup() {
    testInProcessServer.shutdown();
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldGetTaskProgress() {
    when(delegateServiceGrpcAgentClient.taskProgress(eq(accountId), eq(taskId)))
        .thenReturn(TaskExecutionStage.EXECUTING)
        .thenThrow(new IllegalArgumentException());
    TaskProgressResponse taskProgressResponse = taskServiceBlockingStub.taskProgress(
        TaskProgressRequest.newBuilder().setAccountId(accountId).setTaskId(taskId).build());
    assertThat(taskProgressResponse)
        .isEqualTo(TaskProgressResponse.newBuilder().setCurrentStage(TaskExecutionStage.EXECUTING).build());

    assertThatThrownBy(()
                           -> taskServiceBlockingStub.taskProgress(
                               TaskProgressRequest.newBuilder().setAccountId(accountId).setTaskId(taskId).build()))
        .isInstanceOf(io.grpc.StatusRuntimeException.class);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldSendTaskStatus() {
    when(delegateServiceGrpcAgentClient.sendTaskStatus(eq(accountId), eq(taskId), eq(delegateCallbackToken),
             eq(taskServiceTestHelper.getDeflatedStepStatusTaskResponseData())))
        .thenReturn(true)
        .thenThrow(new IllegalArgumentException());
    SendTaskStatusResponse sendTaskStatusResponse =
        taskServiceBlockingStub.sendTaskStatus(SendTaskStatusRequest.newBuilder()
                                                   .setAccountId(accountId)
                                                   .setTaskId(taskId)
                                                   .setCallbackToken(delegateCallbackToken)
                                                   .setTaskStatusData(taskServiceTestHelper.getTaskResponseData())
                                                   .build());
    assertThat(sendTaskStatusResponse).isEqualTo(SendTaskStatusResponse.newBuilder().setSuccess(true).build());

    SendTaskStatusResponse sendTaskStatusEmptyResponse =
        taskServiceBlockingStub.sendTaskStatus(SendTaskStatusRequest.newBuilder()
                                                   .setAccountId(accountId)
                                                   .setTaskId(taskId)
                                                   .setCallbackToken(delegateCallbackToken)
                                                   .setTaskStatusData(TaskStatusData.newBuilder().build())
                                                   .build());
    assertThat(sendTaskStatusEmptyResponse).isEqualTo(SendTaskStatusResponse.newBuilder().setSuccess(false).build());

    assertThatThrownBy(()
                           -> taskServiceBlockingStub.sendTaskStatus(
                               SendTaskStatusRequest.newBuilder()
                                   .setAccountId(accountId)
                                   .setTaskId(taskId)
                                   .setCallbackToken(delegateCallbackToken)
                                   .setTaskStatusData(taskServiceTestHelper.getTaskResponseData())
                                   .build()))
        .isInstanceOf(io.grpc.StatusRuntimeException.class);
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldSendTaskProgressSuccess() {
    when(delegateServiceGrpcAgentClient.sendTaskProgressUpdate(eq(accountId), eq(taskId), eq(delegateCallbackToken),
             eq(taskServiceTestHelper.getTaskProgressResponseData().getKryoResultsData().toByteArray())))
        .thenReturn(true);
    SendTaskProgressResponse sendTaskProgressResponse = taskServiceBlockingStub.sendTaskProgress(
        SendTaskProgressRequest.newBuilder()
            .setAccountId(accountId)
            .setTaskId(taskId)
            .setCallbackToken(delegateCallbackToken)
            .setTaskResponseData(taskServiceTestHelper.getTaskProgressResponseData())
            .build());

    assertThat(sendTaskProgressResponse).isEqualTo(SendTaskProgressResponse.newBuilder().setSuccess(true).build());
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldSendTaskProgressException() {
    when(delegateServiceGrpcAgentClient.sendTaskProgressUpdate(eq(accountId), eq(taskId), eq(delegateCallbackToken),
             eq(taskServiceTestHelper.getTaskProgressResponseData().getKryoResultsData().toByteArray())))
        .thenThrow(new IllegalArgumentException());

    assertThatThrownBy(()
                           -> taskServiceBlockingStub.sendTaskProgress(
                               SendTaskProgressRequest.newBuilder()
                                   .setAccountId(accountId)
                                   .setTaskId(taskId)
                                   .setCallbackToken(delegateCallbackToken)
                                   .setTaskResponseData(taskServiceTestHelper.getTaskProgressResponseData())
                                   .build()))
        .isInstanceOf(io.grpc.StatusRuntimeException.class);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldBuildDockerArtifactMetadata() {
    StepStatus stepStatus =
        StepStatus.newBuilder()
            .setArtifact(
                io.harness.product.ci.engine.proto.Artifact.newBuilder()
                    .setDockerArtifact(
                        io.harness.product.ci.engine.proto.DockerArtifactMetadata.newBuilder()
                            .setRegistryType("Docker")
                            .setRegistryUrl("https://registry.docker.io/v2")
                            .addDockerImages(
                                io.harness.product.ci.engine.proto.DockerImageMetadata.newBuilder()
                                    .setDigest(
                                        "sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f70")
                                    .setImage("harness/ci-automation:1.2")
                                    .build())
                            .addDockerImages(
                                io.harness.product.ci.engine.proto.DockerImageMetadata.newBuilder()
                                    .setDigest(
                                        "sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f70")
                                    .setImage("harness/ci-automation:latest")
                                    .build())
                            .build())
                    .build())
            .build();
    ArtifactMetadata artifactMetadata = taskService.buildArtifactMetadata(stepStatus);
    assertThat(artifactMetadata).isNotNull();
    assertThat(artifactMetadata.getType()).isEqualTo(DOCKER_ARTIFACT_METADATA);
    assertThat(artifactMetadata.getSpec())
        .isEqualTo(
            DockerArtifactMetadata.builder()
                .registryUrl("https://registry.docker.io/v2")
                .registryType("Docker")
                .dockerArtifact(DockerArtifactDescriptor.builder()
                                    .imageName("harness/ci-automation:1.2")
                                    .digest("sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f70")
                                    .build())
                .dockerArtifact(DockerArtifactDescriptor.builder()
                                    .imageName("harness/ci-automation:latest")
                                    .digest("sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f70")
                                    .build())
                .build());
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldBuildFileArtifactMetadata() {
    StepStatus stepStatus =
        StepStatus.newBuilder()
            .setArtifact(
                io.harness.product.ci.engine.proto.Artifact.newBuilder()
                    .setFileArtifact(
                        io.harness.product.ci.engine.proto.FileArtifactMetadata.newBuilder()
                            .addFileArtifacts(io.harness.product.ci.engine.proto.FileArtifact.newBuilder()
                                                  .setName("/dir/file1")
                                                  .setUrl("https://mybucket.s3.us-east-1.amazonaws.com/dir/file1")
                                                  .build())
                            .addFileArtifacts(io.harness.product.ci.engine.proto.FileArtifact.newBuilder()
                                                  .setName("/dir/file2")
                                                  .setUrl("https://mybucket.s3.us-east-1.amazonaws.com/dir/file2")
                                                  .build())
                            .build())
                    .build())
            .build();
    ArtifactMetadata artifactMetadata = taskService.buildArtifactMetadata(stepStatus);
    assertThat(artifactMetadata).isNotNull();
    assertThat(artifactMetadata.getType()).isEqualTo(FILE_ARTIFACT_METADATA);
    assertThat(artifactMetadata.getSpec())
        .isEqualTo(FileArtifactMetadata.builder()
                       .fileArtifactDescriptor(FileArtifactDescriptor.builder()
                                                   .name("/dir/file1")
                                                   .url("https://mybucket.s3.us-east-1.amazonaws.com/dir/file1")
                                                   .build())
                       .fileArtifactDescriptor(FileArtifactDescriptor.builder()
                                                   .name("/dir/file2")
                                                   .url("https://mybucket.s3.us-east-1.amazonaws.com/dir/file2")
                                                   .build())
                       .build());
  }
}
