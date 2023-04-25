/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.delegate.beans.artifact.ArtifactFileMetadata.builder;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.ROHITKARELIA;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.command.CommandExecutionContext.Builder.aCommandExecutionContext;
import static software.wings.utils.WingsTestConstants.ACCESS_KEY;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_FILE_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_PATH;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.BUCKET_NAME;
import static software.wings.utils.WingsTestConstants.BUILD_NO;
import static software.wings.utils.WingsTestConstants.HOST_NAME;
import static software.wings.utils.WingsTestConstants.S3_URL;
import static software.wings.utils.WingsTestConstants.SECRET_KEY;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.artifact.ArtifactFileMetadata;
import io.harness.logging.CommandExecutionStatus;
import io.harness.nexus.NexusRequest;
import io.harness.rule.Owner;
import io.harness.shell.AccessType;
import io.harness.shell.BaseScriptExecutor;

import software.wings.WingsBaseTest;
import software.wings.annotation.EncryptableSetting;
import software.wings.beans.AwsConfig;
import software.wings.beans.BambooConfig;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.artifact.AzureArtifactsArtifactStreamProtocolType;
import software.wings.beans.command.ScpCommandUnit.ScpFileCategory;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.beans.config.NexusConfig;
import software.wings.beans.dto.Log;
import software.wings.beans.infrastructure.Host;
import software.wings.beans.settings.azureartifacts.AzureArtifactsConfig;
import software.wings.beans.settings.azureartifacts.AzureArtifactsPATConfig;
import software.wings.core.ssh.executors.FileBasedScriptExecutor;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackageFileInfo;
import software.wings.helpers.ext.azure.devops.AzureArtifactsService;
import software.wings.helpers.ext.bamboo.BambooService;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.Jenkins;
import software.wings.helpers.ext.nexus.NexusService;
import software.wings.helpers.ext.nexus.NexusTwoServiceImpl;
import software.wings.service.impl.jenkins.JenkinsUtils;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.utils.ArtifactType;
import software.wings.utils.RepositoryType;
import software.wings.utils.WingsTestConstants;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ScpCommandUnitTest extends WingsBaseTest {
  private static final String JENKINS_ARTIFACT_URL_1 =
      "http://localhost:8089/job/scheduler-svn/75/artifact/build/libs/docker-scheduler-1.0-SNAPSHOT-all.jar";
  private static final String JENKINS_ARTIFACT_URL_2 =
      "http://localhost:8089/job/scheduler-svn/75/artifact/build/libs/docker-scheduler-1.0-SNAPSHOT-sources.jar";
  private static final String JENKINS_ARTIFACT_FILENAME_1 = "docker-scheduler-1.0-SNAPSHOT-all.jar";
  private static final String JENKINS_ARTIFACT_FILENAME_2 = "docker-scheduler-1.0-SNAPSHOT-sources.jar";
  private static final String NEXUS_URL =
      "https://nexus2-cdteam.harness.io/service/local/artifact/maven/content?r=releases&g=io.harness.test&a=todolist&v=7.0&p=war&e=war";
  private Host host = Host.Builder.aHost().withPublicDns(WingsTestConstants.PUBLIC_DNS).build();

  @InjectMocks private ScpCommandUnit scpCommandUnit = new ScpCommandUnit();
  @Mock BaseScriptExecutor baseExecutor;
  @Mock FileBasedScriptExecutor fileBasedScriptExecutor;
  @Mock AzureArtifactsService azureArtifactsService;
  @Mock EncryptionService encryptionService;
  @Mock JenkinsUtils jenkinsUtils;
  @Mock Jenkins jenkins;
  @Mock DelegateLogService delegateLogService;
  @Mock BambooService bambooService;
  @Mock NexusService nexusService;
  @Mock NexusTwoServiceImpl nexusTwoService;

  private SettingAttribute awsSetting =
      aSettingAttribute()
          .withUuid(SETTING_ID)
          .withValue(AwsConfig.builder().secretKey(SECRET_KEY).accessKey(ACCESS_KEY.toCharArray()).build())
          .build();

  private SettingAttribute hostConnectionAttributes =
      aSettingAttribute()
          .withValue(HostConnectionAttributes.Builder.aHostConnectionAttributes()
                         .withAccessType(AccessType.USER_PASSWORD)
                         .withAccountId(WingsTestConstants.ACCOUNT_ID)
                         .build())
          .build();

  private SettingAttribute artifactorySetting = aSettingAttribute()
                                                    .withUuid(SETTING_ID)
                                                    .withValue(ArtifactoryConfig.builder()
                                                                   .artifactoryUrl(WingsTestConstants.ARTIFACTORY_URL)
                                                                   .username("admin")
                                                                   .password("dummy123!".toCharArray())
                                                                   .build())
                                                    .build();
  private SettingAttribute azureArtifactsConfig =
      aSettingAttribute()
          .withUuid(SETTING_ID)
          .withValue(AzureArtifactsPATConfig.builder()
                         .azureDevopsUrl(WingsTestConstants.AZURE_DEVOPS_URL)
                         .pat("dummy123!".toCharArray())
                         .build())
          .build();

  private SettingAttribute jenkinsConfig = aSettingAttribute()
                                               .withUuid(SETTING_ID)
                                               .withValue(JenkinsConfig.builder()
                                                              .jenkinsUrl(WingsTestConstants.HARNESS_JENKINS)
                                                              .username("admin")
                                                              .password("dummy123!".toCharArray())
                                                              .build())
                                               .build();

  private SettingAttribute bambooSetting = aSettingAttribute()
                                               .withUuid(SETTING_ID)
                                               .withValue(BambooConfig.builder()
                                                              .bambooUrl("http://localhost:9095/")
                                                              .username("admin")
                                                              .password("admin".toCharArray())
                                                              .build())
                                               .build();

  private SettingAttribute nexusSetting = aSettingAttribute()
                                              .withUuid(SETTING_ID)
                                              .withValue(NexusConfig.builder()
                                                             .nexusUrl(WingsTestConstants.HARNESS_NEXUS)
                                                             .username("admin")
                                                             .password("dummy123!".toCharArray())
                                                             .build())
                                              .build();

  private ArtifactStreamAttributes artifactStreamAttributesForS3 =
      ArtifactStreamAttributes.builder()
          .artifactStreamType(ArtifactStreamType.AMAZON_S3.name())
          .metadataOnly(true)
          .metadata(mockMetadata(ArtifactStreamType.AMAZON_S3))
          .serverSetting(awsSetting.toDTO())
          .artifactServerEncryptedDataDetails(Collections.emptyList())
          .build();
  private ArtifactStreamAttributes artifactStreamAttributesForArtifactory =
      ArtifactStreamAttributes.builder()
          .artifactStreamType(ArtifactStreamType.ARTIFACTORY.name())
          .metadataOnly(true)
          .metadata(mockMetadata(ArtifactStreamType.ARTIFACTORY))
          .serverSetting(artifactorySetting.toDTO())
          .artifactServerEncryptedDataDetails(Collections.emptyList())
          .artifactType(ArtifactType.WAR)
          .build();

  private ArtifactStreamAttributes artifactStreamAttributesForArtifactoryRpmType =
      ArtifactStreamAttributes.builder()
          .artifactStreamType(ArtifactStreamType.ARTIFACTORY.name())
          .metadataOnly(true)
          .metadata(mockMetadata(ArtifactStreamType.ARTIFACTORY))
          .serverSetting(artifactorySetting.toDTO())
          .artifactServerEncryptedDataDetails(Collections.emptyList())
          .artifactType(ArtifactType.RPM)
          .build();

  private ArtifactStreamAttributes artifactStreamAttributesForAzureArtifacts =
      ArtifactStreamAttributes.builder()
          .artifactStreamType(ArtifactStreamType.AZURE_ARTIFACTS.name())
          .metadataOnly(true)
          .metadata(mockMetadata(ArtifactStreamType.AZURE_ARTIFACTS))
          .serverSetting(azureArtifactsConfig.toDTO())
          .artifactServerEncryptedDataDetails(Collections.emptyList())
          .protocolType(AzureArtifactsArtifactStreamProtocolType.maven.name())
          .project("PROJECT")
          .feed("FEED")
          .packageId("PACKAGE_ID")
          .packageName("GROUP_ID:ARTIFACT_ID")
          .build();

  private ArtifactStreamAttributes artifactStreamAttributesForJenkins =
      ArtifactStreamAttributes.builder()
          .artifactStreamType(ArtifactStreamType.JENKINS.name())
          .metadataOnly(true)
          .metadata(mockMetadata(ArtifactStreamType.JENKINS))
          .artifactFileMetadata(
              asList(builder().fileName(JENKINS_ARTIFACT_FILENAME_1).url(JENKINS_ARTIFACT_URL_1).build(),
                  builder().fileName(JENKINS_ARTIFACT_FILENAME_2).url(JENKINS_ARTIFACT_URL_2).build()))
          .serverSetting(jenkinsConfig.toDTO())
          .artifactStreamId(ARTIFACT_STREAM_ID)
          .jobName("scheduler")
          .artifactPaths(asList("build/libs/docker-scheduler-1.0-SNAPSHOT-all.jar",
              "build/libs/docker-scheduler-1.0-SNAPSHOT-sources.jar"))
          .artifactServerEncryptedDataDetails(Collections.emptyList())
          .build();

  private ArtifactStreamAttributes artifactStreamAttributesForJenkinsOld =
      ArtifactStreamAttributes.builder()
          .artifactStreamType(ArtifactStreamType.JENKINS.name())
          .metadataOnly(true)
          .metadata(mockMetadata(ArtifactStreamType.JENKINS))
          .serverSetting(jenkinsConfig.toDTO())
          .artifactStreamId(ARTIFACT_STREAM_ID)
          .jobName("scheduler")
          .artifactPaths(asList("build/libs/docker-scheduler-1.0-SNAPSHOT-all.jar",
              "build/libs/docker-scheduler-1.0-SNAPSHOT-sources.jar"))
          .artifactServerEncryptedDataDetails(Collections.emptyList())
          .build();

  private ArtifactStreamAttributes bambooStreamAttributes =
      ArtifactStreamAttributes.builder()
          .artifactStreamType(ArtifactStreamType.BAMBOO.name())
          .jobName("TOD-TOD")
          .artifactPaths(Arrays.asList("artifacts/todolist.tar"))
          .metadataOnly(true)
          .metadata(mockMetadata(ArtifactStreamType.BAMBOO))
          .artifactFileMetadata(
              Arrays.asList(ArtifactFileMetadata.builder()
                                .fileName("todolist.tar")
                                .url("http://localhost:9095/artifact/TOD-TOD/JOB1/build-11/artifacts/todolist.tar")
                                .build(),
                  ArtifactFileMetadata.builder()
                      .fileName("todolist.war")
                      .url("http://localhost:9095/artifact/TOD-TOD/JOB1/build-11/artifacts/todolist.war")
                      .build()))
          .serverSetting(bambooSetting.toDTO())
          .artifactServerEncryptedDataDetails(Collections.emptyList())
          .artifactStreamId(ARTIFACT_STREAM_ID)
          .build();

  private ArtifactStreamAttributes artifactStreamAttributesForNexus =
      ArtifactStreamAttributes.builder()
          .artifactStreamType(ArtifactStreamType.NEXUS.name())
          .metadataOnly(true)
          .metadata(mockMetadata(ArtifactStreamType.NEXUS))
          .artifactFileMetadata(asList(builder().fileName("todolist-7.0-sources.war").url(NEXUS_URL).build()))
          .serverSetting(nexusSetting.toDTO())
          .artifactStreamId(ARTIFACT_STREAM_ID)
          .jobName("releases")
          .groupId("io.harness.test")
          .artifactName("todolist")
          .extension("war")
          .classifier("sources")
          .artifactServerEncryptedDataDetails(Collections.emptyList())
          .build();

  private ArtifactStreamAttributes artifactStreamAttributesForNexusWithEmptyArtifactFileMetadata =
      ArtifactStreamAttributes.builder()
          .artifactStreamType(ArtifactStreamType.NEXUS.name())
          .metadataOnly(true)
          .metadata(mockMetadata(ArtifactStreamType.NEXUS))
          .artifactFileMetadata(Collections.emptyList())
          .serverSetting(nexusSetting.toDTO())
          .artifactStreamId(ARTIFACT_STREAM_ID)
          .jobName("releases")
          .groupId("io.harness.test")
          .artifactName("todolist")
          .extension("war")
          .classifier("sources")
          .repositoryType(RepositoryType.maven.name())
          .artifactServerEncryptedDataDetails(Collections.emptyList())
          .build();

  @InjectMocks
  private ShellCommandExecutionContext contextForS3 = new ShellCommandExecutionContext(
      aCommandExecutionContext().artifactStreamAttributes(artifactStreamAttributesForS3).build());

  @InjectMocks
  private ShellCommandExecutionContext contextForArtifactory = new ShellCommandExecutionContext(
      aCommandExecutionContext().artifactStreamAttributes(artifactStreamAttributesForArtifactory).build());

  @InjectMocks
  private ShellCommandExecutionContext contextForArtifactoryRpm = new ShellCommandExecutionContext(
      aCommandExecutionContext().artifactStreamAttributes(artifactStreamAttributesForArtifactoryRpmType).build());

  @InjectMocks
  ShellCommandExecutionContext contextForAzureArtifacts =
      new ShellCommandExecutionContext(aCommandExecutionContext()
                                           .artifactStreamAttributes(artifactStreamAttributesForAzureArtifacts)
                                           .metadata(mockMetadata(ArtifactStreamType.AZURE_ARTIFACTS))
                                           .build());
  @InjectMocks
  ShellCommandExecutionContext contextForBambooArtifacts =
      new ShellCommandExecutionContext(aCommandExecutionContext()
                                           .artifactStreamAttributes(bambooStreamAttributes)
                                           .metadata(mockMetadata(ArtifactStreamType.BAMBOO))
                                           .build());
  @InjectMocks
  ShellCommandExecutionContext contextForJenkins =
      new ShellCommandExecutionContext(aCommandExecutionContext()
                                           .artifactStreamAttributes(artifactStreamAttributesForJenkins)
                                           .metadata(mockMetadata(ArtifactStreamType.JENKINS))
                                           .build());

  @InjectMocks
  ShellCommandExecutionContext contextForJenkinsOld =
      new ShellCommandExecutionContext(aCommandExecutionContext()
                                           .artifactStreamAttributes(artifactStreamAttributesForJenkinsOld)
                                           .metadata(mockMetadata(ArtifactStreamType.JENKINS))
                                           .appId(APP_ID)
                                           .activityId(ACTIVITY_ID)
                                           .host(Host.Builder.aHost().withHostName(HOST_NAME).build())
                                           .build());

  public ScpCommandUnitTest() throws URISyntaxException {}

  @InjectMocks
  ShellCommandExecutionContext contextForNexusArtifacts =
      new ShellCommandExecutionContext(aCommandExecutionContext()
                                           .artifactStreamAttributes(artifactStreamAttributesForNexus)
                                           .metadata(mockMetadata(ArtifactStreamType.NEXUS))
                                           .build());

  @InjectMocks
  ShellCommandExecutionContext contextForNexusArtifactsWithEmptyArtifactFileMetadata = new ShellCommandExecutionContext(
      aCommandExecutionContext()
          .artifactStreamAttributes(artifactStreamAttributesForNexusWithEmptyArtifactFileMetadata)
          .metadata(mockMetadata(ArtifactStreamType.NEXUS))
          .host(host)
          .build());

  /**
   * Sets up mocks.
   *
   * @throws Exception the exception
   */
  @Before
  public void setup() {
    scpCommandUnit.setFileCategory(ScpFileCategory.ARTIFACTS);
    scpCommandUnit.setDestinationDirectoryPath(WingsTestConstants.DESTINATION_DIR_PATH);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldDownloadArtifactFromAmazonS3IfMetadataOnly() {
    when(fileBasedScriptExecutor.copyFiles(
             any(), any(ArtifactStreamAttributes.class), any(), any(), any(), any(), any()))
        .thenReturn(CommandExecutionStatus.SUCCESS);
    CommandExecutionStatus status = scpCommandUnit.executeInternal(contextForS3);
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldDownloadArtifactFromArtifactoryIfMetadataOnly() {
    when(fileBasedScriptExecutor.copyFiles(
             any(), any(ArtifactStreamAttributes.class), any(), any(), any(), any(), any()))
        .thenReturn(CommandExecutionStatus.SUCCESS);
    CommandExecutionStatus status = scpCommandUnit.executeInternal(contextForArtifactory);
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotDownloadArtifactFromArtifactoryForRpmType() {
    when(fileBasedScriptExecutor.copyFiles(
             any(), any(ArtifactStreamAttributes.class), any(), any(), any(), any(), any()))
        .thenReturn(CommandExecutionStatus.SUCCESS);
    CommandExecutionStatus status = scpCommandUnit.executeInternal(contextForArtifactoryRpm);
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldDownloadArtifactFromAzureArtifactsIfMetadataOnly() {
    when(azureArtifactsService.listFiles(any(AzureArtifactsConfig.class), any(), any(), anyMap(), eq(false)))
        .thenReturn(Collections.singletonList(new AzureArtifactsPackageFileInfo(ARTIFACT_FILE_NAME, 10)));
    when(fileBasedScriptExecutor.copyFiles(anyString(), any(ArtifactStreamAttributes.class), anyString(), anyString(),
             anyString(), anyString(), anyString()))
        .thenReturn(CommandExecutionStatus.SUCCESS);
    CommandExecutionStatus status = scpCommandUnit.executeInternal(contextForAzureArtifacts);
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldDownloadArtifactFromJenkinsIfMetadataOnly() {
    when(encryptionService.decrypt(any(), any(), eq(false))).thenReturn(null);
    when(jenkinsUtils.getJenkins(any())).thenReturn(jenkins);
    when(jenkins.getFileSize(anyString(), anyString(), eq(JENKINS_ARTIFACT_FILENAME_1))).thenReturn(123L);
    when(jenkins.getFileSize(anyString(), anyString(), eq(JENKINS_ARTIFACT_FILENAME_2))).thenReturn(2323L);
    when(fileBasedScriptExecutor.copyFiles(anyString(), any(ArtifactStreamAttributes.class), anyString(), anyString(),
             anyString(), anyString(), anyString()))
        .thenReturn(CommandExecutionStatus.SUCCESS);
    when(fileBasedScriptExecutor.copyFiles(anyString(), any(ArtifactStreamAttributes.class), anyString(), anyString(),
             anyString(), anyString(), anyString()))
        .thenReturn(CommandExecutionStatus.SUCCESS);
    CommandExecutionStatus status = scpCommandUnit.executeInternal(contextForJenkins);
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotDownloadArtifactFromJenkinsIfNoArtifactFileMetadata() {
    CommandExecutionStatus status = scpCommandUnit.executeInternal(contextForJenkinsOld);
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(delegateLogService, times(1)).save(any(), any(Log.class));
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldDownloadArtifactFromBambooIfMetadataOnly() {
    when(bambooService.getFileSize(any(), any(), eq("todolist.tar"),
             eq("http://localhost:9095/artifact/TOD-TOD/JOB1/build-11/artifacts/todolist.tar")))
        .thenReturn(1234L);
    when(bambooService.getFileSize(any(), any(), eq("todolist.war"),
             eq("http://localhost:9095/artifact/TOD-TOD/JOB1/build-11/artifacts/todolist.war")))
        .thenReturn(345L);
    when(fileBasedScriptExecutor.copyFiles(anyString(), any(ArtifactStreamAttributes.class), anyString(), anyString(),
             anyString(), anyString(), anyString()))
        .thenReturn(CommandExecutionStatus.SUCCESS);
    when(fileBasedScriptExecutor.copyFiles(anyString(), any(ArtifactStreamAttributes.class), anyString(), anyString(),
             anyString(), anyString(), anyString()))
        .thenReturn(CommandExecutionStatus.SUCCESS);
    CommandExecutionStatus status = scpCommandUnit.executeInternal(contextForBambooArtifacts);
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldDownloadArtifactFromNexusIfMetadataOnly() {
    when(nexusService.getFileSize(any(), eq("todolist.tar"), eq(NEXUS_URL))).thenReturn(1234L);
    when(fileBasedScriptExecutor.copyFiles(anyString(), any(ArtifactStreamAttributes.class), anyString(), anyString(),
             anyString(), anyString(), anyString()))
        .thenReturn(CommandExecutionStatus.SUCCESS);
    CommandExecutionStatus status = scpCommandUnit.executeInternal(contextForNexusArtifacts);
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldReturnSuccessIfArtifactFileMetadataIfEmpty() {
    when(nexusService.getFileSize(any(), eq("todolist.tar"), eq(NEXUS_URL))).thenReturn(1234L);
    when(fileBasedScriptExecutor.copyFiles(anyString(), any(ArtifactStreamAttributes.class), anyString(), anyString(),
             anyString(), anyString(), anyString()))
        .thenReturn(CommandExecutionStatus.SUCCESS);
    ExecutionLogCallback mockCallBack = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallBack).saveExecutionLog(anyString(), any());
    CommandExecutionStatus status =
        scpCommandUnit.executeInternal(contextForNexusArtifactsWithEmptyArtifactFileMetadata);
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldFetchDownloadIfArtifactFileMetadataIfEmpty() throws IOException {
    ArtifactFileMetadata artifactFileMetadata =
        ArtifactFileMetadata.builder()
            .fileName("todolist")
            .url(
                "https://nexus2-cdteam.harness.io/service/local/artifact/maven/content?r=releases&g=io.harness.test&a=todolist&v=7.0&p=war&e=war&c=sources")
            .build();

    List<ArtifactFileMetadata> artifactFileMetadataList = new ArrayList<>();
    artifactFileMetadataList.add(artifactFileMetadata);

    BuildDetails buildDetails =
        BuildDetails.Builder.aBuildDetails().withArtifactDownloadMetadata(artifactFileMetadataList).build();
    List<BuildDetails> buildDetailsList = new ArrayList<>();
    buildDetailsList.add(buildDetails);
    when(encryptionService.decrypt(any(EncryptableSetting.class), anyList(), eq(false)))
        .thenReturn((EncryptableSetting) hostConnectionAttributes.getValue());
    when(nexusTwoService.getVersion(any(NexusRequest.class), any(), any(), any(), any(), any(), any()))
        .thenReturn(buildDetailsList);
    when(nexusService.getFileSize(any(), eq("todolist.tar"), eq(NEXUS_URL))).thenReturn(1234L);
    when(fileBasedScriptExecutor.copyFiles(anyString(), any(ArtifactStreamAttributes.class), anyString(), anyString(),
             anyString(), anyString(), anyString()))
        .thenReturn(CommandExecutionStatus.SUCCESS);
    ExecutionLogCallback mockCallBack = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallBack).saveExecutionLog(anyString(), any());
    CommandExecutionStatus status =
        scpCommandUnit.executeInternal(contextForNexusArtifactsWithEmptyArtifactFileMetadata);
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  private Map<String, String> mockMetadata(ArtifactStreamType artifactStreamType) {
    Map<String, String> map = new HashMap<>();
    switch (artifactStreamType) {
      case AMAZON_S3:
        map.put(ArtifactMetadataKeys.bucketName, BUCKET_NAME);
        map.put(ArtifactMetadataKeys.artifactFileName, ARTIFACT_FILE_NAME);
        map.put(ArtifactMetadataKeys.artifactPath, ARTIFACT_PATH);
        map.put(ArtifactMetadataKeys.buildNo, BUILD_NO);
        map.put(ArtifactMetadataKeys.artifactFileSize, String.valueOf(WingsTestConstants.ARTIFACT_FILE_SIZE));
        map.put(ArtifactMetadataKeys.key, ACCESS_KEY);
        map.put(ArtifactMetadataKeys.url, S3_URL);
        break;
      case ARTIFACTORY:
        map.put(ArtifactMetadataKeys.artifactFileName, ARTIFACT_FILE_NAME);
        map.put(ArtifactMetadataKeys.artifactPath, ARTIFACT_PATH);
        map.put(ArtifactMetadataKeys.buildNo, BUILD_NO);
        map.put(ArtifactMetadataKeys.artifactFileSize, String.valueOf(WingsTestConstants.ARTIFACT_FILE_SIZE));
        break;
      case AZURE_ARTIFACTS:
        map.put(ArtifactMetadataKeys.version, BUILD_NO);
        map.put(ArtifactMetadataKeys.buildNo, BUILD_NO);
        break;
      case JENKINS:
        map.put(ArtifactMetadataKeys.buildNo, BUILD_NO);
        break;
      case BAMBOO:
        map.put(ArtifactMetadataKeys.buildNo, "11");
        break;
      case NEXUS:
        map.put(ArtifactMetadataKeys.buildNo, "7.0");
        break;
      default:
        break;
    }
    return map;
  }
}
