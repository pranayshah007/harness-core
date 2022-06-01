/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.mappers;

import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactoryRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.NexusRegistryArtifactConfig;
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
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import software.wings.utils.RepositoryFormat;

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
    ArtifactConfig artifactConfig =
        NexusRegistryArtifactConfig.builder()
            .connectorRef(ParameterField.createValueField("connector"))
            .repository(ParameterField.createValueField("REPO_NAME"))
            .artifactPath(ParameterField.createValueField("IMAGE"))
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

    ArtifactOutcome artifactOutcome = ArtifactResponseToOutcomeMapper.toArtifactOutcome(artifactConfig, null, false);
    assertThat(artifactOutcome).isNotNull();
    assertThat(artifactOutcome).isInstanceOf(CustomArtifactOutcome.class);
    assertThat(artifactOutcome.getArtifactType()).isEqualTo(ArtifactSourceType.CUSTOM_ARTIFACT.getDisplayName());
    assertThat(artifactOutcome.getIdentifier()).isEqualTo("test");
    assertThat(artifactOutcome.isPrimaryArtifact()).isTrue();
    assertThat(((CustomArtifactOutcome) artifactOutcome).getVersion()).isEqualTo("build-x");
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
    ArtifactDelegateResponse artifactDelegateResponse =
        ArtifactoryGenericArtifactDelegateResponse.builder().artifactPath("IMAGE").build();

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
}
