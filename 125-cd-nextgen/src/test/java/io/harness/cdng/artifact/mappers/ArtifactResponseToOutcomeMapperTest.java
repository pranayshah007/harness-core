/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.mappers;

import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactoryRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.NexusRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomArtifactScriptInfo;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomArtifactScriptSourceWrapper;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomArtifactScripts;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomScriptInlineSource;
import io.harness.cdng.artifact.bean.yaml.customartifact.FetchAllArtifacts;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryDockerConfig;
import io.harness.cdng.artifact.outcome.AcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactoryArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactoryGenericArtifactOutcome;
import io.harness.cdng.artifact.outcome.CustomArtifactOutcome;
import io.harness.cdng.artifact.outcome.DockerArtifactOutcome;
import io.harness.cdng.artifact.outcome.NexusArtifactOutcome;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryGenericArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.azure.AcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.custom.CustomArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import software.wings.utils.RepositoryFormat;

import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ArtifactResponseToOutcomeMapperTest extends CategoryTest {
  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testToDockerArtifactOutcome() {
    ArtifactConfig artifactConfig = DockerHubArtifactConfig.builder()
                                        .connectorRef(ParameterField.createValueField("connector"))
                                        .imagePath(ParameterField.createValueField("IMAGE"))
                                        .build();
    ArtifactDelegateResponse artifactDelegateResponse = DockerArtifactDelegateResponse.builder().build();

    ArtifactOutcome artifactOutcome =
        ArtifactResponseToOutcomeMapper.toArtifactOutcome(artifactConfig, artifactDelegateResponse, true);

    assertThat(artifactOutcome).isNotNull();
    assertThat(artifactOutcome).isInstanceOf(DockerArtifactOutcome.class);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testToNexusArtifactOutcome() {
    NexusRegistryDockerConfig nexusRegistryDockerConfig =
        NexusRegistryDockerConfig.builder()
            .artifactPath(ParameterField.createValueField("IMAGE"))
            .repositoryPort(ParameterField.createValueField("TEST_REPO"))
            .build();
    ArtifactConfig artifactConfig =
        NexusRegistryArtifactConfig.builder()
            .connectorRef(ParameterField.createValueField("connector"))
            .repository(ParameterField.createValueField("REPO_NAME"))
            .nexusRegistryConfigSpec(nexusRegistryDockerConfig)
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.docker.name()))
            .build();
    ArtifactDelegateResponse artifactDelegateResponse = NexusArtifactDelegateResponse.builder().build();

    ArtifactOutcome artifactOutcome =
        ArtifactResponseToOutcomeMapper.toArtifactOutcome(artifactConfig, artifactDelegateResponse, true);

    assertThat(artifactOutcome).isNotNull();
    assertThat(artifactOutcome).isInstanceOf(NexusArtifactOutcome.class);
    assertThat(artifactOutcome.getArtifactType()).isEqualTo(ArtifactSourceType.NEXUS3_REGISTRY.getDisplayName());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testToNexusArtifactOutcomeMaven() {
    NexusRegistryDockerConfig nexusRegistryDockerConfig =
        NexusRegistryDockerConfig.builder()
            .artifactPath(ParameterField.createValueField("IMAGE"))
            .repositoryPort(ParameterField.createValueField("TEST_REPO"))
            .build();
    ArtifactConfig artifactConfig =
        NexusRegistryArtifactConfig.builder()
            .connectorRef(ParameterField.createValueField("connector"))
            .repository(ParameterField.createValueField("REPO_NAME"))
            .nexusRegistryConfigSpec(nexusRegistryDockerConfig)
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.maven.name()))
            .build();
    ArtifactDelegateResponse artifactDelegateResponse =
        NexusArtifactDelegateResponse.builder().artifactPath("test").build();

    ArtifactOutcome artifactOutcome =
        ArtifactResponseToOutcomeMapper.toArtifactOutcome(artifactConfig, artifactDelegateResponse, true);

    assertThat(artifactOutcome).isInstanceOf(NexusArtifactOutcome.class);
    assertThat(artifactOutcome.getArtifactType()).isEqualTo(ArtifactSourceType.NEXUS3_REGISTRY.getDisplayName());
    assertThat(((NexusArtifactOutcome) artifactOutcome).getArtifactPath()).isEqualTo("test");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testToNexusArtifactOutcomeNPM() {
    NexusRegistryDockerConfig nexusRegistryDockerConfig =
        NexusRegistryDockerConfig.builder()
            .artifactPath(ParameterField.createValueField("IMAGE"))
            .repositoryPort(ParameterField.createValueField("TEST_REPO"))
            .build();
    ArtifactConfig artifactConfig = NexusRegistryArtifactConfig.builder()
                                        .connectorRef(ParameterField.createValueField("connector"))
                                        .repository(ParameterField.createValueField("REPO_NAME"))
                                        .nexusRegistryConfigSpec(nexusRegistryDockerConfig)
                                        .repositoryFormat(ParameterField.createValueField(RepositoryFormat.npm.name()))
                                        .build();
    ArtifactDelegateResponse artifactDelegateResponse =
        NexusArtifactDelegateResponse.builder()
            .buildDetails(
                ArtifactBuildDetailsNG.builder().metadata(Collections.singletonMap("package", "test")).build())
            .build();

    ArtifactOutcome artifactOutcome =
        ArtifactResponseToOutcomeMapper.toArtifactOutcome(artifactConfig, artifactDelegateResponse, true);

    assertThat(artifactOutcome).isInstanceOf(NexusArtifactOutcome.class);
    assertThat(artifactOutcome.getArtifactType()).isEqualTo(ArtifactSourceType.NEXUS3_REGISTRY.getDisplayName());
    assertThat(((NexusArtifactOutcome) artifactOutcome).getArtifactPath()).isEqualTo("test");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testToArtifactoryArtifactOutcome() {
    ArtifactConfig artifactConfig =
        ArtifactoryRegistryArtifactConfig.builder()
            .connectorRef(ParameterField.createValueField("connector"))
            .repository(ParameterField.createValueField("REPO_NAME"))
            .artifactPath(ParameterField.createValueField("IMAGE"))
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.docker.name()))
            .build();
    ArtifactDelegateResponse artifactDelegateResponse = ArtifactoryArtifactDelegateResponse.builder().build();

    ArtifactOutcome artifactOutcome =
        ArtifactResponseToOutcomeMapper.toArtifactOutcome(artifactConfig, artifactDelegateResponse, true);

    assertThat(artifactOutcome).isNotNull();
    assertThat(artifactOutcome).isInstanceOf(ArtifactoryArtifactOutcome.class);
    assertThat(artifactOutcome.getArtifactType()).isEqualTo(ArtifactSourceType.ARTIFACTORY_REGISTRY.getDisplayName());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testToCustomArtifactOutcomeFixedBuild() {
    ArtifactConfig artifactConfig = CustomArtifactConfig.builder()
                                        .identifier("test")
                                        .primaryArtifact(true)
                                        .version(ParameterField.createValueField("build-x"))
                                        .build();
    assertCustomArtifactOutcome(artifactConfig);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testToArtifactoryGenericArtifactOutcome() {
    ArtifactConfig artifactConfig =
        ArtifactoryRegistryArtifactConfig.builder()
            .connectorRef(ParameterField.createValueField("connector"))
            .repository(ParameterField.createValueField("REPO_NAME"))
            .artifactPath(ParameterField.createValueField("IMAGE"))
            .artifactDirectory(ParameterField.createValueField("IMAGE1"))
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.generic.name()))
            .build();
    ArtifactBuildDetailsNG artifactBuildDetailsNG =
        ArtifactBuildDetailsNG.builder().metadata(Collections.singletonMap("url", "url")).build();
    ArtifactDelegateResponse artifactDelegateResponse = ArtifactoryGenericArtifactDelegateResponse.builder()
                                                            .artifactPath("IMAGE")
                                                            .buildDetails(artifactBuildDetailsNG)
                                                            .build();

    ArtifactOutcome artifactOutcome =
        ArtifactResponseToOutcomeMapper.toArtifactOutcome(artifactConfig, artifactDelegateResponse, true);

    assertThat(artifactOutcome).isNotNull();
    assertThat(artifactOutcome).isInstanceOf(ArtifactoryGenericArtifactOutcome.class);
    assertThat(artifactOutcome.getArtifactType()).isEqualTo(ArtifactSourceType.ARTIFACTORY_REGISTRY.getDisplayName());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testToAcrArtifactOutcome() {
    ArtifactConfig artifactConfig = AcrArtifactConfig.builder()
                                        .connectorRef(ParameterField.createValueField("connector"))
                                        .subscriptionId(ParameterField.createValueField("123456-6543-3456-654321"))
                                        .registry(ParameterField.createValueField("AZURE_REGISTRY"))
                                        .repository(ParameterField.createValueField("REPO_NAME"))
                                        .tag(ParameterField.createValueField("TAG"))
                                        .build();
    ArtifactDelegateResponse artifactDelegateResponse = AcrArtifactDelegateResponse.builder().build();

    ArtifactOutcome artifactOutcome =
        ArtifactResponseToOutcomeMapper.toArtifactOutcome(artifactConfig, artifactDelegateResponse, true);

    assertThat(artifactOutcome).isNotNull();
    assertThat(artifactOutcome).isInstanceOf(AcrArtifactOutcome.class);
    assertThat(artifactOutcome.getArtifactType()).isEqualTo(ArtifactSourceType.ACR.getDisplayName());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testToValidateArtifactoryArtifactPath() {
    ArtifactConfig artifactConfig =
        ArtifactoryRegistryArtifactConfig.builder()
            .connectorRef(ParameterField.createValueField("connector"))
            .repository(ParameterField.createValueField("REPO_NAME"))
            .artifactPath(ParameterField.createValueField("IMAGE"))
            .artifactDirectory(ParameterField.createValueField("serverless"))
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.generic.name()))
            .build();
    ArtifactBuildDetailsNG buildDetails =
        ArtifactBuildDetailsNG.builder().metadata(Collections.singletonMap("URL", "URL")).build();
    ArtifactDelegateResponse artifactDelegateResponse = ArtifactoryGenericArtifactDelegateResponse.builder()
                                                            .buildDetails(buildDetails)
                                                            .artifactPath("serverless/IMAGE")
                                                            .build();

    ArtifactOutcome artifactOutcome =
        ArtifactResponseToOutcomeMapper.toArtifactOutcome(artifactConfig, artifactDelegateResponse, true);
    ArtifactoryGenericArtifactOutcome artifactoryArtifactOutcome = (ArtifactoryGenericArtifactOutcome) artifactOutcome;

    assertThat(artifactOutcome).isNotNull();
    assertThat(artifactOutcome.getArtifactType()).isEqualTo(ArtifactSourceType.ARTIFACTORY_REGISTRY.getDisplayName());
    assertThat(artifactoryArtifactOutcome.getArtifactPath()).isEqualTo("serverless/IMAGE");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testToCustomArtifactOutcomeFixedBuildWithNulls() {
    ArtifactConfig artifactConfig = CustomArtifactConfig.builder()
                                        .identifier("test")
                                        .primaryArtifact(true)
                                        .scripts(CustomArtifactScripts.builder().build())
                                        .version(ParameterField.createValueField("build-x"))
                                        .build();
    assertCustomArtifactOutcome(artifactConfig);

    artifactConfig = CustomArtifactConfig.builder()
                         .identifier("test")
                         .primaryArtifact(true)
                         .scripts(CustomArtifactScripts.builder().fetchAllArtifacts(null).build())
                         .version(ParameterField.createValueField("build-x"))
                         .build();
    assertCustomArtifactOutcome(artifactConfig);

    artifactConfig =
        CustomArtifactConfig.builder()
            .identifier("test")
            .primaryArtifact(true)
            .scripts(CustomArtifactScripts.builder().fetchAllArtifacts(FetchAllArtifacts.builder().build()).build())
            .version(ParameterField.createValueField("build-x"))
            .build();
    assertCustomArtifactOutcome(artifactConfig);

    artifactConfig =
        CustomArtifactConfig.builder()
            .identifier("test")
            .primaryArtifact(true)
            .scripts(CustomArtifactScripts.builder()
                         .fetchAllArtifacts(FetchAllArtifacts.builder()
                                                .shellScriptBaseStepInfo(CustomArtifactScriptInfo.builder().build())
                                                .build())
                         .build())
            .version(ParameterField.createValueField("build-x"))
            .build();
    assertCustomArtifactOutcome(artifactConfig);

    artifactConfig =
        CustomArtifactConfig.builder()
            .identifier("test")
            .primaryArtifact(true)
            .scripts(CustomArtifactScripts.builder()
                         .fetchAllArtifacts(FetchAllArtifacts.builder()
                                                .shellScriptBaseStepInfo(
                                                    CustomArtifactScriptInfo.builder()
                                                        .source(CustomArtifactScriptSourceWrapper.builder().build())
                                                        .build())
                                                .build())
                         .build())
            .version(ParameterField.createValueField("build-x"))
            .build();
    assertCustomArtifactOutcome(artifactConfig);

    artifactConfig =
        CustomArtifactConfig.builder()
            .identifier("test")
            .primaryArtifact(true)
            .scripts(CustomArtifactScripts.builder()
                         .fetchAllArtifacts(FetchAllArtifacts.builder()
                                                .shellScriptBaseStepInfo(
                                                    CustomArtifactScriptInfo.builder()
                                                        .source(CustomArtifactScriptSourceWrapper.builder()
                                                                    .spec(CustomScriptInlineSource.builder().build())
                                                                    .build())
                                                        .build())
                                                .build())
                         .build())
            .version(ParameterField.createValueField("build-x"))
            .build();
    assertCustomArtifactOutcome(artifactConfig);

    artifactConfig =
        CustomArtifactConfig.builder()
            .identifier("test")
            .primaryArtifact(true)
            .scripts(CustomArtifactScripts.builder()
                         .fetchAllArtifacts(
                             FetchAllArtifacts.builder()
                                 .shellScriptBaseStepInfo(
                                     CustomArtifactScriptInfo.builder()
                                         .source(CustomArtifactScriptSourceWrapper.builder()
                                                     .spec(CustomScriptInlineSource.builder()
                                                               .script(ParameterField.createValueField("       "))
                                                               .build())
                                                     .build())
                                         .build())
                                 .build())
                         .build())
            .version(ParameterField.createValueField("build-x"))
            .build();
    assertCustomArtifactOutcome(artifactConfig);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testCustomArtifactOutcome() {
    ArtifactConfig artifactConfig =
        CustomArtifactConfig.builder()
            .identifier("test")
            .primaryArtifact(true)
            .scripts(CustomArtifactScripts.builder()
                         .fetchAllArtifacts(
                             FetchAllArtifacts.builder()
                                 .shellScriptBaseStepInfo(
                                     CustomArtifactScriptInfo.builder()
                                         .source(CustomArtifactScriptSourceWrapper.builder()
                                                     .spec(CustomScriptInlineSource.builder()
                                                               .script(ParameterField.createValueField("echo"))
                                                               .build())
                                                     .build())
                                         .build())
                                 .build())
                         .build())
            .version(ParameterField.createValueField("build-x"))
            .build();
    CustomArtifactDelegateResponse customArtifactDelegateResponse =
        CustomArtifactDelegateResponse.builder()
            .buildDetails(ArtifactBuildDetailsNG.builder().uiDisplayName("BuildName").build())
            .build();
    ArtifactOutcome artifactOutcome =
        ArtifactResponseToOutcomeMapper.toArtifactOutcome(artifactConfig, customArtifactDelegateResponse, true);
    assertThat(artifactOutcome).isNotNull();
    assertThat(artifactOutcome).isInstanceOf(CustomArtifactOutcome.class);
    assertThat(artifactOutcome.getArtifactType()).isEqualTo(ArtifactSourceType.CUSTOM_ARTIFACT.getDisplayName());
    assertThat(((CustomArtifactOutcome) artifactOutcome).getVersion()).isEqualTo("build-x");
    assertThat(((CustomArtifactOutcome) artifactOutcome).getDisplayName()).isEqualTo("BuildName");
    assertThat(((CustomArtifactOutcome) artifactOutcome).getImage()).isEqualTo("build-x");
  }

  private void assertCustomArtifactOutcome(ArtifactConfig artifactConfig) {
    ArtifactOutcome artifactOutcome = ArtifactResponseToOutcomeMapper.toArtifactOutcome(artifactConfig, null, false);
    assertThat(artifactOutcome).isNotNull();
    assertThat(artifactOutcome).isInstanceOf(CustomArtifactOutcome.class);
    assertThat(artifactOutcome.getArtifactType()).isEqualTo(ArtifactSourceType.CUSTOM_ARTIFACT.getDisplayName());
    assertThat(artifactOutcome.getIdentifier()).isEqualTo("test");
    assertThat(artifactOutcome.isPrimaryArtifact()).isTrue();
    assertThat(((CustomArtifactOutcome) artifactOutcome).getVersion()).isEqualTo("build-x");
  }
}
