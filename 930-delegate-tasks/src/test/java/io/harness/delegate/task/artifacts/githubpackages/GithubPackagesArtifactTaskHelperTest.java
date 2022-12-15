/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.githubpackages;

import static io.harness.delegate.task.artifacts.ArtifactSourceType.GITHUB_PACKAGES;
import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.mappers.GithubPackagesRequestResponseMapper;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.eraro.ErrorCode;
import io.harness.exception.runtime.GithubPackagesServerRuntimeException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.DummyLogCallbackImpl;
import io.harness.rule.Owner;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDC)
public class GithubPackagesArtifactTaskHelperTest extends CategoryTest {
  @Mock private GithubPackagesArtifactTaskHandler githubPackagesArtifactTaskHandler;

  @InjectMocks private GithubPackagesArtifactTaskHelper githubPackagesArtifactTaskHelper;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetBuilds() {
    SecretRefData secretRefData =
        SecretRefData.builder().identifier("id").decryptedValue("token".toCharArray()).scope(Scope.ACCOUNT).build();

    GithubTokenSpecDTO githubTokenSpecDTO = GithubTokenSpecDTO.builder().tokenRef(secretRefData).build();

    GithubApiAccessDTO githubApiAccessDTO =
        GithubApiAccessDTO.builder().spec(githubTokenSpecDTO).type(GithubApiAccessType.TOKEN).build();

    GithubUsernameTokenDTO githubUsernameTokenDTO =
        GithubUsernameTokenDTO.builder().username("username").tokenRef(secretRefData).build();

    GithubHttpCredentialsDTO githubHttpCredentialsDTO = GithubHttpCredentialsDTO.builder()
                                                            .httpCredentialsSpec(githubUsernameTokenDTO)
                                                            .type(GithubHttpAuthenticationType.USERNAME_AND_TOKEN)
                                                            .build();

    GithubAuthenticationDTO githubAuthenticationDTO =
        GithubAuthenticationDTO.builder().authType(GitAuthType.HTTP).credentials(githubHttpCredentialsDTO).build();

    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                .apiAccess(githubApiAccessDTO)
                                                .authentication(githubAuthenticationDTO)
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .executeOnDelegate(true)
                                                .url("url")
                                                .build();

    GithubPackagesArtifactDelegateRequest githubPackagesArtifactDelegateRequest =
        GithubPackagesArtifactDelegateRequest.builder()
            .org(null)
            .packageType("container")
            .packageName("packageName")
            .sourceType(GITHUB_PACKAGES)
            .connectorRef("ref")
            .githubConnectorDTO(githubConnectorDTO)
            .version(null)
            .versionRegex("*")
            .encryptedDataDetails(new ArrayList<>())
            .build();

    List<BuildDetails> builds = new ArrayList<>();

    BuildDetails build1 = new BuildDetails();
    build1.setNumber("b1");
    build1.setUiDisplayName("Version# b1");

    BuildDetails build2 = new BuildDetails();
    build2.setNumber("b2");
    build2.setUiDisplayName("Version# b2");

    BuildDetails build3 = new BuildDetails();
    build3.setNumber("b3");
    build3.setUiDisplayName("Version# b3");

    BuildDetails build4 = new BuildDetails();
    build4.setNumber("b4");
    build4.setUiDisplayName("Version# b4");

    BuildDetails build5 = new BuildDetails();
    build5.setNumber("b5");
    build5.setUiDisplayName("Version# b5");

    builds.add(build1);
    builds.add(build2);
    builds.add(build3);
    builds.add(build4);
    builds.add(build5);

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(githubPackagesArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_BUILDS)
                                                        .build();

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder()
                                                                      .buildDetails(builds)
                                                                      .artifactDelegateResponses(new ArrayList<>())
                                                                      .isArtifactServerValid(true)
                                                                      .isArtifactSourceValid(true)
                                                                      .build();

    doNothing().when(githubPackagesArtifactTaskHandler).decryptRequestDTOs(githubPackagesArtifactDelegateRequest);

    when(githubPackagesArtifactTaskHandler.getBuilds(githubPackagesArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        githubPackagesArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(githubPackagesArtifactTaskHandler).decryptRequestDTOs(githubPackagesArtifactDelegateRequest);
    verify(githubPackagesArtifactTaskHandler).getBuilds(githubPackagesArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuild() {
    SecretRefData secretRefData =
        SecretRefData.builder().identifier("id").decryptedValue("token".toCharArray()).scope(Scope.ACCOUNT).build();

    GithubTokenSpecDTO githubTokenSpecDTO = GithubTokenSpecDTO.builder().tokenRef(secretRefData).build();

    GithubApiAccessDTO githubApiAccessDTO =
        GithubApiAccessDTO.builder().spec(githubTokenSpecDTO).type(GithubApiAccessType.TOKEN).build();

    GithubUsernameTokenDTO githubUsernameTokenDTO =
        GithubUsernameTokenDTO.builder().username("username").tokenRef(secretRefData).build();

    GithubHttpCredentialsDTO githubHttpCredentialsDTO = GithubHttpCredentialsDTO.builder()
                                                            .httpCredentialsSpec(githubUsernameTokenDTO)
                                                            .type(GithubHttpAuthenticationType.USERNAME_AND_TOKEN)
                                                            .build();

    GithubAuthenticationDTO githubAuthenticationDTO =
        GithubAuthenticationDTO.builder().authType(GitAuthType.HTTP).credentials(githubHttpCredentialsDTO).build();

    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                .apiAccess(githubApiAccessDTO)
                                                .authentication(githubAuthenticationDTO)
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .executeOnDelegate(true)
                                                .url("url")
                                                .build();

    GithubPackagesArtifactDelegateRequest githubPackagesArtifactDelegateRequest =
        GithubPackagesArtifactDelegateRequest.builder()
            .org(null)
            .packageType("container")
            .packageName("packageName")
            .sourceType(GITHUB_PACKAGES)
            .connectorRef("ref")
            .githubConnectorDTO(githubConnectorDTO)
            .version(null)
            .versionRegex("*")
            .encryptedDataDetails(new ArrayList<>())
            .build();

    List<BuildDetails> builds = new ArrayList<>();

    BuildDetails lastSuccessfulBuild = new BuildDetails();
    lastSuccessfulBuild.setNumber("b1");
    lastSuccessfulBuild.setUiDisplayName("Version# b1");

    builds.add(lastSuccessfulBuild);

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(githubPackagesArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                                                        .build();

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder()
                                                                      .buildDetails(builds)
                                                                      .artifactDelegateResponses(new ArrayList<>())
                                                                      .isArtifactServerValid(true)
                                                                      .isArtifactSourceValid(true)
                                                                      .build();

    doNothing().when(githubPackagesArtifactTaskHandler).decryptRequestDTOs(githubPackagesArtifactDelegateRequest);

    when(githubPackagesArtifactTaskHandler.getLastSuccessfulBuild(githubPackagesArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        githubPackagesArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(githubPackagesArtifactTaskHandler).decryptRequestDTOs(githubPackagesArtifactDelegateRequest);
    verify(githubPackagesArtifactTaskHandler).getLastSuccessfulBuild(githubPackagesArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetPackages() {
    SecretRefData secretRefData =
        SecretRefData.builder().identifier("id").decryptedValue("token".toCharArray()).scope(Scope.ACCOUNT).build();

    GithubTokenSpecDTO githubTokenSpecDTO = GithubTokenSpecDTO.builder().tokenRef(secretRefData).build();

    GithubApiAccessDTO githubApiAccessDTO =
        GithubApiAccessDTO.builder().spec(githubTokenSpecDTO).type(GithubApiAccessType.TOKEN).build();

    GithubUsernameTokenDTO githubUsernameTokenDTO =
        GithubUsernameTokenDTO.builder().username("username").tokenRef(secretRefData).build();

    GithubHttpCredentialsDTO githubHttpCredentialsDTO = GithubHttpCredentialsDTO.builder()
                                                            .httpCredentialsSpec(githubUsernameTokenDTO)
                                                            .type(GithubHttpAuthenticationType.USERNAME_AND_TOKEN)
                                                            .build();

    GithubAuthenticationDTO githubAuthenticationDTO =
        GithubAuthenticationDTO.builder().authType(GitAuthType.HTTP).credentials(githubHttpCredentialsDTO).build();

    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                .apiAccess(githubApiAccessDTO)
                                                .authentication(githubAuthenticationDTO)
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .executeOnDelegate(true)
                                                .url("url")
                                                .build();

    GithubPackagesArtifactDelegateRequest githubPackagesArtifactDelegateRequest =
        GithubPackagesArtifactDelegateRequest.builder()
            .org(null)
            .packageType("container")
            .sourceType(GITHUB_PACKAGES)
            .connectorRef("ref")
            .githubConnectorDTO(githubConnectorDTO)
            .encryptedDataDetails(new ArrayList<>())
            .build();

    List<Map<String, String>> packageDetails = new ArrayList<>();

    Map<String, String> package1 = new HashMap<>();

    package1.put("packageId", "id1");
    package1.put("packageName", "name1");
    package1.put("packageType", "container");
    package1.put("visibility", "visibility1");
    package1.put("packageUrl", "url1");

    Map<String, String> package2 = new HashMap<>();

    package2.put("packageId", "id2");
    package2.put("packageName", "name2");
    package2.put("packageType", "container");
    package2.put("visibility", "visibility2");
    package2.put("packageUrl", "url2");

    Map<String, String> package3 = new HashMap<>();

    package3.put("packageId", "id3");
    package3.put("packageName", "name3");
    package3.put("packageType", "container");
    package3.put("visibility", "visibility3");
    package3.put("packageUrl", "url3");

    Map<String, String> package4 = new HashMap<>();

    package4.put("packageId", "id4");
    package4.put("packageName", "name4");
    package4.put("packageType", "container");
    package4.put("visibility", "visibility4");
    package4.put("packageUrl", "url4");

    Map<String, String> package5 = new HashMap<>();

    package5.put("packageId", "id5");
    package5.put("packageName", "name5");
    package5.put("packageType", "container");
    package5.put("visibility", "visibility5");
    package5.put("packageUrl", "url5");

    packageDetails.add(package1);
    packageDetails.add(package2);
    packageDetails.add(package3);
    packageDetails.add(package4);
    packageDetails.add(package5);

    List<GithubPackagesArtifactDelegateResponse> githubPackagesArtifactDelegateResponses =
        GithubPackagesRequestResponseMapper.toGithubPackagesResponse(
            packageDetails, githubPackagesArtifactDelegateRequest);

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(githubPackagesArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_GITHUB_PACKAGES)
                                                        .build();

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder()
            .buildDetails(null)
            .artifactDelegateResponses(githubPackagesArtifactDelegateResponses)
            .isArtifactServerValid(true)
            .isArtifactSourceValid(true)
            .build();

    doNothing().when(githubPackagesArtifactTaskHandler).decryptRequestDTOs(githubPackagesArtifactDelegateRequest);

    when(githubPackagesArtifactTaskHandler.listPackages(githubPackagesArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        githubPackagesArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(githubPackagesArtifactTaskHandler).decryptRequestDTOs(githubPackagesArtifactDelegateRequest);
    verify(githubPackagesArtifactTaskHandler).listPackages(githubPackagesArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testSaveLogs() {
    DummyLogCallbackImpl logCallback = new DummyLogCallbackImpl();

    logCallback.saveExecutionLog("hello");

    githubPackagesArtifactTaskHelper.saveLogs(logCallback, "world");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testInvalidTaskType() {
    SecretRefData secretRefData =
        SecretRefData.builder().identifier("id").decryptedValue("token".toCharArray()).scope(Scope.ACCOUNT).build();

    GithubTokenSpecDTO githubTokenSpecDTO = GithubTokenSpecDTO.builder().tokenRef(secretRefData).build();

    GithubApiAccessDTO githubApiAccessDTO =
        GithubApiAccessDTO.builder().spec(githubTokenSpecDTO).type(GithubApiAccessType.TOKEN).build();

    GithubUsernameTokenDTO githubUsernameTokenDTO =
        GithubUsernameTokenDTO.builder().username("username").tokenRef(secretRefData).build();

    GithubHttpCredentialsDTO githubHttpCredentialsDTO = GithubHttpCredentialsDTO.builder()
                                                            .httpCredentialsSpec(githubUsernameTokenDTO)
                                                            .type(GithubHttpAuthenticationType.USERNAME_AND_TOKEN)
                                                            .build();

    GithubAuthenticationDTO githubAuthenticationDTO =
        GithubAuthenticationDTO.builder().authType(GitAuthType.HTTP).credentials(githubHttpCredentialsDTO).build();

    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                .apiAccess(githubApiAccessDTO)
                                                .authentication(githubAuthenticationDTO)
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .executeOnDelegate(true)
                                                .url("url")
                                                .build();

    GithubPackagesArtifactDelegateRequest githubPackagesArtifactDelegateRequest =
        GithubPackagesArtifactDelegateRequest.builder()
            .org(null)
            .packageType("container")
            .packageName("packageName")
            .sourceType(GITHUB_PACKAGES)
            .connectorRef("ref")
            .githubConnectorDTO(githubConnectorDTO)
            .version(null)
            .versionRegex("*")
            .encryptedDataDetails(new ArrayList<>())
            .build();

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(githubPackagesArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_JOBS)
                                                        .build();

    ArtifactTaskResponse expectedArtifactTaskResponse =
        ArtifactTaskResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .errorMessage("There is no Github Package artifact task type impl defined for - "
                + artifactTaskParameters.getArtifactTaskType().name())
            .errorCode(ErrorCode.INVALID_ARGUMENT)
            .build();

    doNothing().when(githubPackagesArtifactTaskHandler).decryptRequestDTOs(githubPackagesArtifactDelegateRequest);

    ArtifactTaskResponse artifactTaskResponse =
        githubPackagesArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getCommandExecutionStatus())
        .isEqualTo(expectedArtifactTaskResponse.getCommandExecutionStatus());
    assertThat(artifactTaskResponse.getErrorMessage()).isEqualTo(expectedArtifactTaskResponse.getErrorMessage());
    assertThat(artifactTaskResponse.getErrorCode()).isEqualTo(expectedArtifactTaskResponse.getErrorCode());

    verify(githubPackagesArtifactTaskHandler).decryptRequestDTOs(githubPackagesArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testThrowException() {
    SecretRefData secretRefData =
        SecretRefData.builder().identifier("id").decryptedValue("token".toCharArray()).scope(Scope.ACCOUNT).build();

    GithubTokenSpecDTO githubTokenSpecDTO = GithubTokenSpecDTO.builder().tokenRef(secretRefData).build();

    GithubApiAccessDTO githubApiAccessDTO =
        GithubApiAccessDTO.builder().spec(githubTokenSpecDTO).type(GithubApiAccessType.TOKEN).build();

    GithubUsernameTokenDTO githubUsernameTokenDTO =
        GithubUsernameTokenDTO.builder().username("username").tokenRef(secretRefData).build();

    GithubHttpCredentialsDTO githubHttpCredentialsDTO = GithubHttpCredentialsDTO.builder()
                                                            .httpCredentialsSpec(githubUsernameTokenDTO)
                                                            .type(GithubHttpAuthenticationType.USERNAME_AND_TOKEN)
                                                            .build();

    GithubAuthenticationDTO githubAuthenticationDTO =
        GithubAuthenticationDTO.builder().authType(GitAuthType.HTTP).credentials(githubHttpCredentialsDTO).build();

    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                .apiAccess(githubApiAccessDTO)
                                                .authentication(githubAuthenticationDTO)
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .executeOnDelegate(true)
                                                .url("url")
                                                .build();

    GithubPackagesArtifactDelegateRequest githubPackagesArtifactDelegateRequest =
        GithubPackagesArtifactDelegateRequest.builder()
            .org(null)
            .packageType("container")
            .packageName("packageName")
            .sourceType(GITHUB_PACKAGES)
            .connectorRef("ref")
            .githubConnectorDTO(githubConnectorDTO)
            .version(null)
            .versionRegex("*")
            .encryptedDataDetails(new ArrayList<>())
            .build();

    List<BuildDetails> builds = new ArrayList<>();

    BuildDetails build1 = new BuildDetails();
    build1.setNumber("b1");
    build1.setUiDisplayName("Version# b1");

    BuildDetails build2 = new BuildDetails();
    build2.setNumber("b2");
    build2.setUiDisplayName("Version# b2");

    BuildDetails build3 = new BuildDetails();
    build3.setNumber("b3");
    build3.setUiDisplayName("Version# b3");

    BuildDetails build4 = new BuildDetails();
    build4.setNumber("b4");
    build4.setUiDisplayName("Version# b4");

    BuildDetails build5 = new BuildDetails();
    build5.setNumber("b5");
    build5.setUiDisplayName("Version# b5");

    builds.add(build1);
    builds.add(build2);
    builds.add(build3);
    builds.add(build4);
    builds.add(build5);

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(githubPackagesArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_BUILDS)
                                                        .build();

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder()
                                                                      .buildDetails(builds)
                                                                      .artifactDelegateResponses(new ArrayList<>())
                                                                      .isArtifactServerValid(true)
                                                                      .isArtifactSourceValid(true)
                                                                      .build();

    doNothing().when(githubPackagesArtifactTaskHandler).decryptRequestDTOs(githubPackagesArtifactDelegateRequest);

    when(githubPackagesArtifactTaskHandler.getBuilds(githubPackagesArtifactDelegateRequest))
        .thenThrow(new GithubPackagesServerRuntimeException("error"));

    assertThatThrownBy(() -> githubPackagesArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters))
        .isInstanceOf(GithubPackagesServerRuntimeException.class);
  }
}
