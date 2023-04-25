/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegatetasks;

import static io.harness.delegate.beans.artifact.ArtifactFileMetadata.builder;
import static io.harness.rule.OwnerRule.AADITI;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.artifact.ArtifactFileMetadata;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.service.DelegateFileManagerImpl;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.rule.Owner;
import io.harness.rule.Repeat;

import software.wings.beans.AwsConfig;
import software.wings.beans.BambooConfig;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.beans.config.NexusConfig;
import software.wings.beans.dto.SettingAttribute;
import software.wings.delegatetasks.collect.artifacts.ArtifactCollectionTaskHelper;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class DelegateFileManagerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock ArtifactCollectionTaskHelper artifactCollectionTaskHelper;
  @Mock DelegateAgentManagerClient delegateAgentManagerClient;

  DelegateConfiguration delegateConfiguration = DelegateConfiguration.builder().maxCachedArtifacts(10).build();
  @InjectMocks
  private DelegateFileManagerImpl delegateFileManager =
      new DelegateFileManagerImpl(delegateAgentManagerClient, delegateConfiguration);

  private static final String ACCESS_KEY = "ACCESS_KEY";
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ACTIVITY_ID = "ACTIVITY_ID";
  private static final String APP_ID = "APP_ID";
  private static final String ARTIFACT_FILE_NAME = "ARTIFACT_FILE_NAME";
  private static final String ARTIFACT_STREAM_ID_S3 = "ARTIFACT_STREAM_ID_S3";
  private static final String ARTIFACT_STREAM_ID_ARTIFACTORY = "ARTIFACT_STREAM_ID_ARTIFACTORY";
  private static final String ARTIFACT_STREAM_ID_JENKINS = "ARTIFACT_STREAM_ID_JENKINS";
  private static final String JENKINS_FILE_NAME = "file.war";
  private static final String ARTIFACT_STREAM_ID_BAMBOO = "ARTIFACT_STREAM_ID_BAMBOO";
  private static final String BAMBOO_FILE_NAME = "todolist.tar";
  private static final String BAMBOO_FILE_PATH =
      "http://localhost:9095/artifact/TOD-TOD/JOB1/build-11/artifacts/todolist.tar";
  private static final String ARTIFACT_STREAM_ID_NEXUS = "ARTIFACT_STREAM_ID_NEXUS";
  private static final String ARTIFACT_PATH = "ARTIFACT_PATH";
  private static final String BUCKET_NAME = "BUCKET_NAME";
  private static final String BUILD_NO = "BUILD_NO";
  private static final String COMMAND_UNIT_NAME = "COMMAND_UNIT_NAME";
  private static final String HOST_NAME = "HOST_NAME";
  private static final String S3_URL = "S3_URL";
  private static final char[] SECRET_KEY = "SECRET_KEY".toCharArray();
  private static final String SETTING_ID = "SETTING_ID";
  private static final Long MY_SIZE = 3433L;
  private static final String ARTIFACT_REPO_BASE_DIR = "./repository/artifacts/";
  private static final String ARTIFACTORY_URL = "ARTIFACTORY_URL";

  private SettingAttribute awsSetting =
      SettingAttribute.builder()
          .uuid(SETTING_ID)
          .value(AwsConfig.builder().secretKey(SECRET_KEY).accessKey(ACCESS_KEY.toCharArray()).build())
          .build();
  private ArtifactStreamAttributes artifactStreamAttributesForS3 =
      ArtifactStreamAttributes.builder()
          .artifactStreamType(ArtifactStreamType.AMAZON_S3.name())
          .metadataOnly(true)
          .metadata(mockMetadata(ArtifactStreamType.AMAZON_S3))
          .serverSetting(awsSetting)
          .artifactStreamId(ARTIFACT_STREAM_ID_S3)
          .artifactServerEncryptedDataDetails(Collections.emptyList())
          .build();

  private SettingAttribute artifactorySetting = SettingAttribute.builder()
                                                    .uuid(SETTING_ID)
                                                    .value(ArtifactoryConfig.builder()
                                                               .artifactoryUrl(ARTIFACTORY_URL)
                                                               .username("admin")
                                                               .password("dummy123!".toCharArray())
                                                               .build())
                                                    .build();
  private ArtifactStreamAttributes artifactStreamAttributesForArtifactory =
      ArtifactStreamAttributes.builder()
          .artifactStreamType(ArtifactStreamType.ARTIFACTORY.name())
          .metadataOnly(true)
          .metadata(mockMetadata(ArtifactStreamType.ARTIFACTORY))
          .serverSetting(artifactorySetting)
          .artifactStreamId(ARTIFACT_STREAM_ID_ARTIFACTORY)
          .artifactServerEncryptedDataDetails(Collections.emptyList())
          .build();

  private ArtifactStreamAttributes artifactStreamAttributesForJenkins =
      ArtifactStreamAttributes.builder()
          .artifactStreamType(ArtifactStreamType.JENKINS.name())
          .metadataOnly(true)
          .metadata(ImmutableMap.of(ArtifactMetadataKeys.buildNo, BUILD_NO, ArtifactMetadataKeys.artifactFileName,
              JENKINS_FILE_NAME, ArtifactMetadataKeys.artifactFileSize, "1233"))
          .serverSetting(SettingAttribute.builder().value(JenkinsConfig.builder().build()).build())
          .artifactStreamId(ARTIFACT_STREAM_ID_JENKINS)
          .jobName("scheduler")
          .artifactPaths(Collections.singletonList(JENKINS_FILE_NAME))
          .artifactServerEncryptedDataDetails(Collections.emptyList())
          .build();

  private SettingAttribute bambooSetting = SettingAttribute.builder()
                                               .uuid(SETTING_ID)
                                               .value(BambooConfig.builder()
                                                          .bambooUrl("http://localhost:9095/")
                                                          .username("admin")
                                                          .password("admin".toCharArray())
                                                          .build())
                                               .build();

  private ArtifactStreamAttributes bambooStreamAttributes =
      ArtifactStreamAttributes.builder()
          .artifactStreamType(ArtifactStreamType.BAMBOO.name())
          .jobName("TOD-TOD")
          .artifactPaths(Arrays.asList("artifacts/todolist.tar"))
          .metadataOnly(true)
          .metadata(ImmutableMap.of(ArtifactMetadataKeys.buildNo, BUILD_NO, ArtifactMetadataKeys.artifactFileName,
              BAMBOO_FILE_NAME, ArtifactMetadataKeys.artifactFileSize, "1233", ArtifactMetadataKeys.artifactPath,
              BAMBOO_FILE_PATH))
          .artifactFileMetadata(
              Arrays.asList(ArtifactFileMetadata.builder().fileName(BAMBOO_FILE_NAME).url(BAMBOO_FILE_PATH).build()))
          .serverSetting(bambooSetting)
          .artifactServerEncryptedDataDetails(Collections.emptyList())
          .artifactStreamId(ARTIFACT_STREAM_ID_BAMBOO)
          .build();

  private static final String NEXUS2_FILE_NAME = "todolist.tar";
  private static final String NEXUS2_FILE_PATH =
      "http://localhost:9095/artifact/TOD-TOD/JOB1/build-11/artifacts/todolist.tar";

  private ArtifactStreamAttributes artifactStreamAttributesForNexus =
      ArtifactStreamAttributes.builder()
          .artifactStreamType(ArtifactStreamType.NEXUS.name())
          .metadataOnly(true)
          .metadata(ImmutableMap.of(ArtifactMetadataKeys.buildNo, "11", ArtifactMetadataKeys.artifactFileName,
              NEXUS2_FILE_NAME, ArtifactMetadataKeys.artifactPath, NEXUS2_FILE_PATH,
              ArtifactMetadataKeys.artifactFileSize, "1233"))
          .artifactFileMetadata(asList(builder().fileName("todolist-7.0.war").url(NEXUS2_FILE_PATH).build()))
          .serverSetting(SettingAttribute.builder().value(NexusConfig.builder().build()).build())
          .artifactStreamId(ARTIFACT_STREAM_ID_NEXUS)
          .jobName("releases")
          .groupId("io.harness.test")
          .artifactName("todolist")
          .artifactServerEncryptedDataDetails(Collections.emptyList())
          .build();

  @Test
  @Owner(developers = AADITI)
  @Repeat(times = 3, successes = 1)
  @Category(UnitTests.class)
  public void testDownloadArtifactAtRuntimeForS3() throws IOException, ExecutionException {
    String fileContent = "test";
    InputStream is = new ByteArrayInputStream(fileContent.getBytes(Charset.defaultCharset()));
    Pair<String, InputStream> pair = new ImmutablePair<>(fileContent, is);
    when(artifactCollectionTaskHelper.downloadArtifactAtRuntime(
             artifactStreamAttributesForS3, ACCOUNT_ID, APP_ID, ACTIVITY_ID, COMMAND_UNIT_NAME, HOST_NAME))
        .thenReturn(pair);
    delegateFileManager.downloadArtifactAtRuntime(
        artifactStreamAttributesForS3, ACCOUNT_ID, APP_ID, ACTIVITY_ID, COMMAND_UNIT_NAME, HOST_NAME);
    String text =
        Files.toString(new File(ARTIFACT_REPO_BASE_DIR + "_" + ARTIFACT_STREAM_ID_S3 + "-" + BUILD_NO), Charsets.UTF_8);
    assertThat(text).isEqualTo(fileContent);
    FileUtils.deleteQuietly(FileUtils.getFile(ARTIFACT_REPO_BASE_DIR + "_" + ARTIFACT_STREAM_ID_S3 + "-" + BUILD_NO));
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testDownloadArtifactAtRuntimeForArtifactory() throws IOException, ExecutionException {
    String fileContent = "test";
    InputStream is = new ByteArrayInputStream(fileContent.getBytes(Charset.defaultCharset()));
    Pair<String, InputStream> pair = new ImmutablePair<>(fileContent, is);
    when(artifactCollectionTaskHelper.downloadArtifactAtRuntime(
             artifactStreamAttributesForArtifactory, ACCOUNT_ID, APP_ID, ACTIVITY_ID, COMMAND_UNIT_NAME, HOST_NAME))
        .thenReturn(pair);
    delegateFileManager.downloadArtifactAtRuntime(
        artifactStreamAttributesForArtifactory, ACCOUNT_ID, APP_ID, ACTIVITY_ID, COMMAND_UNIT_NAME, HOST_NAME);
    String text = Files.toString(
        new File(ARTIFACT_REPO_BASE_DIR + "_" + ARTIFACT_STREAM_ID_ARTIFACTORY + "-" + BUILD_NO), Charsets.UTF_8);
    assertThat(text).isEqualTo(fileContent);
    FileUtils.deleteQuietly(
        FileUtils.getFile(ARTIFACT_REPO_BASE_DIR + "_" + ARTIFACT_STREAM_ID_ARTIFACTORY + "-" + BUILD_NO));
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetArtifactFileSize() {
    when(artifactCollectionTaskHelper.getArtifactFileSize(any(ArtifactStreamAttributes.class))).thenReturn(1234L);
    Long size = delegateFileManager.getArtifactFileSize(artifactStreamAttributesForS3);
    assertThat(size.longValue()).isEqualTo(1234L);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testDownloadArtifactAtRuntimeForJenkins() throws IOException, ExecutionException {
    String fileContent = "test";
    InputStream is = new ByteArrayInputStream(fileContent.getBytes(Charset.defaultCharset()));
    Pair<String, InputStream> pair = new ImmutablePair<>(fileContent, is);
    when(artifactCollectionTaskHelper.downloadArtifactAtRuntime(
             artifactStreamAttributesForJenkins, ACCOUNT_ID, APP_ID, ACTIVITY_ID, COMMAND_UNIT_NAME, HOST_NAME))
        .thenReturn(pair);
    delegateFileManager.downloadArtifactAtRuntime(
        artifactStreamAttributesForJenkins, ACCOUNT_ID, APP_ID, ACTIVITY_ID, COMMAND_UNIT_NAME, HOST_NAME);
    String text = Files.toString(
        new File(ARTIFACT_REPO_BASE_DIR + "_" + ARTIFACT_STREAM_ID_JENKINS + "-" + BUILD_NO + "-" + JENKINS_FILE_NAME),
        Charsets.UTF_8);
    assertThat(text).isEqualTo(fileContent);
    FileUtils.deleteQuietly(FileUtils.getFile(
        ARTIFACT_REPO_BASE_DIR + "_" + ARTIFACT_STREAM_ID_JENKINS + "-" + BUILD_NO + "-" + JENKINS_FILE_NAME));
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testDownloadArtifactAtRuntimeForBamboo() throws IOException, ExecutionException {
    String fileContent = "testBamboo";
    InputStream is = new ByteArrayInputStream(fileContent.getBytes(Charset.defaultCharset()));
    Pair<String, InputStream> pair = new ImmutablePair<>(fileContent, is);
    when(artifactCollectionTaskHelper.downloadArtifactAtRuntime(
             bambooStreamAttributes, ACCOUNT_ID, APP_ID, ACTIVITY_ID, COMMAND_UNIT_NAME, HOST_NAME))
        .thenReturn(pair);
    delegateFileManager.downloadArtifactAtRuntime(
        bambooStreamAttributes, ACCOUNT_ID, APP_ID, ACTIVITY_ID, COMMAND_UNIT_NAME, HOST_NAME);
    String text = Files.toString(
        new File(ARTIFACT_REPO_BASE_DIR + "_" + ARTIFACT_STREAM_ID_BAMBOO + "-" + BUILD_NO + "-" + BAMBOO_FILE_NAME),
        Charsets.UTF_8);
    assertThat(text).isEqualTo(fileContent);
    FileUtils.deleteQuietly(FileUtils.getFile(
        ARTIFACT_REPO_BASE_DIR + "_" + ARTIFACT_STREAM_ID_BAMBOO + "-" + BUILD_NO + "-" + BAMBOO_FILE_NAME));
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testDownloadArtifactAtRuntimeForNexus() throws IOException, ExecutionException {
    String fileContent = "testNexus";
    InputStream is = new ByteArrayInputStream(fileContent.getBytes(Charset.defaultCharset()));
    Pair<String, InputStream> pair = new ImmutablePair<>(fileContent, is);
    when(artifactCollectionTaskHelper.downloadArtifactAtRuntime(
             artifactStreamAttributesForNexus, ACCOUNT_ID, APP_ID, ACTIVITY_ID, COMMAND_UNIT_NAME, HOST_NAME))
        .thenReturn(pair);
    delegateFileManager.downloadArtifactAtRuntime(
        artifactStreamAttributesForNexus, ACCOUNT_ID, APP_ID, ACTIVITY_ID, COMMAND_UNIT_NAME, HOST_NAME);
    String text = Files.toString(
        new File(ARTIFACT_REPO_BASE_DIR + "_" + ARTIFACT_STREAM_ID_NEXUS + "-11-" + NEXUS2_FILE_NAME), Charsets.UTF_8);
    assertThat(text).isEqualTo(fileContent);
    FileUtils.deleteQuietly(
        FileUtils.getFile(ARTIFACT_REPO_BASE_DIR + "_" + ARTIFACT_STREAM_ID_NEXUS + "-11-" + NEXUS2_FILE_NAME));
  }

  private Map<String, String> mockMetadata(ArtifactStreamType artifactStreamType) {
    Map<String, String> map = new HashMap<>();
    switch (artifactStreamType) {
      case AMAZON_S3:
        map.put(ArtifactMetadataKeys.bucketName, BUCKET_NAME);
        map.put(ArtifactMetadataKeys.artifactFileName, ARTIFACT_FILE_NAME);
        map.put(ArtifactMetadataKeys.artifactPath, ARTIFACT_PATH);
        map.put(ArtifactMetadataKeys.buildNo, BUILD_NO);
        map.put(ArtifactMetadataKeys.artifactFileSize, String.valueOf(MY_SIZE));
        map.put(ArtifactMetadataKeys.key, ACCESS_KEY);
        map.put(ArtifactMetadataKeys.url, S3_URL);
        break;
      case ARTIFACTORY:
        map.put(ArtifactMetadataKeys.artifactFileName, ARTIFACT_FILE_NAME);
        map.put(ArtifactMetadataKeys.artifactPath, ARTIFACT_PATH);
        map.put(ArtifactMetadataKeys.buildNo, BUILD_NO);
        map.put(ArtifactMetadataKeys.artifactFileSize, String.valueOf(MY_SIZE));
        break;
      default:
        break;
    }
    return map;
  }
}
