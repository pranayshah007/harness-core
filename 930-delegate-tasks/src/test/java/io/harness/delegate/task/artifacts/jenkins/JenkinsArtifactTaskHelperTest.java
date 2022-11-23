/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.jenkins;

import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthType;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthenticationDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class JenkinsArtifactTaskHelperTest extends CategoryTest {
  @Mock private JenkinsArtifactTaskHandler jenkinsArtifactTaskHandler;

  @InjectMocks private JenkinsArtifactTaskHelper jenkinsArtifactTaskHelper;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseGetLastSuccessfulBuild() {
    doNothing().when(jenkinsArtifactTaskHandler).decryptRequestDTOs(any());
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
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(jenkinsArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(jenkinsArtifactTaskHandler.getLastSuccessfulBuild(jenkinsArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        jenkinsArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(jenkinsArtifactTaskHandler).decryptRequestDTOs(any());
    verify(jenkinsArtifactTaskHandler).getLastSuccessfulBuild(jenkinsArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseGetBuilds() {
    doNothing().when(jenkinsArtifactTaskHandler).decryptRequestDTOs(any());
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
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(jenkinsArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_BUILDS)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(jenkinsArtifactTaskHandler.getBuilds(jenkinsArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        jenkinsArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(jenkinsArtifactTaskHandler).decryptRequestDTOs(any());
    verify(jenkinsArtifactTaskHandler).getBuilds(jenkinsArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseValidateServer() {
    doNothing().when(jenkinsArtifactTaskHandler).decryptRequestDTOs(any());
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
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(jenkinsArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.VALIDATE_ARTIFACT_SERVER)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(jenkinsArtifactTaskHandler.validateArtifactServer(jenkinsArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        jenkinsArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(jenkinsArtifactTaskHandler).decryptRequestDTOs(any());
    verify(jenkinsArtifactTaskHandler).validateArtifactServer(jenkinsArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseGetJob() {
    doNothing().when(jenkinsArtifactTaskHandler).decryptRequestDTOs(any());
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
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(jenkinsArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_JOBS)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(jenkinsArtifactTaskHandler.getJob(jenkinsArtifactDelegateRequest)).thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        jenkinsArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(jenkinsArtifactTaskHandler).decryptRequestDTOs(any());
    verify(jenkinsArtifactTaskHandler).getJob(jenkinsArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseGetJobParam() {
    doNothing().when(jenkinsArtifactTaskHandler).decryptRequestDTOs(any());
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
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(jenkinsArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_JOB_PARAMETERS)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(jenkinsArtifactTaskHandler.getJobWithParamters(jenkinsArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        jenkinsArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(jenkinsArtifactTaskHandler).decryptRequestDTOs(any());
    verify(jenkinsArtifactTaskHandler).getJobWithParamters(jenkinsArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseGetJenkinsBuild() {
    doNothing().when(jenkinsArtifactTaskHandler).decryptRequestDTOs(any());
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
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(jenkinsArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.JENKINS_BUILD)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(jenkinsArtifactTaskHandler.triggerBuild(jenkinsArtifactDelegateRequest, null))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        jenkinsArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(jenkinsArtifactTaskHandler).decryptRequestDTOs(any());
    verify(jenkinsArtifactTaskHandler).triggerBuild(jenkinsArtifactDelegateRequest, null);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseGetJenkinsPollTask() {
    doNothing().when(jenkinsArtifactTaskHandler).decryptRequestDTOs(any());
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
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(jenkinsArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.JENKINS_POLL_TASK)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(jenkinsArtifactTaskHandler.pollTask(jenkinsArtifactDelegateRequest, null))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        jenkinsArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(jenkinsArtifactTaskHandler).decryptRequestDTOs(any());
    verify(jenkinsArtifactTaskHandler).pollTask(jenkinsArtifactDelegateRequest, null);
  }
}
