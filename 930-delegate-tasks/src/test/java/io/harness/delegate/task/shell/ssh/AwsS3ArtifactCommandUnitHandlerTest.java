/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.aws.AwsS3DelegateTaskHelper;
import io.harness.delegate.task.ssh.artifact.ArtifactoryArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.AwsS3ArtifactDelegateConfig;
import io.harness.exception.HintException;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import software.wings.service.impl.AwsApiHelperService;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class AwsS3ArtifactCommandUnitHandlerTest extends CategoryTest {
  @Mock LogCallback logCallback;

  @Mock private AwsNgConfigMapper awsNgConfigMapper;
  @Mock private AwsS3DelegateTaskHelper awsS3DelegateTaskHelper;
  @Mock private AwsApiHelperService awsApiHelperService;

  @Mock S3Object s3Object;

  final String fileContent = "test";
  InputStream is = new ByteArrayInputStream(fileContent.getBytes(Charset.defaultCharset()));

  @InjectMocks @Spy AwsS3ArtifactCommandUnitHandler handler;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    doNothing().when(awsS3DelegateTaskHelper).decryptRequestDTOs(any(), anyList());
    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testShouldDownloadArtifactRemote() throws IOException {
    SshExecutorFactoryContext context = getContext();
    doReturn(s3Object).when(awsApiHelperService).getObjectFromS3(any(), anyString(), anyString(), anyString());
    doReturn(new S3ObjectInputStream(is, null)).when(s3Object).getObjectContent();

    InputStream result = handler.downloadFromRemoteRepo(context, logCallback);

    String resultText = IOUtils.toString(result, Charset.defaultCharset());
    assertThat(result).isNotNull();
    assertThat(resultText).isEqualTo(fileContent);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testShouldDownloadArtifactRemoteFails() {
    SshExecutorFactoryContext context = getContext();
    doThrow(new RuntimeException("oopps"))
        .when(awsApiHelperService)
        .getObjectFromS3(any(), anyString(), anyString(), anyString());
    assertThatThrownBy(() -> handler.downloadFromRemoteRepo(context, logCallback)).isInstanceOf(HintException.class);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testShouldDownloadArtifactRemoteReturnsNull() throws IOException {
    SshExecutorFactoryContext context = SshExecutorFactoryContext.builder()
                                            .artifactDelegateConfig(ArtifactoryArtifactDelegateConfig.builder().build())
                                            .build();
    InputStream result = handler.downloadFromRemoteRepo(context, logCallback);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetArtifactSize() throws IOException {
    SshExecutorFactoryContext context = getContext();
    doReturn(s3Object).when(awsApiHelperService).getObjectFromS3(any(), anyString(), anyString(), anyString());

    ObjectMetadata objectMetadata = new ObjectMetadata();
    objectMetadata.setHeader("Content-Length", 100L);
    doReturn(objectMetadata).when(s3Object).getObjectMetadata();

    Long result = handler.getArtifactSize(context, logCallback);

    assertThat(result).isEqualTo(100L);
    assertArtifactMetadata(context.getArtifactMetadata());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetArtifactSizeFails() {
    SshExecutorFactoryContext context = getContext();
    doThrow(new RuntimeException("oopps"))
        .when(awsApiHelperService)
        .getObjectFromS3(any(), anyString(), anyString(), anyString());
    assertThatThrownBy(() -> handler.getArtifactSize(context, logCallback)).isInstanceOf(HintException.class);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetArtifactSizeReturnsNull() {
    SshExecutorFactoryContext context = SshExecutorFactoryContext.builder()
                                            .artifactDelegateConfig(ArtifactoryArtifactDelegateConfig.builder().build())
                                            .build();
    Long result = handler.getArtifactSize(context, logCallback);
    assertThat(result).isEqualTo(0L);
  }

  private void assertArtifactMetadata(Map<String, String> artifactMetadata) {
    assertThat(artifactMetadata.get(ArtifactMetadataKeys.artifactPath)).isEqualTo("/path/to/artifact");
    assertThat(artifactMetadata.get(ArtifactMetadataKeys.artifactName)).isEqualTo("artifact");
  }

  private SshExecutorFactoryContext getContext() {
    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder()
                                          .credential(AwsCredentialDTO.builder()
                                                          .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                                                          .config(AwsManualConfigSpecDTO.builder().build())
                                                          .build())
                                          .build();

    return SshExecutorFactoryContext.builder()
        .artifactDelegateConfig(AwsS3ArtifactDelegateConfig.builder()
                                    .identifier("testIdentifier")
                                    .bucketName("bucketName")
                                    .accountId("id")
                                    .artifactPath("/path/to/artifact")
                                    .region("region")
                                    .awsConnector(awsConnectorDTO)
                                    .build())
        .build();
  }
}
