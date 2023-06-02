/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.artifact.ArtifactConstants.ARTIFACT_REPO_BASE_DIR;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.VITALIE;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactConstants;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.beans.DecryptableEntity;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.ssh.artifact.ArtifactoryArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactType;
import io.harness.delegate.task.ssh.exception.SshExceptionConstants;
import io.harness.encryption.SecretRefData;
import io.harness.exception.HintException;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.common.io.Files;
import com.google.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.apache.commons.io.FileUtils;
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
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class ArtifactoryCommandUnitHandlerTest extends CategoryTest {
  @Mock SecretDecryptionService secretDecryptionService;
  ArtifactoryRequestMapper artifactoryRequestMapper = new ArtifactoryRequestMapper();
  @Mock ArtifactoryNgService artifactoryNgService;
  @Mock LogCallback logCallback;
  @Mock DecryptableEntity decryptableEntity;

  final String fileContent = "test";
  InputStream is = new ByteArrayInputStream(fileContent.getBytes(Charset.defaultCharset()));

  @Inject
  @InjectMocks
  @Spy
  ArtifactoryCommandUnitHandler handler =
      new ArtifactoryCommandUnitHandler(secretDecryptionService, artifactoryRequestMapper, artifactoryNgService);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    doReturn(decryptableEntity).when(secretDecryptionService).decrypt(any(), anyList());
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
  public void testShouldDownloadArtifactRemote() throws IOException {
    SshExecutorFactoryContext context = getContext();
    doReturn(is).when(artifactoryNgService).downloadArtifacts(any(), any(), any(), any(), any());
    InputStream result = handler.downloadFromRemoteRepo(context, logCallback);

    String resultText = IOUtils.toString(result, Charset.defaultCharset());
    assertThat(result).isNotNull();
    assertThat(resultText).isEqualTo(fileContent);
    assertArtifactMetadata(context.getArtifactMetadata());

    ArgumentCaptor<ArtifactoryConfigRequest> configRequestCaptor =
        ArgumentCaptor.forClass(ArtifactoryConfigRequest.class);
    verify(artifactoryNgService, times(1))
        .downloadArtifacts(configRequestCaptor.capture(), eq("test"), eq(context.getArtifactMetadata()),
            eq(ArtifactMetadataKeys.artifactPath), eq(ArtifactMetadataKeys.artifactName));
    assertArtifactoryConfigRequest(configRequestCaptor.getValue());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldDownloadArtifactLocal() throws IOException, ExecutionException {
    SshExecutorFactoryContext context = getContext();
    context.getArtifactMetadata().put(ArtifactMetadataKeys.artifactName, "artifact-file.jar");
    doReturn(is).when(artifactoryNgService).downloadArtifacts(any(), any(), any(), any(), any());
    handler.downloadToLocal(context, logCallback);

    String text = Files.toString(new File(ARTIFACT_REPO_BASE_DIR + "_testIdentifier"
                                     + "-"
                                     + "artifact-file.jar"),
        Charset.defaultCharset());
    assertThat(text).isEqualTo(fileContent);
    assertArtifactMetadata(context.getArtifactMetadata());

    ArgumentCaptor<ArtifactoryConfigRequest> configRequestCaptor =
        ArgumentCaptor.forClass(ArtifactoryConfigRequest.class);
    verify(artifactoryNgService, times(1))
        .downloadArtifacts(configRequestCaptor.capture(), eq("test"), eq(context.getArtifactMetadata()),
            eq(ArtifactMetadataKeys.artifactPath), eq(ArtifactMetadataKeys.artifactName));
    assertArtifactoryConfigRequest(configRequestCaptor.getValue());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testDownloadArtifactLocalExceedsLimit() throws IOException, ExecutionException {
    SshExecutorFactoryContext context = getContext();
    context.getArtifactMetadata().put(ArtifactMetadataKeys.artifactName, "artifact-file.jar");
    context.getArtifactMetadata().put(
        ArtifactMetadataKeys.artifactFileSize, String.valueOf(ArtifactConstants.ARTIFACT_FILE_SIZE_LIMIT + 1L));

    doReturn(is).when(artifactoryNgService).downloadArtifacts(any(), any(), any(), any(), any());

    assertThatThrownBy(() -> handler.downloadToLocal(context, logCallback)).isInstanceOf(HintException.class);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testDownloadArtifactLocalNullArtifact() throws IOException, ExecutionException {
    SshExecutorFactoryContext context = getContext();
    context.getArtifactMetadata().put(ArtifactMetadataKeys.artifactName, "artifact-file.jar");

    doReturn(is).when(artifactoryNgService).downloadArtifacts(any(), any(), any(), any(), any());
    doReturn(null).when(handler).downloadFromRemoteRepo(any(), any());

    assertThatThrownBy(() -> handler.downloadToLocal(context, logCallback)).isInstanceOf(HintException.class);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldCleanCachedArtifacts() throws IOException {
    File cacheDir = new File(ARTIFACT_REPO_BASE_DIR);

    File destinationFile1 = new File(ARTIFACT_REPO_BASE_DIR + "_testIdentifier"
        + "-"
        + "artifact-file1.jar");
    File destinationFile2 = new File(ARTIFACT_REPO_BASE_DIR + "_testIdentifier"
        + "-"
        + "artifact-file2.jar");
    File destinationFile3 = new File(ARTIFACT_REPO_BASE_DIR + "_testIdentifier"
        + "-"
        + "artifact-file3.jar");
    File destinationFile4 = new File(ARTIFACT_REPO_BASE_DIR + "_testIdentifier"
        + "-"
        + "artifact-file4.jar");

    InputStream artifact1 = new ByteArrayInputStream("test1".getBytes(Charset.defaultCharset()));
    InputStream artifact2 = new ByteArrayInputStream("test2".getBytes(Charset.defaultCharset()));
    InputStream artifact3 = new ByteArrayInputStream("test3".getBytes(Charset.defaultCharset()));
    InputStream artifact4 = new ByteArrayInputStream("test4".getBytes(Charset.defaultCharset()));

    FileUtils.copyInputStreamToFile(artifact1, destinationFile1);
    FileUtils.copyInputStreamToFile(artifact2, destinationFile2);
    FileUtils.copyInputStreamToFile(artifact3, destinationFile3);
    FileUtils.copyInputStreamToFile(artifact4, destinationFile4);

    assertThat(cacheDir.listFiles()).isNotEmpty();
    assertThat(cacheDir.listFiles().length).isEqualTo(4);

    handler.deleteCachedArtifacts();

    assertThat(cacheDir.listFiles()).isNotEmpty();
    assertThat(cacheDir.listFiles().length).isEqualTo(3);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldDownloadArtifactRemoteFailure() throws IOException {
    SshExecutorFactoryContext context = getContext();
    doThrow(new RuntimeException("No artifact found at specified path"))
        .when(artifactoryNgService)
        .downloadArtifacts(any(), any(), any(), any(), any());

    assertThatThrownBy(() -> handler.downloadFromRemoteRepo(context, logCallback))
        .isInstanceOf(HintException.class)
        .hasMessage(format(SshExceptionConstants.ARTIFACT_DOWNLOAD_HINT, SshWinRmArtifactType.ARTIFACTORY.name()))
        .getCause()
        .hasMessage(format(SshExceptionConstants.ARTIFACT_DOWNLOAD_EXPLANATION, "testIdentifier",
            SshWinRmArtifactType.ARTIFACTORY.name()));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldGetArtifactFileSize() {
    SshExecutorFactoryContext context = getContext();
    doReturn(100L).when(artifactoryNgService).getFileSize(any(), any(), any());
    Long result = handler.getArtifactSize(context, logCallback);

    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(100L);
    assertArtifactMetadata(context.getArtifactMetadata());

    ArgumentCaptor<ArtifactoryConfigRequest> configRequestCaptor =
        ArgumentCaptor.forClass(ArtifactoryConfigRequest.class);
    verify(artifactoryNgService, times(1))
        .getFileSize(
            configRequestCaptor.capture(), eq(context.getArtifactMetadata()), eq(ArtifactMetadataKeys.artifactPath));
    assertArtifactoryConfigRequest(configRequestCaptor.getValue());
  }

  private void assertArtifactMetadata(Map<String, String> artifactMetadata) {
    assertThat(artifactMetadata).containsKey(ArtifactMetadataKeys.artifactPath);
    assertThat(artifactMetadata).containsKey(ArtifactMetadataKeys.artifactName);
    assertThat(artifactMetadata.get(ArtifactMetadataKeys.artifactPath)).isEqualTo("test/path/to/artifact");
    assertThat(artifactMetadata.get(ArtifactMetadataKeys.artifactName)).isEqualTo("test/path/to/artifact");
  }

  private void assertArtifactoryConfigRequest(ArtifactoryConfigRequest configRequest) {
    assertThat(configRequest).isNotNull();
    assertThat(configRequest.getArtifactoryUrl()).isEqualTo("http://artifactory");
    assertThat(configRequest.getUsername()).isEqualTo("test");
    assertThat(configRequest.getPassword()).isEqualTo("password".toCharArray());
  }

  private SshExecutorFactoryContext getContext() {
    ArtifactoryUsernamePasswordAuthDTO creds =
        ArtifactoryUsernamePasswordAuthDTO.builder()
            .username("test")
            .passwordRef(SecretRefData.builder().decryptedValue("password".toCharArray()).build())
            .build();

    ArtifactoryAuthenticationDTO auth =
        ArtifactoryAuthenticationDTO.builder().authType(ArtifactoryAuthType.USER_PASSWORD).credentials(creds).build();

    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder()
            .connectorConfig(
                ArtifactoryConnectorDTO.builder().artifactoryServerUrl("http://artifactory").auth(auth).build())
            .build();

    return SshExecutorFactoryContext.builder()
        .artifactDelegateConfig(ArtifactoryArtifactDelegateConfig.builder()
                                    .artifactPath("/path/to/artifact")
                                    .repositoryName("test")
                                    .identifier("testIdentifier")
                                    .encryptedDataDetails(Collections.emptyList())
                                    .connectorDTO(connectorInfoDTO)
                                    .build())
        .build();
  }
}
