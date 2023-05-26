/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.azureartifacts.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.VLICA;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.azureartifacts.beans.AzureArtifactsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import software.wings.helpers.ext.azure.devops.AzureArtifactsFeed;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackage;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackageFile;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackageFileInfo;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackageVersion;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackageVersions;
import software.wings.helpers.ext.azure.devops.AzureArtifactsProtocolMetadata;
import software.wings.helpers.ext.azure.devops.AzureArtifactsProtocolMetadataData;
import software.wings.helpers.ext.azure.devops.AzureArtifactsRestClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
    mockStatic(AzureArtifactsRegistryServiceImpl.class);

    when(AzureArtifactsRegistryServiceImpl.getAzureArtifactsRestClient(any(), any()))
        .thenReturn(azureArtifactsRestClient);
    Call<ResponseDTO<AzureArtifactsPackageVersions>> callRequest = PowerMockito.mock(Call.class);
    doReturn(callRequest).when(azureArtifactsRestClient).listPackageVersions(any(), any(), any());
    doReturn(Response.success(azureArtifactsPackageVersions)).when(callRequest).execute();

    assertThatThrownBy(()
                           -> azureArtifactsRegistryServiceImpl1.getLastSuccessfulBuildFromRegex(
                               azureArtifactsInternalConfig1, "maven", mavenPackageName, "abc", feed, "project", "abc"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No builds found matching project= project, feed= testFeed , packageId= id , versionRegex = abc");
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
