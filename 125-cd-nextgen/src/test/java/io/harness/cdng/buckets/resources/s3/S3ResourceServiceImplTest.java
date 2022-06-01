/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.buckets.resources.s3;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
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
import io.harness.exception.BucketServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.BaseNGAccess;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.TaskType;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
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
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldGetBucketsFailure() {
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
}
