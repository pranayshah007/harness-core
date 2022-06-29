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

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactoryRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.NexusRegistryArtifactConfig;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryGenericArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.azure.AcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateRequest;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.utils.RepositoryFormat;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class ArtifactConfigToDelegateReqMapperTest extends CategoryTest {
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
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetNexusDelegateRequest() {
    NexusRegistryArtifactConfig artifactConfig =
        NexusRegistryArtifactConfig.builder()
            .repository(ParameterField.createValueField("TEST_REPO"))
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.docker.name()))
            .repositoryPort(ParameterField.createValueField("8181"))
            .artifactPath(ParameterField.createValueField("IMAGE"))
            .build();
    NexusConnectorDTO connectorDTO = NexusConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetailList = Collections.emptyList();

    NexusArtifactDelegateRequest delegateRequest = ArtifactConfigToDelegateReqMapper.getNexusArtifactDelegateRequest(
        artifactConfig, connectorDTO, encryptedDataDetailList, "");

    assertThat(delegateRequest.getNexusConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(delegateRequest.getRepositoryName()).isEqualTo(artifactConfig.getRepository().getValue());
    assertThat(delegateRequest.getRepositoryFormat()).isEqualTo(RepositoryFormat.docker.name());
    assertThat(delegateRequest.getRepositoryPort()).isEqualTo(artifactConfig.getRepositoryPort().getValue());
    assertThat(delegateRequest.getArtifactRepositoryUrl()).isNull();
    assertThat(delegateRequest.getEncryptedDataDetails()).isEqualTo(encryptedDataDetailList);
    assertThat(delegateRequest.getArtifactPath()).isEqualTo(artifactConfig.getArtifactPath().getValue());
    assertThat(delegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.NEXUS3_REGISTRY);
    assertThat(delegateRequest.getTag()).isEqualTo("");
    assertThat(delegateRequest.getConnectorRef()).isEqualTo("");
    assertThat(delegateRequest.getTagRegex()).isEqualTo("\\*");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetNexusDelegateRequestWithoutPort() {
    NexusRegistryArtifactConfig artifactConfig =
        NexusRegistryArtifactConfig.builder()
            .repository(ParameterField.createValueField("TEST_REPO"))
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.docker.name()))
            .artifactPath(ParameterField.createValueField("IMAGE"))
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
    assertThat(delegateRequest.getArtifactPath()).isEqualTo(artifactConfig.getArtifactPath().getValue());
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
}
