/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthCredentialsDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.encryption.SecretRefData;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class ServerlessTaskHelperBaseTest extends CategoryTest {
  private static final String ARTIFACTORY_ARTIFACT_PATH = "artifactPath";
  private static final String ARTIFACTORY_ARTIFACT_NAME = "artifactName";
  private static final String ARTIFACT_FILE_NAME = "artifactFile";
  private static final String ARTIFACT_ZIP_REGEX = ".*\\.zip";
  private static final String ARTIFACT_JAR_REGEX = ".*\\.jar";
  private static final String ARTIFACT_WAR_REGEX = ".*\\.war";
  private static final String ARTIFACT_PATH_REGEX = ".*<\\+artifact\\.path>.*";
  private static final String ARTIFACT_PATH = "<+artifact.path>";

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private ArtifactoryNgService artifactoryNgService;
  @Mock private ArtifactoryRequestMapper artifactoryRequestMapper;
  @Mock private SecretDecryptionService secretDecryptionService;
  @InjectMocks @Spy private ServerlessTaskHelperBase serverlessTaskHelperBase;

  private static final String ARTIFACT_DIRECTORY = "./repository/serverless/";
  private static final String ARTIFACTORY_PATH = "asdffasd.zip";
  String repositoryName = "dfsgvgasd";

  ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder().build();
  SecretRefData password = SecretRefData.builder().build();
  ArtifactoryAuthCredentialsDTO artifactoryAuthCredentialsDTO =
      ArtifactoryUsernamePasswordAuthDTO.builder().passwordRef(password).build();
  ArtifactoryAuthenticationDTO artifactoryAuthenticationDTO = ArtifactoryAuthenticationDTO.builder()
                                                                  .credentials(artifactoryAuthCredentialsDTO)
                                                                  .authType(ArtifactoryAuthType.USER_PASSWORD)
                                                                  .build();
  ArtifactoryConnectorDTO connectorConfigDTO =
      ArtifactoryConnectorDTO.builder().auth(artifactoryAuthenticationDTO).build();
  ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().connectorConfig(connectorConfigDTO).build();
  List<EncryptedDataDetail> encryptedDataDetailsList =
      Arrays.asList(EncryptedDataDetail.builder().fieldName("afsd").build());
  ServerlessArtifactConfig serverlessArtifactConfig = ServerlessArtifactoryArtifactConfig.builder()
                                                          .artifactPath(ARTIFACTORY_PATH)
                                                          .connectorDTO(connectorInfoDTO)
                                                          .encryptedDataDetails(encryptedDataDetailsList)
                                                          .repositoryName(repositoryName)
                                                          .build();
  @Mock LogCallback logCallback;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void fetchArtifactTest() throws Exception {
    doReturn(artifactoryConfigRequest).when(artifactoryRequestMapper).toArtifactoryRequest(connectorConfigDTO);
    Map<String, String> artifactMetadata = new HashMap<>();
    artifactMetadata.put(ARTIFACTORY_ARTIFACT_PATH, repositoryName + "/" + ARTIFACTORY_PATH);
    artifactMetadata.put(ARTIFACTORY_ARTIFACT_NAME, repositoryName + "/" + ARTIFACTORY_PATH);

    String input = "asfd";
    InputStream inputStream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
    String artifactPath = Paths
                              .get(((ServerlessArtifactoryArtifactConfig) serverlessArtifactConfig).getRepositoryName(),
                                  ((ServerlessArtifactoryArtifactConfig) serverlessArtifactConfig).getArtifactPath())
                              .toString();

    doReturn(inputStream)
        .when(artifactoryNgService)
        .downloadArtifacts(artifactoryConfigRequest, repositoryName, artifactMetadata, ARTIFACTORY_ARTIFACT_PATH,
            ARTIFACTORY_ARTIFACT_NAME);
    serverlessTaskHelperBase.fetchArtifact(serverlessArtifactConfig, logCallback, ARTIFACT_DIRECTORY);
    verify(logCallback)
        .saveExecutionLog(color(
            format("Downloading %s artifact with identifier: %s", serverlessArtifactConfig.getServerlessArtifactType(),
                ((ServerlessArtifactoryArtifactConfig) serverlessArtifactConfig).getIdentifier()),
            White, Bold));
    verify(logCallback).saveExecutionLog("Artifactory Artifact Path: " + artifactPath);
    verify(logCallback).saveExecutionLog(color("Successfully downloaded artifact..", White, Bold));
  }
}
