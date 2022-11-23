/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.jenkins;

import static io.harness.rule.OwnerRule.SHIVAM;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.jenkins.beans.JenkinsInternalConfig;
import io.harness.artifacts.jenkins.service.JenkinsRegistryService;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthType;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthenticationDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.task.artifacts.mappers.JenkinsRequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.exception.HintException;
import io.harness.rule.Owner;

import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;

import com.google.inject.name.Named;
import com.offbytwo.jenkins.model.JobWithDetails;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDC)
public class JenkinsArtifactTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock @Named("jenkinsExecutor") private ExecutorService jenkinsExecutor;
  @InjectMocks JenkinsArtifactTaskHandler jenkinsArtifactTaskHandler;
  @Mock JenkinsRegistryService jenkinsRegistryService;

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
}
