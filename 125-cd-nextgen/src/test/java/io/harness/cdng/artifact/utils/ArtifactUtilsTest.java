/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.utils;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.MLUKIC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactoryRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.NexusRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.cdng.artifact.bean.yaml.SidecarArtifact;
import io.harness.cdng.artifact.bean.yaml.SidecarArtifactWrapper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactoryArtifactOutcome;
import io.harness.cdng.artifact.outcome.DockerArtifactOutcome;
import io.harness.cdng.artifact.outcome.NexusArtifactOutcome;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class ArtifactUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldAppendIfNecessary() {
    StringBuilder keyBuilder = new StringBuilder("KEY_BUILDER");
    String value = "VALUE";
    String expectedString = "KEY_BUILDER:VALUE";
    ArtifactUtils.appendIfNecessary(keyBuilder, value);
    assertThat(keyBuilder.toString().equals(expectedString)).isTrue();

    keyBuilder = new StringBuilder("KEY_BUILDER");
    value = "";
    expectedString = "KEY_BUILDER";
    ArtifactUtils.appendIfNecessary(keyBuilder, value);
    assertThat(expectedString.equals(keyBuilder.toString())).isTrue();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldThrowExceptionInAppend() {
    String value = "ABC";
    StringBuilder keyBuilder = null;
    String errorMessage = "Key string builder cannot be null";
    assertThatThrownBy(() -> ArtifactUtils.appendIfNecessary(keyBuilder, value))
        .isInstanceOf(InvalidRequestException.class)
        .matches(ex -> ex.getMessage().equals(errorMessage));
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldGenerateSameUniqueHashForSameList() {
    List<String> firstList = Arrays.asList("Value1", "Value2", "AnotherValue");
    List<String> secondList = Arrays.asList("Value2", "AnotherValue", "Value1");
    List<String> thirdList = Arrays.asList("Value2", null, "AnotherValue", "", "Value1");

    String firstListHash = ArtifactUtils.generateUniqueHashFromStringList(firstList);
    String secondListHash = ArtifactUtils.generateUniqueHashFromStringList(secondList);
    String thirdListHash = ArtifactUtils.generateUniqueHashFromStringList(thirdList);

    assertThat(firstListHash.equals(secondListHash)).isTrue();
    assertThat(thirdListHash.equals(secondListHash)).isTrue();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDockerHub_IsPrimaryArtifact() {
    DockerHubArtifactConfig config = DockerHubArtifactConfig.builder().primaryArtifact(true).build();
    boolean primaryArtifact = config.isPrimaryArtifact();
    assertThat(primaryArtifact).isTrue();
    config = DockerHubArtifactConfig.builder().primaryArtifact(false).build();
    primaryArtifact = config.isPrimaryArtifact();
    assertThat(primaryArtifact).isFalse();

    ArtifactOutcome artifactOutcome = DockerArtifactOutcome.builder().primaryArtifact(true).build();
    primaryArtifact = artifactOutcome.isPrimaryArtifact();
    assertThat(primaryArtifact).isTrue();
    artifactOutcome = DockerArtifactOutcome.builder().primaryArtifact(false).build();
    primaryArtifact = artifactOutcome.isPrimaryArtifact();
    assertThat(primaryArtifact).isFalse();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testNexusRegistry_IsPrimaryArtifact() {
    NexusRegistryArtifactConfig config = NexusRegistryArtifactConfig.builder().primaryArtifact(true).build();
    boolean primaryArtifact = config.isPrimaryArtifact();
    assertThat(primaryArtifact).isTrue();
    config = NexusRegistryArtifactConfig.builder().primaryArtifact(false).build();
    primaryArtifact = config.isPrimaryArtifact();
    assertThat(primaryArtifact).isFalse();

    ArtifactOutcome artifactOutcome = NexusArtifactOutcome.builder().primaryArtifact(true).build();
    primaryArtifact = artifactOutcome.isPrimaryArtifact();
    assertThat(primaryArtifact).isTrue();
    artifactOutcome = NexusArtifactOutcome.builder().primaryArtifact(false).build();
    primaryArtifact = artifactOutcome.isPrimaryArtifact();
    assertThat(primaryArtifact).isFalse();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testArtifactoryRegistry_IsPrimaryArtifact() {
    ArtifactoryRegistryArtifactConfig config =
        ArtifactoryRegistryArtifactConfig.builder().primaryArtifact(true).build();
    boolean primaryArtifact = config.isPrimaryArtifact();
    assertThat(primaryArtifact).isTrue();
    config = ArtifactoryRegistryArtifactConfig.builder().primaryArtifact(false).build();
    primaryArtifact = config.isPrimaryArtifact();
    assertThat(primaryArtifact).isFalse();

    ArtifactOutcome artifactOutcome = ArtifactoryArtifactOutcome.builder().primaryArtifact(true).build();
    primaryArtifact = artifactOutcome.isPrimaryArtifact();
    assertThat(primaryArtifact).isTrue();
    artifactOutcome = ArtifactoryArtifactOutcome.builder().primaryArtifact(false).build();
    primaryArtifact = artifactOutcome.isPrimaryArtifact();
    assertThat(primaryArtifact).isFalse();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDockerHub_GetArtifactKey() {
    DockerHubArtifactConfig artifactConfig =
        DockerHubArtifactConfig.builder().primaryArtifact(true).identifier("ARTIFACT1").build();
    String artifactKey = ArtifactUtils.getArtifactKey(artifactConfig);
    assertThat(artifactKey).isEqualTo("ARTIFACT1");
    artifactConfig = DockerHubArtifactConfig.builder().primaryArtifact(false).identifier("ARTIFACT1").build();
    artifactKey = ArtifactUtils.getArtifactKey(artifactConfig);
    assertThat(artifactKey).isEqualTo("sidecars.ARTIFACT1");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testNexusRegistry_GetArtifactKey() {
    NexusRegistryArtifactConfig artifactConfig =
        NexusRegistryArtifactConfig.builder().primaryArtifact(true).identifier("ARTIFACT1").build();
    String artifactKey = ArtifactUtils.getArtifactKey(artifactConfig);
    assertThat(artifactKey).isEqualTo("ARTIFACT1");
    artifactConfig = NexusRegistryArtifactConfig.builder().primaryArtifact(false).identifier("ARTIFACT1").build();
    artifactKey = ArtifactUtils.getArtifactKey(artifactConfig);
    assertThat(artifactKey).isEqualTo("sidecars.ARTIFACT1");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testArtifactoryRegistry_GetArtifactKey() {
    ArtifactoryRegistryArtifactConfig artifactConfig =
        ArtifactoryRegistryArtifactConfig.builder().primaryArtifact(true).identifier("ARTIFACT1").build();
    String artifactKey = ArtifactUtils.getArtifactKey(artifactConfig);
    assertThat(artifactKey).isEqualTo("ARTIFACT1");
    artifactConfig = ArtifactoryRegistryArtifactConfig.builder().primaryArtifact(false).identifier("ARTIFACT1").build();
    artifactKey = ArtifactUtils.getArtifactKey(artifactConfig);
    assertThat(artifactKey).isEqualTo("sidecars.ARTIFACT1");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDockerHub_ConvertArtifactListConfig() {
    DockerHubArtifactConfig primaryArtifact =
        DockerHubArtifactConfig.builder().primaryArtifact(true).identifier("ARTIFACT1").build();
    DockerHubArtifactConfig sidecarArtifact =
        DockerHubArtifactConfig.builder().primaryArtifact(false).identifier("ARTIFACT2").build();
    ArtifactListConfig artifactListConfig =
        ArtifactListConfig.builder()
            .primary(PrimaryArtifact.builder().spec(primaryArtifact).build())
            .sidecar(SidecarArtifactWrapper.builder()
                         .sidecar(SidecarArtifact.builder().spec(sidecarArtifact).build())
                         .build())
            .build();
    List<ArtifactConfig> artifactsList = ArtifactUtils.convertArtifactListIntoArtifacts(artifactListConfig, null);
    assertThat(artifactsList).containsOnly(primaryArtifact, sidecarArtifact);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testNexusRegistry_ConvertArtifactListConfig() {
    NexusRegistryArtifactConfig primaryArtifact =
        NexusRegistryArtifactConfig.builder().primaryArtifact(true).identifier("ARTIFACT1").build();
    NexusRegistryArtifactConfig sidecarArtifact =
        NexusRegistryArtifactConfig.builder().primaryArtifact(false).identifier("ARTIFACT2").build();
    ArtifactListConfig artifactListConfig =
        ArtifactListConfig.builder()
            .primary(PrimaryArtifact.builder().spec(primaryArtifact).build())
            .sidecar(SidecarArtifactWrapper.builder()
                         .sidecar(SidecarArtifact.builder().spec(sidecarArtifact).build())
                         .build())
            .build();
    List<ArtifactConfig> artifactsList = ArtifactUtils.convertArtifactListIntoArtifacts(artifactListConfig, null);
    assertThat(artifactsList).containsOnly(primaryArtifact, sidecarArtifact);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testArtifactoryRegistry_ConvertArtifactListConfig() {
    ArtifactoryRegistryArtifactConfig primaryArtifact =
        ArtifactoryRegistryArtifactConfig.builder().primaryArtifact(true).identifier("ARTIFACT1").build();
    ArtifactoryRegistryArtifactConfig sidecarArtifact =
        ArtifactoryRegistryArtifactConfig.builder().primaryArtifact(false).identifier("ARTIFACT2").build();
    ArtifactListConfig artifactListConfig =
        ArtifactListConfig.builder()
            .primary(PrimaryArtifact.builder().spec(primaryArtifact).build())
            .sidecar(SidecarArtifactWrapper.builder()
                         .sidecar(SidecarArtifact.builder().spec(sidecarArtifact).build())
                         .build())
            .build();
    List<ArtifactConfig> artifactsList = ArtifactUtils.convertArtifactListIntoArtifacts(artifactListConfig, null);
    assertThat(artifactsList).containsOnly(primaryArtifact, sidecarArtifact);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testAcr_ConvertArtifactListConfig() {
    AcrArtifactConfig primaryArtifact =
        AcrArtifactConfig.builder().primaryArtifact(true).identifier("ARTIFACT1").build();
    AcrArtifactConfig sidecarArtifact =
        AcrArtifactConfig.builder().primaryArtifact(false).identifier("ARTIFACT2").build();
    ArtifactListConfig artifactListConfig =
        ArtifactListConfig.builder()
            .primary(PrimaryArtifact.builder().spec(primaryArtifact).build())
            .sidecar(SidecarArtifactWrapper.builder()
                         .sidecar(SidecarArtifact.builder().spec(sidecarArtifact).build())
                         .build())
            .build();
    List<ArtifactConfig> artifactsList = ArtifactUtils.convertArtifactListIntoArtifacts(artifactListConfig, null);
    assertThat(artifactsList).containsOnly(primaryArtifact, sidecarArtifact);
  }
}
