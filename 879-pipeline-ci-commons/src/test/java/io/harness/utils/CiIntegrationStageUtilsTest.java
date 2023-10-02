/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.exception.InvalidArgumentsException;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CiIntegrationStageUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testRetrieveGenericGitConnectorURL_RepoConnectionType_ReturnsProvidedURL() {
    String repoName = "myrepo";
    GitConnectionType connectionType = GitConnectionType.REPO;
    String url = "https://github.com/myuser/myrepo";

    String result = CiIntegrationStageUtils.retrieveGenericGitConnectorURL(repoName, connectionType, url);

    assertThat(url).isEqualTo(result);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testRetrieveGenericGitConnectorURL_ProjectConnectionType_EmptyRepoName_ThrowsException() {
    String repoName = "";
    GitConnectionType connectionType = GitConnectionType.PROJECT;
    String url = "https://dev.azure.com/myorg/myproject";

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> CiIntegrationStageUtils.retrieveGenericGitConnectorURL(repoName, connectionType, url))
        .withMessageContaining("Repo name is not set ");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testRetrieveGenericGitConnectorURL_ProjectConnectionType_ValidArguments_ReturnsCorrectURL() {
    String repoName = "myrepo";
    GitConnectionType connectionType = GitConnectionType.PROJECT;
    String url = "https://dev.azure.com/myorg/myproject";

    String result = CiIntegrationStageUtils.retrieveGenericGitConnectorURL(repoName, connectionType, url);

    String expectedUrl = "https://dev.azure.com/myorg/myproject/_git/myrepo";
    assertThat(result).isEqualTo(expectedUrl);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testRetrieveGenericGitConnectorURL_AccountConnectionType_EmptyRepoName_ThrowsException() {
    String repoName = "";
    GitConnectionType connectionType = GitConnectionType.ACCOUNT;
    String url = "https://github.com/myuser";
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> CiIntegrationStageUtils.retrieveGenericGitConnectorURL(repoName, connectionType, url))
        .withMessageContaining("Repo name is not set");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testRetrieveGenericGitConnectorURL_AccountConnectionType_ValidArguments_ReturnsCorrectURL() {
    String repoName = "myrepo";
    GitConnectionType connectionType = GitConnectionType.ACCOUNT;
    String url = "https://github.com/myuser";

    String result = CiIntegrationStageUtils.retrieveGenericGitConnectorURL(repoName, connectionType, url);

    String expectedUrl = "https://github.com/myuser/myrepo";
    assertThat(expectedUrl).isEqualTo(result);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetFullyQualifiedImageName_NullConnectorDetails_ReturnsOriginalImageName() {
    String imageName = "myimage";
    ConnectorDetails connectorDetails = null;

    String result = CiIntegrationStageUtils.getFullyQualifiedImageName(imageName, connectorDetails);

    assertThat(imageName).isEqualTo(result);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetFullyQualifiedImageName_ConnectorTypeNotDocker_ReturnsOriginalImageName() {
    String imageName = "myimage";
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorType(ConnectorType.GIT)
                                            .connectorConfig(GithubConnectorDTO.builder().build())
                                            .build();

    String result = CiIntegrationStageUtils.getFullyQualifiedImageName(imageName, connectorDetails);

    assertThat(imageName).isEqualTo(result);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetFullyQualifiedImageName_ConnectorTypeDocker_RegistryUrlMalformed_ThrowsException() {
    String imageName = "myimage";
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.DOCKER)
            .connectorConfig(DockerConnectorDTO.builder()
                                 .auth(DockerAuthenticationDTO.builder().authType(DockerAuthType.ANONYMOUS).build())
                                 .dockerRegistryUrl("invalidUrl")
                                 .build())
            .build();
    connectorDetails.setConnectorType(ConnectorType.DOCKER);
    DockerConnectorDTO dockerConnectorDTO = new DockerConnectorDTO();
    dockerConnectorDTO.setDockerRegistryUrl("invalidUrl");
    connectorDetails.setConnectorConfig(dockerConnectorDTO);

    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(() -> CiIntegrationStageUtils.getFullyQualifiedImageName(imageName, connectorDetails))
        .withMessageContaining("Malformed registryUrl invalidUrl");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetFullyQualifiedImageName_ConnectorTypeDocker_ImageNameAlreadyQualified_ReturnsOriginalImageName() {
    String imageName = "myregistry.com/myimage";
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.DOCKER)
            .connectorConfig(DockerConnectorDTO.builder()
                                 .auth(DockerAuthenticationDTO.builder().authType(DockerAuthType.ANONYMOUS).build())
                                 .dockerRegistryUrl("https://myregistry.com")
                                 .build())
            .build();

    String result = CiIntegrationStageUtils.getFullyQualifiedImageName(imageName, connectorDetails);

    assertThat(imageName).isEqualTo(result);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void
  testGetFullyQualifiedImageName_ConnectorTypeDocker_RegistryHostNameIndexDockerIo_ReturnsOriginalImageName() {
    String imageName = "myimage";
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.DOCKER)
            .connectorConfig(DockerConnectorDTO.builder()
                                 .auth(DockerAuthenticationDTO.builder().authType(DockerAuthType.ANONYMOUS).build())
                                 .dockerRegistryUrl("https://index.docker.io")
                                 .build())
            .build();

    String result = CiIntegrationStageUtils.getFullyQualifiedImageName(imageName, connectorDetails);

    assertThat(imageName).isEqualTo(result);
  }
}
