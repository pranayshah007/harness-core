/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.utils;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.PRAGYESH;
import static io.harness.rule.OwnerRule.SARTHAK_KASAT;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AMIArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AmazonS3ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactoryRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AzureArtifactsConfig;
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
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.cdng.artifact.bean.yaml.SidecarArtifact;
import io.harness.cdng.artifact.bean.yaml.SidecarArtifactWrapper;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.Nexus2RegistryArtifactConfig;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactoryArtifactOutcome;
import io.harness.cdng.artifact.outcome.DockerArtifactOutcome;
import io.harness.cdng.artifact.outcome.JenkinsArtifactOutcome;
import io.harness.cdng.artifact.outcome.NexusArtifactOutcome;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
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
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testGetArtifactKey_IsPrimaryArtifact() {
    DockerHubArtifactConfig config = DockerHubArtifactConfig.builder().primaryArtifact(true).build();
    boolean primaryArtifact = config.isPrimaryArtifact();
    assertThat(primaryArtifact).isTrue();
    String artifactKey = ArtifactUtils.getArtifactKey(config);
    assertThat(config.getIdentifier()).isNotEqualTo("primary");
    assertThat(artifactKey).isEqualTo("primary");
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
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testJenkins_IsPrimaryArtifact() {
    JenkinsArtifactConfig config = JenkinsArtifactConfig.builder().primaryArtifact(true).build();
    boolean primaryArtifact = config.isPrimaryArtifact();
    assertThat(primaryArtifact).isTrue();
    config = JenkinsArtifactConfig.builder().primaryArtifact(false).build();
    primaryArtifact = config.isPrimaryArtifact();
    assertThat(primaryArtifact).isFalse();

    ArtifactOutcome artifactOutcome = JenkinsArtifactOutcome.builder().primaryArtifact(true).build();
    primaryArtifact = artifactOutcome.isPrimaryArtifact();
    assertThat(primaryArtifact).isTrue();
    artifactOutcome = JenkinsArtifactOutcome.builder().primaryArtifact(false).build();
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
    assertThat(artifactKey).isEqualTo("primary");
    artifactConfig = DockerHubArtifactConfig.builder().primaryArtifact(false).identifier("ARTIFACT1").build();
    artifactKey = ArtifactUtils.getArtifactKey(artifactConfig);
    assertThat(artifactKey).isEqualTo("sidecars.ARTIFACT1");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testJenkins_GetArtifactKey() {
    JenkinsArtifactConfig artifactConfig =
        JenkinsArtifactConfig.builder().primaryArtifact(true).identifier("ARTIFACT1").build();
    String artifactKey = ArtifactUtils.getArtifactKey(artifactConfig);
    assertThat(artifactKey).isEqualTo("primary");
    artifactConfig = JenkinsArtifactConfig.builder().primaryArtifact(false).identifier("ARTIFACT1").build();
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
    assertThat(artifactKey).isEqualTo("primary");
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
    assertThat(artifactKey).isEqualTo("primary");
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
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testJenkins_ConvertArtifactListConfig() {
    JenkinsArtifactConfig primaryArtifact =
        JenkinsArtifactConfig.builder().primaryArtifact(true).identifier("ARTIFACT1").build();
    JenkinsArtifactConfig sidecarArtifact =
        JenkinsArtifactConfig.builder().primaryArtifact(false).identifier("ARTIFACT2").build();
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
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGar() {
    GoogleArtifactRegistryConfig primaryArtifact =
        GoogleArtifactRegistryConfig.builder()
            .region(ParameterField.<String>builder().value("us").build())
            .repositoryName(ParameterField.<String>builder().value("vivek").build())
            .pkg(ParameterField.<String>builder().value("alphine").build())
            .project(ParameterField.<String>builder().value("cd-play").build())
            .version(ParameterField.<String>builder().value("version").build())
            .googleArtifactRegistryType(ParameterField.<String>builder().value("docker").build())
            .connectorRef(ParameterField.<String>builder().value("account.gcp").build())
            .isPrimaryArtifact(true)
            .identifier("ARTIFACT1")
            .build();
    GoogleArtifactRegistryConfig sidecarArtifact =
        GoogleArtifactRegistryConfig.builder().isPrimaryArtifact(false).identifier("ARTIFACT12").build();
    ArtifactListConfig artifactListConfig =
        ArtifactListConfig.builder()
            .primary(
                PrimaryArtifact.builder().sourceType(primaryArtifact.getSourceType()).spec(primaryArtifact).build())
            .sidecar(SidecarArtifactWrapper.builder()
                         .sidecar(SidecarArtifact.builder().spec(sidecarArtifact).build())
                         .build())
            .build();

    List<ArtifactConfig> artifactsList = ArtifactUtils.convertArtifactListIntoArtifacts(artifactListConfig, null);
    String log = ArtifactUtils.getLogInfo(
        artifactListConfig.getPrimary().getSpec(), artifactListConfig.getPrimary().getSourceType());
    assertThat(artifactsList).containsOnly(primaryArtifact, sidecarArtifact);
    assertThat(log).isEqualTo("\ntype: GoogleArtifactRegistry \n"
        + "region: us \n"
        + "project: cd-play \n"
        + "repositoryName: vivek \n"
        + "package: alphine \n"
        + "version/versionRegex: version \n"
        + "connectorRef: account.gcp \n"
        + "registryType: docker\n");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testJenkinsLog() {
    // Jenkins Primary Artifact
    JenkinsArtifactConfig primaryArtifact =
        JenkinsArtifactConfig.builder()
            .artifactPath(ParameterField.<String>builder().value("artifactPath").build())
            .build(ParameterField.<String>builder().value("build").build())
            .jobName(ParameterField.<String>builder().value("jobName").build())
            .connectorRef(ParameterField.<String>builder().value("jenkins").build())
            .build();

    // Jenkins side car Artifact
    JenkinsArtifactConfig sidecarArtifact =
        JenkinsArtifactConfig.builder().primaryArtifact(false).identifier("ARTIFACT12").build();

    // List of artifacts
    ArtifactListConfig artifactListConfig =
        ArtifactListConfig.builder()
            .primary(
                PrimaryArtifact.builder().sourceType(primaryArtifact.getSourceType()).spec(primaryArtifact).build())
            .sidecar(SidecarArtifactWrapper.builder()
                         .sidecar(SidecarArtifact.builder().spec(sidecarArtifact).build())
                         .build())
            .build();

    // artifactList
    List<ArtifactConfig> artifactsList = ArtifactUtils.convertArtifactListIntoArtifacts(artifactListConfig, null);

    String log = ArtifactUtils.getLogInfo(
        artifactListConfig.getPrimary().getSpec(), artifactListConfig.getPrimary().getSourceType());

    assertThat(artifactsList).containsOnly(primaryArtifact, sidecarArtifact);

    // Comparing the generated the log
    assertThat(log).isEqualTo("\n"
        + "type: Jenkins \n"
        + "JobName: jobName \n"
        + "ArtifactPath: artifactPath \n"
        + "Build: build \n"
        + "ConnectorRef: jenkins\n");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testArtifactUtilsAmazonS3() {
    // AmazonS3 Primary Artifact
    AmazonS3ArtifactConfig primaryArtifact =
        AmazonS3ArtifactConfig.builder()
            .connectorRef(ParameterField.<String>builder().value("Amazons3").build())
            .bucketName(ParameterField.<String>builder().value("bucketName").build())
            .filePathRegex(ParameterField.<String>builder().value("filePathRegex").build())
            .filePath(ParameterField.<String>builder().value("filePath").build())
            .build();

    // Amazons3 side car Artifact
    AmazonS3ArtifactConfig sidecarArtifact =
        AmazonS3ArtifactConfig.builder().primaryArtifact(false).identifier("ARTIFACT12").build();

    // List of artifacts
    ArtifactListConfig artifactListConfig =
        ArtifactListConfig.builder()
            .primary(
                PrimaryArtifact.builder().sourceType(primaryArtifact.getSourceType()).spec(primaryArtifact).build())
            .sidecar(SidecarArtifactWrapper.builder()
                         .sidecar(SidecarArtifact.builder().spec(sidecarArtifact).build())
                         .build())
            .build();

    // artifactList
    List<ArtifactConfig> artifactsList = ArtifactUtils.convertArtifactListIntoArtifacts(artifactListConfig, null);

    String log = ArtifactUtils.getLogInfo(
        artifactListConfig.getPrimary().getSpec(), artifactListConfig.getPrimary().getSourceType());

    assertThat(artifactsList).containsOnly(primaryArtifact, sidecarArtifact);

    // Comparing the generated the log
    assertThat(log).isEqualTo("\n"
        + "type: AmazonS3 \n"
        + "bucketName: bucketName \n"
        + "filePath: filePath \n"
        + "filePathRegex: filePathRegex \n"
        + "connectorRef: Amazons3\n");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGCR() {
    // Gcr Primary Artifact
    GcrArtifactConfig primaryArtifact =
        GcrArtifactConfig.builder()
            .imagePath(ParameterField.<String>builder().value("imagePath").build())
            .registryHostname(ParameterField.<String>builder().value("registryHostname").build())
            .connectorRef(ParameterField.<String>builder().value("GCR").build())
            .tag(ParameterField.<String>builder().value("TAG").build())
            .build();

    // Gcr side car Artifact
    JenkinsArtifactConfig sidecarArtifact =
        JenkinsArtifactConfig.builder().primaryArtifact(false).identifier("ARTIFACT12").build();

    // List of artifacts
    ArtifactListConfig artifactListConfig =
        ArtifactListConfig.builder()
            .primary(
                PrimaryArtifact.builder().sourceType(primaryArtifact.getSourceType()).spec(primaryArtifact).build())
            .sidecar(SidecarArtifactWrapper.builder()
                         .sidecar(SidecarArtifact.builder().spec(sidecarArtifact).build())
                         .build())
            .build();

    // artifactList
    List<ArtifactConfig> artifactsList = ArtifactUtils.convertArtifactListIntoArtifacts(artifactListConfig, null);

    String log = ArtifactUtils.getLogInfo(
        artifactListConfig.getPrimary().getSpec(), artifactListConfig.getPrimary().getSourceType());

    assertThat(artifactsList).containsOnly(primaryArtifact, sidecarArtifact);

    // Comparing the generated the log
    assertThat(log).isEqualTo(" type: Gcr, image: imagePath, tag/tagRegex: TAG, connectorRef: GCR\n");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testEcrLog() {
    // Jenkins Primary Artifact
    EcrArtifactConfig primaryArtifact = EcrArtifactConfig.builder()
                                            .imagePath(ParameterField.<String>builder().value("imagePath").build())
                                            .connectorRef(ParameterField.<String>builder().value("ECR").build())
                                            .tag(ParameterField.<String>builder().value("TAG").build())
                                            .build();

    // Ecr side car Artifact
    JenkinsArtifactConfig sidecarArtifact =
        JenkinsArtifactConfig.builder().primaryArtifact(false).identifier("ARTIFACT12").build();

    // List of artifacts
    ArtifactListConfig artifactListConfig =
        ArtifactListConfig.builder()
            .primary(
                PrimaryArtifact.builder().sourceType(primaryArtifact.getSourceType()).spec(primaryArtifact).build())
            .sidecar(SidecarArtifactWrapper.builder()
                         .sidecar(SidecarArtifact.builder().spec(sidecarArtifact).build())
                         .build())
            .build();

    // artifactList
    List<ArtifactConfig> artifactsList = ArtifactUtils.convertArtifactListIntoArtifacts(artifactListConfig, null);

    String log = ArtifactUtils.getLogInfo(
        artifactListConfig.getPrimary().getSpec(), artifactListConfig.getPrimary().getSourceType());

    assertThat(artifactsList).containsOnly(primaryArtifact, sidecarArtifact);

    // Comparing the generated the log
    assertThat(log).isEqualTo(" type: Ecr, image: imagePath, tag/tagRegex: TAG, connectorRef: ECR\n");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testNexus3Log() {
    // Jenkins Primary Artifact
    NexusRegistryArtifactConfig primaryArtifact =
        NexusRegistryArtifactConfig.builder()
            .connectorRef(ParameterField.<String>builder().value("Nexus3").build())
            .artifactPath(ParameterField.<String>builder().value("ArtifactPath").build())
            .repository(ParameterField.<String>builder().value("Repository").build())
            .tag(ParameterField.<String>builder().value("TAG").build())
            .build();

    // Ecr side car Artifact
    JenkinsArtifactConfig sidecarArtifact =
        JenkinsArtifactConfig.builder().primaryArtifact(false).identifier("ARTIFACT12").build();

    // List of artifacts
    ArtifactListConfig artifactListConfig =
        ArtifactListConfig.builder()
            .primary(
                PrimaryArtifact.builder().sourceType(primaryArtifact.getSourceType()).spec(primaryArtifact).build())
            .sidecar(SidecarArtifactWrapper.builder()
                         .sidecar(SidecarArtifact.builder().spec(sidecarArtifact).build())
                         .build())
            .build();

    // artifactList
    List<ArtifactConfig> artifactsList = ArtifactUtils.convertArtifactListIntoArtifacts(artifactListConfig, null);

    String log = ArtifactUtils.getLogInfo(
        artifactListConfig.getPrimary().getSpec(), artifactListConfig.getPrimary().getSourceType());

    assertThat(artifactsList).containsOnly(primaryArtifact, sidecarArtifact);

    // Comparing the generated the log
    assertThat(log).isEqualTo(" type: Nexus3Registry, image: Repository, tag/tagRegex: TAG, connectorRef: Nexus3\n");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testNexus2Log() {
    // Jenkins Primary Artifact
    Nexus2RegistryArtifactConfig primaryArtifact =
        Nexus2RegistryArtifactConfig.builder()
            .connectorRef(ParameterField.<String>builder().value("Nexus2").build())
            .repository(ParameterField.<String>builder().value("Repository").build())
            .tag(ParameterField.<String>builder().value("TAG").build())
            .build();

    // Ecr side car Artifact
    JenkinsArtifactConfig sidecarArtifact =
        JenkinsArtifactConfig.builder().primaryArtifact(false).identifier("ARTIFACT12").build();

    // List of artifacts
    ArtifactListConfig artifactListConfig =
        ArtifactListConfig.builder()
            .primary(
                PrimaryArtifact.builder().sourceType(primaryArtifact.getSourceType()).spec(primaryArtifact).build())
            .sidecar(SidecarArtifactWrapper.builder()
                         .sidecar(SidecarArtifact.builder().spec(sidecarArtifact).build())
                         .build())
            .build();

    // artifactList
    List<ArtifactConfig> artifactsList = ArtifactUtils.convertArtifactListIntoArtifacts(artifactListConfig, null);

    String log = ArtifactUtils.getLogInfo(
        artifactListConfig.getPrimary().getSpec(), artifactListConfig.getPrimary().getSourceType());

    assertThat(artifactsList).containsOnly(primaryArtifact, sidecarArtifact);

    // Comparing the generated the log
    assertThat(log).isEqualTo(" type: Nexus2Registry, image: Repository, tag/tagRegex: TAG, connectorRef: Nexus2\n");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testArtifactoryLogDocker() {
    // Artifactory Primary Artifact
    ArtifactoryRegistryArtifactConfig primaryArtifact =
        ArtifactoryRegistryArtifactConfig.builder()
            .connectorRef(ParameterField.<String>builder().value("Artifactory").build())
            .repository(ParameterField.<String>builder().value("Repository").build())
            .artifactPath(ParameterField.<String>builder().value("ArtifactPath").build())
            .tag(ParameterField.<String>builder().value("TAG").build())
            .repositoryFormat(ParameterField.<String>builder().value("Docker").build())
            .build();

    // Ecr side car Artifact
    JenkinsArtifactConfig sidecarArtifact =
        JenkinsArtifactConfig.builder().primaryArtifact(false).identifier("ARTIFACT12").build();

    // List of artifacts
    ArtifactListConfig artifactListConfig =
        ArtifactListConfig.builder()
            .primary(
                PrimaryArtifact.builder().sourceType(primaryArtifact.getSourceType()).spec(primaryArtifact).build())
            .sidecar(SidecarArtifactWrapper.builder()
                         .sidecar(SidecarArtifact.builder().spec(sidecarArtifact).build())
                         .build())
            .build();

    // artifactList
    List<ArtifactConfig> artifactsList = ArtifactUtils.convertArtifactListIntoArtifacts(artifactListConfig, null);

    String log = ArtifactUtils.getLogInfo(
        artifactListConfig.getPrimary().getSpec(), artifactListConfig.getPrimary().getSourceType());

    assertThat(artifactsList).containsOnly(primaryArtifact, sidecarArtifact);

    // Comparing the generated the log
    assertThat(log).isEqualTo(
        " type: ArtifactoryRegistry, image: ArtifactPath, tag/tagRegex: TAG, connectorRef: Artifactory\n");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testArtifactoryLogGeneric() {
    // Artifactory Primary Artifact
    ArtifactoryRegistryArtifactConfig primaryArtifact =
        ArtifactoryRegistryArtifactConfig.builder()
            .connectorRef(ParameterField.<String>builder().value("Artifactory").build())
            .repository(ParameterField.<String>builder().value("Repository").build())
            .artifactPath(ParameterField.<String>builder().value("ArtifactPath").build())
            .tag(ParameterField.<String>builder().value("TAG").build())
            .artifactDirectory(ParameterField.<String>builder().value("artifactDirectory").build())
            .repositoryFormat(ParameterField.<String>builder().value("generic").build())
            .build();

    // Ecr side car Artifact
    JenkinsArtifactConfig sidecarArtifact =
        JenkinsArtifactConfig.builder().primaryArtifact(false).identifier("ARTIFACT12").build();

    // List of artifacts
    ArtifactListConfig artifactListConfig =
        ArtifactListConfig.builder()
            .primary(
                PrimaryArtifact.builder().sourceType(primaryArtifact.getSourceType()).spec(primaryArtifact).build())
            .sidecar(SidecarArtifactWrapper.builder()
                         .sidecar(SidecarArtifact.builder().spec(sidecarArtifact).build())
                         .build())
            .build();

    // artifactList
    List<ArtifactConfig> artifactsList = ArtifactUtils.convertArtifactListIntoArtifacts(artifactListConfig, null);

    String log = ArtifactUtils.getLogInfo(
        artifactListConfig.getPrimary().getSpec(), artifactListConfig.getPrimary().getSourceType());

    assertThat(artifactsList).containsOnly(primaryArtifact, sidecarArtifact);

    // Comparing the generated the log
    assertThat(log).isEqualTo(
        "type: ArtifactoryRegistry, artifactDirectory: artifactDirectory, artifactPath/artifactPathFilter: ArtifactPath, connectorRef: Artifactory\n");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testACR() {
    // Artifactory Primary Artifact
    AcrArtifactConfig primaryArtifact = AcrArtifactConfig.builder()
                                            .connectorRef(ParameterField.<String>builder().value("Acr").build())
                                            .repository(ParameterField.<String>builder().value("Repository").build())
                                            .tag(ParameterField.<String>builder().value("TAG").build())
                                            .build();

    // Acr side car Artifact
    JenkinsArtifactConfig sidecarArtifact =
        JenkinsArtifactConfig.builder().primaryArtifact(false).identifier("ARTIFACT12").build();

    // List of artifacts
    ArtifactListConfig artifactListConfig =
        ArtifactListConfig.builder()
            .primary(
                PrimaryArtifact.builder().sourceType(primaryArtifact.getSourceType()).spec(primaryArtifact).build())
            .sidecar(SidecarArtifactWrapper.builder()
                         .sidecar(SidecarArtifact.builder().spec(sidecarArtifact).build())
                         .build())
            .build();

    // artifactList
    List<ArtifactConfig> artifactsList = ArtifactUtils.convertArtifactListIntoArtifacts(artifactListConfig, null);

    String log = ArtifactUtils.getLogInfo(
        artifactListConfig.getPrimary().getSpec(), artifactListConfig.getPrimary().getSourceType());

    assertThat(artifactsList).containsOnly(primaryArtifact, sidecarArtifact);

    // Comparing the generated the log
    assertThat(log).isEqualTo(" type: Acr, image: Repository, tag/tagRegex: TAG, connectorRef: Acr\n");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGithubPackages() {
    // Artifactory Primary Artifact
    GithubPackagesArtifactConfig primaryArtifact =
        GithubPackagesArtifactConfig.builder()
            .connectorRef(ParameterField.<String>builder().value("githubpackages").build())
            .packageType(ParameterField.<String>builder().value("Maven").build())
            .packageName(ParameterField.<String>builder().value("packageName").build())
            .version(ParameterField.<String>builder().value("version").build())
            .versionRegex(ParameterField.<String>builder().value("versionRegex").build())
            .org(ParameterField.<String>builder().value("org").build())
            .build();

    // Acr side car Artifact
    JenkinsArtifactConfig sidecarArtifact =
        JenkinsArtifactConfig.builder().primaryArtifact(false).identifier("ARTIFACT12").build();

    // List of artifacts
    ArtifactListConfig artifactListConfig =
        ArtifactListConfig.builder()
            .primary(
                PrimaryArtifact.builder().sourceType(primaryArtifact.getSourceType()).spec(primaryArtifact).build())
            .sidecar(SidecarArtifactWrapper.builder()
                         .sidecar(SidecarArtifact.builder().spec(sidecarArtifact).build())
                         .build())
            .build();

    // artifactList
    List<ArtifactConfig> artifactsList = ArtifactUtils.convertArtifactListIntoArtifacts(artifactListConfig, null);

    String log = ArtifactUtils.getLogInfo(
        artifactListConfig.getPrimary().getSpec(), artifactListConfig.getPrimary().getSourceType());

    assertThat(artifactsList).containsOnly(primaryArtifact, sidecarArtifact);

    // Comparing the generated the log
    assertThat(log).isEqualTo("\n"
        + "type: GithubPackageRegistry \n"
        + "connectorRef: githubpackages \n"
        + "org: org \n"
        + "packageName: packageName \n"
        + "packageType: Maven \n"
        + "version: version \n"
        + "versionRegex: versionRegex\n");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testAzureArtifacts() {
    // Azure Artifacts Primary Artifact
    AzureArtifactsConfig primaryArtifact =
        AzureArtifactsConfig.builder()
            .connectorRef(ParameterField.<String>builder().value("azure").build())
            .packageType(ParameterField.<String>builder().value("Maven").build())
            .packageName(ParameterField.<String>builder().value("packageName").build())
            .version(ParameterField.<String>builder().value("version").build())
            .versionRegex(ParameterField.<String>builder().value("versionRegex").build())
            .feed(ParameterField.<String>builder().value("feed").build())
            .scope(ParameterField.<String>builder().value("project").build())
            .project(ParameterField.<String>builder().value("project").build())
            .build();

    // Acr side car Artifact
    JenkinsArtifactConfig sidecarArtifact =
        JenkinsArtifactConfig.builder().primaryArtifact(false).identifier("ARTIFACT12").build();

    // List of artifacts
    ArtifactListConfig artifactListConfig =
        ArtifactListConfig.builder()
            .primary(
                PrimaryArtifact.builder().sourceType(primaryArtifact.getSourceType()).spec(primaryArtifact).build())
            .sidecar(SidecarArtifactWrapper.builder()
                         .sidecar(SidecarArtifact.builder().spec(sidecarArtifact).build())
                         .build())
            .build();

    // artifactList
    List<ArtifactConfig> artifactsList = ArtifactUtils.convertArtifactListIntoArtifacts(artifactListConfig, null);

    String log = ArtifactUtils.getLogInfo(
        artifactListConfig.getPrimary().getSpec(), artifactListConfig.getPrimary().getSourceType());

    assertThat(artifactsList).containsOnly(primaryArtifact, sidecarArtifact);

    // Comparing the generated the log
    assertThat(log).isEqualTo("\n"
        + "type: AzureArtifacts \n"
        + "scope: project \n"
        + "project: project \n"
        + "feed: feed \n"
        + "packageType: Maven \n"
        + "packageName: packageName \n"
        + "version: version \n"
        + "versionRegex: versionRegex \n"
        + "connectorRef: azure\n");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testAMI() {
    // Azure Artifacts Primary Artifact
    AMIArtifactConfig primaryArtifact =
        AMIArtifactConfig.builder()
            .connectorRef(ParameterField.<String>builder().value("AMI").build())
            .version(ParameterField.<String>builder().value("version").build())
            .versionRegex(ParameterField.<String>builder().value("versionRegex").build())
            .build();

    // AMI side car Artifact
    JenkinsArtifactConfig sidecarArtifact =
        JenkinsArtifactConfig.builder().primaryArtifact(false).identifier("ARTIFACT12").build();

    // List of artifacts
    ArtifactListConfig artifactListConfig =
        ArtifactListConfig.builder()
            .primary(
                PrimaryArtifact.builder().sourceType(primaryArtifact.getSourceType()).spec(primaryArtifact).build())
            .sidecar(SidecarArtifactWrapper.builder()
                         .sidecar(SidecarArtifact.builder().spec(sidecarArtifact).build())
                         .build())
            .build();

    // artifactList
    List<ArtifactConfig> artifactsList = ArtifactUtils.convertArtifactListIntoArtifacts(artifactListConfig, null);

    String log = ArtifactUtils.getLogInfo(
        artifactListConfig.getPrimary().getSpec(), artifactListConfig.getPrimary().getSourceType());

    assertThat(artifactsList).containsOnly(primaryArtifact, sidecarArtifact);

    // Comparing the generated the log
    assertThat(log).isEqualTo("\n"
        + "type: AmazonMachineImage \n"
        + "version: version \n"
        + "versionRegex: versionRegex \n"
        + "connectorRef: AMI\n");
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testGCStorage() {
    // GCStorage Artifacts Primary Artifact
    GoogleCloudStorageArtifactConfig primaryArtifact =
        GoogleCloudStorageArtifactConfig.builder()
            .connectorRef(ParameterField.<String>builder().value("GCS").build())
            .bucket(ParameterField.<String>builder().value("bucket").build())
            .project(ParameterField.<String>builder().value("project").build())
            .artifactPath(ParameterField.<String>builder().value("path").build())
            .build();

    // GCStorage side car Artifact
    GoogleCloudStorageArtifactConfig sidecarArtifact =
        GoogleCloudStorageArtifactConfig.builder().isPrimaryArtifact(false).identifier("ARTIFACT13").build();

    // List of artifacts
    ArtifactListConfig artifactListConfig =
        ArtifactListConfig.builder()
            .primary(
                PrimaryArtifact.builder().sourceType(primaryArtifact.getSourceType()).spec(primaryArtifact).build())
            .sidecar(SidecarArtifactWrapper.builder()
                         .sidecar(SidecarArtifact.builder().spec(sidecarArtifact).build())
                         .build())
            .build();

    // artifactList
    List<ArtifactConfig> artifactsList = ArtifactUtils.convertArtifactListIntoArtifacts(artifactListConfig, null);

    String log = ArtifactUtils.getLogInfo(
        artifactListConfig.getPrimary().getSpec(), artifactListConfig.getPrimary().getSourceType());

    assertThat(artifactsList).containsOnly(primaryArtifact, sidecarArtifact);

    // Comparing the generated the log
    assertThat(log).isEqualTo("\n"
        + "type: GoogleCloudStorage \n"
        + "connectorRef: GCS \n"
        + "project: project \n"
        + "bucket: bucket \n"
        + "artifactPath: path");
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testGCSourceWithBranch() {
    // GCSource Artifacts Primary Artifact
    GoogleCloudSourceArtifactConfig primaryArtifact =
        GoogleCloudSourceArtifactConfig.builder()
            .connectorRef(ParameterField.<String>builder().value("GCS").build())
            .repository(ParameterField.<String>builder().value("repo").build())
            .project(ParameterField.<String>builder().value("project").build())
            .sourceDirectory(ParameterField.<String>builder().value("path").build())
            .fetchType(GoogleCloudSourceFetchType.BRANCH)
            .branch(ParameterField.<String>builder().value("branch").build())
            .build();

    // GCSource side car Artifact
    GoogleCloudSourceArtifactConfig sidecarArtifact =
        GoogleCloudSourceArtifactConfig.builder().isPrimaryArtifact(false).identifier("ARTIFACT14").build();

    // List of artifacts
    ArtifactListConfig artifactListConfig =
        ArtifactListConfig.builder()
            .primary(
                PrimaryArtifact.builder().sourceType(primaryArtifact.getSourceType()).spec(primaryArtifact).build())
            .sidecar(SidecarArtifactWrapper.builder()
                         .sidecar(SidecarArtifact.builder().spec(sidecarArtifact).build())
                         .build())
            .build();

    // artifactList
    List<ArtifactConfig> artifactsList = ArtifactUtils.convertArtifactListIntoArtifacts(artifactListConfig, null);

    String log = ArtifactUtils.getLogInfo(
        artifactListConfig.getPrimary().getSpec(), artifactListConfig.getPrimary().getSourceType());

    assertThat(artifactsList).containsOnly(primaryArtifact, sidecarArtifact);

    // Comparing the generated the log
    assertThat(log).isEqualTo("\n"
        + "type: GoogleCloudSource \n"
        + "connectorRef: GCS \n"
        + "project: project \n"
        + "repository: repo \n"
        + "branch: branch \n"
        + "sourceDirectory: path");
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testGCSourceWithCommitId() {
    // GCSource Artifacts Primary Artifact
    GoogleCloudSourceArtifactConfig primaryArtifact =
        GoogleCloudSourceArtifactConfig.builder()
            .connectorRef(ParameterField.<String>builder().value("GCS").build())
            .repository(ParameterField.<String>builder().value("repo").build())
            .project(ParameterField.<String>builder().value("project").build())
            .sourceDirectory(ParameterField.<String>builder().value("path").build())
            .fetchType(GoogleCloudSourceFetchType.COMMIT)
            .commitId(ParameterField.<String>builder().value("commitId").build())
            .build();

    // GCSource side car Artifact
    GoogleCloudSourceArtifactConfig sidecarArtifact =
        GoogleCloudSourceArtifactConfig.builder().isPrimaryArtifact(false).identifier("ARTIFACT15").build();

    // List of artifacts
    ArtifactListConfig artifactListConfig =
        ArtifactListConfig.builder()
            .primary(
                PrimaryArtifact.builder().sourceType(primaryArtifact.getSourceType()).spec(primaryArtifact).build())
            .sidecar(SidecarArtifactWrapper.builder()
                         .sidecar(SidecarArtifact.builder().spec(sidecarArtifact).build())
                         .build())
            .build();

    // artifactList
    List<ArtifactConfig> artifactsList = ArtifactUtils.convertArtifactListIntoArtifacts(artifactListConfig, null);

    String log = ArtifactUtils.getLogInfo(
        artifactListConfig.getPrimary().getSpec(), artifactListConfig.getPrimary().getSourceType());

    assertThat(artifactsList).containsOnly(primaryArtifact, sidecarArtifact);

    // Comparing the generated the log
    assertThat(log).isEqualTo("\n"
        + "type: GoogleCloudSource \n"
        + "connectorRef: GCS \n"
        + "project: project \n"
        + "repository: repo \n"
        + "commitId: commitId \n"
        + "sourceDirectory: path");
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testGCSourceWithTag() {
    // GCSource Artifacts Primary Artifact
    GoogleCloudSourceArtifactConfig primaryArtifact =
        GoogleCloudSourceArtifactConfig.builder()
            .connectorRef(ParameterField.<String>builder().value("GCS").build())
            .repository(ParameterField.<String>builder().value("repo").build())
            .project(ParameterField.<String>builder().value("project").build())
            .sourceDirectory(ParameterField.<String>builder().value("path").build())
            .fetchType(GoogleCloudSourceFetchType.TAG)
            .tag(ParameterField.<String>builder().value("tag").build())
            .build();

    // GCSource side car Artifact
    GoogleCloudSourceArtifactConfig sidecarArtifact =
        GoogleCloudSourceArtifactConfig.builder().isPrimaryArtifact(false).identifier("ARTIFACT16").build();

    // List of artifacts
    ArtifactListConfig artifactListConfig =
        ArtifactListConfig.builder()
            .primary(
                PrimaryArtifact.builder().sourceType(primaryArtifact.getSourceType()).spec(primaryArtifact).build())
            .sidecar(SidecarArtifactWrapper.builder()
                         .sidecar(SidecarArtifact.builder().spec(sidecarArtifact).build())
                         .build())
            .build();

    // artifactList
    List<ArtifactConfig> artifactsList = ArtifactUtils.convertArtifactListIntoArtifacts(artifactListConfig, null);

    String log = ArtifactUtils.getLogInfo(
        artifactListConfig.getPrimary().getSpec(), artifactListConfig.getPrimary().getSourceType());

    assertThat(artifactsList).containsOnly(primaryArtifact, sidecarArtifact);

    // Comparing the generated the log
    assertThat(log).isEqualTo("\n"
        + "type: GoogleCloudSource \n"
        + "connectorRef: GCS \n"
        + "project: project \n"
        + "repository: repo \n"
        + "tag: tag \n"
        + "sourceDirectory: path");
  }
}