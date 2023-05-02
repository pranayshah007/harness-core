/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.jenkins;

import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.vivekveman;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.jenkins.beans.JenkinsInternalConfig;
import io.harness.artifacts.jenkins.service.JenkinsRegistryService;
import io.harness.artifacts.jenkins.service.JenkinsRegistryUtils;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthType;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthenticationDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.artifacts.mappers.JenkinsRequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.jenkins.JenkinsBuildTaskNGResponse;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.HintException;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import software.wings.beans.JenkinsConfig;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.Jenkins;
import software.wings.helpers.ext.jenkins.JenkinsFactory;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.helpers.ext.jenkins.model.CustomBuildWithDetails;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.name.Named;
import com.offbytwo.jenkins.client.JenkinsHttpClient;
import com.offbytwo.jenkins.client.JenkinsHttpConnection;
import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.BuildResult;
import com.offbytwo.jenkins.model.BuildWithDetails;
import com.offbytwo.jenkins.model.JobWithDetails;
import com.offbytwo.jenkins.model.QueueReference;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDC)
public class JenkinsArtifactTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock @Named("jenkinsExecutor") private ExecutorService jenkinsExecutor;
  @Spy @InjectMocks JenkinsArtifactTaskHandler jenkinsArtifactTaskHandler;
  @Mock JenkinsRegistryService jenkinsRegistryService;
  @Mock CustomBuildWithDetails customBuildWithDetails;
  @Mock JenkinsRegistryUtils jenkinsRegistryUtils;
  @Mock private JenkinsFactory jenkinsFactory;
  @Mock private Jenkins jenkins;
  @Mock private JenkinsHttpConnection jenkinsHttpConnection;
  @Mock Build jenkinsBuild;
  @Mock private BuildWithDetails buildWithDetails;
  @Mock private QueueReference queueReference;
  @Mock private EncryptionService encryptionService;
  @Mock private DelegateLogService logService;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock LogCallback logCallback;
  @Mock JenkinsHttpClient client;
  @Mock JenkinsBuildTaskNGResponse jenkinsBuildTaskNGResponse;
  private String jenkinsUrl = "http://jenkins";
  private String buildUrl = "http://jenkins/job/TestJob/job/111";
  private String queueItemUrlPart = "http://jenkins/queue/item/111";
  private String userName = "user1";
  private char[] password = "pass1".toCharArray();
  private String jobName = "job1";
  private String activityId = "activityId";
  private String stateName = "jenkins_state";
  private String appId = "testAppId";
  private JenkinsConfig jenkinsConfig =
      JenkinsConfig.builder().jenkinsUrl(jenkinsUrl).username(userName).password(password).build();
  private Map<String, String> parameters = new HashMap<>();
  private Map<String, String> assertions = new HashMap<>();

  @Before
  public void setUp() throws Exception {
    when(jenkinsFactory.create(anyString(), anyString(), any(char[].class))).thenReturn(jenkins);
    when(jenkins.getBuild(any(QueueReference.class), any(JenkinsConfig.class))).thenReturn(jenkinsBuild);
    when(jenkinsBuild.getUrl()).thenReturn(buildUrl);
    when(jenkinsBuild.details()).thenReturn(buildWithDetails);
    when(jenkinsBuild.getNumber()).thenReturn(20);
    when(buildWithDetails.getClient()).thenReturn(jenkinsHttpConnection);
    when(customBuildWithDetails.details()).thenReturn(customBuildWithDetails);
    when(jenkinsHttpConnection.get(any(), any())).thenReturn(customBuildWithDetails);
    when(buildWithDetails.isBuilding()).thenReturn(false);
    when(buildWithDetails.getConsoleOutputText()).thenReturn("console output");
    when(buildWithDetails.getNumber()).thenReturn(20);
    when(buildWithDetails.getDescription()).thenReturn("Jenkins Build");
    when(buildWithDetails.getDisplayName()).thenReturn("Jenkins# 20");
    when(buildWithDetails.getFullDisplayName()).thenReturn("Jenkins# 20");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testValidateServer() throws UnsupportedEncodingException {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder().authType(JenkinsAuthType.USER_PASSWORD).build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .build();
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails()
                                    .withNumber("tag")
                                    .withBuildUrl("https://Jenkins.com")
                                    .withMetadata(Collections.singletonMap("metadat", "label"))
                                    .withUiDisplayName("tag")
                                    .build();
    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    JobDetails jobDetails = Mockito.mock(JobDetails.class, RETURNS_DEEP_STUBS);
    doReturn(true).when(jenkinsRegistryService).validateCredentials(jenkinsInternalConfig);

    ArtifactTaskExecutionResponse validateArtifactServer =
        jenkinsArtifactTaskHandler.validateArtifactServer(jenkinsArtifactDelegateRequest);
    assertThat(validateArtifactServer).isNotNull();
    assertThat(validateArtifactServer.isArtifactServerValid()).isTrue();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetJob() throws UnsupportedEncodingException {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder().authType(JenkinsAuthType.USER_PASSWORD).build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .build();
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails()
                                    .withNumber("tag")
                                    .withBuildUrl("https://Jenkins.com")
                                    .withMetadata(Collections.singletonMap("metadat", "label"))
                                    .withUiDisplayName("tag")
                                    .build();
    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    JobDetails jobDetails = Mockito.mock(JobDetails.class, RETURNS_DEEP_STUBS);
    doReturn(Collections.singletonList(jobDetails)).when(jenkinsRegistryService).getJobs(jenkinsInternalConfig, null);

    ArtifactTaskExecutionResponse job = jenkinsArtifactTaskHandler.getJob(jenkinsArtifactDelegateRequest);
    assertThat(job).isNotNull();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testJobWithParamters() throws UnsupportedEncodingException {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder().authType(JenkinsAuthType.USER_PASSWORD).build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .build();
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails()
                                    .withNumber("tag")
                                    .withBuildUrl("https://Jenkins.com")
                                    .withMetadata(Collections.singletonMap("metadat", "label"))
                                    .withUiDisplayName("tag")
                                    .build();
    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    JobDetails jobDetails = Mockito.mock(JobDetails.class, RETURNS_DEEP_STUBS);
    doReturn(jobDetails)
        .when(jenkinsRegistryService)
        .getJobWithParamters(jenkinsInternalConfig, jenkinsArtifactDelegateRequest.getJobName());

    ArtifactTaskExecutionResponse jobWithParamters =
        jenkinsArtifactTaskHandler.getJobWithParamters(jenkinsArtifactDelegateRequest);
    assertThat(jobWithParamters).isNotNull();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetArtifactPaths() throws UnsupportedEncodingException {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder().authType(JenkinsAuthType.USER_PASSWORD).build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .build();
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails()
                                    .withNumber("tag")
                                    .withBuildUrl("https://Jenkins.com")
                                    .withMetadata(Collections.singletonMap("metadat", "label"))
                                    .withUiDisplayName("tag")
                                    .build();
    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    JobWithDetails jobWithDetails = new JobWithDetails();
    doReturn(jobWithDetails)
        .when(jenkinsRegistryService)
        .getJobWithDetails(jenkinsInternalConfig, jenkinsArtifactDelegateRequest.getJobName());

    ArtifactTaskExecutionResponse artifactPaths =
        jenkinsArtifactTaskHandler.getArtifactPaths(jenkinsArtifactDelegateRequest);
    assertThat(artifactPaths).isNotNull();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetArtifactPathsForWingsException() {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder().authType(JenkinsAuthType.USER_PASSWORD).build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .build();
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails()
                                    .withNumber("tag")
                                    .withBuildUrl("https://Jenkins.com")
                                    .withMetadata(Collections.singletonMap("metadat", "label"))
                                    .withUiDisplayName("tag")
                                    .build();
    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    JobWithDetails jobWithDetails = new JobWithDetails();
    when(jenkinsRegistryService.getJobWithDetails(jenkinsInternalConfig, jenkinsArtifactDelegateRequest.getJobName()))
        .thenThrow(RuntimeException.class);
    try {
      ArtifactTaskExecutionResponse artifactPaths =
          jenkinsArtifactTaskHandler.getArtifactPaths(jenkinsArtifactDelegateRequest);
    } catch (ArtifactServerException ex) {
      assertThat(ex.getMessage()).contains("Error in artifact paths from jenkins server. Reason:");
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetBuild() throws UnsupportedEncodingException {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder().authType(JenkinsAuthType.USER_PASSWORD).build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .build();
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails()
                                    .withNumber("tag")
                                    .withBuildUrl("https://Jenkins.com")
                                    .withMetadata(Collections.singletonMap("metadat", "label"))
                                    .withUiDisplayName("tag")
                                    .build();
    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    doReturn(Collections.singletonList(buildDetails))
        .when(jenkinsRegistryService)
        .getBuildsForJob(jenkinsInternalConfig, jenkinsArtifactDelegateRequest.getJobName(),
            jenkinsArtifactDelegateRequest.getArtifactPaths(), 25);

    ArtifactTaskExecutionResponse getBuilds = jenkinsArtifactTaskHandler.getBuilds(jenkinsArtifactDelegateRequest);
    assertThat(getBuilds).isNotNull();
    assertThat(getBuilds.getArtifactDelegateResponses().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildForVerify() throws UnsupportedEncodingException {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder().authType(JenkinsAuthType.USER_PASSWORD).build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .buildNumber("tag")
            .build();
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails().withNumber("tag").build();
    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    jobName = URLEncoder.encode(jobName, StandardCharsets.UTF_8.toString());
    doReturn(Collections.singletonList(buildDetails))
        .when(jenkinsRegistryService)
        .getBuildsForJob(jenkinsInternalConfig, jobName, jenkinsArtifactDelegateRequest.getArtifactPaths(), 25);

    ArtifactTaskExecutionResponse lastSuccessfulBuild =
        jenkinsArtifactTaskHandler.getLastSuccessfulBuild(jenkinsArtifactDelegateRequest);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildInfo() throws UnsupportedEncodingException {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder().authType(JenkinsAuthType.USER_PASSWORD).build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .buildNumber("")
            .build();
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails()
                                    .withNumber("tag")
                                    .withBuildUrl("https://Jenkins.com")
                                    .withMetadata(Collections.singletonMap("metadat", "label"))
                                    .withUiDisplayName("tag")
                                    .build();
    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    jobName = URLEncoder.encode(jobName, StandardCharsets.UTF_8.toString());
    doReturn(buildDetails)
        .when(jenkinsRegistryService)
        .getLastSuccessfulBuildForJob(
            jenkinsInternalConfig, jobName, jenkinsArtifactDelegateRequest.getArtifactPaths());

    ArtifactTaskExecutionResponse lastSuccessfulBuild =
        jenkinsArtifactTaskHandler.getLastSuccessfulBuild(jenkinsArtifactDelegateRequest);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().size()).isEqualTo(1);
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildForVerifyingOlderBuild() throws UnsupportedEncodingException {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder().authType(JenkinsAuthType.USER_PASSWORD).build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .buildNumber("oldertag")
            .build();
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails().withNumber("tag").build();
    BuildDetails buildDetailsForOlderBuild = BuildDetails.Builder.aBuildDetails().withNumber("tag").build();
    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    jobName = URLEncoder.encode(jobName, StandardCharsets.UTF_8.toString());
    doReturn(Collections.singletonList(buildDetails))
        .when(jenkinsRegistryService)
        .getBuildsForJob(jenkinsInternalConfig, jobName, jenkinsArtifactDelegateRequest.getArtifactPaths(), 25);

    doReturn(buildDetailsForOlderBuild)
        .when(jenkinsRegistryService)
        .verifyBuildForJob(
            jenkinsInternalConfig, jobName, jenkinsArtifactDelegateRequest.getArtifactPaths(), "oldertag");

    ArtifactTaskExecutionResponse lastSuccessfulBuild =
        jenkinsArtifactTaskHandler.getLastSuccessfulBuild(jenkinsArtifactDelegateRequest);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildForNotFound() throws UnsupportedEncodingException {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder().authType(JenkinsAuthType.USER_PASSWORD).build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .buildNumber("tag")
            .build();
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails().withNumber("tag12").build();
    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    jobName = URLEncoder.encode(jobName, StandardCharsets.UTF_8.toString());
    doReturn(Collections.singletonList(buildDetails))
        .when(jenkinsRegistryService)
        .getBuildsForJob(jenkinsInternalConfig, jobName, jenkinsArtifactDelegateRequest.getArtifactPaths(), 25);
    try {
      ArtifactTaskExecutionResponse lastSuccessfulBuild =
          jenkinsArtifactTaskHandler.getLastSuccessfulBuild(jenkinsArtifactDelegateRequest);
    } catch (HintException ex) {
      assertThat(ex).isInstanceOf(HintException.class);
      assertEquals(
          ex.getMessage(), "Check if the version exist & check if the right connector chosen for fetching the build.");
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildForEmpty() throws UnsupportedEncodingException {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder().authType(JenkinsAuthType.USER_PASSWORD).build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .buildNumber("tag")
            .build();
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails().withNumber("tag12").build();
    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    jobName = URLEncoder.encode(jobName, StandardCharsets.UTF_8.toString());
    doReturn(Collections.EMPTY_LIST)
        .when(jenkinsRegistryService)
        .getBuildsForJob(jenkinsInternalConfig, jobName, jenkinsArtifactDelegateRequest.getArtifactPaths(), 25);
    try {
      ArtifactTaskExecutionResponse lastSuccessfulBuild =
          jenkinsArtifactTaskHandler.getLastSuccessfulBuild(jenkinsArtifactDelegateRequest);
    } catch (HintException ex) {
      assertThat(ex).isInstanceOf(HintException.class);
      assertEquals(
          ex.getMessage(), "Check if the version exist & check if the right connector chosen for fetching the build.");
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testTriggerBuild() {
    try {
      String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
      JenkinsConnectorDTO jenkinsConnectorDTO =
          JenkinsConnectorDTO.builder()
              .jenkinsUrl("https://Jenkins.com")
              .auth(JenkinsAuthenticationDTO.builder().authType(JenkinsAuthType.USER_PASSWORD).build())
              .build();
      JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
          JenkinsArtifactDelegateRequest.builder()
              .artifactPaths(Collections.singletonList("artifactPath"))
              .jobName(jobName)
              .jenkinsConnectorDTO(jenkinsConnectorDTO)
              .buildNumber("tag")
              .build();
      BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails().withNumber("tag12").build();

      JenkinsInternalConfig jenkinsInternalConfig =
          JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
      jobName = URLEncoder.encode(jobName, StandardCharsets.UTF_8.toString());
      when(jenkinsBuild.getNumber()).thenReturn(20);
      doReturn(new QueueReference(jenkinsUrl))
          .when(jenkinsRegistryUtils)
          .trigger(jobName, jenkinsInternalConfig, jenkinsArtifactDelegateRequest.getJobParameter());
      Build build = new Build();
      doReturn(build).when(jenkinsRegistryUtils).waitForJobToStartExecution(queueReference, jenkinsInternalConfig);
      CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
      ArtifactTaskExecutionResponse lastSuccessfulBuild =
          jenkinsArtifactTaskHandler.triggerBuild(jenkinsArtifactDelegateRequest, logCallback);
    } catch (Exception ex) {
      verify(jenkinsBuildTaskNGResponse).setErrorMessage(ExceptionUtils.getMessage(ex));
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testPollTaskForNull() throws IOException, URISyntaxException {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder().authType(JenkinsAuthType.USER_PASSWORD).build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .useConnectorUrlForJobExecution(true)
            .buildNumber("tag")
            .queuedBuildUrl(queueItemUrlPart)
            .build();
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails().withNumber("tag12").build();
    queueItemUrlPart = jenkinsArtifactDelegateRequest.getQueuedBuildUrl();

    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    jobName = URLEncoder.encode(jobName, StandardCharsets.UTF_8.toString());
    when(jenkinsBuild.getNumber()).thenReturn(20);
    Build jenkinsBuild = new Build(220, buildUrl);
    when(jenkinsRegistryUtils.getBuild(new QueueReference(queueItemUrlPart), jenkinsInternalConfig))
        .thenReturn(jenkinsBuild);

    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        jenkinsArtifactTaskHandler.pollTask(jenkinsArtifactDelegateRequest, logCallback);
    assertThat(artifactTaskExecutionResponse.getJenkinsBuildTaskNGResponse().getExecutionStatus())
        .isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testPollTask() throws IOException, URISyntaxException {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder().authType(JenkinsAuthType.USER_PASSWORD).build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .useConnectorUrlForJobExecution(true)
            .buildNumber("tag")
            .queuedBuildUrl(queueItemUrlPart)
            .captureEnvironmentVariable(true)
            .build();
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails().withNumber("tag12").build();
    queueItemUrlPart = jenkinsArtifactDelegateRequest.getQueuedBuildUrl();

    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    jobName = URLEncoder.encode(jobName, StandardCharsets.UTF_8.toString());
    when(buildWithDetails.getResult()).thenReturn(BuildResult.SUCCESS);
    doReturn(buildWithDetails).when(jenkinsArtifactTaskHandler).waitForJobExecutionToFinish(any(), any(), any(), any());
    when(jenkinsBuild.getNumber()).thenReturn(20);
    when(jenkinsRegistryUtils.getBuild(any(), any())).thenReturn(jenkinsBuild);
    when(jenkinsRegistryUtils.getEnvVars(any(), any())).thenReturn(Collections.singletonMap("envVar", "test"));

    when(jenkinsBuild.details()).thenReturn(buildWithDetails);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        jenkinsArtifactTaskHandler.pollTask(jenkinsArtifactDelegateRequest, logCallback);
    assertThat(artifactTaskExecutionResponse.getJenkinsBuildTaskNGResponse().getExecutionStatus())
        .isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testPollTaskUnstableAsSuccess() throws IOException, URISyntaxException {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder().authType(JenkinsAuthType.USER_PASSWORD).build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .useConnectorUrlForJobExecution(true)
            .buildNumber("tag")
            .queuedBuildUrl(queueItemUrlPart)
            .unstableStatusAsSuccess(true)
            .captureEnvironmentVariable(true)
            .build();
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails().withNumber("tag12").build();
    queueItemUrlPart = jenkinsArtifactDelegateRequest.getQueuedBuildUrl();

    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    jobName = URLEncoder.encode(jobName, StandardCharsets.UTF_8.toString());
    when(buildWithDetails.getResult()).thenReturn(BuildResult.UNSTABLE);

    doReturn(buildWithDetails).when(jenkinsArtifactTaskHandler).waitForJobExecutionToFinish(any(), any(), any(), any());
    when(jenkinsBuild.getNumber()).thenReturn(20);
    when(jenkinsRegistryUtils.getBuild(any(), any())).thenReturn(jenkinsBuild);

    when(jenkinsBuild.details()).thenReturn(buildWithDetails);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        jenkinsArtifactTaskHandler.pollTask(jenkinsArtifactDelegateRequest, logCallback);
    assertThat(artifactTaskExecutionResponse.getJenkinsBuildTaskNGResponse().getExecutionStatus())
        .isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testPollTaskUnstable() throws IOException, URISyntaxException {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder().authType(JenkinsAuthType.USER_PASSWORD).build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .useConnectorUrlForJobExecution(true)
            .buildNumber("tag")
            .queuedBuildUrl(queueItemUrlPart)
            .captureEnvironmentVariable(true)
            .build();
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails().withNumber("tag12").build();
    queueItemUrlPart = jenkinsArtifactDelegateRequest.getQueuedBuildUrl();

    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    jobName = URLEncoder.encode(jobName, StandardCharsets.UTF_8.toString());
    when(buildWithDetails.getResult()).thenReturn(BuildResult.UNSTABLE);

    doReturn(buildWithDetails).when(jenkinsArtifactTaskHandler).waitForJobExecutionToFinish(any(), any(), any(), any());
    when(jenkinsBuild.getNumber()).thenReturn(20);
    when(jenkinsRegistryUtils.getBuild(any(), any())).thenReturn(jenkinsBuild);

    when(jenkinsBuild.details()).thenReturn(buildWithDetails);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        jenkinsArtifactTaskHandler.pollTask(jenkinsArtifactDelegateRequest, logCallback);
    assertThat(artifactTaskExecutionResponse.getJenkinsBuildTaskNGResponse().getExecutionStatus())
        .isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testPollTaskFailed() throws IOException, URISyntaxException {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder().authType(JenkinsAuthType.USER_PASSWORD).build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .useConnectorUrlForJobExecution(true)
            .buildNumber("tag")
            .queuedBuildUrl(queueItemUrlPart)
            .captureEnvironmentVariable(true)
            .build();
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails().withNumber("tag12").build();
    queueItemUrlPart = jenkinsArtifactDelegateRequest.getQueuedBuildUrl();

    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    jobName = URLEncoder.encode(jobName, StandardCharsets.UTF_8.toString());
    when(buildWithDetails.getResult()).thenReturn(BuildResult.FAILURE);

    doReturn(buildWithDetails).when(jenkinsArtifactTaskHandler).waitForJobExecutionToFinish(any(), any(), any(), any());
    when(jenkinsBuild.getNumber()).thenReturn(20);
    when(jenkinsRegistryUtils.getBuild(any(), any())).thenReturn(jenkinsBuild);

    when(jenkinsBuild.details()).thenReturn(buildWithDetails);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        jenkinsArtifactTaskHandler.pollTask(jenkinsArtifactDelegateRequest, logCallback);
    assertThat(artifactTaskExecutionResponse.getJenkinsBuildTaskNGResponse().getExecutionStatus())
        .isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testPollTaskException() throws IOException, URISyntaxException {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder().authType(JenkinsAuthType.USER_PASSWORD).build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .useConnectorUrlForJobExecution(true)
            .buildNumber("tag")
            .queuedBuildUrl(queueItemUrlPart)
            .captureEnvironmentVariable(true)
            .build();
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails().withNumber("tag12").build();
    queueItemUrlPart = jenkinsArtifactDelegateRequest.getQueuedBuildUrl();

    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    jobName = URLEncoder.encode(jobName, StandardCharsets.UTF_8.toString());
    when(buildWithDetails.getResult()).thenReturn(BuildResult.SUCCESS);
    when(jenkinsBuild.getNumber()).thenReturn(20);
    when(jenkinsRegistryUtils.getBuild(any(), any())).thenReturn(jenkinsBuild);
    when(jenkinsBuild.details()).thenReturn(buildWithDetails);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        jenkinsArtifactTaskHandler.pollTask(jenkinsArtifactDelegateRequest, logCallback);
    assertThat(artifactTaskExecutionResponse.getJenkinsBuildTaskNGResponse().getExecutionStatus())
        .isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testPollTaskExceptionForEnvParam() throws IOException, URISyntaxException {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder().authType(JenkinsAuthType.USER_PASSWORD).build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .useConnectorUrlForJobExecution(true)
            .buildNumber("tag")
            .queuedBuildUrl(queueItemUrlPart)
            .captureEnvironmentVariable(true)
            .build();
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails().withNumber("tag12").build();
    queueItemUrlPart = jenkinsArtifactDelegateRequest.getQueuedBuildUrl();

    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    jobName = URLEncoder.encode(jobName, StandardCharsets.UTF_8.toString());
    when(buildWithDetails.getResult()).thenReturn(BuildResult.SUCCESS);
    when(jenkinsBuild.getNumber()).thenReturn(20);
    when(jenkinsRegistryUtils.getBuild(any(), any())).thenReturn(jenkinsBuild);
    when(jenkinsBuild.details()).thenReturn(buildWithDetails);
    when(buildWithDetails.getParameters()).thenThrow(RuntimeException.class);
    doReturn(buildWithDetails).when(jenkinsArtifactTaskHandler).waitForJobExecutionToFinish(any(), any(), any(), any());
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        jenkinsArtifactTaskHandler.pollTask(jenkinsArtifactDelegateRequest, logCallback);
    assertThat(artifactTaskExecutionResponse.getJenkinsBuildTaskNGResponse().getExecutionStatus())
        .isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testPollTaskWingsException() throws IOException, URISyntaxException {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder().authType(JenkinsAuthType.USER_PASSWORD).build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .useConnectorUrlForJobExecution(true)
            .buildNumber("tag")
            .queuedBuildUrl(queueItemUrlPart)
            .captureEnvironmentVariable(true)
            .build();
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails().withNumber("tag12").build();
    queueItemUrlPart = jenkinsArtifactDelegateRequest.getQueuedBuildUrl();

    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    jobName = URLEncoder.encode(jobName, StandardCharsets.UTF_8.toString());
    when(buildWithDetails.getResult()).thenThrow(RuntimeException.class);
    when(jenkinsBuild.getNumber()).thenReturn(20);
    when(jenkinsRegistryUtils.getBuild(any(), any())).thenReturn(jenkinsBuild);
    when(jenkinsBuild.details()).thenReturn(buildWithDetails);
    doReturn(buildWithDetails).when(jenkinsArtifactTaskHandler).waitForJobExecutionToFinish(any(), any(), any(), any());
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        jenkinsArtifactTaskHandler.pollTask(jenkinsArtifactDelegateRequest, logCallback);
    assertThat(artifactTaskExecutionResponse.getJenkinsBuildTaskNGResponse().getExecutionStatus())
        .isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testPollTaskForEnvVar() throws IOException, URISyntaxException {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder().authType(JenkinsAuthType.USER_PASSWORD).build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .useConnectorUrlForJobExecution(true)
            .buildNumber("tag")
            .queuedBuildUrl(queueItemUrlPart)
            .captureEnvironmentVariable(true)
            .build();
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails().withNumber("tag12").build();
    queueItemUrlPart = jenkinsArtifactDelegateRequest.getQueuedBuildUrl();

    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    jobName = URLEncoder.encode(jobName, StandardCharsets.UTF_8.toString());
    when(buildWithDetails.getResult()).thenReturn(BuildResult.SUCCESS);
    doReturn(buildWithDetails).when(jenkinsArtifactTaskHandler).waitForJobExecutionToFinish(any(), any(), any(), any());
    when(jenkinsBuild.getNumber()).thenReturn(20);
    when(jenkinsRegistryUtils.getBuild(any(), any())).thenReturn(jenkinsBuild);
    when(jenkinsRegistryUtils.getEnvVars(any(), any())).thenReturn(Collections.singletonMap("envVar", "test"));

    when(jenkinsBuild.details()).thenReturn(buildWithDetails);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        jenkinsArtifactTaskHandler.pollTask(jenkinsArtifactDelegateRequest, logCallback);
    assertThat(artifactTaskExecutionResponse.getJenkinsBuildTaskNGResponse().getExecutionStatus())
        .isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(artifactTaskExecutionResponse.getJenkinsBuildTaskNGResponse().getEnvVars())
        .isEqualTo(Collections.singletonMap("envVar", "test"));
  }
}
