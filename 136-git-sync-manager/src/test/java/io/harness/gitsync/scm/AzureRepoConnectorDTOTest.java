/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.scm;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.BHAVYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.beans.GitRepositoryDTO;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class AzureRepoConnectorDTOTest extends GitSyncTestBase {
  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetGitConnectionUrlForAzureRepoSSH() {
    String repoUrl = "git@ssh.dev.azure.com:v3/repoOrg/repoProject";
    AzureRepoConnectorDTO azureRepoConnectorDTO =
        AzureRepoConnectorDTO.builder()
            .connectionType(AzureRepoConnectionTypeDTO.PROJECT)
            .authentication(AzureRepoAuthenticationDTO.builder().authType(GitAuthType.SSH).build())
            .url(repoUrl)
            .build();
    assertThat(azureRepoConnectorDTO.getGitConnectionUrl(GitRepositoryDTO.builder().name("repoName").build()))
        .isEqualTo("git@ssh.dev.azure.com:v3/repoOrg/repoProject/repoName");
  }
}
