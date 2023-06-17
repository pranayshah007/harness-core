/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import static io.harness.ModuleType.CORE;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.streaming.dtos.AuditBatchDTO;
import io.harness.audit.streaming.dtos.PutObjectResultResponse;
import io.harness.audit.streaming.outgoing.OutgoingAuditMessage;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsPutAuditBatchToBucketTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsPutAuditBatchToBucketTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsS3BucketResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskType;
import io.harness.delegate.beans.connector.awsconnector.S3BuildResponse;
import io.harness.delegate.beans.connector.awsconnector.S3BuildsResponse;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.impl.AwsApiHelperService;

import com.amazonaws.AmazonServiceException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class AwsS3DelegateTaskHelperTest extends CategoryTest {
  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock private AwsApiHelperService awsApiHelperService;
  @InjectMocks private AwsS3DelegateTaskHelper taskHelper;
  @InjectMocks private AwsApiHelperService awsApiHelperService1;

  private static final char[] accessKey = "abcd".toCharArray();
  private static final char[] secretKey = "pqrs".toCharArray();
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    on(taskHelper).set("awsNgConfigMapper", new AwsNgConfigMapper());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldGetS3BucketsManualConfig() {
    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder()
            .credential(
                AwsCredentialDTO.builder()
                    .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                    .config(AwsManualConfigSpecDTO.builder()
                                .accessKey("test-access-key")
                                .secretKeyRef(SecretRefData.builder().decryptedValue("secret".toCharArray()).build())
                                .build())
                    .build())
            .build();
    AwsTaskParams awsTaskParams = AwsTaskParams.builder()
                                      .encryptionDetails(Collections.emptyList())
                                      .awsConnector(awsConnectorDTO)
                                      .awsTaskType(AwsTaskType.LIST_S3_BUCKETS)
                                      .region("test")
                                      .build();

    ArgumentCaptor<AwsInternalConfig> internalConfigArgumentCaptor = ArgumentCaptor.forClass(AwsInternalConfig.class);
    ArgumentCaptor<String> regionArgumentCaptor = ArgumentCaptor.forClass(String.class);
    List<String> buckets = Arrays.asList("bucket1", "bucket2");
    doReturn(null).when(secretDecryptionService).decrypt(any(), any());
    doReturn(buckets)
        .when(awsApiHelperService)
        .listS3Buckets(internalConfigArgumentCaptor.capture(), regionArgumentCaptor.capture());

    DelegateResponseData responseData = taskHelper.getS3Buckets(awsTaskParams);
    assertThat(responseData).isNotNull();
    assertThat(responseData).isInstanceOf(AwsS3BucketResponse.class);
    AwsS3BucketResponse awsS3BucketResponse = (AwsS3BucketResponse) responseData;
    assertThat(awsS3BucketResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(awsS3BucketResponse.getBuckets()).isNotEmpty();
    assertThat(awsS3BucketResponse.getBuckets()).containsKeys("bucket1", "bucket2");
    assertThat(awsS3BucketResponse.getBuckets()).containsValues("bucket1", "bucket2");

    AwsInternalConfig awsInternalConfig = internalConfigArgumentCaptor.getValue();
    assertThat(awsInternalConfig.getAccessKey()).isEqualTo("test-access-key".toCharArray());
    assertThat(awsInternalConfig.getSecretKey()).isEqualTo("secret".toCharArray());

    String region = regionArgumentCaptor.getValue();
    assertThat(region).isEqualTo("test");

    verify(secretDecryptionService, times(1)).decrypt(any(), any());
    verify(awsApiHelperService, times(1)).listS3Buckets(any(), any());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldGetS3BucketsInheritFromDelegate() {
    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder()
            .credential(AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE).build())
            .build();
    AwsTaskParams awsTaskParams = AwsTaskParams.builder()
                                      .encryptionDetails(Collections.emptyList())
                                      .awsConnector(awsConnectorDTO)
                                      .awsTaskType(AwsTaskType.LIST_S3_BUCKETS)
                                      .region("test")
                                      .build();

    ArgumentCaptor<AwsInternalConfig> internalConfigArgumentCaptor = ArgumentCaptor.forClass(AwsInternalConfig.class);
    ArgumentCaptor<String> regionArgumentCaptor = ArgumentCaptor.forClass(String.class);
    List<String> buckets = Arrays.asList("bucket1", "bucket2");
    doReturn(buckets)
        .when(awsApiHelperService)
        .listS3Buckets(internalConfigArgumentCaptor.capture(), regionArgumentCaptor.capture());

    DelegateResponseData responseData = taskHelper.getS3Buckets(awsTaskParams);
    assertThat(responseData).isNotNull();
    assertThat(responseData).isInstanceOf(AwsS3BucketResponse.class);
    AwsS3BucketResponse awsS3BucketResponse = (AwsS3BucketResponse) responseData;
    assertThat(awsS3BucketResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(awsS3BucketResponse.getBuckets()).isNotEmpty();
    assertThat(awsS3BucketResponse.getBuckets()).containsKeys("bucket1", "bucket2");
    assertThat(awsS3BucketResponse.getBuckets()).containsValues("bucket1", "bucket2");

    AwsInternalConfig awsInternalConfig = internalConfigArgumentCaptor.getValue();
    assertThat(awsInternalConfig.isUseEc2IamCredentials()).isTrue();

    String region = regionArgumentCaptor.getValue();
    assertThat(region).isEqualTo("test");

    verify(secretDecryptionService, times(0)).decrypt(any(), any());
    verify(awsApiHelperService, times(1)).listS3Buckets(any(), any());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldGetNoS3BucketsIfEmptyList() {
    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder()
            .credential(
                AwsCredentialDTO.builder()
                    .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                    .config(AwsManualConfigSpecDTO.builder()
                                .accessKey("test-access-key")
                                .secretKeyRef(SecretRefData.builder().decryptedValue("secret".toCharArray()).build())
                                .build())
                    .build())
            .build();
    AwsTaskParams awsTaskParams = AwsTaskParams.builder()
                                      .encryptionDetails(Collections.emptyList())
                                      .awsConnector(awsConnectorDTO)
                                      .awsTaskType(AwsTaskType.LIST_S3_BUCKETS)
                                      .region("test")
                                      .build();

    ArgumentCaptor<AwsInternalConfig> internalConfigArgumentCaptor = ArgumentCaptor.forClass(AwsInternalConfig.class);
    ArgumentCaptor<String> regionArgumentCaptor = ArgumentCaptor.forClass(String.class);
    doReturn(null).when(secretDecryptionService).decrypt(any(), any());
    doReturn(Collections.emptyList())
        .when(awsApiHelperService)
        .listS3Buckets(internalConfigArgumentCaptor.capture(), regionArgumentCaptor.capture());

    DelegateResponseData responseData = taskHelper.getS3Buckets(awsTaskParams);
    assertThat(responseData).isNotNull();
    assertThat(responseData).isInstanceOf(AwsS3BucketResponse.class);
    AwsS3BucketResponse awsS3BucketResponse = (AwsS3BucketResponse) responseData;
    assertThat(awsS3BucketResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(awsS3BucketResponse.getBuckets()).isEmpty();

    AwsInternalConfig awsInternalConfig = internalConfigArgumentCaptor.getValue();
    assertThat(awsInternalConfig.getAccessKey()).isEqualTo("test-access-key".toCharArray());
    assertThat(awsInternalConfig.getSecretKey()).isEqualTo("secret".toCharArray());

    String region = regionArgumentCaptor.getValue();
    assertThat(region).isEqualTo("test");

    verify(secretDecryptionService, times(1)).decrypt(any(), any());
    verify(awsApiHelperService, times(1)).listS3Buckets(any(), any());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldGetNoS3BucketsIfNull() {
    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder()
            .credential(
                AwsCredentialDTO.builder()
                    .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                    .config(AwsManualConfigSpecDTO.builder()
                                .accessKey("test-access-key")
                                .secretKeyRef(SecretRefData.builder().decryptedValue("secret".toCharArray()).build())
                                .build())
                    .build())
            .build();
    AwsTaskParams awsTaskParams = AwsTaskParams.builder()
                                      .encryptionDetails(Collections.emptyList())
                                      .awsConnector(awsConnectorDTO)
                                      .awsTaskType(AwsTaskType.LIST_S3_BUCKETS)
                                      .region("test")
                                      .build();

    ArgumentCaptor<AwsInternalConfig> internalConfigArgumentCaptor = ArgumentCaptor.forClass(AwsInternalConfig.class);
    ArgumentCaptor<String> regionArgumentCaptor = ArgumentCaptor.forClass(String.class);
    doReturn(null).when(secretDecryptionService).decrypt(any(), any());
    doReturn(null)
        .when(awsApiHelperService)
        .listS3Buckets(internalConfigArgumentCaptor.capture(), regionArgumentCaptor.capture());

    DelegateResponseData responseData = taskHelper.getS3Buckets(awsTaskParams);
    assertThat(responseData).isNotNull();
    assertThat(responseData).isInstanceOf(AwsS3BucketResponse.class);
    AwsS3BucketResponse awsS3BucketResponse = (AwsS3BucketResponse) responseData;
    assertThat(awsS3BucketResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(awsS3BucketResponse.getBuckets()).isEmpty();

    AwsInternalConfig awsInternalConfig = internalConfigArgumentCaptor.getValue();
    assertThat(awsInternalConfig.getAccessKey()).isEqualTo("test-access-key".toCharArray());
    assertThat(awsInternalConfig.getSecretKey()).isEqualTo("secret".toCharArray());

    String region = regionArgumentCaptor.getValue();
    assertThat(region).isEqualTo("test");

    verify(secretDecryptionService, times(1)).decrypt(any(), any());
    verify(awsApiHelperService, times(1)).listS3Buckets(any(), any());
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testDecryptDTOsConnector() {
    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder()
            .credential(
                AwsCredentialDTO.builder()
                    .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                    .config(AwsManualConfigSpecDTO.builder()
                                .accessKey("test-access-key")
                                .secretKeyRef(SecretRefData.builder().decryptedValue("secret".toCharArray()).build())
                                .build())
                    .build())
            .build();

    AwsTaskParams awsTaskParams = AwsTaskParams.builder()
                                      .encryptionDetails(Collections.emptyList())
                                      .awsConnector(awsConnectorDTO)
                                      .awsTaskType(AwsTaskType.LIST_S3_BUCKETS)
                                      .region("test")
                                      .build();

    DelegateResponseData responseData = taskHelper.getS3Buckets(awsTaskParams);

    verify(secretDecryptionService, times(1)).decrypt(any(), any());
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testDecryptDTOsConnectorConfigNullScenario() {
    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder()
            .credential(
                AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS).config(null).build())
            .build();

    AwsTaskParams awsTaskParams = AwsTaskParams.builder()
                                      .encryptionDetails(Collections.emptyList())
                                      .awsConnector(awsConnectorDTO)
                                      .awsTaskType(AwsTaskType.LIST_S3_BUCKETS)
                                      .region("test")
                                      .build();

    try {
      DelegateResponseData responseData = taskHelper.getS3Buckets(awsTaskParams);
    } catch (InvalidArgumentsException ex) {
      // do nothing
    }

    verify(secretDecryptionService, times(0)).decrypt(any(), any());
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetBuilds() {
    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder()
            .credential(
                AwsCredentialDTO.builder()
                    .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                    .config(AwsManualConfigSpecDTO.builder()
                                .accessKey("test-access-key")
                                .secretKeyRef(SecretRefData.builder().decryptedValue("secret".toCharArray()).build())
                                .build())
                    .build())
            .build();

    AwsTaskParams awsTaskParams = AwsTaskParams.builder()
                                      .encryptionDetails(Collections.emptyList())
                                      .awsConnector(awsConnectorDTO)
                                      .awsTaskType(AwsTaskType.GET_BUILDS)
                                      .region("test")
                                      .build();

    ArgumentCaptor<AwsInternalConfig> internalConfigArgumentCaptor = ArgumentCaptor.forClass(AwsInternalConfig.class);
    ArgumentCaptor<String> regionArgumentCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> bucketArgumentCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> versionRegexArgumentCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Boolean> fetchObjectMetadata = ArgumentCaptor.forClass(Boolean.class);
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

    doReturn(null).when(secretDecryptionService).decrypt(any(), any());

    doReturn(builds)
        .when(awsApiHelperService)
        .listBuilds(internalConfigArgumentCaptor.capture(), regionArgumentCaptor.capture(),
            bucketArgumentCaptor.capture(), versionRegexArgumentCaptor.capture(), fetchObjectMetadata.capture());

    DelegateResponseData responseData = taskHelper.getBuilds(awsTaskParams);

    assertThat(responseData).isNotNull();
    assertThat(responseData).isInstanceOf(S3BuildsResponse.class);

    S3BuildsResponse s3BuildsResponse = (S3BuildsResponse) responseData;

    assertThat(s3BuildsResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(s3BuildsResponse.getBuilds()).isNotEmpty();

    assertThat(s3BuildsResponse.getBuilds().size()).isEqualTo(5);
    assertThat(s3BuildsResponse.getBuilds().get(0).getNumber()).isEqualTo("b1");
    assertThat(s3BuildsResponse.getBuilds().get(1).getUiDisplayName()).isEqualTo("Version# b2");
    assertThat(s3BuildsResponse.getBuilds().get(2).getUiDisplayName()).isEqualTo("Version# b3");
    assertThat(s3BuildsResponse.getBuilds().get(3).getUiDisplayName()).isEqualTo("Version# b4");
    assertThat(s3BuildsResponse.getBuilds().get(4).getNumber()).isEqualTo("b5");

    AwsInternalConfig awsInternalConfig = internalConfigArgumentCaptor.getValue();

    assertThat(awsInternalConfig.getAccessKey()).isEqualTo("test-access-key".toCharArray());
    assertThat(awsInternalConfig.getSecretKey()).isEqualTo("secret".toCharArray());

    String region = regionArgumentCaptor.getValue();

    assertThat(region).isEqualTo("test");

    verify(secretDecryptionService, times(1)).decrypt(any(), any());

    verify(awsApiHelperService, times(1)).listBuilds(any(), any(), any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetBuild() {
    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder()
            .credential(
                AwsCredentialDTO.builder()
                    .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                    .config(AwsManualConfigSpecDTO.builder()
                                .accessKey("test-access-key")
                                .secretKeyRef(SecretRefData.builder().decryptedValue("secret".toCharArray()).build())
                                .build())
                    .build())
            .build();

    AwsTaskParams awsTaskParams = AwsTaskParams.builder()
                                      .encryptionDetails(Collections.emptyList())
                                      .awsConnector(awsConnectorDTO)
                                      .awsTaskType(AwsTaskType.GET_BUILD)
                                      .filePath("b1")
                                      .region("test")
                                      .build();

    ArgumentCaptor<AwsInternalConfig> internalConfigArgumentCaptor = ArgumentCaptor.forClass(AwsInternalConfig.class);
    ArgumentCaptor<String> regionArgumentCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> bucketArgumentCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> filePathCaptor = ArgumentCaptor.forClass(String.class);

    BuildDetails build1 = new BuildDetails();
    build1.setNumber("b1");
    build1.setUiDisplayName("Version# b1");

    doReturn(null).when(secretDecryptionService).decrypt(any(), any());

    doReturn(build1)
        .when(awsApiHelperService)
        .getBuild(internalConfigArgumentCaptor.capture(), regionArgumentCaptor.capture(),
            bucketArgumentCaptor.capture(), filePathCaptor.capture());

    DelegateResponseData responseData = taskHelper.getBuild(awsTaskParams);

    assertThat(responseData).isNotNull();
    assertThat(responseData).isInstanceOf(S3BuildResponse.class);

    S3BuildResponse s3BuildResponse = (S3BuildResponse) responseData;

    assertThat(s3BuildResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(s3BuildResponse.getFilePath()).isNotEmpty();
    assertThat(s3BuildResponse.getFilePath()).isEqualTo("b1");

    AwsInternalConfig awsInternalConfig = internalConfigArgumentCaptor.getValue();

    assertThat(awsInternalConfig.getAccessKey()).isEqualTo("test-access-key".toCharArray());
    assertThat(awsInternalConfig.getSecretKey()).isEqualTo("secret".toCharArray());

    verify(secretDecryptionService, times(1)).decrypt(any(), any());

    verify(awsApiHelperService, times(1)).getBuild(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuild() {
    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder()
            .credential(
                AwsCredentialDTO.builder()
                    .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                    .config(AwsManualConfigSpecDTO.builder()
                                .accessKey("test-access-key")
                                .secretKeyRef(SecretRefData.builder().decryptedValue("secret".toCharArray()).build())
                                .build())
                    .build())
            .build();

    AwsTaskParams awsTaskParams = AwsTaskParams.builder()
                                      .encryptionDetails(Collections.emptyList())
                                      .awsConnector(awsConnectorDTO)
                                      .awsTaskType(AwsTaskType.LAST_SUCCESSFUL_BUILD)
                                      .filePathRegex("*")
                                      .region("test")
                                      .build();

    List<BuildDetails> builds = new ArrayList<>();

    BuildDetails build1 = new BuildDetails();
    build1.setNumber("b1");
    build1.setArtifactPath("b1");
    build1.setUiDisplayName("Version# b1");

    BuildDetails build2 = new BuildDetails();
    build2.setNumber("b2");
    build2.setArtifactPath("b2");
    build2.setUiDisplayName("Version# b2");

    BuildDetails build3 = new BuildDetails();
    build3.setNumber("b3");
    build3.setArtifactPath("b3");
    build3.setUiDisplayName("Version# b3");

    BuildDetails build4 = new BuildDetails();
    build4.setNumber("b4");
    build4.setArtifactPath("b4");
    build4.setUiDisplayName("Version# b4");

    BuildDetails build5 = new BuildDetails();
    build5.setNumber("b5");
    build5.setArtifactPath("b5");
    build5.setUiDisplayName("Version# b5");

    builds.add(build1);
    builds.add(build2);
    builds.add(build3);
    builds.add(build4);
    builds.add(build5);

    ArgumentCaptor<AwsInternalConfig> internalConfigArgumentCaptor = ArgumentCaptor.forClass(AwsInternalConfig.class);
    ArgumentCaptor<String> regionArgumentCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> bucketArgumentCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> filePathCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Boolean> fetchObjectMetadata = ArgumentCaptor.forClass(Boolean.class);

    doReturn(null).when(secretDecryptionService).decrypt(any(), any());

    doReturn(builds)
        .when(awsApiHelperService)
        .listBuilds(internalConfigArgumentCaptor.capture(), regionArgumentCaptor.capture(),
            bucketArgumentCaptor.capture(), filePathCaptor.capture(), fetchObjectMetadata.capture());

    DelegateResponseData responseData = taskHelper.getLastSuccessfulBuild(awsTaskParams);

    assertThat(responseData).isNotNull();
    assertThat(responseData).isInstanceOf(S3BuildResponse.class);

    S3BuildResponse s3BuildResponse = (S3BuildResponse) responseData;

    assertThat(s3BuildResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(s3BuildResponse.getFilePath()).isNotEmpty();
    assertThat(s3BuildResponse.getFilePath()).isEqualTo("b5");

    AwsInternalConfig awsInternalConfig = internalConfigArgumentCaptor.getValue();

    assertThat(awsInternalConfig.getAccessKey()).isEqualTo("test-access-key".toCharArray());
    assertThat(awsInternalConfig.getSecretKey()).isEqualTo("secret".toCharArray());

    verify(secretDecryptionService, times(1)).decrypt(any(), any());

    verify(awsApiHelperService, times(1)).listBuilds(any(), any(), any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testPutAuditBatchToBucket() {
    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder()
            .credential(
                AwsCredentialDTO.builder()
                    .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                    .config(AwsManualConfigSpecDTO.builder()
                                .accessKey("test-access-key")
                                .secretKeyRef(SecretRefData.builder().decryptedValue("secret".toCharArray()).build())
                                .build())
                    .build())
            .build();

    AwsPutAuditBatchToBucketTaskParamsRequest awsTaskParams =
        AwsPutAuditBatchToBucketTaskParamsRequest.builder()
            .awsTaskType(AwsTaskType.PUT_AUDIT_BATCH_TO_BUCKET)
            .awsConnector(awsConnectorDTO)
            .encryptionDetails(Collections.emptyList())
            .region("region")
            .bucketName("bucketName")
            .auditBatch(AuditBatchDTO.builder()
                            .batchId("123")
                            .accountIdentifier("accID123")
                            .streamingDestinationIdentifier("polID123")
                            .startTime(System.currentTimeMillis())
                            .endTime(System.currentTimeMillis())
                            .numberOfRecords(10)
                            .outgoingAuditMessages(Arrays.asList(OutgoingAuditMessage.builder()
                                                                     .auditEventId("audId123")
                                                                     .auditAction("CREATE")
                                                                     .auditModule(CORE)
                                                                     .build()))
                            .status(AuditBatchDTO.BatchStatus.builder()
                                        .state(AuditBatchDTO.BatchState.SUCCESS)
                                        .message("none")
                                        .build())
                            .retryCount(5)
                            .build())
            .build();

    ArgumentCaptor<AwsInternalConfig> internalConfigArgumentCaptor = ArgumentCaptor.forClass(AwsInternalConfig.class);
    ArgumentCaptor<String> regionArgumentCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> bucketNameArgumentCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<AuditBatchDTO> auditBatchArgumentCaptor = ArgumentCaptor.forClass(AuditBatchDTO.class);

    PutObjectResultResponse putObjectResultResponse =
        PutObjectResultResponse.builder().contentMd5("MD5").eTag("eTag").build();

    doReturn(null).when(secretDecryptionService).decrypt(any(), any());
    doReturn(putObjectResultResponse)
        .when(awsApiHelperService)
        .putAuditBatchToBucket(internalConfigArgumentCaptor.capture(), regionArgumentCaptor.capture(),
            bucketNameArgumentCaptor.capture(), auditBatchArgumentCaptor.capture());

    DelegateResponseData responseData = taskHelper.putAuditBatchToBucket(awsTaskParams);
    assertThat(responseData).isNotNull();
    assertThat(responseData).isInstanceOf(AwsPutAuditBatchToBucketTaskResponse.class);
    AwsPutAuditBatchToBucketTaskResponse awsPutAuditBatchToBucketTaskResponse =
        (AwsPutAuditBatchToBucketTaskResponse) responseData;
    assertThat(awsPutAuditBatchToBucketTaskResponse.getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(awsPutAuditBatchToBucketTaskResponse.getPutObjectResultResponse()).isNotNull();
    assertThat(awsPutAuditBatchToBucketTaskResponse.getPutObjectResultResponse().getContentMd5()).isEqualTo("MD5");
    assertThat(awsPutAuditBatchToBucketTaskResponse.getPutObjectResultResponse().getETag()).isEqualTo("eTag");

    AwsInternalConfig awsInternalConfig = internalConfigArgumentCaptor.getValue();
    assertThat(awsInternalConfig.getAccessKey()).isEqualTo("test-access-key".toCharArray());
    assertThat(awsInternalConfig.getSecretKey()).isEqualTo("secret".toCharArray());

    String region = regionArgumentCaptor.getValue();
    assertThat(region).isEqualTo("region");
    String bucketName = bucketNameArgumentCaptor.getValue();
    assertThat(bucketName).isEqualTo("bucketName");

    verify(secretDecryptionService, times(1)).decrypt(any(), any());
    verify(awsApiHelperService, times(1)).putAuditBatchToBucket(any(), any(), any(), any());

    doThrow(new AmazonServiceException("Exception while writing to S3 bucket"))
        .when(awsApiHelperService)
        .putAuditBatchToBucket(internalConfigArgumentCaptor.capture(), regionArgumentCaptor.capture(),
            bucketNameArgumentCaptor.capture(), auditBatchArgumentCaptor.capture());

    responseData = taskHelper.putAuditBatchToBucket(awsTaskParams);
    assertThat(responseData).isNotNull();
    assertThat(responseData).isInstanceOf(AwsPutAuditBatchToBucketTaskResponse.class);
    awsPutAuditBatchToBucketTaskResponse = (AwsPutAuditBatchToBucketTaskResponse) responseData;
    assertThat(awsPutAuditBatchToBucketTaskResponse.getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.FAILURE);
  }
}
