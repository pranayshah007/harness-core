/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.s3;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDC)
public class S3ArtifactTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

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
}
