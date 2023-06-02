/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.azure;

import static io.harness.rule.OwnerRule.GARVIT;

import static software.wings.helpers.ext.azure.devops.AzureArtifactsServiceHelper.getSubdomainUrl;
import static software.wings.helpers.ext.azure.devops.AzureArtifactsServiceHelper.validateAzureDevopsUrl;
import static software.wings.helpers.ext.azure.devops.AzureArtifactsServiceHelper.validateRawResponse;
import static software.wings.helpers.ext.azure.devops.AzureArtifactsServiceHelper.validateResponse;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.task.ListNotifyResponseData;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.AzureArtifactsArtifactStreamProtocolType;
import software.wings.beans.settings.azureartifacts.AzureArtifactsConfig;
import software.wings.beans.settings.azureartifacts.AzureArtifactsPATConfig;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.helpers.ext.azure.devops.AzureArtifactsFeed;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackage;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackageFileInfo;
import software.wings.helpers.ext.azure.devops.AzureArtifactsService;
import software.wings.helpers.ext.azure.devops.AzureDevopsProject;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.persistence.artifact.ArtifactFile;
import software.wings.utils.ArtifactType;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import okhttp3.Protocol;
import okhttp3.Request;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
@TargetModule(HarnessModule._960_API_SERVICES)
public class AzureArtifactsServiceTest extends WingsBaseTest {
  private static final String DEFAULT_AZURE_ARTIFACTS_URL_WITHOUT_SLASH = "http://localhost:9891/azureartifacts";
  private static final String DEFAULT_AZURE_ARTIFACTS_URL = DEFAULT_AZURE_ARTIFACTS_URL_WITHOUT_SLASH + "/";
  private static final String MAVEN = AzureArtifactsArtifactStreamProtocolType.maven.name();
  private static final String NUGET = AzureArtifactsArtifactStreamProtocolType.nuget.name();
  private static final String FEED = "FEED";
  private static final String PACKAGE_ID = "PACKAGE_ID";
  private static final String GROUP_ID = "GROUP_ID";
  private static final String ARTIFACT_ID = "ARTIFACT_ID";
  private static final String PACKAGE_NAME_MAVEN = "GROUP_ID:ARTIFACT_ID";
  private static final String PACKAGE_NAME_NUGET = "PACKAGE_NAME";
  private static final String FILE_ID = "FILE_ID";

  @Rule public WireMockRule wireMockRule = new WireMockRule(9891);
  @Inject @InjectMocks private AzureArtifactsService azureArtifactsService;
  @Inject private DelegateFileManager delegateFileManager;

  private AzureArtifactsConfig azureArtifactsConfigWithoutSlash =
      AzureArtifactsPATConfig.builder()
          .azureDevopsUrl(DEFAULT_AZURE_ARTIFACTS_URL_WITHOUT_SLASH)
          .pat("admin".toCharArray())
          .build();

