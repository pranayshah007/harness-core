/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.artifact.ArtifactConstants.ARTIFACT_REPO_BASE_DIR;
import static io.harness.rule.OwnerRule.ACASIAN;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.beans.DecryptableEntity;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.ssh.artifact.ArtifactoryArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.GoogleCloudStorageArtifactDelegateConfig;
import io.harness.delegate.task.ssh.exception.SshExceptionConstants;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.HintException;
import io.harness.exception.runtime.SecretNotFoundRuntimeException;
import io.harness.googlecloudstorage.GcsHelperService;
import io.harness.googlecloudstorage.GcsInternalConfig;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class GcsArtifactCommandUnitHandlerTest extends CategoryTest {
  @Mock LogCallback logCallback;
  @Mock GcsHelperService gcsHelperService;
  @Mock DecryptionHelper decryptionHelper;
  @Mock DecryptableEntity decryptableEntity;

  final String fileContent = "test";
  InputStream fileInputStream = new ByteArrayInputStream(fileContent.getBytes(Charset.defaultCharset()));

  @Inject @InjectMocks GcsArtifactCommandUnitHandler handler;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    doReturn(decryptableEntity).when(decryptionHelper).decrypt(any(), anyList());
  }

  @After
  public void cleanup() {
    File cacheDir = new File(ARTIFACT_REPO_BASE_DIR);
    if (cacheDir != null) {
      File[] files = cacheDir.listFiles();
      if (files != null && files.length > 0) {
        cacheDir.delete();
      }
    }
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testDownloadGcsArtifact() throws Exception {
    SshExecutorFactoryContext context = getContext();
    doReturn(fileInputStream).when(gcsHelperService).downloadObject(any(), any());

    InputStream result = handler.downloadFromRemoteRepo(context, logCallback);

    String resultText = IOUtils.toString(result, Charset.defaultCharset());
    assertThat(result).isNotNull();
    assertThat(resultText).isEqualTo(fileContent);
    assertArtifactMetadata(context.getArtifactMetadata());

    ArgumentCaptor<GcsInternalConfig> gcsInternalConfigArgumentCaptor =
        ArgumentCaptor.forClass(GcsInternalConfig.class);
    ArgumentCaptor<String> fileNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(gcsHelperService, times(1))
        .downloadObject(gcsInternalConfigArgumentCaptor.capture(), fileNameCaptor.capture());

    assertGcpInternalConfig(gcsInternalConfigArgumentCaptor.getValue());
    assertThat(fileNameCaptor.getValue()).isEqualTo("test/path/to/artifact");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGetArtifactSize() throws Exception {
    SshExecutorFactoryContext context = getContext();
    doReturn(Long.valueOf(fileContent.length())).when(gcsHelperService).getObjectSize(any(), any());

    Long result = handler.getArtifactSize(context, logCallback);

    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(Long.valueOf(fileContent.length()));
    assertArtifactMetadata(context.getArtifactMetadata());

    ArgumentCaptor<GcsInternalConfig> gcsInternalConfigArgumentCaptor =
        ArgumentCaptor.forClass(GcsInternalConfig.class);
    ArgumentCaptor<String> fileNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(gcsHelperService, times(1))
        .getObjectSize(gcsInternalConfigArgumentCaptor.capture(), fileNameCaptor.capture());

    assertGcpInternalConfig(gcsInternalConfigArgumentCaptor.getValue());
    assertThat(fileNameCaptor.getValue()).isEqualTo("test/path/to/artifact");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testDownloadGcsArtifactBadDelegateConfig() {
    SshExecutorFactoryContext context = SshExecutorFactoryContext.builder()
                                            .artifactDelegateConfig(ArtifactoryArtifactDelegateConfig.builder().build())
                                            .build();

    assertThatThrownBy(() -> handler.downloadFromRemoteRepo(context, logCallback))
        .isInstanceOf(HintException.class)
        .hasMessage(SshExceptionConstants.BAD_ARTIFACT_TYPE)
        .getCause()
        .hasMessage(format(
            SshExceptionConstants.BAD_ARTIFACT_TYPE_HINT, ArtifactoryArtifactDelegateConfig.class.getSimpleName()));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testDownloadGcsArtifactMissingArtifactPath() {
    SshExecutorFactoryContext context =
        SshExecutorFactoryContext.builder()
            .artifactDelegateConfig(GoogleCloudStorageArtifactDelegateConfig.builder().build())
            .build();

    assertThatThrownBy(() -> handler.downloadFromRemoteRepo(context, logCallback))
        .isInstanceOf(HintException.class)
        .hasMessage(SshExceptionConstants.GCS_INVALID_ARTIFACT_PATH_HINT)
        .getCause()
        .hasMessage(format(SshExceptionConstants.GCS_INVALID_ARTIFACT_PATH_EXPLANATION,
            ArtifactoryArtifactDelegateConfig.class.getSimpleName()));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGetGcsArtifactSizeMissingArtifactPath() {
    SshExecutorFactoryContext context =
        SshExecutorFactoryContext.builder()
            .artifactDelegateConfig(GoogleCloudStorageArtifactDelegateConfig.builder().build())
            .build();

    assertThatThrownBy(() -> handler.getArtifactSize(context, logCallback))
        .isInstanceOf(HintException.class)
        .hasMessage(SshExceptionConstants.GCS_INVALID_ARTIFACT_PATH_HINT)
        .getCause()
        .hasMessage(format(SshExceptionConstants.GCS_INVALID_ARTIFACT_PATH_EXPLANATION,
            ArtifactoryArtifactDelegateConfig.class.getSimpleName()));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testDownloadGcsArtifactThrowsException() throws Exception {
    SshExecutorFactoryContext context = getContext();
    doThrow(new RuntimeException("Could not find object at specified path"))
        .when(gcsHelperService)
        .downloadObject(any(), any());

    assertThatThrownBy(() -> handler.downloadFromRemoteRepo(context, logCallback))
        .isInstanceOf(HintException.class)
        .hasMessage(SshExceptionConstants.GCS_ARTIFACT_DOWNLOAD_HINT)
        .getCause()
        .hasMessage(format(
            SshExceptionConstants.GCS_ARTIFACT_DOWNLOAD_EXPLANATION, "test/path/to/artifact", "bucket", "project"));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGetGcsArtifactSizeThrowsException() throws Exception {
    SshExecutorFactoryContext context = getContext();
    doThrow(new RuntimeException("Could not find object at specified path"))
        .when(gcsHelperService)
        .getObjectSize(any(), any());

    assertThatThrownBy(() -> handler.getArtifactSize(context, logCallback))
        .isInstanceOf(HintException.class)
        .hasMessage(SshExceptionConstants.GCS_ARTIFACT_CALCULATE_SIZE_FAILED_HINT)
        .getCause()
        .hasMessage(format(SshExceptionConstants.GCS_ARTIFACT_CALCULATE_SIZE_FAILED_EXPLANATION,
            "test/path/to/artifact", "bucket", "project"));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testDownloadGcsArtifactInvalidCredentials() {
    GcpConnectorDTO gcpConnectorDto = GcpConnectorDTO.builder()
                                          .credential(GcpConnectorCredentialDTO.builder()
                                                          .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                                                          .config(GcpManualDetailsDTO.builder().build())
                                                          .build())
                                          .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().connectorConfig(gcpConnectorDto).build();

    SshExecutorFactoryContext context = SshExecutorFactoryContext.builder()
                                            .artifactDelegateConfig(GoogleCloudStorageArtifactDelegateConfig.builder()
                                                                        .filePath("test")
                                                                        .bucket("bucket")
                                                                        .connectorDTO(connectorInfoDTO)
                                                                        .build())
                                            .build();

    assertThatThrownBy(() -> handler.downloadFromRemoteRepo(context, logCallback))
        .isInstanceOf(SecretNotFoundRuntimeException.class)
        .hasMessage("Invalid GCS credentials provided, failed to locate secret ref.");

    gcpConnectorDto = GcpConnectorDTO.builder()
                          .credential(GcpConnectorCredentialDTO.builder()
                                          .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                                          .config(GcpManualDetailsDTO.builder()
                                                      .secretKeyRef(SecretRefData.builder()
                                                                        .decryptedValue(null)
                                                                        .identifier("testsecret")
                                                                        .scope(Scope.ACCOUNT)
                                                                        .build())
                                                      .build())
                                          .build())
                          .build();
    connectorInfoDTO = ConnectorInfoDTO.builder().identifier("testconnector").connectorConfig(gcpConnectorDto).build();

    SshExecutorFactoryContext context2 = SshExecutorFactoryContext.builder()
                                             .artifactDelegateConfig(GoogleCloudStorageArtifactDelegateConfig.builder()
                                                                         .filePath("test")
                                                                         .bucket("bucket")
                                                                         .connectorDTO(connectorInfoDTO)
                                                                         .build())
                                             .build();

    assertThatThrownBy(() -> handler.downloadFromRemoteRepo(context2, logCallback))
        .isInstanceOf(SecretNotFoundRuntimeException.class)
        .hasMessage("Could not find secret testsecret under the scope of current ACCOUNT");
  }

  private SshExecutorFactoryContext getContext() {
    GcpConnectorDTO gcpConnectorDto =
        GcpConnectorDTO.builder()
            .credential(
                GcpConnectorCredentialDTO.builder()
                    .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                    .config(
                        GcpManualDetailsDTO.builder()
                            .secretKeyRef(SecretRefData.builder().decryptedValue("keepitsecret".toCharArray()).build())
                            .build())
                    .build())
            .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().connectorConfig(gcpConnectorDto).build();

    return SshExecutorFactoryContext.builder()
        .artifactDelegateConfig(GoogleCloudStorageArtifactDelegateConfig.builder()
                                    .identifier("gcp")
                                    .bucket("bucket")
                                    .project("project")
                                    .filePath("test/path/to/artifact")
                                    .connectorDTO(connectorInfoDTO)
                                    .build())
        .build();
  }

  private void assertGcpInternalConfig(GcsInternalConfig gcsInternalConfig) {
    assertThat(gcsInternalConfig).isNotNull();
    assertThat(gcsInternalConfig.getProject()).isEqualTo("project");
    assertThat(gcsInternalConfig.getBucket()).isEqualTo("bucket");
  }

  private void assertArtifactMetadata(Map<String, String> artifactMetadata) {
    assertThat(artifactMetadata).containsKey(ArtifactMetadataKeys.artifactPath);
    assertThat(artifactMetadata).containsKey(ArtifactMetadataKeys.artifactName);
    assertThat(artifactMetadata.get(ArtifactMetadataKeys.artifactPath)).isEqualTo("bucket/test/path/to/artifact");
    assertThat(artifactMetadata.get(ArtifactMetadataKeys.artifactName)).isEqualTo("bucket/test/path/to/artifact");
  }
}