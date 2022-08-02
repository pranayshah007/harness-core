/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.exception.HintException;
import io.harness.filesystem.FileIo;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import software.wings.utils.ArtifactType;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class AzureArtifactDownloadServiceImplTest extends CategoryTest {
  private static final String ARTIFACT_FILE_CONTENT = "artifact-file-content";

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ArtifactoryNgService artifactoryNgService;
  @Mock private ArtifactoryRequestMapper artifactoryRequestMapper;

  @Mock private AzureLogCallbackProvider logCallbackProvider;
  @Mock private LogCallback logCallback;

  @InjectMocks private AzureArtifactDownloadServiceImpl downloadService;

  @Before
  public void setup() {
    doReturn(logCallback).when(logCallbackProvider).obtainLogCallback(anyString());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDownloadArtifactoryArtifact() throws Exception {
    final ArtifactoryConnectorDTO connector = ArtifactoryConnectorDTO.builder().build();
    final ArtifactoryAzureArtifactRequestDetails requestDetails = ArtifactoryAzureArtifactRequestDetails.builder()
                                                                      .repository("test")
                                                                      .repository("repository")
                                                                      .artifactPaths(singletonList("test/artifact.zip"))
                                                                      .build();
    final ArtifactDownloadContext downloadContext =
        createDownloadContext(ArtifactSourceType.ARTIFACTORY_REGISTRY, requestDetails, connector);
    final ArtifactoryConfigRequest configRequest = ArtifactoryConfigRequest.builder().build();

    try (InputStream artifactStream = new ByteArrayInputStream(ARTIFACT_FILE_CONTENT.getBytes())) {
      doReturn(configRequest).when(artifactoryRequestMapper).toArtifactoryRequest(connector);
      doReturn(artifactStream)
          .when(artifactoryNgService)
          .downloadArtifacts(configRequest, "repository", requestDetails.toMetadata(),
              ArtifactMetadataKeys.artifactPath, ArtifactMetadataKeys.artifactName);

      AzureArtifactDownloadResponse downloadResponse = downloadService.download(downloadContext);
      List<String> fileContent = Files.readAllLines(downloadResponse.getArtifactFile().toPath());
      assertThat(fileContent).containsOnly(ARTIFACT_FILE_CONTENT);
      assertThat(downloadResponse.getArtifactType()).isEqualTo(ArtifactType.ZIP);
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(downloadContext.getWorkingDirectory().getAbsolutePath());
    }
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDownloadArtifactoryArtifactInvalidConnectorType() throws Exception {
    final ConnectorConfigDTO connector = AzureConnectorDTO.builder().build();
    final ArtifactDownloadContext downloadContext = createDownloadContext(ArtifactSourceType.ARTIFACTORY_REGISTRY,
        ArtifactoryAzureArtifactRequestDetails.builder().artifactPaths(singletonList("test")).build(), connector);

    try {
      assertThatThrownBy(() -> downloadService.download(downloadContext)).isInstanceOf(HintException.class);
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(downloadContext.getWorkingDirectory().getAbsolutePath());
    }
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDownloadArtifactoryArtifactInvalidRequestDetails() throws Exception {
    final ArtifactoryConnectorDTO connector = ArtifactoryConnectorDTO.builder().build();
    final AzureArtifactRequestDetails artifactRequestDetails = mock(AzureArtifactRequestDetails.class);
    final ArtifactDownloadContext downloadContext =
        createDownloadContext(ArtifactSourceType.ARTIFACTORY_REGISTRY, artifactRequestDetails, connector);

    doReturn("test").when(artifactRequestDetails).getArtifactName();

    try {
      assertThatThrownBy(() -> downloadService.download(downloadContext)).isInstanceOf(HintException.class);
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(downloadContext.getWorkingDirectory().getAbsolutePath());
    }
  }

  private ArtifactDownloadContext createDownloadContext(ArtifactSourceType sourceType,
      AzureArtifactRequestDetails requestDetails, ConnectorConfigDTO connector) throws Exception {
    return ArtifactDownloadContext.builder()
        .artifactConfig(AzurePackageArtifactConfig.builder()
                            .sourceType(sourceType)
                            .artifactDetails(requestDetails)
                            .connectorConfig(connector)
                            .build())
        .commandUnitName("Download Artifacts")
        .logCallbackProvider(logCallbackProvider)
        .workingDirectory(new File(Files.createTempDirectory("testAzureArtifact").toString()))
        .build();
  }
}