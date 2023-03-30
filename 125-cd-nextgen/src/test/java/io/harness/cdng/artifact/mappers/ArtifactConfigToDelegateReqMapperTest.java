/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.mappers;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.InputSetValidatorType;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.bean.yaml.AMIArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactoryRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AzureArtifactsConfig;
import io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.EcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GithubPackagesArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleArtifactRegistryConfig;
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
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateRequest;
import io.harness.exception.InvalidArtifactServerException;
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
  public void testGetDockerDelegateRequestRegexValidator() {
    InputSetValidator inputSetValidator = new InputSetValidator(InputSetValidatorType.REGEX, "stable*");
    DockerHubArtifactConfig dockerHubArtifactConfig =
        DockerHubArtifactConfig.builder()
            .imagePath(ParameterField.createValueField("IMAGE"))
            .tagRegex(ParameterField.createExpressionField(true, "<+lastPublished.tag>", inputSetValidator, true))
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
    assertThat(dockerDelegateRequest.getTagRegex()).isEqualTo("stable*");
    assertThat(dockerDelegateRequest.getShouldFetchDockerV2DigestSHA256()).isEqualTo(false);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetGitHubDelegateRequestRegexValidator() {
    InputSetValidator inputSetValidator = new InputSetValidator(InputSetValidatorType.REGEX, "stable*");
    GithubPackagesArtifactConfig githubPackagesArtifactConfig =
        GithubPackagesArtifactConfig.builder()
            .version(ParameterField.createValueField("IMAGE"))
            .packageName(ParameterField.createValueField("PACKAGE"))
            .packageType(ParameterField.createValueField("type"))
            .org(ParameterField.createValueField("org"))
            .versionRegex(ParameterField.createExpressionField(true, "<+lastPublished.tag>", inputSetValidator, true))
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
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetAzureDelegateRequestRegexValidator() {
    InputSetValidator inputSetValidator = new InputSetValidator(InputSetValidatorType.REGEX, "stable*");
    AzureArtifactsConfig azureArtifactsConfig =
        AzureArtifactsConfig.builder()
            .version(ParameterField.createValueField("IMAGE"))
            .packageName(ParameterField.createValueField("PACKAGE"))
            .packageType(ParameterField.createValueField("type"))
            .project(ParameterField.createValueField("project"))
            .feed(ParameterField.createValueField("feed"))
            .versionRegex(ParameterField.createExpressionField(true, "<+lastPublished.tag>", inputSetValidator, true))
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
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetAMIDelegateRequestRegexValidator() {
    InputSetValidator inputSetValidator = new InputSetValidator(InputSetValidatorType.REGEX, "stable*");
    AMIArtifactConfig amiArtifactConfig =
        AMIArtifactConfig.builder()
            .version(ParameterField.createValueField("IMAGE"))
            .versionRegex(ParameterField.createExpressionField(true, "<+lastPublished.tag>", inputSetValidator, true))
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
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetJenkinsDelegateRequestRegexValidator() {
    InputSetValidator inputSetValidator = new InputSetValidator(InputSetValidatorType.REGEX, "stable*");
    JenkinsArtifactConfig jenkinsArtifactConfig =
        JenkinsArtifactConfig.builder()
            .artifactPath(ParameterField.createValueField("ARTIFACT"))
            .jobName(ParameterField.createValueField("JOB"))
            .build(ParameterField.createExpressionField(true, "<+lastPublished.tag>", inputSetValidator, true))
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
    assertThat(jenkinsArtifactDelegateRequest.getBuildNumber()).isEqualTo("stable*");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetBambooDelegateRequestRegexValidator() {
    InputSetValidator inputSetValidator = new InputSetValidator(InputSetValidatorType.REGEX, "stable*");
    BambooArtifactConfig bambooArtifactConfig =
        BambooArtifactConfig.builder()
            .artifactPaths(ParameterField.createValueField(Collections.singletonList("ARTIFACT")))
            .planKey(ParameterField.createValueField("PLAN"))
            .build(ParameterField.createExpressionField(true, "<+lastPublished.tag>", inputSetValidator, true))
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
    assertThat(bambooArtifactDelegateRequest.getBuildNumber()).isEqualTo("stable*");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetCustomDelegateRequestRegexValidator() {
    InputSetValidator inputSetValidator = new InputSetValidator(InputSetValidatorType.REGEX, "stable*");
    CustomArtifactConfig customArtifactConfig =
        CustomArtifactConfig.builder()
            .identifier("test")
            .primaryArtifact(true)
            .version(ParameterField.createValueField("v1"))
            .versionRegex(ParameterField.createExpressionField(true, "<+lastPublished.tag>", inputSetValidator, true))
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
    assertThat(customArtifactDelegateRequest.getVersionRegex()).isEqualTo("stable*");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetCustomDelegateRequestRegexValidators() {
    InputSetValidator inputSetValidator = new InputSetValidator(InputSetValidatorType.REGEX, "stable*");
    CustomArtifactConfig customArtifactConfig =
        CustomArtifactConfig.builder()
            .identifier("test")
            .primaryArtifact(true)
            .version(ParameterField.createValueField("v1"))
            .versionRegex(ParameterField.createExpressionField(true, "<+lastPublished.tag>", inputSetValidator, true))
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
            .versionRegex(ParameterField.createExpressionField(true, "<+lastPublished.tag>", inputSetValidator, true))
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
    assertThat(customArtifactDelegateRequest.getVersionRegex()).isEqualTo("stable*");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetGCRDelegateRequestRegexValidator() {
    InputSetValidator inputSetValidator = new InputSetValidator(InputSetValidatorType.REGEX, "stable*");
    GcrArtifactConfig gcrArtifactConfig =
        GcrArtifactConfig.builder()
            .imagePath(ParameterField.createValueField("IMAGE"))
            .tagRegex(ParameterField.createExpressionField(true, "<+lastPublished.tag>", inputSetValidator, true))
            .registryHostname(ParameterField.createValueField("host"))
            .build();
    GcpConnectorDTO connectorDTO = GcpConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    GcrArtifactDelegateRequest amiArtifactDelegateRequest = ArtifactConfigToDelegateReqMapper.getGcrDelegateRequest(
        gcrArtifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(amiArtifactDelegateRequest.getGcpConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(amiArtifactDelegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(amiArtifactDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.GCR);
    assertThat(amiArtifactDelegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(amiArtifactDelegateRequest.getTagRegex()).isEqualTo("stable*");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetGARDelegateRequestRegexValidator() {
    InputSetValidator inputSetValidator = new InputSetValidator(InputSetValidatorType.REGEX, "stable*");
    GoogleArtifactRegistryConfig garArtifactInfo =
        GoogleArtifactRegistryConfig.builder()
            .region(ParameterField.createValueField("region"))
            .versionRegex(ParameterField.createExpressionField(true, "<+lastPublished.tag>", inputSetValidator, true))
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
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetECRDelegateRequestRegexValidator() {
    InputSetValidator inputSetValidator = new InputSetValidator(InputSetValidatorType.REGEX, "stable*");
    EcrArtifactConfig ecrArtifactConfig =
        EcrArtifactConfig.builder()
            .region(ParameterField.createValueField("region"))
            .tagRegex(ParameterField.createExpressionField(true, "<+lastPublished.tag>", inputSetValidator, true))
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
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetNexusDelegateRequestRegexValidator() {
    InputSetValidator inputSetValidator = new InputSetValidator(InputSetValidatorType.REGEX, "stable*");
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
            .tagRegex(ParameterField.createExpressionField(true, "<+lastPublished.tag>", inputSetValidator, true))
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
    assertThat(delegateRequest.getTagRegex()).isEqualTo("stable*");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetNexusDelegateRequestRegexValidatorNonDocker() {
    InputSetValidator inputSetValidator = new InputSetValidator(InputSetValidatorType.REGEX, "stable*");
    NexusRegistryNpmConfig nexusRegistryNpmConfig =
        NexusRegistryNpmConfig.builder().packageName(ParameterField.createValueField("package")).build();

    Nexus2RegistryArtifactConfig artifactConfig =
        Nexus2RegistryArtifactConfig.builder()
            .repository(ParameterField.createValueField("TEST_REPO"))
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.npm.name()))
            .nexusRegistryConfigSpec(nexusRegistryNpmConfig)
            .tagRegex(ParameterField.createExpressionField(true, "<+lastPublished.tag>", inputSetValidator, true))
            .build();
    NexusConnectorDTO connectorDTO = NexusConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    NexusArtifactDelegateRequest delegateRequest = ArtifactConfigToDelegateReqMapper.getNexus2ArtifactDelegateRequest(
        artifactConfig, connectorDTO, encryptedDataDetailList, "");
    nexusRegistryNpmConfig = (NexusRegistryNpmConfig) artifactConfig.getNexusRegistryConfigSpec();

    assertThat(delegateRequest.getNexusConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(delegateRequest.getRepositoryName()).isEqualTo(artifactConfig.getRepository().getValue());
    assertThat(delegateRequest.getRepositoryFormat()).isEqualTo(RepositoryFormat.npm.name());
    assertThat(delegateRequest.getArtifactRepositoryUrl()).isNull();
    assertThat(delegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(delegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.NEXUS2_REGISTRY);
    assertThat(delegateRequest.getTag()).isEqualTo("");
    assertThat(delegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(delegateRequest.getTagRegex()).isEqualTo("stable*");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetArtifactoryDelegateRequestRegexValidator() {
    InputSetValidator inputSetValidator = new InputSetValidator(InputSetValidatorType.REGEX, "stable*");
    ArtifactoryRegistryArtifactConfig artifactConfig =
        ArtifactoryRegistryArtifactConfig.builder()
            .repository(ParameterField.createValueField("TEST_REPO"))
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.docker.name()))
            .repositoryUrl(ParameterField.createValueField("harness-repo.jfrog.io"))
            .artifactPath(ParameterField.createValueField("IMAGE"))
            .tagRegex(ParameterField.createExpressionField(true, "<+lastPublished.tag>", inputSetValidator, true))
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
    assertThat(delegateRequest.getTagRegex()).isEqualTo("stable*");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetAcrDelegateRequestWithTagRegexValidator() {
    InputSetValidator inputSetValidator = new InputSetValidator(InputSetValidatorType.REGEX, "stable*");
    AcrArtifactConfig acrArtifactConfig =
        AcrArtifactConfig.builder()
            .subscriptionId(ParameterField.createValueField("123456-6543-3456-654321"))
            .registry(ParameterField.createValueField("AZURE_CR"))
            .repository(ParameterField.createValueField("library/testapp"))
            .tagRegex(ParameterField.createExpressionField(true, "<+lastPublished.tag>", inputSetValidator, true))
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
  }
}
