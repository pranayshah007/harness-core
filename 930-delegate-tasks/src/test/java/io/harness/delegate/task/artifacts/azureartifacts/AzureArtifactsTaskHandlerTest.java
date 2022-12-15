/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.azureartifacts;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.azureartifacts.beans.AzureArtifactsInternalConfig;
import io.harness.artifacts.azureartifacts.service.AzureArtifactsRegistryService;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationType;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsCredentialsDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsTokenDTO;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.azure.devops.AzureArtifactsFeed;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackage;
import software.wings.helpers.ext.azure.devops.AzureDevopsProject;
import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDC)
public class AzureArtifactsTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock AzureArtifactsRegistryService azureArtifactsRegistryService;

  @InjectMocks AzureArtifactsTaskHandler azureArtifactsTaskHandler;

  @Mock SecretDecryptionService secretDecryptionService;

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testValidateArtifactServer() {
    AzureArtifactsInternalConfig azureArtifactsInternalConfig =
        AzureArtifactsInternalConfig.builder()
            .authMechanism("PersonalAccessToken")
            .registryUrl("https://dev.azure.com/automation-cdc/")
            .packageId(null)
            .username("")
            .password("")
            .token("value")
            .build();

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

    AzureArtifactsDelegateRequest sourceAttributes = AzureArtifactsDelegateRequest.builder()
                                                         .project(null)
                                                         .feed(null)
                                                         .packageId(null)
                                                         .packageName(null)
                                                         .packageType(null)
                                                         .azureArtifactsConnectorDTO(azureArtifactsConnectorDTO)
                                                         .build();

    doReturn(true).when(azureArtifactsRegistryService).validateCredentials(azureArtifactsInternalConfig);

    ArtifactTaskExecutionResponse executionResponse =
        azureArtifactsTaskHandler.validateArtifactServer(sourceAttributes);

    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.isArtifactServerValid()).isTrue();
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetBuilds() {
    AzureArtifactsInternalConfig azureArtifactsInternalConfig =
        AzureArtifactsInternalConfig.builder()
            .authMechanism("PersonalAccessToken")
            .registryUrl("https://dev.azure.com/automation-cdc/")
            .packageId(null)
            .username("")
            .password("")
            .token("value")
            .build();

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

    AzureArtifactsDelegateRequest sourceAttributes = AzureArtifactsDelegateRequest.builder()
                                                         .project(null)
                                                         .feed("feed")
                                                         .packageId(null)
                                                         .packageName("package")
                                                         .packageType("maven")
                                                         .versionRegex("*")
                                                         .azureArtifactsConnectorDTO(azureArtifactsConnectorDTO)
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

    doReturn(builds)
        .when(azureArtifactsRegistryService)
        .listPackageVersions(azureArtifactsInternalConfig, sourceAttributes.getPackageType(),
            sourceAttributes.getPackageName(), sourceAttributes.getVersionRegex(), sourceAttributes.getFeed(),
            sourceAttributes.getProject());

    ArtifactTaskExecutionResponse executionResponse = azureArtifactsTaskHandler.getBuilds(sourceAttributes);

    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getBuildDetails()).isNotNull();
    assertThat(executionResponse.getBuildDetails().size()).isEqualTo(5);
    assertThat(executionResponse.getBuildDetails().get(0).getNumber()).isEqualTo("b1");
    assertThat(executionResponse.getBuildDetails().get(1).getUiDisplayName()).isEqualTo("Version# b2");
    assertThat(executionResponse.getBuildDetails().get(2).getUiDisplayName()).isEqualTo("Version# b3");
    assertThat(executionResponse.getBuildDetails().get(3).getUiDisplayName()).isEqualTo("Version# b4");
    assertThat(executionResponse.getBuildDetails().get(4).getNumber()).isEqualTo("b5");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetBuildsFromRegex() {
    AzureArtifactsInternalConfig azureArtifactsInternalConfig =
        AzureArtifactsInternalConfig.builder()
            .authMechanism("PersonalAccessToken")
            .registryUrl("https://dev.azure.com/automation-cdc/")
            .packageId(null)
            .username("")
            .password("")
            .token("value")
            .build();

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

    AzureArtifactsDelegateRequest sourceAttributes = AzureArtifactsDelegateRequest.builder()
                                                         .project(null)
                                                         .feed("feed")
                                                         .packageId(null)
                                                         .packageName("package")
                                                         .packageType("maven")
                                                         .versionRegex("4*")
                                                         .azureArtifactsConnectorDTO(azureArtifactsConnectorDTO)
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

    doReturn(builds)
        .when(azureArtifactsRegistryService)
        .listPackageVersions(azureArtifactsInternalConfig, sourceAttributes.getPackageType(),
            sourceAttributes.getPackageName(), sourceAttributes.getVersionRegex(), sourceAttributes.getFeed(),
            sourceAttributes.getProject());

    ArtifactTaskExecutionResponse executionResponse = azureArtifactsTaskHandler.getBuilds(sourceAttributes);

    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getBuildDetails()).isNotNull();
    assertThat(executionResponse.getBuildDetails().size()).isEqualTo(1);
    assertThat(executionResponse.getBuildDetails().get(0).getNumber()).isEqualTo("b4");
    assertThat(executionResponse.getBuildDetails().get(0).getUiDisplayName()).isEqualTo("Version# b4");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFromRegex() {
    AzureArtifactsInternalConfig azureArtifactsInternalConfig =
        AzureArtifactsInternalConfig.builder()
            .authMechanism("PersonalAccessToken")
            .registryUrl("https://dev.azure.com/automation-cdc/")
            .packageId(null)
            .username("")
            .password("")
            .token("value")
            .build();

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

    AzureArtifactsDelegateRequest sourceAttributes = AzureArtifactsDelegateRequest.builder()
                                                         .project(null)
                                                         .feed("feed")
                                                         .packageId(null)
                                                         .packageName("package")
                                                         .packageType("maven")
                                                         .versionRegex("*")
                                                         .azureArtifactsConnectorDTO(azureArtifactsConnectorDTO)
                                                         .build();

    BuildDetails lastSuccessfulBuild = new BuildDetails();
    lastSuccessfulBuild.setNumber("b1");
    lastSuccessfulBuild.setUiDisplayName("Version# b1");

    doReturn(lastSuccessfulBuild)
        .when(azureArtifactsRegistryService)
        .getLastSuccessfulBuildFromRegex(azureArtifactsInternalConfig, sourceAttributes.getPackageType(),
            sourceAttributes.getPackageName(), sourceAttributes.getVersionRegex(), sourceAttributes.getFeed(),
            sourceAttributes.getProject(), sourceAttributes.getScope());

    ArtifactTaskExecutionResponse executionResponse =
        azureArtifactsTaskHandler.getLastSuccessfulBuild(sourceAttributes);

    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getBuildDetails()).isNotNull();
    assertThat(executionResponse.getBuildDetails().size()).isEqualTo(1);
    assertThat(executionResponse.getBuildDetails().get(0).getNumber()).isEqualTo("b1");
    assertThat(executionResponse.getBuildDetails().get(0).getUiDisplayName()).isEqualTo("Version# b1");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFromSpecificVersion() {
    AzureArtifactsInternalConfig azureArtifactsInternalConfig =
        AzureArtifactsInternalConfig.builder()
            .authMechanism("PersonalAccessToken")
            .registryUrl("https://dev.azure.com/automation-cdc/")
            .packageId(null)
            .username("")
            .password("")
            .token("value")
            .build();

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

    AzureArtifactsDelegateRequest sourceAttributes = AzureArtifactsDelegateRequest.builder()
                                                         .project(null)
                                                         .feed("feed")
                                                         .packageId(null)
                                                         .packageName("package")
                                                         .packageType("maven")
                                                         .version("b1")
                                                         .azureArtifactsConnectorDTO(azureArtifactsConnectorDTO)
                                                         .build();

    BuildDetails lastSuccessfulBuild = new BuildDetails();
    lastSuccessfulBuild.setNumber("b1");
    lastSuccessfulBuild.setUiDisplayName("Version# b1");

    doReturn(lastSuccessfulBuild)
        .when(azureArtifactsRegistryService)
        .getBuild(azureArtifactsInternalConfig, sourceAttributes.getPackageType(), sourceAttributes.getPackageName(),
            sourceAttributes.getVersion(), sourceAttributes.getFeed(), sourceAttributes.getProject());

    ArtifactTaskExecutionResponse executionResponse =
        azureArtifactsTaskHandler.getLastSuccessfulBuild(sourceAttributes);

    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getBuildDetails()).isNotNull();
    assertThat(executionResponse.getBuildDetails().size()).isEqualTo(1);
    assertThat(executionResponse.getBuildDetails().get(0).getNumber()).isEqualTo("b1");
    assertThat(executionResponse.getBuildDetails().get(0).getUiDisplayName()).isEqualTo("Version# b1");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetFeeds() {
    AzureArtifactsInternalConfig azureArtifactsInternalConfig =
        AzureArtifactsInternalConfig.builder()
            .authMechanism("PersonalAccessToken")
            .registryUrl("https://dev.azure.com/automation-cdc/")
            .packageId(null)
            .username("")
            .password("")
            .token("value")
            .build();

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

    AzureArtifactsDelegateRequest sourceAttributes = AzureArtifactsDelegateRequest.builder()
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

    doReturn(feeds)
        .when(azureArtifactsRegistryService)
        .listFeeds(azureArtifactsInternalConfig, sourceAttributes.getProject());

    ArtifactTaskExecutionResponse executionResponse =
        azureArtifactsTaskHandler.getAzureArtifactsFeeds(sourceAttributes);

    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getAzureArtifactsFeeds()).isNotNull();
    assertThat(executionResponse.getAzureArtifactsFeeds().size()).isEqualTo(5);
    assertThat(executionResponse.getAzureArtifactsFeeds().get(0).getId()).isEqualTo("f1");
    assertThat(executionResponse.getAzureArtifactsFeeds().get(1).getName()).isEqualTo("feed2");
    assertThat(executionResponse.getAzureArtifactsFeeds().get(2).getProject()).isEqualTo(null);
    assertThat(executionResponse.getAzureArtifactsFeeds().get(3).getFullyQualifiedName()).isEqualTo("feed4");
    assertThat(executionResponse.getAzureArtifactsFeeds().get(4).getId()).isEqualTo("f5");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetProjects() {
    AzureArtifactsInternalConfig azureArtifactsInternalConfig =
        AzureArtifactsInternalConfig.builder()
            .authMechanism("PersonalAccessToken")
            .registryUrl("https://dev.azure.com/automation-cdc/")
            .packageId(null)
            .username("")
            .password("")
            .token("value")
            .build();

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

    AzureArtifactsDelegateRequest sourceAttributes = AzureArtifactsDelegateRequest.builder()
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

    doReturn(projects).when(azureArtifactsRegistryService).listProjects(azureArtifactsInternalConfig);

    ArtifactTaskExecutionResponse executionResponse =
        azureArtifactsTaskHandler.getAzureArtifactsProjects(sourceAttributes);

    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getAzureArtifactsProjects()).isNotNull();
    assertThat(executionResponse.getAzureArtifactsProjects().size()).isEqualTo(5);
    assertThat(executionResponse.getAzureArtifactsProjects().get(0).getId()).isEqualTo("p1");
    assertThat(executionResponse.getAzureArtifactsProjects().get(1).getName()).isEqualTo("project2");
    assertThat(executionResponse.getAzureArtifactsProjects().get(2).getId()).isEqualTo("p3");
    assertThat(executionResponse.getAzureArtifactsProjects().get(3).getName()).isEqualTo("project4");
    assertThat(executionResponse.getAzureArtifactsProjects().get(4).getId()).isEqualTo("p5");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetPackages() {
    AzureArtifactsInternalConfig azureArtifactsInternalConfig =
        AzureArtifactsInternalConfig.builder()
            .authMechanism("PersonalAccessToken")
            .registryUrl("https://dev.azure.com/automation-cdc/")
            .packageId(null)
            .username("")
            .password("")
            .token("value")
            .build();

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

    AzureArtifactsDelegateRequest sourceAttributes = AzureArtifactsDelegateRequest.builder()
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

    doReturn(packages)
        .when(azureArtifactsRegistryService)
        .listPackages(azureArtifactsInternalConfig, sourceAttributes.getProject(), sourceAttributes.getFeed(),
            sourceAttributes.getPackageType());

    ArtifactTaskExecutionResponse executionResponse =
        azureArtifactsTaskHandler.getAzureArtifactsPackages(sourceAttributes);

    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getAzureArtifactsPackages()).isNotNull();
    assertThat(executionResponse.getAzureArtifactsPackages().size()).isEqualTo(5);
    assertThat(executionResponse.getAzureArtifactsPackages().get(0).getId()).isEqualTo("p1");
    assertThat(executionResponse.getAzureArtifactsPackages().get(1).getName()).isEqualTo("package2");
    assertThat(executionResponse.getAzureArtifactsPackages().get(2).getId()).isEqualTo("p3");
    assertThat(executionResponse.getAzureArtifactsPackages().get(3).getProtocolType()).isEqualTo("maven");
    assertThat(executionResponse.getAzureArtifactsPackages().get(4).getId()).isEqualTo("p5");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testDecryptDTOs() {
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
            .azureArtifactsConnectorDTO(azureArtifactsConnectorDTO)
            .build();

    doReturn(null)
        .when(secretDecryptionService)
        .decrypt(azureArtifactsDelegateRequest.getAzureArtifactsConnectorDTO()
                     .getAuth()
                     .getCredentials()
                     .getCredentialsSpec(),
            azureArtifactsDelegateRequest.getEncryptedDataDetails());

    azureArtifactsTaskHandler.decryptRequestDTOs(azureArtifactsDelegateRequest);

    verify(secretDecryptionService)
        .decrypt(azureArtifactsDelegateRequest.getAzureArtifactsConnectorDTO()
                     .getAuth()
                     .getCredentials()
                     .getCredentialsSpec(),
            azureArtifactsDelegateRequest.getEncryptedDataDetails());
  }
}
