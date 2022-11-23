/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.azureartifacts;

import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationType;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsCredentialsDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsTokenDTO;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.encryption.SecretRefData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.helpers.ext.azure.devops.AzureArtifactsFeed;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackage;
import software.wings.helpers.ext.azure.devops.AzureDevopsProject;
import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
public class AzureArtifactsResourceServiceImplTest extends CategoryTest {
  private static String ACCOUNT_ID = "accountId";

  private static String ORG_IDENTIFIER = "orgIdentifier";

  private static String PROJECT_IDENTIFIER = "projectIdentifier";

  @Mock ConnectorService connectorService;

  @Mock SecretManagerClientService secretManagerClientService;

  @Mock DelegateGrpcClientWrapper delegateGrpcClientWrapper;

  @Spy @InjectMocks AzureArtifactsResourceServiceImpl azureArtifactsResourceService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetBuildDetails() {
    SecretRefData tokenRef = SecretRefData.builder().decryptedValue("value".toCharArray()).build();

    AzureArtifactsTokenDTO azureArtifactsTokenDTO = AzureArtifactsTokenDTO.builder().tokenRef(tokenRef).build();

    AzureArtifactsCredentialsDTO azureArtifactsCredentialsDTO =
        AzureArtifactsCredentialsDTO.builder()
            .credentialsSpec(azureArtifactsTokenDTO)
            .type(AzureArtifactsAuthenticationType.PERSONAL_ACCESS_TOKEN)
            .build();

    AzureArtifactsAuthenticationDTO azureArtifactsAuthenticationDTO =
        AzureArtifactsAuthenticationDTO.builder().credentials(azureArtifactsCredentialsDTO).build();

    AzureArtifactsConnectorDTO azureArtifactsConnectorDTO =
        AzureArtifactsConnectorDTO.builder()
            .azureArtifactsUrl("https://dev.azure.com/automation-cdc/")
            .auth(azureArtifactsAuthenticationDTO)
            .delegateSelectors(new HashSet<>())
            .build();

    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();

    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.AZURE_ARTIFACTS)
                                            .connectorConfig(azureArtifactsConnectorDTO)
                                            .orgIdentifier("dummyOrg")
                                            .projectIdentifier("dummyProject")
                                            .build();

    ConnectorResponseDTO connectorResponse = ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();

    String packageName = "packageName";
    String packageType = "packageType";
    String feed = "feed";
    String project = "project";
    String versionRegex = "*";
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

    when(delegateGrpcClientWrapper.executeSyncTask(any())).thenReturn(artifactTaskResponse);

    List<BuildDetails> versions = azureArtifactsResourceService.listVersionsOfAzureArtifactsPackage(identifierRef,
        ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, project, feed, packageType, packageName, versionRegex);

