/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.artifactory;

import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.VED;
import static io.harness.rule.OwnerRule.YUVRAJ;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryNgServiceImpl;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.logging.DummyLogCallbackImpl;
import io.harness.rule.Owner;

import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.BuildDetails.BuildStatus;
import software.wings.utils.RepositoryFormat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class ArtifactoryArtifactTaskHelperTest extends CategoryTest {
  @Mock private ArtifactoryArtifactTaskHandler artifactoryArtifactTaskHandler;

  @InjectMocks private ArtifactoryArtifactTaskHelper artifactoryArtifactTaskHelper;

  @Mock ArtifactoryRequestMapper artifactoryRequestMapper;

  @Mock ArtifactoryNgServiceImpl artifactoryNgService;

  private static final String ARTIFACT_FILTER = "artifactFilter";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseGetLastSuccessfulBuild() {
    doNothing()
        .when(artifactoryArtifactTaskHandler)
        .decryptRequestDTOs(ArtifactoryArtifactDelegateRequest.builder().build());
    ArtifactoryArtifactDelegateRequest artifactoryArtifactDelegateRequest =
        ArtifactoryArtifactDelegateRequest.builder()
            .artifactoryConnectorDTO(
                ArtifactoryConnectorDTO.builder().auth(ArtifactoryAuthenticationDTO.builder().build()).build())
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(artifactoryArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(artifactoryArtifactTaskHandler.getLastSuccessfulBuild(artifactoryArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        artifactoryArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(artifactoryArtifactTaskHandler).decryptRequestDTOs(artifactoryArtifactDelegateRequest);
    verify(artifactoryArtifactTaskHandler).getLastSuccessfulBuild(artifactoryArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseGetBuilds() {
    doNothing()
        .when(artifactoryArtifactTaskHandler)
        .decryptRequestDTOs(ArtifactoryArtifactDelegateRequest.builder().build());
    ArtifactoryArtifactDelegateRequest artifactoryArtifactDelegateRequest =
        ArtifactoryArtifactDelegateRequest.builder()
            .artifactoryConnectorDTO(
                ArtifactoryConnectorDTO.builder().auth(ArtifactoryAuthenticationDTO.builder().build()).build())
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(artifactoryArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_BUILDS)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(artifactoryArtifactTaskHandler.getBuilds(artifactoryArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        artifactoryArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(artifactoryArtifactTaskHandler).decryptRequestDTOs(artifactoryArtifactDelegateRequest);
    verify(artifactoryArtifactTaskHandler).getBuilds(artifactoryArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseValidateArtifactServers() {
    doNothing()
        .when(artifactoryArtifactTaskHandler)
        .decryptRequestDTOs(ArtifactoryArtifactDelegateRequest.builder().build());
    ArtifactoryArtifactDelegateRequest artifactoryArtifactDelegateRequest =
        ArtifactoryArtifactDelegateRequest.builder()
            .artifactoryConnectorDTO(
                ArtifactoryConnectorDTO.builder().auth(ArtifactoryAuthenticationDTO.builder().build()).build())
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(artifactoryArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.VALIDATE_ARTIFACT_SERVER)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(artifactoryArtifactTaskHandler.validateArtifactServer(artifactoryArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        artifactoryArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(artifactoryArtifactTaskHandler).decryptRequestDTOs(artifactoryArtifactDelegateRequest);
    verify(artifactoryArtifactTaskHandler).validateArtifactServer(artifactoryArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGenericGetArtifactCollectResponseGetLastSuccessfulBuild() {
    doNothing()
        .when(artifactoryArtifactTaskHandler)
        .decryptRequestDTOs(ArtifactoryArtifactDelegateRequest.builder().build());

    ArtifactoryGenericArtifactDelegateRequest artifactoryArtifactDelegateRequest =
        ArtifactoryGenericArtifactDelegateRequest.builder()
            .artifactoryConnectorDTO(
                ArtifactoryConnectorDTO.builder().auth(ArtifactoryAuthenticationDTO.builder().build()).build())
            .build();

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(artifactoryArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_BUILDS)
                                                        .build();

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();

    when(artifactoryArtifactTaskHandler.getBuilds(artifactoryArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        artifactoryArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    assertThat(artifactTaskResponse).isNotNull();

    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(artifactoryArtifactTaskHandler).getBuilds(artifactoryArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseValidateArtifactServers1() {
    doNothing()
        .when(artifactoryArtifactTaskHandler)
        .decryptRequestDTOs(ArtifactoryArtifactDelegateRequest.builder().build());
    ArtifactoryArtifactDelegateRequest artifactoryArtifactDelegateRequest =
        ArtifactoryArtifactDelegateRequest.builder()
            .artifactoryConnectorDTO(
                ArtifactoryConnectorDTO.builder().auth(ArtifactoryAuthenticationDTO.builder().build()).build())
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(artifactoryArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.JENKINS_BUILD)
                                                        .build();

    ArtifactTaskResponse artifactTaskResponse =
        artifactoryArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getCommandExecutionStatus()).isEqualTo(FAILURE);

    verify(artifactoryArtifactTaskHandler).decryptRequestDTOs(artifactoryArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testArtifactFailure() {
    doNothing()
        .when(artifactoryArtifactTaskHandler)
        .decryptRequestDTOs(ArtifactoryArtifactDelegateRequest.builder().build());
    ArtifactoryGenericArtifactDelegateRequest artifactoryArtifactDelegateRequest =
        ArtifactoryGenericArtifactDelegateRequest.builder()
            .artifactoryConnectorDTO(
                ArtifactoryConnectorDTO.builder().auth(ArtifactoryAuthenticationDTO.builder().build()).build())
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(artifactoryArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.JENKINS_BUILD)
                                                        .build();

    ArtifactTaskResponse artifactTaskResponse =
        artifactoryArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getCommandExecutionStatus()).isEqualTo(FAILURE);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testSaveLogs() {
    DummyLogCallbackImpl logCallback = new DummyLogCallbackImpl();

    logCallback.saveExecutionLog("hello");

    artifactoryArtifactTaskHelper.saveLogs(logCallback, "world");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseGetLastSuccessfulBuild_2() {
    doNothing()
        .when(artifactoryArtifactTaskHandler)
        .decryptRequestDTOs(ArtifactoryArtifactDelegateRequest.builder().build());

    ArtifactoryArtifactDelegateRequest artifactoryArtifactDelegateRequest =
        ArtifactoryArtifactDelegateRequest.builder()
            .artifactoryConnectorDTO(
                ArtifactoryConnectorDTO.builder().auth(ArtifactoryAuthenticationDTO.builder().build()).build())
            .build();

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(artifactoryArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                                                        .build();

    List<ArtifactDelegateResponse> artifactDelegateResponses = new ArrayList<>();

    ArtifactoryArtifactDelegateResponse artifactDelegateResponse1 =
        ArtifactoryArtifactDelegateResponse.builder()
            .tag("tag")
            .label(null)
            .artifactPath("path")
            .repositoryFormat(RepositoryFormat.docker.name())
            .repositoryName("repo")
            .build();

    artifactDelegateResponses.add(artifactDelegateResponse1);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder().artifactDelegateResponses(artifactDelegateResponses).build();

    when(artifactoryArtifactTaskHandler.getLastSuccessfulBuild(artifactoryArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        artifactoryArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    assertThat(artifactTaskResponse).isNotNull();

    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(artifactoryArtifactTaskHandler).decryptRequestDTOs(artifactoryArtifactDelegateRequest);

    verify(artifactoryArtifactTaskHandler).getLastSuccessfulBuild(artifactoryArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseGetLastSuccessfulBuild_3() {
    doNothing()
        .when(artifactoryArtifactTaskHandler)
        .decryptRequestDTOs(ArtifactoryArtifactDelegateRequest.builder().build());

    ArtifactoryArtifactDelegateRequest artifactoryArtifactDelegateRequest =
        ArtifactoryArtifactDelegateRequest.builder()
            .artifactoryConnectorDTO(
                ArtifactoryConnectorDTO.builder().auth(ArtifactoryAuthenticationDTO.builder().build()).build())
            .build();

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(artifactoryArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                                                        .build();

    List<ArtifactDelegateResponse> artifactDelegateResponses = new ArrayList<>();

    ArtifactBuildDetailsNG buildDetailsNG = ArtifactBuildDetailsNG.builder().buildUrl("url").build();

    ArtifactoryArtifactDelegateResponse artifactDelegateResponse1 =
        ArtifactoryArtifactDelegateResponse.builder()
            .buildDetails(buildDetailsNG)
            .tag("tag")
            .label(null)
            .artifactPath("path")
            .repositoryFormat(RepositoryFormat.docker.name())
            .repositoryName("repo")
            .build();

    artifactDelegateResponses.add(artifactDelegateResponse1);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder().artifactDelegateResponses(artifactDelegateResponses).build();

    when(artifactoryArtifactTaskHandler.getLastSuccessfulBuild(artifactoryArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        artifactoryArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    assertThat(artifactTaskResponse).isNotNull();

    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(artifactoryArtifactTaskHandler).decryptRequestDTOs(artifactoryArtifactDelegateRequest);

    verify(artifactoryArtifactTaskHandler).getLastSuccessfulBuild(artifactoryArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseGetLastSuccessfulBuild_4() {
    doNothing()
        .when(artifactoryArtifactTaskHandler)
        .decryptRequestDTOs(ArtifactoryArtifactDelegateRequest.builder().build());

    ArtifactoryArtifactDelegateRequest artifactoryArtifactDelegateRequest =
        ArtifactoryArtifactDelegateRequest.builder()
            .artifactoryConnectorDTO(
                ArtifactoryConnectorDTO.builder().auth(ArtifactoryAuthenticationDTO.builder().build()).build())
            .build();

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(artifactoryArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                                                        .build();

    List<ArtifactDelegateResponse> artifactDelegateResponses = new ArrayList<>();

    ArtifactBuildDetailsNG buildDetailsNG =
        ArtifactBuildDetailsNG.builder().buildUrl("url").metadata(new HashMap<>()).build();

    ArtifactoryArtifactDelegateResponse artifactDelegateResponse1 =
        ArtifactoryArtifactDelegateResponse.builder()
            .buildDetails(buildDetailsNG)
            .tag("tag")
            .label(null)
            .artifactPath("path")
            .repositoryFormat(RepositoryFormat.docker.name())
            .repositoryName("repo")
            .build();

    artifactDelegateResponses.add(artifactDelegateResponse1);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder().artifactDelegateResponses(artifactDelegateResponses).build();

    when(artifactoryArtifactTaskHandler.getLastSuccessfulBuild(artifactoryArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        artifactoryArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    assertThat(artifactTaskResponse).isNotNull();

    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(artifactoryArtifactTaskHandler).decryptRequestDTOs(artifactoryArtifactDelegateRequest);

    verify(artifactoryArtifactTaskHandler).getLastSuccessfulBuild(artifactoryArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGenericLastSuccessfulBuild() {
    doNothing()
        .when(artifactoryArtifactTaskHandler)
        .decryptRequestDTOs(ArtifactoryArtifactDelegateRequest.builder().build());

    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder()
                                                            .artifactoryUrl("url")
                                                            .artifactRepositoryUrl("repoUrl")
                                                            .username("username")
                                                            .password("password".toCharArray())
                                                            .build();

    ArtifactoryGenericArtifactDelegateRequest artifactoryArtifactDelegateRequest =
        ArtifactoryGenericArtifactDelegateRequest.builder()
            .artifactoryConnectorDTO(
                ArtifactoryConnectorDTO.builder().auth(ArtifactoryAuthenticationDTO.builder().build()).build())
            .artifactDirectory("directory")
            .artifactPath("path")
            .artifactPathFilter("filter")
            .connectorRef("connectorRef")
            .repositoryFormat("repoFormat")
            .repositoryName("repoName")
            .artifactFilter(ARTIFACT_FILTER)
            .build();

    doReturn(artifactoryConfigRequest).when(artifactoryRequestMapper).toArtifactoryRequest(any());

    BuildDetails buildDetails = new BuildDetails();
    buildDetails.setNumber("b1");
    buildDetails.setArtifactPath("path");
    buildDetails.setBuildUrl("url");
    buildDetails.setStatus(BuildStatus.SUCCESS);

    doReturn(buildDetails)
        .when(artifactoryNgService)
        .getLatestArtifact(artifactoryConfigRequest, artifactoryArtifactDelegateRequest.getRepositoryName(),
            artifactoryArtifactDelegateRequest.getArtifactDirectory(),
            artifactoryArtifactDelegateRequest.getArtifactPathFilter(),
            artifactoryArtifactDelegateRequest.getArtifactPath(), 10000,
            artifactoryArtifactDelegateRequest.getArtifactFilter());

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(artifactoryArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                                                        .build();

    List<ArtifactDelegateResponse> artifactDelegateResponses = new ArrayList<>();

    ArtifactBuildDetailsNG buildDetailsNG =
        ArtifactBuildDetailsNG.builder().buildUrl("url").metadata(new HashMap<>()).build();

    ArtifactoryGenericArtifactDelegateResponse artifactDelegateResponse1 =
        ArtifactoryGenericArtifactDelegateResponse.builder()
            .buildDetails(buildDetailsNG)
            .repositoryFormat(RepositoryFormat.generic.name())
            .artifactPath("path")
            .artifactDirectory("directory")
            .repositoryName("repo")
            .build();

    artifactDelegateResponses.add(artifactDelegateResponse1);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder().artifactDelegateResponses(artifactDelegateResponses).build();

    when(artifactoryArtifactTaskHandler.getSuccessTaskExecutionResponseGeneric(any()))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        artifactoryArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    assertThat(artifactTaskResponse).isNotNull();

    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(artifactoryArtifactTaskHandler).decryptRequestDTOs(any());

    verify(artifactoryArtifactTaskHandler).getSuccessTaskExecutionResponseGeneric(any());
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGenericLastSuccessfulBuild_2() {
    doNothing()
        .when(artifactoryArtifactTaskHandler)
        .decryptRequestDTOs(ArtifactoryArtifactDelegateRequest.builder().build());

    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder()
                                                            .artifactoryUrl("url")
                                                            .artifactRepositoryUrl("repoUrl")
                                                            .username("username")
                                                            .password("password".toCharArray())
                                                            .build();

    ArtifactoryGenericArtifactDelegateRequest artifactoryArtifactDelegateRequest =
        ArtifactoryGenericArtifactDelegateRequest.builder()
            .artifactoryConnectorDTO(
                ArtifactoryConnectorDTO.builder().auth(ArtifactoryAuthenticationDTO.builder().build()).build())
            .artifactDirectory("directory")
            .artifactPath("path")
            .artifactPathFilter("filter")
            .connectorRef("connectorRef")
            .repositoryFormat("repoFormat")
            .artifactFilter(ARTIFACT_FILTER)
            .repositoryName("repoName")
            .build();

    doReturn(artifactoryConfigRequest).when(artifactoryRequestMapper).toArtifactoryRequest(any());

    doReturn(null)
        .when(artifactoryNgService)
        .getLatestArtifact(artifactoryConfigRequest, artifactoryArtifactDelegateRequest.getRepositoryName(),
            artifactoryArtifactDelegateRequest.getArtifactDirectory(),
            artifactoryArtifactDelegateRequest.getArtifactPathFilter(),
            artifactoryArtifactDelegateRequest.getArtifactPath(), 10000,
            artifactoryArtifactDelegateRequest.getArtifactFilter());

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(artifactoryArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                                                        .build();

    List<ArtifactDelegateResponse> artifactDelegateResponses = new ArrayList<>();

    ArtifactoryGenericArtifactDelegateResponse artifactDelegateResponse1 =
        ArtifactoryGenericArtifactDelegateResponse.builder()
            .buildDetails(null)
            .repositoryFormat(RepositoryFormat.generic.name())
            .artifactPath("path")
            .artifactDirectory("directory")
            .repositoryName("repo")
            .build();

    artifactDelegateResponses.add(artifactDelegateResponse1);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder().artifactDelegateResponses(artifactDelegateResponses).build();

    when(artifactoryArtifactTaskHandler.getSuccessTaskExecutionResponseGeneric(any()))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        artifactoryArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    assertThat(artifactTaskResponse).isNotNull();

    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(artifactoryArtifactTaskHandler).decryptRequestDTOs(any());

    verify(artifactoryArtifactTaskHandler).getSuccessTaskExecutionResponseGeneric(any());
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGenericLastSuccessfulBuild_3() {
    doNothing()
        .when(artifactoryArtifactTaskHandler)
        .decryptRequestDTOs(ArtifactoryArtifactDelegateRequest.builder().build());

    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder()
                                                            .artifactoryUrl("url")
                                                            .artifactRepositoryUrl("repoUrl")
                                                            .username("username")
                                                            .password("password".toCharArray())
                                                            .build();

    ArtifactoryGenericArtifactDelegateRequest artifactoryArtifactDelegateRequest =
        ArtifactoryGenericArtifactDelegateRequest.builder()
            .artifactoryConnectorDTO(
                ArtifactoryConnectorDTO.builder().auth(ArtifactoryAuthenticationDTO.builder().build()).build())
            .artifactDirectory("directory")
            .artifactPath("path")
            .artifactPathFilter("filter")
            .connectorRef("connectorRef")
            .repositoryFormat("repoFormat")
            .artifactFilter(ARTIFACT_FILTER)
            .repositoryName("repoName")
            .build();

    doReturn(artifactoryConfigRequest).when(artifactoryRequestMapper).toArtifactoryRequest(any());

    doReturn(null)
        .when(artifactoryNgService)
        .getLatestArtifact(artifactoryConfigRequest, artifactoryArtifactDelegateRequest.getRepositoryName(),
            artifactoryArtifactDelegateRequest.getArtifactDirectory(),
            artifactoryArtifactDelegateRequest.getArtifactPathFilter(),
            artifactoryArtifactDelegateRequest.getArtifactPath(), 10000,
            artifactoryArtifactDelegateRequest.getArtifactFilter());

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(artifactoryArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                                                        .build();

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder().artifactDelegateResponses(new ArrayList<>()).build();

    when(artifactoryArtifactTaskHandler.getSuccessTaskExecutionResponseGeneric(any()))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        artifactoryArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    assertThat(artifactTaskResponse).isNotNull();

    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(artifactoryArtifactTaskHandler).decryptRequestDTOs(any());

    verify(artifactoryArtifactTaskHandler).getSuccessTaskExecutionResponseGeneric(any());
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGenericLastSuccessfulBuild_4() {
    doNothing()
        .when(artifactoryArtifactTaskHandler)
        .decryptRequestDTOs(ArtifactoryArtifactDelegateRequest.builder().build());

    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder()
                                                            .artifactoryUrl("url")
                                                            .artifactRepositoryUrl("repoUrl")
                                                            .username("username")
                                                            .password("password".toCharArray())
                                                            .build();

    ArtifactoryGenericArtifactDelegateRequest artifactoryArtifactDelegateRequest =
        ArtifactoryGenericArtifactDelegateRequest.builder()
            .artifactoryConnectorDTO(
                ArtifactoryConnectorDTO.builder().auth(ArtifactoryAuthenticationDTO.builder().build()).build())
            .artifactDirectory(null)
            .artifactPath("path")
            .artifactPathFilter("filter")
            .connectorRef("connectorRef")
            .repositoryFormat("repoFormat")
            .artifactFilter(ARTIFACT_FILTER)
            .repositoryName("repoName")
            .build();

    doReturn(artifactoryConfigRequest).when(artifactoryRequestMapper).toArtifactoryRequest(any());

    BuildDetails buildDetails = new BuildDetails();
    buildDetails.setNumber("b1");
    buildDetails.setArtifactPath("path");
    buildDetails.setBuildUrl("url");
    buildDetails.setStatus(BuildStatus.SUCCESS);

    doReturn(buildDetails)
        .when(artifactoryNgService)
        .getLatestArtifact(artifactoryConfigRequest, artifactoryArtifactDelegateRequest.getRepositoryName(),
            artifactoryArtifactDelegateRequest.getArtifactDirectory(),
            artifactoryArtifactDelegateRequest.getArtifactPathFilter(),
            artifactoryArtifactDelegateRequest.getArtifactPath(), 10000,
            artifactoryArtifactDelegateRequest.getArtifactFilter());

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(artifactoryArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                                                        .build();

    List<ArtifactDelegateResponse> artifactDelegateResponses = new ArrayList<>();

    ArtifactBuildDetailsNG buildDetailsNG =
        ArtifactBuildDetailsNG.builder().buildUrl("url").metadata(new HashMap<>()).build();

    ArtifactoryGenericArtifactDelegateResponse artifactDelegateResponse1 =
        ArtifactoryGenericArtifactDelegateResponse.builder()
            .buildDetails(buildDetailsNG)
            .repositoryFormat(RepositoryFormat.generic.name())
            .artifactPath("path")
            .artifactDirectory(null)
            .repositoryName("repo")
            .build();

    artifactDelegateResponses.add(artifactDelegateResponse1);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder().artifactDelegateResponses(artifactDelegateResponses).build();

    when(artifactoryArtifactTaskHandler.getSuccessTaskExecutionResponseGeneric(any()))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        artifactoryArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    assertThat(artifactTaskResponse).isNotNull();

    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(artifactoryArtifactTaskHandler).decryptRequestDTOs(any());

    verify(artifactoryArtifactTaskHandler).getSuccessTaskExecutionResponseGeneric(any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGenericLastSuccessfulBuild_5() {
    doNothing()
        .when(artifactoryArtifactTaskHandler)
        .decryptRequestDTOs(ArtifactoryArtifactDelegateRequest.builder().build());

    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder()
                                                            .artifactoryUrl("url")
                                                            .artifactRepositoryUrl("repoUrl")
                                                            .username("username")
                                                            .password("password".toCharArray())
                                                            .build();

    ArtifactoryGenericArtifactDelegateRequest artifactoryArtifactDelegateRequest =
        ArtifactoryGenericArtifactDelegateRequest.builder()
            .artifactoryConnectorDTO(
                ArtifactoryConnectorDTO.builder().auth(ArtifactoryAuthenticationDTO.builder().build()).build())
            .artifactDirectory("dir/dir1")
            .artifactPath("path")
            .artifactPathFilter("")
            .connectorRef("connectorRef")
            .artifactFilter(ARTIFACT_FILTER)
            .repositoryFormat("repoFormat")
            .repositoryName("repoName")
            .build();

    doReturn(artifactoryConfigRequest).when(artifactoryRequestMapper).toArtifactoryRequest(any());

    BuildDetails buildDetails = new BuildDetails();
    buildDetails.setNumber("b1");
    buildDetails.setArtifactPath("dir/dir1/path");
    buildDetails.setBuildUrl("url");
    buildDetails.setStatus(BuildStatus.SUCCESS);

    doReturn(buildDetails)
        .when(artifactoryNgService)
        .getLatestArtifact(artifactoryConfigRequest, artifactoryArtifactDelegateRequest.getRepositoryName(),
            artifactoryArtifactDelegateRequest.getArtifactDirectory(),
            artifactoryArtifactDelegateRequest.getArtifactPathFilter(),
            artifactoryArtifactDelegateRequest.getArtifactPath(), 10000,
            artifactoryArtifactDelegateRequest.getArtifactFilter());

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(artifactoryArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                                                        .build();
    doCallRealMethod().when(artifactoryArtifactTaskHandler).getSuccessTaskExecutionResponseGeneric(any());

    ArtifactTaskResponse artifactTaskResponse =
        artifactoryArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses().size())
        .isEqualTo(1);
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()
                   .getArtifactDelegateResponses()
                   .get(0)
                   .getBuildDetails()
                   .getMetadata()
                   .get(ArtifactMetadataKeys.FILE_NAME))
        .isEqualTo("path");
  }
}
