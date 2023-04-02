/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.mappers;

import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.PRAGYESH;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactoryRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleArtifactRegistryConfig;
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
import io.harness.cdng.artifact.outcome.GarArtifactOutcome;
import io.harness.cdng.artifact.outcome.NexusArtifactOutcome;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryGenericArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.azure.AcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.custom.CustomArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.gar.GarDelegateResponse;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;
import io.harness.exception.ArtifactServerException;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import software.wings.utils.RepositoryFormat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testToDockerArtifactOutcomeWithMatchingV1Digest() {
    ArtifactConfig artifactConfig = DockerHubArtifactConfig.builder()
                                        .connectorRef(ParameterField.createValueField("connector"))
                                        .imagePath(ParameterField.createValueField("IMAGE"))
                                        .digest(ParameterField.createValueField("V1_DIGEST"))
                                        .build();
    Map<String, String> metadata = new HashMap<>();
    metadata.put(ArtifactMetadataKeys.SHA, "V1_DIGEST");
    metadata.put(ArtifactMetadataKeys.SHAV2, "V2_DIGEST");
    ArtifactBuildDetailsNG buildDetails = ArtifactBuildDetailsNG.builder().metadata(metadata).build();
    ArtifactDelegateResponse artifactDelegateResponse =
        DockerArtifactDelegateResponse.builder().buildDetails(buildDetails).build();

    ArtifactOutcome artifactOutcome =
        ArtifactResponseToOutcomeMapper.toArtifactOutcome(artifactConfig, artifactDelegateResponse, true);

    assertThat(artifactOutcome).isNotNull();
    assertThat(artifactOutcome).isInstanceOf(DockerArtifactOutcome.class);
    assertThat(((DockerArtifactOutcome) artifactOutcome).getDigest()).isEqualTo("V1_DIGEST");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testToDockerArtifactOutcomeWithMatchingV2Digest() {
    ArtifactConfig artifactConfig = DockerHubArtifactConfig.builder()
                                        .connectorRef(ParameterField.createValueField("connector"))
                                        .imagePath(ParameterField.createValueField("IMAGE"))
                                        .digest(ParameterField.createValueField("V2_DIGEST"))
                                        .build();
    Map<String, String> metadata = new HashMap<>();
    metadata.put(ArtifactMetadataKeys.SHA, "V1_DIGEST");
    metadata.put(ArtifactMetadataKeys.SHAV2, "V2_DIGEST");
    ArtifactBuildDetailsNG buildDetails = ArtifactBuildDetailsNG.builder().metadata(metadata).build();
    ArtifactDelegateResponse artifactDelegateResponse =
        DockerArtifactDelegateResponse.builder().buildDetails(buildDetails).build();

    ArtifactOutcome artifactOutcome =
        ArtifactResponseToOutcomeMapper.toArtifactOutcome(artifactConfig, artifactDelegateResponse, true);

    assertThat(artifactOutcome).isNotNull();
    assertThat(artifactOutcome).isInstanceOf(DockerArtifactOutcome.class);
    assertThat(((DockerArtifactOutcome) artifactOutcome).getDigest()).isEqualTo("V2_DIGEST");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testToDockerArtifactOutcomeWithUnmatchedDigest() {
    ArtifactConfig artifactConfig = DockerHubArtifactConfig.builder()
                                        .connectorRef(ParameterField.createValueField("connector"))
                                        .imagePath(ParameterField.createValueField("IMAGE"))
                                        .digest(ParameterField.createValueField("WRONG_DIGEST"))
                                        .build();
    Map<String, String> metadata = new HashMap<>();
    metadata.put(ArtifactMetadataKeys.SHA, "V1_DIGEST");
    metadata.put(ArtifactMetadataKeys.SHAV2, "V2_DIGEST");
    ArtifactBuildDetailsNG buildDetails = ArtifactBuildDetailsNG.builder().metadata(metadata).build();
    ArtifactDelegateResponse artifactDelegateResponse =
        DockerArtifactDelegateResponse.builder().buildDetails(buildDetails).build();

    assertThatThrownBy(
        () -> ArtifactResponseToOutcomeMapper.toArtifactOutcome(artifactConfig, artifactDelegateResponse, true))
        .isInstanceOf(ArtifactServerException.class)
        .hasMessage(
            "Artifact image SHA256 validation failed: image sha256 digest mismatch.\n Requested digest: WRONG_DIGEST\nAvailable digests:\nV1_DIGEST (V1)\nV2_DIGEST (V2)");
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
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testToArtifactoryGenericArtifactPathOutcome() {
    ArtifactoryRegistryArtifactConfig artifactConfig =
        ArtifactoryRegistryArtifactConfig.builder()
            .connectorRef(ParameterField.createValueField("connector"))
            .repository(ParameterField.createValueField("REPO_NAME"))
            .artifactPath(ParameterField.createValueField("path"))
            .artifactDirectory(ParameterField.createValueField("dir"))
            .repositoryFormat(ParameterField.createValueField(RepositoryFormat.generic.name()))
            .build();
    assertArtifactoryGenericArtifactPathOutcome(artifactConfig, "dir/path");

    artifactConfig.setArtifactPathFilter(ParameterField.createValueField("abc"));
    assertArtifactoryGenericArtifactPathOutcome(artifactConfig, "path");
  }

  private void assertArtifactoryGenericArtifactPathOutcome(
      ArtifactoryRegistryArtifactConfig artifactConfig, String expectedArtifactPath) {
    ArtifactDelegateResponse artifactDelegateResponse = ArtifactoryGenericArtifactDelegateResponse.builder().build();

    ArtifactOutcome artifactOutcome =
        ArtifactResponseToOutcomeMapper.toArtifactOutcome(artifactConfig, artifactDelegateResponse, false);

    assertThat(artifactOutcome).isNotNull();
    assertThat(artifactOutcome).isInstanceOf(ArtifactoryGenericArtifactOutcome.class);
    assertThat(artifactOutcome.getArtifactType()).isEqualTo(ArtifactSourceType.ARTIFACTORY_REGISTRY.getDisplayName());
    ArtifactoryGenericArtifactOutcome artifactoryGenericArtifactOutcome =
        (ArtifactoryGenericArtifactOutcome) artifactOutcome;
    assertThat(artifactoryGenericArtifactOutcome.getArtifactPath()).isEqualTo(expectedArtifactPath);
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

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getGarArtifactOutcomeTest() {
    final String pkg = "pkg";
    final String connectorRef = "connectorRef";
    final String project = "project";
    final String region = "region";
    final String repoName = "repoName";
    final String versionRegex = "versionRegex";
    final String identifier = "identifier";
    final String type = "type";

    GoogleArtifactRegistryConfig googleArtifactRegistryConfig =
        GoogleArtifactRegistryConfig.builder()
            .pkg(ParameterField.createValueField(pkg))
            .connectorRef(ParameterField.createValueField(connectorRef))
            .identifier(identifier)
            .project(ParameterField.createValueField(project))
            .region(ParameterField.createValueField(region))
            .repositoryName(ParameterField.createValueField(repoName))
            .versionRegex(ParameterField.createValueField(versionRegex))
            .isPrimaryArtifact(true)
            .googleArtifactRegistryType(ParameterField.<String>builder().value(type).build())
            .build();
    Map<String, String> metaData = new HashMap<>();
    metaData.put(ArtifactMetadataKeys.REGISTRY_HOSTNAME, ArtifactMetadataKeys.REGISTRY_HOSTNAME);
    metaData.put(ArtifactMetadataKeys.IMAGE, ArtifactMetadataKeys.IMAGE);
    Map<String, String> label = new HashMap<>();
    label.put("1", "2");
    label.put("3", "4");
    GarDelegateResponse garDelegateResponse =
        GarDelegateResponse.builder()
            .version(versionRegex)
            .label(label)
            .buildDetails(ArtifactBuildDetailsNG.builder().metadata(metaData).build())
            .build();
    GarArtifactOutcome garArtifactOutcome = (GarArtifactOutcome) ArtifactResponseToOutcomeMapper.toArtifactOutcome(
        googleArtifactRegistryConfig, garDelegateResponse, true);
    assertThat(garArtifactOutcome.getVersion()).isEqualTo(versionRegex);
    assertThat(garArtifactOutcome.getRegistryHostname()).isEqualTo(ArtifactMetadataKeys.REGISTRY_HOSTNAME);
    assertThat(garArtifactOutcome.getConnectorRef()).isEqualTo(connectorRef);
    assertThat(garArtifactOutcome.getPkg()).isEqualTo(pkg);
    assertThat(garArtifactOutcome.getProject()).isEqualTo(project);
    assertThat(garArtifactOutcome.getRegion()).isEqualTo(region);
    assertThat(garArtifactOutcome.getRepositoryName()).isEqualTo(repoName);
    assertThat(garArtifactOutcome.getVersionRegex()).isEqualTo(versionRegex);
    assertThat(garArtifactOutcome.getType()).isEqualTo(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY.getDisplayName());
    assertThat(garArtifactOutcome.getIdentifier()).isEqualTo(identifier);
    assertThat(garArtifactOutcome.isPrimaryArtifact()).isEqualTo(true);
    assertThat(garArtifactOutcome.getImage()).isEqualTo(ArtifactMetadataKeys.IMAGE);
    assertThat(garArtifactOutcome.getMetadata()).isEqualTo(metaData);
    assertThat(garArtifactOutcome.getRepositoryType()).isEqualTo(type);
    assertThat(garArtifactOutcome.getLabel()).isEqualTo(label);
  }
}
