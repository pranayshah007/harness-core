/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.VLICA;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.artifacts.azureartifacts.service.AzureArtifactsRegistryService;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationType;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsCredentialsDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsTokenDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthType;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthenticationDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsUserNamePasswordDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.delegate.task.nexus.NexusMapper;
import io.harness.encryption.SecretRefData;
import io.harness.exception.HintException;
import io.harness.filesystem.FileIo;
import io.harness.logging.LogCallback;
import io.harness.nexus.NexusRequest;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.jenkins.Jenkins;
import software.wings.helpers.ext.nexus.NexusService;
import software.wings.service.impl.AwsApiHelperService;
import software.wings.service.impl.jenkins.JenkinsUtils;
import software.wings.utils.ArtifactType;
import software.wings.utils.RepositoryFormat;

import com.amazonaws.services.s3.model.S3Object;
import com.google.common.collect.ImmutableMap;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
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
  private static final String NEXUS_NUGET_DOWNLOAD_URL =
      "https://nexus.dev/repository/azure-webapp-nuget/myWebApp/1.0.0";
  private static final String NEXUS_NUGET_ARTIFACT_NAME = "test-nuget-1.0.0.nupkg";
  private static final String NEXUS_MAVEN_DOWNLOAD_URL =
      "https://nexus.dev/repository/azure-webapp-maven/io/harness/test/hello-app/2.0.0/hello-app-2.0.0.jar";
  private static final String NEXUS_MAVEN_ARTIFACT_NAME = "hello-app-2.0.0.jar";

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ArtifactoryNgService artifactoryNgService;
  @Mock private ArtifactoryRequestMapper artifactoryRequestMapper;
  @Mock private AzureLogCallbackProvider logCallbackProvider;
  @Mock private LogCallback logCallback;
  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock private AwsApiHelperService awsApiHelperService;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;
  @Mock private NexusService nexusService;
  @Mock private NexusMapper nexusMapper;
  @Mock JenkinsUtils jenkinsUtil;
  @Mock Jenkins jenkins;
  @Mock private AzureArtifactsRegistryService azureArtifactsRegistryService;

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
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testDownloadS3AwsArtifact() throws Exception {
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

    final AwsS3AzureArtifactRequestDetails requestDetails = AwsS3AzureArtifactRequestDetails.builder()
                                                                .bucketName("testBucket")
                                                                .region("testRegion")
                                                                .filePath("test.war")
                                                                .identifier("PACKAGE")
                                                                .build();
    final ArtifactDownloadContext downloadContext =
        createDownloadContext(ArtifactSourceType.AMAZONS3, requestDetails, awsConnectorDTO);

    S3Object s3Object = new S3Object();
    s3Object.setObjectContent(new ByteArrayInputStream(ARTIFACT_FILE_CONTENT.getBytes()));
    try {
      doReturn(s3Object).when(awsApiHelperService).getObjectFromS3(any(), any(), any(), eq(requestDetails.filePath));
      doReturn(null).when(secretDecryptionService).decrypt(any(), any());
      doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());

      AzureArtifactDownloadResponse downloadResponse = downloadService.download(downloadContext);
      List<String> fileContent = Files.readAllLines(downloadResponse.getArtifactFile().toPath());
      assertThat(fileContent).containsOnly(ARTIFACT_FILE_CONTENT);
      assertThat(downloadResponse.getArtifactFile().toString()).contains("test.war");
      assertThat(downloadResponse.getArtifactType()).isEqualTo(ArtifactType.WAR);
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(downloadContext.getWorkingDirectory().getAbsolutePath());
    }
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testDownloadS3AwsArtifactExceptionIsThrown() throws Exception {
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

    final AwsS3AzureArtifactRequestDetails requestDetails = AwsS3AzureArtifactRequestDetails.builder()
                                                                .bucketName("testBucket")
                                                                .region("testRegion")
                                                                .filePath("test.war")
                                                                .identifier("PACKAGE")
                                                                .build();
    final ArtifactDownloadContext downloadContext =
        createDownloadContext(ArtifactSourceType.AMAZONS3, requestDetails, awsConnectorDTO);

    doReturn(null).when(secretDecryptionService).decrypt(any(), any());
    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());
    doThrow(new RuntimeException())
        .when(awsApiHelperService)
        .getObjectFromS3(any(), any(), any(), eq(requestDetails.filePath));

    try {
      assertThatThrownBy(() -> downloadService.download(downloadContext))
          .isInstanceOf(HintException.class)
          .hasMessageContaining("Please review the Artifact Details and check the File/Folder");
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(downloadContext.getWorkingDirectory().getAbsolutePath());
    }
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testDownloadJenkinsArtifact() throws Exception {
    InputStream is = new ByteArrayInputStream(ARTIFACT_FILE_CONTENT.getBytes());

    when(jenkinsUtil.getJenkins(any())).thenReturn(jenkins);
    when(jenkins.downloadArtifact(any(), any(), any())).thenReturn(new Pair<String, InputStream>() {
      @Override
      public String getLeft() {
        return "result";
      }
      @Override
      public InputStream getRight() {
        return is;
      }
      @Override
      public InputStream setValue(InputStream value) {
        return value;
      }
    });

    final JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("testJenkinsUrl")
            .auth(JenkinsAuthenticationDTO.builder()
                      .authType(JenkinsAuthType.USER_PASSWORD)
                      .credentials(JenkinsUserNamePasswordDTO.builder()
                                       .username("testUsername")
                                       .passwordRef(SecretRefData.builder().build())
                                       .build())
                      .build())
            .build();

    final JenkinsAzureArtifactRequestDetails requestDetails = JenkinsAzureArtifactRequestDetails.builder()
                                                                  .jobName("testJobName")
                                                                  .build("testBuild-123")
                                                                  .artifactPath("testArtifactPath.war")
                                                                  .identifier("PACKAGE")
                                                                  .build();

    final ArtifactDownloadContext downloadContext =
        createDownloadContext(ArtifactSourceType.JENKINS, requestDetails, jenkinsConnectorDTO);

    try {
      AzureArtifactDownloadResponse downloadResponse = downloadService.download(downloadContext);
      List<String> fileContent = Files.readAllLines(downloadResponse.getArtifactFile().toPath());
      assertThat(fileContent).containsOnly(ARTIFACT_FILE_CONTENT);
      assertThat(downloadResponse.getArtifactFile().toString()).contains("testArtifactPath.war");
      assertThat(downloadResponse.getArtifactType()).isEqualTo(ArtifactType.WAR);
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(downloadContext.getWorkingDirectory().getAbsolutePath());
    }
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testDownloadJenkinsArtifactAndExceptionIsThrown() throws Exception {
    InputStream is = new ByteArrayInputStream(ARTIFACT_FILE_CONTENT.getBytes());

    when(jenkinsUtil.getJenkins(any())).thenReturn(jenkins);
    doThrow(new RuntimeException()).when(jenkins).downloadArtifact(any(), any(), any());

    final JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("testJenkinsUrl")
            .auth(JenkinsAuthenticationDTO.builder()
                      .authType(JenkinsAuthType.USER_PASSWORD)
                      .credentials(JenkinsUserNamePasswordDTO.builder()
                                       .username("testUsername")
                                       .passwordRef(SecretRefData.builder().build())
                                       .build())
                      .build())
            .build();

    final JenkinsAzureArtifactRequestDetails requestDetails = JenkinsAzureArtifactRequestDetails.builder()
                                                                  .jobName("testJobName")
                                                                  .build("testBuild-123")
                                                                  .artifactPath("testArtifactPath.war")
                                                                  .identifier("PACKAGE")
                                                                  .build();

    final ArtifactDownloadContext downloadContext =
        createDownloadContext(ArtifactSourceType.JENKINS, requestDetails, jenkinsConnectorDTO);

    try {
      assertThatThrownBy(() -> downloadService.download(downloadContext))
          .isInstanceOf(HintException.class)
          .hasMessageContaining("Please review the Jenkins Artifact Details and check Path to the artifact");
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
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testDownloadAzureDevopsArtifact() throws Exception {
    InputStream is = new ByteArrayInputStream(ARTIFACT_FILE_CONTENT.getBytes());

    when(azureArtifactsRegistryService.downloadArtifact(any(), any(), any(), any(), any(), any()))
        .thenReturn(new Pair<String, InputStream>() {
          @Override
          public String getLeft() {
            return "package-test.war";
          }
          @Override
          public InputStream getRight() {
            return is;
          }
          @Override
          public InputStream setValue(InputStream value) {
            return value;
          }
        });

    final AzureArtifactsConnectorDTO azureArtifactsConnectorDTO =
        AzureArtifactsConnectorDTO.builder()
            .azureArtifactsUrl("dummyDevopsAzureURL")
            .auth(AzureArtifactsAuthenticationDTO.builder()
                      .credentials(
                          AzureArtifactsCredentialsDTO.builder()
                              .type(AzureArtifactsAuthenticationType.PERSONAL_ACCESS_TOKEN)
                              .credentialsSpec(
                                  AzureArtifactsTokenDTO.builder()
                                      .tokenRef(SecretRefData.builder().decryptedValue("secret".toCharArray()).build())
                                      .build())
                              .build())
                      .build())
            .build();

    AzureDevOpsArtifactRequestDetails requestDetails = AzureDevOpsArtifactRequestDetails.builder()
                                                           .feed("testFeed")
                                                           .scope("org")
                                                           .packageType("maven")
                                                           .packageName("test.package.name:package-test")
                                                           .version("1.0")
                                                           .identifier("PACKAGE")
                                                           .build();

    final ArtifactDownloadContext downloadContext =
        createDownloadContext(ArtifactSourceType.AZURE_ARTIFACTS, requestDetails, azureArtifactsConnectorDTO);

    try {
      AzureArtifactDownloadResponse downloadResponse = downloadService.download(downloadContext);
      List<String> fileContent = Files.readAllLines(downloadResponse.getArtifactFile().toPath());
      assertThat(fileContent).containsOnly(ARTIFACT_FILE_CONTENT);
      assertThat(downloadResponse.getArtifactFile().toString()).contains("test.package.name");
      assertThat(downloadResponse.getArtifactType()).isEqualTo(ArtifactType.WAR);
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(downloadContext.getWorkingDirectory().getAbsolutePath());
    }
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testDownloadAzureDevopsArtifactAndExceptionIsThrown() throws Exception {
    when(azureArtifactsRegistryService.downloadArtifact(any(), any(), any(), any(), any(), any()))
        .thenThrow(new RuntimeException());

    final AzureArtifactsConnectorDTO azureArtifactsConnectorDTO =
        AzureArtifactsConnectorDTO.builder()
            .azureArtifactsUrl("dummyDevopsAzureURL")
            .auth(AzureArtifactsAuthenticationDTO.builder()
                      .credentials(
                          AzureArtifactsCredentialsDTO.builder()
                              .type(AzureArtifactsAuthenticationType.PERSONAL_ACCESS_TOKEN)
                              .credentialsSpec(
                                  AzureArtifactsTokenDTO.builder()
                                      .tokenRef(SecretRefData.builder().decryptedValue("secret".toCharArray()).build())
                                      .build())
                              .build())
                      .build())
            .build();

    AzureDevOpsArtifactRequestDetails requestDetails = AzureDevOpsArtifactRequestDetails.builder()
                                                           .feed("testFeed")
                                                           .scope("org")
                                                           .packageType("maven")
                                                           .packageName("test.package.name:package-test")
                                                           .version("1.0")
                                                           .identifier("PACKAGE")
                                                           .build();

    final ArtifactDownloadContext downloadContext =
        createDownloadContext(ArtifactSourceType.AZURE_ARTIFACTS, requestDetails, azureArtifactsConnectorDTO);

    try {
      assertThatThrownBy(() -> downloadService.download(downloadContext))
          .isInstanceOf(HintException.class)
          .hasMessageContaining(
              "Please review the Artifact Details and check the Azure DevOps project/organization feed of the artifact.");
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

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDownloadNexusNugetArtifact() throws Exception {
    testDownloadNexusRequest(
        NEXUS_NUGET_DOWNLOAD_URL, RepositoryFormat.nuget.name(), NEXUS_NUGET_ARTIFACT_NAME, ArtifactType.NUGET);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDownloadNexusMavenArtifact() throws Exception {
    testDownloadNexusRequest(
        NEXUS_MAVEN_DOWNLOAD_URL, RepositoryFormat.maven.name(), NEXUS_MAVEN_ARTIFACT_NAME, ArtifactType.JAR);
  }

  private void testDownloadNexusRequest(String artifactUrl, String repositoryFormat, String expectedArtifactName,
      ArtifactType expectedArtifactType) throws Exception {
    final NexusConnectorDTO connectorDTO = NexusConnectorDTO.builder().version("3.x").build();
    final NexusAzureArtifactRequestDetails artifactRequestDetails =
        NexusAzureArtifactRequestDetails.builder()
            .certValidationRequired(false)
            .artifactUrl(artifactUrl)
            .repositoryFormat(repositoryFormat)
            .identifier("test")
            .metadata(ImmutableMap.of("url", artifactUrl, "package", "test-nuget", "version", "1.0.0"))
            .build();
    final NexusRequest nexusRequest = NexusRequest.builder().build();
    final ArtifactDownloadContext downloadContext =
        createDownloadContext(ArtifactSourceType.NEXUS3_REGISTRY, artifactRequestDetails, connectorDTO);

    doReturn(nexusRequest).when(nexusMapper).toNexusRequest(connectorDTO, artifactRequestDetails);

    try (InputStream artifactStream = new ByteArrayInputStream(ARTIFACT_FILE_CONTENT.getBytes())) {
      doReturn(Pair.of("artifact", artifactStream))
          .when(nexusService)
          .downloadArtifactByUrl(nexusRequest, expectedArtifactName, artifactUrl);

      AzureArtifactDownloadResponse downloadResponse = downloadService.download(downloadContext);

      List<String> fileContent = Files.readAllLines(downloadResponse.getArtifactFile().toPath());
      assertThat(fileContent).containsOnly(ARTIFACT_FILE_CONTENT);
      assertThat(downloadResponse.getArtifactType()).isEqualTo(expectedArtifactType);
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