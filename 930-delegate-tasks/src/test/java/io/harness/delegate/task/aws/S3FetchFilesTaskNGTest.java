/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.aws.s3.AwsS3FetchFileDelegateConfig;
import io.harness.delegate.beans.aws.s3.AwsS3FetchFilesResponse;
import io.harness.delegate.beans.aws.s3.AwsS3FetchFilesTaskParams;
import io.harness.delegate.beans.aws.s3.S3FileDetailRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.service.impl.AwsApiHelperService;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.model.S3Object;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class S3FetchFilesTaskNGTest extends CategoryTest {
  @Mock private AwsNgConfigMapper awsNgConfigMapper;
  @Mock private AwsS3DelegateTaskHelper awsS3DelegateTaskHelper;
  @Mock private AwsApiHelperService awsApiHelperService;
  @Mock private ILogStreamingTaskClient logStreamingTaskClient;
  @Mock private ExecutorService executorService;

  @InjectMocks
  private S3FetchFilesTaskNG s3FetchFilesTaskNG =
      new S3FetchFilesTaskNG(DelegateTaskPackage.builder().data(TaskData.builder().build()).build(),
          logStreamingTaskClient, mock(Consumer.class), mock(BooleanSupplier.class));

  AwsS3FetchFilesTaskParams params;
  private Future<?> future;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doReturn(executorService).when(logStreamingTaskClient).obtainTaskProgressExecutor();
    doReturn(future).when(executorService).submit(any(Runnable.class));

    AwsS3FetchFileDelegateConfig awsS3FetchFileDelegateConfig =
        AwsS3FetchFileDelegateConfig.builder()
            .identifier("test-s3-identifier")
            .region("test-aws-region")
            .fileDetails(Collections.singletonList(
                S3FileDetailRequest.builder().fileKey("test-fileKey").bucketName("test-bucketName").build()))
            .awsConnector(AwsConnectorDTO.builder().build())
            .build();

    params = AwsS3FetchFilesTaskParams.builder()
                 .fetchFileDelegateConfigs(Collections.singletonList(awsS3FetchFileDelegateConfig))
                 .build();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFetchS3FilesIsSuccess() {
    S3Object s3Object = new S3Object();
    s3Object.setObjectContent(new ByteArrayInputStream("s3ObjectContent".getBytes()));
    doReturn(s3Object).when(awsApiHelperService).getObjectFromS3(any(), any(), any(), any());

    AwsInternalConfig emptyConfig = AwsInternalConfig.builder().build();
    doReturn(emptyConfig).when(awsNgConfigMapper).createAwsInternalConfig(any());

    AwsS3FetchFilesResponse awsS3FetchFilesResponse = (AwsS3FetchFilesResponse) s3FetchFilesTaskNG.run(params);

    assertThat(awsS3FetchFilesResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(awsS3FetchFilesResponse.getS3filesDetails().get("test-s3-identifier").size()).isEqualTo(1);
    assertThat(awsS3FetchFilesResponse.getS3filesDetails().get("test-s3-identifier").get(0).getBucketName())
        .isEqualTo("test-bucketName");
    assertThat(awsS3FetchFilesResponse.getS3filesDetails().get("test-s3-identifier").get(0).getFileKey())
        .isEqualTo("test-fileKey");
    assertThat(awsS3FetchFilesResponse.getS3filesDetails().get("test-s3-identifier").get(0).getFileContent())
        .isEqualTo("s3ObjectContent");
    verify(awsApiHelperService, times(1))
        .getObjectFromS3(any(), eq("test-aws-region"), eq("test-bucketName"), eq("test-fileKey"));
    verify(awsS3DelegateTaskHelper, times(1)).decryptRequestDTOs(any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFetchMultipleS3FilesIsSuccess() {
    S3Object s3Object = new S3Object();
    s3Object.setObjectContent(new ByteArrayInputStream("s3ObjectContent".getBytes()));
    doReturn(s3Object).when(awsApiHelperService).getObjectFromS3(any(), any(), any(), eq("test-fileKey"));
    doReturn(s3Object).when(awsApiHelperService).getObjectFromS3(any(), any(), any(), eq("test-fileKey-2"));

    AwsInternalConfig emptyConfig = AwsInternalConfig.builder().build();
    doReturn(emptyConfig).when(awsNgConfigMapper).createAwsInternalConfig(any());

    params.getFetchFileDelegateConfigs().get(0).setFileDetails(
        Arrays.asList(S3FileDetailRequest.builder().fileKey("test-fileKey").bucketName("test-bucketName").build(),
            S3FileDetailRequest.builder().fileKey("test-fileKey-2").bucketName("test-bucketName-2").build()));

    AwsS3FetchFilesResponse awsS3FetchFilesResponse = (AwsS3FetchFilesResponse) s3FetchFilesTaskNG.run(params);

    assertThat(awsS3FetchFilesResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(awsS3FetchFilesResponse.getS3filesDetails().get("test-s3-identifier").size()).isEqualTo(2);
    verify(awsApiHelperService, times(2)).getObjectFromS3(any(), eq("test-aws-region"), any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFetchS3FilesIsSuccessWithoutFileContent() {
    S3Object s3Object = new S3Object();
    s3Object.setObjectContent(new ByteArrayInputStream("s3ObjectContent".getBytes()));
    doReturn(s3Object).when(awsApiHelperService).getObjectFromS3(any(), any(), any(), any());

    AwsInternalConfig emptyConfig = AwsInternalConfig.builder().build();
    doReturn(emptyConfig).when(awsNgConfigMapper).createAwsInternalConfig(any());

    params.getFetchFileDelegateConfigs().get(0).setFileDetails(new ArrayList<>());

    AwsS3FetchFilesResponse awsS3FetchFilesResponse = (AwsS3FetchFilesResponse) s3FetchFilesTaskNG.run(params);

    assertThat(awsS3FetchFilesResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(awsS3FetchFilesResponse.getS3filesDetails().get("test-s3-identifier").size()).isEqualTo(0);
    verify(awsApiHelperService, times(0)).getObjectFromS3(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFetchS3FilesAndExceptionThrown() {
    AwsInternalConfig emptyConfig = AwsInternalConfig.builder().build();
    doReturn(emptyConfig).when(awsNgConfigMapper).createAwsInternalConfig(any());

    doThrow(new AmazonServiceException("AWS Service Exception"))
        .when(awsApiHelperService)
        .getObjectFromS3(any(), any(), any(), any());

    assertThatThrownBy(() -> s3FetchFilesTaskNG.run(params)).isInstanceOf(TaskNGDataException.class);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testTaskRunWithInvalidParams() {
    assertThatThrownBy(() -> s3FetchFilesTaskNG.run(new Object[] {})).isInstanceOf(NotImplementedException.class);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testTaskIsSupportingErrorFramework() {
    assertThat(s3FetchFilesTaskNG.isSupportingErrorFramework()).isEqualTo(true);
  }
}
