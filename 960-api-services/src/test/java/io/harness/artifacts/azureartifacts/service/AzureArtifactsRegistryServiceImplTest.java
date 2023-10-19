/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.azureartifacts.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.VINIT_KUMAR;
import static io.harness.rule.OwnerRule.VITALIE;
import static io.harness.rule.OwnerRule.VLICA;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.azureartifacts.beans.AzureArtifactsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.exception.HintException;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import software.wings.helpers.ext.azure.devops.AzureArtifactsFeed;
import software.wings.helpers.ext.azure.devops.AzureArtifactsFeeds;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackage;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackageFile;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackageFileInfo;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackageVersion;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackageVersions;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackages;
import software.wings.helpers.ext.azure.devops.AzureArtifactsProtocolMetadata;
import software.wings.helpers.ext.azure.devops.AzureArtifactsProtocolMetadataData;
import software.wings.helpers.ext.azure.devops.AzureArtifactsRestClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import okhttp3.ResponseBody;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(CDP)
public class AzureArtifactsRegistryServiceImplTest extends CategoryTest {
  private static final String ARTIFACT_FILE_CONTENT = "artifact-file-content";

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock AzureArtifactsDownloadHelper azureArtifactsDownloadHelper;

  @InjectMocks private AzureArtifactsRegistryServiceImpl azureArtifactsRegistryServiceImpl;

