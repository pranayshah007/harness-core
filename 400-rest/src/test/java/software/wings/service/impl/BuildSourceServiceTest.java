/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessModule._930_DELEGATE_TASKS;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;
import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.RAFAEL;

import static software.wings.beans.template.artifactsource.CustomRepositoryMapping.AttributeMapping.builder;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.JOB_NAME;
import static software.wings.utils.WingsTestConstants.PROJECT_ID;
import static software.wings.utils.WingsTestConstants.REPOSITORY_FORMAT;
import static software.wings.utils.WingsTestConstants.REPO_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.utils.WingsTestConstants.TRIGGER_ID;
import static software.wings.utils.WingsTestConstants.TRIGGER_NAME;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ArtifactMetadata;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.BambooConfig;
import software.wings.beans.DockerConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SftpConfig;
import software.wings.beans.SmbConfig;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.artifact.AmazonS3ArtifactStream;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.artifact.AzureArtifactsArtifactStreamProtocolType;
import software.wings.beans.artifact.BambooArtifactStream;
import software.wings.beans.artifact.CustomArtifactStream;
import software.wings.beans.artifact.GcsArtifactStream;
import software.wings.beans.artifact.JenkinsArtifactStream;
import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.beans.command.GcbTaskParams;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.beans.config.NexusConfig;
import software.wings.beans.settings.azureartifacts.AzureArtifactsPATConfig;
import software.wings.beans.template.artifactsource.CustomRepositoryMapping;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.delegatetasks.GcbDelegateResponse;
import software.wings.helpers.ext.azure.devops.AzureArtifactsFeed;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackage;
import software.wings.helpers.ext.azure.devops.AzureDevopsProject;
import software.wings.helpers.ext.gcb.GcbService;
import software.wings.helpers.ext.gcb.models.GcbTrigger;
import software.wings.helpers.ext.gcs.GcsService;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.helpers.ext.nexus.NexusService;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AmazonS3BuildService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.AzureArtifactsBuildService;
import software.wings.service.intfc.BambooBuildService;
import software.wings.service.intfc.GcsBuildService;
import software.wings.service.intfc.JenkinsBuildService;
import software.wings.service.intfc.NexusBuildService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.SftpBuildService;
import software.wings.service.intfc.SmbBuildService;
import software.wings.service.intfc.artifact.CustomBuildSourceService;
import software.wings.utils.ArtifactType;
import software.wings.utils.RepositoryFormat;
import software.wings.utils.RepositoryType;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@TargetModule(_930_DELEGATE_TASKS)
@OwnedBy(CDC)
public class BuildSourceServiceTest extends WingsBaseTest {
  public static final String DELEGATE_SELECTOR = "delegateSelector";
  private final SettingsService settingsService = Mockito.mock(SettingsServiceImpl.class);
  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private ArtifactService artifactService;
  @Mock private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Mock BambooBuildService bambooBuildService;
  @Mock GcsBuildService gcsBuildService;
  @Mock NexusBuildService nexusBuildService;
  @Mock SmbBuildService smbBuildService;
  @Mock SftpBuildService sftpBuildService;
  @Mock JenkinsBuildService jenkinsBuildService;
  @Mock CustomBuildSourceService customBuildSourceService;
  @Mock GcsService gcsService;
  @Mock GcbService gcbService;
  @Mock NexusService nexusService;
  @Mock AmazonS3BuildService amazonS3BuildService;
  @Mock AzureArtifactsBuildService azureArtifactsBuildService;
  @Mock ServiceResourceService serviceResourceService;
  @Mock DelegateServiceImpl delegateService;
  @Inject @InjectMocks private BuildSourceServiceImpl buildSourceService;
  @Mock DelegateProxyFactory delegateProxyFactory;
  @Mock ExpressionEvaluator evaluator;
  @Mock FeatureFlagService featureFlagService;
  @Mock AccountService accountService;

