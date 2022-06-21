/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.stateutils.buildstate;

import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.executionplan.CIExecutionTestBase;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CodebaseUtilsTest extends CIExecutionTestBase {
  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetCompleteUrlForHttpRepoConnector() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.REPO)
                                 .url("https://github.com/test/repo")
                                 .authentication(GithubAuthenticationDTO.builder().authType(GitAuthType.HTTP).build())
                                 .build())
            .build();

    String completeURL = CodebaseUtils.getCompleteURLFromConnector(connectorDetails, null, null);
    assertThat(completeURL).isEqualTo("https://github.com/test/repo");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetCompleteUrlForSshRepoConnector() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.REPO)
                                 .url("git@github.com:test/test-repo.git")
                                 .authentication(GithubAuthenticationDTO.builder().authType(GitAuthType.SSH).build())
                                 .build())
            .build();

    String completeURL = CodebaseUtils.getCompleteURLFromConnector(connectorDetails, null, null);
    assertThat(completeURL).isEqualTo("git@github.com:test/test-repo.git");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetCompleteUrlForHttpAccountConnector() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.ACCOUNT)
                                 .url("https://github.com/test")
                                 .authentication(GithubAuthenticationDTO.builder().authType(GitAuthType.HTTP).build())
                                 .build())
            .build();

    String completeURL = CodebaseUtils.getCompleteURLFromConnector(connectorDetails, null, "repo");
    assertThat(completeURL).isEqualTo("https://github.com/test/repo");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetCompleteUrlForSshAccountConnector() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.ACCOUNT)
                                 .url("git@github.com:test")
                                 .authentication(GithubAuthenticationDTO.builder().authType(GitAuthType.SSH).build())
                                 .build())
            .build();

    String completeURL = CodebaseUtils.getCompleteURLFromConnector(connectorDetails, null, "test-repo");
    assertThat(completeURL).isEqualTo("git@github.com:test/test-repo");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetCompleteUrlForAzureHttpAccountConnector() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.AZURE_REPO)
            .connectorConfig(
                AzureRepoConnectorDTO.builder()
                    .connectionType(GitConnectionType.ACCOUNT)
                    .url("https://dev.azure.com/org")
                    .authentication(AzureRepoAuthenticationDTO.builder().authType(GitAuthType.HTTP).build())
                    .build())
            .build();

    String completeURL = CodebaseUtils.getCompleteURLFromConnector(connectorDetails, "project", "repo");
    assertThat(completeURL).isEqualTo("https://dev.azure.com/org/project/_git/repo");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetCompleteUrlForAzureSshAccountConnector() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.AZURE_REPO)
            .connectorConfig(AzureRepoConnectorDTO.builder()
                                 .connectionType(GitConnectionType.ACCOUNT)
                                 .url("git@ssh.dev.azure.com:v3/org")
                                 .authentication(AzureRepoAuthenticationDTO.builder().authType(GitAuthType.SSH).build())
                                 .build())
            .build();

    String completeURL = CodebaseUtils.getCompleteURLFromConnector(connectorDetails, "project", "repo");
    assertThat(completeURL).isEqualTo("git@ssh.dev.azure.com:v3/org/project/repo");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetCompleteUrlForAzureHttpAccountConnectorWithoutProjectName() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.AZURE_REPO)
            .connectorConfig(
                AzureRepoConnectorDTO.builder()
                    .connectionType(GitConnectionType.ACCOUNT)
                    .url("https://dev.azure.com/org")
                    .authentication(AzureRepoAuthenticationDTO.builder().authType(GitAuthType.HTTP).build())
                    .build())
            .build();

    assertThatThrownBy(() -> CodebaseUtils.getCompleteURLFromConnector(connectorDetails, null, "repo"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