    assertThat(versions).isNotNull();
    assertThat(versions.size()).isEqualTo(5);
    assertThat(versions.get(0).getNumber()).isEqualTo("b1");
    assertThat(versions.get(1).getUiDisplayName()).isEqualTo("Version# b2");
    assertThat(versions.get(2).getUiDisplayName()).isEqualTo("Version# b3");
    assertThat(versions.get(3).getUiDisplayName()).isEqualTo("Version# b4");
    assertThat(versions.get(4).getNumber()).isEqualTo("b5");

    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);

    verify(connectorService).get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier");

    verify(delegateGrpcClientWrapper).executeSyncTask(delegateTaskRequestCaptor.capture());

    DelegateTaskRequest request = delegateTaskRequestCaptor.getValue();

    ArtifactTaskParameters taskParameters = (ArtifactTaskParameters) request.getTaskParameters();

    assertThat(taskParameters.getArtifactTaskType()).isEqualTo(ArtifactTaskType.GET_BUILDS);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuild() {
    SecretRefData tokenRef = SecretRefData.builder().decryptedValue("value".toCharArray()).build();

    AzureArtifactsTokenDTO azureArtifactsTokenDTO = AzureArtifactsTokenDTO.builder().tokenRef(tokenRef).build();

    AzureArtifactsCredentialsDTO azureArtifactsCredentialsDTO =
        AzureArtifactsCredentialsDTO.builder()
            .credentialsSpec(azureArtifactsTokenDTO)
            .type(AzureArtifactsAuthenticationType.PERSONAL_ACCESS_TOKEN)
            .build();

    AzureArtifactsAuthenticationDTO azureArtifactsAuthenticationDTO =
        AzureArtifactsAuthenticationDTO.builder().credentials(azureArtifactsCredentialsDTO).build();

    AzureArtifactsConnectorDTO azureArtifactsConnectorDTO =
        AzureArtifactsConnectorDTO.builder()
            .azureArtifactsUrl("https://dev.azure.com/automation-cdc/")
            .auth(azureArtifactsAuthenticationDTO)
            .delegateSelectors(new HashSet<>())
            .build();

    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();

    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.AZURE_ARTIFACTS)
                                            .connectorConfig(azureArtifactsConnectorDTO)
                                            .orgIdentifier("dummyOrg")
                                            .projectIdentifier("dummyProject")
                                            .build();

    ConnectorResponseDTO connectorResponse = ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();

    String packageName = "packageName";
    String packageType = "packageType";
    String feed = "feed";
    String project = "project";
    String versionRegex = "*";
    String version = "version";

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

    when(delegateGrpcClientWrapper.executeSyncTask(any())).thenReturn(artifactTaskResponse);

    BuildDetails lastSuccessfulVersion = azureArtifactsResourceService.getLastSuccessfulVersion(identifierRef,
        ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, project, feed, packageType, packageName, version, versionRegex);

    assertThat(lastSuccessfulVersion).isNotNull();
    assertThat(lastSuccessfulVersion.getNumber()).isEqualTo("b1");
    assertThat(lastSuccessfulVersion.getUiDisplayName()).isEqualTo("Version# b1");

    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);

    verify(connectorService).get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier");

    verify(delegateGrpcClientWrapper).executeSyncTask(delegateTaskRequestCaptor.capture());

    DelegateTaskRequest request = delegateTaskRequestCaptor.getValue();

    ArtifactTaskParameters taskParameters = (ArtifactTaskParameters) request.getTaskParameters();

    assertThat(taskParameters.getArtifactTaskType()).isEqualTo(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testListPackages() {
    SecretRefData tokenRef = SecretRefData.builder().decryptedValue("value".toCharArray()).build();

    AzureArtifactsTokenDTO azureArtifactsTokenDTO = AzureArtifactsTokenDTO.builder().tokenRef(tokenRef).build();

    AzureArtifactsCredentialsDTO azureArtifactsCredentialsDTO =
        AzureArtifactsCredentialsDTO.builder()
            .credentialsSpec(azureArtifactsTokenDTO)
            .type(AzureArtifactsAuthenticationType.PERSONAL_ACCESS_TOKEN)
            .build();

    AzureArtifactsAuthenticationDTO azureArtifactsAuthenticationDTO =
        AzureArtifactsAuthenticationDTO.builder().credentials(azureArtifactsCredentialsDTO).build();

    AzureArtifactsConnectorDTO azureArtifactsConnectorDTO =
        AzureArtifactsConnectorDTO.builder()
            .azureArtifactsUrl("https://dev.azure.com/automation-cdc/")
            .auth(azureArtifactsAuthenticationDTO)
            .delegateSelectors(new HashSet<>())
            .build();

    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();

    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.AZURE_ARTIFACTS)
                                            .connectorConfig(azureArtifactsConnectorDTO)
                                            .orgIdentifier("dummyOrg")
                                            .projectIdentifier("dummyProject")
                                            .build();

    ConnectorResponseDTO connectorResponse = ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();

    String packageName = "null";
    String packageType = "packageType";
    String feed = "feed";
    String project = "project";
    String versionRegex = "null";
    String version = "null";

    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>();

    List<AzureArtifactsPackage> packages = new ArrayList<>();

    AzureArtifactsPackage package1 = new AzureArtifactsPackage();
    package1.setId("p1");
    package1.setName("package1");
    package1.setProtocolType("maven");

    AzureArtifactsPackage package2 = new AzureArtifactsPackage();
    package2.setId("p2");
    package2.setName("package2");
    package2.setProtocolType("maven");

    AzureArtifactsPackage package3 = new AzureArtifactsPackage();
    package3.setId("p3");
    package3.setName("package3");
    package3.setProtocolType("maven");

    AzureArtifactsPackage package4 = new AzureArtifactsPackage();
    package4.setId("p4");
    package4.setName("package4");
    package4.setProtocolType("maven");

    AzureArtifactsPackage package5 = new AzureArtifactsPackage();
    package5.setId("p5");
    package5.setName("package5");
    package5.setProtocolType("maven");

    packages.add(package1);
    packages.add(package2);
    packages.add(package3);
    packages.add(package4);
    packages.add(package5);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder()
                                                                      .buildDetails(null)
                                                                      .azureArtifactsPackages(packages)
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

    when(delegateGrpcClientWrapper.executeSyncTask(any())).thenReturn(artifactTaskResponse);

    List<AzureArtifactsPackage> packageResponse = azureArtifactsResourceService.listAzureArtifactsPackages(
        identifierRef, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, project, feed, packageType);

    assertThat(packageResponse).isNotNull();
    assertThat(packageResponse.size()).isEqualTo(5);
    assertThat(packageResponse.get(0).getId()).isEqualTo("p1");
    assertThat(packageResponse.get(1).getName()).isEqualTo("package2");
    assertThat(packageResponse.get(2).getId()).isEqualTo("p3");
    assertThat(packageResponse.get(3).getProtocolType()).isEqualTo("maven");
    assertThat(packageResponse.get(4).getId()).isEqualTo("p5");

    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);

    verify(connectorService).get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier");

    verify(delegateGrpcClientWrapper).executeSyncTask(delegateTaskRequestCaptor.capture());

    DelegateTaskRequest request = delegateTaskRequestCaptor.getValue();

    ArtifactTaskParameters taskParameters = (ArtifactTaskParameters) request.getTaskParameters();

    assertThat(taskParameters.getArtifactTaskType()).isEqualTo(ArtifactTaskType.GET_AZURE_PACKAGES);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testListFeeds() {
    SecretRefData tokenRef = SecretRefData.builder().decryptedValue("value".toCharArray()).build();

    AzureArtifactsTokenDTO azureArtifactsTokenDTO = AzureArtifactsTokenDTO.builder().tokenRef(tokenRef).build();

    AzureArtifactsCredentialsDTO azureArtifactsCredentialsDTO =
        AzureArtifactsCredentialsDTO.builder()
            .credentialsSpec(azureArtifactsTokenDTO)
            .type(AzureArtifactsAuthenticationType.PERSONAL_ACCESS_TOKEN)
            .build();

    AzureArtifactsAuthenticationDTO azureArtifactsAuthenticationDTO =
        AzureArtifactsAuthenticationDTO.builder().credentials(azureArtifactsCredentialsDTO).build();

    AzureArtifactsConnectorDTO azureArtifactsConnectorDTO =
        AzureArtifactsConnectorDTO.builder()
            .azureArtifactsUrl("https://dev.azure.com/automation-cdc/")
            .auth(azureArtifactsAuthenticationDTO)
            .delegateSelectors(new HashSet<>())
            .build();

    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();

    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.AZURE_ARTIFACTS)
                                            .connectorConfig(azureArtifactsConnectorDTO)
                                            .orgIdentifier("dummyOrg")
                                            .projectIdentifier("dummyProject")
                                            .build();

    ConnectorResponseDTO connectorResponse = ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();

    String packageName = null;
    String packageType = null;
    String feed = null;
    String project = null;
    String versionRegex = null;
    String version = null;

    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>();

    List<AzureArtifactsFeed> feeds = new ArrayList<>();

    AzureArtifactsFeed feed1 = new AzureArtifactsFeed();
    feed1.setId("f1");
    feed1.setName("feed1");
    feed1.setProject(null);
    feed1.setFullyQualifiedName("feed1");

    AzureArtifactsFeed feed2 = new AzureArtifactsFeed();
    feed2.setId("f2");
    feed2.setName("feed2");
    feed2.setProject(null);
    feed2.setFullyQualifiedName("feed2");

    AzureArtifactsFeed feed3 = new AzureArtifactsFeed();
    feed3.setId("f3");
    feed3.setName("feed3");
    feed3.setProject(null);
    feed3.setFullyQualifiedName("feed3");

    AzureArtifactsFeed feed4 = new AzureArtifactsFeed();
    feed4.setId("f4");
    feed4.setName("feed4");
    feed4.setProject(null);
    feed4.setFullyQualifiedName("feed4");

    AzureArtifactsFeed feed5 = new AzureArtifactsFeed();
    feed5.setId("f5");
    feed5.setName("feed5");
    feed5.setProject(null);
    feed5.setFullyQualifiedName("feed5");

    feeds.add(feed1);
    feeds.add(feed2);
    feeds.add(feed3);
    feeds.add(feed4);
    feeds.add(feed5);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder()
                                                                      .buildDetails(null)
                                                                      .azureArtifactsFeeds(feeds)
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

    when(delegateGrpcClientWrapper.executeSyncTask(any())).thenReturn(artifactTaskResponse);

    List<AzureArtifactsFeed> feedsResponse = azureArtifactsResourceService.listAzureArtifactsFeeds(
        identifierRef, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, project);

    assertThat(feedsResponse).isNotNull();
    assertThat(feedsResponse.size()).isEqualTo(5);
    assertThat(feedsResponse.get(0).getId()).isEqualTo("f1");
    assertThat(feedsResponse.get(1).getName()).isEqualTo("feed2");
    assertThat(feedsResponse.get(2).getId()).isEqualTo("f3");
    assertThat(feedsResponse.get(3).getFullyQualifiedName()).isEqualTo("feed4");
    assertThat(feedsResponse.get(4).getName()).isEqualTo("feed5");

    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);

    verify(connectorService).get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier");

    verify(delegateGrpcClientWrapper).executeSyncTask(delegateTaskRequestCaptor.capture());

    DelegateTaskRequest request = delegateTaskRequestCaptor.getValue();

    ArtifactTaskParameters taskParameters = (ArtifactTaskParameters) request.getTaskParameters();

    assertThat(taskParameters.getArtifactTaskType()).isEqualTo(ArtifactTaskType.GET_AZURE_FEEDS);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testListProjects() {
    SecretRefData tokenRef = SecretRefData.builder().decryptedValue("value".toCharArray()).build();

    AzureArtifactsTokenDTO azureArtifactsTokenDTO = AzureArtifactsTokenDTO.builder().tokenRef(tokenRef).build();

    AzureArtifactsCredentialsDTO azureArtifactsCredentialsDTO =
        AzureArtifactsCredentialsDTO.builder()
            .credentialsSpec(azureArtifactsTokenDTO)
            .type(AzureArtifactsAuthenticationType.PERSONAL_ACCESS_TOKEN)
            .build();

    AzureArtifactsAuthenticationDTO azureArtifactsAuthenticationDTO =
        AzureArtifactsAuthenticationDTO.builder().credentials(azureArtifactsCredentialsDTO).build();

    AzureArtifactsConnectorDTO azureArtifactsConnectorDTO =
        AzureArtifactsConnectorDTO.builder()
            .azureArtifactsUrl("https://dev.azure.com/automation-cdc/")
            .auth(azureArtifactsAuthenticationDTO)
            .delegateSelectors(new HashSet<>())
            .build();

    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();

    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.AZURE_ARTIFACTS)
                                            .connectorConfig(azureArtifactsConnectorDTO)
                                            .orgIdentifier("dummyOrg")
                                            .projectIdentifier("dummyProject")
                                            .build();

    ConnectorResponseDTO connectorResponse = ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();

    String packageName = null;
    String packageType = null;
    String feed = null;
    String project = null;
    String versionRegex = null;
    String version = null;

    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>();

    List<AzureDevopsProject> projects = new ArrayList<>();

    AzureDevopsProject project1 = new AzureDevopsProject();
    project1.setId("p1");
    project1.setName("project1");

    AzureDevopsProject project2 = new AzureDevopsProject();
    project2.setId("p2");
    project2.setName("project2");

    AzureDevopsProject project3 = new AzureDevopsProject();
    project3.setId("p3");
    project3.setName("project3");

    AzureDevopsProject project4 = new AzureDevopsProject();
    project4.setId("p4");
    project4.setName("project4");

    AzureDevopsProject project5 = new AzureDevopsProject();
    project5.setId("p5");
    project5.setName("project5");

    projects.add(project1);
    projects.add(project2);
    projects.add(project3);
    projects.add(project4);
    projects.add(project5);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder()
                                                                      .buildDetails(null)
                                                                      .azureArtifactsProjects(projects)
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

    when(delegateGrpcClientWrapper.executeSyncTask(any())).thenReturn(artifactTaskResponse);

    List<AzureDevopsProject> projectsResponse = azureArtifactsResourceService.listAzureArtifactsProjects(
        identifierRef, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertThat(projectsResponse).isNotNull();
    assertThat(projectsResponse.size()).isEqualTo(5);
    assertThat(projectsResponse.get(0).getId()).isEqualTo("p1");
    assertThat(projectsResponse.get(1).getName()).isEqualTo("project2");
    assertThat(projectsResponse.get(2).getId()).isEqualTo("p3");
    assertThat(projectsResponse.get(3).getName()).isEqualTo("project4");
    assertThat(projectsResponse.get(4).getId()).isEqualTo("p5");

    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);

    verify(connectorService).get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier");

    verify(delegateGrpcClientWrapper).executeSyncTask(delegateTaskRequestCaptor.capture());

    DelegateTaskRequest request = delegateTaskRequestCaptor.getValue();

    ArtifactTaskParameters taskParameters = (ArtifactTaskParameters) request.getTaskParameters();

    assertThat(taskParameters.getArtifactTaskType()).isEqualTo(ArtifactTaskType.GET_AZURE_PROJECTS);
  }
}