  @Before
  public void setup() {
    when(accountService.isCertValidationRequired(anyString())).thenReturn(false);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetJobsWithAppIdForBamboo() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(BambooConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(bambooBuildService);
    when(bambooBuildService.getJobs(any(), any(), any()))
        .thenReturn(asList(new JobDetails("USERDEFINEDPROJECTKEY-RIS", false), new JobDetails("SAM-BUIL", false),
            new JobDetails("SAM-SAM", false), new JobDetails("TOD-TODIR", false),
            new JobDetails("USERDEFINEDPROJECTKEY-TES", false), new JobDetails("TOD-TOD", false)));
    Set<JobDetails> jobDetails = buildSourceService.getJobs(APP_ID, SETTING_ID, null);
    assertThat(jobDetails).isNotEmpty();
    assertThat(jobDetails.size()).isEqualTo(6);
    assertThat(jobDetails)
        .extracting(JobDetails::getJobName)
        .containsSequence(
            "SAM-BUIL", "SAM-SAM", "TOD-TOD", "TOD-TODIR", "USERDEFINEDPROJECTKEY-RIS", "USERDEFINEDPROJECTKEY-TES");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetProjectWithAppAndSettingIdForGCS() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(GcpConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(gcsService);
    when(gcsService.getProject(any(), any())).thenReturn("exploration-161417");
    assertThat(buildSourceService.getProject(APP_ID, SETTING_ID)).isEqualTo("exploration-161417");
  }

  @Test(expected = WingsException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetProjectWithInvalidSettingAttribute() {
    when(settingsService.get(SETTING_ID)).thenReturn(null);
    buildSourceService.getProject(APP_ID, SETTING_ID);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetProjectWithSettingIdForGCS() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(GcpConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(bambooBuildService);

    when(gcsService.getProject(any(), any())).thenReturn("exploration-161417");
    assertThat(buildSourceService.getProject(SETTING_ID)).isEqualTo("exploration-161417");
  }

  @Test(expected = WingsException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetProjectWithInvalidSettingId() {
    when(settingsService.get(SETTING_ID)).thenReturn(null);
    buildSourceService.getProject(SETTING_ID);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetBucketsWithAppAndSettingIdForGCS() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(GcpConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(gcsBuildService);
    Map<String, String> map = new HashMap<>();
    map.put("functional-test", "functional-test");
    map.put("playground", "playground");
    map.put("staging", "staging");
    when(gcsBuildService.getBuckets(any(), anyString(), any())).thenReturn(map);
    Map<String, String> bucketsMap = buildSourceService.getBuckets(APP_ID, PROJECT_ID, SETTING_ID);
    assertThat(bucketsMap).isNotEmpty();
    assertThat(bucketsMap.size()).isEqualTo(3);
    assertThat(bucketsMap).containsKeys("functional-test", "playground", "staging");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetBucketsWithSettingIdForGCS() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(GcpConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(gcsBuildService);

    Map<String, String> map = new HashMap<>();
    map.put("functional-test", "functional-test");
    map.put("playground", "playground");
    map.put("staging", "staging");
    when(gcsBuildService.getBuckets(any(), anyString(), any())).thenReturn(map);
    Map<String, String> bucketsMap = buildSourceService.getBuckets(PROJECT_ID, SETTING_ID);
    assertThat(bucketsMap).isNotEmpty();
    assertThat(bucketsMap.size()).isEqualTo(3);
    assertThat(bucketsMap).containsKeys("functional-test", "playground", "staging");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testFetchNexusPackageNamesWithAppForNexus() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(NexusConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(nexusBuildService);
    List<String> groupIds = new ArrayList<>();
    groupIds.add("group1");
    groupIds.add("group2");
    when(nexusBuildService.getGroupIds(anyString(), anyString(), any(), any())).thenReturn(groupIds);
    Set<String> packageNames =
        buildSourceService.fetchNexusPackageNames(APP_ID, REPO_NAME, REPOSITORY_FORMAT, SETTING_ID);
    assertThat(packageNames).isNotEmpty();
    assertThat(packageNames.size()).isEqualTo(2);
    assertThat(packageNames).contains("group1", "group2");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testFetchNexusPackageNamesForNexus() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(NexusConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(nexusBuildService);
    List<String> groupIds = new ArrayList<>();
    groupIds.add("group1");
    groupIds.add("group2");
    when(nexusBuildService.getGroupIds(anyString(), anyString(), any(), any())).thenReturn(groupIds);
    Set<String> packageNames = buildSourceService.fetchNexusPackageNames(REPO_NAME, REPOSITORY_FORMAT, SETTING_ID);
    assertThat(packageNames).isNotEmpty();
    assertThat(packageNames.size()).isEqualTo(2);
    assertThat(packageNames).contains("group1", "group2");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetSmbPathsWithAppAndSettingIdForSmb() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(SmbConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(smbBuildService);
    when(smbBuildService.getSmbPaths(any(), any())).thenReturn(asList("path1", "path2"));
    List<String> smbPaths = buildSourceService.getSmbPaths(APP_ID, SETTING_ID);
    assertThat(smbPaths).isNotEmpty();
    assertThat(smbPaths.size()).isEqualTo(2);
    assertThat(smbPaths).containsExactly("path1", "path2");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetSmbPathsWithSettingIdForSmb() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(SmbConfig.builder().build())
                                            .build();
    doReturn(settingAttribute).when(settingsService).get(SETTING_ID);
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(smbBuildService);
    when(smbBuildService.getSmbPaths(any(), any())).thenReturn(asList("path1", "path2"));
    List<String> smbPaths = buildSourceService.getSmbPaths(SETTING_ID);
    assertThat(smbPaths).isNotEmpty();
    assertThat(smbPaths.size()).isEqualTo(2);
    assertThat(smbPaths).containsExactly("path1", "path2");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetSftpPathsWithSettingIdForSftp() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(SftpConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(sftpBuildService);
    when(sftpBuildService.getArtifactPathsByStreamType(any(), any(), anyString()))
        .thenReturn(asList("sftp1", "sftp2", "sftp3"));
    List<String> sftpPaths =
        buildSourceService.getArtifactPathsByStreamType(SETTING_ID, ArtifactStreamType.SFTP.name());
    assertThat(sftpPaths).isNotEmpty();
    assertThat(sftpPaths.size()).isEqualTo(3);
    assertThat(sftpPaths).containsExactly("sftp1", "sftp2", "sftp3");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetSftpPathsWithAppIdSettingIdForSftp() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(SftpConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(sftpBuildService);
    when(sftpBuildService.getArtifactPathsByStreamType(any(), any(), anyString()))
        .thenReturn(asList("sftp1", "sftp2", "sftp3"));
    List<String> sftpPaths =
        buildSourceService.getArtifactPathsByStreamType(APP_ID, SETTING_ID, ArtifactStreamType.SFTP.name());
    assertThat(sftpPaths).isNotEmpty();
    assertThat(sftpPaths.size()).isEqualTo(3);
    assertThat(sftpPaths).containsExactly("sftp1", "sftp2", "sftp3");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetGroupIdsWithRepositoryFormatForNexus() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(NexusConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(nexusBuildService);
    List<String> groupIds = new ArrayList<>();
    groupIds.add("group1");
    groupIds.add("group2");
    when(nexusBuildService.getGroupIds(anyString(), anyString(), any(), any())).thenReturn(groupIds);
    Set<String> ids = buildSourceService.getGroupIdsForRepositoryFormat(JOB_NAME, SETTING_ID, REPOSITORY_FORMAT);
    assertThat(ids).isNotEmpty();
    assertThat(ids.size()).isEqualTo(2);
    assertThat(ids).containsExactly("group1", "group2");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetGroupIdsWithSettingIdForNexus() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(NexusConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(nexusBuildService);
    List<String> groupIds = new ArrayList<>();
    groupIds.add("group1");
    groupIds.add("group2");
    when(nexusBuildService.getGroupIds(anyString(), any(), any())).thenReturn(groupIds);
    Set<String> ids = buildSourceService.getGroupIds(REPOSITORY_FORMAT, SETTING_ID);
    assertThat(ids).isNotEmpty();
    assertThat(ids.size()).isEqualTo(2);
    assertThat(ids).containsExactly("group1", "group2");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetPlansForNexus() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(NexusConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(nexusBuildService);
    Map<String, String> map = new HashMap<>();
    map.put("Todolist_Snapshots", "Todolist Snapshots");
    map.put("releases", "Releases");
    map.put("snapshots", "Snapshots");
    map.put("thirdparty", "3rd party");
    when(nexusBuildService.getPlans(any(), any())).thenReturn(map);
    Map<String, String> plans = buildSourceService.getPlans(SETTING_ID, ArtifactStreamType.NEXUS.name());
    assertThat(plans).isNotEmpty();
    assertThat(plans.size()).isEqualTo(4);
    assertThat(plans).containsKeys("Todolist_Snapshots", "releases", "snapshots", "thirdparty");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetPlansWithRepositoryFormatForNexus() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(NexusConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(nexusBuildService);
    Map<String, String> map = new HashMap<>();
    map.put("Todolist_Snapshots", "Todolist Snapshots");
    map.put("releases", "Releases");
    map.put("snapshots", "Snapshots");
    map.put("thirdparty", "3rd party");
    when(nexusBuildService.getPlans(any(), any(), any(RepositoryFormat.class))).thenReturn(map);
    Map<String, String> plans = buildSourceService.getPlansForRepositoryFormat(
        SETTING_ID, ArtifactStreamType.NEXUS.name(), RepositoryFormat.maven);
    assertThat(plans).isNotEmpty();
    assertThat(plans.size()).isEqualTo(4);
    assertThat(plans).containsKeys("Todolist_Snapshots", "releases", "snapshots", "thirdparty");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetPlansWithRepositoryTypeForNexus() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(NexusConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(nexusBuildService);
    Map<String, String> map = new HashMap<>();
    map.put("Todolist_Snapshots", "Todolist Snapshots");
    map.put("releases", "Releases");
    map.put("snapshots", "Snapshots");
    map.put("thirdparty", "3rd party");
    when(nexusBuildService.getPlans(any(), any(), any(RepositoryType.class))).thenReturn(map);
    Map<String, String> plans =
        buildSourceService.getPlansForRepositoryType(SETTING_ID, ArtifactStreamType.NEXUS.name(), RepositoryType.maven);
    assertThat(plans).isNotEmpty();
    assertThat(plans.size()).isEqualTo(4);
    assertThat(plans).containsKeys("Todolist_Snapshots", "releases", "snapshots", "thirdparty");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetPlansWithAppIdAndSettingIdForNexus() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(NexusConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(nexusBuildService);
    Map<String, String> map = new HashMap<>();
    map.put("Todolist_Snapshots", "Todolist Snapshots");
    map.put("releases", "Releases");
    map.put("snapshots", "Snapshots");
    map.put("thirdparty", "3rd party");
    when(nexusBuildService.getPlans(any(), any())).thenReturn(map);
    Map<String, String> plans = buildSourceService.getPlans(APP_ID, SETTING_ID, ArtifactStreamType.NEXUS.name());
    assertThat(plans).isNotEmpty();
    assertThat(plans.size()).isEqualTo(4);
    assertThat(plans).containsKeys("Todolist_Snapshots", "releases", "snapshots", "thirdparty");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetPlansWithAppIdAndSettingIdRepoTypeForNexus() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(NexusConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(nexusBuildService);
    Map<String, String> map = new HashMap<>();
    map.put("Todolist_Snapshots", "Todolist Snapshots");
    map.put("releases", "Releases");
    map.put("snapshots", "Snapshots");
    map.put("thirdparty", "3rd party");
    when(nexusBuildService.getPlans(any(), any(), any(), anyString())).thenReturn(map);
    when(serviceResourceService.get(anyString(), anyString(), anyBoolean())).thenReturn(Service.builder().build());
    Map<String, String> plans = buildSourceService.getPlans(
        APP_ID, SETTING_ID, SERVICE_ID, ArtifactStreamType.NEXUS.name(), RepositoryType.maven.name());
    assertThat(plans).isNotEmpty();
    assertThat(plans.size()).isEqualTo(4);
    assertThat(plans).containsKeys("Todolist_Snapshots", "releases", "snapshots", "thirdparty");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetArtifactPathsWithAppIdForAmazonS3() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(AwsConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(amazonS3BuildService);
    when(amazonS3BuildService.getArtifactPaths(any(), any(), any(), anyList()))
        .thenReturn(asList("todolist.war", "todolist.jar"));
    Set<String> artifactPaths = buildSourceService.getArtifactPaths(
        APP_ID, "aaditi-todolist-test", SETTING_ID, null, ArtifactStreamType.AMAZON_S3.name());
    assertThat(artifactPaths).isNotEmpty();
    assertThat(artifactPaths.size()).isEqualTo(2);
    assertThat(artifactPaths).contains("todolist.war", "todolist.jar");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetArtifactPathsWithAppIdForNexus2Maven() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(NexusConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(nexusBuildService);
    when(nexusBuildService.getArtifactPaths(anyString(), anyString(), any(), anyList(), anyString()))
        .thenReturn(asList("myartifact"));
    Set<String> artifactPaths = buildSourceService.getArtifactPaths(APP_ID, "maven-releases", SETTING_ID, "mygroup",
        ArtifactStreamType.NEXUS.name(), RepositoryFormat.maven.name());
    assertThat(artifactPaths).isNotEmpty();
    assertThat(artifactPaths.size()).isEqualTo(1);
    assertThat(artifactPaths).contains("myartifact");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildForAmazonS3AtConnector() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(AwsConfig.builder().build())
                                            .build();
    AmazonS3ArtifactStream amazonS3ArtifactStream = AmazonS3ArtifactStream.builder()
                                                        .appId(APP_ID)
                                                        .jobname("aaditi-todolist-test")
                                                        .artifactPaths(asList("todolist.war"))
                                                        .build();
    when(artifactStreamService.get(anyString())).thenReturn(amazonS3ArtifactStream);
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(amazonS3BuildService);
    when(amazonS3BuildService.getLastSuccessfulBuild(anyString(), any(), any(), anyList())).thenReturn(null);
    BuildDetails buildDetails = buildSourceService.getLastSuccessfulBuild(ARTIFACT_STREAM_ID, SETTING_ID);
    assertThat(buildDetails).isNull();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildWithAppIdForAmazonS3() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(AwsConfig.builder().build())
                                            .build();
    AmazonS3ArtifactStream amazonS3ArtifactStream = AmazonS3ArtifactStream.builder()
                                                        .appId(APP_ID)
                                                        .jobname("aaditi-todolist-test")
                                                        .artifactPaths(asList("todolist.war"))
                                                        .build();
    when(artifactStreamServiceBindingService.getService(any(), any(), anyBoolean()))
        .thenReturn(Service.builder().artifactType(ArtifactType.WAR).build());
    when(artifactStreamService.get(anyString())).thenReturn(amazonS3ArtifactStream);
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(amazonS3BuildService);
    when(amazonS3BuildService.getLastSuccessfulBuild(anyString(), any(), any(), anyList())).thenReturn(null);
    BuildDetails buildDetails = buildSourceService.getLastSuccessfulBuild(APP_ID, ARTIFACT_STREAM_ID, SETTING_ID);
    assertThat(buildDetails).isNull();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildForJenkins() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(JenkinsConfig.builder().build())
                                            .build();
    JenkinsArtifactStream jenkinsArtifactStream = JenkinsArtifactStream.builder()
                                                      .appId(APP_ID)
                                                      .jobname("harness-samples")
                                                      .artifactPaths(asList("echo/target/echo.war"))
                                                      .build();
    when(artifactStreamService.get(anyString())).thenReturn(jenkinsArtifactStream);
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(jenkinsBuildService);
    Map<String, String> map = new HashMap<>();
    map.put("url", "https://jenkinsint.harness.io/job/harness-samples/5/");
    map.put("buildNo", "5");
    map.put("buildFullDisplayName", "harness-samples #5");
    when(jenkinsBuildService.getLastSuccessfulBuild(anyString(), any(), any(), anyList()))
        .thenReturn(BuildDetails.Builder.aBuildDetails()
                        .withNumber("5")
                        .withRevision("qeqweqed")
                        .withArtifactPath("todolist.war")
                        .withArtifactFileSize("45887")
                        .withUiDisplayName("Build# 5")
                        .withBuildParameters(map)
                        .build());
    BuildDetails buildDetails = buildSourceService.getLastSuccessfulBuild(ARTIFACT_STREAM_ID, SETTING_ID);
    assertThat(buildDetails).isNotNull();
    assertThat(buildDetails.getNumber()).isEqualTo("5");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildWithAppIdForJenkins() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(JenkinsConfig.builder().build())
                                            .build();
    JenkinsArtifactStream jenkinsArtifactStream = JenkinsArtifactStream.builder()
                                                      .appId(APP_ID)
                                                      .jobname("harness-samples")
                                                      .artifactPaths(asList("echo/target/echo.war"))
                                                      .build();
    when(artifactStreamService.get(anyString())).thenReturn(jenkinsArtifactStream);
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(artifactStreamServiceBindingService.getService(any(), any(), anyBoolean()))
        .thenReturn(Service.builder().artifactType(ArtifactType.JAR).build());
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(jenkinsBuildService);
    Map<String, String> map = new HashMap<>();
    map.put("url", "https://jenkinsint.harness.io/job/harness-samples/5/");
    map.put("buildNo", "5");
    map.put("buildFullDisplayName", "harness-samples #5");
    when(jenkinsBuildService.getLastSuccessfulBuild(anyString(), any(), any(), anyList()))
        .thenReturn(BuildDetails.Builder.aBuildDetails()
                        .withNumber("5")
                        .withRevision("qeqweqed")
                        .withArtifactPath("todolist.war")
                        .withArtifactFileSize("45887")
                        .withUiDisplayName("Build# 5")
                        .withBuildParameters(map)
                        .build());
    BuildDetails buildDetails = buildSourceService.getLastSuccessfulBuild(APP_ID, ARTIFACT_STREAM_ID, SETTING_ID);
    assertThat(buildDetails).isNotNull();
    assertThat(buildDetails.getNumber()).isEqualTo("5");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildForBamboo() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(BambooConfig.builder().build())
                                            .build();
    BambooArtifactStream bambooArtifactStream = BambooArtifactStream.builder()
                                                    .appId(APP_ID)
                                                    .jobname("TOD-TOD")
                                                    .artifactPaths(asList("artifacts/todolist.war"))
                                                    .build();
    when(artifactStreamService.get(anyString())).thenReturn(bambooArtifactStream);
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(bambooBuildService);
    Map<String, String> map = new HashMap<>();
    map.put("buildNo", "210");
    map.put("url", "http://ec2-18-208-86-222.compute-1.amazonaws.com:8085/rest/api/latest/result/TOD-TOD-210");
    when(bambooBuildService.getLastSuccessfulBuild(anyString(), any(), any(), anyList()))
        .thenReturn(BuildDetails.Builder.aBuildDetails()
                        .withNumber("210")
                        .withRevision("revision")
                        .withBuildUrl(
                            "http://ec2-18-208-86-222.compute-1.amazonaws.com:8085/rest/api/latest/result/TOD-TOD-210")
                        .withUiDisplayName("Build# 210")
                        .withBuildParameters(map)
                        .build());
    BuildDetails buildDetails = buildSourceService.getLastSuccessfulBuild(ARTIFACT_STREAM_ID, SETTING_ID);
    assertThat(buildDetails).isNotNull();
    assertThat(buildDetails.getNumber()).isEqualTo("210");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testCollectArtifactWithoutBuildDetailsShouldFail() {
    buildSourceService.collectArtifact(APP_ID, ARTIFACT_STREAM_ID, null);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetJobsForBambooAtConnector() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(BambooConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(bambooBuildService);
    when(bambooBuildService.getJobs(any(), any(), any()))
        .thenReturn(asList(new JobDetails("USERDEFINEDPROJECTKEY-RIS", false), new JobDetails("SAM-BUIL", false),
            new JobDetails("SAM-SAM", false), new JobDetails("TOD-TODIR", false),
            new JobDetails("USERDEFINEDPROJECTKEY-TES", false), new JobDetails("TOD-TOD", false)));
    Set<JobDetails> jobDetails = buildSourceService.getJobs(SETTING_ID, null);
    assertThat(jobDetails).isNotEmpty();
    assertThat(jobDetails.size()).isEqualTo(6);
    assertThat(jobDetails)
        .extracting(JobDetails::getJobName)
        .containsSequence(
            "SAM-BUIL", "SAM-SAM", "TOD-TOD", "TOD-TODIR", "USERDEFINEDPROJECTKEY-RIS", "USERDEFINEDPROJECTKEY-TES");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetArtifactPathsForAmazonS3AtConnector() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(AwsConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(amazonS3BuildService);
    when(amazonS3BuildService.getArtifactPaths(any(), any(), any(), anyList()))
        .thenReturn(asList("todolist.war", "todolist.jar"));
    Set<String> artifactPaths = buildSourceService.getArtifactPaths(
        "aaditi-todolist-test", SETTING_ID, null, ArtifactStreamType.AMAZON_S3.name());
    assertThat(artifactPaths).isNotEmpty();
    assertThat(artifactPaths.size()).isEqualTo(2);
    assertThat(artifactPaths).contains("todolist.war", "todolist.jar");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetArtifactPathsForNexus2MavenAtConnector() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(NexusConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(nexusBuildService);
    when(nexusBuildService.getArtifactPaths(anyString(), anyString(), any(), anyList(), anyString()))
        .thenReturn(asList("myartifact"));
    Set<String> artifactPaths = buildSourceService.getArtifactPathsForRepositoryFormat(
        "maven-releases", SETTING_ID, "mygroup", ArtifactStreamType.NEXUS.name(), RepositoryFormat.maven.name());
    assertThat(artifactPaths).isNotEmpty();
    assertThat(artifactPaths.size()).isEqualTo(1);
    assertThat(artifactPaths).contains("myartifact");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetJobWithAppIdForJenkins() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(JenkinsConfig.builder()
                                                           .jenkinsUrl("http://jenkins.harness.io")
                                                           .username("username")
                                                           .password("password".toCharArray())
                                                           .build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(jenkinsBuildService);
    JobDetails.JobParameter jobParameter = new JobDetails.JobParameter();
    jobParameter.setName("branch");
    jobParameter.setDefaultValue("release");
    jobParameter.setDescription("description");
    JobDetails jobDetails = new JobDetails(JOB_NAME, "URL", asList(jobParameter));
    when(jenkinsBuildService.getJob(anyString(), any(), any())).thenReturn(jobDetails);
    JobDetails actualJobDetails = buildSourceService.getJob(APP_ID, SETTING_ID, JOB_NAME);
    assertThat(actualJobDetails).isNotNull();
    assertThat(actualJobDetails.getJobName()).isEqualTo(JOB_NAME);
    assertThat(actualJobDetails.getUrl()).isEqualTo("URL");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetGroupIds() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(NexusConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(nexusBuildService);
    List<String> groupIds = new ArrayList<>();
    groupIds.add("group1");
    groupIds.add("group2");
    when(nexusBuildService.getGroupIds(anyString(), any(), any(), any())).thenReturn(groupIds);
    Set<String> ids = buildSourceService.getGroupIds(APP_ID, "harness-maven", SETTING_ID);
    assertThat(ids).isNotEmpty();
    assertThat(ids.size()).isEqualTo(2);
    assertThat(ids).contains("group1", "group2");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetGroupIdsWithRepositoryFormat() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(NexusConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(nexusBuildService);
    List<String> groupIds = new ArrayList<>();
    groupIds.add("group1");
    groupIds.add("group2");
    when(nexusBuildService.getGroupIds(anyString(), anyString(), any(), any())).thenReturn(groupIds);
    Set<String> ids =
        buildSourceService.getGroupIds(APP_ID, "harness-maven", SETTING_ID, RepositoryFormat.maven.name());
    assertThat(ids).isNotEmpty();
    assertThat(ids.size()).isEqualTo(2);
    assertThat(ids).contains("group1", "group2");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void getBuildsWithInvalidSettingAttribute() {
    BambooArtifactStream bambooArtifactStream = BambooArtifactStream.builder()
                                                    .appId(APP_ID)
                                                    .jobname("TOD-TOD")
                                                    .artifactPaths(asList("artifacts/todolist.war"))
                                                    .build();
    when(artifactStreamService.get(anyString())).thenReturn(bambooArtifactStream);
    when(settingsService.get(SETTING_ID)).thenReturn(null);
    assertThat(buildSourceService.getBuilds(APP_ID, ARTIFACT_STREAM_ID, SETTING_ID)).isEmpty();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetBuilds() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(BambooConfig.builder().build())
                                            .build();
    BambooArtifactStream bambooArtifactStream = BambooArtifactStream.builder()
                                                    .appId(APP_ID)
                                                    .jobname("TOD-TOD")
                                                    .artifactPaths(asList("artifacts/todolist.war"))
                                                    .build();
    when(artifactStreamService.get(anyString())).thenReturn(bambooArtifactStream);
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(bambooBuildService);
    Map<String, String> map = new HashMap<>();
    map.put("buildNo", "210");
    map.put("url", "http://ec2-18-208-86-222.compute-1.amazonaws.com:8085/rest/api/latest/result/TOD-TOD-210");
    BuildDetails bd1 =
        BuildDetails.Builder.aBuildDetails()
            .withNumber("210")
            .withRevision("revision")
            .withBuildUrl("http://ec2-18-208-86-222.compute-1.amazonaws.com:8085/rest/api/latest/result/TOD-TOD-210")
            .withUiDisplayName("Build# 210")
            .withBuildParameters(map)
            .build();

    map.put("buildNo", "211");
    map.put("url", "http://ec2-18-208-86-222.compute-1.amazonaws.com:8085/rest/api/latest/result/TOD-TOD-211");
    BuildDetails bd2 =
        BuildDetails.Builder.aBuildDetails()
            .withNumber("211")
            .withRevision("revision")
            .withBuildUrl("http://ec2-18-208-86-222.compute-1.amazonaws.com:8085/rest/api/latest/result/TOD-TOD-211")
            .withUiDisplayName("Build# 211")
            .withBuildParameters(map)
            .build();

    when(bambooBuildService.getBuilds(anyString(), any(), any(), any())).thenReturn(asList(bd2, bd1));
    when(artifactStreamServiceBindingService.getService(any(), any(), anyBoolean()))
        .thenReturn(Service.builder().build());
    List<BuildDetails> buildDetails = buildSourceService.getBuilds(APP_ID, ARTIFACT_STREAM_ID, SETTING_ID);
    assertThat(buildDetails).isNotEmpty();
    assertThat(buildDetails.size()).isEqualTo(2);
    assertThat(buildDetails).extracting(BuildDetails::getNumber).containsSequence("211", "210");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testValidateNexusArtifactSource() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(NexusConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(nexusBuildService);
    ArtifactStreamAttributes nexusArtifactStream = ArtifactStreamAttributes.builder().extension("jar").build();
    when(nexusService.existsVersion(any(), anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(false);
    assertThat(buildSourceService.validateArtifactSource(APP_ID, SETTING_ID, nexusArtifactStream)).isFalse();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testValidateCustomArtifactSource() {
    List<CustomRepositoryMapping.AttributeMapping> attributeMapping = new ArrayList<>();
    attributeMapping.add(builder().relativePath("assets[0].downloadUrl").mappedAttribute("url").build());
    attributeMapping.add(builder().relativePath("repository").mappedAttribute("repository").build());
    attributeMapping.add(builder().relativePath("assets[0].path").mappedAttribute("path").build());
    CustomRepositoryMapping mapping = CustomRepositoryMapping.builder()
                                          .artifactRoot("$.items")
                                          .buildNoPath("version")
                                          .artifactAttributes(attributeMapping)
                                          .build();
    ArtifactStream customArtifactStream = CustomArtifactStream.builder()
                                              .appId(APP_ID)
                                              .serviceId(SERVICE_ID)
                                              .uuid(ARTIFACT_STREAM_ID)
                                              .name("test")
                                              .scripts(asList(CustomArtifactStream.Script.builder()
                                                                  .scriptString("echo \"hello world\"")
                                                                  .customRepositoryMapping(mapping)
                                                                  .build()))
                                              .tags(asList())
                                              .accountId(ACCOUNT_ID)
                                              .build();
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(customBuildSourceService);
    when(customBuildSourceService.validateArtifactSource(any())).thenReturn(true);
    assertThat(buildSourceService.validateArtifactSource(customArtifactStream)).isTrue();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetProjects() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(AzureArtifactsPATConfig.builder().build())
                                            .build();

    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(azureArtifactsBuildService);

    AzureDevopsProject project = new AzureDevopsProject();
    project.setId("id1");
    project.setName("name1");
    when(azureArtifactsBuildService.getProjects(any(), any())).thenReturn(Collections.singletonList(project));
    List<AzureDevopsProject> projects = buildSourceService.getProjects(SETTING_ID);
    assertThat(projects).isNotEmpty();
    assertThat(projects).hasSize(1).extracting(AzureDevopsProject::getId).containsExactly("id1");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotGetProjectsForNonAzureArtifacts() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(BambooConfig.builder().build())
                                            .build();

    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(bambooBuildService);

    List<AzureDevopsProject> projects = buildSourceService.getProjects(SETTING_ID);
    assertThat(projects).isEmpty();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetFeeds() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(AzureArtifactsPATConfig.builder().build())
                                            .build();

    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(azureArtifactsBuildService);

    AzureArtifactsFeed feed = new AzureArtifactsFeed();
    feed.setId("id1");
    feed.setName("name1");
    when(azureArtifactsBuildService.getFeeds(any(), any(), any())).thenReturn(Collections.singletonList(feed));
    List<AzureArtifactsFeed> feeds = buildSourceService.getFeeds(SETTING_ID, null);
    assertThat(feeds).isNotEmpty();
    assertThat(feeds).hasSize(1).extracting(AzureArtifactsFeed::getId).containsExactly("id1");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotGetFeedsForNonAzureArtifacts() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(BambooConfig.builder().build())
                                            .build();

    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(bambooBuildService);

    List<AzureArtifactsFeed> feeds = buildSourceService.getFeeds(SETTING_ID, null);
    assertThat(feeds).isEmpty();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetPackages() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(AzureArtifactsPATConfig.builder().build())
                                            .build();

    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(azureArtifactsBuildService);

    AzureArtifactsPackage aPackage = new AzureArtifactsPackage();
    aPackage.setId("id1");
    aPackage.setName("name1");
    when(azureArtifactsBuildService.getPackages(any(), any(), any(), any(), any()))
        .thenReturn(Collections.singletonList(aPackage));
    List<AzureArtifactsPackage> packages =
        buildSourceService.getPackages(SETTING_ID, null, "FEED", AzureArtifactsArtifactStreamProtocolType.maven.name());
    assertThat(packages).isNotEmpty();
    assertThat(packages).hasSize(1).extracting(AzureArtifactsPackage::getId).containsExactly("id1");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotGetPackagesForNonAzureArtifacts() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(BambooConfig.builder().build())
                                            .build();

    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(bambooBuildService);

    List<AzureArtifactsPackage> packages =
        buildSourceService.getPackages(SETTING_ID, null, "FEED", AzureArtifactsArtifactStreamProtocolType.maven.name());
    assertThat(packages).isEmpty();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotManuallyPullArtifactsForParameterizedArtifactStream() {
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .settingId(SETTING_ID)
                                                  .jobname("releases")
                                                  .groupId("io.harness.test")
                                                  .artifactPaths(asList("${path}"))
                                                  .autoPopulate(false)
                                                  .serviceId(SERVICE_ID)
                                                  .name("testNexus")
                                                  .uuid(ARTIFACT_STREAM_ID)
                                                  .build();
    nexusArtifactStream.setArtifactStreamParameterized(true);
    when(artifactStreamService.get(anyString())).thenReturn(nexusArtifactStream);
    buildSourceService.getBuilds(APP_ID, ARTIFACT_STREAM_ID, SETTING_ID);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void getBuildForNexus2xMavenArtifactStream() {
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .settingId(SETTING_ID)
                                                  .jobname("${repo}")
                                                  .groupId("${group}")
                                                  .artifactPaths(asList("${path}"))
                                                  .autoPopulate(false)
                                                  .serviceId(SERVICE_ID)
                                                  .name("testNexus")
                                                  .uuid(ARTIFACT_STREAM_ID)
                                                  .build();
    nexusArtifactStream.setArtifactStreamParameterized(true);
    when(artifactStreamService.get(anyString())).thenReturn(nexusArtifactStream);
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(NexusConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    Map<String, Object> runtimeValues = new HashMap<>();
    runtimeValues.put("repo", "releases");
    runtimeValues.put("group", "mygroup");
    runtimeValues.put("path", "todolist");
    runtimeValues.put("buildNo", "1.0");
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(nexusBuildService);
    when(evaluator.evaluate(eq("repo"), eq(runtimeValues))).thenReturn("releases");
    when(evaluator.evaluate(eq("group"), eq(runtimeValues))).thenReturn("mygroup");
    when(evaluator.evaluate(eq("path"), eq(runtimeValues))).thenReturn("todolist");
    when(artifactStreamServiceBindingService.getService(anyString(), anyString(), anyBoolean()))
        .thenReturn(Service.builder().build());
    when(nexusBuildService.getBuild(anyString(), any(), any(), any(), anyString()))
        .thenReturn(BuildDetails.Builder.aBuildDetails().withNumber("1.0").build());
    BuildDetails buildDetails = buildSourceService.getBuild(APP_ID, ARTIFACT_STREAM_ID, SETTING_ID, runtimeValues);
    assertThat(buildDetails).isNotNull();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void getBuildForNexus2xNPMArtifactStream() {
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .appId(APP_ID)
                                                  .settingId(SETTING_ID)
                                                  .jobname("${repo}")
                                                  .packageName("${packageName}")
                                                  .autoPopulate(false)
                                                  .serviceId(SERVICE_ID)
                                                  .name("testNexus")
                                                  .uuid(ARTIFACT_STREAM_ID)
                                                  .repositoryFormat(RepositoryFormat.npm.name())
                                                  .build();
    nexusArtifactStream.setArtifactStreamParameterized(true);
    when(artifactStreamService.get(anyString())).thenReturn(nexusArtifactStream);
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(NexusConfig.builder().build())
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    Map<String, Object> runtimeValues = new HashMap<>();
    runtimeValues.put("repo", "releases");
    runtimeValues.put("packageName", "abbrev");
    runtimeValues.put("buildNo", "1.0.0");
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(nexusBuildService);
    when(evaluator.evaluate(eq("repo"), eq(runtimeValues))).thenReturn("releases");
    when(evaluator.evaluate(eq("packageName"), eq(runtimeValues))).thenReturn("abbrev");
    when(artifactStreamServiceBindingService.getService(anyString(), anyString(), anyBoolean()))
        .thenReturn(Service.builder().build());
    when(nexusBuildService.getBuild(anyString(), any(), any(), any(), anyString()))
        .thenReturn(BuildDetails.Builder.aBuildDetails().withNumber("1.0.0").build());
    BuildDetails buildDetails = buildSourceService.getBuild(APP_ID, ARTIFACT_STREAM_ID, SETTING_ID, runtimeValues);
    assertThat(buildDetails).isNotNull();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testCollectArtifactForParameterizedArtifactStreamShouldFail() {
    NexusArtifactStream nexusArtifactStream =
        NexusArtifactStream.builder().uuid(ARTIFACT_STREAM_ID).accountId(ACCOUNT_ID).build();
    nexusArtifactStream.setArtifactStreamParameterized(true);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(nexusArtifactStream);
    buildSourceService.collectArtifact(
        APP_ID, ARTIFACT_STREAM_ID, BuildDetails.Builder.aBuildDetails().withNumber("1.0").build());
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnListOfTriggerNames() throws InterruptedException {
    GcbDelegateResponse delegateResponse =
        new GcbDelegateResponse(ExecutionStatus.NEW, null, GcbTaskParams.builder().build(), null, false);
    delegateResponse.setTriggers(Collections.singletonList(TRIGGER_NAME));
    GcbTrigger gcbTrigger = new GcbTrigger();
    gcbTrigger.setId(TRIGGER_ID);
    gcbTrigger.setName(TRIGGER_NAME);
    List<GcbTrigger> triggers = Collections.singletonList(gcbTrigger);
    GcpConfig gcpConfig = GcpConfig.builder().accountId(ACCOUNT_ID).build();
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute().withAccountId(ACCOUNT_ID).withValue(gcpConfig).build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(gcbService.getAllTriggers(any(), any())).thenReturn(triggers);
    when(delegateService.executeTaskV2(any(DelegateTask.class))).thenReturn(delegateResponse);

    List<String> actualTriggerNames = buildSourceService.getGcbTriggers(SETTING_ID);
    assertThat(actualTriggerNames).hasSize(1);
    assertThat(actualTriggerNames.get(0)).isEqualTo(TRIGGER_NAME);
  }

  @Test(expected = IllegalArgumentException.class)
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void shouldReturnExceptionWhenDelegateResponseIsNull() throws InterruptedException {
    GcbTrigger gcbTrigger = new GcbTrigger();
    gcbTrigger.setId(TRIGGER_ID);
    gcbTrigger.setName(TRIGGER_NAME);
    List<GcbTrigger> triggers = Collections.singletonList(gcbTrigger);
    GcpConfig gcpConfig = GcpConfig.builder().accountId(ACCOUNT_ID).build();
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute().withAccountId(ACCOUNT_ID).withValue(gcpConfig).build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(gcbService.getAllTriggers(any(), any())).thenReturn(triggers);
    when(delegateService.executeTaskV2(any(DelegateTask.class))).thenReturn(null);

    buildSourceService.getGcbTriggers(SETTING_ID);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void shouldReturnExceptionWhenDelegateResponseGivesError() throws InterruptedException {
    GcbDelegateResponse delegateResponse =
        new GcbDelegateResponse(ExecutionStatus.FAILED, null, GcbTaskParams.builder().build(), "erorMessage", false);
    delegateResponse.setTriggers(Collections.singletonList(TRIGGER_NAME));
    GcbTrigger gcbTrigger = new GcbTrigger();
    gcbTrigger.setId(TRIGGER_ID);
    gcbTrigger.setName(TRIGGER_NAME);
    List<GcbTrigger> triggers = Collections.singletonList(gcbTrigger);
    GcpConfig gcpConfig = GcpConfig.builder().accountId(ACCOUNT_ID).build();
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute().withAccountId(ACCOUNT_ID).withValue(gcpConfig).build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(gcbService.getAllTriggers(any(), any())).thenReturn(triggers);
    when(delegateService.executeTaskV2(any(DelegateTask.class))).thenReturn(delegateResponse);

    buildSourceService.getGcbTriggers(SETTING_ID);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionOnNullSettingAttribute() {
    when(settingsService.get(SETTING_ID)).thenReturn(null);
    buildSourceService.getGcbTriggers(SETTING_ID);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionOnNullSettingValue() {
    when(settingsService.get(SETTING_ID)).thenReturn(new SettingAttribute());
    buildSourceService.getGcbTriggers(SETTING_ID);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldAppendDelegateSelectorToSyncTaskContextWhenGcpConfigIsUseDelegate() {
    when(settingsService.isSettingValueGcp(any())).thenCallRealMethod();
    when(settingsService.getDelegateSelectors(any())).thenCallRealMethod();
    when(settingsService.hasDelegateSelectorProperty(any())).thenCallRealMethod();
    ArgumentCaptor<SyncTaskContext> syncTaskContextArgumentCaptor = ArgumentCaptor.forClass(SyncTaskContext.class);
    buildSourceService.getBuildService(
        SettingAttribute.Builder.aSettingAttribute()
            .withValue(GcpConfig.builder()
                           .useDelegateSelectors(true)
                           .delegateSelectors(Collections.singletonList(DELEGATE_SELECTOR))
                           .build())
            .build());
    verify(delegateProxyFactory).getV2(any(), syncTaskContextArgumentCaptor.capture());
    assertThat(syncTaskContextArgumentCaptor.getValue().getTags()).contains(DELEGATE_SELECTOR);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldNotAppendDelegateSelectorToSyncTaskContextWhenGcpConfigIsNotUseDelegate() {
    when(settingsService.isSettingValueGcp(any())).thenCallRealMethod();
    when(settingsService.getDelegateSelectors(any())).thenCallRealMethod();
    ArgumentCaptor<SyncTaskContext> syncTaskContextArgumentCaptor = ArgumentCaptor.forClass(SyncTaskContext.class);
    buildSourceService.getBuildService(
        SettingAttribute.Builder.aSettingAttribute()
            .withValue(GcpConfig.builder()
                           .useDelegateSelectors(false)
                           .delegateSelectors(Collections.singletonList(DELEGATE_SELECTOR))
                           .build())
            .build());
    verify(delegateProxyFactory).getV2(any(), syncTaskContextArgumentCaptor.capture());
    assertThat(syncTaskContextArgumentCaptor.getValue().getTags()).isNull();
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testDelegateSelectorForDockerConfig() {
    when(settingsService.isSettingValueGcp(any())).thenCallRealMethod();
    when(settingsService.hasDelegateSelectorProperty(any())).thenCallRealMethod();
    when(settingsService.getDelegateSelectors(any())).thenCallRealMethod();
    ArgumentCaptor<SyncTaskContext> syncTaskContextArgumentCaptor = ArgumentCaptor.forClass(SyncTaskContext.class);
    buildSourceService.getBuildService(
        SettingAttribute.Builder.aSettingAttribute()
            .withValue(DockerConfig.builder()
                           .dockerRegistryUrl("https://registry.hub.docker.com/v2/")
                           .delegateSelectors(Collections.singletonList(DELEGATE_SELECTOR))
                           .build())
            .build());
    verify(delegateProxyFactory).getV2(any(), syncTaskContextArgumentCaptor.capture());
    assertThat(syncTaskContextArgumentCaptor.getValue().getTags()).isNotEmpty();
    assertThat(syncTaskContextArgumentCaptor.getValue().getTags())
        .isEqualTo(Collections.singletonList(DELEGATE_SELECTOR));
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testDelegateSelectorForNexusConfig() {
    when(settingsService.isSettingValueGcp(any())).thenCallRealMethod();
    when(settingsService.hasDelegateSelectorProperty(any())).thenCallRealMethod();
    when(settingsService.getDelegateSelectors(any())).thenCallRealMethod();
    ArgumentCaptor<SyncTaskContext> syncTaskContextArgumentCaptor = ArgumentCaptor.forClass(SyncTaskContext.class);
    buildSourceService.getBuildService(
        SettingAttribute.Builder.aSettingAttribute()
            .withValue(NexusConfig.builder()
                           .nexusUrl("https://harness.nexus.com/")
                           .delegateSelectors(Collections.singletonList(DELEGATE_SELECTOR))
                           .build())
            .build());
    verify(delegateProxyFactory).getV2(any(), syncTaskContextArgumentCaptor.capture());
    assertThat(syncTaskContextArgumentCaptor.getValue().getTags()).isNotEmpty();
    assertThat(syncTaskContextArgumentCaptor.getValue().getTags())
        .isEqualTo(Collections.singletonList(DELEGATE_SELECTOR));
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testDelegateSelectorForArtifactoryConfig() {
    when(settingsService.isSettingValueGcp(any())).thenCallRealMethod();
    when(settingsService.hasDelegateSelectorProperty(any())).thenCallRealMethod();
    when(settingsService.getDelegateSelectors(any())).thenCallRealMethod();
    ArgumentCaptor<SyncTaskContext> syncTaskContextArgumentCaptor = ArgumentCaptor.forClass(SyncTaskContext.class);
    buildSourceService.getBuildService(
        SettingAttribute.Builder.aSettingAttribute()
            .withValue(ArtifactoryConfig.builder()
                           .artifactoryUrl("https://harness.jfrog.com/")
                           .delegateSelectors(Collections.singletonList(DELEGATE_SELECTOR))
                           .build())
            .build());
    verify(delegateProxyFactory).getV2(any(), syncTaskContextArgumentCaptor.capture());
    assertThat(syncTaskContextArgumentCaptor.getValue().getTags()).isNotEmpty();
    assertThat(syncTaskContextArgumentCaptor.getValue().getTags())
        .isEqualTo(Collections.singletonList(DELEGATE_SELECTOR));
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldNotAppendDelegateSelectorToSyncTaskContextWhenSettingAttributeValuesIsNotGcp() {
    when(settingsService.isSettingValueGcp(any())).thenReturn(false);
    ArgumentCaptor<SyncTaskContext> syncTaskContextArgumentCaptor = ArgumentCaptor.forClass(SyncTaskContext.class);
    buildSourceService.getBuildService(
        SettingAttribute.Builder.aSettingAttribute().withValue(AwsConfig.builder().build()).build());
    verify(delegateProxyFactory).getV2(any(), syncTaskContextArgumentCaptor.capture());
    assertThat(syncTaskContextArgumentCaptor.getValue().getTags()).isNull();
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testGetJobsWithAppIdForBambooWithCertValidation() {
    BambooConfig bambooConfig = BambooConfig.builder().build();
    bambooConfig.setCertValidationRequired(true);

    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute().withAccountId(ACCOUNT_ID).withValue(bambooConfig).build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(bambooBuildService);
    when(bambooBuildService.getJobs(any(), any(), any()))
        .thenReturn(asList(new JobDetails("USERDEFINEDPROJECTKEY-RIS", false), new JobDetails("SAM-BUIL", false),
            new JobDetails("SAM-SAM", false), new JobDetails("TOD-TODIR", false),
            new JobDetails("USERDEFINEDPROJECTKEY-TES", false), new JobDetails("TOD-TOD", false)));
    Set<JobDetails> jobDetails = buildSourceService.getJobs(APP_ID, SETTING_ID, null);
    assertThat(jobDetails).isNotEmpty();
    assertThat(jobDetails.size()).isEqualTo(6);
    assertThat(jobDetails)
        .extracting(JobDetails::getJobName)
        .containsSequence(
            "SAM-BUIL", "SAM-SAM", "TOD-TOD", "TOD-TODIR", "USERDEFINEDPROJECTKEY-RIS", "USERDEFINEDPROJECTKEY-TES");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testValidateNexusArtifactSourceWithCertValidation() {
    NexusConfig nexusConfig = NexusConfig.builder().build();
    nexusConfig.setCertValidationRequired(true);
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute().withAccountId(ACCOUNT_ID).withValue(nexusConfig).build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(nexusBuildService);
    ArtifactStreamAttributes nexusArtifactStream = ArtifactStreamAttributes.builder().extension("jar").build();
    when(nexusService.existsVersion(any(), anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(false);
    assertThat(buildSourceService.validateArtifactSource(APP_ID, SETTING_ID, nexusArtifactStream)).isFalse();
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldListArtifactsByArtifactStreamAndFilterPath() {
    ArtifactStream artifactStream =
        GcsArtifactStream.builder().accountId(ACCOUNT_ID).artifactPaths(List.of("artifactory/*")).build();
    artifactStream.setCollectionEnabled(true);

    Artifact gcsArt = Artifact.Builder.anArtifact().build();
    List<Artifact> artifactList = setupList(5, List.of("a", "b", "c"), "artifactory", ".zip");
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(artifactStream);
    when(artifactService.listArtifactsByArtifactStreamId(ACCOUNT_ID, ARTIFACT_STREAM_ID)).thenReturn(List.of(gcsArt));
    List<Artifact> artifacts =
        buildSourceService.listArtifactByArtifactStreamAndFilterPath(artifactList, artifactStream);
    assertThat(artifacts).hasSize(15);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldListArtifactsByArtifactStreamAndFilterPathWithSpecificPath() {
    ArtifactStream artifactStream =
        GcsArtifactStream.builder().accountId(ACCOUNT_ID).artifactPaths(List.of("artifactory/a0.zip")).build();
    artifactStream.setCollectionEnabled(true);

    Artifact gcsArt = Artifact.Builder.anArtifact().build();
    List<Artifact> artifactList = setupList(5, List.of("a", "b", "c"), "artifactory", ".zip");
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(artifactStream);
    when(artifactService.listArtifactsByArtifactStreamId(ACCOUNT_ID, ARTIFACT_STREAM_ID)).thenReturn(List.of(gcsArt));
    List<Artifact> artifacts =
        buildSourceService.listArtifactByArtifactStreamAndFilterPath(artifactList, artifactStream);
    assertThat(artifacts).hasSize(1);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldListArtifactsByArtifactStreamAndFilterPathWithSpecificTypeFile() {
    ArtifactStream artifactStream =
        GcsArtifactStream.builder().accountId(ACCOUNT_ID).artifactPaths(List.of("artifactory/*.zip")).build();
    artifactStream.setCollectionEnabled(true);

    Artifact gcsArt = Artifact.Builder.anArtifact().build();
    List<Artifact> artifactList = setupList(5, List.of("a", "b", "c"), "artifactory", ".zip");
    artifactList.addAll(setupList(5, List.of("d"), "artifactory", ".tar"));

    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(artifactStream);
    when(artifactService.listArtifactsByArtifactStreamId(ACCOUNT_ID, ARTIFACT_STREAM_ID)).thenReturn(List.of(gcsArt));
    List<Artifact> artifacts =
        buildSourceService.listArtifactByArtifactStreamAndFilterPath(artifactList, artifactStream);
    assertThat(artifacts).hasSize(15);
    assertThat(artifactList.size()).isEqualTo(20);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldListArtifactsByArtifactStreamAndFilterPathWithoutSpecific() {
    ArtifactStream artifactStream =
        GcsArtifactStream.builder().accountId(ACCOUNT_ID).artifactPaths(List.of("artifactory/noart.zip")).build();
    artifactStream.setCollectionEnabled(true);

    Artifact gcsArt = Artifact.Builder.anArtifact().build();
    List<Artifact> artifactList = setupList(5, List.of("a", "b", "c"), "artifactory", ".zip");
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(artifactStream);
    when(artifactService.listArtifactsByArtifactStreamId(ACCOUNT_ID, ARTIFACT_STREAM_ID)).thenReturn(List.of(gcsArt));
    List<Artifact> artifacts =
        buildSourceService.listArtifactByArtifactStreamAndFilterPath(artifactList, artifactStream);
    assertThat(artifacts).hasSize(0);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldListArtifactsByArtifactStreamAndFilterPathStartsWith() {
    ArtifactStream artifactStream =
        GcsArtifactStream.builder().accountId(ACCOUNT_ID).artifactPaths(List.of("artifactory/*/*.zip")).build();
    artifactStream.setCollectionEnabled(true);

    Artifact gcsArt = Artifact.Builder.anArtifact().build();
    List<Artifact> artifactList = setupList(5, List.of("a", "b", "c"), "artifactory", ".zip");
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(artifactStream);
    when(artifactService.listArtifactsByArtifactStreamId(ACCOUNT_ID, ARTIFACT_STREAM_ID)).thenReturn(List.of(gcsArt));
    List<Artifact> artifacts =
        buildSourceService.listArtifactByArtifactStreamAndFilterPath(artifactList, artifactStream);
    assertThat(artifacts).hasSize(15);
  }

  private List<Artifact> setupList(int n, List<String> names, String folder, String type) {
    List<Artifact> artifactList = new ArrayList<>();
    for (String name : names) {
      for (int i = 0; i < n; ++i) {
        String artFileName = name + i + type;
        String artPathName = folder + "/" + artFileName;
        ArtifactMetadata artifactMetadata = new ArtifactMetadata();
        artifactMetadata.put("bucketName", "artifacts");
        artifactMetadata.put("artifactFileName", artFileName);
        artifactMetadata.put("artifactPath", artPathName);
        artifactMetadata.put("buildNo", artPathName);
        artifactMetadata.put("artifactFileSize", null);
        artifactMetadata.put("key", artPathName);
        artifactMetadata.put("url", "https://storage.cloud.google.com/artifacts/" + artPathName);

        Artifact artifact = Artifact.Builder.anArtifact()
                                .withAppId(APP_ID)
                                .withAccountId(ACCOUNT_ID)
                                .withArtifactStreamId(ARTIFACT_STREAM_ID)
                                .withMetadata(artifactMetadata)
                                .withArtifactStreamType(ArtifactStreamType.GCS.name())
                                .build();

        artifactList.add(artifact);
      }
    }
    return artifactList;
  }
}
