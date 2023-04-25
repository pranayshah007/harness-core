/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.collect.artifacts;

import static io.harness.delegate.beans.artifact.ArtifactFileMetadata.builder;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.GARVIT;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.COMMAND_UNIT_NAME;
import static software.wings.utils.WingsTestConstants.HOST_NAME;

import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.artifact.ArtifactFileMetadata;
import io.harness.exception.InvalidArgumentsException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.BambooConfig;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.artifact.AzureArtifactsArtifactStreamProtocolType;
import software.wings.beans.config.NexusConfig;
import software.wings.beans.dto.SettingAttribute;
import software.wings.beans.settings.azureartifacts.AzureArtifactsConfig;
import software.wings.beans.settings.azureartifacts.AzureArtifactsPATConfig;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackageFileInfo;
import software.wings.helpers.ext.azure.devops.AzureArtifactsService;
import software.wings.helpers.ext.bamboo.BambooService;
import software.wings.helpers.ext.jenkins.Jenkins;
import software.wings.helpers.ext.nexus.NexusService;
import software.wings.service.impl.jenkins.JenkinsUtils;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ArtifactCollectionTaskHelperTest extends WingsBaseTest {
  private static final String MAVEN = AzureArtifactsArtifactStreamProtocolType.maven.name();
  private static final String FEED = "FEED";
  private static final String PACKAGE_ID = "PACKAGE_ID";
  private static final String PACKAGE_NAME_MAVEN = "GROUP_ID:ARTIFACT_ID";

  @Inject @InjectMocks private ArtifactCollectionTaskHelper artifactCollectionTaskHelper;
  @Mock private AzureArtifactsService azureArtifactsService;
  @Mock private Jenkins jenkins;
  @Mock private JenkinsUtils jenkinsUtils;
  @Mock private BambooService bambooService;
  @Mock private NexusService nexusService;

  private static final String BAMBOO_FILE_NAME = "todolist.tar";
  private static final String BAMBOO_FILE_PATH =
      "http://localhost:9095/artifact/TOD-TOD/JOB1/build-11/artifacts/todolist.tar";
  private static final ArtifactStreamAttributes BAMBOO_ARTIFACT_STREAM_ATTRIBUTES =
      ArtifactStreamAttributes.builder()
          .artifactStreamType(ArtifactStreamType.BAMBOO.name())
          .jobName("TOD-TOD")
          .artifactPaths(Arrays.asList("artifacts/todolist.tar"))
          .metadataOnly(true)
          .metadata(ImmutableMap.of(ArtifactMetadataKeys.buildNo, "11", ArtifactMetadataKeys.artifactFileName,
              BAMBOO_FILE_NAME, ArtifactMetadataKeys.artifactPath, BAMBOO_FILE_PATH))
          .artifactFileMetadata(
              Arrays.asList(ArtifactFileMetadata.builder().fileName(BAMBOO_FILE_NAME).url(BAMBOO_FILE_PATH).build()))
          .serverSetting(SettingAttribute.builder().value(BambooConfig.builder().build()).build())
          .artifactServerEncryptedDataDetails(Collections.emptyList())
          .artifactStreamId(ARTIFACT_STREAM_ID)
          .build();

  private static final String NEXUS2_FILE_NAME = "todolist.tar";
  private static final String NEXUS2_FILE_PATH =
      "http://localhost:9095/artifact/TOD-TOD/JOB1/build-11/artifacts/todolist.tar";

  private static final ArtifactStreamAttributes artifactStreamAttributesForNexus =
      ArtifactStreamAttributes.builder()
          .artifactStreamType(ArtifactStreamType.NEXUS.name())
          .metadataOnly(true)
          .metadata(ImmutableMap.of(ArtifactMetadataKeys.buildNo, "11", ArtifactMetadataKeys.artifactFileName,
              NEXUS2_FILE_NAME, ArtifactMetadataKeys.artifactPath, NEXUS2_FILE_PATH,
              ArtifactMetadataKeys.artifactFileSize, "1233"))
          .artifactFileMetadata(asList(builder().fileName("todolist-7.0.war").url(NEXUS2_FILE_PATH).build()))
          .serverSetting(SettingAttribute.builder().value(NexusConfig.builder().build()).build())
          .artifactStreamId(ARTIFACT_STREAM_ID)
          .jobName("releases")
          .groupId("io.harness.test")
          .artifactName("todolist")
          .artifactServerEncryptedDataDetails(Collections.emptyList())
          .build();

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldDownloadAzureArtifactsArtifactAtRuntime() {
    String fileName = "file.war";
    String content = "file content";
    InputStream inputStream = new ByteArrayInputStream(content.getBytes());
    when(azureArtifactsService.downloadArtifact(
             any(AzureArtifactsConfig.class), any(), any(ArtifactStreamAttributes.class), anyMap()))
        .thenReturn(ImmutablePair.of(fileName, inputStream));

    Pair<String, InputStream> pair = artifactCollectionTaskHelper.downloadArtifactAtRuntime(
        ArtifactStreamAttributes.builder()
            .artifactStreamType(ArtifactStreamType.AZURE_ARTIFACTS.name())
            .serverSetting(SettingAttribute.builder().value(AzureArtifactsPATConfig.builder().build()).build())
            .feed(FEED)
            .packageId(PACKAGE_ID)
            .protocolType(MAVEN)
            .packageName(PACKAGE_NAME_MAVEN)
            .metadata(ImmutableMap.of(ArtifactMetadataKeys.versionId, "id1", ArtifactMetadataKeys.version, "1",
                ArtifactMetadataKeys.artifactFileName, fileName))
            .build(),
        ACCOUNT_ID, APP_ID, ACTIVITY_ID, COMMAND_UNIT_NAME, HOST_NAME);

    assertThat(pair).isNotNull();
    assertThat(pair.getLeft()).isEqualTo(fileName);
    assertThat(pair.getRight()).isNotNull();
    assertThat(convertInputStreamToString(pair.getRight())).isEqualTo(content);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetAzureArtifactsFileSize() {
    String fileName = "file.war";
    when(azureArtifactsService.listFiles(
             any(AzureArtifactsConfig.class), any(), any(ArtifactStreamAttributes.class), anyMap(), eq(false)))
        .thenReturn(Arrays.asList(new AzureArtifactsPackageFileInfo("random1", 4),
            new AzureArtifactsPackageFileInfo(fileName, 8), new AzureArtifactsPackageFileInfo("random2", 16)));
    assertThat(getArtifactFileSize(fileName)).isEqualTo(8);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotGetAzureArtifactsFileSizeForInvalidFileName() {
    String fileName = "file.war";
    when(azureArtifactsService.listFiles(
             any(AzureArtifactsConfig.class), anyList(), any(ArtifactStreamAttributes.class), anyMap(), eq(false)))
        .thenReturn(Arrays.asList(new AzureArtifactsPackageFileInfo("random1", 4),
            new AzureArtifactsPackageFileInfo(fileName, 8), new AzureArtifactsPackageFileInfo("random2", 16)));
    getArtifactFileSize(null);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotGetAzureArtifactsFileSizeForNoFiles() {
    String fileName = "file.war";
    when(azureArtifactsService.listFiles(
             any(AzureArtifactsConfig.class), anyList(), any(ArtifactStreamAttributes.class), anyMap(), eq(false)))
        .thenReturn(Collections.emptyList());
    getArtifactFileSize(fileName);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotGetAzureArtifactsFileSizeForFileNotPresent() {
    String fileName = "file.war";
    when(azureArtifactsService.listFiles(
             any(AzureArtifactsConfig.class), anyList(), any(ArtifactStreamAttributes.class), anyMap(), eq(false)))
        .thenReturn(Arrays.asList(
            new AzureArtifactsPackageFileInfo("random1", 4), new AzureArtifactsPackageFileInfo("random2", 16)));
    getArtifactFileSize(fileName);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldDownloadJenkinsArtifactAtRuntime() throws IOException, URISyntaxException {
    String fileName = "file.war";
    String fileUrl = "https://www.somejenkins/artifact/file.war";
    String content = "file content";
    InputStream inputStream = new ByteArrayInputStream(content.getBytes());
    when(jenkinsUtils.getJenkins(any())).thenReturn(jenkins);
    when(jenkins.downloadArtifact(anyString(), anyString(), anyString()))
        .thenReturn(ImmutablePair.of(fileName, inputStream));

    Pair<String, InputStream> pair = artifactCollectionTaskHelper.downloadArtifactAtRuntime(
        ArtifactStreamAttributes.builder()
            .artifactStreamType(ArtifactStreamType.JENKINS.name())
            .metadataOnly(true)
            .metadata(ImmutableMap.of(ArtifactMetadataKeys.buildNo, "1", ArtifactMetadataKeys.artifactFileName,
                fileName, ArtifactMetadataKeys.artifactPath, fileUrl))
            .artifactFileMetadata(asList(builder().fileName(fileName).url(fileUrl).build()))
            .serverSetting(SettingAttribute.builder().value(JenkinsConfig.builder().build()).build())
            .artifactStreamId(ARTIFACT_STREAM_ID)
            .jobName("scheduler")
            .artifactPaths(asList("file.war"))
            .artifactServerEncryptedDataDetails(Collections.emptyList())
            .build(),
        ACCOUNT_ID, APP_ID, ACTIVITY_ID, COMMAND_UNIT_NAME, HOST_NAME);

    assertThat(pair).isNotNull();
    assertThat(pair.getLeft()).isEqualTo(fileName);
    assertThat(pair.getRight()).isNotNull();
    assertThat(convertInputStreamToString(pair.getRight())).isEqualTo(content);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetJenkinsArtifactsFileSize() {
    String fileName = "file.war";
    when(jenkinsUtils.getJenkins(any())).thenReturn(jenkins);
    when(jenkins.getFileSize(anyString(), anyString(), anyString())).thenReturn(1234L);

    long size = artifactCollectionTaskHelper.getArtifactFileSize(
        ArtifactStreamAttributes.builder()
            .artifactStreamType(ArtifactStreamType.JENKINS.name())
            .metadataOnly(true)
            .metadata(ImmutableMap.of(ArtifactMetadataKeys.buildNo, "1", ArtifactMetadataKeys.artifactFileName,
                fileName, ArtifactMetadataKeys.artifactPath, "https://www.somejenkins/artifact/file.war"))
            .serverSetting(SettingAttribute.builder().value(JenkinsConfig.builder().build()).build())
            .artifactStreamId(ARTIFACT_STREAM_ID)
            .jobName("scheduler")
            .artifactPaths(asList("file.war"))
            .artifactServerEncryptedDataDetails(Collections.emptyList())
            .build());
    assertThat(size).isEqualTo(1234L);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetBambooArtifactsFileSize() {
    when(bambooService.getFileSize(any(), any(), eq(BAMBOO_FILE_NAME), eq(BAMBOO_FILE_PATH))).thenReturn(1234L);
    long size = artifactCollectionTaskHelper.getArtifactFileSize(BAMBOO_ARTIFACT_STREAM_ATTRIBUTES);
    assertThat(size).isEqualTo(1234L);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldDownloadBambooArtifactAtRuntime() {
    String content = "file content";
    InputStream inputStream = new ByteArrayInputStream(content.getBytes());
    when(bambooService.downloadArtifact(any(), any(), anyString(), anyString()))
        .thenReturn(ImmutablePair.of(BAMBOO_FILE_NAME, inputStream));

    Pair<String, InputStream> pair = artifactCollectionTaskHelper.downloadArtifactAtRuntime(
        BAMBOO_ARTIFACT_STREAM_ATTRIBUTES, ACCOUNT_ID, APP_ID, ACTIVITY_ID, COMMAND_UNIT_NAME, HOST_NAME);

    assertThat(pair).isNotNull();
    assertThat(pair.getLeft()).isEqualTo(BAMBOO_FILE_NAME);
    assertThat(pair.getRight()).isNotNull();
    assertThat(convertInputStreamToString(pair.getRight())).isEqualTo(content);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldDownloadNexusArtifactAtRuntime() {
    String content = "file content";
    InputStream inputStream = new ByteArrayInputStream(content.getBytes());
    when(nexusService.downloadArtifactByUrl(any(), anyString(), anyString()))
        .thenReturn(ImmutablePair.of(NEXUS2_FILE_NAME, inputStream));

    Pair<String, InputStream> pair = artifactCollectionTaskHelper.downloadArtifactAtRuntime(
        artifactStreamAttributesForNexus, ACCOUNT_ID, APP_ID, ACTIVITY_ID, COMMAND_UNIT_NAME, HOST_NAME);

    assertThat(pair).isNotNull();
    assertThat(pair.getLeft()).isEqualTo(NEXUS2_FILE_NAME);
    assertThat(pair.getRight()).isNotNull();
    assertThat(convertInputStreamToString(pair.getRight())).isEqualTo(content);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetNexusArtifactsFileSize() {
    when(nexusService.getFileSize(any(), eq(NEXUS2_FILE_NAME), eq(NEXUS2_FILE_PATH))).thenReturn(1234L);
    long size = artifactCollectionTaskHelper.getArtifactFileSize(artifactStreamAttributesForNexus);
    assertThat(size).isEqualTo(1234L);
  }

  private Long getArtifactFileSize(String fileName) {
    Map<String, String> metadata = new HashMap<>();
    metadata.put(ArtifactMetadataKeys.version, "id1");
    metadata.put(ArtifactMetadataKeys.versionId, "1");
    if (isNotBlank(fileName)) {
      metadata.put(ArtifactMetadataKeys.artifactFileName, fileName);
    }

    return artifactCollectionTaskHelper.getArtifactFileSize(
        ArtifactStreamAttributes.builder()
            .artifactStreamType(ArtifactStreamType.AZURE_ARTIFACTS.name())
            .serverSetting(SettingAttribute.builder().value(AzureArtifactsPATConfig.builder().build()).build())
            .feed(FEED)
            .packageId(PACKAGE_ID)
            .protocolType(MAVEN)
            .packageName(PACKAGE_NAME_MAVEN)
            .metadata(metadata)
            .build());
  }

  private String convertInputStreamToString(InputStream in) {
    try {
      StringBuilder sb = new StringBuilder();
      try (Reader reader = new BufferedReader(new InputStreamReader(in))) {
        int c;
        while ((c = reader.read()) != -1) {
          sb.append((char) c);
        }
      }
      return sb.toString();
    } catch (IOException e) {
      return "";
    }
  }
}
