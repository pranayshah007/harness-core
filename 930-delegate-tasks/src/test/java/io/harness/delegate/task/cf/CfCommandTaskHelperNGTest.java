/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.cf;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.cf.CfTestConstants.ACCOUNT_ID;
import static io.harness.delegate.cf.CfTestConstants.APP_ID;
import static io.harness.delegate.cf.CfTestConstants.APP_NAME;
import static io.harness.delegate.cf.CfTestConstants.STOPPED;
import static io.harness.rule.OwnerRule.RISHABH;

import static software.wings.utils.ArtifactType.ZIP;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.artifacts.azureartifacts.service.AzureArtifactsRegistryService;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
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
import io.harness.delegate.beans.connector.jenkins.JenkinsBearerTokenDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsUserNamePasswordDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthenticationDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusUsernamePasswordAuthDTO;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfInternalInstanceElement;
import io.harness.delegate.beans.pcf.CfRollbackCommandResult;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.beans.pcf.TasApplicationInfo;
import io.harness.delegate.cf.PcfCommandTaskBaseHelper;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.delegate.task.nexus.NexusMapper;
import io.harness.delegate.task.pcf.artifact.ArtifactoryTasArtifactRequestDetails;
import io.harness.delegate.task.pcf.artifact.AwsS3TasArtifactRequestDetails;
import io.harness.delegate.task.pcf.artifact.AzureDevOpsTasArtifactRequestDetails;
import io.harness.delegate.task.pcf.artifact.JenkinsTasArtifactRequestDetails;
import io.harness.delegate.task.pcf.artifact.NexusTasArtifactRequestDetails;
import io.harness.delegate.task.pcf.artifact.TasArtifactRequestDetails;
import io.harness.delegate.task.pcf.artifact.TasPackageArtifactConfig;
import io.harness.delegate.task.pcf.request.CfCommandRollbackRequest;
import io.harness.delegate.task.pcf.request.CfDeployCommandRequestNG;
import io.harness.delegate.task.pcf.request.TasManifestsPackage;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.encryption.SecretRefData;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.filesystem.FileIo;
import io.harness.logging.LogCallback;
import io.harness.nexus.NexusRequest;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.cloudfoundry.operations.applications.InstanceDetail;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class CfCommandTaskHelperNGTest extends CategoryTest {
  private static final String ARTIFACT_FILE_CONTENT = "artifact-file-content";
  private static final String NEXUS_NUGET_DOWNLOAD_URL =
      "https://nexus.dev/repository/azure-webapp-nuget/myWebApp/1.0.0";
  private static final String NEXUS_NUGET_ARTIFACT_NAME = "test-nuget-1.0.0.nupkg";
  private static final String NEXUS_MAVEN_DOWNLOAD_URL =
      "https://nexus.dev/repository/azure-webapp-maven/io/harness/test/hello-app/2.0.0/hello-app-2.0.0.jar";
  private static final String NEXUS2_MAVEN_DOWNLOAD_URL =
      "https://nexus.dev/service/local/artifact/maven/content?r=azure-webapp-maven&g=test&a=hello-app&v=2.0.0&p=jar&e=jar";
  private static final String NEXUS_MAVEN_ARTIFACT_NAME = "hello-app-2.0.0.jar";

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private ArtifactoryNgService artifactoryNgService;
  @Mock private ArtifactoryRequestMapper artifactoryRequestMapper;
  @Mock private AzureLogCallbackProvider logCallbackProvider;
  @Mock LogCallback logCallback;
  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock private AwsApiHelperService awsApiHelperService;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;
  @Mock private NexusService nexusService;
  @Mock private NexusMapper nexusMapper;
  @Mock JenkinsUtils jenkinsUtil;
  @Mock Jenkins jenkins;
  @Mock private DecryptionHelper decryptionHelper;
  @Mock private AzureArtifactsRegistryService azureArtifactsRegistryService;
  @InjectMocks @Spy PcfCommandTaskBaseHelper pcfCommandTaskHelper;
  @Mock CfDeploymentManager cfDeploymentManager;
  @InjectMocks @Spy private CfCommandTaskHelperNG cfCommandTaskHelperNG;

  @Before
  public void setup() {
    initMocks(this);
    doReturn(logCallback).when(logCallbackProvider).obtainLogCallback(anyString());
    when(decryptionHelper.decrypt(any(), any())).thenReturn(null);
    doNothing().when(logCallback).saveExecutionLog(anyString());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testDownloadArtifactoryArtifact() throws Exception {
    final ArtifactoryConnectorDTO connector =
        ArtifactoryConnectorDTO.builder()
            .auth(ArtifactoryAuthenticationDTO.builder()
                      .credentials(ArtifactoryUsernamePasswordAuthDTO.builder().build())
                      .build())
            .build();
    final ArtifactoryTasArtifactRequestDetails requestDetails = ArtifactoryTasArtifactRequestDetails.builder()
                                                                    .repository("test")
                                                                    .repository("repository")
                                                                    .artifactPaths(singletonList("test/artifact.zip"))
                                                                    .build();
    final TasArtifactDownloadContext downloadContext =
        createDownloadContext(ArtifactSourceType.ARTIFACTORY_REGISTRY, requestDetails, connector);
    final ArtifactoryConfigRequest configRequest = ArtifactoryConfigRequest.builder().build();

    try (InputStream artifactStream = new ByteArrayInputStream(ARTIFACT_FILE_CONTENT.getBytes())) {
      doReturn(configRequest).when(artifactoryRequestMapper).toArtifactoryRequest(connector);
      doReturn(artifactStream)
          .when(artifactoryNgService)
          .downloadArtifacts(configRequest, "repository", requestDetails.toMetadata(),
              ArtifactMetadataKeys.artifactPath, ArtifactMetadataKeys.artifactName);

      TasArtifactDownloadResponse downloadResponse =
          cfCommandTaskHelperNG.downloadPackageArtifact(downloadContext, logCallback);
      List<String> fileContent = Files.readAllLines(downloadResponse.getArtifactFile().toPath());
      assertThat(fileContent).containsOnly(ARTIFACT_FILE_CONTENT);
      assertThat(downloadResponse.getArtifactType()).isEqualTo(ZIP);
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(downloadContext.getWorkingDirectory().getAbsolutePath());
    }
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testDownloadCustomArtifact() throws Exception {
    final ArtifactoryConnectorDTO connector =
        ArtifactoryConnectorDTO.builder()
            .auth(ArtifactoryAuthenticationDTO.builder()
                      .credentials(ArtifactoryUsernamePasswordAuthDTO.builder().build())
                      .build())
            .build();
    final ArtifactoryTasArtifactRequestDetails requestDetails = ArtifactoryTasArtifactRequestDetails.builder()
                                                                    .repository("test")
                                                                    .repository("repository")
                                                                    .artifactPaths(singletonList("test/artifact.zip"))
                                                                    .build();
    final TasArtifactDownloadContext downloadContext =
        createDownloadContext(ArtifactSourceType.CUSTOM_ARTIFACT, requestDetails, connector);
    final ArtifactoryConfigRequest configRequest = ArtifactoryConfigRequest.builder().build();

    try (InputStream artifactStream = new ByteArrayInputStream(ARTIFACT_FILE_CONTENT.getBytes())) {
      doReturn(configRequest).when(artifactoryRequestMapper).toArtifactoryRequest(connector);
      doReturn(artifactStream)
          .when(artifactoryNgService)
          .downloadArtifacts(configRequest, "repository", requestDetails.toMetadata(),
              ArtifactMetadataKeys.artifactPath, ArtifactMetadataKeys.artifactName);

      TasArtifactDownloadResponse downloadResponse =
          cfCommandTaskHelperNG.downloadPackageArtifact(downloadContext, logCallback);
      assertThat(downloadResponse.getArtifactFile()).isNull();
      assertThat(downloadResponse.getArtifactType()).isEqualTo(ZIP);
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(downloadContext.getWorkingDirectory().getAbsolutePath());
    }
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testDownloadAMIArtifact() throws Exception {
    final ArtifactoryConnectorDTO connector =
        ArtifactoryConnectorDTO.builder()
            .auth(ArtifactoryAuthenticationDTO.builder()
                      .credentials(ArtifactoryUsernamePasswordAuthDTO.builder().build())
                      .build())
            .build();
    final ArtifactoryTasArtifactRequestDetails requestDetails = ArtifactoryTasArtifactRequestDetails.builder()
                                                                    .repository("test")
                                                                    .repository("repository")
                                                                    .artifactPaths(singletonList("test/artifact.zip"))
                                                                    .build();
    final TasArtifactDownloadContext downloadContext =
        createDownloadContext(ArtifactSourceType.AMI, requestDetails, connector);
    final ArtifactoryConfigRequest configRequest = ArtifactoryConfigRequest.builder().build();

    try (InputStream artifactStream = new ByteArrayInputStream(ARTIFACT_FILE_CONTENT.getBytes())) {
      doReturn(configRequest).when(artifactoryRequestMapper).toArtifactoryRequest(connector);
      doReturn(artifactStream)
          .when(artifactoryNgService)
          .downloadArtifacts(configRequest, "repository", requestDetails.toMetadata(),
              ArtifactMetadataKeys.artifactPath, ArtifactMetadataKeys.artifactName);

      assertThatThrownBy(() -> cfCommandTaskHelperNG.downloadPackageArtifact(downloadContext, logCallback))
          .hasMessage("Use supported artifact registry");
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(downloadContext.getWorkingDirectory().getAbsolutePath());
    }
  }

  @Test
  @Owner(developers = RISHABH)
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

    final AwsS3TasArtifactRequestDetails requestDetails = AwsS3TasArtifactRequestDetails.builder()
                                                              .bucketName("testBucket")
                                                              .region("testRegion")
                                                              .filePath("test.war")
                                                              .identifier("PACKAGE")
                                                              .build();
    final TasArtifactDownloadContext downloadContext =
        createDownloadContext(ArtifactSourceType.AMAZONS3, requestDetails, awsConnectorDTO);

    S3Object s3Object = new S3Object();
    s3Object.setObjectContent(new ByteArrayInputStream(ARTIFACT_FILE_CONTENT.getBytes()));
    try {
      doReturn(s3Object)
          .when(awsApiHelperService)
          .getObjectFromS3(any(), any(), any(), eq(requestDetails.getFilePath()));
      doReturn(null).when(secretDecryptionService).decrypt(any(), any());
      doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());

      TasArtifactDownloadResponse downloadResponse =
          cfCommandTaskHelperNG.downloadPackageArtifact(downloadContext, logCallback);
      List<String> fileContent = Files.readAllLines(downloadResponse.getArtifactFile().toPath());
      assertThat(fileContent).containsOnly(ARTIFACT_FILE_CONTENT);
      assertThat(downloadResponse.getArtifactFile().toString()).contains("test.war");
      assertThat(downloadResponse.getArtifactType()).isEqualTo(ArtifactType.WAR);
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(downloadContext.getWorkingDirectory().getAbsolutePath());
    }
  }

  @Test
  @Owner(developers = RISHABH)
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

    final AwsS3TasArtifactRequestDetails requestDetails = AwsS3TasArtifactRequestDetails.builder()
                                                              .bucketName("testBucket")
                                                              .region("testRegion")
                                                              .filePath("test.war")
                                                              .identifier("PACKAGE")
                                                              .build();
    final TasArtifactDownloadContext downloadContext =
        createDownloadContext(ArtifactSourceType.AMAZONS3, requestDetails, awsConnectorDTO);

    doReturn(null).when(secretDecryptionService).decrypt(any(), any());
    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());
    doThrow(new RuntimeException())
        .when(awsApiHelperService)
        .getObjectFromS3(any(), any(), any(), eq(requestDetails.getFilePath()));

    try {
      assertThatThrownBy(() -> cfCommandTaskHelperNG.downloadPackageArtifact(downloadContext, logCallback))
          .isInstanceOf(HintException.class)
          .hasMessageContaining("Please review the Artifact Details and check the File/Folder");
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(downloadContext.getWorkingDirectory().getAbsolutePath());
    }
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testDownloadS3AwsNullArtifactExceptionIsThrown() throws Exception {
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

    final AwsS3TasArtifactRequestDetails requestDetails = AwsS3TasArtifactRequestDetails.builder()
                                                              .bucketName("testBucket")
                                                              .region("testRegion")
                                                              .identifier("PACKAGE")
                                                              .build();
    final TasArtifactDownloadContext downloadContext =
        createDownloadContext(ArtifactSourceType.AMAZONS3, requestDetails, awsConnectorDTO);

    doReturn(null).when(secretDecryptionService).decrypt(any(), any());
    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());
    doThrow(new RuntimeException())
        .when(awsApiHelperService)
        .getObjectFromS3(any(), any(), any(), eq(requestDetails.getFilePath()));

    try {
      assertThatThrownBy(() -> cfCommandTaskHelperNG.downloadPackageArtifact(downloadContext, logCallback))
          .isInstanceOf(HintException.class)
          .hasMessageContaining("Please check artifactDirectory or artifactPath field");
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(downloadContext.getWorkingDirectory().getAbsolutePath());
    }
  }

  @Test
  @Owner(developers = RISHABH)
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

    final JenkinsTasArtifactRequestDetails requestDetails = JenkinsTasArtifactRequestDetails.builder()
                                                                .jobName("testJobName")
                                                                .build("testBuild-123")
                                                                .artifactPath("testArtifactPath.war")
                                                                .identifier("PACKAGE")
                                                                .build();

    final TasArtifactDownloadContext downloadContext =
        createDownloadContext(ArtifactSourceType.JENKINS, requestDetails, jenkinsConnectorDTO);

    try {
      TasArtifactDownloadResponse downloadResponse =
          cfCommandTaskHelperNG.downloadPackageArtifact(downloadContext, logCallback);
      List<String> fileContent = Files.readAllLines(downloadResponse.getArtifactFile().toPath());
      assertThat(fileContent).containsOnly(ARTIFACT_FILE_CONTENT);
      assertThat(downloadResponse.getArtifactFile().toString()).contains("testArtifactPath.war");
      assertThat(downloadResponse.getArtifactType()).isEqualTo(ArtifactType.WAR);
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(downloadContext.getWorkingDirectory().getAbsolutePath());
    }
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testDownloadJenkinsArtifactToken() throws Exception {
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
                      .authType(JenkinsAuthType.BEARER_TOKEN)
                      .credentials(JenkinsBearerTokenDTO.builder().tokenRef(SecretRefData.builder().build()).build())
                      .build())
            .build();

    final JenkinsTasArtifactRequestDetails requestDetails = JenkinsTasArtifactRequestDetails.builder()
                                                                .jobName("testJobName")
                                                                .build("testBuild-123")
                                                                .artifactPath("testArtifactPath.war")
                                                                .identifier("PACKAGE")
                                                                .build();

    final TasArtifactDownloadContext downloadContext =
        createDownloadContext(ArtifactSourceType.JENKINS, requestDetails, jenkinsConnectorDTO);

    try {
      TasArtifactDownloadResponse downloadResponse =
          cfCommandTaskHelperNG.downloadPackageArtifact(downloadContext, logCallback);
      List<String> fileContent = Files.readAllLines(downloadResponse.getArtifactFile().toPath());
      assertThat(fileContent).containsOnly(ARTIFACT_FILE_CONTENT);
      assertThat(downloadResponse.getArtifactFile().toString()).contains("testArtifactPath.war");
      assertThat(downloadResponse.getArtifactType()).isEqualTo(ArtifactType.WAR);
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(downloadContext.getWorkingDirectory().getAbsolutePath());
    }
  }

  @Test
  @Owner(developers = RISHABH)
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

    final JenkinsTasArtifactRequestDetails requestDetails = JenkinsTasArtifactRequestDetails.builder()
                                                                .jobName("testJobName")
                                                                .build("testBuild-123")
                                                                .artifactPath("testArtifactPath.war")
                                                                .identifier("PACKAGE")
                                                                .build();

    final TasArtifactDownloadContext downloadContext =
        createDownloadContext(ArtifactSourceType.JENKINS, requestDetails, jenkinsConnectorDTO);

    try {
      assertThatThrownBy(() -> cfCommandTaskHelperNG.downloadPackageArtifact(downloadContext, logCallback))
          .isInstanceOf(HintException.class)
          .hasMessageContaining("Please review the Jenkins Artifact Details and check Path to the artifact");
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(downloadContext.getWorkingDirectory().getAbsolutePath());
    }
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testDownloadJenkinsArtifactNoPackageAndExceptionIsThrown() throws Exception {
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

    final JenkinsTasArtifactRequestDetails requestDetails = JenkinsTasArtifactRequestDetails.builder()
                                                                .jobName("testJobName")
                                                                .build("testBuild-123")
                                                                .identifier("PACKAGE")
                                                                .build();

    final TasArtifactDownloadContext downloadContext =
        createDownloadContext(ArtifactSourceType.JENKINS, requestDetails, jenkinsConnectorDTO);

    try {
      assertThatThrownBy(() -> cfCommandTaskHelperNG.downloadPackageArtifact(downloadContext, logCallback))
          .isInstanceOf(HintException.class)
          .hasMessageContaining("Please check artifactDirectory or artifactPath field");
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(downloadContext.getWorkingDirectory().getAbsolutePath());
    }
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testDownloadArtifactoryArtifactInvalidConnectorType() throws Exception {
    final ConnectorConfigDTO connector = AzureConnectorDTO.builder().build();
    final TasArtifactDownloadContext downloadContext = createDownloadContext(ArtifactSourceType.ARTIFACTORY_REGISTRY,
        ArtifactoryTasArtifactRequestDetails.builder().artifactPaths(singletonList("test")).build(), connector);

    try {
      assertThatThrownBy(() -> cfCommandTaskHelperNG.downloadPackageArtifact(downloadContext, logCallback))
          .isInstanceOf(HintException.class);
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(downloadContext.getWorkingDirectory().getAbsolutePath());
    }
  }

  @Test
  @Owner(developers = RISHABH)
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

    AzureDevOpsTasArtifactRequestDetails requestDetails = AzureDevOpsTasArtifactRequestDetails.builder()
                                                              .feed("testFeed")
                                                              .scope("org")
                                                              .packageType("maven")
                                                              .packageName("test.package.name:package-test")
                                                              .version("1.0")
                                                              .identifier("PACKAGE")
                                                              .build();

    final TasArtifactDownloadContext downloadContext =
        createDownloadContext(ArtifactSourceType.AZURE_ARTIFACTS, requestDetails, azureArtifactsConnectorDTO);

    try {
      TasArtifactDownloadResponse downloadResponse =
          cfCommandTaskHelperNG.downloadPackageArtifact(downloadContext, logCallback);
      List<String> fileContent = Files.readAllLines(downloadResponse.getArtifactFile().toPath());
      assertThat(fileContent).containsOnly(ARTIFACT_FILE_CONTENT);
      assertThat(downloadResponse.getArtifactFile().toString()).contains("test.package.name");
      assertThat(downloadResponse.getArtifactType()).isEqualTo(ArtifactType.WAR);
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(downloadContext.getWorkingDirectory().getAbsolutePath());
    }
  }

  @Test
  @Owner(developers = RISHABH)
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

    AzureDevOpsTasArtifactRequestDetails requestDetails = AzureDevOpsTasArtifactRequestDetails.builder()
                                                              .feed("testFeed")
                                                              .scope("org")
                                                              .packageType("maven")
                                                              .packageName("test.package.name:package-test")
                                                              .version("1.0")
                                                              .identifier("PACKAGE")
                                                              .build();

    final TasArtifactDownloadContext downloadContext =
        createDownloadContext(ArtifactSourceType.AZURE_ARTIFACTS, requestDetails, azureArtifactsConnectorDTO);

    try {
      assertThatThrownBy(() -> cfCommandTaskHelperNG.downloadPackageArtifact(downloadContext, logCallback))
          .isInstanceOf(HintException.class)
          .hasMessageContaining(
              "Please review the Artifact Details and check the Azure DevOps project/organization feed of the artifact.");
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(downloadContext.getWorkingDirectory().getAbsolutePath());
    }
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testDownloadAzureDevopsArtifactAndInvalidVersionExceptionIsThrown() throws Exception {
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

    AzureDevOpsTasArtifactRequestDetails requestDetails = AzureDevOpsTasArtifactRequestDetails.builder()
                                                              .feed("testFeed")
                                                              .scope("org")
                                                              .packageType("maven")
                                                              .packageName("test.package.name:package-test")
                                                              .version("")
                                                              .identifier("PACKAGE")
                                                              .build();

    final TasArtifactDownloadContext downloadContext =
        createDownloadContext(ArtifactSourceType.AZURE_ARTIFACTS, requestDetails, azureArtifactsConnectorDTO);

    try {
      assertThatThrownBy(() -> cfCommandTaskHelperNG.downloadPackageArtifact(downloadContext, logCallback))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessageContaining("Artifact version is invalid");
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(downloadContext.getWorkingDirectory().getAbsolutePath());
    }
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testDownloadArtifactoryArtifactInvalidRequestDetails() throws Exception {
    final ArtifactoryConnectorDTO connector = ArtifactoryConnectorDTO.builder().build();
    final TasArtifactRequestDetails artifactRequestDetails = mock(TasArtifactRequestDetails.class);
    final TasArtifactDownloadContext downloadContext =
        createDownloadContext(ArtifactSourceType.ARTIFACTORY_REGISTRY, artifactRequestDetails, connector);

    doReturn("test").when(artifactRequestDetails).getArtifactName();

    try {
      assertThatThrownBy(() -> cfCommandTaskHelperNG.downloadPackageArtifact(downloadContext, logCallback))
          .isInstanceOf(HintException.class);
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(downloadContext.getWorkingDirectory().getAbsolutePath());
    }
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testDownloadNexusNugetArtifact() throws Exception {
    testDownloadNexusRequest(NEXUS_NUGET_DOWNLOAD_URL, RepositoryFormat.nuget.name(), NEXUS_NUGET_ARTIFACT_NAME,
        ArtifactType.NUGET, ArtifactSourceType.NEXUS2_REGISTRY);
    testDownloadNexusRequest(NEXUS_NUGET_DOWNLOAD_URL, RepositoryFormat.nuget.name(), NEXUS_NUGET_ARTIFACT_NAME,
        ArtifactType.NUGET, ArtifactSourceType.NEXUS3_REGISTRY);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetAppNameBasedOnGuidForBlueGreenDeployment() throws Exception {
    List<ApplicationSummary> previousReleases = new ArrayList<>();
    previousReleases.add(ApplicationSummary.builder()
                             .name("a_s_e__1")
                             .diskQuota(1)
                             .requestedState("RUNNING")
                             .id("1")
                             .instances(0)
                             .memoryLimit(1)
                             .runningInstances(0)
                             .build());
    previousReleases.add(ApplicationSummary.builder()
                             .name("a_s_e__2")
                             .diskQuota(1)
                             .requestedState("RUNNING")
                             .id("2")
                             .instances(0)
                             .memoryLimit(1)
                             .runningInstances(0)
                             .build());
    doReturn(previousReleases).when(cfDeploymentManager).getPreviousReleases(any(), anyString());

    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    assertThat(cfCommandTaskHelperNG.getAppNameBasedOnGuidForBlueGreenDeployment(cfRequestConfig, "a_s_e", "1"))
        .isEqualTo(Arrays.asList("a_s_e__1"));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testDownloadNexusMavenArtifact() throws Exception {
    testDownloadNexusRequest(NEXUS2_MAVEN_DOWNLOAD_URL, RepositoryFormat.maven.name(), NEXUS_MAVEN_ARTIFACT_NAME, ZIP,
        ArtifactSourceType.NEXUS2_REGISTRY);
    testDownloadNexusRequest(NEXUS_MAVEN_DOWNLOAD_URL, RepositoryFormat.maven.name(), NEXUS_MAVEN_ARTIFACT_NAME,
        ArtifactType.JAR, ArtifactSourceType.NEXUS3_REGISTRY);
  }

  private void testDownloadNexusRequest(String artifactUrl, String repositoryFormat, String expectedArtifactName,
      ArtifactType expectedArtifactType, ArtifactSourceType artifactSourceType) throws Exception {
    String version = "3.x";
    if (ArtifactSourceType.NEXUS2_REGISTRY.equals(artifactSourceType)) {
      version = "2.x";
    }
    final NexusConnectorDTO connectorDTO =
        NexusConnectorDTO.builder()
            .version(version)
            .auth(NexusAuthenticationDTO.builder().credentials(NexusUsernamePasswordAuthDTO.builder().build()).build())
            .build();
    final NexusTasArtifactRequestDetails artifactRequestDetails =
        NexusTasArtifactRequestDetails.builder()
            .certValidationRequired(false)
            .artifactUrl(artifactUrl)
            .repositoryFormat(repositoryFormat)
            .identifier("test")
            .metadata(ImmutableMap.of("url", artifactUrl, "package", "test-nuget", "version", "1.0.0"))
            .build();
    final NexusRequest nexusRequest = NexusRequest.builder().build();
    final TasArtifactDownloadContext downloadContext =
        createDownloadContext(artifactSourceType, artifactRequestDetails, connectorDTO);

    doReturn(nexusRequest).when(nexusMapper).toNexusRequest(connectorDTO, artifactRequestDetails);

    try (InputStream artifactStream = new ByteArrayInputStream(ARTIFACT_FILE_CONTENT.getBytes())) {
      doReturn(Pair.of("artifact", artifactStream))
          .when(nexusService)
          .downloadArtifactByUrl(nexusRequest, expectedArtifactName, artifactUrl);

      TasArtifactDownloadResponse downloadResponse =
          cfCommandTaskHelperNG.downloadPackageArtifact(downloadContext, logCallback);

      List<String> fileContent = Files.readAllLines(downloadResponse.getArtifactFile().toPath());
      assertThat(fileContent).containsOnly(ARTIFACT_FILE_CONTENT);
      assertThat(downloadResponse.getArtifactType()).isEqualTo(expectedArtifactType);
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(downloadContext.getWorkingDirectory().getAbsolutePath());
    }
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testDownloadWrongArtifactDetails() throws Exception {
    testDownloadNexusRequestWrongArtifactDetails(ArtifactSourceType.NEXUS3_REGISTRY);
    testDownloadNexusRequestWrongArtifactDetails(ArtifactSourceType.AMAZONS3);
    testDownloadNexusRequestWrongArtifactDetails(ArtifactSourceType.JENKINS);
    testDownloadNexusRequestWrongArtifactDetails(ArtifactSourceType.AZURE_ARTIFACTS);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testDownloadWrongConnector() throws Exception {
    final JenkinsConnectorDTO connectorDTO =
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
    final NexusTasArtifactRequestDetails artifactRequestDetails =
        NexusTasArtifactRequestDetails.builder()
            .certValidationRequired(false)
            .artifactUrl(NEXUS2_MAVEN_DOWNLOAD_URL)
            .repositoryFormat(RepositoryFormat.maven.name())
            .identifier("test")
            .metadata(ImmutableMap.of("url", NEXUS2_MAVEN_DOWNLOAD_URL, "package", "test-nuget", "version", "1.0.0"))
            .build();
    final TasArtifactDownloadContext downloadContext =
        createDownloadContext(ArtifactSourceType.NEXUS2_REGISTRY, artifactRequestDetails, connectorDTO);
    assertThatThrownBy(() -> cfCommandTaskHelperNG.downloadPackageArtifact(downloadContext, logCallback))
        .isInstanceOf(HintException.class)
        .hasMessageContaining("Configure nexus connector for nexus configuration");
  }

  private void testDownloadNexusRequestWrongArtifactDetails(ArtifactSourceType artifactSourceType) throws Exception {
    final NexusConnectorDTO connectorDTO =
        NexusConnectorDTO.builder()
            .version("3.x")
            .auth(NexusAuthenticationDTO.builder().credentials(NexusUsernamePasswordAuthDTO.builder().build()).build())
            .build();
    final ArtifactoryTasArtifactRequestDetails requestDetails =
        ArtifactoryTasArtifactRequestDetails.builder().artifactPaths(singletonList("test")).build();
    final TasArtifactDownloadContext downloadContext =
        createDownloadContext(artifactSourceType, requestDetails, connectorDTO);
    assertThatThrownBy(() -> cfCommandTaskHelperNG.downloadPackageArtifact(downloadContext, logCallback))
        .isInstanceOf(HintException.class)
        .hasMessageContaining("Please contact harness support team");
  }

  private TasArtifactDownloadContext createDownloadContext(ArtifactSourceType sourceType,
      TasArtifactRequestDetails requestDetails, ConnectorConfigDTO connector) throws Exception {
    return TasArtifactDownloadContext.builder()
        .artifactConfig(TasPackageArtifactConfig.builder()
                            .sourceType(sourceType)
                            .artifactDetails(requestDetails)
                            .connectorConfig(connector)
                            .encryptedDataDetails(singletonList(EncryptedDataDetail.builder().build()))
                            .build())
        .workingDirectory(new File(Files.createTempDirectory("testArtifact").toString()))
        .build();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUnmapRoutesIfAppDownsizedToZeroStandardBG() throws PivotalClientApiException {
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();

    CfDeployCommandRequestNG cfCommandDeployRequest = CfDeployCommandRequestNG.builder()
                                                          .tasInfraConfig(TasInfraConfig.builder().build())
                                                          .isStandardBlueGreen(true)
                                                          .build();
    cfCommandTaskHelperNG.unmapRoutesIfAppDownsizedToZero(cfCommandDeployRequest, cfRequestConfig, logCallback);
    verify(pcfCommandTaskHelper, never()).unmapExistingRouteMaps(any(), any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUnmapRoutesIfAppDownsizedToZeroEmptyAppDetails() throws PivotalClientApiException {
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    CfDeployCommandRequestNG cfCommandDeployRequest = CfDeployCommandRequestNG.builder()
                                                          .tasInfraConfig(TasInfraConfig.builder().build())
                                                          .isStandardBlueGreen(false)
                                                          .downsizeAppDetail(null)
                                                          .build();
    cfCommandTaskHelperNG.unmapRoutesIfAppDownsizedToZero(cfCommandDeployRequest, cfRequestConfig, logCallback);
    verify(pcfCommandTaskHelper, never()).unmapExistingRouteMaps(any(), any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUnmapRoutesIfAppDownsizedToZeroEmptyAppName() throws PivotalClientApiException {
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    CfDeployCommandRequestNG cfCommandDeployRequest = CfDeployCommandRequestNG.builder()
                                                          .isStandardBlueGreen(false)
                                                          .tasInfraConfig(TasInfraConfig.builder().build())
                                                          .downsizeAppDetail(TasApplicationInfo.builder().build())
                                                          .build();
    cfCommandTaskHelperNG.unmapRoutesIfAppDownsizedToZero(cfCommandDeployRequest, cfRequestConfig, logCallback);
    verify(pcfCommandTaskHelper, never()).unmapExistingRouteMaps(any(), any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUnmapRoutesIfAppDownsizedToZeroEmptyAppNameNumberOfInstancesNotZero()
      throws PivotalClientApiException {
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    String appName = "appName";
    CfDeployCommandRequestNG cfCommandDeployRequest =
        CfDeployCommandRequestNG.builder()
            .isStandardBlueGreen(false)
            .tasInfraConfig(TasInfraConfig.builder().build())
            .downsizeAppDetail(TasApplicationInfo.builder().applicationName(appName).build())
            .build();
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("10")
                                              .diskQuota(1)
                                              .instances(1)
                                              .memoryLimit(1)
                                              .name("app1")
                                              .requestedState("STOPPED")
                                              .stack("")
                                              .urls(Arrays.asList("url1"))
                                              .runningInstances(1)
                                              .build();
    when(cfDeploymentManager.getApplicationByName(any())).thenReturn(applicationDetail);
    cfCommandTaskHelperNG.unmapRoutesIfAppDownsizedToZero(cfCommandDeployRequest, cfRequestConfig, logCallback);
    verify(pcfCommandTaskHelper, never()).unmapExistingRouteMaps(any(), any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUnmapRoutesIfAppDownsizedToZero() throws PivotalClientApiException {
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    String appName = "appName";
    CfDeployCommandRequestNG cfCommandDeployRequest =
        CfDeployCommandRequestNG.builder()
            .isStandardBlueGreen(false)
            .tasInfraConfig(TasInfraConfig.builder().build())
            .downsizeAppDetail(TasApplicationInfo.builder().applicationName(appName).build())
            .build();
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("10")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("app1")
                                              .requestedState("STOPPED")
                                              .stack("")
                                              .urls(Arrays.asList("url1"))
                                              .runningInstances(1)
                                              .build();
    when(cfDeploymentManager.getApplicationByName(any())).thenReturn(applicationDetail);
    doNothing().when(pcfCommandTaskHelper).unmapExistingRouteMaps(applicationDetail, cfRequestConfig, logCallback);
    cfCommandTaskHelperNG.unmapRoutesIfAppDownsizedToZero(cfCommandDeployRequest, cfRequestConfig, logCallback);
    assertThat(cfRequestConfig.getApplicationName()).isEqualTo(appName);
    verify(pcfCommandTaskHelper, times(1))
        .unmapExistingRouteMaps(eq(applicationDetail), eq(cfRequestConfig), eq(logCallback));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpsizeNewApplication() throws PivotalClientApiException, IOException {
    int previousCount = 2;
    int desiredCount = 5;
    CfDeployCommandRequestNG cfDeployCommandRequestNG = CfDeployCommandRequestNG.builder()
                                                            .tasInfraConfig(TasInfraConfig.builder().build())
                                                            .newReleaseName("releaseName")
                                                            .upsizeCount(previousCount)
                                                            .build();
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();

    List<InstanceDetail> instancesAfterUpsize = new ArrayList<>();
    instancesAfterUpsize.add(InstanceDetail.builder().index("idx1").state("RUNNING").build());
    instancesAfterUpsize.add(InstanceDetail.builder().index("idx1").state("RUNNING").build());

    List<CfServiceData> cfServiceDataUpdated = new ArrayList<>();
    cfServiceDataUpdated.add(CfServiceData.builder().desiredCount(desiredCount).previousCount(previousCount).build());

    ApplicationDetail applicationDetail = getApplicationDetail(Collections.emptyList());
    List<CfInternalInstanceElement> pcfInstanceElements = new ArrayList<>();

    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(cfRequestConfig);
    doReturn(getApplicationDetail(instancesAfterUpsize))
        .when(cfDeploymentManager)
        .upsizeApplicationWithSteadyStateCheck(cfRequestConfig, logCallback);
    String path = "./test" + System.currentTimeMillis();
    CfAppAutoscalarRequestData autoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                           .applicationName(APP_NAME)
                                                           .applicationGuid(APP_ID)
                                                           .configPathVar(path)
                                                           .build();

    cfCommandTaskHelperNG.upsizeNewApplication(logCallback, cfDeployCommandRequestNG, cfServiceDataUpdated,
        cfRequestConfig, applicationDetail, pcfInstanceElements, autoscalarRequestData);

    assertThat(pcfInstanceElements.size()).isEqualTo(previousCount);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testEnableAutoscalerIfNeeded() throws PivotalClientApiException {
    ArgumentCaptor<CfAppAutoscalarRequestData> cfAppAutoscalarRequestDataArgumentCaptor =
        ArgumentCaptor.forClass(CfAppAutoscalarRequestData.class);
    doReturn(true)
        .when(cfDeploymentManager)
        .changeAutoscalarState(cfAppAutoscalarRequestDataArgumentCaptor.capture(), any(), anyBoolean());
    String path = "./test" + System.currentTimeMillis();
    CfAppAutoscalarRequestData autoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                           .applicationName(APP_NAME)
                                                           .applicationGuid(APP_ID)
                                                           .configPathVar(path)
                                                           .build();

    cfCommandTaskHelperNG.enableAutoscalerIfNeeded(ApplicationDetail.builder()
                                                       .id("1")
                                                       .diskQuota(1)
                                                       .instances(0)
                                                       .memoryLimit(1)
                                                       .name("test-app")
                                                       .stack("")
                                                       .requestedState("STOPPED")
                                                       .runningInstances(0)
                                                       .urls(Arrays.asList("1.com", "2.com"))
                                                       .build(),
        autoscalarRequestData, logCallback);
    CfAppAutoscalarRequestData cfAppAutoscalarRequestData = cfAppAutoscalarRequestDataArgumentCaptor.getValue();
    assertThat(cfAppAutoscalarRequestData.getApplicationName()).isEqualTo("test-app");
    assertThat(cfAppAutoscalarRequestData.getApplicationGuid()).isEqualTo("1");
    assertThat(cfAppAutoscalarRequestData.isExpectedEnabled()).isFalse();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUnmapRoutesFromNewAppAfterDownsize() throws PivotalClientApiException {
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();

    // no new applications
    cfCommandTaskHelperNG.unmapRoutesFromNewAppAfterDownsize(logCallback, null, cfRequestConfig);
    verify(pcfCommandTaskHelper, never()).unmapExistingRouteMaps(any(), any(), any());

    // empty name of new application details
    cfCommandTaskHelperNG.unmapRoutesFromNewAppAfterDownsize(
        logCallback, TasApplicationInfo.builder().build(), cfRequestConfig);
    verify(pcfCommandTaskHelper, never()).unmapExistingRouteMaps(any(), any(), any());

    String appName = "appName";
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("10")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("app1")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .urls(Collections.singletonList("url1"))
                                              .runningInstances(1)
                                              .build();
    when(cfDeploymentManager.getApplicationByName(any())).thenReturn(applicationDetail);
    cfCommandTaskHelperNG.unmapRoutesFromNewAppAfterDownsize(
        logCallback, TasApplicationInfo.builder().applicationName(appName).build(), cfRequestConfig);
    assertThat(cfRequestConfig.getApplicationName()).isEqualTo("app1");
    verify(pcfCommandTaskHelper).unmapExistingRouteMaps(applicationDetail, cfRequestConfig, logCallback);
  }
  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testEnableAutoscalerIfNeededTasApplicationInfo() throws PivotalClientApiException {
    ArgumentCaptor<CfAppAutoscalarRequestData> cfAppAutoscalarRequestDataArgumentCaptor =
        ArgumentCaptor.forClass(CfAppAutoscalarRequestData.class);
    doReturn(true)
        .when(cfDeploymentManager)
        .changeAutoscalarState(cfAppAutoscalarRequestDataArgumentCaptor.capture(), any(), anyBoolean());
    String path = "./test" + System.currentTimeMillis();
    CfAppAutoscalarRequestData autoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                           .applicationName(APP_NAME)
                                                           .applicationGuid(APP_ID)
                                                           .configPathVar(path)
                                                           .build();

    cfCommandTaskHelperNG.enableAutoscalerIfNeeded(
        TasApplicationInfo.builder().applicationName("old-app").applicationGuid("1234").build(), autoscalarRequestData,
        logCallback);
    CfAppAutoscalarRequestData cfAppAutoscalarRequestData = cfAppAutoscalarRequestDataArgumentCaptor.getValue();
    assertThat(cfAppAutoscalarRequestData.getApplicationName()).isEqualTo("old-app");
    assertThat(cfAppAutoscalarRequestData.getApplicationGuid()).isEqualTo("1234");
    assertThat(cfAppAutoscalarRequestData.isExpectedEnabled()).isFalse();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testRestoreRoutesForOldApplication() throws PivotalClientApiException {
    String appName = APP_NAME;
    List<String> urls = Collections.singletonList("url1");
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("10")
                                              .diskQuota(1)
                                              .instances(1)
                                              .memoryLimit(1)
                                              .name("app1")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(1)
                                              .build();
    when(cfDeploymentManager.getApplicationByName(any())).thenReturn(applicationDetail);
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();

    // map route maps
    cfCommandTaskHelperNG.restoreRoutesForOldApplication(
        TasApplicationInfo.builder().applicationName(appName).attachedRoutes(urls).build(), cfRequestConfig,
        logCallback, CfRollbackCommandResult.builder().build());
    assertThat(cfRequestConfig.getApplicationName()).isEqualTo(appName);
    verify(pcfCommandTaskHelper).mapRouteMaps(appName, urls, cfRequestConfig, logCallback);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testconfigureAutoscalarIfNeeded() throws PivotalClientApiException, IOException {
    CfDeployCommandRequestNG cfDeployCommandRequestNG = CfDeployCommandRequestNG.builder()
                                                            .timeoutIntervalInMin(5)
                                                            .tasInfraConfig(TasInfraConfig.builder().build())
                                                            .build();
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("10")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("app1")
                                              .requestedState("STOPPED")
                                              .stack("")
                                              .urls(Arrays.asList("url1"))
                                              .runningInstances(1)
                                              .build();
    CfAppAutoscalarRequestData pcfAppAutoscalarRequestData =
        CfAppAutoscalarRequestData.builder().configPathVar("/tmp").build();

    // don't use autoscalar
    cfDeployCommandRequestNG.setUseAppAutoScalar(false);
    cfCommandTaskHelperNG.configureAutoscalarIfNeeded(
        cfDeployCommandRequestNG, applicationDetail, pcfAppAutoscalarRequestData, logCallback);
    verify(cfDeploymentManager, never()).performConfigureAutoscalar(any(), any());

    // empty PcfManifestsPackage
    cfDeployCommandRequestNG.setUseAppAutoScalar(true);
    cfDeployCommandRequestNG.setTasManifestsPackage(null);
    cfCommandTaskHelperNG.configureAutoscalarIfNeeded(
        cfDeployCommandRequestNG, applicationDetail, pcfAppAutoscalarRequestData, logCallback);
    verify(cfDeploymentManager, never()).performConfigureAutoscalar(any(), any());

    // empty autoscalarManifestsYaml
    cfDeployCommandRequestNG.setTasManifestsPackage(TasManifestsPackage.builder().build());
    cfCommandTaskHelperNG.configureAutoscalarIfNeeded(
        cfDeployCommandRequestNG, applicationDetail, pcfAppAutoscalarRequestData, logCallback);
    verify(cfDeploymentManager, never()).performConfigureAutoscalar(any(), any());

    // max count bigger than update count
    cfDeployCommandRequestNG.setTasManifestsPackage(
        TasManifestsPackage.builder().autoscalarManifestYml("autoscalarManifestYml").build());
    cfDeployCommandRequestNG.setMaxCount(2);
    cfDeployCommandRequestNG.setUpsizeCount(1);
    cfCommandTaskHelperNG.configureAutoscalarIfNeeded(
        cfDeployCommandRequestNG, applicationDetail, pcfAppAutoscalarRequestData, logCallback);
    verify(cfDeploymentManager, never()).performConfigureAutoscalar(any(), any());
    doReturn(null).when(pcfCommandTaskHelper).createYamlFileLocally(anyString(), anyString());
    // max count bigger than update count
    ArgumentCaptor<CfAppAutoscalarRequestData> argumentCaptor =
        ArgumentCaptor.forClass(CfAppAutoscalarRequestData.class);

    cfDeployCommandRequestNG.setTasManifestsPackage(
        TasManifestsPackage.builder().autoscalarManifestYml("autoscalarManifestYml").build());
    cfDeployCommandRequestNG.setMaxCount(2);
    cfDeployCommandRequestNG.setUpsizeCount(2);
    cfCommandTaskHelperNG.configureAutoscalarIfNeeded(
        cfDeployCommandRequestNG, applicationDetail, pcfAppAutoscalarRequestData, logCallback);
    verify(cfDeploymentManager, times(1)).performConfigureAutoscalar(argumentCaptor.capture(), any());
    CfAppAutoscalarRequestData cfAppAutoscalarRequestData =
        (CfAppAutoscalarRequestData) argumentCaptor.getAllValues().get(0);
    assertThat(cfAppAutoscalarRequestData.getAutoscalarFilePath()).contains("/tmp/autoscalar_");
    assertThat(cfAppAutoscalarRequestData.getApplicationName()).isEqualTo("app1");
    assertThat(cfAppAutoscalarRequestData.getApplicationGuid()).isEqualTo("10");
    assertThat(cfAppAutoscalarRequestData.getTimeoutInMins()).isEqualTo(5);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testRestoreRoutesForOldApplicationNoAppsToDownsize() throws PivotalClientApiException {
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("10")
                                              .diskQuota(1)
                                              .instances(1)
                                              .memoryLimit(1)
                                              .name("app1")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(1)
                                              .build();
    when(cfDeploymentManager.getApplicationByName(any())).thenReturn(applicationDetail);
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();

    cfCommandTaskHelperNG.restoreRoutesForOldApplication(
        null, cfRequestConfig, logCallback, CfRollbackCommandResult.builder().build());
    verify(pcfCommandTaskHelper, never())
        .mapRouteMaps(anyString(), anyList(), any(CfRequestConfig.class), any(LogCallback.class));
  }
  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testDownsizePreviousReleases() throws Exception {
    CfDeployCommandRequestNG request = CfDeployCommandRequestNG.builder()
                                           .accountId(ACCOUNT_ID)
                                           .downsizeAppDetail(null)
                                           .tasInfraConfig(TasInfraConfig.builder().build())
                                           .build();

    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    List<CfServiceData> cfServiceDataList = new ArrayList<>();
    List<CfInternalInstanceElement> pcfInstanceElements = new ArrayList<>();

    // No old app exists
    cfCommandTaskHelperNG.downsizePreviousReleases(request, cfRequestConfig, logCallback, cfServiceDataList, 0,
        pcfInstanceElements, CfAppAutoscalarRequestData.builder().build());
    verify(cfDeploymentManager, never()).getApplicationByName(any());

    InstanceDetail instanceDetail0 = InstanceDetail.builder()
                                         .cpu(0.0)
                                         .index("0")
                                         .diskQuota(0l)
                                         .diskUsage(0l)
                                         .memoryQuota(0l)
                                         .memoryUsage(0l)
                                         .state("RUNNING")
                                         .build();

    InstanceDetail instanceDetail1 = InstanceDetail.builder()
                                         .cpu(0.0)
                                         .index("1")
                                         .diskQuota(0l)
                                         .diskUsage(0l)
                                         .memoryQuota(0l)
                                         .memoryUsage(0l)
                                         .state("RUNNING")
                                         .build();
    // old app exists, but downsize is not required.
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .diskQuota(1)
                                              .id("id")
                                              .name("app")
                                              .instanceDetails(instanceDetail0, instanceDetail1)
                                              .instances(2)
                                              .memoryLimit(1)
                                              .stack("stack")
                                              .runningInstances(2)
                                              .requestedState("RUNNING")
                                              .build();

    ApplicationDetail applicationDetailAfterDownsize = ApplicationDetail.builder()
                                                           .diskQuota(1)
                                                           .id("id")
                                                           .name("app")
                                                           .instanceDetails(instanceDetail0)
                                                           .instances(1)
                                                           .memoryLimit(1)
                                                           .stack("stack")
                                                           .runningInstances(1)
                                                           .requestedState("RUNNING")
                                                           .build();

    request.setDownsizeAppDetail(
        TasApplicationInfo.builder().applicationGuid("1").applicationName("app").runningCount(1).build());
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());

    request.setStandardBlueGreen(true);
    doReturn(applicationDetailAfterDownsize).when(cfDeploymentManager).resizeApplication(any());
    request.setDownSizeCount(2);
    doReturn(applicationDetail).when(pcfCommandTaskHelper).printApplicationDetail(any(), any());
    cfCommandTaskHelperNG.downsizePreviousReleases(request, cfRequestConfig, logCallback, cfServiceDataList, 2,
        pcfInstanceElements, CfAppAutoscalarRequestData.builder().build());
    verify(cfDeploymentManager, times(1)).getApplicationByName(any());

    // expectedCount = currentCount, no downsize should be called.
    request.setStandardBlueGreen(false);
    cfCommandTaskHelperNG.downsizePreviousReleases(request, cfRequestConfig, logCallback, cfServiceDataList, 2,
        pcfInstanceElements, CfAppAutoscalarRequestData.builder().applicationGuid("id").applicationName("app").build());
    verify(cfDeploymentManager, times(3)).getApplicationByName(any());
    assertThat(cfServiceDataList.size()).isEqualTo(2);
    assertThat(cfServiceDataList.get(0).getDesiredCount()).isEqualTo(2);
    assertThat(cfServiceDataList.get(0).getPreviousCount()).isEqualTo(2);
    assertThat(cfServiceDataList.get(0).getId()).isEqualTo("id");
    assertThat(cfServiceDataList.get(0).getName()).isEqualTo("app");

    // Downsize application from 2 to 1
    doReturn(applicationDetailAfterDownsize).when(cfDeploymentManager).resizeApplication(any());
    pcfInstanceElements.clear();
    cfServiceDataList.clear();
    cfCommandTaskHelperNG.downsizePreviousReleases(request, cfRequestConfig, logCallback, cfServiceDataList, 1,
        pcfInstanceElements, CfAppAutoscalarRequestData.builder().build());
    verify(cfDeploymentManager, times(5)).getApplicationByName(any());
    assertThat(cfServiceDataList.size()).isEqualTo(1);
    assertThat(cfServiceDataList.get(0).getDesiredCount()).isEqualTo(1);
    assertThat(cfServiceDataList.get(0).getPreviousCount()).isEqualTo(2);
    assertThat(cfServiceDataList.get(0).getId()).isEqualTo("id");
    assertThat(cfServiceDataList.get(0).getName()).isEqualTo("app");

    assertThat(pcfInstanceElements.size()).isEqualTo(1);
    assertThat(pcfInstanceElements.get(0).getApplicationId()).isEqualTo("id");
    assertThat(pcfInstanceElements.get(0).getDisplayName()).isEqualTo("app");
    assertThat(pcfInstanceElements.get(0).getInstanceIndex()).isEqualTo("0");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testDownsizePreviousReleases_autoscalar() throws Exception {
    CfDeployCommandRequestNG request = CfDeployCommandRequestNG.builder()
                                           .accountId(ACCOUNT_ID)
                                           .downsizeAppDetail(null)
                                           .useAppAutoScalar(true)
                                           .tasInfraConfig(TasInfraConfig.builder().build())
                                           .build();

    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    List<CfServiceData> cfServiceDataList = new ArrayList<>();
    List<CfInternalInstanceElement> pcfInstanceElements = new ArrayList<>();

    InstanceDetail instanceDetail0 = InstanceDetail.builder()
                                         .cpu(0.0)
                                         .index("0")
                                         .diskQuota(0l)
                                         .diskUsage(0l)
                                         .memoryQuota(0l)
                                         .memoryUsage(0l)
                                         .state("RUNNING")
                                         .build();

    InstanceDetail instanceDetail1 = InstanceDetail.builder()
                                         .cpu(0.0)
                                         .index("1")
                                         .diskQuota(0l)
                                         .diskUsage(0l)
                                         .memoryQuota(0l)
                                         .memoryUsage(0l)
                                         .state("RUNNING")
                                         .build();
    // old app exists, but downsize is not required.
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .diskQuota(1)
                                              .id("id")
                                              .name("app")
                                              .instanceDetails(instanceDetail0, instanceDetail1)
                                              .instances(2)
                                              .memoryLimit(1)
                                              .stack("stack")
                                              .runningInstances(2)
                                              .requestedState("RUNNING")
                                              .build();

    ApplicationDetail applicationDetailAfterDownsize = ApplicationDetail.builder()
                                                           .diskQuota(1)
                                                           .id("id")
                                                           .name("app")
                                                           .instanceDetails(instanceDetail0)
                                                           .instances(1)
                                                           .memoryLimit(1)
                                                           .stack("stack")
                                                           .runningInstances(1)
                                                           .requestedState("RUNNING")
                                                           .build();

    request.setDownsizeAppDetail(
        TasApplicationInfo.builder().applicationGuid("1").applicationName("app").runningCount(1).build());
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());

    // Downsize application from 2 to 1
    doReturn(applicationDetailAfterDownsize).when(cfDeploymentManager).resizeApplication(any());
    doReturn(true).when(cfDeploymentManager).changeAutoscalarState(any(), any(), anyBoolean());
    pcfInstanceElements.clear();
    cfServiceDataList.clear();
    cfCommandTaskHelperNG.downsizePreviousReleases(request, cfRequestConfig, logCallback, cfServiceDataList, 1,
        pcfInstanceElements,
        CfAppAutoscalarRequestData.builder()
            .applicationName(applicationDetail.getName())
            .applicationGuid(applicationDetail.getId())
            .build());
    verify(cfDeploymentManager, times(2)).getApplicationByName(any());
    verify(cfDeploymentManager, times(1)).changeAutoscalarState(any(), any(), anyBoolean());
    assertThat(cfServiceDataList.size()).isEqualTo(1);
    assertThat(cfServiceDataList.get(0).getDesiredCount()).isEqualTo(1);
    assertThat(cfServiceDataList.get(0).getPreviousCount()).isEqualTo(2);
    assertThat(cfServiceDataList.get(0).getId()).isEqualTo("id");
    assertThat(cfServiceDataList.get(0).getName()).isEqualTo("app");
    assertThat(cfServiceDataList.get(0).isDisableAutoscalarPerformed()).isTrue();

    assertThat(pcfInstanceElements.size()).isEqualTo(1);
    assertThat(pcfInstanceElements.get(0).getApplicationId()).isEqualTo("id");
    assertThat(pcfInstanceElements.get(0).getDisplayName()).isEqualTo("app");
    assertThat(pcfInstanceElements.get(0).getInstanceIndex()).isEqualTo("0");
  }
  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testRestoreRoutesForOldApplicationEmptyUrls() throws PivotalClientApiException {
    String appName = APP_NAME;
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("10")
                                              .diskQuota(1)
                                              .instances(1)
                                              .memoryLimit(1)
                                              .name("app1")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(1)
                                              .build();
    when(cfDeploymentManager.getApplicationByName(any())).thenReturn(applicationDetail);
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();

    cfCommandTaskHelperNG.restoreRoutesForOldApplication(TasApplicationInfo.builder().applicationName(appName).build(),
        cfRequestConfig, logCallback, CfRollbackCommandResult.builder().build());
    assertThat(cfRequestConfig.getApplicationName()).isEqualTo(appName);
    verify(pcfCommandTaskHelper, never())
        .mapRouteMaps(anyString(), anyList(), any(CfRequestConfig.class), any(LogCallback.class));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testRestoreRoutesForOldApplication2() throws PivotalClientApiException {
    String appName = "appName";
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("10")
                                              .diskQuota(1)
                                              .instances(1)
                                              .memoryLimit(1)
                                              .name("app1")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .urls(Collections.singletonList("url1"))
                                              .runningInstances(1)
                                              .build();
    when(cfDeploymentManager.getApplicationByName(any())).thenReturn(applicationDetail);
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();

    List<String> urls = Collections.singletonList("url2");
    cfCommandTaskHelperNG.restoreRoutesForOldApplication(
        TasApplicationInfo.builder().applicationName(appName).attachedRoutes(urls).build(), cfRequestConfig,
        logCallback, CfRollbackCommandResult.builder().build());
    assertThat(cfRequestConfig.getApplicationName()).isEqualTo(appName);
    verify(pcfCommandTaskHelper).mapRouteMaps(appName, urls, cfRequestConfig, logCallback);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testDownSizeListOfInstances() throws Exception {
    ApplicationDetail detail = ApplicationDetail.builder()
                                   .diskQuota(1)
                                   .id("id")
                                   .name("app")
                                   .instances(0)
                                   .memoryLimit(1)
                                   .stack("stack")
                                   .runningInstances(2)
                                   .requestedState("RUNNING")
                                   .build();

    doReturn(detail).when(cfDeploymentManager).getApplicationByName(any());
    doReturn(detail).when(cfDeploymentManager).resizeApplication(any());

    List<CfServiceData> cfServiceDataListToBeUpdated = new ArrayList<>();
    List<CfServiceData> cfServiceDataList = new ArrayList<>();
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    cfServiceDataList.add(CfServiceData.builder().name("test").desiredCount(2).build());

    doReturn(true).when(cfDeploymentManager).changeAutoscalarState(any(), any(), anyBoolean());
    cfCommandTaskHelperNG.downSizeListOfInstances(logCallback, cfServiceDataListToBeUpdated, cfRequestConfig,
        cfServiceDataList, true,
        CfAppAutoscalarRequestData.builder().applicationName(detail.getName()).applicationGuid(detail.getId()).build());
    verify(cfDeploymentManager, times(1)).changeAutoscalarState(any(), any(), anyBoolean());
    assertThat(cfServiceDataListToBeUpdated.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testDownSizeListOfInstancesFailAutoscaler() throws Exception {
    ApplicationDetail detail = ApplicationDetail.builder()
                                   .diskQuota(1)
                                   .id("id")
                                   .name("app")
                                   .instances(0)
                                   .memoryLimit(1)
                                   .stack("stack")
                                   .runningInstances(2)
                                   .requestedState("RUNNING")
                                   .build();

    doReturn(detail).when(cfDeploymentManager).getApplicationByName(any());
    doReturn(detail).when(cfDeploymentManager).resizeApplication(any());

    List<CfServiceData> cfServiceDataListToBeUpdated = new ArrayList<>();
    List<CfServiceData> cfServiceDataList = new ArrayList<>();
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    cfServiceDataList.add(CfServiceData.builder().name("test").desiredCount(2).build());

    doThrow(new PivotalClientApiException("#Throwing exception to test if flow stops or not"))
        .when(pcfCommandTaskHelper)
        .disableAutoscalar(any(), any());

    cfCommandTaskHelperNG.downSizeListOfInstances(logCallback, cfServiceDataListToBeUpdated, cfRequestConfig,
        cfServiceDataList, true,
        CfAppAutoscalarRequestData.builder().applicationName(detail.getName()).applicationGuid(detail.getId()).build());
    verify(cfDeploymentManager, times(0)).changeAutoscalarState(any(), any(), anyBoolean());
    verify(pcfCommandTaskHelper, times(1)).disableAutoscalar(any(), any());
    verify(pcfCommandTaskHelper, times(1)).disableAutoscalarSafe(any(), any());
    assertThat(cfServiceDataListToBeUpdated.size()).isEqualTo(1);
  }

  private CfCommandRollbackRequest createPcfCommandRollbackRequest(
      boolean downsizeApps, String appName, List<String> urls) {
    return CfCommandRollbackRequest.builder()
        .isStandardBlueGreenWorkflow(false)
        .appsToBeDownSized(downsizeApps
                ? Collections.singletonList(
                    CfAppSetupTimeDetails.builder().applicationName(appName).initialInstanceCount(1).urls(urls).build())
                : null)
        .build();
  }

  private ApplicationDetail getApplicationDetail(List<InstanceDetail> instances) {
    return ApplicationDetail.builder()
        .diskQuota(1)
        .id("appId")
        .name("appName")
        .memoryLimit(1)
        .stack("stack")
        .runningInstances(1)
        .requestedState("RUNNING")
        .instances(2)
        .instanceDetails(instances)
        .build();
  }
}