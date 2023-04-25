/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.s3;

import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.dto.Log;
import software.wings.beans.s3.FetchS3FilesCommandParams;
import software.wings.beans.s3.FetchS3FilesExecutionResponse;
import software.wings.beans.s3.S3File;
import software.wings.beans.s3.S3FileRequest;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.intfc.aws.delegate.AwsS3HelperServiceDelegate;
import software.wings.service.intfc.security.EncryptionService;

import com.amazonaws.services.s3.model.S3Object;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class S3FetchFilesTaskTest extends WingsBaseTest {
  @Mock private EncryptionService encryptionService;
  @Mock private AwsHelperService awsHelperService;
  @Mock private AwsS3HelperServiceDelegate awsS3HelperServiceDelegate;
  @Mock private DelegateLogService delegateLogService;
  @InjectMocks
  S3FetchFilesTask s3FetchFilesTask =
      new S3FetchFilesTask(DelegateTaskPackage.builder().data(TaskData.builder().build()).build(), null,
          mock(Consumer.class), mock(BooleanSupplier.class));

  FetchS3FilesCommandParams params;

  @Before
  public void setup() {
    AwsConfig awsConfig = new AwsConfig();
    List<S3FileRequest> s3FileRequests = new ArrayList<>();
    s3FileRequests.add(
        S3FileRequest.builder().bucketName("BUCKET_NAME").fileKeys(Collections.singletonList("FILE_KEY")).build());
    params = FetchS3FilesCommandParams.builder()
                 .awsConfig(awsConfig)
                 .executionLogName("FetchFiles")
                 .encryptionDetails(new ArrayList<>())
                 .accountId("ACCOUNT_ID")
                 .activityId("ACTIVITY_ID")
                 .appId("APP_ID")
                 .s3FileRequests(s3FileRequests)
                 .build();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testRunS3FetchFileTask() {
    S3Object s3Object = new S3Object();
    s3Object.setObjectContent(new ByteArrayInputStream("s3ObjectContent".getBytes()));
    doReturn(s3Object).when(awsS3HelperServiceDelegate).getObjectFromS3(any(), any(), any(), any());
    FetchS3FilesExecutionResponse s3FilesExecutionResponse = s3FetchFilesTask.run(params);

    assertThat(s3FilesExecutionResponse.getCommandStatus())
        .isEqualTo(FetchS3FilesExecutionResponse.FetchS3FilesCommandStatus.SUCCESS);
    assertThat(s3FilesExecutionResponse.getS3FetchFileResult()
                   .getS3Buckets()
                   .stream()
                   .filter(s3Bucket -> s3Bucket.getName().equals("BUCKET_NAME"))
                   .flatMap(s3Bucket -> s3Bucket.getS3Files().stream())
                   .filter(s3File -> s3File.getFileKey().equals("FILE_KEY"))
                   .map(S3File::getFileContent)
                   .findFirst()
                   .get())
        .isEqualTo("s3ObjectContent");
    verify(delegateLogService, times(2)).save(any(), (Log) any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testRunS3FetchFileTaskMultipleFiles() {
    S3Object s3Object = new S3Object();
    s3Object.setObjectContent(new ByteArrayInputStream("s3ObjectContent".getBytes()));
    S3Object secondS3Object = new S3Object();
    secondS3Object.setObjectContent(new ByteArrayInputStream("secondS3ObjectContent".getBytes()));

    doReturn(secondS3Object)
        .when(awsS3HelperServiceDelegate)
        .getObjectFromS3(params.getAwsConfig(), params.getEncryptionDetails(), "BUCKET_NAME_2", "FILE_KEY");
    doReturn(s3Object)
        .when(awsS3HelperServiceDelegate)
        .getObjectFromS3(params.getAwsConfig(), params.getEncryptionDetails(), "BUCKET_NAME", "FILE_KEY");

    params.getS3FileRequests().add(
        S3FileRequest.builder().bucketName("BUCKET_NAME_2").fileKeys(Collections.singletonList("FILE_KEY")).build());
    FetchS3FilesExecutionResponse s3FilesExecutionResponse = s3FetchFilesTask.run(params);

    assertThat(s3FilesExecutionResponse.getCommandStatus())
        .isEqualTo(FetchS3FilesExecutionResponse.FetchS3FilesCommandStatus.SUCCESS);
    assertThat(s3FilesExecutionResponse.getS3FetchFileResult()
                   .getS3Buckets()
                   .stream()
                   .filter(s3Bucket -> s3Bucket.getName().equals("BUCKET_NAME"))
                   .flatMap(s3Bucket -> s3Bucket.getS3Files().stream())
                   .filter(s3File -> s3File.getFileKey().equals("FILE_KEY"))
                   .map(S3File::getFileContent)
                   .findFirst()
                   .get())
        .isEqualTo("s3ObjectContent");
    assertThat(s3FilesExecutionResponse.getS3FetchFileResult()
                   .getS3Buckets()
                   .stream()
                   .filter(s3Bucket -> s3Bucket.getName().equals("BUCKET_NAME_2"))
                   .flatMap(s3Bucket -> s3Bucket.getS3Files().stream())
                   .filter(s3File -> s3File.getFileKey().equals("FILE_KEY"))
                   .map(S3File::getFileContent)
                   .findFirst()
                   .get())
        .isEqualTo("secondS3ObjectContent");
    verify(delegateLogService, times(3)).save(any(), (Log) any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testRunS3FetchFileTaskMultipleFilesOneFails() {
    S3Object s3Object = new S3Object();
    s3Object.setObjectContent(new ByteArrayInputStream("s3ObjectContent".getBytes()));
    S3Object secondS3Object = new S3Object();
    secondS3Object.setObjectContent(new ByteArrayInputStream("secondS3ObjectContent".getBytes()));

    doThrow(new RuntimeException("File not available"))
        .when(awsS3HelperServiceDelegate)
        .getObjectFromS3(params.getAwsConfig(), params.getEncryptionDetails(), "BUCKET_NAME_2", "FILE_KEY");
    doReturn(s3Object)
        .when(awsS3HelperServiceDelegate)
        .getObjectFromS3(params.getAwsConfig(), params.getEncryptionDetails(), "BUCKET_NAME", "FILE_KEY");

    params.getS3FileRequests().add(
        S3FileRequest.builder().bucketName("BUCKET_NAME_2").fileKeys(Collections.singletonList("FILE_KEY")).build());
    FetchS3FilesExecutionResponse s3FilesExecutionResponse = s3FetchFilesTask.run(params);
    assertThat(s3FilesExecutionResponse.getCommandStatus())
        .isEqualTo(FetchS3FilesExecutionResponse.FetchS3FilesCommandStatus.FAILURE);
    assertThat(s3FilesExecutionResponse.getErrorMessage())
        .isEqualTo("Exception: RuntimeException: File not available while fetching s3 file.");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testFailedRunS3FetchFileTask() {
    S3Object s3Object = new S3Object();
    s3Object.setObjectContent(new ByteArrayInputStream("s3ObjectContent".getBytes()));
    doThrow(new RuntimeException("Invalid Credentials"))
        .when(awsS3HelperServiceDelegate)
        .getObjectFromS3(any(), any(), any(), any());

    FetchS3FilesExecutionResponse s3FilesExecutionResponse = s3FetchFilesTask.run(params);
    assertThat(s3FilesExecutionResponse.getCommandStatus())
        .isEqualTo(FetchS3FilesExecutionResponse.FetchS3FilesCommandStatus.FAILURE);
    assertThat(s3FilesExecutionResponse.getErrorMessage())
        .isEqualTo("Exception: RuntimeException: Invalid Credentials while fetching s3 file.");
  }
}
