/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.mappers;

import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.PRAGYESH;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.InputSetValidatorType;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.bean.yaml.AMIArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AmazonS3ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactoryRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AzureArtifactsConfig;
import io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.EcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GithubPackagesArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleArtifactRegistryConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleCloudSourceArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleCloudSourceFetchType;
import io.harness.cdng.artifact.bean.yaml.GoogleCloudStorageArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.JenkinsArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.NexusRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomArtifactScriptInfo;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomArtifactScriptSourceWrapper;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomArtifactScripts;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomScriptInlineSource;
import io.harness.cdng.artifact.bean.yaml.customartifact.FetchAllArtifacts;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.BambooArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.Nexus2RegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryDockerConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryNpmConfig;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.bamboo.BambooConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ami.AMIArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.ami.AMIFilter;
import io.harness.delegate.task.artifacts.ami.AMITag;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryGenericArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.azure.AcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.azureartifacts.AzureArtifactsDelegateRequest;
import io.harness.delegate.task.artifacts.bamboo.BambooArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.custom.CustomArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.ecr.EcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.gar.GarDelegateRequest;
import io.harness.delegate.task.artifacts.gcr.GcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.githubpackages.GithubPackagesArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.googlecloudsource.GoogleCloudSourceArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.googlecloudstorage.GoogleCloudStorageArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.s3.S3ArtifactDelegateRequest;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.validation.InputSetValidator;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.yaml.core.timeout.Timeout;