  String feed = "testFeed";
  String project = "testProject";
  String nugetPackageType = "nuget";
  String mavenPackageType = "maven";
  String nugetPackageName = "testPackageName";
  String mavenPackageName = "test.package.name:artifact";
  String version = "testVersion.1.0";
  AzureArtifactsInternalConfig azureArtifactsInternalConfig =
      AzureArtifactsInternalConfig.builder().registryUrl("testURL").token("testToken").build();

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testListFilesOfAzureMavenArtifact() throws Exception {
    AzureArtifactsPackageVersion azureArtifactsPackageVersion = createArtifactsPackageVersion();

    when(azureArtifactsDownloadHelper.getPackageId(any(), any(), any(), any(), any())).thenReturn("testPackageId");
    when(azureArtifactsDownloadHelper.getPackageVersionId(any(), any(), any(), any(), any(), any()))
        .thenReturn("testPackageVersionId");
    when(azureArtifactsDownloadHelper.getPackageVersion(any(), any(), any(), any()))
        .thenReturn(azureArtifactsPackageVersion);

    List<AzureArtifactsPackageFileInfo> listPackageFiles = azureArtifactsRegistryServiceImpl.listPackageFiles(
        azureArtifactsInternalConfig, project, feed, mavenPackageType, mavenPackageName, version);

    assertThat(listPackageFiles).size().isEqualTo(1);
    assertThat(listPackageFiles.get(0).getName()).isEqualTo("artifact.war");
    assertThat(listPackageFiles.get(0).getSize()).isEqualTo(12345);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testListFilesOfAzureNugetArtifact() throws Exception {
    when(azureArtifactsDownloadHelper.downloadArtifactByUrl(any(), any()))
        .thenReturn(new ByteArrayInputStream(ARTIFACT_FILE_CONTENT.getBytes()));
    when(azureArtifactsDownloadHelper.getNuGetDownloadUrl(any(), any(), any(), any(), any()))
        .thenReturn("testNugetUrl");

    List<AzureArtifactsPackageFileInfo> listPackageFiles = azureArtifactsRegistryServiceImpl.listPackageFiles(
        azureArtifactsInternalConfig, project, feed, nugetPackageType, nugetPackageName, version);

    assertThat(listPackageFiles).size().isEqualTo(1);
    assertThat(listPackageFiles.get(0).getName()).isEqualTo("testPackageName");
    assertThat(listPackageFiles.get(0).getSize()).isEqualTo(21L);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testDownloadAzureMavenArtifact() throws Exception {
    AzureArtifactsPackageVersion azureArtifactsPackageVersion = createArtifactsPackageVersion();

    when(azureArtifactsDownloadHelper.getPackageId(any(), any(), any(), any(), any())).thenReturn("testPackageId");
    when(azureArtifactsDownloadHelper.getPackageVersionId(any(), any(), any(), any(), any(), any()))
        .thenReturn("testPackageVersionId");
    when(azureArtifactsDownloadHelper.getPackageVersion(any(), any(), any(), any()))
        .thenReturn(azureArtifactsPackageVersion);
    when(azureArtifactsDownloadHelper.downloadArtifactByUrl(any(), any()))
        .thenReturn(new ByteArrayInputStream(ARTIFACT_FILE_CONTENT.getBytes()));
    when(azureArtifactsDownloadHelper.getMavenDownloadUrl(any(), any(), any(), any(), any(), any()))
        .thenReturn("testMavenUrl");

    Pair<String, InputStream> pair = azureArtifactsRegistryServiceImpl.downloadArtifact(
        azureArtifactsInternalConfig, project, feed, mavenPackageType, mavenPackageName, version);

    assertThat(pair).isNotNull();
    assertThat(IOUtils.toString(pair.getRight(), StandardCharsets.UTF_8.name())).isEqualTo("artifact-file-content");
    assertThat(pair.getKey()).isEqualTo("artifact.war");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testDownloadAzureNugetArtifact() throws Exception {
    when(azureArtifactsDownloadHelper.downloadArtifactByUrl(any(), any()))
        .thenReturn(new ByteArrayInputStream(ARTIFACT_FILE_CONTENT.getBytes()));
    when(azureArtifactsDownloadHelper.getMavenDownloadUrl(any(), any(), any(), any(), any(), any()))
        .thenReturn("testMavenUrl");

    Pair<String, InputStream> pair = azureArtifactsRegistryServiceImpl.downloadArtifact(
        azureArtifactsInternalConfig, project, feed, nugetPackageType, nugetPackageName, version);

    assertThat(pair).isNotNull();
    assertThat(IOUtils.toString(pair.getRight(), StandardCharsets.UTF_8.name())).isEqualTo("artifact-file-content");
    assertThat(pair.getKey()).isEqualTo(nugetPackageName);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testDownloadAzureMavenArtifactThrowsException() throws Exception {
    when(azureArtifactsDownloadHelper.downloadArtifactByUrl(any(), any())).thenThrow(new RuntimeException());
    when(azureArtifactsDownloadHelper.getNuGetDownloadUrl(any(), any(), any(), any(), any()))
        .thenReturn("testNugetUrl");

    assertThatThrownBy(()
                           -> azureArtifactsRegistryServiceImpl.downloadArtifact(azureArtifactsInternalConfig, project,
                               feed, nugetPackageType, nugetPackageName, version))
        .isInstanceOf(InvalidArtifactServerException.class)
        .hasMessageContaining("Failed to download azure artifact");
  }
  @Test
  @Owner(developers = VINIT_KUMAR)
  @Category(UnitTests.class)
  public void testListPackageVersionsWithFeedList() throws Exception {
    AzureArtifactsInternalConfig azureArtifactsInternalConfig1 =
        AzureArtifactsInternalConfig.builder().registryUrl("https://dev.azure.com/org").token("testToken").build();

    AzureArtifactsRegistryServiceImpl azureArtifactsRegistryServiceImpl1 = spy(azureArtifactsRegistryServiceImpl);
    doThrow(new HintException("Failed to get the Azure Feeds list."))
        .when(azureArtifactsRegistryServiceImpl1)
        .listFeeds(any(), any());

    assertThatThrownBy(()
                           -> azureArtifactsRegistryServiceImpl1.listPackageVersions(
                               azureArtifactsInternalConfig1, "maven", mavenPackageName, "abc", feed, "project"))
        .isInstanceOf(HintException.class)
        .hasMessage("Failed to get versions. Could not fetch feeds");
  }

  @Test
  @Owner(developers = VINIT_KUMAR)
  @Category(UnitTests.class)
  public void testListPackageVersionsWithEmptyFeedList() throws Exception {
    AzureArtifactsInternalConfig azureArtifactsInternalConfig1 =
        AzureArtifactsInternalConfig.builder().registryUrl("https://dev.azure.com/org").token("testToken").build();
    AzureArtifactsRegistryServiceImpl azureArtifactsRegistryServiceImpl1 = spy(azureArtifactsRegistryServiceImpl);

    List<AzureArtifactsFeed> emptyFeeds = Collections.emptyList();
    doReturn(emptyFeeds).when(azureArtifactsRegistryServiceImpl1).listFeeds(azureArtifactsInternalConfig1, "myProject");

    assertThatThrownBy(()
                           -> azureArtifactsRegistryServiceImpl1.listPackageVersions(
                               azureArtifactsInternalConfig1, "maven", "abc", "1.0.0", "testFeed", "myProject"))
        .isInstanceOf(HintException.class)
        .hasMessage("Failed to get versions. Feeds not found");
  }

  @Test
  @Owner(developers = VINIT_KUMAR)
  @Category(UnitTests.class)
  public void testListPackageVersionsWithNullFeedID() throws Exception {
    AzureArtifactsInternalConfig azureArtifactsInternalConfig1 =
        AzureArtifactsInternalConfig.builder().registryUrl("https://dev.azure.com/org").token("testToken").build();
    AzureArtifactsRegistryServiceImpl azureArtifactsRegistryServiceImpl1 = spy(azureArtifactsRegistryServiceImpl);

    AzureArtifactsFeed azureArtifactsFeed = new AzureArtifactsFeed();
    azureArtifactsFeed.setName("testFeed");
    azureArtifactsFeed.setProject(null);
    azureArtifactsFeed.setFullyQualifiedName("feed5");

    doReturn(Collections.singletonList(azureArtifactsFeed))
        .when(azureArtifactsRegistryServiceImpl1)
        .listFeeds(azureArtifactsInternalConfig1, "myProject");

    assertThatThrownBy(()
                           -> azureArtifactsRegistryServiceImpl1.listPackageVersions(
                               azureArtifactsInternalConfig1, "maven", "abc", "1.0.0", "myFeed", "myProject"))
        .isInstanceOf(HintException.class)
        .hasMessage("Failed to get versions. Feed [myFeed] not found in feeds");
  }

  @Test
  @Owner(developers = VINIT_KUMAR)
  @Category(UnitTests.class)
  public void testListPackageVersionsWithPackageList() throws Exception {
    AzureArtifactsInternalConfig azureArtifactsInternalConfig1 =
        AzureArtifactsInternalConfig.builder().registryUrl("https://dev.azure.com/org").token("testToken").build();
    AzureArtifactsRegistryServiceImpl azureArtifactsRegistryServiceImpl1 = spy(azureArtifactsRegistryServiceImpl);

    AzureArtifactsFeed azureArtifactsFeed = new AzureArtifactsFeed();
    azureArtifactsFeed.setId("f5");
    azureArtifactsFeed.setName("testFeed");
    azureArtifactsFeed.setProject(null);
    azureArtifactsFeed.setFullyQualifiedName("feed5");

    doReturn(Collections.singletonList(azureArtifactsFeed))
        .when(azureArtifactsRegistryServiceImpl1)
        .listFeeds(azureArtifactsInternalConfig1, "project");

    doThrow(new HintException("Failed to get the Azure Packages list."))
        .when(azureArtifactsRegistryServiceImpl1)
        .listPackages(any(), any(), any(), any());

    assertThatThrownBy(()
                           -> azureArtifactsRegistryServiceImpl1.listPackageVersions(
                               azureArtifactsInternalConfig1, "maven", mavenPackageName, "abc", feed, "project"))
        .isInstanceOf(HintException.class)
        .hasMessage("Failed to get versions. Could not fetch packages");
  }
  @Test
  @Owner(developers = VINIT_KUMAR)
  @Category(UnitTests.class)
  public void testListPackageVersionsWithEmptyPackageList() throws Exception {
    AzureArtifactsInternalConfig azureArtifactsInternalConfig1 =
        AzureArtifactsInternalConfig.builder().registryUrl("https://dev.azure.com/org").token("testToken").build();
    AzureArtifactsRegistryServiceImpl azureArtifactsRegistryServiceImpl1 = spy(azureArtifactsRegistryServiceImpl);

    AzureArtifactsFeed azureArtifactsFeed = new AzureArtifactsFeed();
    azureArtifactsFeed.setId("f5");
    azureArtifactsFeed.setName("testFeed");
    azureArtifactsFeed.setProject(null);
    azureArtifactsFeed.setFullyQualifiedName("feed5");

    doReturn(Collections.singletonList(azureArtifactsFeed))
        .when(azureArtifactsRegistryServiceImpl1)
        .listFeeds(azureArtifactsInternalConfig1, "myProject");

    List<AzureArtifactsPackage> emptyPackages = Collections.emptyList();
    doReturn(emptyPackages)
        .when(azureArtifactsRegistryServiceImpl1)
        .listPackages(azureArtifactsInternalConfig1, "myProject", "testFeed", "maven");

    assertThatThrownBy(()
                           -> azureArtifactsRegistryServiceImpl1.listPackageVersions(
                               azureArtifactsInternalConfig1, "maven", "abc", "1.0.0", "testFeed", "myProject"))
        .isInstanceOf(HintException.class)
        .hasMessage("Failed to get versions. Packages not found");
  }
  @Test
  @Owner(developers = VINIT_KUMAR)
  @Category(UnitTests.class)
  public void testListPackageVersionsWithNullPackageID() throws Exception {
    AzureArtifactsInternalConfig azureArtifactsInternalConfig1 =
        AzureArtifactsInternalConfig.builder().registryUrl("https://dev.azure.com/org").token("testToken").build();
    AzureArtifactsRegistryServiceImpl azureArtifactsRegistryServiceImpl1 = spy(azureArtifactsRegistryServiceImpl);

    AzureArtifactsFeed azureArtifactsFeed = new AzureArtifactsFeed();
    azureArtifactsFeed.setId("f5");
    azureArtifactsFeed.setName("testFeed");
    azureArtifactsFeed.setProject(null);
    azureArtifactsFeed.setFullyQualifiedName("feed5");
    AzureArtifactsFeeds azureArtifactsFeeds = new AzureArtifactsFeeds();
    azureArtifactsFeeds.setValue(Collections.singletonList(azureArtifactsFeed));

    AzureArtifactsPackage azureArtifactsPackage = new AzureArtifactsPackage();
    azureArtifactsPackage.setName(mavenPackageName);
    azureArtifactsPackage.setProtocolType("protocol");
    AzureArtifactsPackages azureArtifactsPackages = new AzureArtifactsPackages();
    azureArtifactsPackages.setValue(Collections.singletonList(azureArtifactsPackage));

    doReturn(Collections.singletonList(azureArtifactsFeed))
        .when(azureArtifactsRegistryServiceImpl1)
        .listFeeds(azureArtifactsInternalConfig1, "myProject");

    doReturn(Collections.singletonList(azureArtifactsPackage))
        .when(azureArtifactsRegistryServiceImpl1)
        .listPackages(azureArtifactsInternalConfig1, "myProject", "testFeed", "maven");

    assertThatThrownBy(()
                           -> azureArtifactsRegistryServiceImpl1.listPackageVersions(
                               azureArtifactsInternalConfig1, "maven", "abc", "1.0.0", "testFeed", "myProject"))
        .isInstanceOf(HintException.class)
        .hasMessage("Failed to get versions. Package [abc] not found in Packages");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testgetLastSuccessfulBuildFromRegexInvalidRegex() throws Exception {
    AzureArtifactsInternalConfig azureArtifactsInternalConfig1 =
        AzureArtifactsInternalConfig.builder().registryUrl("https://dev.azure.com/org").token("testToken").build();
    AzureArtifactsRestClient azureArtifactsRestClient = mock(AzureArtifactsRestClient.class);

    AzureArtifactsFeed azureArtifactsFeed = new AzureArtifactsFeed();
    azureArtifactsFeed.setId("f5");
    azureArtifactsFeed.setName("testFeed");
    azureArtifactsFeed.setProject(null);
    azureArtifactsFeed.setFullyQualifiedName("feed5");

    AzureArtifactsPackage azureArtifactsPackage = new AzureArtifactsPackage();
    azureArtifactsPackage.setName(mavenPackageName);
    azureArtifactsPackage.setId("id");
    azureArtifactsPackage.setProtocolType("protocol");

    List<AzureArtifactsPackageVersion> azureArtifactsPackageVersion = new ArrayList<>();
    AzureArtifactsPackageVersion azureArtifactsPackageVersion1 = new AzureArtifactsPackageVersion();
    azureArtifactsPackageVersion1.setVersion("version");
    azureArtifactsPackageVersion.add(azureArtifactsPackageVersion1);

    AzureArtifactsPackageVersions azureArtifactsPackageVersions = new AzureArtifactsPackageVersions();
    azureArtifactsPackageVersions.setCount(1);
    azureArtifactsPackageVersions.setValue(azureArtifactsPackageVersion);

    AzureArtifactsRegistryServiceImpl azureArtifactsRegistryServiceImpl1 = spy(azureArtifactsRegistryServiceImpl);

    doReturn(Collections.singletonList(azureArtifactsFeed))
        .when(azureArtifactsRegistryServiceImpl1)
        .listFeeds(any(), any());
    doReturn(Collections.singletonList(azureArtifactsPackage))
        .when(azureArtifactsRegistryServiceImpl1)
        .listPackages(azureArtifactsInternalConfig1, "project", feed, mavenPackageType);

    try (MockedStatic<AzureArtifactsRegistryServiceImpl> mockedStatic =
             mockStatic(AzureArtifactsRegistryServiceImpl.class)) {
      mockedStatic.when(() -> AzureArtifactsRegistryServiceImpl.getAzureArtifactsRestClient(any(), any()))
          .thenReturn(azureArtifactsRestClient);

      Call<ResponseDTO<AzureArtifactsPackageVersions>> callRequest = PowerMockito.mock(Call.class);
      doReturn(callRequest).when(callRequest).clone();
      doReturn(callRequest).when(azureArtifactsRestClient).listPackageVersions(any(), any(), any());
      doReturn(Response.success(azureArtifactsPackageVersions)).when(callRequest).execute();

      assertThatThrownBy(
          ()
              -> azureArtifactsRegistryServiceImpl1.getLastSuccessfulBuildFromRegex(
                  azureArtifactsInternalConfig1, "maven", mavenPackageName, "abc", feed, "project", "abc"))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessage("No builds found matching project= project, feed= testFeed , packageId= id , versionRegex = abc");
    }
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testListFeeds() throws Exception {
    AzureArtifactsInternalConfig azureArtifactsInternalConfig =
        AzureArtifactsInternalConfig.builder().registryUrl("https://dev.azure.com/org").token("testToken").build();
    AzureArtifactsRestClient azureArtifactsRestClient = mock(AzureArtifactsRestClient.class);

    AzureArtifactsFeed azureArtifactsFeed = new AzureArtifactsFeed();
    azureArtifactsFeed.setName("azureArtifactsFeed");

    AzureArtifactsFeeds azureArtifactsFeeds = new AzureArtifactsFeeds();
    azureArtifactsFeeds.setValue(Arrays.asList(azureArtifactsFeed));

    try (MockedStatic<AzureArtifactsRegistryServiceImpl> mockedStatic =
             mockStatic(AzureArtifactsRegistryServiceImpl.class)) {
      mockedStatic.when(() -> AzureArtifactsRegistryServiceImpl.getAzureArtifactsRestClient(any(), any()))
          .thenReturn(azureArtifactsRestClient);
      mockedStatic.when(() -> AzureArtifactsRegistryServiceImpl.getAuthHeader(any())).thenReturn("Header: Auth");

      Call<ResponseDTO<AzureArtifactsPackageVersions>> callRequest = PowerMockito.mock(Call.class);
      doReturn(callRequest).when(callRequest).clone();
      doReturn(callRequest).when(azureArtifactsRestClient).listFeeds(anyString());
      doReturn(Response.success(azureArtifactsFeeds)).when(callRequest).execute();

      List<AzureArtifactsFeed> result =
          azureArtifactsRegistryServiceImpl.listFeeds(azureArtifactsInternalConfig, "project");
      assertThat(result.get(0).getName()).isEqualTo(azureArtifactsFeed.getName());
    }
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testListFeeds_Fails() throws Exception {
    AzureArtifactsInternalConfig azureArtifactsInternalConfig =
        AzureArtifactsInternalConfig.builder().registryUrl("https://dev.azure.com/org").token("testToken").build();
    AzureArtifactsRestClient azureArtifactsRestClient = mock(AzureArtifactsRestClient.class);

    AzureArtifactsFeed azureArtifactsFeed = new AzureArtifactsFeed();
    azureArtifactsFeed.setName("azureArtifactsFeed");

    AzureArtifactsFeeds azureArtifactsFeeds = new AzureArtifactsFeeds();
    azureArtifactsFeeds.setValue(Arrays.asList(azureArtifactsFeed));

    try (MockedStatic<AzureArtifactsRegistryServiceImpl> mockedStatic =
             mockStatic(AzureArtifactsRegistryServiceImpl.class)) {
      mockedStatic.when(() -> AzureArtifactsRegistryServiceImpl.getAzureArtifactsRestClient(any(), any()))
          .thenReturn(azureArtifactsRestClient);
      mockedStatic.when(() -> AzureArtifactsRegistryServiceImpl.getAuthHeader(any())).thenReturn("Header: Auth");

      Call<ResponseDTO<AzureArtifactsPackageVersions>> callRequest = PowerMockito.mock(Call.class);
      doReturn(callRequest).when(callRequest).clone();
      doReturn(callRequest).when(azureArtifactsRestClient).listFeeds(anyString());
      doReturn(Response.error(404, mock(ResponseBody.class))).when(callRequest).execute();

      assertThatThrownBy(() -> azureArtifactsRegistryServiceImpl.listFeeds(azureArtifactsInternalConfig, "project"))
          .isInstanceOf(HintException.class)
          .hasMessage("Failed to get the Azure Feeds list.");
    }
  }

  private AzureArtifactsPackageVersion createArtifactsPackageVersion() {
    AzureArtifactsProtocolMetadataData data = new AzureArtifactsProtocolMetadataData();
    data.setSize(12345);
    AzureArtifactsProtocolMetadata azureArtifactsProtocolMetadata = new AzureArtifactsProtocolMetadata();
    azureArtifactsProtocolMetadata.setData(data);
    AzureArtifactsPackageFile azureArtifactsPackageFile = new AzureArtifactsPackageFile();
    azureArtifactsPackageFile.setName("artifact.war");
    azureArtifactsPackageFile.setProtocolMetadata(azureArtifactsProtocolMetadata);

    AzureArtifactsPackageVersion azureArtifactsPackageVersion = new AzureArtifactsPackageVersion();
    azureArtifactsPackageVersion.setFiles(Collections.singletonList(azureArtifactsPackageFile));
    return azureArtifactsPackageVersion;
  }
}
