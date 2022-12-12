/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ecs;

import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.storeconfig.S3StoreDelegateConfig;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.ecs.EcsS3FetchFileConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsS3FetchRequest;
import io.harness.delegate.task.ecs.request.EcsS3FetchRunTaskRequest;
import io.harness.delegate.task.ecs.response.EcsS3FetchResponse;
import io.harness.delegate.task.ecs.response.EcsS3FetchRunTaskResponse;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import software.wings.service.impl.AwsApiHelperService;

import com.amazonaws.services.s3.model.S3Object;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class EcsS3FetchCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private String region = "us-east-1";
  private String bucket = "bucket";
  private String filePath = "ecs/filePath.yaml";
  private String content = "content";

  @Mock private AwsNgConfigMapper awsNgConfigMapper;
  @Mock private AwsApiHelperService awsApiHelperService;
  @Mock private LogCallback logCallback;
  @Mock private EcsTaskHelperBase ecsTaskHelperBase;
  @Mock private ILogStreamingTaskClient iLogStreamingTaskClient;

  @Spy @InjectMocks private EcsS3FetchCommandTaskHandler ecsS3FetchCommandTaskHandler;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void ecsS3FetchResponseTest() throws Exception {
    AwsCredentialDTO awsCredentialDTO = AwsCredentialDTO.builder().build();
    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
    S3StoreDelegateConfig s3StoreConfig = S3StoreDelegateConfig.builder()
                                              .awsConnector(awsConnectorDTO)
                                              .region(region)
                                              .bucketName(bucket)
                                              .path(filePath)
                                              .build();
    EcsS3FetchFileConfig ecsS3FetchFileConfig =
        EcsS3FetchFileConfig.builder().s3StoreDelegateConfig(s3StoreConfig).build();
    EcsS3FetchRequest ecsS3FetchRequest = EcsS3FetchRequest.builder()
                                              .shouldOpenLogStream(true)
                                              .ecsTaskDefinitionS3FetchFileConfig(ecsS3FetchFileConfig)
                                              .ecsServiceDefinitionS3FetchFileConfig(ecsS3FetchFileConfig)
                                              .ecsScalableTargetS3FetchFileConfigs(Arrays.asList(ecsS3FetchFileConfig))
                                              .ecsScalingPolicyS3FetchFileConfigs(Arrays.asList(ecsS3FetchFileConfig))
                                              .build();

    doReturn(logCallback).when(ecsTaskHelperBase).getLogCallback(any(), anyString(), anyBoolean(), any());
    AwsInternalConfig awsConfig = AwsInternalConfig.builder().build();
    doReturn(awsConfig).when(awsNgConfigMapper).createAwsInternalConfig(s3StoreConfig.getAwsConnector());
    List<S3Object> s3Objects = Arrays.asList(new S3Object(), new S3Object(), new S3Object(), new S3Object());
    for (S3Object s3Object : s3Objects) {
      s3Object.setObjectContent(new ByteArrayInputStream(content.getBytes()));
    }

    Mockito.when(awsApiHelperService.getObjectFromS3(any(), eq(region), eq(bucket), eq(filePath)))
        .thenReturn(s3Objects.get(0))
        .thenReturn(s3Objects.get(1))
        .thenReturn(s3Objects.get(2))
        .thenReturn(s3Objects.get(3));

    EcsS3FetchResponse ecsS3FetchResponse = (EcsS3FetchResponse) ecsS3FetchCommandTaskHandler.getS3FetchResponse(
        ecsS3FetchRequest, iLogStreamingTaskClient);

    assertThat(ecsS3FetchResponse.getTaskStatus()).isEqualTo(TaskStatus.SUCCESS);
    assertThat(ecsS3FetchResponse.getEcsS3TaskDefinitionContent()).isEqualTo(content);
    assertThat(ecsS3FetchResponse.getEcsS3ServiceDefinitionContent()).isEqualTo(content);
    assertThat(ecsS3FetchResponse.getEcsS3ScalableTargetContents()).isEqualTo(Arrays.asList(content));
    assertThat(ecsS3FetchResponse.getEcsS3ScalingPolicyContents()).isEqualTo(Arrays.asList(content));
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void runTaskS3FetchResponseTest() throws Exception {
    AwsCredentialDTO awsCredentialDTO = AwsCredentialDTO.builder().build();
    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
    S3StoreDelegateConfig s3StoreConfig = S3StoreDelegateConfig.builder()
                                              .awsConnector(awsConnectorDTO)
                                              .region(region)
                                              .bucketName(bucket)
                                              .path(filePath)
                                              .build();
    EcsS3FetchFileConfig ecsS3FetchFileConfig =
        EcsS3FetchFileConfig.builder().s3StoreDelegateConfig(s3StoreConfig).build();
    EcsS3FetchRunTaskRequest ecsS3FetchRequest = EcsS3FetchRunTaskRequest.builder()
                                                     .shouldOpenLogStream(true)
                                                     .runTaskDefinitionS3FetchFileConfig(ecsS3FetchFileConfig)
                                                     .runTaskRequestDefinitionS3FetchFileConfig(ecsS3FetchFileConfig)
                                                     .build();

    doReturn(logCallback).when(ecsTaskHelperBase).getLogCallback(any(), anyString(), anyBoolean(), any());
    AwsInternalConfig awsConfig = AwsInternalConfig.builder().build();
    doReturn(awsConfig).when(awsNgConfigMapper).createAwsInternalConfig(s3StoreConfig.getAwsConnector());
    List<S3Object> s3Objects = Arrays.asList(new S3Object(), new S3Object(), new S3Object(), new S3Object());
    for (S3Object s3Object : s3Objects) {
      s3Object.setObjectContent(new ByteArrayInputStream(content.getBytes()));
    }

    Mockito.when(awsApiHelperService.getObjectFromS3(any(), eq(region), eq(bucket), eq(filePath)))
        .thenReturn(s3Objects.get(0))
        .thenReturn(s3Objects.get(1))
        .thenReturn(s3Objects.get(2))
        .thenReturn(s3Objects.get(3));

    EcsS3FetchRunTaskResponse ecsS3FetchRunTaskResponse =
        (EcsS3FetchRunTaskResponse) ecsS3FetchCommandTaskHandler.getS3FetchResponse(
            ecsS3FetchRequest, iLogStreamingTaskClient);

    assertThat(ecsS3FetchRunTaskResponse.getTaskStatus()).isEqualTo(TaskStatus.SUCCESS);
    assertThat(ecsS3FetchRunTaskResponse.getRunTaskDefinitionFileContent()).isEqualTo(content);
    assertThat(ecsS3FetchRunTaskResponse.getRunTaskRequestDefinitionFileContent()).isEqualTo(content);
  }

  @Test(expected = TaskNGDataException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getS3FetchResponseExceptionTest() {
    AwsCredentialDTO awsCredentialDTO = AwsCredentialDTO.builder().build();
    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
    S3StoreDelegateConfig s3StoreConfig = S3StoreDelegateConfig.builder()
                                              .awsConnector(awsConnectorDTO)
                                              .region(region)
                                              .bucketName(bucket)
                                              .path(filePath)
                                              .build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    EcsS3FetchFileConfig ecsS3FetchFileConfig =
        EcsS3FetchFileConfig.builder().s3StoreDelegateConfig(s3StoreConfig).build();
    EcsS3FetchRequest ecsS3FetchRequest = EcsS3FetchRequest.builder()
                                              .shouldOpenLogStream(true)
                                              .commandUnitsProgress(commandUnitsProgress)
                                              .ecsTaskDefinitionS3FetchFileConfig(ecsS3FetchFileConfig)
                                              .ecsServiceDefinitionS3FetchFileConfig(ecsS3FetchFileConfig)
                                              .ecsScalableTargetS3FetchFileConfigs(Arrays.asList(ecsS3FetchFileConfig))
                                              .ecsScalingPolicyS3FetchFileConfigs(Arrays.asList(ecsS3FetchFileConfig))
                                              .build();

    doReturn(logCallback).when(ecsTaskHelperBase).getLogCallback(any(), anyString(), anyBoolean(), any());
    AwsInternalConfig awsConfig = AwsInternalConfig.builder().build();
    doReturn(awsConfig).when(awsNgConfigMapper).createAwsInternalConfig(s3StoreConfig.getAwsConnector());
    List<S3Object> s3Objects = Arrays.asList(new S3Object(), new S3Object(), new S3Object(), new S3Object());
    for (S3Object s3Object : s3Objects) {
      s3Object.setObjectContent(new ByteArrayInputStream(content.getBytes()));
    }

    Exception e = new InvalidRequestException("error");

    Mockito.when(awsApiHelperService.getObjectFromS3(any(), eq(region), eq(bucket), eq(filePath))).thenThrow(e);

    ecsS3FetchCommandTaskHandler.getS3FetchResponse(ecsS3FetchRequest, iLogStreamingTaskClient);
  }

  @Test(expected = TaskNGDataException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getS3FetchRunTaskResponseExceptionTest() {
    AwsCredentialDTO awsCredentialDTO = AwsCredentialDTO.builder().build();
    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
    S3StoreDelegateConfig s3StoreConfig = S3StoreDelegateConfig.builder()
                                              .awsConnector(awsConnectorDTO)
                                              .region(region)
                                              .bucketName(bucket)
                                              .path(filePath)
                                              .build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    EcsS3FetchFileConfig ecsS3FetchFileConfig =
        EcsS3FetchFileConfig.builder().s3StoreDelegateConfig(s3StoreConfig).build();
    EcsS3FetchRunTaskRequest ecsS3FetchRunTaskRequest =
        EcsS3FetchRunTaskRequest.builder()
            .shouldOpenLogStream(true)
            .commandUnitsProgress(commandUnitsProgress)
            .runTaskDefinitionS3FetchFileConfig(ecsS3FetchFileConfig)
            .runTaskRequestDefinitionS3FetchFileConfig(ecsS3FetchFileConfig)
            .build();

    doReturn(logCallback).when(ecsTaskHelperBase).getLogCallback(any(), anyString(), anyBoolean(), any());
    AwsInternalConfig awsConfig = AwsInternalConfig.builder().build();
    doReturn(awsConfig).when(awsNgConfigMapper).createAwsInternalConfig(s3StoreConfig.getAwsConnector());
    List<S3Object> s3Objects = Arrays.asList(new S3Object(), new S3Object(), new S3Object(), new S3Object());
    for (S3Object s3Object : s3Objects) {
      s3Object.setObjectContent(new ByteArrayInputStream(content.getBytes()));
    }

    Exception e = new InvalidRequestException("error");

    Mockito.when(awsApiHelperService.getObjectFromS3(any(), eq(region), eq(bucket), eq(filePath))).thenThrow(e);

    ecsS3FetchCommandTaskHandler.getS3FetchResponse(ecsS3FetchRunTaskRequest, iLogStreamingTaskClient);
  }
}
