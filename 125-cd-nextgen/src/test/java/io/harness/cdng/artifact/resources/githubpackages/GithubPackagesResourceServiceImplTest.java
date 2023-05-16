/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.githubpackages;

import static io.harness.delegate.task.artifacts.ArtifactSourceType.GITHUB_PACKAGES;
import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.resources.githubpackages.dtos.GithubPackagesResponseDTO;
import io.harness.cdng.artifact.resources.githubpackages.service.GithubPackagesResourceServiceImpl;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubOauthDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.githubpackages.GithubPackagesArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.githubpackages.GithubPackagesArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.mappers.GithubPackagesRequestResponseMapper;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.CDC)
public class GithubPackagesResourceServiceImplTest extends CategoryTest {
  private static String ACCOUNT_ID = "accountId";

  private static String ORG_IDENTIFIER = "orgIdentifier";

  private static String PROJECT_IDENTIFIER = "projectIdentifier";

  private static final String IDENTIFIER = "identifier";

  private static final String PACKAGE_NAME = "packageName";

  private static final String PACKAGE_TYPE = "packageType";

  private static final String INPUT = "<+input>-abc";

  private static final IdentifierRef IDENTIFIER_REF = IdentifierRef.builder()
                                                          .accountIdentifier(ACCOUNT_ID)
                                                          .identifier(IDENTIFIER)
                                                          .projectIdentifier(PROJECT_IDENTIFIER)
                                                          .orgIdentifier(ORG_IDENTIFIER)
                                                          .build();

  private static final String PACKAGE_NAME_MESSAGE = "value for packageName is empty or not provided";
  private static final String PACKAGE_TYPE_MESSAGE = "value for packageType is empty or not provided";

  @Mock ConnectorService connectorService;

  @Mock SecretManagerClientService secretManagerClientService;

  @Mock DelegateGrpcClientWrapper delegateGrpcClientWrapper;

  @Spy @InjectMocks GithubPackagesResourceServiceImpl githubPackagesResourceService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetBuildDetails() {
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
                                                .delegateSelectors(new HashSet<>())
                                                .build();

    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();

    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.GITHUB)
                                            .connectorConfig(githubConnectorDTO)
                                            .orgIdentifier("dummyOrg")
                                            .projectIdentifier("dummyProject")
                                            .build();

    ConnectorResponseDTO connectorResponse = ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();

