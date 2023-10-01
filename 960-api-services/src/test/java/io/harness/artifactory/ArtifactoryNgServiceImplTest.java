/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifactory;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.SARTHAK_KASAT;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.vivekveman;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.comparator.BuildDetailsComparatorDescending;
import io.harness.category.element.UnitTests;
import io.harness.exception.ArtifactoryRegistryException;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.common.collect.ImmutableMap;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class ArtifactoryNgServiceImplTest extends CategoryTest {
  @Mock ArtifactoryClientImpl artifactoryClient;

  @InjectMocks ArtifactoryNgServiceImpl artifactoryNgService;

  private static final ArtifactoryConfigRequest ARTIFACTORY_CONFIG_REQUEST = ArtifactoryConfigRequest.builder().build();
  private static final String REPO_NAME = "repoName";
  private static final String FULL_ARTIFACT_PATH = "abc/artifactPath.exe.temp";
  private static final String FULL_ARTIFACT_PATH_2 = "artifactPath.exe";
  private static final String FILTER = "filter";
  private static final int MAX_VERSION = 10;
  private static final String ARTIFACT_PATH_REGEX = "[a-zA-Z.]+(exe)";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetRepositories() {
    Map<String, String> repositories = Collections.singletonMap("repo", "repo");
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder().build();
    doReturn(repositories).when(artifactoryClient).getRepositories(any(), any());

    Map<String, String> result = artifactoryNgService.getRepositories(artifactoryConfigRequest, "any");

    verify(artifactoryClient, times(1)).getRepositories(any(), any());
    assertThat(result).isEqualTo(repositories);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testBetBuildDetails() {
    List<BuildDetails> buildDetails = Collections.singletonList(aBuildDetails().build());
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder().build();
    doReturn(buildDetails).when(artifactoryClient).getBuildDetails(any(), any(), any(), anyInt());

    List<BuildDetails> result =
        artifactoryNgService.getBuildDetails(artifactoryConfigRequest, "repoName", "artifactPath", 10);

    verify(artifactoryClient, times(1)).getBuildDetails(any(), any(), any(), anyInt());
    assertThat(result).isEqualTo(buildDetails);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void getFileSize() {
    Long artifactFileSize = 100L;
    Map<String, String> metadata = ImmutableMap.of("artifactPath", "path/to/artifactImage");
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder().build();
    doReturn(artifactFileSize).when(artifactoryClient).getFileSize(any(), any(), any());

    Long result = artifactoryNgService.getFileSize(artifactoryConfigRequest, metadata, "artifactPath");
    assertThat(result).isEqualTo(artifactFileSize);
    verify(artifactoryClient, times(1)).getFileSize(eq(artifactoryConfigRequest), eq(metadata), eq("artifactPath"));
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetArtifactList() {
    List<BuildDetails> buildDetails = Collections.singletonList(aBuildDetails().build());
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder().build();
    doReturn(buildDetails).when(artifactoryClient).getArtifactList(any(), any(), any(), anyInt());

    List<BuildDetails> result =
        artifactoryNgService.getArtifactList(artifactoryConfigRequest, "repoName", "artifactPath", 10, null, null);

    verify(artifactoryClient, times(1)).getArtifactList(any(), any(), any(), anyInt());
    assertThat(result).isEqualTo(buildDetails);
  }
  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testGetArtifactListWithArtifactFilter() {
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder().build();
    List<BuildDetails> buildDetails = new ArrayList<>();
    buildDetails.add(
        BuildDetails.Builder.aBuildDetails().withArtifactPath("artifactDirectory/artifactPathFilter.exe.temp").build());
    doReturn(buildDetails).when(artifactoryClient).getArtifactList(any(), any(), any(), anyInt());

    List<BuildDetails> result = artifactoryNgService.getArtifactList(
        artifactoryConfigRequest, "repoName", null, 10, "artifactPathFilter.*", "artifactDirectory");

    verify(artifactoryClient, times(1)).getArtifactList(any(), any(), any(), anyInt());
    assertThat(result).isEqualTo(buildDetails);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetRepositorie() {
    Map<String, String> repositories = Collections.singletonMap("repo", "repo");
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder().build();
    doReturn(repositories).when(artifactoryClient).getRepositories(any(), any());

    Map<String, String> result = artifactoryNgService.getRepositories(artifactoryConfigRequest, "docker");

    assertThat(result).isEqualTo(repositories);

    Map<String, String> resultmaven = artifactoryNgService.getRepositories(artifactoryConfigRequest, "maven");

    assertThat(resultmaven).isEqualTo(repositories);

    Map<String, String> resultgeneric = artifactoryNgService.getRepositories(artifactoryConfigRequest, "generic");

    assertThat(resultgeneric).isEqualTo(repositories);
    verify(artifactoryClient, times(3)).getRepositories(any(), any());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testaGetLatestArtifact() {
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder().build();
    List<BuildDetails> buildDetails = new ArrayList<>();
    buildDetails.add(
        BuildDetails.Builder.aBuildDetails().withArtifactPath("artifactDirectory/artifactPath.exe.temp").build());
    doReturn(buildDetails).when(artifactoryClient).getArtifactList(any(), any(), any(), anyInt());

    assertThat(artifactoryNgService.getLatestArtifact(
                   artifactoryConfigRequest, "repoName", "artifactDirectory", "(artifactPath.exe.temp)", "", 10, null))
        .isEqualTo(
            buildDetails.stream().sorted(new BuildDetailsComparatorDescending()).collect(Collectors.toList()).get(0));

    assertThatThrownBy(
        () -> artifactoryNgService.getLatestArtifact(artifactoryConfigRequest, "repoName", "", "", "", 10, null))
        .hasMessage("Please check ArtifactPath/ArtifactPathFilter field in Artifactory artifact configuration.");
    BuildDetails expectedresult = BuildDetails.Builder.aBuildDetails().withArtifactPath("artifactPath").build();
    BuildDetails actualresult =
        artifactoryNgService.getLatestArtifact(artifactoryConfigRequest, "repoName", "", "", "artifactpath", 10, null);

    assertThat(actualresult).isEqualTo(expectedresult);
    String artifactDirectory = "/artifactDirectory/";
    String artifactPathFilter = "/artifactPathFilter/";
    String filePath = Paths.get(artifactDirectory, artifactPathFilter).toString();
    buildDetails = Collections.emptyList();
    doReturn(buildDetails).when(artifactoryClient).getArtifactList(any(), any(), any(), anyInt());
    assertThatThrownBy(()
                           -> artifactoryNgService.getLatestArtifact(
                               artifactoryConfigRequest, "repoName", artifactDirectory, "", "artifactpath", 10, null))
        .hasMessage(
            "Please check artifactPath or artifactDirectory or repository field in Artifactory artifact configuration.");

    assertThatThrownBy(()
                           -> artifactoryNgService.getLatestArtifact(artifactoryConfigRequest, "repoName",
                               artifactDirectory, artifactPathFilter, "artifact", 10, null))
        .hasMessage(
            "Please check artifactPathFilter or artifactDirectory or repository field in Artifactory artifact .");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLatestArtifactFixedValue_SameFileNameWithMultipleSuffixes() {
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder().build();
    List<BuildDetails> buildDetails1 = new ArrayList<>();
    buildDetails1.add(BuildDetails.Builder.aBuildDetails()
                          .withArtifactPath("artifactDirectory/subfolder/artifactPath.exe.temp")
                          .build());
    buildDetails1.add(BuildDetails.Builder.aBuildDetails()
                          .withArtifactPath("artifactDirectory/subfolder/artifactPath.exe.hash")
                          .build());
    doReturn(buildDetails1).when(artifactoryClient).getArtifactList(any(), anyString(), anyString(), anyInt());

    assertThat(artifactoryNgService.getLatestArtifact(artifactoryConfigRequest, "repoName",
                   "artifactDirectory/subfolder", null, "artifactPath.exe.", 10, null))
        .isEqualTo(
            buildDetails1.stream().sorted(new BuildDetailsComparatorDescending()).collect(Collectors.toList()).get(0));
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testGetLatestArtifact_RecursiveRegex() {
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder().build();
    List<BuildDetails> buildDetails1 = new ArrayList<>();
    buildDetails1.add(BuildDetails.Builder.aBuildDetails().withArtifactPath("A/B/C.zip").build());
    buildDetails1.add(BuildDetails.Builder.aBuildDetails().withArtifactPath("A/B/C1.zip").build());
    doReturn(List.of()).when(artifactoryClient).getArtifactList(any(), anyString(), eq("A/*/*/B/C.zip"), anyInt());
    doReturn(List.of()).when(artifactoryClient).getArtifactList(any(), anyString(), eq("A/B/*/C.zip"), anyInt());
    doReturn(buildDetails1).when(artifactoryClient).getArtifactList(any(), anyString(), eq("A/B/C.zip"), anyInt());

    assertThat(artifactoryNgService.getLatestArtifact(
                   artifactoryConfigRequest, "repoName", "A/*/*", null, "B/C.zip", 10, null))
        .isEqualTo(
            buildDetails1.stream().sorted(new BuildDetailsComparatorDescending()).collect(Collectors.toList()).get(0));
    assertThat(
        artifactoryNgService.getLatestArtifact(artifactoryConfigRequest, "repoName", "A/B/*", null, "C.zip", 10, null))
        .isEqualTo(
            buildDetails1.stream().sorted(new BuildDetailsComparatorDescending()).collect(Collectors.toList()).get(0));
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLatestArtifactFixedValue() {
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder().build();
    List<BuildDetails> buildDetails2 = new ArrayList<>();
    buildDetails2.add(BuildDetails.Builder.aBuildDetails()
                          .withArtifactPath("artifactDirectory/subfolder/artifactPath.exe.temp")
                          .build());
    buildDetails2.add(
        BuildDetails.Builder.aBuildDetails().withArtifactPath("artifactDirectory/subfolder/artifactPath.exe").build());
    buildDetails2.add(BuildDetails.Builder.aBuildDetails()
                          .withArtifactPath("artifactDirectory/subfolder/artifactPath.exe.hash")
                          .build());
    doReturn(buildDetails2).when(artifactoryClient).getArtifactList(any(), anyString(), anyString(), anyInt());

    assertThat(artifactoryNgService.getLatestArtifact(artifactoryConfigRequest, "repoName",
                   "artifactDirectory/subfolder", "", "artifactPath.exe", 10, null))
        .isEqualTo(
            buildDetails2.stream().sorted(new BuildDetailsComparatorDescending()).collect(Collectors.toList()).get(1));

    assertThat(artifactoryNgService.getLatestArtifact(artifactoryConfigRequest, "repoName",
                   "artifactDirectory/subfolder", "", "artifactPath.exe.hash", 10, null))
        .isEqualTo(
            buildDetails2.stream().sorted(new BuildDetailsComparatorDescending()).collect(Collectors.toList()).get(2));
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLatestArtifactRegex_SameFileNameWithMultipleSuffixes() {
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder().build();
    List<BuildDetails> buildDetails4 = new ArrayList<>();
    buildDetails4.add(BuildDetails.Builder.aBuildDetails()
                          .withArtifactPath("artifactDirectory/subfolder/artifactPath.exe.temp")
                          .build());
    buildDetails4.add(
        BuildDetails.Builder.aBuildDetails().withArtifactPath("artifactDirectory/subfolder/artifactPath.exe").build());
    buildDetails4.add(BuildDetails.Builder.aBuildDetails()
                          .withArtifactPath("artifactDirectory/subfolder/artifactPath.exe.hash")
                          .build());
    doReturn(buildDetails4).when(artifactoryClient).getArtifactList(any(), anyString(), anyString(), anyInt());

    BuildDetails result4 = artifactoryNgService.getLatestArtifact(
        artifactoryConfigRequest, "repoName", "artifactDirectory/subfolder", "[a-zA-Z.]+(exe).*", "", 10, null);
    assertThat(result4.getArtifactPath()).endsWith("artifactPath.exe.temp");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLatestArtifactRegex_withComplexDirectoryPath() {
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder().build();
    List<BuildDetails> buildDetails5 = new ArrayList<>();
    buildDetails5.add(BuildDetails.Builder.aBuildDetails()
                          .withArtifactPath("artifactDirectory/subfolder/artifactPath.exe.temp")
                          .build());
    buildDetails5.add(
        BuildDetails.Builder.aBuildDetails().withArtifactPath("artifactDirectory/subfolder/artifactPath.exe").build());
    buildDetails5.add(BuildDetails.Builder.aBuildDetails()
                          .withArtifactPath("artifactDirectory/subfolder/artifactPath.exe.hash")
                          .build());
    doReturn(buildDetails5)
        .when(artifactoryClient)
        .getArtifactList(any(), anyString(), eq("artifactDirectory/subfolder/*"), anyInt());

    BuildDetails result5 = artifactoryNgService.getLatestArtifact(
        artifactoryConfigRequest, "repoName", "artifactDirectory/subfolder", "[a-zA-Z.]+(exe)", "", 10, null);
    assertThat(result5.getArtifactPath()).endsWith("artifactPath.exe");
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testGetLatestArtifactRegex_WithFilteringOnArtifactorySide() {
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder().build();
    List<BuildDetails> buildDetails5 = new ArrayList<>();
    buildDetails5.add(BuildDetails.Builder.aBuildDetails()
                          .withArtifactPath("artifactDirectory/subfolder/www-202345457459.tar.gz")
                          .build());
    doReturn(buildDetails5).when(artifactoryClient).getArtifactList(any(), anyString(), anyString(), anyInt());

    BuildDetails result5 = artifactoryNgService.getLatestArtifact(
        artifactoryConfigRequest, "repoName", "artifactDirectory/subfolder", "www-*.tar.gz", "", 10, null);
    assertThat(result5.getArtifactPath()).endsWith("www-202345457459.tar.gz");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLatestArtifactRegex_withSimpleDirectoryPath() {
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder().build();
    List<BuildDetails> buildDetails6 = new ArrayList<>();
    buildDetails6.add(BuildDetails.Builder.aBuildDetails().withArtifactPath("artifactPath.exe.temp").build());
    buildDetails6.add(BuildDetails.Builder.aBuildDetails().withArtifactPath("artifactPath.exe").build());
    buildDetails6.add(BuildDetails.Builder.aBuildDetails().withArtifactPath("artifactPath.exe.hash").build());
    buildDetails6.add(BuildDetails.Builder.aBuildDetails().withArtifactPath("artifactPath.bin.asd").build());
    buildDetails6.add(BuildDetails.Builder.aBuildDetails().withArtifactPath("artifactPath.exe.asd").build());
    buildDetails6.add(BuildDetails.Builder.aBuildDetails().withArtifactPath("artifactPath.zip").build());
    doReturn(buildDetails6).when(artifactoryClient).getArtifactList(any(), anyString(), eq("/*"), anyInt());

    BuildDetails result6 = artifactoryNgService.getLatestArtifact(
        artifactoryConfigRequest, "repoName", "/", "[a-zA-Z.]+(exe)", "", 10, null);
    assertThat(result6.getArtifactPath()).endsWith("artifactPath.exe");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLatestArtifactRegex_withoutDirectoryPath() {
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder().build();
    List<BuildDetails> buildDetails7 = new ArrayList<>();
    buildDetails7.add(BuildDetails.Builder.aBuildDetails().withArtifactPath("artifactPath.exe.temp").build());
    buildDetails7.add(BuildDetails.Builder.aBuildDetails().withArtifactPath("artifactPath.exe").build());
    buildDetails7.add(BuildDetails.Builder.aBuildDetails().withArtifactPath("artifactPath.exe.hash").build());
    doReturn(buildDetails7).when(artifactoryClient).getArtifactList(any(), anyString(), eq("*"), anyInt());

    BuildDetails result7 = artifactoryNgService.getLatestArtifact(
        artifactoryConfigRequest, "repoName", "", "[a-zA-Z.]+(exe)", "", 10, null);
    assertThat(result7.getArtifactPath()).endsWith("artifactPath.exe");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLatestArtifactRegex_withInvalidRegex() {
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder().build();
    assertThatThrownBy(()
                           -> artifactoryNgService.getLatestArtifact(artifactoryConfigRequest, "repoName",
                               "artifactDirectory/subfolder", "[a-zA-Z.]+(exe)[.a-zA-Z]+)", "", 10, null))
        .isInstanceOf(WingsException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(ArtifactoryRegistryException.class);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetLatestArtifact_ArtifactFilterArtifactPathFixed() {
    doReturn(getBuildDetailsList().get(0))
        .when(artifactoryClient)
        .getBuildDetailWithFullPath(any(), anyString(), anyString());

    BuildDetails result7 = artifactoryNgService.getLatestArtifact(
        ARTIFACTORY_CONFIG_REQUEST, REPO_NAME, null, null, FULL_ARTIFACT_PATH, MAX_VERSION, FILTER);
    verify(artifactoryClient).getBuildDetailWithFullPath(ARTIFACTORY_CONFIG_REQUEST, REPO_NAME, FULL_ARTIFACT_PATH);
    assertThat(result7.getArtifactPath()).isEqualTo(FULL_ARTIFACT_PATH);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetLatestArtifact_ArtifactFilterArtifactPathRegex() {
    doReturn(getBuildDetailsList()).when(artifactoryClient).getArtifactList(any(), any(), any(), anyInt());

    BuildDetails result7 = artifactoryNgService.getLatestArtifact(
        ARTIFACTORY_CONFIG_REQUEST, REPO_NAME, null, ARTIFACT_PATH_REGEX, null, MAX_VERSION, FILTER);
    verify(artifactoryClient).getArtifactList(ARTIFACTORY_CONFIG_REQUEST, REPO_NAME, FILTER, MAX_VERSION);
    assertThat(result7.getArtifactPath()).isEqualTo(FULL_ARTIFACT_PATH_2);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetLatestArtifact_ArtifactFilterArtifactPathFixed_EmptyList() {
    doReturn(null).when(artifactoryClient).getBuildDetailWithFullPath(any(), anyString(), anyString());

    assertThatThrownBy(()
                           -> artifactoryNgService.getLatestArtifact(ARTIFACTORY_CONFIG_REQUEST, REPO_NAME, null, null,
                               FULL_ARTIFACT_PATH, MAX_VERSION, FILTER))
        .isInstanceOf(HintException.class)
        .hasMessage("Please check artifactPath or artifactFilter or repository field in Artifactory artifact.")
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(ArtifactoryRegistryException.class);

    verify(artifactoryClient).getBuildDetailWithFullPath(ARTIFACTORY_CONFIG_REQUEST, REPO_NAME, FULL_ARTIFACT_PATH);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetLatestArtifact_ArtifactFilterArtifactPathRegex_EmptyList() {
    doReturn(new ArrayList<>()).when(artifactoryClient).getArtifactList(any(), any(), any(), anyInt());
    assertThatThrownBy(()
                           -> artifactoryNgService.getLatestArtifact(ARTIFACTORY_CONFIG_REQUEST, REPO_NAME, null,
                               ARTIFACT_PATH_REGEX, null, MAX_VERSION, FILTER))
        .isInstanceOf(HintException.class)
        .hasMessage("Please check artifactPathFilter or artifactFilter or repository field in Artifactory artifact.")
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(ArtifactoryRegistryException.class);

    verify(artifactoryClient).getArtifactList(ARTIFACTORY_CONFIG_REQUEST, REPO_NAME, FILTER, MAX_VERSION);
  }

  private List<BuildDetails> getBuildDetailsList() {
    List<BuildDetails> buildDetails7 = new ArrayList<>();
    buildDetails7.add(BuildDetails.Builder.aBuildDetails().withArtifactPath(FULL_ARTIFACT_PATH).build());
    buildDetails7.add(BuildDetails.Builder.aBuildDetails().withArtifactPath(FULL_ARTIFACT_PATH_2).build());
    buildDetails7.add(BuildDetails.Builder.aBuildDetails().withArtifactPath("abc/artifactPath.exe.hash").build());
    return buildDetails7;
  }
}
