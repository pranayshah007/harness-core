/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.azureartifacts;

import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
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
import io.harness.exception.GeneralException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.helpers.ext.azure.devops.AzureArtifactsFeed;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackage;
import software.wings.helpers.ext.azure.devops.AzureDevopsProject;
import software.wings.helpers.ext.jenkins.BuildDetails;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDC)
public class AzureArtifactsTaskNGTest extends CategoryTest {
  @Mock AzureArtifactsTaskHelper azureArtifactsTaskHelper;

  @InjectMocks
  private AzureArtifactsTaskNG azureArtifactsTaskNG = new AzureArtifactsTaskNG(
      DelegateTaskPackage.builder().data(TaskData.builder().build()).build(), null, null, null);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testRunObjectParams() throws IOException {
    assertThatThrownBy(() -> azureArtifactsTaskNG.run(new Object[10]))
        .hasMessage("not implemented")
        .isInstanceOf(NotImplementedException.class);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testRunForBuilds() {
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
            .build();

    AzureArtifactsDelegateRequest azureArtifactsDelegateRequest =
        AzureArtifactsDelegateRequest.builder()
            .project(null)
            .feed("feed")
            .packageId(null)
            .packageName("package")
            .packageType("maven")
            .versionRegex("*")
            .azureArtifactsConnectorDTO(azureArtifactsConnectorDTO)
            .build();

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(azureArtifactsDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_BUILDS)
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

    when(azureArtifactsTaskHelper.getArtifactCollectResponse(artifactTaskParameters)).thenReturn(artifactTaskResponse);

    assertThat(azureArtifactsTaskNG.run(artifactTaskParameters)).isEqualTo(artifactTaskResponse);

    verify(azureArtifactsTaskHelper).getArtifactCollectResponse(artifactTaskParameters);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testRunForLastSuccessfulBuild() {
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
            .build();

    AzureArtifactsDelegateRequest azureArtifactsDelegateRequest =
        AzureArtifactsDelegateRequest.builder()
            .project(null)
            .feed("feed")
            .packageId(null)
            .packageName("package")
            .packageType("maven")
            .versionRegex("*")
            .azureArtifactsConnectorDTO(azureArtifactsConnectorDTO)
            .build();

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(azureArtifactsDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                                                        .build();

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

    when(azureArtifactsTaskHelper.getArtifactCollectResponse(artifactTaskParameters)).thenReturn(artifactTaskResponse);

    assertThat(azureArtifactsTaskNG.run(artifactTaskParameters)).isEqualTo(artifactTaskResponse);

    verify(azureArtifactsTaskHelper).getArtifactCollectResponse(artifactTaskParameters);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testRunForValidateArtifactServer() {
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
            .build();

    AzureArtifactsDelegateRequest azureArtifactsDelegateRequest =
        AzureArtifactsDelegateRequest.builder()
            .project(null)
            .feed(null)
            .packageId(null)
            .packageName(null)
            .packageType(null)
            .version(null)
            .versionRegex(null)
            .azureArtifactsConnectorDTO(azureArtifactsConnectorDTO)
            .build();

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(azureArtifactsDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.VALIDATE_ARTIFACT_SERVER)
                                                        .build();

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder()
                                                                      .buildDetails(new ArrayList<>())
                                                                      .artifactDelegateResponses(new ArrayList<>())
                                                                      .isArtifactServerValid(true)
                                                                      .isArtifactSourceValid(true)
                                                                      .build();

    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .build();

    when(azureArtifactsTaskHelper.getArtifactCollectResponse(artifactTaskParameters)).thenReturn(artifactTaskResponse);

    assertThat(azureArtifactsTaskNG.run(artifactTaskParameters)).isEqualTo(artifactTaskResponse);

    verify(azureArtifactsTaskHelper).getArtifactCollectResponse(artifactTaskParameters);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testRunForListPackages() {
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
            .build();

    AzureArtifactsDelegateRequest azureArtifactsDelegateRequest =
        AzureArtifactsDelegateRequest.builder()
            .project(null)
            .feed("feed")
            .packageId(null)
            .packageName(null)
            .packageType("maven")
            .version(null)
            .azureArtifactsConnectorDTO(azureArtifactsConnectorDTO)
            .build();

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

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(azureArtifactsDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_AZURE_PACKAGES)
                                                        .build();

    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .build();

    when(azureArtifactsTaskHelper.getArtifactCollectResponse(artifactTaskParameters)).thenReturn(artifactTaskResponse);

    assertThat(azureArtifactsTaskNG.run(artifactTaskParameters)).isEqualTo(artifactTaskResponse);

    verify(azureArtifactsTaskHelper).getArtifactCollectResponse(artifactTaskParameters);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testRunForListFeeds() {
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
            .build();

    AzureArtifactsDelegateRequest azureArtifactsDelegateRequest =
        AzureArtifactsDelegateRequest.builder()
            .project(null)
            .feed(null)
            .packageId(null)
            .packageName(null)
            .packageType(null)
            .version(null)
            .azureArtifactsConnectorDTO(azureArtifactsConnectorDTO)
            .build();

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

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(azureArtifactsDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_AZURE_FEEDS)
                                                        .build();

    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .build();

    when(azureArtifactsTaskHelper.getArtifactCollectResponse(artifactTaskParameters)).thenReturn(artifactTaskResponse);

    assertThat(azureArtifactsTaskNG.run(artifactTaskParameters)).isEqualTo(artifactTaskResponse);

    verify(azureArtifactsTaskHelper).getArtifactCollectResponse(artifactTaskParameters);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testRunForListProjects() {
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
            .build();

    AzureArtifactsDelegateRequest azureArtifactsDelegateRequest =
        AzureArtifactsDelegateRequest.builder()
            .project(null)
            .feed(null)
            .packageId(null)
            .packageName(null)
            .packageType(null)
            .version(null)
            .azureArtifactsConnectorDTO(azureArtifactsConnectorDTO)
            .build();

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

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(azureArtifactsDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_AZURE_FEEDS)
                                                        .build();

    ArtifactTaskResponse artifactTaskResponse = ArtifactTaskResponse.builder()
                                                    .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .build();

    when(azureArtifactsTaskHelper.getArtifactCollectResponse(artifactTaskParameters)).thenReturn(artifactTaskResponse);

    assertThat(azureArtifactsTaskNG.run(artifactTaskParameters)).isEqualTo(artifactTaskResponse);

    verify(azureArtifactsTaskHelper).getArtifactCollectResponse(artifactTaskParameters);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testRunDoThrowException() {
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
            .build();

    AzureArtifactsDelegateRequest azureArtifactsDelegateRequest =
        AzureArtifactsDelegateRequest.builder()
            .project(null)
            .feed("feed")
            .packageId(null)
            .packageName("package")
            .packageType("maven")
            .versionRegex("*")
            .azureArtifactsConnectorDTO(azureArtifactsConnectorDTO)
            .build();

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(azureArtifactsDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_BUILDS)
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

    String message = "General Exception";

    when(azureArtifactsTaskHelper.getArtifactCollectResponse(any())).thenThrow(new GeneralException(message));

    assertThatThrownBy(() -> azureArtifactsTaskNG.run(artifactTaskParameters))
        .isInstanceOf(GeneralException.class)
        .hasMessage(message);

    verify(azureArtifactsTaskHelper).getArtifactCollectResponse(artifactTaskParameters);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testIsSupportingErrorFramework() {
    assertThat(azureArtifactsTaskNG.isSupportingErrorFramework()).isEqualTo(true);
  }
}