    String packageName = "packageName";
    String packageType = "packageType";
    String versionRegex = "versionRegex";
    String org = "org";

    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>();

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

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder()
                                                                      .buildDetails(builds)
                                                                      .artifactDelegateResponses(new ArrayList<>())
                                                                      .isArtifactServerValid(true)
                                                                      .isArtifactSourceValid(true)
                                                                      .build();

    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .build();

    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(connectorResponse));

    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(encryptionDetails);

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(artifactTaskResponse);

    List<BuildDetails> versions = githubPackagesResourceService.getVersionsOfPackage(
        identifierRef, packageName, packageType, versionRegex, org, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertThat(versions).isNotNull();
    assertThat(versions.size()).isEqualTo(5);
    assertThat(versions.get(0).getNumber()).isEqualTo("b1");
    assertThat(versions.get(1).getUiDisplayName()).isEqualTo("Version# b2");
    assertThat(versions.get(2).getUiDisplayName()).isEqualTo("Version# b3");
    assertThat(versions.get(3).getUiDisplayName()).isEqualTo("Version# b4");
    assertThat(versions.get(4).getNumber()).isEqualTo("b5");

    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);

    verify(connectorService).get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier");

    verify(delegateGrpcClientWrapper).executeSyncTaskV2(delegateTaskRequestCaptor.capture());

    DelegateTaskRequest request = delegateTaskRequestCaptor.getValue();

    ArtifactTaskParameters taskParameters = (ArtifactTaskParameters) request.getTaskParameters();

    assertThat(taskParameters.getArtifactTaskType()).isEqualTo(ArtifactTaskType.GET_BUILDS);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetBuildDetailsWithEmptyVersionRegex() {
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
                                                .delegateSelectors(new HashSet<>())
                                                .build();

    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();

    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.GITHUB)
                                            .connectorConfig(githubConnectorDTO)
                                            .orgIdentifier("dummyOrg")
                                            .projectIdentifier("dummyProject")
                                            .build();

    ConnectorResponseDTO connectorResponse = ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();

    String packageName = "packageName";
    String packageType = "packageType";
    String versionRegex = "";
    String org = "org";

    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>();

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

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder()
                                                                      .buildDetails(builds)
                                                                      .artifactDelegateResponses(new ArrayList<>())
                                                                      .isArtifactServerValid(true)
                                                                      .isArtifactSourceValid(true)
                                                                      .build();

    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .build();

    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(connectorResponse));

    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(encryptionDetails);

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(artifactTaskResponse);

    List<BuildDetails> versions = githubPackagesResourceService.getVersionsOfPackage(
        identifierRef, packageName, packageType, versionRegex, org, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertThat(versions).isNotNull();
    assertThat(versions.size()).isEqualTo(0);
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
                                                .delegateSelectors(new HashSet<>())
                                                .build();

    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();

    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.GITHUB)
                                            .connectorConfig(githubConnectorDTO)
                                            .orgIdentifier("dummyOrg")
                                            .projectIdentifier("dummyProject")
                                            .build();

    ConnectorResponseDTO connectorResponse = ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();

    String packageName = "packageName";
    String packageType = "packageType";
    String versionRegex = "versionRegex";
    String org = "org";

    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>();

    List<BuildDetails> builds = new ArrayList<>();

    BuildDetails build1 = new BuildDetails();
    build1.setNumber("b1");
    build1.setUiDisplayName("Version# b1");

    builds.add(build1);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder()
                                                                      .buildDetails(builds)
                                                                      .artifactDelegateResponses(new ArrayList<>())
                                                                      .isArtifactServerValid(true)
                                                                      .isArtifactSourceValid(true)
                                                                      .build();

    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .build();

    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(connectorResponse));

    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(encryptionDetails);

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(artifactTaskResponse);

    BuildDetails lastSuccessfulVersion = githubPackagesResourceService.getLastSuccessfulVersion(identifierRef,
        packageName, packageType, null, versionRegex, org, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertThat(lastSuccessfulVersion).isNotNull();
    assertThat(lastSuccessfulVersion.getNumber()).isEqualTo("b1");
    assertThat(lastSuccessfulVersion.getUiDisplayName()).isEqualTo("Version# b1");

    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);

    verify(connectorService).get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier");

    verify(delegateGrpcClientWrapper).executeSyncTaskV2(delegateTaskRequestCaptor.capture());

    DelegateTaskRequest request = delegateTaskRequestCaptor.getValue();

    ArtifactTaskParameters taskParameters = (ArtifactTaskParameters) request.getTaskParameters();

    assertThat(taskParameters.getArtifactTaskType()).isEqualTo(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildWithEmptyVersionRegexAndVersion() {
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
                                                .delegateSelectors(new HashSet<>())
                                                .build();

    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();

    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.GITHUB)
                                            .connectorConfig(githubConnectorDTO)
                                            .orgIdentifier("dummyOrg")
                                            .projectIdentifier("dummyProject")
                                            .build();

    ConnectorResponseDTO connectorResponse = ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();

    String packageName = "packageName";
    String packageType = "packageType";
    String versionRegex = null;
    String org = "org";

    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>();

    List<BuildDetails> builds = new ArrayList<>();

    BuildDetails build1 = new BuildDetails();
    build1.setNumber("b1");
    build1.setUiDisplayName("Version# b1");

    builds.add(build1);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder()
                                                                      .buildDetails(builds)
                                                                      .artifactDelegateResponses(new ArrayList<>())
                                                                      .isArtifactServerValid(true)
                                                                      .isArtifactSourceValid(true)
                                                                      .build();

    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .build();

    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(connectorResponse));

    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(encryptionDetails);

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(artifactTaskResponse);

    BuildDetails lastSuccessfulVersion = githubPackagesResourceService.getLastSuccessfulVersion(
        identifierRef, packageName, packageType, null, null, org, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertThat(lastSuccessfulVersion).isNotNull();
    assertThat(lastSuccessfulVersion.getNumber()).isEqualTo("b1");
    assertThat(lastSuccessfulVersion.getUiDisplayName()).isEqualTo("Version# b1");

    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);

    verify(connectorService).get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier");

    verify(delegateGrpcClientWrapper).executeSyncTaskV2(delegateTaskRequestCaptor.capture());

    DelegateTaskRequest request = delegateTaskRequestCaptor.getValue();

    ArtifactTaskParameters taskParameters = (ArtifactTaskParameters) request.getTaskParameters();

    assertThat(taskParameters.getArtifactTaskType()).isEqualTo(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD);
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
                                                .delegateSelectors(new HashSet<>())
                                                .build();

    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();

    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.GITHUB)
                                            .connectorConfig(githubConnectorDTO)
                                            .orgIdentifier("dummyOrg")
                                            .projectIdentifier("dummyProject")
                                            .build();

    ConnectorResponseDTO connectorResponse = ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();

    String packageType = "packageType";
    String org = "org";

    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>();

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

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder()
            .buildDetails(null)
            .artifactDelegateResponses(githubPackagesArtifactDelegateResponses)
            .isArtifactServerValid(true)
            .isArtifactSourceValid(true)
            .build();

    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .build();

    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(connectorResponse));

    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(encryptionDetails);

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(artifactTaskResponse);

    GithubPackagesResponseDTO packageResponse = githubPackagesResourceService.getPackageDetails(
        identifierRef, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, packageType, org);

    assertThat(packageResponse).isNotNull();
    assertThat(packageResponse.getGithubPackageResponse().size()).isEqualTo(5);
    assertThat(packageResponse.getGithubPackageResponse().get(0).getPackageId()).isEqualTo("id1");
    assertThat(packageResponse.getGithubPackageResponse().get(1).getPackageName()).isEqualTo("name2");
    assertThat(packageResponse.getGithubPackageResponse().get(2).getPackageType()).isEqualTo("container");
    assertThat(packageResponse.getGithubPackageResponse().get(3).getVisibility()).isEqualTo("visibility4");
    assertThat(packageResponse.getGithubPackageResponse().get(4).getPackageUrl()).isEqualTo("url5");

    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);

    verify(connectorService).get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier");

    verify(delegateGrpcClientWrapper).executeSyncTaskV2(delegateTaskRequestCaptor.capture());

    DelegateTaskRequest request = delegateTaskRequestCaptor.getValue();

    ArtifactTaskParameters taskParameters = (ArtifactTaskParameters) request.getTaskParameters();

    assertThat(taskParameters.getArtifactTaskType()).isEqualTo(ArtifactTaskType.GET_GITHUB_PACKAGES);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testInvalidGithubApiAccessType() {
    SecretRefData secretRefData =
        SecretRefData.builder().identifier("id").decryptedValue("token".toCharArray()).scope(Scope.ACCOUNT).build();

    GithubOauthDTO githubOauthDTO = GithubOauthDTO.builder().tokenRef(secretRefData).build();

    GithubApiAccessDTO githubApiAccessDTO =
        GithubApiAccessDTO.builder().spec(githubOauthDTO).type(GithubApiAccessType.OAUTH).build();

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
                                                .delegateSelectors(new HashSet<>())
                                                .build();

    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();

    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.GITHUB)
                                            .connectorConfig(githubConnectorDTO)
                                            .orgIdentifier("dummyOrg")
                                            .projectIdentifier("dummyProject")
                                            .build();

    ConnectorResponseDTO connectorResponse = ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();

    String packageName = "packageName";
    String packageType = "packageType";
    String versionRegex = "versionRegex";
    String org = "org";

    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>();

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

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder()
                                                                      .buildDetails(builds)
                                                                      .artifactDelegateResponses(new ArrayList<>())
                                                                      .isArtifactServerValid(true)
                                                                      .isArtifactSourceValid(true)
                                                                      .build();

    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .build();

    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(connectorResponse));

    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(encryptionDetails);

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(artifactTaskResponse);

    assertThatThrownBy(()
                           -> githubPackagesResourceService.getVersionsOfPackage(identifierRef, packageName,
                               packageType, versionRegex, org, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Please select the authentication type for API Access as Token");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testInvalidApiAccess() {
    SecretRefData secretRefData =
        SecretRefData.builder().identifier("id").decryptedValue("token".toCharArray()).scope(Scope.ACCOUNT).build();

    GithubOauthDTO githubOauthDTO = GithubOauthDTO.builder().tokenRef(secretRefData).build();

    GithubApiAccessDTO githubApiAccessDTO =
        GithubApiAccessDTO.builder().spec(githubOauthDTO).type(GithubApiAccessType.OAUTH).build();

    GithubUsernameTokenDTO githubUsernameTokenDTO =
        GithubUsernameTokenDTO.builder().username("username").tokenRef(secretRefData).build();

    GithubHttpCredentialsDTO githubHttpCredentialsDTO = GithubHttpCredentialsDTO.builder()
                                                            .httpCredentialsSpec(githubUsernameTokenDTO)
                                                            .type(GithubHttpAuthenticationType.USERNAME_AND_TOKEN)
                                                            .build();

    GithubAuthenticationDTO githubAuthenticationDTO =
        GithubAuthenticationDTO.builder().authType(GitAuthType.HTTP).credentials(githubHttpCredentialsDTO).build();

    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                .apiAccess(null)
                                                .authentication(githubAuthenticationDTO)
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .executeOnDelegate(true)
                                                .url("url")
                                                .delegateSelectors(new HashSet<>())
                                                .build();

    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();

    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.GITHUB)
                                            .connectorConfig(githubConnectorDTO)
                                            .orgIdentifier("dummyOrg")
                                            .projectIdentifier("dummyProject")
                                            .build();

    ConnectorResponseDTO connectorResponse = ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();

    String packageName = "packageName";
    String packageType = "packageType";
    String versionRegex = "versionRegex";
    String org = "org";

    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>();

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

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder()
                                                                      .buildDetails(builds)
                                                                      .artifactDelegateResponses(new ArrayList<>())
                                                                      .isArtifactServerValid(true)
                                                                      .isArtifactSourceValid(true)
                                                                      .build();

    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .build();

    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(connectorResponse));

    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(encryptionDetails);

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(artifactTaskResponse);

    assertThatThrownBy(()
                           -> githubPackagesResourceService.getVersionsOfPackage(identifierRef, packageName,
                               packageType, versionRegex, org, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Please enable API Access for the Github Connector");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testIsGithubConnectorTrue() {
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
                                                .delegateSelectors(new HashSet<>())
                                                .build();

    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.GITHUB)
                                            .connectorConfig(githubConnectorDTO)
                                            .orgIdentifier("dummyOrg")
                                            .projectIdentifier("dummyProject")
                                            .build();

    ConnectorResponseDTO connectorResponseDTO = ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();

    assertThat(githubPackagesResourceService.isAGithubConnector(connectorResponseDTO)).isEqualTo(true);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testIsGithubConnectorFalse() {
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
                                                .delegateSelectors(new HashSet<>())
                                                .build();

    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.AWS)
                                            .connectorConfig(githubConnectorDTO)
                                            .orgIdentifier("dummyOrg")
                                            .projectIdentifier("dummyProject")
                                            .build();

    ConnectorResponseDTO connectorResponseDTO = ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();

    assertThat(githubPackagesResourceService.isAGithubConnector(connectorResponseDTO)).isEqualTo(false);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetConnectorThrowException() {
    SecretRefData secretRefData =
        SecretRefData.builder().identifier("id").decryptedValue("token".toCharArray()).scope(Scope.ACCOUNT).build();

    GithubTokenSpecDTO githubTokenSpecDTO = GithubTokenSpecDTO.builder().tokenRef(secretRefData).build();

    GithubApiAccessDTO githubApiAccessDTO =
        GithubApiAccessDTO.builder().spec(githubTokenSpecDTO).type(GithubApiAccessType.OAUTH).build();

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
                                                .delegateSelectors(new HashSet<>())
                                                .build();

    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();

    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.AWS)
                                            .connectorConfig(githubConnectorDTO)
                                            .orgIdentifier("dummyOrg")
                                            .projectIdentifier("dummyProject")
                                            .build();

    ConnectorResponseDTO connectorResponse = ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();

    String packageName = "packageName";
    String packageType = "packageType";
    String versionRegex = "versionRegex";
    String org = "org";

    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>();

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

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder()
                                                                      .buildDetails(builds)
                                                                      .artifactDelegateResponses(new ArrayList<>())
                                                                      .isArtifactServerValid(true)
                                                                      .isArtifactSourceValid(true)
                                                                      .build();

    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .build();

    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(connectorResponse));

    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(encryptionDetails);

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(artifactTaskResponse);

    assertThatThrownBy(()
                           -> githubPackagesResourceService.getVersionsOfPackage(identifierRef, packageName,
                               packageType, versionRegex, org, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetTaskExecutionResponseForStatusAsFailure() {
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
                                                .delegateSelectors(new HashSet<>())
                                                .build();

    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();

    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.GITHUB)
                                            .connectorConfig(githubConnectorDTO)
                                            .orgIdentifier("dummyOrg")
                                            .projectIdentifier("dummyProject")
                                            .build();

    ConnectorResponseDTO connectorResponse = ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();

    String packageName = "packageName";
    String packageType = "packageType";
    String versionRegex = "versionRegex";
    String org = "org";

    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>();

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

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder()
                                                                      .buildDetails(builds)
                                                                      .artifactDelegateResponses(new ArrayList<>())
                                                                      .isArtifactServerValid(true)
                                                                      .isArtifactSourceValid(true)
                                                                      .build();

    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                                    .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                    .build();

    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(connectorResponse));

    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(encryptionDetails);

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(artifactTaskResponse);

    assertThatThrownBy(()
                           -> githubPackagesResourceService.getVersionsOfPackage(identifierRef, packageName,
                               packageType, versionRegex, org, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(ArtifactServerException.class);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuild_PackageName_Null() {
    assertThatThrownBy(()
                           -> githubPackagesResourceService.getLastSuccessfulVersion(IDENTIFIER_REF, null, PACKAGE_TYPE,
                               null, null, ORG_IDENTIFIER, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(PACKAGE_NAME_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuild_PackageName_Input() {
    assertThatThrownBy(
        ()
            -> githubPackagesResourceService.getLastSuccessfulVersion(IDENTIFIER_REF, INPUT, PACKAGE_TYPE, null, null,
                ORG_IDENTIFIER, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(PACKAGE_NAME_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuild_PackageType_Null() {
    assertThatThrownBy(()
                           -> githubPackagesResourceService.getLastSuccessfulVersion(IDENTIFIER_REF, PACKAGE_NAME, null,
                               null, null, ORG_IDENTIFIER, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(PACKAGE_TYPE_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuild_PackageType_Input() {
    assertThatThrownBy(()
                           -> githubPackagesResourceService.getLastSuccessfulVersion(IDENTIFIER_REF, PACKAGE_NAME,
                               INPUT, null, null, ORG_IDENTIFIER, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(PACKAGE_TYPE_MESSAGE);
  }
}
