/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.s3;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.S3ArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.impl.AwsApiHelperService;

import com.google.common.collect.ImmutableMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDC)
public class S3ArtifactTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock AwsApiHelperService awsApiHelperService;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;

  @InjectMocks S3ArtifactTaskHandler s3ArtifactTaskHandler;

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildWithEmptyBucketName() {
    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE).build();

    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder().credential(awsCredentialDTO).executeOnDelegate(true).build();

    S3ArtifactDelegateRequest s3ArtifactDelegateRequest = S3ArtifactDelegateRequest.builder()
                                                              .awsConnectorDTO(awsConnectorDTO)
                                                              .connectorRef("ref")
                                                              .bucketName("")
                                                              .filePathRegex("")
                                                              .filePath("")
                                                              .region("us-east-1")
                                                              .encryptedDataDetails(null)
                                                              .sourceType(ArtifactSourceType.AMAZONS3)
                                                              .build();

    assertThatThrownBy(() -> s3ArtifactTaskHandler.getLastSuccessfulBuild(s3ArtifactDelegateRequest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Please specify the bucket for the S3 artifact source.");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildWithEmptyFilePathRegex() {
    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE).build();

    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder().credential(awsCredentialDTO).executeOnDelegate(true).build();

    S3ArtifactDelegateRequest s3ArtifactDelegateRequest = S3ArtifactDelegateRequest.builder()
                                                              .awsConnectorDTO(awsConnectorDTO)
                                                              .connectorRef("ref")
                                                              .bucketName("abc")
                                                              .filePathRegex("")
                                                              .filePath("")
                                                              .region("us-east-1")
                                                              .encryptedDataDetails(null)
                                                              .sourceType(ArtifactSourceType.AMAZONS3)
                                                              .build();

    assertThatThrownBy(() -> s3ArtifactTaskHandler.getLastSuccessfulBuild(s3ArtifactDelegateRequest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Please specify a filePath or filePathRegex before executing the pipeline.");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildWithFixedFilePath() {
    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE).build();

    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder().credential(awsCredentialDTO).executeOnDelegate(true).build();

    S3ArtifactDelegateRequest s3ArtifactDelegateRequest = S3ArtifactDelegateRequest.builder()
                                                              .awsConnectorDTO(awsConnectorDTO)
                                                              .connectorRef("ref")
                                                              .bucketName("abc")
                                                              .filePathRegex("")
                                                              .filePath("filepath/fixed:4edcscsvsdvsd")
                                                              .region("us-east-1")
                                                              .encryptedDataDetails(null)
                                                              .sourceType(ArtifactSourceType.AMAZONS3)
                                                              .build();

    BuildDetails buildDetails = new BuildDetails();
    buildDetails.setArtifactPath("filepath/fixed");
    buildDetails.setNumber("filepath/fixed");
    buildDetails.setMetadata(ImmutableMap.<String, String>builder()
                                 .put(ArtifactMetadataKeys.url, "https://s3.amazonaws.com/abc/filepath/fixed")
                                 .put(ArtifactMetadataKeys.key, "filepath/fixed")
                                 .put(ArtifactMetadataKeys.artifactPath, "filepath/fixed")
                                 .build());
    Mockito
        .when(
            awsApiHelperService.getBuild(any(), Mockito.eq("us-east-1"), eq("abc"), eq("filepath/fixed:4edcscsvsdvsd")))
        .thenReturn(buildDetails);
    ArtifactTaskExecutionResponse response = s3ArtifactTaskHandler.getLastSuccessfulBuild(s3ArtifactDelegateRequest);
    assertThat(response).isNotNull();
    assertThat(response.getArtifactDelegateResponses()).isNotEmpty();
    assertThat(response.getArtifactDelegateResponses().get(0)).isNotNull();
    S3ArtifactDelegateResponse s3Response = (S3ArtifactDelegateResponse) response.getArtifactDelegateResponses().get(0);
    assertThat(s3Response.getBucketName()).isEqualTo("abc");
    assertThat(s3Response.getFilePathRegex()).isEmpty();
    assertThat(s3Response.getFilePath()).isEqualTo("filepath/fixed");
    assertThat(s3Response.getSourceType()).isEqualTo(ArtifactSourceType.AMAZONS3);
    ArtifactBuildDetailsNG buildDetailsNG = s3Response.getBuildDetails();
    assertThat(buildDetailsNG.getMetadata()).isNotEmpty();
    assertThat(buildDetailsNG.getMetadata().get(ArtifactMetadataKeys.url))
        .isEqualTo("https://s3.amazonaws.com/abc/filepath/fixed");
    assertThat(buildDetailsNG.getMetadata().get(ArtifactMetadataKeys.artifactPath)).isEqualTo("filepath/fixed");
    assertThat(buildDetailsNG.getMetadata().get(ArtifactMetadataKeys.key)).isEqualTo("filepath/fixed");
  }
}
