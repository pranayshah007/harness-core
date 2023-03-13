/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.artifactory.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.VED;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifactory.ArtifactoryClientImpl;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.service.ArtifactoryRegistryServiceImpl;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ArtifactoryServerException;
import io.harness.exception.HintException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.rule.Owner;

import software.wings.utils.RepositoryFormat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class ArtifactoryRegistryServiceImplTest extends CategoryTest {
  @Mock private ArtifactoryClientImpl artifactoryClient;
  @InjectMocks ArtifactoryRegistryServiceImpl artifactoryRegistryService;

  private static String ARTIFACTORY_URL_HOSTNAME = "artifactory.harness.io";
  private static String ARTIFACTORY_URL = "https://" + ARTIFACTORY_URL_HOSTNAME;
  private static String ARTIFACTORY_USERNAME = "username";
  private static String ARTIFACTORY_PASSWORD = "password";
  private static int MAX_NO_OF_TAGS_PER_IMAGE = 10000;

  private static Map<String, List<BuildDetailsInternal>> buildDetailsData;

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);

    buildDetailsData = new HashMap<>();

    List<BuildDetailsInternal> bdiList = new ArrayList<>();
    String repo = "test1";
    String repoUrl = repo + ".artifactory.harness.io";
    String imageName = "superApp";
    bdiList.add(createBuildDetails(repoUrl, null, repo, imageName, "1.0"));
    bdiList.add(createBuildDetails(repoUrl, null, repo, imageName, "2.0"));
    bdiList.add(createBuildDetails(repoUrl, null, repo, imageName, "3.0"));
    buildDetailsData.put("bdi1", bdiList);

    bdiList = new ArrayList<>();
    repo = "test2";
    repoUrl = repo + ".artifactory.harness.io";
    imageName = "super/duper/app";
    bdiList.add(createBuildDetails(repoUrl, null, repo, imageName, "2.4.1"));
    bdiList.add(createBuildDetails(repoUrl, null, repo, imageName, "2.4.2"));
    bdiList.add(createBuildDetails(repoUrl, null, repo, imageName, "2.5"));
    bdiList.add(createBuildDetails(repoUrl, null, repo, imageName, "2.5.3"));
    buildDetailsData.put("bdi2", bdiList);

    bdiList = new ArrayList<>();
    repo = "test2";
    repoUrl = repo + ".artifactory.harness.io";
    imageName = "extra/megaapp";
    bdiList.add(createBuildDetails(repoUrl, null, repo, imageName, "a4"));
    bdiList.add(createBuildDetails(repoUrl, null, repo, imageName, "b23"));
    bdiList.add(createBuildDetails(repoUrl, null, repo, imageName, "latest"));
    bdiList.add(createBuildDetails(repoUrl, null, repo, imageName, "basic"));
    buildDetailsData.put("bdi3", bdiList);

    bdiList = new ArrayList<>();
    buildDetailsData.put("bdi4", bdiList);

    bdiList = new ArrayList<>();
    repo = "test1";
    repoUrl = repo + ".artifactory.harness.io";
    imageName = "superApp";
    bdiList.add(createBuildDetails(repoUrl, null, repo, imageName, "1.0"));
    bdiList.add(createBuildDetails(repoUrl, null, repo, imageName, "1.0"));
    buildDetailsData.put("bdi5", bdiList);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetBuilds() throws IOException {
    ArtifactoryConfigRequest artifactoryInternalConfig = ArtifactoryConfigRequest.builder()
                                                             .artifactoryUrl(ARTIFACTORY_URL)
                                                             .username(ARTIFACTORY_USERNAME)
                                                             .password(ARTIFACTORY_PASSWORD.toCharArray())
                                                             .artifactRepositoryUrl("test1.artifactory.harness.io")
                                                             .build();

    doReturn(buildDetailsData.get("bdi1"))
        .when(artifactoryClient)
        .getArtifactsDetails(artifactoryInternalConfig, "test1", "superApp", RepositoryFormat.docker.name());

    List<BuildDetailsInternal> response = artifactoryRegistryService.getBuilds(
        artifactoryInternalConfig, "test1", "superApp", RepositoryFormat.docker.name());
    assertThat(response).isNotNull();
    assertThat(response.size()).isEqualTo(3);
    for (BuildDetailsInternal bdi : response) {
      assertThat(bdi.getMetadata().get(ArtifactMetadataKeys.IMAGE))
          .startsWith("test1.artifactory.harness.io/superApp:");
      assertThat(bdi.getMetadata().get(ArtifactMetadataKeys.IMAGE))
          .isNotEqualTo("test1.artifactory.harness.io/superApp:");
    }
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFromRegex_1() throws IOException {
    ArtifactoryConfigRequest artifactoryInternalConfig = ArtifactoryConfigRequest.builder()
                                                             .artifactoryUrl(ARTIFACTORY_URL)
                                                             .username(ARTIFACTORY_USERNAME)
                                                             .password(ARTIFACTORY_PASSWORD.toCharArray())
                                                             .artifactRepositoryUrl("test1.artifactory.harness.io")
                                                             .build();

    doReturn(buildDetailsData.get("bdi1"))
        .when(artifactoryClient)
        .getArtifactsDetails(artifactoryInternalConfig, "test1", "superApp", RepositoryFormat.docker.name());

    BuildDetailsInternal response = artifactoryRegistryService.getLastSuccessfulBuildFromRegex(
        artifactoryInternalConfig, "test1", "superApp", RepositoryFormat.docker.name(), "[\\d]{1}.0");
    assertThat(response).isNotNull();
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.TAG)).isEqualTo("3.0");
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.IMAGE))
        .isEqualTo("test1.artifactory.harness.io/superApp:3.0");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFromRegex_2() throws IOException {
    ArtifactoryConfigRequest artifactoryInternalConfig = ArtifactoryConfigRequest.builder()
                                                             .artifactoryUrl(ARTIFACTORY_URL)
                                                             .username(ARTIFACTORY_USERNAME)
                                                             .password(ARTIFACTORY_PASSWORD.toCharArray())
                                                             .artifactRepositoryUrl("test2.artifactory.harness.io")
                                                             .build();

    doReturn(buildDetailsData.get("bdi2"))
        .when(artifactoryClient)
        .getArtifactsDetails(artifactoryInternalConfig, "test2", "super/duper/app", RepositoryFormat.docker.name());

    BuildDetailsInternal response =
        artifactoryRegistryService.getLastSuccessfulBuildFromRegex(artifactoryInternalConfig, "test2",
            "super/duper/app", RepositoryFormat.docker.name(), "[\\d]{1}.[\\d]{1}.[\\d]{1}");
    assertThat(response).isNotNull();
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.TAG)).isEqualTo("2.5.3");
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.IMAGE))
        .isEqualTo("test2.artifactory.harness.io/super/duper/app:2.5.3");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFromRegex_3() throws IOException {
    ArtifactoryConfigRequest artifactoryInternalConfig = ArtifactoryConfigRequest.builder()
                                                             .artifactoryUrl(ARTIFACTORY_URL)
                                                             .username(ARTIFACTORY_USERNAME)
                                                             .password(ARTIFACTORY_PASSWORD.toCharArray())
                                                             .artifactRepositoryUrl("test2.artifactory.harness.io")
                                                             .build();

    doReturn(buildDetailsData.get("bdi3"))
        .when(artifactoryClient)
        .getArtifactsDetails(artifactoryInternalConfig, "test2", "extra/megaapp", RepositoryFormat.docker.name());

    BuildDetailsInternal response = artifactoryRegistryService.getLastSuccessfulBuildFromRegex(
        artifactoryInternalConfig, "test2", "extra/megaapp", RepositoryFormat.docker.name(), "*");
    assertThat(response).isNotNull();
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.TAG)).isEqualTo("latest");
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.IMAGE))
        .isEqualTo("test2.artifactory.harness.io/extra/megaapp:latest");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFromRegex_NoArtifactFound() throws IOException {
    ArtifactoryConfigRequest artifactoryInternalConfig = ArtifactoryConfigRequest.builder()
                                                             .artifactoryUrl(ARTIFACTORY_URL)
                                                             .username(ARTIFACTORY_USERNAME)
                                                             .password(ARTIFACTORY_PASSWORD.toCharArray())
                                                             .artifactRepositoryUrl("test2.artifactory.harness.io")
                                                             .build();

    doReturn(buildDetailsData.get("bdi3"))
        .when(artifactoryClient)
        .getArtifactsDetails(artifactoryInternalConfig, "test2", "extra/megaapp", RepositoryFormat.docker.name());

    assertThatThrownBy(()
                           -> artifactoryRegistryService.getLastSuccessfulBuildFromRegex(artifactoryInternalConfig,
                               "test2", "extra/megaapp", RepositoryFormat.docker.name(), "noArtifactFound"))
        .isInstanceOf(HintException.class);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testVerifyBuildNumber() throws IOException {
    ArtifactoryConfigRequest artifactoryInternalConfig = ArtifactoryConfigRequest.builder()
                                                             .artifactoryUrl(ARTIFACTORY_URL)
                                                             .username(ARTIFACTORY_USERNAME)
                                                             .password(ARTIFACTORY_PASSWORD.toCharArray())
                                                             .artifactRepositoryUrl("test2.artifactory.harness.io")
                                                             .build();

    doReturn(buildDetailsData.get("bdi3"))
        .when(artifactoryClient)
        .getArtifactsDetails(artifactoryInternalConfig, "test2", "extra/megaapp", RepositoryFormat.docker.name());

    BuildDetailsInternal response = artifactoryRegistryService.verifyBuildNumber(
        artifactoryInternalConfig, "test2", "extra/megaapp", RepositoryFormat.docker.name(), "b23");
    assertThat(response).isNotNull();
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.TAG)).isEqualTo("b23");
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.IMAGE))
        .isEqualTo("test2.artifactory.harness.io/extra/megaapp:b23");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testValidateCredentials() throws IOException {
    ArtifactoryConfigRequest artifactoryInternalConfig = ArtifactoryConfigRequest.builder()
                                                             .artifactoryUrl(ARTIFACTORY_URL)
                                                             .username(ARTIFACTORY_USERNAME)
                                                             .password(ARTIFACTORY_PASSWORD.toCharArray())
                                                             .artifactRepositoryUrl("test2.artifactory.harness.io")
                                                             .build();

    doReturn(true).when(artifactoryClient).validateArtifactServer(artifactoryInternalConfig);

    boolean response = artifactoryRegistryService.validateCredentials(artifactoryInternalConfig);
    assertThat(response).isNotNull();
    assertThat(response).isEqualTo(true);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetLabels() throws IOException {
    ArtifactoryConfigRequest artifactoryInternalConfig = ArtifactoryConfigRequest.builder()
                                                             .artifactoryUrl(ARTIFACTORY_URL)
                                                             .username(ARTIFACTORY_USERNAME)
                                                             .password(ARTIFACTORY_PASSWORD.toCharArray())
                                                             .artifactRepositoryUrl("test2.artifactory.harness.io")
                                                             .build();

    Map<String, String> labels = new HashMap<>();

    // hashmap for labels
    labels.put("multi.key.value", "abc");
    labels.put("build_date", "2017-09-05");
    labels.put("maintainer", "dev@someproject.org");

    List<Map<String, String>> labelsList = new ArrayList<>();

    labelsList.add(labels);

    doReturn(labelsList)
        .when(artifactoryClient)
        .getLabels(artifactoryInternalConfig, "imageName", "RepositoryName", "buildNos");

    assertThat(
        artifactoryRegistryService.getLabels(artifactoryInternalConfig, "imageName", "RepositoryName", "buildNos"))
        .isEqualTo(labelsList);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetLabelsException() throws IOException {
    ArtifactoryConfigRequest artifactoryInternalConfig = ArtifactoryConfigRequest.builder()
                                                             .artifactoryUrl(ARTIFACTORY_URL)
                                                             .username(ARTIFACTORY_USERNAME)
                                                             .password(ARTIFACTORY_PASSWORD.toCharArray())
                                                             .artifactRepositoryUrl("test2.artifactory.harness.io")
                                                             .build();

    Map<String, String> labels = new HashMap<>();

    // hashmap for labels
    labels.put("multi.key.value", "abc");
    labels.put("build_date", "2017-09-05");
    labels.put("maintainer", "dev@someproject.org");

    List<Map<String, String>> labelsList = new ArrayList<>();

    labelsList.add(labels);

    doThrow(
        NestedExceptionUtils.hintWithExplanationException(
            "Check if the URL is correct. Consider appending `/artifactory` to the connector endpoint if you have not already. Check artifact configuration (repository and artifact path field values).",

            "Artifactory connector URL or artifact configuration may be incorrect or the server is down or the server is not reachable from the delegate",

            new ArtifactoryServerException(
                "Artifactory Server responded with Not Found.", ErrorCode.INVALID_ARTIFACT_SERVER, USER)))
        .when(artifactoryClient)
        .getLabels(artifactoryInternalConfig, "imageName", "RepositoryName", "buildNos");

    assertThat(
        artifactoryRegistryService.getLabels(artifactoryInternalConfig, "imageName", "RepositoryName", "buildNos"))
        .isEqualTo(Collections.emptyList());
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetBuildsWithWrongRepositoryFormat() {
    ArtifactoryConfigRequest artifactoryInternalConfig = ArtifactoryConfigRequest.builder()
                                                             .artifactoryUrl(ARTIFACTORY_URL)
                                                             .username(ARTIFACTORY_USERNAME)
                                                             .password(ARTIFACTORY_PASSWORD.toCharArray())
                                                             .artifactRepositoryUrl("test1.artifactory.harness.io")
                                                             .build();

    assertThatThrownBy(()
                           -> artifactoryRegistryService.getBuilds(
                               artifactoryInternalConfig, "test1", "superApp", RepositoryFormat.maven.name()))
        .isInstanceOf(HintException.class);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildIncorrectRegex() {
    ArtifactoryConfigRequest artifactoryInternalConfig = ArtifactoryConfigRequest.builder()
                                                             .artifactoryUrl(ARTIFACTORY_URL)
                                                             .username(ARTIFACTORY_USERNAME)
                                                             .password(ARTIFACTORY_PASSWORD.toCharArray())
                                                             .artifactRepositoryUrl("test2.artifactory.harness.io")
                                                             .build();

    assertThatThrownBy(()
                           -> artifactoryRegistryService.getLastSuccessfulBuildFromRegex(artifactoryInternalConfig,
                               "test2", "extra/megaapp", RepositoryFormat.docker.name(), "(r!egex"))
        .isInstanceOf(PatternSyntaxException.class);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testVerifyBuildNumberWithEmptyBuildsScenario() throws IOException {
    ArtifactoryConfigRequest artifactoryInternalConfig = ArtifactoryConfigRequest.builder()
                                                             .artifactoryUrl(ARTIFACTORY_URL)
                                                             .username(ARTIFACTORY_USERNAME)
                                                             .password(ARTIFACTORY_PASSWORD.toCharArray())
                                                             .artifactRepositoryUrl("test2.artifactory.harness.io")
                                                             .build();

    doReturn(buildDetailsData.get("bdi4"))
        .when(artifactoryClient)
        .getArtifactsDetails(artifactoryInternalConfig, "test2", "extra/megaapp", RepositoryFormat.docker.name());

    assertThatThrownBy(()
                           -> artifactoryRegistryService.verifyBuildNumber(artifactoryInternalConfig, "test2",
                               "extra/megaapp", RepositoryFormat.docker.name(), "b23"))
        .isInstanceOf(HintException.class);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testVerifyBuildNumberWithRepetitiveTag() throws IOException {
    ArtifactoryConfigRequest artifactoryInternalConfig = ArtifactoryConfigRequest.builder()
                                                             .artifactoryUrl(ARTIFACTORY_URL)
                                                             .username(ARTIFACTORY_USERNAME)
                                                             .password(ARTIFACTORY_PASSWORD.toCharArray())
                                                             .artifactRepositoryUrl("test2.artifactory.harness.io")
                                                             .build();

    doReturn(buildDetailsData.get("bdi5"))
        .when(artifactoryClient)
        .getArtifactsDetails(artifactoryInternalConfig, "test2", "extra/megaapp", RepositoryFormat.docker.name());

    assertThatThrownBy(()
                           -> artifactoryRegistryService.verifyBuildNumber(artifactoryInternalConfig, "test2",
                               "extra/megaapp", RepositoryFormat.docker.name(), "b23"))
        .isInstanceOf(HintException.class);
  }

  private BuildDetailsInternal createBuildDetails(
      String repoUrl, String port, String repoName, String imageName, String tag) {
    return BuildDetailsInternal.builder()
        .number(tag)
        .metadata(createBuildMetadata(repoUrl, port, repoName, imageName, tag))
        .build();
  }

  private String generateArtifactPullUrl(String hostname, String port, String imagePath, String imageTag) {
    return hostname + (isEmpty(port) ? "" : ":" + port) + "/" + imagePath + ":" + imageTag;
  }

  private Map<String, String> createBuildMetadata(
      String hostname, String port, String repoName, String imagePath, String imageTag) {
    Map<String, String> metadata = new HashMap<>();
    metadata.put(ArtifactMetadataKeys.IMAGE, generateArtifactPullUrl(hostname, port, imagePath, imageTag));
    metadata.put(ArtifactMetadataKeys.TAG, imageTag);
    return metadata;
  }
}