  private AzureArtifactsConfig azureArtifactsConfig =
      AzureArtifactsPATConfig.builder().azureDevopsUrl(DEFAULT_AZURE_ARTIFACTS_URL).pat("admin".toCharArray()).build();

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldValidateArtifactServer() {
    wireMockRule.stubFor(
        get(urlEqualTo("/azureartifacts/_apis/projects?api-version=5.1"))
            .willReturn(aResponse().withStatus(200).withBody("{}").withHeader("Content-Type", "application/json")));
    assertThat(azureArtifactsService.validateArtifactServer(azureArtifactsConfig, null, false)).isTrue();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldValidateArtifactServerWithoutSlash() {
    wireMockRule.stubFor(
        get(urlEqualTo("/azureartifacts/_apis/projects?api-version=5.1"))
            .willReturn(aResponse().withStatus(200).withBody("{}").withHeader("Content-Type", "application/json")));
    assertThat(azureArtifactsService.validateArtifactServer(azureArtifactsConfigWithoutSlash, null, false)).isTrue();
  }

  @Test(expected = InvalidArtifactServerException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotValidateArtifactServer() {
    wireMockRule.stubFor(
        get(urlEqualTo("/azureartifacts/_apis/projects?api-version=5.1"))
            .willReturn(aResponse().withStatus(404).withBody("{}").withHeader("Content-Type", "application/json")));
    azureArtifactsService.validateArtifactServer(azureArtifactsConfig, null, false);
  }

  @Test(expected = InvalidArtifactServerException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotValidateArtifactServerForInvalidPAT() {
    wireMockRule.stubFor(
        get(urlEqualTo("/azureartifacts/_apis/projects?api-version=5.1"))
            .willReturn(aResponse().withStatus(203).withBody("{}").withHeader("Content-Type", "application/json")));
    azureArtifactsService.validateArtifactServer(azureArtifactsConfig, null, false);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotValidateArtifactServerForInvalidUrl() {
    wireMockRule.stubFor(
        get(urlEqualTo("/azureartifacts/_apis/projects?api-version=5.1"))
            .willReturn(aResponse().withStatus(200).withBody("{}").withHeader("Content-Type", "application/json")));
    azureArtifactsService.validateArtifactServer(azureArtifactsConfig, null, true);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldValidateArtifactSource() {
    validateArtifactSource("");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldValidateArtifactSourceWithProject() {
    validateArtifactSource("project");
  }

  private void validateArtifactSource(String project) {
    wireMockRule.stubFor(
        get(urlEqualTo(format("/azureartifacts%s/_apis/packaging/feeds/%s/packages/%s?api-version=5.1-preview.1",
                isBlank(project) ? "" : "/" + project, FEED, PACKAGE_ID)))
            .willReturn(aResponse()
                            .withStatus(200)
                            .withBody(format(
                                "{\"id\": \"%s\", \"protocolType\": \"%s\", \"name\": \"NAME\"}", PACKAGE_ID, MAVEN))
                            .withHeader("Content-Type", "application/json")));
    assertThat(azureArtifactsService.validateArtifactSource(azureArtifactsConfig, null,
                   ArtifactStreamAttributes.builder()
                       .project(project)
                       .feed(FEED)
                       .packageId(PACKAGE_ID)
                       .protocolType(MAVEN)
                       .build()))
        .isTrue();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotValidateArtifactSourceForInvalidProtocolType() {
    wireMockRule.stubFor(
        get(urlEqualTo(format(
                "/azureartifacts/_apis/packaging/feeds/%s/packages/%s?api-version=5.1-preview.1", FEED, PACKAGE_ID)))
            .willReturn(aResponse()
                            .withStatus(200)
                            .withBody(format(
                                "{\"id\": \"%s\", \"protocolType\": \"%s\", \"name\": \"NAME\"}", PACKAGE_ID, NUGET))
                            .withHeader("Content-Type", "application/json")));
    assertThat(azureArtifactsService.validateArtifactSource(azureArtifactsConfig, null,
                   ArtifactStreamAttributes.builder().feed(FEED).packageId(PACKAGE_ID).protocolType(MAVEN).build()))
        .isFalse();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetBuilds() {
    wireMockRule.stubFor(
        get(urlEqualTo(format(
                "/azureartifacts/_apis/packaging/feeds/%s/packages/%s/versions?api-version=5.1-preview.1&isDeleted=false",
                FEED, PACKAGE_ID)))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        "{\"count\": 2, \"value\": [{\"id\": \"id1\", \"version\": \"1\", \"publishDate\": \"d1\", \"files\": []}, {\"id\": \"id2\", \"version\": \"2\", \"publishDate\": \"d2\", \"files\": []}]}")
                    .withHeader("Content-Type", "application/json")));
    List<BuildDetails> buildDetails = azureArtifactsService.getBuilds(ArtifactStreamAttributes.builder()
                                                                          .metadataOnly(true)
                                                                          .feed(FEED)
                                                                          .packageId(PACKAGE_ID)
                                                                          .protocolType(MAVEN)
                                                                          .build(),
        azureArtifactsConfig, null);
    assertThat(buildDetails).isNotNull();
    assertThat(buildDetails.size()).isEqualTo(2);
    assertThat(buildDetails.get(0).getNumber()).isEqualTo("1");
    assertThat(buildDetails.get(1).getNumber()).isEqualTo("2");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetEmptyBuilds() {
    wireMockRule.stubFor(get(
        urlEqualTo(format(
            "/azureartifacts/_apis/packaging/feeds/%s/packages/%s/versions?api-version=5.1-preview.1&isDeleted=false",
            FEED, PACKAGE_ID)))
                             .willReturn(aResponse()
                                             .withStatus(200)
                                             .withBody("{\"count\": 0, \"value\": []}")
                                             .withHeader("Content-Type", "application/json")));
    List<BuildDetails> buildDetails = azureArtifactsService.getBuilds(ArtifactStreamAttributes.builder()
                                                                          .metadataOnly(true)
                                                                          .feed(FEED)
                                                                          .packageId(PACKAGE_ID)
                                                                          .protocolType(MAVEN)
                                                                          .build(),
        azureArtifactsConfig, null);
    assertThat(buildDetails).isNotNull();
    assertThat(buildDetails.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotGetInvalidBuilds() {
    wireMockRule.stubFor(
        get(urlEqualTo(format(
                "/azureartifacts/_apis/packaging/feeds/%s/packages/%s/versions?api-version=5.1-preview.1&isDeleted=false",
                FEED, PACKAGE_ID)))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        "{\"count\": 2, \"value\": [{\"id\": \"id1\", \"publishDate\": \"d1\", \"files\": []}, {\"id\": \"id2\", \"publishDate\": \"d2\", \"files\": []}]}")
                    .withHeader("Content-Type", "application/json")));
    List<BuildDetails> buildDetails = azureArtifactsService.getBuilds(ArtifactStreamAttributes.builder()
                                                                          .metadataOnly(true)
                                                                          .feed(FEED)
                                                                          .packageId(PACKAGE_ID)
                                                                          .protocolType(MAVEN)
                                                                          .build(),
        azureArtifactsConfig, null);
    assertThat(buildDetails).isNotNull();
    assertThat(buildDetails.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldListProjects() {
    wireMockRule.stubFor(
        get(urlEqualTo("/azureartifacts/_apis/projects?api-version=5.1"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        "{\"count\": 2, \"value\": [{\"id\": \"id1\", \"name\": \"p1\"}, {\"id\": \"id2\", \"name\": \"p2\"}]}")
                    .withHeader("Content-Type", "application/json")));
    List<AzureDevopsProject> azureDevopsProjects = azureArtifactsService.listProjects(azureArtifactsConfig, null);
    assertThat(azureDevopsProjects).isNotNull();
    assertThat(azureDevopsProjects.size()).isEqualTo(2);
    assertThat(azureDevopsProjects.get(0).getId()).isEqualTo("id1");
    assertThat(azureDevopsProjects.get(1).getId()).isEqualTo("id2");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldListFeeds() {
    wireMockRule.stubFor(
        get(urlEqualTo("/azureartifacts/tmp/_apis/packaging/feeds?api-version=5.1-preview.1"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        "{\"count\": 2, \"value\": [{\"id\": \"id1\", \"name\": \"n1\"}, {\"id\": \"id2\", \"name\": \"n2\"}]}")
                    .withHeader("Content-Type", "application/json")));
    List<AzureArtifactsFeed> azureArtifactsFeeds = azureArtifactsService.listFeeds(azureArtifactsConfig, null, "tmp");
    assertThat(azureArtifactsFeeds).isNotNull();
    assertThat(azureArtifactsFeeds.size()).isEqualTo(2);
    assertThat(azureArtifactsFeeds.get(0).getId()).isEqualTo("id1");
    assertThat(azureArtifactsFeeds.get(1).getId()).isEqualTo("id2");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldListFeedsForBlankProject() {
    wireMockRule.stubFor(
        get(urlEqualTo("/azureartifacts/_apis/packaging/feeds?api-version=5.1-preview.1"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        "{\"count\": 2, \"value\": [{\"id\": \"id1\", \"name\": \"n1\", \"project\": {\"id\": \"pid1\", \"name\": \"p1\"}}, {\"id\": \"id2\", \"name\": \"n2\"}]}")
                    .withHeader("Content-Type", "application/json")));
    List<AzureArtifactsFeed> azureArtifactsFeeds = azureArtifactsService.listFeeds(azureArtifactsConfig, null, null);
    assertThat(azureArtifactsFeeds).isNotNull();
    assertThat(azureArtifactsFeeds.size()).isEqualTo(1);
    assertThat(azureArtifactsFeeds.get(0).getId()).isEqualTo("id2");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldListPackages() {
    wireMockRule.stubFor(
        get(urlEqualTo(
                format("/azureartifacts/_apis/packaging/feeds/%s/packages?api-version=5.1-preview.1&protocolType=%s",
                    FEED, MAVEN)))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        "{\"count\": 2, \"value\": [{\"id\": \"id1\", \"name\": \"n1\"}, {\"id\": \"id2\", \"name\": \"n2\"}]}")
                    .withHeader("Content-Type", "application/json")));
    List<AzureArtifactsPackage> azureArtifactsPackages =
        azureArtifactsService.listPackages(azureArtifactsConfig, null, null, FEED, MAVEN);
    assertThat(azureArtifactsPackages).isNotNull();
    assertThat(azureArtifactsPackages.size()).isEqualTo(2);
    assertThat(azureArtifactsPackages.get(0).getId()).isEqualTo("id1");
    assertThat(azureArtifactsPackages.get(1).getId()).isEqualTo("id2");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldListMavenFiles() {
    wireMockRule.stubFor(
        get(urlEqualTo(
                format("/azureartifacts/_apis/packaging/feeds/%s/packages/%s/versions/id1?api-version=5.1-preview.1",
                    FEED, PACKAGE_ID)))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        "{\"id\": \"id1\", \"version\": \"1\", \"publishDate\": \"d1\", \"files\": [{\"name\": \"file.war\", \"protocolMetadata\": {\"data\": {\"size\": 10}}}, {\"name\": \"file.war.sha1\"}]}")
                    .withHeader("Content-Type", "application/json")));

    List<AzureArtifactsPackageFileInfo> fileInfos = azureArtifactsService.listFiles(azureArtifactsConfig, null,
        ArtifactStreamAttributes.builder()
            .feed(FEED)
            .packageId(PACKAGE_ID)
            .protocolType(MAVEN)
            .packageName(PACKAGE_NAME_MAVEN)
            .build(),
        ImmutableMap.of(ArtifactMetadataKeys.versionId, "id1", ArtifactMetadataKeys.version, "1"), false);

    assertThat(fileInfos).isNotNull();
    assertThat(fileInfos.size()).isEqualTo(1);
    assertThat(fileInfos.get(0).getName()).isEqualTo("file.war");
    assertThat(fileInfos.get(0).getSize()).isEqualTo(10);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldListNuGetFiles() {
    validateListNuGetFiles(false);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldListNuGetFilesWithNameOnly() {
    validateListNuGetFiles(true);
  }

  private void validateListNuGetFiles(boolean nameOnly) {
    if (!nameOnly) {
      wireMockRule.stubFor(get(
          urlEqualTo(format(
              "/azureartifacts/_apis/packaging/feeds/%s/nuget/packages/%s/versions/1/content?api-version=5.1-preview.1",
              FEED, PACKAGE_NAME_NUGET)))
                               .willReturn(aResponse().withStatus(200).withBody(new byte[] {1, 2, 3, 4})));
    }

    List<AzureArtifactsPackageFileInfo> fileInfos = azureArtifactsService.listFiles(azureArtifactsConfig, null,
        ArtifactStreamAttributes.builder()
            .feed(FEED)
            .packageId(PACKAGE_ID)
            .protocolType(NUGET)
            .packageName(PACKAGE_NAME_NUGET)
            .build(),
        ImmutableMap.of(ArtifactMetadataKeys.versionId, "id1", ArtifactMetadataKeys.version, "1"), nameOnly);

    assertThat(fileInfos).isNotNull();
    assertThat(fileInfos.size()).isEqualTo(1);
    assertThat(fileInfos.get(0).getName()).isEqualTo(PACKAGE_NAME_NUGET);
    if (nameOnly) {
      assertThat(fileInfos.get(0).getSize()).isLessThan(0);
    } else {
      assertThat(fileInfos.get(0).getSize()).isEqualTo(4);
    }
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotListFilesForInvalidVersion() {
    azureArtifactsService.listFiles(azureArtifactsConfig, null,
        ArtifactStreamAttributes.builder().protocolType(MAVEN).build(), Collections.emptyMap(), false);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotListFilesForInvalidProtocolType() {
    List<AzureArtifactsPackageFileInfo> fileInfos = azureArtifactsService.listFiles(azureArtifactsConfig, null,
        ArtifactStreamAttributes.builder().protocolType("random").build(),
        ImmutableMap.of(ArtifactMetadataKeys.version, "tmp"), false);
    assertThat(fileInfos).isEmpty();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldDownloadMavenPackage() {
    wireMockRule.stubFor(
        get(urlEqualTo(
                format("/azureartifacts/_apis/packaging/feeds/%s/packages/%s/versions/id1?api-version=5.1-preview.1",
                    FEED, PACKAGE_ID)))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        "{\"id\": \"id1\", \"version\": \"1\", \"publishDate\": \"d1\", \"files\": [{\"name\": \"file.war\"}, {\"name\": \"file.war.sha1\"}]}")
                    .withHeader("Content-Type", "application/json")));
    wireMockRule.stubFor(
        get(urlEqualTo(format(
                "/azureartifacts/_apis/packaging/feeds/%s/maven/%s/%s/1/file.war/content?api-version=5.1-preview.1",
                FEED, GROUP_ID, ARTIFACT_ID)))
            .willReturn(aResponse().withStatus(200).withBody(new byte[] {1, 2, 3, 4})));

    ListNotifyResponseData listNotifyResponseData = new ListNotifyResponseData();
    DelegateFile delegateFile = DelegateFile.Builder.aDelegateFile().withFileId(FILE_ID).build();
    when(delegateFileManager.upload(any(), any())).thenReturn(delegateFile);

    azureArtifactsService.downloadArtifact(azureArtifactsConfig, null,
        ArtifactStreamAttributes.builder()
            .feed(FEED)
            .packageId(PACKAGE_ID)
            .protocolType(MAVEN)
            .packageName(PACKAGE_NAME_MAVEN)
            .build(),
        ImmutableMap.of(ArtifactMetadataKeys.versionId, "id1", ArtifactMetadataKeys.version, "1"), null, null, null,
        listNotifyResponseData);

    assertThat(listNotifyResponseData.getData().size()).isEqualTo(1);
    assertThat(ArtifactFile.fromDTO(listNotifyResponseData.getData().get(0)).getFileUuid()).isEqualTo(FILE_ID);
    assertThat(ArtifactFile.fromDTO(listNotifyResponseData.getData().get(0)).getName()).isEqualTo("file.war");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldDownloadMavenPackageAtRuntime() {
    wireMockRule.stubFor(
        get(urlEqualTo(
                format("/azureartifacts/_apis/packaging/feeds/%s/packages/%s/versions/id1?api-version=5.1-preview.1",
                    FEED, PACKAGE_ID)))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        "{\"id\": \"id1\", \"version\": \"1\", \"publishDate\": \"d1\", \"files\": [{\"name\": \"file.war\", \"protocolMetadata\": {\"data\": { \"size\": 100 }} }, {\"name\": \"file.war.sha1\"}]}")
                    .withHeader("Content-Type", "application/json")));
    wireMockRule.stubFor(
        get(urlEqualTo(format(
                "/azureartifacts/_apis/packaging/feeds/%s/maven/%s/%s/1/file.war/content?api-version=5.1-preview.1",
                FEED, GROUP_ID, ARTIFACT_ID)))
            .willReturn(aResponse().withStatus(200).withBody(new byte[] {1, 2, 3, 4})));

    Pair<String, InputStream> pair = azureArtifactsService.downloadArtifact(azureArtifactsConfig, null,
        ArtifactStreamAttributes.builder()
            .feed(FEED)
            .packageId(PACKAGE_ID)
            .protocolType(MAVEN)
            .packageName(PACKAGE_NAME_MAVEN)
            .artifactType(ArtifactType.WAR)
            .build(),
        ImmutableMap.of(ArtifactMetadataKeys.versionId, "id1", ArtifactMetadataKeys.version, "1",
            ArtifactMetadataKeys.artifactFileName, "file.war"));

    assertThat(pair).isNotNull();
    assertThat(pair.getLeft()).isEqualTo("file.war");
    assertThat(pair.getRight()).isNotNull();
    assertThat(calcInputStreamSize(pair.getRight())).isEqualTo(4);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldDownloadNuGetPackage() {
    wireMockRule.stubFor(get(
        urlEqualTo(format(
            "/azureartifacts/_apis/packaging/feeds/%s/nuget/packages/%s/versions/1/content?api-version=5.1-preview.1",
            FEED, PACKAGE_NAME_NUGET)))
                             .willReturn(aResponse().withStatus(200).withBody(new byte[] {1, 2, 3, 4})));

    ListNotifyResponseData listNotifyResponseData = new ListNotifyResponseData();
    DelegateFile delegateFile = DelegateFile.Builder.aDelegateFile().withFileId(FILE_ID).build();
    when(delegateFileManager.upload(any(), any())).thenReturn(delegateFile);

    azureArtifactsService.downloadArtifact(azureArtifactsConfig, null,
        ArtifactStreamAttributes.builder()
            .feed(FEED)
            .packageId(PACKAGE_ID)
            .protocolType(NUGET)
            .packageName(PACKAGE_NAME_NUGET)
            .build(),
        ImmutableMap.of(ArtifactMetadataKeys.versionId, "id1", ArtifactMetadataKeys.version, "1"), null, null, null,
        listNotifyResponseData);

    assertThat(listNotifyResponseData.getData().size()).isEqualTo(1);
    assertThat(ArtifactFile.fromDTO(listNotifyResponseData.getData().get(0)).getFileUuid()).isEqualTo(FILE_ID);
    assertThat(ArtifactFile.fromDTO(listNotifyResponseData.getData().get(0)).getName()).isEqualTo(PACKAGE_NAME_NUGET);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldDownloadNuGetPackageAtRuntime() {
    wireMockRule.stubFor(get(
        urlEqualTo(format(
            "/azureartifacts/_apis/packaging/feeds/%s/nuget/packages/%s/versions/1/content?api-version=5.1-preview.1",
            FEED, PACKAGE_NAME_NUGET)))
                             .willReturn(aResponse().withStatus(200).withBody(new byte[] {1, 2, 3, 4})));

    Pair<String, InputStream> pair = azureArtifactsService.downloadArtifact(azureArtifactsConfig, null,
        ArtifactStreamAttributes.builder()
            .feed(FEED)
            .packageId(PACKAGE_ID)
            .protocolType(NUGET)
            .packageName(PACKAGE_NAME_NUGET)
            .build(),
        ImmutableMap.of(ArtifactMetadataKeys.versionId, "id1", ArtifactMetadataKeys.version, "1",
            ArtifactMetadataKeys.artifactFileName, PACKAGE_NAME_NUGET));

    assertThat(pair).isNotNull();
    assertThat(pair.getLeft()).isEqualTo(PACKAGE_NAME_NUGET);
    assertThat(pair.getRight()).isNotNull();
    assertThat(calcInputStreamSize(pair.getRight())).isEqualTo(4);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotDownloadArtifactForInvalidVersion() {
    azureArtifactsService.downloadArtifact(azureArtifactsConfig, null,
        ArtifactStreamAttributes.builder()
            .feed(FEED)
            .packageId(PACKAGE_ID)
            .protocolType(MAVEN)
            .packageName(PACKAGE_NAME_MAVEN)
            .build(),
        ImmutableMap.of(ArtifactMetadataKeys.versionId, "id1"), null, null, null, null);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotDownloadArtifactForInvalidVersionId() {
    azureArtifactsService.downloadArtifact(azureArtifactsConfig, null,
        ArtifactStreamAttributes.builder()
            .feed(FEED)
            .packageId(PACKAGE_ID)
            .protocolType(MAVEN)
            .packageName(PACKAGE_NAME_MAVEN)
            .build(),
        ImmutableMap.of(ArtifactMetadataKeys.version, "1"), null, null, null, null);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotDownloadArtifactAtRuntimeForInvalidVersion() {
    azureArtifactsService.downloadArtifact(azureArtifactsConfig, null,
        ArtifactStreamAttributes.builder()
            .feed(FEED)
            .packageId(PACKAGE_ID)
            .protocolType(MAVEN)
            .packageName(PACKAGE_NAME_MAVEN)
            .build(),
        ImmutableMap.of(ArtifactMetadataKeys.versionId, "id1", ArtifactMetadataKeys.artifactFileName, "file.war"));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotDownloadArtifactAtRuntimeForInvalidArtifactFileName() {
    azureArtifactsService.downloadArtifact(azureArtifactsConfig, null,
        ArtifactStreamAttributes.builder()
            .feed(FEED)
            .packageId(PACKAGE_ID)
            .protocolType(MAVEN)
            .packageName(PACKAGE_NAME_MAVEN)
            .build(),
        ImmutableMap.of(ArtifactMetadataKeys.versionId, "id1", ArtifactMetadataKeys.version, "1"));
  }

  @Test(expected = Test.None.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldValidateResponse() {
    validateResponse(Response.success(""));
  }

  @Test(expected = InvalidArtifactServerException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldValidateResponseNull() {
    validateResponse(null);
  }

  @Test(expected = Test.None.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldValidateRawResponse() {
    validateRawResponse(Response.success("").raw());
  }

  @Test(expected = InvalidArtifactServerException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldValidateRawResponseNull() {
    validateRawResponse(null);
  }

  @Test(expected = InvalidArtifactServerException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldValidateRawResponse203() {
    validateRawResponse(Response
                            .success("{}",
                                new okhttp3.Response.Builder()
                                    .code(203)
                                    .message("203")
                                    .protocol(Protocol.HTTP_1_1)
                                    .request(new Request.Builder().url("http://localhost/path").build())
                                    .build())
                            .raw());
  }

  @Test(expected = InvalidArtifactServerException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldValidateRawResponse401() {
    validateRawResponse(new okhttp3.Response.Builder()
                            .code(401)
                            .message("401")
                            .protocol(Protocol.HTTP_1_1)
                            .request(new Request.Builder().url("http://localhost/path").build())
                            .build());
  }

  @Test(expected = InvalidArtifactServerException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldValidateRawResponse500() {
    validateRawResponse(new okhttp3.Response.Builder()
                            .code(500)
                            .message("500")
                            .protocol(Protocol.HTTP_1_1)
                            .request(new Request.Builder().url("http://localhost/path").build())
                            .build());
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetSubdomainUrl() {
    assertThat(getSubdomainUrl("https://dev.azure.com/org", "feeds")).isEqualTo("https://feeds.dev.azure.com/org");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotGetSubdomainUrlForInvalidAzureDevopsUrl() {
    String invalidUrl = "htttttp://invalid.azure.com/org";
    assertThat(getSubdomainUrl(invalidUrl, "feeds")).isEqualTo(invalidUrl);
  }

  @Test(expected = Test.None.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldValidateAzureDevopsUrl() {
    validateAzureDevopsUrl("https://dev.azure.com/org");
  }

  @Test(expected = InvalidArtifactServerException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotValidateInvalidUrlForAzureDevopsUrl() {
    validateAzureDevopsUrl("$[level]");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotValidateInvalidAzureDevopsUrl() {
    validateAzureDevopsUrl("https://devother.azure.com/org");
  }

  private static long calcInputStreamSize(InputStream inputStream) {
    try {
      long size = 0;
      int chunk;
      byte[] buffer = new byte[32];
      while ((chunk = inputStream.read(buffer)) != -1) {
        size += chunk;
        if (size > 512) {
          return -1;
        }
      }
      return size;
    } catch (IOException e) {
      return -1;
    }
  }
}