import software.wings.utils.RepositoryFormat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class ArtifactConfigToDelegateReqMapperTest extends CategoryTest {
  private final String ACCEPT_ALL_REGEX = "\\*";
  private static final String ALL_REGEX = ".*?";
  private static final String LAST_PUBLISHED_EXPRESSION = "<+lastPublished.tag>";
  private static final String TAG = "tag";
  private static final String CONNECTOR_REF = "connectorRef";
  private static final ParameterField LAST_PUBLISHED_EXPRESSION_REGEX =
      ParameterField.createValueFieldWithInputSetValidator(
          LAST_PUBLISHED_EXPRESSION, new InputSetValidator(InputSetValidatorType.REGEX, TAG), true);
  private static final ParameterField LAST_PUBLISHED_EXPRESSION_PARAMETER =
      ParameterField.createValueField(LAST_PUBLISHED_EXPRESSION);
  @Mock DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock DelegateMetricsService delegateMetricsService;
  @Mock SecretManagerClientService ngSecretService;
  @Mock ExceptionManager exceptionManager;
  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetDockerDelegateRequest() {
    DockerHubArtifactConfig dockerHubArtifactConfig =
        DockerHubArtifactConfig.builder().imagePath(ParameterField.createValueField("IMAGE")).build();
    DockerConnectorDTO connectorDTO = DockerConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    DockerArtifactDelegateRequest dockerDelegateRequest = ArtifactConfigToDelegateReqMapper.getDockerDelegateRequest(
        dockerHubArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(dockerDelegateRequest.getDockerConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(dockerDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(dockerDelegateRequest.getImagePath()).isEqualTo(dockerHubArtifactConfig.getImagePath().getValue());
    assertThat(dockerDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.DOCKER_REGISTRY);
    assertThat(dockerDelegateRequest.getTagsList()).isNull();
    assertThat(dockerDelegateRequest.getTag()).isEqualTo("");
    assertThat(dockerDelegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(dockerDelegateRequest.getTagRegex()).isEqualTo("\\*");
    assertThat(dockerDelegateRequest.getShouldFetchDockerV2DigestSHA256()).isEqualTo(false);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetDockerDelegateRequestWithV2SHA256Digest() {
    DockerHubArtifactConfig dockerHubArtifactConfig = DockerHubArtifactConfig.builder()
                                                          .imagePath(ParameterField.createValueField("IMAGE"))
                                                          .digest(ParameterField.createValueField("DIGEST"))
                                                          .build();
    DockerConnectorDTO connectorDTO = DockerConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    DockerArtifactDelegateRequest dockerDelegateRequest = ArtifactConfigToDelegateReqMapper.getDockerDelegateRequest(
        dockerHubArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(dockerDelegateRequest.getDockerConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(dockerDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(dockerDelegateRequest.getImagePath()).isEqualTo(dockerHubArtifactConfig.getImagePath().getValue());
    assertThat(dockerDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.DOCKER_REGISTRY);
    assertThat(dockerDelegateRequest.getTagsList()).isNull();
    assertThat(dockerDelegateRequest.getTag()).isEqualTo("");
    assertThat(dockerDelegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(dockerDelegateRequest.getTagRegex()).isEqualTo("\\*");
    assertThat(dockerDelegateRequest.getShouldFetchDockerV2DigestSHA256()).isEqualTo(true);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetJenkinsDelegateRequest() {
    JenkinsArtifactConfig jenkinsArtifactConfig = JenkinsArtifactConfig.builder()
                                                      .artifactPath(ParameterField.createValueField("ARTIFACT"))
                                                      .jobName(ParameterField.createValueField("JOB"))
                                                      .build(ParameterField.createValueField("build"))
                                                      .build();
    JenkinsConnectorDTO connectorDTO = JenkinsConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getJenkinsDelegateRequest(
            jenkinsArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(jenkinsArtifactDelegateRequest.getJenkinsConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(jenkinsArtifactDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(jenkinsArtifactDelegateRequest.getArtifactPaths())
        .isEqualTo(Arrays.asList(jenkinsArtifactConfig.getArtifactPath().getValue()));
    assertThat(jenkinsArtifactDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.JENKINS);
    assertThat(jenkinsArtifactDelegateRequest.getJobName()).isEqualTo(jenkinsArtifactConfig.getJobName().getValue());
    assertThat(jenkinsArtifactDelegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(jenkinsArtifactDelegateRequest.getBuildNumber()).isEqualTo("build");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetNexusDelegateRequest() {
    NexusRegistryDockerConfig nexusRegistryDockerConfig =
        NexusRegistryDockerConfig.builder()
            .artifactPath(ParameterField.createValueField("IMAGE"))
            .repositoryPort(ParameterField.createValueField("TEST_REPO"))
            .build();

    NexusRegistryArtifactConfig artifactConfig =
        NexusRegistryArtifactConfig.builder()
            .repository(ParameterField.createValueField("TEST_REPO"))
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.docker.name()))
            .nexusRegistryConfigSpec(nexusRegistryDockerConfig)
            .build();
    NexusConnectorDTO connectorDTO = NexusConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    NexusArtifactDelegateRequest delegateRequest = ArtifactConfigToDelegateReqMapper.getNexusArtifactDelegateRequest(
        artifactConfig, connectorDTO, encryptedDataDetailList, "");
    nexusRegistryDockerConfig = (NexusRegistryDockerConfig) artifactConfig.getNexusRegistryConfigSpec();

    assertThat(delegateRequest.getNexusConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(delegateRequest.getRepositoryName()).isEqualTo(artifactConfig.getRepository().getValue());
    assertThat(delegateRequest.getRepositoryFormat()).isEqualTo(RepositoryFormat.docker.name());
    assertThat(delegateRequest.getRepositoryPort()).isEqualTo(nexusRegistryDockerConfig.getRepositoryPort().getValue());
    assertThat(delegateRequest.getArtifactRepositoryUrl()).isNull();
    assertThat(delegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(delegateRequest.getArtifactPath()).isEqualTo(nexusRegistryDockerConfig.getArtifactPath().getValue());
    assertThat(delegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.NEXUS3_REGISTRY);
    assertThat(delegateRequest.getTag()).isEqualTo("");
    assertThat(delegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(delegateRequest.getTagRegex()).isEqualTo("\\*");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetNexusDelegateRequestWithoutPort() {
    NexusRegistryDockerConfig nexusRegistryDockerConfig =
        NexusRegistryDockerConfig.builder().artifactPath(ParameterField.createValueField("IMAGE")).build();
    NexusRegistryArtifactConfig artifactConfig =
        NexusRegistryArtifactConfig.builder()
            .repository(ParameterField.createValueField("TEST_REPO"))
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.docker.name()))
            .nexusRegistryConfigSpec(nexusRegistryDockerConfig)
            .build();
    NexusConnectorDTO connectorDTO = NexusConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    NexusArtifactDelegateRequest delegateRequest = ArtifactConfigToDelegateReqMapper.getNexusArtifactDelegateRequest(
        artifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(delegateRequest.getNexusConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(delegateRequest.getRepositoryName()).isEqualTo(artifactConfig.getRepository().getValue());
    assertThat(delegateRequest.getRepositoryFormat()).isEqualTo(RepositoryFormat.docker.name());
    assertThat(delegateRequest.getRepositoryPort()).isNull();
    assertThat(delegateRequest.getArtifactRepositoryUrl()).isNull();
    assertThat(delegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    nexusRegistryDockerConfig = (NexusRegistryDockerConfig) artifactConfig.getNexusRegistryConfigSpec();
    assertThat(delegateRequest.getArtifactPath()).isEqualTo(nexusRegistryDockerConfig.getArtifactPath().getValue());
    assertThat(delegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.NEXUS3_REGISTRY);
    assertThat(delegateRequest.getTag()).isEqualTo("");
    assertThat(delegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(delegateRequest.getTagRegex()).isEqualTo("\\*");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetArtifactoryDelegateRequest() {
    ArtifactoryRegistryArtifactConfig artifactConfig =
        ArtifactoryRegistryArtifactConfig.builder()
            .repository(ParameterField.createValueField("TEST_REPO"))
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.docker.name()))
            .repositoryUrl(ParameterField.createValueField("harness-repo.jfrog.io"))
            .artifactPath(ParameterField.createValueField("IMAGE"))
            .build();
    ArtifactoryConnectorDTO connectorDTO = ArtifactoryConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    ArtifactoryArtifactDelegateRequest delegateRequest =
        (ArtifactoryArtifactDelegateRequest) ArtifactConfigToDelegateReqMapper.getArtifactoryArtifactDelegateRequest(
            artifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(delegateRequest.getArtifactoryConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(delegateRequest.getRepositoryName()).isEqualTo(artifactConfig.getRepository().getValue());
    assertThat(delegateRequest.getRepositoryFormat()).isEqualTo(RepositoryFormat.docker.name());
    assertThat(delegateRequest.getArtifactRepositoryUrl()).isEqualTo(artifactConfig.getRepositoryUrl().getValue());
    assertThat(delegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(delegateRequest.getArtifactPath()).isEqualTo(artifactConfig.getArtifactPath().getValue());
    assertThat(delegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.ARTIFACTORY_REGISTRY);
    assertThat(delegateRequest.getTag()).isEqualTo("");
    assertThat(delegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(delegateRequest.getTagRegex()).isEqualTo("\\*");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetArtifactoryDelegateRequestWhenGenericRequestTypeWhenArtifactPathAndArtifacDirectoryIsNotNull() {
    ArtifactoryRegistryArtifactConfig artifactConfig =
        ArtifactoryRegistryArtifactConfig.builder()
            .repository(ParameterField.createValueField("TEST_REPO"))
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.generic.name()))
            .artifactPath(ParameterField.createValueField("IMAGE1"))
            .artifactDirectory(ParameterField.createValueField("IMAGE"))
            .build();
    ArtifactoryConnectorDTO connectorDTO = ArtifactoryConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    ArtifactoryGenericArtifactDelegateRequest delegateRequest =
        (ArtifactoryGenericArtifactDelegateRequest) ArtifactConfigToDelegateReqMapper
            .getArtifactoryArtifactDelegateRequest(artifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(delegateRequest.getArtifactoryConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(delegateRequest.getRepositoryName()).isEqualTo(artifactConfig.getRepository().getValue());
    assertThat(delegateRequest.getRepositoryFormat()).isEqualTo(RepositoryFormat.generic.name());
    assertThat(delegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(delegateRequest.getArtifactPath()).isEqualTo(artifactConfig.getArtifactPath().getValue());
    assertThat(delegateRequest.getArtifactDirectory()).isEqualTo(artifactConfig.getArtifactDirectory().getValue());
    assertThat(delegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.ARTIFACTORY_REGISTRY);
    assertThat(delegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(delegateRequest.getArtifactPathFilter()).isEqualTo("");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetArtifactoryDelegateRequestWhenGenericRequestTypeWhenArtifactPathAndArtifactDirectoryIsNull() {
    ArtifactoryRegistryArtifactConfig artifactConfig =
        ArtifactoryRegistryArtifactConfig.builder()
            .repository(ParameterField.createValueField("TEST_REPO"))
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.generic.name()))
            .build();
    ArtifactoryConnectorDTO connectorDTO = ArtifactoryConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    ArtifactoryGenericArtifactDelegateRequest delegateRequest =
        (ArtifactoryGenericArtifactDelegateRequest) ArtifactConfigToDelegateReqMapper
            .getArtifactoryArtifactDelegateRequest(artifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(delegateRequest.getArtifactoryConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(delegateRequest.getRepositoryName()).isEqualTo(artifactConfig.getRepository().getValue());
    assertThat(delegateRequest.getRepositoryFormat()).isEqualTo(RepositoryFormat.generic.name());
    assertThat(delegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(delegateRequest.getArtifactPath()).isEqualTo("");
    assertThat(delegateRequest.getArtifactDirectory()).isNull();
    assertThat(delegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.ARTIFACTORY_REGISTRY);
    assertThat(delegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(delegateRequest.getArtifactPathFilter()).isEqualTo("");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetArtifactoryDelegateRequestWithoutDockerRepoServer() {
    ArtifactoryRegistryArtifactConfig artifactConfig =
        ArtifactoryRegistryArtifactConfig.builder()
            .repository(ParameterField.createValueField("TEST_REPO"))
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.docker.name()))
            .artifactPath(ParameterField.createValueField("IMAGE"))
            .build();
    ArtifactoryConnectorDTO connectorDTO = ArtifactoryConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    ArtifactoryArtifactDelegateRequest delegateRequest =
        (ArtifactoryArtifactDelegateRequest) ArtifactConfigToDelegateReqMapper.getArtifactoryArtifactDelegateRequest(
            artifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(delegateRequest.getArtifactoryConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(delegateRequest.getRepositoryName()).isEqualTo(artifactConfig.getRepository().getValue());
    assertThat(delegateRequest.getRepositoryFormat()).isEqualTo(RepositoryFormat.docker.name());
    assertThat(delegateRequest.getArtifactRepositoryUrl()).isNull();
    assertThat(delegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(delegateRequest.getArtifactPath()).isEqualTo(artifactConfig.getArtifactPath().getValue());
    assertThat(delegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.ARTIFACTORY_REGISTRY);
    assertThat(delegateRequest.getTag()).isEqualTo("");
    assertThat(delegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(delegateRequest.getTagRegex()).isEqualTo("\\*");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetAcrDelegateRequestWithTag() {
    AcrArtifactConfig acrArtifactConfig =
        AcrArtifactConfig.builder()
            .subscriptionId(ParameterField.createValueField("123456-6543-3456-654321"))
            .registry(ParameterField.createValueField("AZURE_CR"))
            .repository(ParameterField.createValueField("library/testapp"))
            .tag(ParameterField.createValueField("2.33"))
            .build();
    AzureConnectorDTO connectorDTO = AzureConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    AcrArtifactDelegateRequest acrDelegateRequest =
        (AcrArtifactDelegateRequest) ArtifactConfigToDelegateReqMapper.getAcrDelegateRequest(
            acrArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(acrDelegateRequest.getAzureConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(acrDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(acrDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.ACR);
    assertThat(acrDelegateRequest.getSubscription()).isEqualTo(acrArtifactConfig.getSubscriptionId().getValue());
    assertThat(acrDelegateRequest.getRegistry()).isEqualTo(acrArtifactConfig.getRegistry().getValue());
    assertThat(acrDelegateRequest.getRepository()).isEqualTo(acrArtifactConfig.getRepository().getValue());
    assertThat(acrDelegateRequest.getTag()).isEqualTo(acrArtifactConfig.getTag().getValue());
    assertThat(acrDelegateRequest.getTagRegex()).isBlank();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetAcrDelegateRequestWithTagRegex() {
    String regex = "[1-9]{1}[.][0-9]{2}";
    AcrArtifactConfig acrArtifactConfig =
        AcrArtifactConfig.builder()
            .subscriptionId(ParameterField.createValueField("123456-6543-3456-654321"))
            .registry(ParameterField.createValueField("AZURE_CR"))
            .repository(ParameterField.createValueField("library/testapp"))
            .tagRegex(ParameterField.createValueField(regex))
            .build();
    AzureConnectorDTO connectorDTO = AzureConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    AcrArtifactDelegateRequest acrDelegateRequest =
        (AcrArtifactDelegateRequest) ArtifactConfigToDelegateReqMapper.getAcrDelegateRequest(
            acrArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(acrDelegateRequest.getAzureConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(acrDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(acrDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.ACR);
    assertThat(acrDelegateRequest.getSubscription()).isEqualTo(acrArtifactConfig.getSubscriptionId().getValue());
    assertThat(acrDelegateRequest.getRegistry()).isEqualTo(acrArtifactConfig.getRegistry().getValue());
    assertThat(acrDelegateRequest.getRepository()).isEqualTo(acrArtifactConfig.getRepository().getValue());
    assertThat(acrDelegateRequest.getTag()).isBlank();
    assertThat(acrDelegateRequest.getTagRegex()).isEqualTo(regex);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetAcrDelegateRequestWithEmptyTagRegex() {
    AcrArtifactConfig acrArtifactConfig =
        AcrArtifactConfig.builder()
            .subscriptionId(ParameterField.createValueField("123456-6543-3456-654321"))
            .registry(ParameterField.createValueField("AZURE_CR"))
            .repository(ParameterField.createValueField("library/testapp"))
            .tagRegex(ParameterField.createValueField(""))
            .build();
    AzureConnectorDTO connectorDTO = AzureConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    AcrArtifactDelegateRequest acrDelegateRequest =
        (AcrArtifactDelegateRequest) ArtifactConfigToDelegateReqMapper.getAcrDelegateRequest(
            acrArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(acrDelegateRequest.getAzureConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(acrDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(acrDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.ACR);
    assertThat(acrDelegateRequest.getSubscription()).isEqualTo(acrArtifactConfig.getSubscriptionId().getValue());
    assertThat(acrDelegateRequest.getRegistry()).isEqualTo(acrArtifactConfig.getRegistry().getValue());
    assertThat(acrDelegateRequest.getRepository()).isEqualTo(acrArtifactConfig.getRepository().getValue());
    assertThat(acrDelegateRequest.getTag()).isBlank();
    assertThat(acrDelegateRequest.getTagRegex()).isEqualTo("\\*");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetCustomDelegateRequest() {
    CustomArtifactConfig customArtifactConfig =
        CustomArtifactConfig.builder()
            .identifier("test")
            .primaryArtifact(true)
            .version(ParameterField.createValueField("v1"))
            .versionRegex(ParameterField.createValueField("regex"))
            .scripts(CustomArtifactScripts.builder()
                         .fetchAllArtifacts(
                             FetchAllArtifacts.builder()
                                 .artifactsArrayPath(ParameterField.createValueField("results"))
                                 .versionPath(ParameterField.createValueField("version"))
                                 .shellScriptBaseStepInfo(
                                     CustomArtifactScriptInfo.builder()
                                         .source(CustomArtifactScriptSourceWrapper.builder()
                                                     .type("Inline")
                                                     .spec(CustomScriptInlineSource.builder()
                                                               .script(ParameterField.createValueField("echo test"))
                                                               .build())
                                                     .build())
                                         .build())
                                 .build())
                         .build())
            .version(ParameterField.createValueField("build-x"))
            .build();

    CustomArtifactDelegateRequest customArtifactDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getCustomDelegateRequest(customArtifactConfig, Ambiance.newBuilder().build());
    assertThat(customArtifactDelegateRequest.getArtifactsArrayPath()).isEqualTo("results");
    assertThat(customArtifactDelegateRequest.getVersionPath()).isEqualTo("version");
    assertThat(customArtifactDelegateRequest.getScript()).isEqualTo("echo test");

    customArtifactConfig =
        CustomArtifactConfig.builder()
            .identifier("test")
            .primaryArtifact(true)
            .timeout(ParameterField.createValueField(Timeout.builder().timeoutInMillis(700000L).build()))
            .version(ParameterField.createValueField("v1"))
            .versionRegex(ParameterField.createValueField("regex"))
            .scripts(CustomArtifactScripts.builder()
                         .fetchAllArtifacts(
                             FetchAllArtifacts.builder()
                                 .artifactsArrayPath(ParameterField.createValueField("results"))
                                 .versionPath(ParameterField.createValueField("version"))
                                 .shellScriptBaseStepInfo(
                                     CustomArtifactScriptInfo.builder()
                                         .source(CustomArtifactScriptSourceWrapper.builder()
                                                     .type("Inline")
                                                     .spec(CustomScriptInlineSource.builder()
                                                               .script(ParameterField.createValueField("echo test"))
                                                               .build())
                                                     .build())
                                         .build())
                                 .build())
                         .build())
            .version(ParameterField.createValueField("build-x"))
            .build();

    customArtifactDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getCustomDelegateRequest(customArtifactConfig, Ambiance.newBuilder().build());
    assertThat(customArtifactDelegateRequest.getArtifactsArrayPath()).isEqualTo("results");
    assertThat(customArtifactDelegateRequest.getVersionPath()).isEqualTo("version");
    assertThat(customArtifactDelegateRequest.getScript()).isEqualTo("echo test");

    customArtifactDelegateRequest = ArtifactConfigToDelegateReqMapper.getCustomDelegateRequest(
        customArtifactConfig, Ambiance.newBuilder().build(), delegateMetricsService, ngSecretService);
    assertThat(customArtifactDelegateRequest.getArtifactsArrayPath()).isEqualTo("results");
    assertThat(customArtifactDelegateRequest.getVersionPath()).isEqualTo("version");
    assertThat(customArtifactDelegateRequest.getScript()).isEqualTo("echo test");
    assertThat(customArtifactDelegateRequest.getExpressionFunctorToken()).isNotNull();
    assertThat(customArtifactDelegateRequest.getTimeout()).isEqualTo(700000L);

    // Validate for Triggers
    customArtifactDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getCustomDelegateRequest(customArtifactConfig, Ambiance.newBuilder().build());
    assertThat(customArtifactDelegateRequest.getTimeout()).isEqualTo(700000L);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetCustomDelegateRequestForInvalidYaml() {
    CustomArtifactConfig customArtifactConfig = CustomArtifactConfig.builder()
                                                    .identifier("test")
                                                    .primaryArtifact(true)
                                                    .version(ParameterField.createValueField("v1"))
                                                    .versionRegex(ParameterField.createValueField("regex"))
                                                    .version(ParameterField.createValueField("build-x"))
                                                    .build();
    try {
      ArtifactConfigToDelegateReqMapper.getCustomDelegateRequest(customArtifactConfig, Ambiance.newBuilder().build());
    } catch (Exception ex) {
      assertThat(ex).isInstanceOf(InvalidArtifactServerException.class);
      assertThat(ex.getMessage()).isEqualTo("INVALID_ARTIFACT_SERVER");
    }

    customArtifactConfig =
        CustomArtifactConfig.builder()
            .identifier("test")
            .primaryArtifact(true)
            .timeout(ParameterField.createValueField(Timeout.builder().timeoutInMillis(700000L).build()))
            .version(ParameterField.createValueField("v1"))
            .versionRegex(ParameterField.createValueField("regex"))
            .scripts(CustomArtifactScripts.builder().build())
            .version(ParameterField.createValueField("build-x"))
            .build();

    try {
      ArtifactConfigToDelegateReqMapper.getCustomDelegateRequest(customArtifactConfig, Ambiance.newBuilder().build());
    } catch (Exception ex) {
      assertThat(ex).isInstanceOf(InvalidArtifactServerException.class);
      assertThat(ex.getMessage()).isEqualTo("INVALID_ARTIFACT_SERVER");
    }

    customArtifactConfig =
        CustomArtifactConfig.builder()
            .identifier("test")
            .primaryArtifact(true)
            .timeout(ParameterField.createValueField(Timeout.builder().timeoutInMillis(700000L).build()))
            .version(ParameterField.createValueField("v1"))
            .versionRegex(ParameterField.createValueField("regex"))
            .scripts(CustomArtifactScripts.builder()
                         .fetchAllArtifacts(FetchAllArtifacts.builder()
                                                .artifactsArrayPath(ParameterField.createValueField("results"))
                                                .versionPath(ParameterField.createValueField("version"))
                                                .build())
                         .build())
            .version(ParameterField.createValueField("build-x"))
            .build();

    try {
      ArtifactConfigToDelegateReqMapper.getCustomDelegateRequest(customArtifactConfig, Ambiance.newBuilder().build());
    } catch (Exception ex) {
      assertThat(ex).isInstanceOf(InvalidArtifactServerException.class);
      assertThat(ex.getMessage()).isEqualTo("INVALID_ARTIFACT_SERVER");
    }

    customArtifactConfig =
        CustomArtifactConfig.builder()
            .identifier("test")
            .primaryArtifact(true)
            .timeout(ParameterField.createValueField(Timeout.builder().timeoutInMillis(700000L).build()))
            .version(ParameterField.createValueField("v1"))
            .versionRegex(ParameterField.createValueField("regex"))
            .scripts(CustomArtifactScripts.builder()
                         .fetchAllArtifacts(FetchAllArtifacts.builder()
                                                .artifactsArrayPath(ParameterField.createValueField("results"))
                                                .versionPath(ParameterField.createValueField("version"))
                                                .shellScriptBaseStepInfo(CustomArtifactScriptInfo.builder().build())
                                                .build())
                         .build())
            .version(ParameterField.createValueField("build-x"))
            .build();
    try {
      ArtifactConfigToDelegateReqMapper.getCustomDelegateRequest(customArtifactConfig, Ambiance.newBuilder().build());
    } catch (Exception ex) {
      assertThat(ex).isInstanceOf(InvalidArtifactServerException.class);
      assertThat(ex.getMessage()).isEqualTo("INVALID_ARTIFACT_SERVER");
    }

    customArtifactConfig =
        CustomArtifactConfig.builder()
            .identifier("test")
            .primaryArtifact(true)
            .timeout(ParameterField.createValueField(Timeout.builder().timeoutInMillis(700000L).build()))
            .version(ParameterField.createValueField("v1"))
            .versionRegex(ParameterField.createValueField("regex"))
            .scripts(CustomArtifactScripts.builder()
                         .fetchAllArtifacts(FetchAllArtifacts.builder()
                                                .artifactsArrayPath(ParameterField.createValueField("results"))
                                                .versionPath(ParameterField.createValueField("version"))
                                                .shellScriptBaseStepInfo(null)
                                                .build())
                         .build())
            .version(ParameterField.createValueField("build-x"))
            .build();
    try {
      ArtifactConfigToDelegateReqMapper.getCustomDelegateRequest(customArtifactConfig, Ambiance.newBuilder().build());
    } catch (Exception ex) {
      assertThat(ex).isInstanceOf(InvalidArtifactServerException.class);
      assertThat(ex.getMessage()).isEqualTo("INVALID_ARTIFACT_SERVER");
    }

    customArtifactConfig =
        CustomArtifactConfig.builder()
            .identifier("test")
            .primaryArtifact(true)
            .timeout(ParameterField.createValueField(Timeout.builder().timeoutInMillis(700000L).build()))
            .version(ParameterField.createValueField("v1"))
            .versionRegex(ParameterField.createValueField("regex"))
            .scripts(CustomArtifactScripts.builder()
                         .fetchAllArtifacts(
                             FetchAllArtifacts.builder()
                                 .versionPath(ParameterField.createValueField("version"))
                                 .shellScriptBaseStepInfo(
                                     CustomArtifactScriptInfo.builder()
                                         .source(CustomArtifactScriptSourceWrapper.builder()
                                                     .type("Inline")
                                                     .spec(CustomScriptInlineSource.builder()
                                                               .script(ParameterField.createValueField("echo test"))
                                                               .build())
                                                     .build())
                                         .build())
                                 .build())
                         .build())
            .version(ParameterField.createValueField("build-x"))
            .build();
    try {
      ArtifactConfigToDelegateReqMapper.getCustomDelegateRequest(customArtifactConfig, Ambiance.newBuilder().build());
    } catch (Exception ex) {
      assertThat(ex).isInstanceOf(InvalidArtifactServerException.class);
      assertThat(ex.getMessage()).isEqualTo("INVALID_ARTIFACT_SERVER");
    }

    customArtifactConfig =
        CustomArtifactConfig.builder()
            .identifier("test")
            .primaryArtifact(true)
            .timeout(ParameterField.createValueField(Timeout.builder().timeoutInMillis(700000L).build()))
            .version(ParameterField.createValueField("v1"))
            .versionRegex(ParameterField.createValueField("regex"))
            .scripts(CustomArtifactScripts.builder()
                         .fetchAllArtifacts(
                             FetchAllArtifacts.builder()
                                 .artifactsArrayPath(ParameterField.createValueField("results"))
                                 .shellScriptBaseStepInfo(
                                     CustomArtifactScriptInfo.builder()
                                         .source(CustomArtifactScriptSourceWrapper.builder()
                                                     .type("Inline")
                                                     .spec(CustomScriptInlineSource.builder()
                                                               .script(ParameterField.createValueField("echo test"))
                                                               .build())
                                                     .build())
                                         .build())
                                 .build())
                         .build())
            .version(ParameterField.createValueField("build-x"))
            .build();
    try {
      ArtifactConfigToDelegateReqMapper.getCustomDelegateRequest(customArtifactConfig, Ambiance.newBuilder().build());
    } catch (Exception ex) {
      assertThat(ex).isInstanceOf(InvalidArtifactServerException.class);
      assertThat(ex.getMessage()).isEqualTo("INVALID_ARTIFACT_SERVER");
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetDockerDelegateRequestTagAsRegex() {
    DockerHubArtifactConfig dockerHubArtifactConfig =
        DockerHubArtifactConfig.builder()
            .imagePath(ParameterField.createValueField("IMAGE"))
            .tag(ParameterField.createValueField(LAST_PUBLISHED_EXPRESSION))
            .build();
    DockerConnectorDTO connectorDTO = DockerConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    DockerArtifactDelegateRequest dockerDelegateRequest = ArtifactConfigToDelegateReqMapper.getDockerDelegateRequest(
        dockerHubArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(dockerDelegateRequest.getDockerConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(dockerDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(dockerDelegateRequest.getImagePath()).isEqualTo(dockerHubArtifactConfig.getImagePath().getValue());
    assertThat(dockerDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.DOCKER_REGISTRY);
    assertThat(dockerDelegateRequest.getTagsList()).isNull();
    assertThat(dockerDelegateRequest.getTagRegex()).isEqualTo(".*?");
    assertThat(dockerDelegateRequest.getShouldFetchDockerV2DigestSHA256()).isEqualTo(false);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetDockerDelegateRequestTagAsNull() {
    DockerHubArtifactConfig dockerHubArtifactConfig = DockerHubArtifactConfig.builder()
                                                          .imagePath(ParameterField.createValueField("IMAGE"))
                                                          .tag(ParameterField.createValueField(null))
                                                          .tagRegex(ParameterField.createValueField(null))
                                                          .build();
    DockerConnectorDTO connectorDTO = DockerConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    DockerArtifactDelegateRequest dockerDelegateRequest = ArtifactConfigToDelegateReqMapper.getDockerDelegateRequest(
        dockerHubArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(dockerDelegateRequest.getDockerConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(dockerDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(dockerDelegateRequest.getImagePath()).isEqualTo(dockerHubArtifactConfig.getImagePath().getValue());
    assertThat(dockerDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.DOCKER_REGISTRY);
    assertThat(dockerDelegateRequest.getTagsList()).isNull();
    assertThat(dockerDelegateRequest.getTagRegex()).isEqualTo(ACCEPT_ALL_REGEX);
    assertThat(dockerDelegateRequest.getShouldFetchDockerV2DigestSHA256()).isEqualTo(false);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetDockerDelegateRequestTagAsInputValidator() {
    DockerHubArtifactConfig dockerHubArtifactConfig =
        DockerHubArtifactConfig.builder()
            .imagePath(ParameterField.createValueField("IMAGE"))
            .tag(ParameterField.createValueFieldWithInputSetValidator(
                LAST_PUBLISHED_EXPRESSION, new InputSetValidator(InputSetValidatorType.REGEX, "stable*"), true))
            .build();
    DockerConnectorDTO connectorDTO = DockerConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    DockerArtifactDelegateRequest dockerDelegateRequest = ArtifactConfigToDelegateReqMapper.getDockerDelegateRequest(
        dockerHubArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(dockerDelegateRequest.getDockerConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(dockerDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(dockerDelegateRequest.getImagePath()).isEqualTo(dockerHubArtifactConfig.getImagePath().getValue());
    assertThat(dockerDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.DOCKER_REGISTRY);
    assertThat(dockerDelegateRequest.getTagsList()).isNull();
    assertThat(dockerDelegateRequest.getTagRegex()).isEqualTo("stable*");
    assertThat(dockerDelegateRequest.getShouldFetchDockerV2DigestSHA256()).isEqualTo(false);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetGitHubDelegateRequestTagAsRegex() {
    GithubPackagesArtifactConfig githubPackagesArtifactConfig =
        GithubPackagesArtifactConfig.builder()
            .version(ParameterField.createValueField(LAST_PUBLISHED_EXPRESSION))
            .packageName(ParameterField.createValueField("PACKAGE"))
            .packageType(ParameterField.createValueField("type"))
            .org(ParameterField.createValueField("org"))
            .build();
    GithubConnectorDTO connectorDTO = GithubConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    GithubPackagesArtifactDelegateRequest githubPackagesArtifactDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getGithubPackagesDelegateRequest(
            githubPackagesArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(githubPackagesArtifactDelegateRequest.getGithubConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(githubPackagesArtifactDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(githubPackagesArtifactDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.GITHUB_PACKAGES);
    assertThat(githubPackagesArtifactDelegateRequest.getPackageName()).isNotNull();
    assertThat(githubPackagesArtifactDelegateRequest.getPackageType()).isEqualTo("type");
    assertThat(githubPackagesArtifactDelegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(githubPackagesArtifactDelegateRequest.getVersionRegex()).isEqualTo("*");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetGitHubDelegateRequestTagAsNull() {
    GithubPackagesArtifactConfig githubPackagesArtifactConfig =
        GithubPackagesArtifactConfig.builder()
            .version(ParameterField.createValueField(null))
            .versionRegex(ParameterField.createValueField(null))
            .packageName(ParameterField.createValueField("PACKAGE"))
            .packageType(ParameterField.createValueField("type"))
            .org(ParameterField.createValueField("org"))
            .build();
    GithubConnectorDTO connectorDTO = GithubConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    GithubPackagesArtifactDelegateRequest githubPackagesArtifactDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getGithubPackagesDelegateRequest(
            githubPackagesArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(githubPackagesArtifactDelegateRequest.getGithubConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(githubPackagesArtifactDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(githubPackagesArtifactDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.GITHUB_PACKAGES);
    assertThat(githubPackagesArtifactDelegateRequest.getPackageName()).isNotNull();
    assertThat(githubPackagesArtifactDelegateRequest.getPackageType()).isEqualTo("type");
    assertThat(githubPackagesArtifactDelegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(githubPackagesArtifactDelegateRequest.getVersionRegex()).isEqualTo("*");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetGitHubDelegateRequestForMavenType() {
    GithubPackagesArtifactConfig githubPackagesArtifactConfig =
        GithubPackagesArtifactConfig.builder()
            .version(ParameterField.createValueField(ACCEPT_ALL_REGEX))
            .packageName(ParameterField.createValueField("PACKAGE"))
            .groupId(ParameterField.createValueField("GroupId"))
            .artifactId(ParameterField.createValueField("ArtifactId"))
            .extension(ParameterField.createValueField("jar"))
            .packageType(ParameterField.createValueField("maven"))
            .org(ParameterField.createValueField("org"))
            .build();
    GithubConnectorDTO connectorDTO = GithubConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    GithubPackagesArtifactDelegateRequest githubPackagesArtifactDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getGithubPackagesDelegateRequest(
            githubPackagesArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(githubPackagesArtifactDelegateRequest.getGithubConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(githubPackagesArtifactDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(githubPackagesArtifactDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.GITHUB_PACKAGES);
    assertThat(githubPackagesArtifactDelegateRequest.getPackageName()).isNotNull();
    assertThat(githubPackagesArtifactDelegateRequest.getPackageType()).isEqualTo("maven");
    assertThat(githubPackagesArtifactDelegateRequest.getGroupId()).isEqualTo("GroupId");
    assertThat(githubPackagesArtifactDelegateRequest.getExtension()).isEqualTo("jar");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetGitHubDelegateRequestTagAsInputValidator() {
    GithubPackagesArtifactConfig githubPackagesArtifactConfig =
        GithubPackagesArtifactConfig.builder()
            .version(ParameterField.createValueFieldWithInputSetValidator(
                LAST_PUBLISHED_EXPRESSION, new InputSetValidator(InputSetValidatorType.REGEX, "stable*"), true))
            .packageName(ParameterField.createValueField("PACKAGE"))
            .packageType(ParameterField.createValueField("type"))
            .org(ParameterField.createValueField("org"))
            .build();
    GithubConnectorDTO connectorDTO = GithubConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    GithubPackagesArtifactDelegateRequest githubPackagesArtifactDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getGithubPackagesDelegateRequest(
            githubPackagesArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(githubPackagesArtifactDelegateRequest.getGithubConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(githubPackagesArtifactDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(githubPackagesArtifactDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.GITHUB_PACKAGES);
    assertThat(githubPackagesArtifactDelegateRequest.getPackageName()).isNotNull();
    assertThat(githubPackagesArtifactDelegateRequest.getPackageType()).isEqualTo("type");
    assertThat(githubPackagesArtifactDelegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(githubPackagesArtifactDelegateRequest.getVersionRegex()).isEqualTo("stable*");
    assertThat(githubPackagesArtifactDelegateRequest.getVersion()).isEqualTo(LAST_PUBLISHED_EXPRESSION);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetAzureDelegateRequestTagAsRegex() {
    AzureArtifactsConfig azureArtifactsConfig = AzureArtifactsConfig.builder()
                                                    .packageName(ParameterField.createValueField("PACKAGE"))
                                                    .packageType(ParameterField.createValueField("type"))
                                                    .project(ParameterField.createValueField("project"))
                                                    .feed(ParameterField.createValueField("feed"))
                                                    .version(ParameterField.createValueField(LAST_PUBLISHED_EXPRESSION))
                                                    .versionRegex(ParameterField.createValueField(""))
                                                    .build();
    AzureArtifactsConnectorDTO connectorDTO = AzureArtifactsConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    AzureArtifactsDelegateRequest azureArtifactsDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getAzureArtifactsDelegateRequest(
            azureArtifactsConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(azureArtifactsDelegateRequest.getAzureArtifactsConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(azureArtifactsDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(azureArtifactsDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.AZURE_ARTIFACTS);
    assertThat(azureArtifactsDelegateRequest.getPackageName()).isNotNull();
    assertThat(azureArtifactsDelegateRequest.getPackageType()).isEqualTo("type");
    assertThat(azureArtifactsDelegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(azureArtifactsDelegateRequest.getVersionRegex()).isEqualTo(".*?");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetAzureDelegateRequestTagAsNull() {
    AzureArtifactsConfig azureArtifactsConfig = AzureArtifactsConfig.builder()
                                                    .packageName(ParameterField.createValueField("PACKAGE"))
                                                    .packageType(ParameterField.createValueField("type"))
                                                    .project(ParameterField.createValueField("project"))
                                                    .feed(ParameterField.createValueField("feed"))
                                                    .version(ParameterField.createValueField(null))
                                                    .versionRegex(ParameterField.createValueField(""))
                                                    .build();
    AzureArtifactsConnectorDTO connectorDTO = AzureArtifactsConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    AzureArtifactsDelegateRequest azureArtifactsDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getAzureArtifactsDelegateRequest(
            azureArtifactsConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(azureArtifactsDelegateRequest.getAzureArtifactsConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(azureArtifactsDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(azureArtifactsDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.AZURE_ARTIFACTS);
    assertThat(azureArtifactsDelegateRequest.getPackageName()).isNotNull();
    assertThat(azureArtifactsDelegateRequest.getPackageType()).isEqualTo("type");
    assertThat(azureArtifactsDelegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(azureArtifactsDelegateRequest.getVersionRegex()).isEqualTo("*");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetAzureDelegateRequestTagAsInputValidator() {
    AzureArtifactsConfig azureArtifactsConfig =
        AzureArtifactsConfig.builder()
            .packageName(ParameterField.createValueField("PACKAGE"))
            .packageType(ParameterField.createValueField("type"))
            .project(ParameterField.createValueField("project"))
            .feed(ParameterField.createValueField("feed"))
            .version(ParameterField.createValueFieldWithInputSetValidator(
                LAST_PUBLISHED_EXPRESSION, new InputSetValidator(InputSetValidatorType.REGEX, "stable*"), true))
            .versionRegex(ParameterField.createValueField(""))
            .build();
    AzureArtifactsConnectorDTO connectorDTO = AzureArtifactsConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    AzureArtifactsDelegateRequest azureArtifactsDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getAzureArtifactsDelegateRequest(
            azureArtifactsConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(azureArtifactsDelegateRequest.getAzureArtifactsConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(azureArtifactsDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(azureArtifactsDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.AZURE_ARTIFACTS);
    assertThat(azureArtifactsDelegateRequest.getPackageName()).isNotNull();
    assertThat(azureArtifactsDelegateRequest.getPackageType()).isEqualTo("type");
    assertThat(azureArtifactsDelegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(azureArtifactsDelegateRequest.getVersionRegex()).isEqualTo("stable*");
    assertThat(azureArtifactsDelegateRequest.getVersion()).isEqualTo(LAST_PUBLISHED_EXPRESSION);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetAMIDelegateRequestTagAsRegex() {
    AMIArtifactConfig amiArtifactConfig =
        AMIArtifactConfig.builder()
            .version(ParameterField.createValueField(LAST_PUBLISHED_EXPRESSION))
            .region(ParameterField.createValueField("IMAGE"))
            .filters(ParameterField.createValueField(
                Collections.singletonList(AMIFilter.builder().name("test").value("test").build())))
            .tags(ParameterField.createValueField(
                Collections.singletonList(AMITag.builder().name("name").value("test").build())))
            .build();
    AwsConnectorDTO connectorDTO = AwsConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    AMIArtifactDelegateRequest amiArtifactDelegateRequest = ArtifactConfigToDelegateReqMapper.getAMIDelegateRequest(
        amiArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(amiArtifactDelegateRequest.getAwsConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(amiArtifactDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(amiArtifactDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.AMI);
    assertThat(amiArtifactDelegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(amiArtifactDelegateRequest.getVersionRegex()).isEqualTo(LAST_PUBLISHED_EXPRESSION);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetAMIDelegateRequestTagAsNull() {
    AMIArtifactConfig amiArtifactConfig =
        AMIArtifactConfig.builder()
            .version(ParameterField.createValueField(null))
            .versionRegex(ParameterField.createValueField(null))
            .region(ParameterField.createValueField("IMAGE"))
            .filters(ParameterField.createValueField(
                Collections.singletonList(AMIFilter.builder().name("test").value("test").build())))
            .tags(ParameterField.createValueField(
                Collections.singletonList(AMITag.builder().name("name").value("test").build())))
            .build();
    AwsConnectorDTO connectorDTO = AwsConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    AMIArtifactDelegateRequest amiArtifactDelegateRequest = ArtifactConfigToDelegateReqMapper.getAMIDelegateRequest(
        amiArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(amiArtifactDelegateRequest.getAwsConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(amiArtifactDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(amiArtifactDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.AMI);
    assertThat(amiArtifactDelegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(amiArtifactDelegateRequest.getVersionRegex()).isEqualTo("*");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetAMIDelegateRequestTagAsInputValidator() {
    AMIArtifactConfig amiArtifactConfig =
        AMIArtifactConfig.builder()
            .version(ParameterField.createValueFieldWithInputSetValidator(
                LAST_PUBLISHED_EXPRESSION, new InputSetValidator(InputSetValidatorType.REGEX, "stable*"), true))
            .region(ParameterField.createValueField("IMAGE"))
            .filters(ParameterField.createValueField(
                Collections.singletonList(AMIFilter.builder().name("test").value("test").build())))
            .tags(ParameterField.createValueField(
                Collections.singletonList(AMITag.builder().name("name").value("test").build())))
            .build();
    AwsConnectorDTO connectorDTO = AwsConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    AMIArtifactDelegateRequest amiArtifactDelegateRequest = ArtifactConfigToDelegateReqMapper.getAMIDelegateRequest(
        amiArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(amiArtifactDelegateRequest.getAwsConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(amiArtifactDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(amiArtifactDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.AMI);
    assertThat(amiArtifactDelegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(amiArtifactDelegateRequest.getVersionRegex()).isEqualTo("stable*");
    assertThat(amiArtifactDelegateRequest.getVersion()).isEqualTo(LAST_PUBLISHED_EXPRESSION);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetJenkinsDelegateRequestTagAsRegex() {
    JenkinsArtifactConfig jenkinsArtifactConfig = JenkinsArtifactConfig.builder()
                                                      .artifactPath(ParameterField.createValueField("ARTIFACT"))
                                                      .jobName(ParameterField.createValueField("JOB"))
                                                      .build(ParameterField.createValueField(LAST_PUBLISHED_EXPRESSION))
                                                      .build();
    JenkinsConnectorDTO connectorDTO = JenkinsConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getJenkinsDelegateRequest(
            jenkinsArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(jenkinsArtifactDelegateRequest.getJenkinsConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(jenkinsArtifactDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(jenkinsArtifactDelegateRequest.getArtifactPaths())
        .isEqualTo(Arrays.asList(jenkinsArtifactConfig.getArtifactPath().getValue()));
    assertThat(jenkinsArtifactDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.JENKINS);
    assertThat(jenkinsArtifactDelegateRequest.getJobName()).isEqualTo(jenkinsArtifactConfig.getJobName().getValue());
    assertThat(jenkinsArtifactDelegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(jenkinsArtifactDelegateRequest.getBuildNumber()).isEqualTo("");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetJenkinsDelegateRequestTagAsNull() {
    JenkinsArtifactConfig jenkinsArtifactConfig = JenkinsArtifactConfig.builder()
                                                      .artifactPath(ParameterField.createValueField("ARTIFACT"))
                                                      .jobName(ParameterField.createValueField("JOB"))
                                                      .build(ParameterField.createValueField(null))
                                                      .build();
    JenkinsConnectorDTO connectorDTO = JenkinsConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getJenkinsDelegateRequest(
            jenkinsArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(jenkinsArtifactDelegateRequest.getJenkinsConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(jenkinsArtifactDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(jenkinsArtifactDelegateRequest.getArtifactPaths())
        .isEqualTo(Arrays.asList(jenkinsArtifactConfig.getArtifactPath().getValue()));
    assertThat(jenkinsArtifactDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.JENKINS);
    assertThat(jenkinsArtifactDelegateRequest.getJobName()).isEqualTo(jenkinsArtifactConfig.getJobName().getValue());
    assertThat(jenkinsArtifactDelegateRequest.getBuildNumber()).isNull();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetJenkinsDelegateRequestTagAsInputValidator() {
    JenkinsArtifactConfig jenkinsArtifactConfig =
        JenkinsArtifactConfig.builder()
            .artifactPath(ParameterField.createValueField("ARTIFACT"))
            .jobName(ParameterField.createValueField("JOB"))
            .build(ParameterField.createValueFieldWithInputSetValidator(
                LAST_PUBLISHED_EXPRESSION, new InputSetValidator(InputSetValidatorType.REGEX, "stable*"), true))
            .build();
    JenkinsConnectorDTO connectorDTO = JenkinsConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getJenkinsDelegateRequest(
            jenkinsArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(jenkinsArtifactDelegateRequest.getJenkinsConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(jenkinsArtifactDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(jenkinsArtifactDelegateRequest.getArtifactPaths())
        .isEqualTo(Arrays.asList(jenkinsArtifactConfig.getArtifactPath().getValue()));
    assertThat(jenkinsArtifactDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.JENKINS);
    assertThat(jenkinsArtifactDelegateRequest.getJobName()).isEqualTo(jenkinsArtifactConfig.getJobName().getValue());
    assertThat(jenkinsArtifactDelegateRequest.getBuildNumber()).isEqualTo("stable*");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetBambooDelegateRequestTagAsRegex() {
    BambooArtifactConfig bambooArtifactConfig =
        BambooArtifactConfig.builder()
            .artifactPaths(ParameterField.createValueField(Collections.singletonList("ARTIFACT")))
            .planKey(ParameterField.createValueField("PLAN"))
            .build(ParameterField.createValueField(LAST_PUBLISHED_EXPRESSION))
            .build();
    BambooConnectorDTO connectorDTO = BambooConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    BambooArtifactDelegateRequest bambooArtifactDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getBambooDelegateRequest(
            bambooArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(bambooArtifactDelegateRequest.getBambooConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(bambooArtifactDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(bambooArtifactDelegateRequest.getArtifactPaths())
        .isEqualTo(bambooArtifactConfig.getArtifactPaths().fetchFinalValue());
    assertThat(bambooArtifactDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.BAMBOO);
    assertThat(bambooArtifactDelegateRequest.getPlanKey()).isEqualTo(bambooArtifactConfig.getPlanKey().getValue());
    assertThat(bambooArtifactDelegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(bambooArtifactDelegateRequest.getBuildNumber()).isEqualTo(".*?");
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetBambooDelegateRequestTagAsRegexWithSomeValue() {
    BambooArtifactConfig bambooArtifactConfig =
        BambooArtifactConfig.builder()
            .artifactPaths(ParameterField.createValueField(Collections.singletonList("ARTIFACT")))
            .planKey(ParameterField.createValueField("PLAN"))
            .build(LAST_PUBLISHED_EXPRESSION_REGEX)
            .build();
    BambooConnectorDTO connectorDTO = BambooConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    BambooArtifactDelegateRequest bambooArtifactDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getBambooDelegateRequest(
            bambooArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(bambooArtifactDelegateRequest.getBambooConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(bambooArtifactDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(bambooArtifactDelegateRequest.getArtifactPaths())
        .isEqualTo(bambooArtifactConfig.getArtifactPaths().fetchFinalValue());
    assertThat(bambooArtifactDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.BAMBOO);
    assertThat(bambooArtifactDelegateRequest.getPlanKey()).isEqualTo(bambooArtifactConfig.getPlanKey().getValue());
    assertThat(bambooArtifactDelegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(bambooArtifactDelegateRequest.getBuildNumber()).isEqualTo(TAG);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetBambooDelegateRequestTag() {
    BambooArtifactConfig bambooArtifactConfig =
        BambooArtifactConfig.builder()
            .artifactPaths(ParameterField.createValueField(Collections.singletonList("ARTIFACT")))
            .planKey(ParameterField.createValueField("PLAN"))
            .build(ParameterField.createValueField(TAG))
            .build();
    BambooConnectorDTO connectorDTO = BambooConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    BambooArtifactDelegateRequest bambooArtifactDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getBambooDelegateRequest(
            bambooArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(bambooArtifactDelegateRequest.getBambooConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(bambooArtifactDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(bambooArtifactDelegateRequest.getArtifactPaths())
        .isEqualTo(bambooArtifactConfig.getArtifactPaths().fetchFinalValue());
    assertThat(bambooArtifactDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.BAMBOO);
    assertThat(bambooArtifactDelegateRequest.getPlanKey()).isEqualTo(bambooArtifactConfig.getPlanKey().getValue());
    assertThat(bambooArtifactDelegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(bambooArtifactDelegateRequest.getBuildNumber()).isEqualTo(TAG);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetCustomDelegateRequestTagAsRegex() {
    CustomArtifactConfig customArtifactConfig =
        CustomArtifactConfig.builder()
            .identifier("test")
            .primaryArtifact(true)
            .version(ParameterField.createValueField(LAST_PUBLISHED_EXPRESSION))
            .versionRegex(ParameterField.createValueField(""))
            .scripts(CustomArtifactScripts.builder()
                         .fetchAllArtifacts(
                             FetchAllArtifacts.builder()
                                 .artifactsArrayPath(ParameterField.createValueField("results"))
                                 .versionPath(ParameterField.createValueField("version"))
                                 .shellScriptBaseStepInfo(
                                     CustomArtifactScriptInfo.builder()
                                         .source(CustomArtifactScriptSourceWrapper.builder()
                                                     .type("Inline")
                                                     .spec(CustomScriptInlineSource.builder()
                                                               .script(ParameterField.createValueField("echo test"))
                                                               .build())
                                                     .build())
                                         .build())
                                 .build())
                         .build())
            .build();

    CustomArtifactDelegateRequest customArtifactDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getCustomDelegateRequest(customArtifactConfig, Ambiance.newBuilder().build());
    assertThat(customArtifactDelegateRequest.getArtifactsArrayPath()).isEqualTo("results");
    assertThat(customArtifactDelegateRequest.getVersionPath()).isEqualTo("version");
    assertThat(customArtifactDelegateRequest.getScript()).isEqualTo("echo test");
    assertThat(customArtifactDelegateRequest.getVersionRegex()).isEqualTo(".*?");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetCustomDelegateRequestTagAsNull() {
    CustomArtifactConfig customArtifactConfig =
        CustomArtifactConfig.builder()
            .identifier("test")
            .primaryArtifact(true)
            .version(ParameterField.createValueField(null))
            .versionRegex(ParameterField.createValueField(null))
            .scripts(CustomArtifactScripts.builder()
                         .fetchAllArtifacts(
                             FetchAllArtifacts.builder()
                                 .artifactsArrayPath(ParameterField.createValueField("results"))
                                 .versionPath(ParameterField.createValueField("version"))
                                 .shellScriptBaseStepInfo(
                                     CustomArtifactScriptInfo.builder()
                                         .source(CustomArtifactScriptSourceWrapper.builder()
                                                     .type("Inline")
                                                     .spec(CustomScriptInlineSource.builder()
                                                               .script(ParameterField.createValueField("echo test"))
                                                               .build())
                                                     .build())
                                         .build())
                                 .build())
                         .build())
            .build();

    CustomArtifactDelegateRequest customArtifactDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getCustomDelegateRequest(customArtifactConfig, Ambiance.newBuilder().build());
    assertThat(customArtifactDelegateRequest.getArtifactsArrayPath()).isEqualTo("results");
    assertThat(customArtifactDelegateRequest.getVersionPath()).isEqualTo("version");
    assertThat(customArtifactDelegateRequest.getScript()).isEqualTo("echo test");
    assertThat(customArtifactDelegateRequest.getVersionRegex()).isEqualTo("");
    assertThat(customArtifactDelegateRequest.getVersion()).isNull();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetCustomDelegateRequestTagAsInputValidator() {
    CustomArtifactConfig customArtifactConfig =
        CustomArtifactConfig.builder()
            .identifier("test")
            .primaryArtifact(true)
            .version(ParameterField.createValueFieldWithInputSetValidator(
                LAST_PUBLISHED_EXPRESSION, new InputSetValidator(InputSetValidatorType.REGEX, "stable*"), true))
            .scripts(CustomArtifactScripts.builder()
                         .fetchAllArtifacts(
                             FetchAllArtifacts.builder()
                                 .artifactsArrayPath(ParameterField.createValueField("results"))
                                 .versionPath(ParameterField.createValueField("version"))
                                 .shellScriptBaseStepInfo(
                                     CustomArtifactScriptInfo.builder()
                                         .source(CustomArtifactScriptSourceWrapper.builder()
                                                     .type("Inline")
                                                     .spec(CustomScriptInlineSource.builder()
                                                               .script(ParameterField.createValueField("echo test"))
                                                               .build())
                                                     .build())
                                         .build())
                                 .build())
                         .build())
            .build();

    CustomArtifactDelegateRequest customArtifactDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getCustomDelegateRequest(customArtifactConfig, Ambiance.newBuilder().build());
    assertThat(customArtifactDelegateRequest.getArtifactsArrayPath()).isEqualTo("results");
    assertThat(customArtifactDelegateRequest.getVersionPath()).isEqualTo("version");
    assertThat(customArtifactDelegateRequest.getScript()).isEqualTo("echo test");
    assertThat(customArtifactDelegateRequest.getVersionRegex()).isEqualTo("stable*");
    assertThat(customArtifactDelegateRequest.getVersion()).isEqualTo(LAST_PUBLISHED_EXPRESSION);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetGCRDelegateRequestTagAsRegex() {
    GcrArtifactConfig gcrArtifactConfig = GcrArtifactConfig.builder()
                                              .imagePath(ParameterField.createValueField("IMAGE"))
                                              .tag(ParameterField.createValueField(LAST_PUBLISHED_EXPRESSION))
                                              .registryHostname(ParameterField.createValueField("host"))
                                              .build();
    GcpConnectorDTO connectorDTO = GcpConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    GcrArtifactDelegateRequest gcrDelegateRequest = ArtifactConfigToDelegateReqMapper.getGcrDelegateRequest(
        gcrArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(gcrDelegateRequest.getGcpConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(gcrDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(gcrDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.GCR);
    assertThat(gcrDelegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(gcrDelegateRequest.getTagRegex()).isEqualTo(".*?");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetGCRDelegateRequestTagAsNUll() {
    GcrArtifactConfig gcrArtifactConfig = GcrArtifactConfig.builder()
                                              .imagePath(ParameterField.createValueField("IMAGE"))
                                              .tag(ParameterField.createValueField(null))
                                              .registryHostname(ParameterField.createValueField("host"))
                                              .build();
    GcpConnectorDTO connectorDTO = GcpConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    GcrArtifactDelegateRequest gcrDelegateRequest = ArtifactConfigToDelegateReqMapper.getGcrDelegateRequest(
        gcrArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(gcrDelegateRequest.getGcpConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(gcrDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(gcrDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.GCR);
    assertThat(gcrDelegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(gcrDelegateRequest.getTagRegex()).isEqualTo(ACCEPT_ALL_REGEX);
    assertThat(gcrDelegateRequest.getTag()).isNull();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetGCRDelegateRequestTagAsInputValidator() {
    GcrArtifactConfig gcrArtifactConfig =
        GcrArtifactConfig.builder()
            .imagePath(ParameterField.createValueField("IMAGE"))
            .tag(ParameterField.createValueFieldWithInputSetValidator(
                LAST_PUBLISHED_EXPRESSION, new InputSetValidator(InputSetValidatorType.REGEX, "stable*"), true))
            .registryHostname(ParameterField.createValueField("host"))
            .build();
    GcpConnectorDTO connectorDTO = GcpConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    GcrArtifactDelegateRequest gcrDelegateRequest = ArtifactConfigToDelegateReqMapper.getGcrDelegateRequest(
        gcrArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(gcrDelegateRequest.getGcpConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(gcrDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(gcrDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.GCR);
    assertThat(gcrDelegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(gcrDelegateRequest.getTagRegex()).isEqualTo("stable*");
    assertThat(gcrDelegateRequest.getTag()).isEqualTo(LAST_PUBLISHED_EXPRESSION);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetGARDelegateRequestRegexTagAsRegex() {
    GoogleArtifactRegistryConfig garArtifactInfo =
        GoogleArtifactRegistryConfig.builder()
            .region(ParameterField.createValueField("region"))
            .version(ParameterField.createValueField(LAST_PUBLISHED_EXPRESSION))
            .repositoryName(ParameterField.createValueField("repo"))
            .pkg(ParameterField.createValueField("pkg"))
            .project(ParameterField.createValueField("project"))
            .build();
    GcpConnectorDTO connectorDTO = GcpConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    GarDelegateRequest amiArtifactDelegateRequest = ArtifactConfigToDelegateReqMapper.getGarDelegateRequest(
        garArtifactInfo, connectorDTO, encryptedDataDetailList, "");

    assertThat(amiArtifactDelegateRequest.getGcpConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(amiArtifactDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(amiArtifactDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY);
    assertThat(amiArtifactDelegateRequest.getVersionRegex()).isEqualTo(".*?");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetGARDelegateRequestRegexTagAsNull() {
    GoogleArtifactRegistryConfig garArtifactInfo = GoogleArtifactRegistryConfig.builder()
                                                       .region(ParameterField.createValueField("region"))
                                                       .version(ParameterField.createValueField(null))
                                                       .versionRegex(ParameterField.createValueField(null))
                                                       .repositoryName(ParameterField.createValueField("repo"))
                                                       .pkg(ParameterField.createValueField("pkg"))
                                                       .project(ParameterField.createValueField("project"))
                                                       .build();
    GcpConnectorDTO connectorDTO = GcpConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    GarDelegateRequest amiArtifactDelegateRequest = ArtifactConfigToDelegateReqMapper.getGarDelegateRequest(
        garArtifactInfo, connectorDTO, encryptedDataDetailList, "");

    assertThat(amiArtifactDelegateRequest.getGcpConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(amiArtifactDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(amiArtifactDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY);
    assertThat(amiArtifactDelegateRequest.getVersionRegex()).isEqualTo("/*");
    assertThat(amiArtifactDelegateRequest.getVersion()).isNull();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetGARDelegateRequestRegexTagAsInputValidator() {
    GoogleArtifactRegistryConfig garArtifactInfo =
        GoogleArtifactRegistryConfig.builder()
            .region(ParameterField.createValueField("region"))
            .version(ParameterField.createValueFieldWithInputSetValidator(
                LAST_PUBLISHED_EXPRESSION, new InputSetValidator(InputSetValidatorType.REGEX, "stable*"), true))
            .repositoryName(ParameterField.createValueField("repo"))
            .pkg(ParameterField.createValueField("pkg"))
            .project(ParameterField.createValueField("project"))
            .build();
    GcpConnectorDTO connectorDTO = GcpConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    GarDelegateRequest amiArtifactDelegateRequest = ArtifactConfigToDelegateReqMapper.getGarDelegateRequest(
        garArtifactInfo, connectorDTO, encryptedDataDetailList, "");

    assertThat(amiArtifactDelegateRequest.getGcpConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(amiArtifactDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(amiArtifactDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY);
    assertThat(amiArtifactDelegateRequest.getVersionRegex()).isEqualTo("stable*");
    assertThat(amiArtifactDelegateRequest.getVersion()).isEmpty();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetECRDelegateRequestTagAsRegex() {
    EcrArtifactConfig ecrArtifactConfig = EcrArtifactConfig.builder()
                                              .region(ParameterField.createValueField("region"))
                                              .tag(ParameterField.createValueField(LAST_PUBLISHED_EXPRESSION))
                                              .imagePath(ParameterField.createValueField("image"))
                                              .build();
    AwsConnectorDTO connectorDTO = AwsConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    EcrArtifactDelegateRequest delegateRequest = ArtifactConfigToDelegateReqMapper.getEcrDelegateRequest(
        ecrArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(delegateRequest.getAwsConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(delegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(delegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.ECR);
    assertThat(delegateRequest.getTagRegex()).isEqualTo("*");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetECRDelegateRequestTagAsNull() {
    EcrArtifactConfig ecrArtifactConfig = EcrArtifactConfig.builder()
                                              .region(ParameterField.createValueField("region"))
                                              .tag(ParameterField.createValueField(null))
                                              .tagRegex(ParameterField.createValueField(null))
                                              .imagePath(ParameterField.createValueField("image"))
                                              .build();
    AwsConnectorDTO connectorDTO = AwsConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    EcrArtifactDelegateRequest delegateRequest = ArtifactConfigToDelegateReqMapper.getEcrDelegateRequest(
        ecrArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(delegateRequest.getAwsConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(delegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(delegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.ECR);
    assertThat(delegateRequest.getTagRegex()).isEqualTo(ACCEPT_ALL_REGEX);
    assertThat(delegateRequest.getTag()).isNull();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetECRDelegateRequestTagAsInputValidator() {
    EcrArtifactConfig ecrArtifactConfig =
        EcrArtifactConfig.builder()
            .region(ParameterField.createValueField("region"))
            .tag(ParameterField.createValueFieldWithInputSetValidator(
                LAST_PUBLISHED_EXPRESSION, new InputSetValidator(InputSetValidatorType.REGEX, "stable*"), true))
            .imagePath(ParameterField.createValueField("image"))
            .build();
    AwsConnectorDTO connectorDTO = AwsConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    EcrArtifactDelegateRequest delegateRequest = ArtifactConfigToDelegateReqMapper.getEcrDelegateRequest(
        ecrArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(delegateRequest.getAwsConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(delegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(delegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.ECR);
    assertThat(delegateRequest.getTagRegex()).isEqualTo("stable*");
    assertThat(delegateRequest.getTag()).isEqualTo(LAST_PUBLISHED_EXPRESSION);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetNexusDelegateRequestTagAsRegex() {
    NexusRegistryDockerConfig nexusRegistryDockerConfig =
        NexusRegistryDockerConfig.builder()
            .artifactPath(ParameterField.createValueField("IMAGE"))
            .repositoryPort(ParameterField.createValueField("TEST_REPO"))
            .build();

    NexusRegistryArtifactConfig artifactConfig =
        NexusRegistryArtifactConfig.builder()
            .repository(ParameterField.createValueField("TEST_REPO"))
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.docker.name()))
            .nexusRegistryConfigSpec(nexusRegistryDockerConfig)
            .tag(ParameterField.createValueField(LAST_PUBLISHED_EXPRESSION))
            .build();
    NexusConnectorDTO connectorDTO = NexusConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    NexusArtifactDelegateRequest delegateRequest = ArtifactConfigToDelegateReqMapper.getNexusArtifactDelegateRequest(
        artifactConfig, connectorDTO, encryptedDataDetailList, "");
    nexusRegistryDockerConfig = (NexusRegistryDockerConfig) artifactConfig.getNexusRegistryConfigSpec();

    assertThat(delegateRequest.getNexusConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(delegateRequest.getRepositoryName()).isEqualTo(artifactConfig.getRepository().getValue());
    assertThat(delegateRequest.getRepositoryFormat()).isEqualTo(RepositoryFormat.docker.name());
    assertThat(delegateRequest.getRepositoryPort()).isEqualTo(nexusRegistryDockerConfig.getRepositoryPort().getValue());
    assertThat(delegateRequest.getArtifactRepositoryUrl()).isNull();
    assertThat(delegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(delegateRequest.getArtifactPath()).isEqualTo(nexusRegistryDockerConfig.getArtifactPath().getValue());
    assertThat(delegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.NEXUS3_REGISTRY);
    assertThat(delegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(delegateRequest.getTagRegex()).isEqualTo(".*?");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetNexusDelegateRequestTagAsNull() {
    NexusRegistryDockerConfig nexusRegistryDockerConfig =
        NexusRegistryDockerConfig.builder()
            .artifactPath(ParameterField.createValueField("IMAGE"))
            .repositoryPort(ParameterField.createValueField("TEST_REPO"))
            .build();

    NexusRegistryArtifactConfig artifactConfig =
        NexusRegistryArtifactConfig.builder()
            .repository(ParameterField.createValueField("TEST_REPO"))
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.docker.name()))
            .nexusRegistryConfigSpec(nexusRegistryDockerConfig)
            .tag(ParameterField.createValueField(null))
            .tagRegex(ParameterField.createValueField(null))
            .build();
    NexusConnectorDTO connectorDTO = NexusConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    NexusArtifactDelegateRequest delegateRequest = ArtifactConfigToDelegateReqMapper.getNexusArtifactDelegateRequest(
        artifactConfig, connectorDTO, encryptedDataDetailList, "");
    nexusRegistryDockerConfig = (NexusRegistryDockerConfig) artifactConfig.getNexusRegistryConfigSpec();

    assertThat(delegateRequest.getNexusConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(delegateRequest.getRepositoryName()).isEqualTo(artifactConfig.getRepository().getValue());
    assertThat(delegateRequest.getRepositoryFormat()).isEqualTo(RepositoryFormat.docker.name());
    assertThat(delegateRequest.getRepositoryPort()).isEqualTo(nexusRegistryDockerConfig.getRepositoryPort().getValue());
    assertThat(delegateRequest.getArtifactRepositoryUrl()).isNull();
    assertThat(delegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(delegateRequest.getArtifactPath()).isEqualTo(nexusRegistryDockerConfig.getArtifactPath().getValue());
    assertThat(delegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.NEXUS3_REGISTRY);
    assertThat(delegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(delegateRequest.getTagRegex()).isEqualTo(ACCEPT_ALL_REGEX);
    assertThat(delegateRequest.getTag()).isNull();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetNexusDelegateRequestTagAsInputValidator() {
    NexusRegistryDockerConfig nexusRegistryDockerConfig =
        NexusRegistryDockerConfig.builder()
            .artifactPath(ParameterField.createValueField("IMAGE"))
            .repositoryPort(ParameterField.createValueField("TEST_REPO"))
            .build();

    NexusRegistryArtifactConfig artifactConfig =
        NexusRegistryArtifactConfig.builder()
            .repository(ParameterField.createValueField("TEST_REPO"))
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.docker.name()))
            .nexusRegistryConfigSpec(nexusRegistryDockerConfig)
            .tag(ParameterField.createValueFieldWithInputSetValidator(
                LAST_PUBLISHED_EXPRESSION, new InputSetValidator(InputSetValidatorType.REGEX, "stable*"), true))
            .build();
    NexusConnectorDTO connectorDTO = NexusConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    NexusArtifactDelegateRequest delegateRequest = ArtifactConfigToDelegateReqMapper.getNexusArtifactDelegateRequest(
        artifactConfig, connectorDTO, encryptedDataDetailList, "");
    nexusRegistryDockerConfig = (NexusRegistryDockerConfig) artifactConfig.getNexusRegistryConfigSpec();

    assertThat(delegateRequest.getNexusConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(delegateRequest.getRepositoryName()).isEqualTo(artifactConfig.getRepository().getValue());
    assertThat(delegateRequest.getRepositoryFormat()).isEqualTo(RepositoryFormat.docker.name());
    assertThat(delegateRequest.getRepositoryPort()).isEqualTo(nexusRegistryDockerConfig.getRepositoryPort().getValue());
    assertThat(delegateRequest.getArtifactRepositoryUrl()).isNull();
    assertThat(delegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(delegateRequest.getArtifactPath()).isEqualTo(nexusRegistryDockerConfig.getArtifactPath().getValue());
    assertThat(delegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.NEXUS3_REGISTRY);
    assertThat(delegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(delegateRequest.getTagRegex()).isEqualTo("stable*");
    assertThat(delegateRequest.getTag()).isEqualTo(LAST_PUBLISHED_EXPRESSION);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetNexusDelegateRequestTagAsRegexNonDocker() {
    NexusRegistryNpmConfig nexusRegistryNpmConfig =
        NexusRegistryNpmConfig.builder().packageName(ParameterField.createValueField("package")).build();

    Nexus2RegistryArtifactConfig artifactConfig =
        Nexus2RegistryArtifactConfig.builder()
            .repository(ParameterField.createValueField("TEST_REPO"))
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.npm.name()))
            .nexusRegistryConfigSpec(nexusRegistryNpmConfig)
            .tag(ParameterField.createValueField(LAST_PUBLISHED_EXPRESSION))
            .build();
    NexusConnectorDTO connectorDTO = NexusConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    NexusArtifactDelegateRequest delegateRequest = ArtifactConfigToDelegateReqMapper.getNexus2ArtifactDelegateRequest(
        artifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(delegateRequest.getNexusConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(delegateRequest.getRepositoryName()).isEqualTo(artifactConfig.getRepository().getValue());
    assertThat(delegateRequest.getRepositoryFormat()).isEqualTo(RepositoryFormat.npm.name());
    assertThat(delegateRequest.getArtifactRepositoryUrl()).isNull();
    assertThat(delegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(delegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.NEXUS2_REGISTRY);
    assertThat(delegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(delegateRequest.getTagRegex()).isEqualTo(".*?");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetNexusDelegateRequestTagAsNullNonDocker() {
    NexusRegistryNpmConfig nexusRegistryNpmConfig =
        NexusRegistryNpmConfig.builder().packageName(ParameterField.createValueField("package")).build();

    Nexus2RegistryArtifactConfig artifactConfig =
        Nexus2RegistryArtifactConfig.builder()
            .repository(ParameterField.createValueField("TEST_REPO"))
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.npm.name()))
            .nexusRegistryConfigSpec(nexusRegistryNpmConfig)
            .tag(ParameterField.createValueField(null))
            .tagRegex(ParameterField.createValueField(null))
            .build();
    NexusConnectorDTO connectorDTO = NexusConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    NexusArtifactDelegateRequest delegateRequest = ArtifactConfigToDelegateReqMapper.getNexus2ArtifactDelegateRequest(
        artifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(delegateRequest.getNexusConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(delegateRequest.getRepositoryName()).isEqualTo(artifactConfig.getRepository().getValue());
    assertThat(delegateRequest.getRepositoryFormat()).isEqualTo(RepositoryFormat.npm.name());
    assertThat(delegateRequest.getArtifactRepositoryUrl()).isNull();
    assertThat(delegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(delegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.NEXUS2_REGISTRY);
    assertThat(delegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(delegateRequest.getTagRegex()).isEqualTo(ACCEPT_ALL_REGEX);
    assertThat(delegateRequest.getTag()).isNull();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetNexusDelegateRequestTagAsInputValidatorNonDocker() {
    NexusRegistryNpmConfig nexusRegistryNpmConfig =
        NexusRegistryNpmConfig.builder().packageName(ParameterField.createValueField("package")).build();

    Nexus2RegistryArtifactConfig artifactConfig =
        Nexus2RegistryArtifactConfig.builder()
            .repository(ParameterField.createValueField("TEST_REPO"))
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.npm.name()))
            .nexusRegistryConfigSpec(nexusRegistryNpmConfig)
            .tag(ParameterField.createValueFieldWithInputSetValidator(
                LAST_PUBLISHED_EXPRESSION, new InputSetValidator(InputSetValidatorType.REGEX, "stable*"), true))
            .build();
    NexusConnectorDTO connectorDTO = NexusConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    NexusArtifactDelegateRequest delegateRequest = ArtifactConfigToDelegateReqMapper.getNexus2ArtifactDelegateRequest(
        artifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(delegateRequest.getNexusConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(delegateRequest.getRepositoryName()).isEqualTo(artifactConfig.getRepository().getValue());
    assertThat(delegateRequest.getRepositoryFormat()).isEqualTo(RepositoryFormat.npm.name());
    assertThat(delegateRequest.getArtifactRepositoryUrl()).isNull();
    assertThat(delegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(delegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.NEXUS2_REGISTRY);
    assertThat(delegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(delegateRequest.getTagRegex()).isEqualTo("stable*");
    assertThat(delegateRequest.getTag()).isEqualTo(LAST_PUBLISHED_EXPRESSION);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetArtifactoryDelegateRequestTagAsRegex() {
    ArtifactoryRegistryArtifactConfig artifactConfig =
        ArtifactoryRegistryArtifactConfig.builder()
            .repository(ParameterField.createValueField("TEST_REPO"))
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.docker.name()))
            .repositoryUrl(ParameterField.createValueField("harness-repo.jfrog.io"))
            .artifactPath(ParameterField.createValueField("IMAGE"))
            .tag(ParameterField.createValueField(LAST_PUBLISHED_EXPRESSION))
            .build();
    ArtifactoryConnectorDTO connectorDTO = ArtifactoryConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    ArtifactoryArtifactDelegateRequest delegateRequest =
        (ArtifactoryArtifactDelegateRequest) ArtifactConfigToDelegateReqMapper.getArtifactoryArtifactDelegateRequest(
            artifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(delegateRequest.getArtifactoryConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(delegateRequest.getRepositoryName()).isEqualTo(artifactConfig.getRepository().getValue());
    assertThat(delegateRequest.getRepositoryFormat()).isEqualTo(RepositoryFormat.docker.name());
    assertThat(delegateRequest.getArtifactRepositoryUrl()).isEqualTo(artifactConfig.getRepositoryUrl().getValue());
    assertThat(delegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(delegateRequest.getArtifactPath()).isEqualTo(artifactConfig.getArtifactPath().getValue());
    assertThat(delegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.ARTIFACTORY_REGISTRY);
    assertThat(delegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(delegateRequest.getTagRegex()).isEqualTo(ACCEPT_ALL_REGEX);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetArtifactoryDelegateRequestTagAsNull() {
    ArtifactoryRegistryArtifactConfig artifactConfig =
        ArtifactoryRegistryArtifactConfig.builder()
            .repository(ParameterField.createValueField("TEST_REPO"))
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.docker.name()))
            .repositoryUrl(ParameterField.createValueField("harness-repo.jfrog.io"))
            .artifactPath(ParameterField.createValueField("IMAGE"))
            .tag(ParameterField.createValueField(null))
            .tagRegex(ParameterField.createValueField(null))
            .build();
    ArtifactoryConnectorDTO connectorDTO = ArtifactoryConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    ArtifactoryArtifactDelegateRequest delegateRequest =
        (ArtifactoryArtifactDelegateRequest) ArtifactConfigToDelegateReqMapper.getArtifactoryArtifactDelegateRequest(
            artifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(delegateRequest.getArtifactoryConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(delegateRequest.getRepositoryName()).isEqualTo(artifactConfig.getRepository().getValue());
    assertThat(delegateRequest.getRepositoryFormat()).isEqualTo(RepositoryFormat.docker.name());
    assertThat(delegateRequest.getArtifactRepositoryUrl()).isEqualTo(artifactConfig.getRepositoryUrl().getValue());
    assertThat(delegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(delegateRequest.getArtifactPath()).isEqualTo(artifactConfig.getArtifactPath().getValue());
    assertThat(delegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.ARTIFACTORY_REGISTRY);
    assertThat(delegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(delegateRequest.getTagRegex()).isEqualTo(ACCEPT_ALL_REGEX);
    assertThat(delegateRequest.getTag()).isNull();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetArtifactoryDelegateRequestTagAsInputValidator() {
    ArtifactoryRegistryArtifactConfig artifactConfig =
        ArtifactoryRegistryArtifactConfig.builder()
            .repository(ParameterField.createValueField("TEST_REPO"))
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.docker.name()))
            .repositoryUrl(ParameterField.createValueField("harness-repo.jfrog.io"))
            .artifactPath(ParameterField.createValueField("IMAGE"))
            .tag(ParameterField.createValueFieldWithInputSetValidator(
                LAST_PUBLISHED_EXPRESSION, new InputSetValidator(InputSetValidatorType.REGEX, "stable*"), true))
            .build();
    ArtifactoryConnectorDTO connectorDTO = ArtifactoryConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    ArtifactoryArtifactDelegateRequest delegateRequest =
        (ArtifactoryArtifactDelegateRequest) ArtifactConfigToDelegateReqMapper.getArtifactoryArtifactDelegateRequest(
            artifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(delegateRequest.getArtifactoryConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(delegateRequest.getRepositoryName()).isEqualTo(artifactConfig.getRepository().getValue());
    assertThat(delegateRequest.getRepositoryFormat()).isEqualTo(RepositoryFormat.docker.name());
    assertThat(delegateRequest.getArtifactRepositoryUrl()).isEqualTo(artifactConfig.getRepositoryUrl().getValue());
    assertThat(delegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(delegateRequest.getArtifactPath()).isEqualTo(artifactConfig.getArtifactPath().getValue());
    assertThat(delegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.ARTIFACTORY_REGISTRY);
    assertThat(delegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(delegateRequest.getTagRegex()).isEqualTo("stable*");
    assertThat(delegateRequest.getTag()).isEqualTo(LAST_PUBLISHED_EXPRESSION);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetAcrDelegateRequestWithTagAsRegex() {
    AcrArtifactConfig acrArtifactConfig =
        AcrArtifactConfig.builder()
            .subscriptionId(ParameterField.createValueField("123456-6543-3456-654321"))
            .registry(ParameterField.createValueField("AZURE_CR"))
            .repository(ParameterField.createValueField("library/testapp"))
            .tag(ParameterField.createValueField(LAST_PUBLISHED_EXPRESSION))
            .build();
    AzureConnectorDTO connectorDTO = AzureConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    AcrArtifactDelegateRequest acrDelegateRequest =
        (AcrArtifactDelegateRequest) ArtifactConfigToDelegateReqMapper.getAcrDelegateRequest(
            acrArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(acrDelegateRequest.getAzureConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(acrDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(acrDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.ACR);
    assertThat(acrDelegateRequest.getSubscription()).isEqualTo(acrArtifactConfig.getSubscriptionId().getValue());
    assertThat(acrDelegateRequest.getRegistry()).isEqualTo(acrArtifactConfig.getRegistry().getValue());
    assertThat(acrDelegateRequest.getRepository()).isEqualTo(acrArtifactConfig.getRepository().getValue());
    assertThat(acrDelegateRequest.getTagRegex()).isEqualTo(".*?");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetAcrDelegateRequestWithTagAsNull() {
    AcrArtifactConfig acrArtifactConfig =
        AcrArtifactConfig.builder()
            .subscriptionId(ParameterField.createValueField("123456-6543-3456-654321"))
            .registry(ParameterField.createValueField("AZURE_CR"))
            .repository(ParameterField.createValueField("library/testapp"))
            .tag(ParameterField.createValueField(null))
            .tagRegex(ParameterField.createValueField(ACCEPT_ALL_REGEX))
            .build();
    AzureConnectorDTO connectorDTO = AzureConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    AcrArtifactDelegateRequest acrDelegateRequest =
        (AcrArtifactDelegateRequest) ArtifactConfigToDelegateReqMapper.getAcrDelegateRequest(
            acrArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(acrDelegateRequest.getAzureConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(acrDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(acrDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.ACR);
    assertThat(acrDelegateRequest.getSubscription()).isEqualTo(acrArtifactConfig.getSubscriptionId().getValue());
    assertThat(acrDelegateRequest.getRegistry()).isEqualTo(acrArtifactConfig.getRegistry().getValue());
    assertThat(acrDelegateRequest.getRepository()).isEqualTo(acrArtifactConfig.getRepository().getValue());
    assertThat(acrDelegateRequest.getTagRegex()).isEqualTo(ACCEPT_ALL_REGEX);
    assertThat(acrDelegateRequest.getTag()).isEqualTo("");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetAcrDelegateRequestWithTagAsValidator() {
    AcrArtifactConfig acrArtifactConfig =
        AcrArtifactConfig.builder()
            .subscriptionId(ParameterField.createValueField("123456-6543-3456-654321"))
            .registry(ParameterField.createValueField("AZURE_CR"))
            .repository(ParameterField.createValueField("library/testapp"))
            .tag(ParameterField.createValueFieldWithInputSetValidator(
                LAST_PUBLISHED_EXPRESSION, new InputSetValidator(InputSetValidatorType.REGEX, "stable*"), true))
            .build();
    AzureConnectorDTO connectorDTO = AzureConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    AcrArtifactDelegateRequest acrDelegateRequest =
        (AcrArtifactDelegateRequest) ArtifactConfigToDelegateReqMapper.getAcrDelegateRequest(
            acrArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(acrDelegateRequest.getAzureConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(acrDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(acrDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.ACR);
    assertThat(acrDelegateRequest.getSubscription()).isEqualTo(acrArtifactConfig.getSubscriptionId().getValue());
    assertThat(acrDelegateRequest.getRegistry()).isEqualTo(acrArtifactConfig.getRegistry().getValue());
    assertThat(acrDelegateRequest.getRepository()).isEqualTo(acrArtifactConfig.getRepository().getValue());
    assertThat(acrDelegateRequest.getTagRegex()).isEqualTo("stable*");
    assertThat(acrDelegateRequest.getTag()).isEqualTo(LAST_PUBLISHED_EXPRESSION);
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testGetGCStorageDelegateRequest() {
    GoogleCloudStorageArtifactConfig googleCloudStorageArtifactConfig =
        GoogleCloudStorageArtifactConfig.builder()
            .project(ParameterField.createValueField("test-project"))
            .bucket(ParameterField.createValueField("test-bucket"))
            .artifactPath(ParameterField.createValueField("test/path"))
            .build();
    GcpConnectorDTO connectorDTO = GcpConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    GoogleCloudStorageArtifactDelegateRequest googleCloudStorageArtifactDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getGoogleCloudStorageArtifactDelegateRequest(
            googleCloudStorageArtifactConfig, connectorDTO, encryptedDataDetailList, CONNECTOR_REF);

    assertThat(googleCloudStorageArtifactDelegateRequest.getGcpConnectorDTO()).isSameAs(connectorDTO);
    assertThat(googleCloudStorageArtifactDelegateRequest.getEncryptedDataDetails()).isSameAs(encryptedDataDetailList);
    assertThat(googleCloudStorageArtifactDelegateRequest.getArtifactPath())
        .isEqualTo(googleCloudStorageArtifactConfig.getArtifactPath().getValue());
    assertThat(googleCloudStorageArtifactDelegateRequest.getBucket())
        .isEqualTo(googleCloudStorageArtifactConfig.getBucket().getValue());
    assertThat(googleCloudStorageArtifactDelegateRequest.getProject())
        .isEqualTo(googleCloudStorageArtifactConfig.getProject().getValue());
    assertThat(googleCloudStorageArtifactDelegateRequest.getConnectorRef()).isEqualTo(CONNECTOR_REF);
    assertThat(googleCloudStorageArtifactDelegateRequest.getSourceType())
        .isEqualTo(ArtifactSourceType.GOOGLE_CLOUD_STORAGE_ARTIFACT);
    assertThat(googleCloudStorageArtifactDelegateRequest.getArtifactPathRegex()).isNull();
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetGCStorageDelegateRequestLastPublishedWithRegex() {
    GoogleCloudStorageArtifactConfig googleCloudStorageArtifactConfig =
        GoogleCloudStorageArtifactConfig.builder()
            .project(ParameterField.createValueField("test-project"))
            .bucket(ParameterField.createValueField("test-bucket"))
            .artifactPath(LAST_PUBLISHED_EXPRESSION_REGEX)
            .build();
    GcpConnectorDTO connectorDTO = GcpConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    GoogleCloudStorageArtifactDelegateRequest googleCloudStorageArtifactDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getGoogleCloudStorageArtifactDelegateRequest(
            googleCloudStorageArtifactConfig, connectorDTO, encryptedDataDetailList, CONNECTOR_REF);

    assertThat(googleCloudStorageArtifactDelegateRequest.getGcpConnectorDTO()).isSameAs(connectorDTO);
    assertThat(googleCloudStorageArtifactDelegateRequest.getEncryptedDataDetails()).isSameAs(encryptedDataDetailList);
    assertThat(googleCloudStorageArtifactDelegateRequest.getArtifactPath()).isEqualTo("");
    assertThat(googleCloudStorageArtifactDelegateRequest.getBucket())
        .isEqualTo(googleCloudStorageArtifactConfig.getBucket().getValue());
    assertThat(googleCloudStorageArtifactDelegateRequest.getProject())
        .isEqualTo(googleCloudStorageArtifactConfig.getProject().getValue());
    assertThat(googleCloudStorageArtifactDelegateRequest.getConnectorRef()).isEqualTo(CONNECTOR_REF);
    assertThat(googleCloudStorageArtifactDelegateRequest.getSourceType())
        .isEqualTo(ArtifactSourceType.GOOGLE_CLOUD_STORAGE_ARTIFACT);
    assertThat(googleCloudStorageArtifactDelegateRequest.getArtifactPathRegex()).isEqualTo(TAG);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetGCStorageDelegateRequestLastPublished() {
    GoogleCloudStorageArtifactConfig googleCloudStorageArtifactConfig =
        GoogleCloudStorageArtifactConfig.builder()
            .project(ParameterField.createValueField("test-project"))
            .bucket(ParameterField.createValueField("test-bucket"))
            .artifactPath(LAST_PUBLISHED_EXPRESSION_PARAMETER)
            .build();
    GcpConnectorDTO connectorDTO = GcpConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    GoogleCloudStorageArtifactDelegateRequest googleCloudStorageArtifactDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getGoogleCloudStorageArtifactDelegateRequest(
            googleCloudStorageArtifactConfig, connectorDTO, encryptedDataDetailList, CONNECTOR_REF);

    assertThat(googleCloudStorageArtifactDelegateRequest.getGcpConnectorDTO()).isSameAs(connectorDTO);
    assertThat(googleCloudStorageArtifactDelegateRequest.getEncryptedDataDetails()).isSameAs(encryptedDataDetailList);
    assertThat(googleCloudStorageArtifactDelegateRequest.getArtifactPath()).isEqualTo("");
    assertThat(googleCloudStorageArtifactDelegateRequest.getBucket())
        .isEqualTo(googleCloudStorageArtifactConfig.getBucket().getValue());
    assertThat(googleCloudStorageArtifactDelegateRequest.getProject())
        .isEqualTo(googleCloudStorageArtifactConfig.getProject().getValue());
    assertThat(googleCloudStorageArtifactDelegateRequest.getConnectorRef()).isEqualTo(CONNECTOR_REF);
    assertThat(googleCloudStorageArtifactDelegateRequest.getSourceType())
        .isEqualTo(ArtifactSourceType.GOOGLE_CLOUD_STORAGE_ARTIFACT);
    assertThat(googleCloudStorageArtifactDelegateRequest.getArtifactPathRegex()).isEqualTo(ALL_REGEX);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetGCStorageDelegateRequestEmptyProject() {
    GoogleCloudStorageArtifactConfig googleCloudStorageArtifactConfig =
        GoogleCloudStorageArtifactConfig.builder()
            .project(ParameterField.createValueField("test-project"))
            .bucket(ParameterField.createValueField(""))
            .artifactPath(LAST_PUBLISHED_EXPRESSION_PARAMETER)
            .build();
    GcpConnectorDTO connectorDTO = GcpConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    assertThatThrownBy(()
                           -> ArtifactConfigToDelegateReqMapper.getGoogleCloudStorageArtifactDelegateRequest(
                               googleCloudStorageArtifactConfig, connectorDTO, encryptedDataDetailList, CONNECTOR_REF))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Please input bucket name.");
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetGCStorageDelegateRequestEmptyBucket() {
    GoogleCloudStorageArtifactConfig googleCloudStorageArtifactConfig =
        GoogleCloudStorageArtifactConfig.builder()
            .project(ParameterField.createValueField(""))
            .bucket(ParameterField.createValueField("test-bucket"))
            .artifactPath(LAST_PUBLISHED_EXPRESSION_PARAMETER)
            .build();
    GcpConnectorDTO connectorDTO = GcpConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    assertThatThrownBy(()
                           -> ArtifactConfigToDelegateReqMapper.getGoogleCloudStorageArtifactDelegateRequest(
                               googleCloudStorageArtifactConfig, connectorDTO, encryptedDataDetailList, CONNECTOR_REF))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Please input project name.");
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testGetGCSourceDelegateRequestWithBranch() {
    GoogleCloudSourceArtifactConfig googleCloudSourceArtifactConfig =
        GoogleCloudSourceArtifactConfig.builder()
            .project(ParameterField.createValueField("test-project"))
            .repository(ParameterField.createValueField("test-repo"))
            .sourceDirectory(ParameterField.createValueField("test/path"))
            .fetchType(GoogleCloudSourceFetchType.BRANCH)
            .branch(ParameterField.createValueField("test-branch"))
            .build();
    GcpConnectorDTO connectorDTO = GcpConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    GoogleCloudSourceArtifactDelegateRequest googleCloudSourceArtifactDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getGoogleCloudSourceArtifactDelegateRequest(
            googleCloudSourceArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(googleCloudSourceArtifactDelegateRequest.getGcpConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(googleCloudSourceArtifactDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(googleCloudSourceArtifactDelegateRequest.getBranch())
        .isEqualTo(googleCloudSourceArtifactConfig.getBranch().getValue());
    assertThat(googleCloudSourceArtifactDelegateRequest.getSourceDirectory())
        .isEqualTo(googleCloudSourceArtifactConfig.getSourceDirectory().getValue());
    assertThat(googleCloudSourceArtifactDelegateRequest.getRepository())
        .isEqualTo(googleCloudSourceArtifactConfig.getRepository().getValue());
    assertThat(googleCloudSourceArtifactDelegateRequest.getGoogleCloudSourceFetchType().toString())
        .isEqualTo(googleCloudSourceArtifactConfig.getFetchType().toString());
    assertThat(googleCloudSourceArtifactDelegateRequest.getProject())
        .isEqualTo(googleCloudSourceArtifactConfig.getProject().getValue());
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testGetGCSourceDelegateRequestWithTag() {
    GoogleCloudSourceArtifactConfig googleCloudSourceArtifactConfig =
        GoogleCloudSourceArtifactConfig.builder()
            .project(ParameterField.createValueField("test-project"))
            .repository(ParameterField.createValueField("test-repo"))
            .sourceDirectory(ParameterField.createValueField("test/path"))
            .fetchType(GoogleCloudSourceFetchType.TAG)
            .tag(ParameterField.createValueField("test-tag"))
            .build();
    GcpConnectorDTO connectorDTO = GcpConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    GoogleCloudSourceArtifactDelegateRequest googleCloudSourceArtifactDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getGoogleCloudSourceArtifactDelegateRequest(
            googleCloudSourceArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(googleCloudSourceArtifactDelegateRequest.getGcpConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(googleCloudSourceArtifactDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(googleCloudSourceArtifactDelegateRequest.getTag())
        .isEqualTo(googleCloudSourceArtifactConfig.getTag().getValue());
    assertThat(googleCloudSourceArtifactDelegateRequest.getSourceDirectory())
        .isEqualTo(googleCloudSourceArtifactConfig.getSourceDirectory().getValue());
    assertThat(googleCloudSourceArtifactDelegateRequest.getRepository())
        .isEqualTo(googleCloudSourceArtifactConfig.getRepository().getValue());
    assertThat(googleCloudSourceArtifactDelegateRequest.getGoogleCloudSourceFetchType().toString())
        .isEqualTo(googleCloudSourceArtifactConfig.getFetchType().toString());
    assertThat(googleCloudSourceArtifactDelegateRequest.getProject())
        .isEqualTo(googleCloudSourceArtifactConfig.getProject().getValue());
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testGetGCSourceDelegateRequestWithCommitId() {
    GoogleCloudSourceArtifactConfig googleCloudSourceArtifactConfig =
        GoogleCloudSourceArtifactConfig.builder()
            .project(ParameterField.createValueField("test-project"))
            .repository(ParameterField.createValueField("test-repo"))
            .sourceDirectory(ParameterField.createValueField("test/path"))
            .fetchType(GoogleCloudSourceFetchType.COMMIT)
            .commitId(ParameterField.createValueField("test-commit"))
            .build();
    GcpConnectorDTO connectorDTO = GcpConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    GoogleCloudSourceArtifactDelegateRequest googleCloudSourceArtifactDelegateRequest =
        ArtifactConfigToDelegateReqMapper.getGoogleCloudSourceArtifactDelegateRequest(
            googleCloudSourceArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(googleCloudSourceArtifactDelegateRequest.getGcpConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(googleCloudSourceArtifactDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(googleCloudSourceArtifactDelegateRequest.getCommitId())
        .isEqualTo(googleCloudSourceArtifactConfig.getCommitId().getValue());
    assertThat(googleCloudSourceArtifactDelegateRequest.getSourceDirectory())
        .isEqualTo(googleCloudSourceArtifactConfig.getSourceDirectory().getValue());
    assertThat(googleCloudSourceArtifactDelegateRequest.getRepository())
        .isEqualTo(googleCloudSourceArtifactConfig.getRepository().getValue());
    assertThat(googleCloudSourceArtifactDelegateRequest.getGoogleCloudSourceFetchType().toString())
        .isEqualTo(googleCloudSourceArtifactConfig.getFetchType().toString());
    assertThat(googleCloudSourceArtifactDelegateRequest.getProject())
        .isEqualTo(googleCloudSourceArtifactConfig.getProject().getValue());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetS3DelegateRequestWithTagAsRegex() {
    AmazonS3ArtifactConfig amazonS3ArtifactConfig =
        AmazonS3ArtifactConfig.builder()
            .filePath(ParameterField.createValueField(LAST_PUBLISHED_EXPRESSION))
            .filePathRegex(ParameterField.createValueField(""))
            .bucketName(ParameterField.createValueField("test"))
            .build();
    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    S3ArtifactDelegateRequest s3ArtifactDelegateRequest = ArtifactConfigToDelegateReqMapper.getAmazonS3DelegateRequest(
        amazonS3ArtifactConfig, awsConnectorDTO, encryptedDataDetailList, "");

    assertThat(s3ArtifactDelegateRequest.getAwsConnectorDTO()).isEqualTo(awsConnectorDTO);
    assertThat(s3ArtifactDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(s3ArtifactDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.AMAZONS3);
    assertThat(s3ArtifactDelegateRequest.getFilePathRegex()).isEqualTo("*");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetS3DelegateRequestWithTagAsRegexGeneric() {
    ArtifactoryRegistryArtifactConfig artifactConfig =
        ArtifactoryRegistryArtifactConfig.builder()
            .repository(ParameterField.createValueField("TEST_REPO"))
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.generic.name()))
            .artifactPath(ParameterField.createValueField(LAST_PUBLISHED_EXPRESSION))
            .artifactDirectory(ParameterField.createValueField("IMAGE"))
            .build();
    ArtifactoryConnectorDTO connectorDTO = ArtifactoryConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    ArtifactoryGenericArtifactDelegateRequest delegateRequest =
        (ArtifactoryGenericArtifactDelegateRequest) ArtifactConfigToDelegateReqMapper
            .getArtifactoryArtifactDelegateRequest(artifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(delegateRequest.getArtifactoryConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(delegateRequest.getRepositoryName()).isEqualTo(artifactConfig.getRepository().getValue());
    assertThat(delegateRequest.getRepositoryFormat()).isEqualTo(RepositoryFormat.generic.name());
    assertThat(delegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(delegateRequest.getArtifactPath()).isEqualTo(artifactConfig.getArtifactPath().getValue());
    assertThat(delegateRequest.getArtifactDirectory()).isEqualTo(artifactConfig.getArtifactDirectory().getValue());
    assertThat(delegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.ARTIFACTORY_REGISTRY);
    assertThat(delegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(delegateRequest.getArtifactPathFilter()).isEqualTo("*");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetS3DelegateRequestWithTagRegexGeneric() {
    ArtifactoryRegistryArtifactConfig artifactConfig =
        ArtifactoryRegistryArtifactConfig.builder()
            .repository(ParameterField.createValueField("TEST_REPO"))
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.generic.name()))
            .artifactPath(ParameterField.createValueFieldWithInputSetValidator(
                LAST_PUBLISHED_EXPRESSION, new InputSetValidator(InputSetValidatorType.REGEX, "stable*"), true))
            .artifactDirectory(ParameterField.createValueField("IMAGE"))
            .build();
    ArtifactoryConnectorDTO connectorDTO = ArtifactoryConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    ArtifactoryGenericArtifactDelegateRequest delegateRequest =
        (ArtifactoryGenericArtifactDelegateRequest) ArtifactConfigToDelegateReqMapper
            .getArtifactoryArtifactDelegateRequest(artifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(delegateRequest.getArtifactoryConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(delegateRequest.getRepositoryName()).isEqualTo(artifactConfig.getRepository().getValue());
    assertThat(delegateRequest.getRepositoryFormat()).isEqualTo(RepositoryFormat.generic.name());
    assertThat(delegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(delegateRequest.getArtifactPath()).isEqualTo(artifactConfig.getArtifactPath().getValue());
    assertThat(delegateRequest.getArtifactDirectory()).isEqualTo(artifactConfig.getArtifactDirectory().getValue());
    assertThat(delegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.ARTIFACTORY_REGISTRY);
    assertThat(delegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(delegateRequest.getArtifactPathFilter()).isEqualTo("stable*");
  }
}
