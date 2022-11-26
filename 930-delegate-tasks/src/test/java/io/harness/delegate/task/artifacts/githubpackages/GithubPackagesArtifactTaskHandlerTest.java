/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.githubpackages;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.task.artifacts.ArtifactSourceType.GITHUB_PACKAGES;
import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.githubpackages.beans.GithubPackagesInternalConfig;
import io.harness.artifacts.githubpackages.service.GithubPackagesRegistryService;
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
import io.harness.delegate.task.artifacts.mappers.GithubPackagesRequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDC)
public class GithubPackagesArtifactTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock GithubPackagesRegistryService githubPackagesRegistryService;

  @Mock SecretDecryptionService secretDecryptionService;

  @InjectMocks GithubPackagesArtifactTaskHandler githubPackagesArtifactTaskHandler;

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

    GithubPackagesInternalConfig githubPackagesInternalConfig =
        GithubPackagesRequestResponseMapper.toGithubPackagesInternalConfig(githubPackagesArtifactDelegateRequest);

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
        .when(githubPackagesRegistryService)
        .getBuilds(githubPackagesInternalConfig, githubPackagesArtifactDelegateRequest.getPackageName(),
            githubPackagesArtifactDelegateRequest.getPackageType(), githubPackagesArtifactDelegateRequest.getOrg(),
            githubPackagesArtifactDelegateRequest.getVersionRegex());

    ArtifactTaskExecutionResponse executionResponse =
        githubPackagesArtifactTaskHandler.getBuilds(githubPackagesArtifactDelegateRequest);

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

    GithubPackagesInternalConfig githubPackagesInternalConfig =
        GithubPackagesRequestResponseMapper.toGithubPackagesInternalConfig(githubPackagesArtifactDelegateRequest);

    List<BuildDetails> builds = new ArrayList<>();

    BuildDetails build1 = new BuildDetails();
    build1.setNumber("b1");
    build1.setUiDisplayName("Version# b1");

    builds.add(build1);

    doReturn(build1)
        .when(githubPackagesRegistryService)
        .getLastSuccessfulBuildFromRegex(githubPackagesInternalConfig,
            githubPackagesArtifactDelegateRequest.getPackageName(),
            githubPackagesArtifactDelegateRequest.getPackageType(),
            githubPackagesArtifactDelegateRequest.getVersionRegex(), githubPackagesArtifactDelegateRequest.getOrg());

    GithubPackagesArtifactDelegateResponse githubPackagesArtifactDelegateResponse =
        GithubPackagesRequestResponseMapper.toGithubPackagesResponse(build1, githubPackagesArtifactDelegateRequest);

    ArtifactTaskExecutionResponse executionResponse =
        githubPackagesArtifactTaskHandler.getLastSuccessfulBuild(githubPackagesArtifactDelegateRequest);

    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getBuildDetails()).isNotNull();
    assertThat(executionResponse.getBuildDetails().size()).isEqualTo(1);
    assertThat(executionResponse.getBuildDetails().get(0).getNumber()).isEqualTo("b1");
    assertThat(executionResponse.getBuildDetails().get(0).getUiDisplayName()).isEqualTo("Version# b1");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildWithSpecificVersion() {
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
            .version("b1")
            .versionRegex(null)
            .encryptedDataDetails(new ArrayList<>())
            .build();

    GithubPackagesInternalConfig githubPackagesInternalConfig =
        GithubPackagesRequestResponseMapper.toGithubPackagesInternalConfig(githubPackagesArtifactDelegateRequest);

    List<BuildDetails> builds = new ArrayList<>();

    BuildDetails build1 = new BuildDetails();
    build1.setNumber("b1");
    build1.setUiDisplayName("Version# b1");

    builds.add(build1);

    doReturn(build1)
        .when(githubPackagesRegistryService)
        .getBuild(githubPackagesInternalConfig, githubPackagesArtifactDelegateRequest.getPackageName(),
            githubPackagesArtifactDelegateRequest.getPackageType(), githubPackagesArtifactDelegateRequest.getVersion(),
            githubPackagesArtifactDelegateRequest.getOrg());

    GithubPackagesArtifactDelegateResponse githubPackagesArtifactDelegateResponse =
        GithubPackagesRequestResponseMapper.toGithubPackagesResponse(build1, githubPackagesArtifactDelegateRequest);

    ArtifactTaskExecutionResponse executionResponse =
        githubPackagesArtifactTaskHandler.getLastSuccessfulBuild(githubPackagesArtifactDelegateRequest);

    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getBuildDetails()).isNotNull();
    assertThat(executionResponse.getBuildDetails().size()).isEqualTo(1);
    assertThat(executionResponse.getBuildDetails().get(0).getNumber()).isEqualTo("b1");
    assertThat(executionResponse.getBuildDetails().get(0).getUiDisplayName()).isEqualTo("Version# b1");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testListPackages() {
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

    GithubPackagesInternalConfig githubPackagesInternalConfig =
        GithubPackagesRequestResponseMapper.toGithubPackagesInternalConfig(githubPackagesArtifactDelegateRequest);

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

    doReturn(packageDetails)
        .when(githubPackagesRegistryService)
        .listPackages(githubPackagesInternalConfig, githubPackagesArtifactDelegateRequest.getPackageType(),
            githubPackagesArtifactDelegateRequest.getOrg());

    ArtifactTaskExecutionResponse executionResponse =
        githubPackagesArtifactTaskHandler.listPackages(githubPackagesArtifactDelegateRequest);

    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getArtifactDelegateResponses()).isNotNull();

    List<ArtifactDelegateResponse> packageResponse = executionResponse.getArtifactDelegateResponses();

    if (packageResponse.get(0) instanceof GithubPackagesArtifactDelegateResponse) {
      assertThat(((GithubPackagesArtifactDelegateResponse) packageResponse.get(0)).getPackageId())
          .isEqualTo(githubPackagesArtifactDelegateResponses.get(0).getPackageId());
      assertThat(((GithubPackagesArtifactDelegateResponse) packageResponse.get(1)).getPackageName())
          .isEqualTo(githubPackagesArtifactDelegateResponses.get(1).getPackageName());
      assertThat(((GithubPackagesArtifactDelegateResponse) packageResponse.get(2)).getPackageType())
          .isEqualTo(githubPackagesArtifactDelegateResponses.get(2).getPackageType());
      assertThat(((GithubPackagesArtifactDelegateResponse) packageResponse.get(3)).getPackageUrl())
          .isEqualTo(githubPackagesArtifactDelegateResponses.get(3).getPackageUrl());
      assertThat(((GithubPackagesArtifactDelegateResponse) packageResponse.get(4)).getPackageVisibility())
          .isEqualTo(githubPackagesArtifactDelegateResponses.get(4).getPackageVisibility());
    }
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testIsRegex() {
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
            .encryptedDataDetails(new ArrayList<>())
            .versionRegex("*")
            .version(null)
            .build();

    boolean b = githubPackagesArtifactTaskHandler.isRegex(githubPackagesArtifactDelegateRequest);

    assertThat(b).isEqualTo(true);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testDecryptRequestDTOs() {
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

    doReturn(null)
        .when(secretDecryptionService)
        .decrypt(githubPackagesArtifactDelegateRequest.getGithubConnectorDTO().getApiAccess().getSpec(),
            githubPackagesArtifactDelegateRequest.getEncryptedDataDetails());

    githubPackagesArtifactTaskHandler.decryptRequestDTOs(githubPackagesArtifactDelegateRequest);

    verify(secretDecryptionService)
        .decrypt(githubPackagesArtifactDelegateRequest.getGithubConnectorDTO().getApiAccess().getSpec(),
            githubPackagesArtifactDelegateRequest.getEncryptedDataDetails());
  }
}
