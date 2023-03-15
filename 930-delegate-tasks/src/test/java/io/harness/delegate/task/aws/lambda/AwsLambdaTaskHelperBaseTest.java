/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.lambda;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ROHITKARELIA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.exception.HintException;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.service.impl.AwsApiHelperService;

import com.amazonaws.services.s3.model.S3Object;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import software.amazon.awssdk.core.SdkBytes;

@RunWith(PowerMockRunner.class)
@PrepareForTest(AwsLambdaTaskHelperBase.class)
@OwnedBy(CDP)
public class AwsLambdaTaskHelperBaseTest extends CategoryTest {
  @InjectMocks AwsLambdaTaskHelperBase awsLambdaTaskHelperBase;

  @Mock private AwsNgConfigMapper awsNgConfigMapper;

  @Mock private AwsApiHelperService awsApiHelperService;

  @Mock private SecretDecryptionService secretDecryptionService;

  @Mock private LogCallback logCallback;

  private final AwsConnectorDTO awsConnectorDTO =
      AwsConnectorDTO.builder()
          .credential(AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS).build())
          .delegateSelectors(ImmutableSet.of("delegate1"))
          .build();
  private final ConnectorInfoDTO awsConnectorInfoDTO =
      ConnectorInfoDTO.builder().connectorType(ConnectorType.AWS).connectorConfig(awsConnectorDTO).build();

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testDownloadArtifactFromS3BucketAndPrepareSdkBytesEmptyArtifactFilePath() {
    AwsLambdaS3ArtifactConfig s3ArtifactConfig = AwsLambdaS3ArtifactConfig.builder().build();

    assertThatThrownBy(
        () -> awsLambdaTaskHelperBase.downloadArtifactFromS3BucketAndPrepareSdkBytes(s3ArtifactConfig, logCallback))
        .isInstanceOf(HintException.class);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testDownloadArtifactFromS3BucketAndPrepareSdkBytes() throws IOException {
    AwsLambdaS3ArtifactConfig s3ArtifactConfig = AwsLambdaS3ArtifactConfig.builder()
                                                     .bucketName("bucketName")
                                                     .filePath("artifactFile")
                                                     .region("us-east-1")
                                                     .connectorDTO(awsConnectorInfoDTO)
                                                     .build();

    InputStream inputStream = IOUtils.toInputStream("hello-world", "UTF-8");
    String key1 = "bucketName/hello-world.zip";

    S3Object s3Object = new S3Object();
    s3Object.setKey(key1);
    s3Object.setObjectContent(inputStream);

    doReturn(s3Object).when(awsApiHelperService).getObjectFromS3(any(), any(), any(), any());
    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());

    SdkBytes sdkBytes =
        awsLambdaTaskHelperBase.downloadArtifactFromS3BucketAndPrepareSdkBytes(s3ArtifactConfig, logCallback);
    assertThat(sdkBytes).isNotNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testDownloadArtifactFromS3BucketAndPrepareSdkBytesFailure1() throws IOException {
    AwsLambdaS3ArtifactConfig s3ArtifactConfig = AwsLambdaS3ArtifactConfig.builder()
                                                     .bucketName("bucketName")
                                                     .filePath("artifactFile")
                                                     .region("us-east-1")
                                                     .connectorDTO(awsConnectorInfoDTO)
                                                     .build();

    InputStream inputStream = IOUtils.toInputStream("hello-world", "UTF-8");
    String key1 = "bucketName/hello-world.zip";

    S3Object s3Object = new S3Object();
    s3Object.setKey(key1);

    doReturn(s3Object).when(awsApiHelperService).getObjectFromS3(any(), any(), any(), any());
    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());

    assertThatThrownBy(
        () -> awsLambdaTaskHelperBase.downloadArtifactFromS3BucketAndPrepareSdkBytes(s3ArtifactConfig, logCallback))
        .isInstanceOf(HintException.class);
  }
}