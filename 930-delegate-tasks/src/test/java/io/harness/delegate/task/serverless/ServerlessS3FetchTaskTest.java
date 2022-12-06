/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.storeconfig.S3StoreDelegateConfig;
import io.harness.delegate.beans.taskprogress.ITaskProgressClient;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.delegate.task.serverless.request.ServerlessS3FetchRequest;
import io.harness.delegate.task.serverless.response.ServerlessS3FetchResponse;
import io.harness.filesystem.FileIo;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class ServerlessS3FetchTaskTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  final DelegateTaskPackage delegateTaskPackage =
      DelegateTaskPackage.builder().data(TaskData.builder().build()).build();
  private final String manifestId = "serverlessId";
  private final String region = "us-east-1";
  private final String bucketName = "serverless-bucket";
  private final String zipFilePath = "serverless/serverless.zip";
  private final String ServerlessAwsLambda = "ServerlessAwsLambda";
  private final String fileContent = "content test";

  @Mock private BooleanSupplier preExecute;
  @Mock private Consumer<DelegateTaskResponse> consumer;
  @Mock private ILogStreamingTaskClient logStreamingTaskClient;
  @Mock private ServerlessTaskHelperBase serverlessTaskHelperBase;
  @Mock private ITaskProgressClient taskProgressClient;
  @Mock private ExecutorService executorService;

  @Inject
  @InjectMocks
  ServerlessS3FetchTask serverlessS3FetchTask =
      new ServerlessS3FetchTask(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    doReturn(taskProgressClient).when(logStreamingTaskClient).obtainTaskProgressClient();
    doReturn(executorService).when(logStreamingTaskClient).obtainTaskProgressExecutor();
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getS3FetchResponseNoConfigOverrideTest() throws IOException {
    S3StoreDelegateConfig s3StoreDelegateConfig =
        S3StoreDelegateConfig.builder().region(region).bucketName(bucketName).path(zipFilePath).build();
    ServerlessS3FetchFileConfig serverlessS3FetchFileConfig = ServerlessS3FetchFileConfig.builder()
                                                                  .s3StoreDelegateConfig(s3StoreDelegateConfig)
                                                                  .identifier(manifestId)
                                                                  .manifestType(ServerlessAwsLambda)
                                                                  .succeedIfFileNotFound(false)
                                                                  .build();
    ServerlessS3FetchRequest serverlessS3FetchRequest =
        ServerlessS3FetchRequest.builder().serverlessS3FetchFileConfig(serverlessS3FetchFileConfig).build();
    String workingDirectory = "./repository/serverless/workDir1";

    FileIo.createDirectoryIfDoesNotExist(workingDirectory);
    Files.createFile(Paths.get(workingDirectory, "serverless.json"));
    Files.write(Paths.get(workingDirectory, "serverless.json"), fileContent.getBytes());

    ServerlessS3FetchResponse serverlessS3FetchResponse =
        serverlessS3FetchTask.getS3FetchResponse(serverlessS3FetchRequest, workingDirectory);

    assertThat(serverlessS3FetchResponse.getServerlessS3FetchFileResult().getFileContent()).isEqualTo(fileContent);
    assertThat(serverlessS3FetchResponse.getServerlessS3FetchFileResult().getFilePath()).isEqualTo("serverless.json");

    FileIo.deleteDirectoryAndItsContentIfExists(workingDirectory);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getS3FetchResponseWithConfigOverrideTest() throws IOException {
    S3StoreDelegateConfig s3StoreDelegateConfig =
        S3StoreDelegateConfig.builder().region(region).bucketName(bucketName).path(zipFilePath).build();
    ServerlessS3FetchFileConfig serverlessS3FetchFileConfig = ServerlessS3FetchFileConfig.builder()
                                                                  .s3StoreDelegateConfig(s3StoreDelegateConfig)
                                                                  .identifier(manifestId)
                                                                  .manifestType(ServerlessAwsLambda)
                                                                  .configOverridePath("serverlessConfig.yaml")
                                                                  .succeedIfFileNotFound(false)
                                                                  .build();
    ServerlessS3FetchRequest serverlessS3FetchRequest =
        ServerlessS3FetchRequest.builder().serverlessS3FetchFileConfig(serverlessS3FetchFileConfig).build();
    String workingDirectory = "./repository/serverless/workDir2";
    FileIo.createDirectoryIfDoesNotExist(workingDirectory);
    Files.createFile(Paths.get(workingDirectory, "serverlessConfig.yaml"));
    Files.write(Paths.get(workingDirectory, "serverlessConfig.yaml"), fileContent.getBytes());

    ServerlessS3FetchResponse serverlessS3FetchResponse =
        serverlessS3FetchTask.getS3FetchResponse(serverlessS3FetchRequest, workingDirectory);

    assertThat(serverlessS3FetchResponse.getTaskStatus()).isEqualTo(TaskStatus.SUCCESS);
    assertThat(serverlessS3FetchResponse.getServerlessS3FetchFileResult().getFileContent()).isEqualTo(fileContent);
    assertThat(serverlessS3FetchResponse.getServerlessS3FetchFileResult().getFilePath())
        .isEqualTo("serverlessConfig.yaml");

    FileIo.deleteDirectoryAndItsContentIfExists(workingDirectory);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getS3FetchResponseWithConfigOverrideFileNotFoundTest() throws IOException {
    S3StoreDelegateConfig s3StoreDelegateConfig =
        S3StoreDelegateConfig.builder().region(region).bucketName(bucketName).path(zipFilePath).build();
    ServerlessS3FetchFileConfig serverlessS3FetchFileConfig = ServerlessS3FetchFileConfig.builder()
                                                                  .s3StoreDelegateConfig(s3StoreDelegateConfig)
                                                                  .identifier(manifestId)
                                                                  .manifestType(ServerlessAwsLambda)
                                                                  .configOverridePath("serverlessConfig.yaml")
                                                                  .succeedIfFileNotFound(false)
                                                                  .build();
    ServerlessS3FetchRequest serverlessS3FetchRequest =
        ServerlessS3FetchRequest.builder().serverlessS3FetchFileConfig(serverlessS3FetchFileConfig).build();
    String workingDirectory = "./repository/serverless/workDir3";
    ServerlessS3FetchResponse serverlessS3FetchResponse =
        serverlessS3FetchTask.getS3FetchResponse(serverlessS3FetchRequest, workingDirectory);

    assertThat(serverlessS3FetchResponse.getTaskStatus()).isEqualTo(TaskStatus.FAILURE);
    assertThat(serverlessS3FetchResponse.getErrorMessage()).isEqualTo("INVALID_REQUEST");

    FileIo.deleteDirectoryAndItsContentIfExists(workingDirectory);
  }
}
