/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.buckets.resources.s3;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.VED;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.resources.AwsResourceServiceHelper;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsS3BucketResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskType;
import io.harness.delegate.beans.connector.awsconnector.S3BuildsResponse;
import io.harness.exception.BucketServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.BaseNGAccess;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.TaskType;
import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class S3ResourceServiceImplTest extends CategoryTest {
  @Mock private AwsResourceServiceHelper serviceHelper;

  @InjectMocks private S3ResourceServiceImpl s3ResourceServiceImpl;

  private final IdentifierRef awsConnectorRef = IdentifierRef.builder().identifier("aws").build();

  private final List<EncryptedDataDetail> encryptionDetails = singletonList(EncryptedDataDetail.builder().build());

  private String region = "us-east-1";

  private String filePathRegex = "*";

  private String orgIdentifier = "org";

  private String projectIdentifier = "project";

  private String bucket = "bucket";

  private BaseNGAccess baseNGAccess = BaseNGAccess.builder().build();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder()
            .credential(AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE).build())
            .delegateSelectors(Sets.newHashSet("proj-delegate"))
            .build();
    doReturn(awsConnectorDTO).when(serviceHelper).getAwsConnector(awsConnectorRef);

    doReturn(baseNGAccess).when(serviceHelper).getBaseNGAccess(any(), any(), any());
    doReturn(encryptionDetails).when(serviceHelper).getAwsEncryptionDetails(awsConnectorDTO, baseNGAccess);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldGetBucketsSuccess() {
    Map<String, String> actualBuckets =
        ImmutableMap.of("test-bucket-1", "test-bucket-1", "test-bucket-2", "test-bucket-2");
    AwsS3BucketResponse response = AwsS3BucketResponse.builder()
                                       .buckets(actualBuckets)
                                       .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                       .build();
    doReturn(response)
        .when(serviceHelper)
        .getResponseData(eq(baseNGAccess), nullable(AwsTaskParams.class), eq(TaskType.NG_AWS_TASK.name()));

    Map<String, String> result = s3ResourceServiceImpl.getBuckets(awsConnectorRef, region, "test-org", "test-proj");
    assertThat(result).isNotNull();
    assertThat(result).isNotEmpty();
    assertThat(result).containsOnlyKeys("test-bucket-1", "test-bucket-2");
    assertThat(result).containsValues("test-bucket-1", "test-bucket-2");

    ArgumentCaptor<AwsTaskParams> awsTaskParamsCaptor = ArgumentCaptor.forClass(AwsTaskParams.class);
    verify(serviceHelper, times(1))
        .getResponseData(eq(baseNGAccess), awsTaskParamsCaptor.capture(), eq(TaskType.NG_AWS_TASK.name()));
    AwsTaskParams awsTaskParams = awsTaskParamsCaptor.getValue();
    assertThat(awsTaskParams).isNotNull();
    assertThat(awsTaskParams.getAwsConnector()).isNotNull();
    assertThat(awsTaskParams.getRegion()).isEqualTo(region);
    assertThat(awsTaskParams.getAwsTaskType()).isEqualTo(AwsTaskType.LIST_S3_BUCKETS);
    assertThat(awsTaskParams.getAwsConnector().getDelegateSelectors()).contains("proj-delegate");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testShouldGetBucketsFailure() {
    AwsS3BucketResponse response =
        AwsS3BucketResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build();

    doReturn(response)
        .when(serviceHelper)
        .getResponseData(eq(baseNGAccess), nullable(AwsTaskParams.class), eq(TaskType.NG_AWS_TASK.name()));

    assertThatThrownBy(() -> s3ResourceServiceImpl.getBuckets(awsConnectorRef, region, "test-org", "test-proj"))
        .isInstanceOf(BucketServerException.class)
        .hasMessage("AWS S3 Get Buckets task failure due to error");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldGetBucketsError() {
    ErrorNotifyResponseData response = ErrorNotifyResponseData.builder()
                                           .errorMessage("Failed to list buckets")
                                           .exception(new InvalidRequestException("Failed to list buckets"))
                                           .build();
    doReturn(response)
        .when(serviceHelper)
        .getResponseData(eq(baseNGAccess), nullable(AwsTaskParams.class), eq(TaskType.NG_AWS_TASK.name()));

    try {
      s3ResourceServiceImpl.getBuckets(awsConnectorRef, region, "test-org", "test-proj");
      fail("Should throw invalid request exception");
    } catch (BucketServerException bucketServerException) {
      assertThat(bucketServerException.getMessage())
          .isEqualTo("AWS S3 Get Buckets task failure due to error - Failed to list buckets");
    }
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testShouldGetBucketsWithDefaultRegion() {
    Map<String, String> actualBuckets =
        ImmutableMap.of("test-bucket-1", "test-bucket-1", "test-bucket-2", "test-bucket-2");
    AwsS3BucketResponse response = AwsS3BucketResponse.builder()
                                       .buckets(actualBuckets)
                                       .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                       .build();
    doReturn(response)
        .when(serviceHelper)
        .getResponseData(eq(baseNGAccess), nullable(AwsTaskParams.class), eq(TaskType.NG_AWS_TASK.name()));

    Map<String, String> result = s3ResourceServiceImpl.getBuckets(awsConnectorRef, null, "test-org", "test-proj");
    assertThat(result).isNotNull();
    assertThat(result).isNotEmpty();
    assertThat(result).containsOnlyKeys("test-bucket-1", "test-bucket-2");
    assertThat(result).containsValues("test-bucket-1", "test-bucket-2");

    ArgumentCaptor<AwsTaskParams> awsTaskParamsCaptor = ArgumentCaptor.forClass(AwsTaskParams.class);
    verify(serviceHelper, times(1))
        .getResponseData(eq(baseNGAccess), awsTaskParamsCaptor.capture(), eq(TaskType.NG_AWS_TASK.name()));
    AwsTaskParams awsTaskParams = awsTaskParamsCaptor.getValue();
    assertThat(awsTaskParams).isNotNull();
    assertThat(awsTaskParams.getAwsConnector()).isNotNull();
    assertThat(awsTaskParams.getRegion()).isEqualTo(region);
    assertThat(awsTaskParams.getAwsTaskType()).isEqualTo(AwsTaskType.LIST_S3_BUCKETS);
    assertThat(awsTaskParams.getAwsConnector().getDelegateSelectors()).contains("proj-delegate");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetFilePaths() {
    List<BuildDetails> builds = new ArrayList<>();

    BuildDetails build1 = new BuildDetails();
    build1.setNumber("b1");
    build1.setUiDisplayName("Version# b1");

    BuildDetails build2 = new BuildDetails();
    build2.setNumber("b2");
    build2.setUiDisplayName("Version# b2");

    BuildDetails build3 = new BuildDetails();
    build3.setNumber("b3");
    build3.setUiDisplayName("Version# b3");

    BuildDetails build4 = new BuildDetails();
    build4.setNumber("b4");
    build4.setUiDisplayName("Version# b4");

    BuildDetails build5 = new BuildDetails();
    build5.setNumber("b5");
    build5.setUiDisplayName("Version# b5");

    builds.add(build1);
    builds.add(build2);
    builds.add(build3);
    builds.add(build4);
    builds.add(build5);

    S3BuildsResponse expectedBuilds = S3BuildsResponse.builder()
                                          .builds(builds)
                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                          .delegateMetaInfo(null)
                                          .build();

    doReturn(expectedBuilds)
        .when(serviceHelper)
        .getResponseData(eq(baseNGAccess), nullable(AwsTaskParams.class), eq(TaskType.NG_AWS_TASK.name()));

    List<BuildDetails> buildResponse = s3ResourceServiceImpl.getFilePaths(
        awsConnectorRef, region, bucket, filePathRegex, orgIdentifier, projectIdentifier);

    assertThat(buildResponse).isNotNull();
    assertThat(buildResponse).isNotEmpty();
    assertThat(buildResponse.size()).isEqualTo(5);
    assertThat(buildResponse.get(0).getNumber()).isEqualTo("b1");
    assertThat(buildResponse.get(1).getUiDisplayName()).isEqualTo("Version# b2");
    assertThat(buildResponse.get(2).getUiDisplayName()).isEqualTo("Version# b3");
    assertThat(buildResponse.get(3).getUiDisplayName()).isEqualTo("Version# b4");
    assertThat(buildResponse.get(4).getNumber()).isEqualTo("b5");

    ArgumentCaptor<AwsTaskParams> awsTaskParamsCaptor = ArgumentCaptor.forClass(AwsTaskParams.class);

    verify(serviceHelper, times(1))
        .getResponseData(eq(baseNGAccess), awsTaskParamsCaptor.capture(), eq(TaskType.NG_AWS_TASK.name()));

    AwsTaskParams awsTaskParams = awsTaskParamsCaptor.getValue();

    assertThat(awsTaskParams).isNotNull();
    assertThat(awsTaskParams.getAwsConnector()).isNotNull();
    assertThat(awsTaskParams.getRegion()).isEqualTo(region);
    assertThat(awsTaskParams.getAwsTaskType()).isEqualTo(AwsTaskType.GET_BUILDS);
    assertThat(awsTaskParams.getAwsConnector().getDelegateSelectors()).contains("proj-delegate");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetFilePathsWithEmptyRegion() {
    List<BuildDetails> builds = new ArrayList<>();

    BuildDetails build1 = new BuildDetails();
    build1.setNumber("b1");
    build1.setUiDisplayName("Version# b1");

    BuildDetails build2 = new BuildDetails();
    build2.setNumber("b2");
    build2.setUiDisplayName("Version# b2");

    BuildDetails build3 = new BuildDetails();
    build3.setNumber("b3");
    build3.setUiDisplayName("Version# b3");

    BuildDetails build4 = new BuildDetails();
    build4.setNumber("b4");
    build4.setUiDisplayName("Version# b4");

    BuildDetails build5 = new BuildDetails();
    build5.setNumber("b5");
    build5.setUiDisplayName("Version# b5");

    builds.add(build1);
    builds.add(build2);
    builds.add(build3);
    builds.add(build4);
    builds.add(build5);

    S3BuildsResponse expectedBuilds = S3BuildsResponse.builder()
                                          .builds(builds)
                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                          .delegateMetaInfo(null)
                                          .build();

    doReturn(expectedBuilds)
        .when(serviceHelper)
        .getResponseData(eq(baseNGAccess), nullable(AwsTaskParams.class), eq(TaskType.NG_AWS_TASK.name()));

    List<BuildDetails> buildResponse = s3ResourceServiceImpl.getFilePaths(
        awsConnectorRef, null, bucket, filePathRegex, orgIdentifier, projectIdentifier);

    assertThat(buildResponse).isNotNull();
    assertThat(buildResponse).isNotEmpty();
    assertThat(buildResponse.size()).isEqualTo(5);
    assertThat(buildResponse.get(0).getNumber()).isEqualTo("b1");
    assertThat(buildResponse.get(1).getUiDisplayName()).isEqualTo("Version# b2");
    assertThat(buildResponse.get(2).getUiDisplayName()).isEqualTo("Version# b3");
    assertThat(buildResponse.get(3).getUiDisplayName()).isEqualTo("Version# b4");
    assertThat(buildResponse.get(4).getNumber()).isEqualTo("b5");

    ArgumentCaptor<AwsTaskParams> awsTaskParamsCaptor = ArgumentCaptor.forClass(AwsTaskParams.class);

    verify(serviceHelper, times(1))
        .getResponseData(eq(baseNGAccess), awsTaskParamsCaptor.capture(), eq(TaskType.NG_AWS_TASK.name()));

    AwsTaskParams awsTaskParams = awsTaskParamsCaptor.getValue();

    assertThat(awsTaskParams).isNotNull();
    assertThat(awsTaskParams.getAwsConnector()).isNotNull();
    assertThat(awsTaskParams.getRegion()).isEqualTo(region);
    assertThat(awsTaskParams.getAwsTaskType()).isEqualTo(AwsTaskType.GET_BUILDS);
    assertThat(awsTaskParams.getAwsConnector().getDelegateSelectors()).contains("proj-delegate");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetFilePathsWithEmptyFilePathRegex() {
    List<BuildDetails> builds = new ArrayList<>();

    BuildDetails build1 = new BuildDetails();
    build1.setNumber("b1");
    build1.setUiDisplayName("Version# b1");

    BuildDetails build2 = new BuildDetails();
    build2.setNumber("b2");
    build2.setUiDisplayName("Version# b2");

    BuildDetails build3 = new BuildDetails();
    build3.setNumber("b3");
    build3.setUiDisplayName("Version# b3");

    BuildDetails build4 = new BuildDetails();
    build4.setNumber("b4");
    build4.setUiDisplayName("Version# b4");

    BuildDetails build5 = new BuildDetails();
    build5.setNumber("b5");
    build5.setUiDisplayName("Version# b5");

    builds.add(build1);
    builds.add(build2);
    builds.add(build3);
    builds.add(build4);
    builds.add(build5);

    S3BuildsResponse expectedBuilds = S3BuildsResponse.builder()
                                          .builds(new ArrayList<>())
                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                          .delegateMetaInfo(null)
                                          .build();

    doReturn(expectedBuilds)
        .when(serviceHelper)
        .getResponseData(eq(baseNGAccess), nullable(AwsTaskParams.class), eq(TaskType.NG_AWS_TASK.name()));

    List<BuildDetails> buildResponse =
        s3ResourceServiceImpl.getFilePaths(awsConnectorRef, region, bucket, null, orgIdentifier, projectIdentifier);

    assertThat(buildResponse).isNotNull();
    assertThat(buildResponse).isEmpty();
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetFilePathsFailure() {
    S3BuildsResponse response =
        S3BuildsResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build();

    doReturn(response)
        .when(serviceHelper)
        .getResponseData(eq(baseNGAccess), nullable(AwsTaskParams.class), eq(TaskType.NG_AWS_TASK.name()));

    assertThatThrownBy(()
                           -> s3ResourceServiceImpl.getFilePaths(
                               awsConnectorRef, region, bucket, filePathRegex, orgIdentifier, projectIdentifier))
        .isInstanceOf(BucketServerException.class)
        .hasMessage("AWS S3 Get File Paths task failure due to error");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testFilePathsError() {
    ErrorNotifyResponseData response = ErrorNotifyResponseData.builder()
                                           .errorMessage("Failed to list file paths")
                                           .exception(new InvalidRequestException("Failed to list file paths"))
                                           .build();

    doReturn(response)
        .when(serviceHelper)
        .getResponseData(eq(baseNGAccess), nullable(AwsTaskParams.class), eq(TaskType.NG_AWS_TASK.name()));

    try {
      s3ResourceServiceImpl.getFilePaths(
          awsConnectorRef, region, bucket, filePathRegex, orgIdentifier, projectIdentifier);

      fail("Should throw invalid request exception");
    } catch (BucketServerException bucketServerException) {
      assertThat(bucketServerException.getMessage())
          .isEqualTo("AWS S3 Get File Paths task failure due to error - Failed to list file paths");
    }
  }
}
