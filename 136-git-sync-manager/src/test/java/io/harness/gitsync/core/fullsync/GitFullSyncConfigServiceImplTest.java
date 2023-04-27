/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.fullsync;

import static io.harness.rule.OwnerRule.BHAVYA;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.PHOENIKX;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.common.beans.GitBranch;
import io.harness.gitsync.common.service.GitBranchService;
import io.harness.gitsync.fullsync.dtos.GitFullSyncConfigDTO;
import io.harness.gitsync.fullsync.dtos.GitFullSyncConfigRequestDTO;
import io.harness.repositories.fullSync.GitFullSyncConfigRepository;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Optional;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PL)
public class GitFullSyncConfigServiceImplTest extends GitSyncTestBase {
  public static final String YAML_GIT_CONFIG = "yamlGitConfig";
  public static final String BRANCH = "branch";
  public static final String PR_TITLE = "pr title";
  public static final String BASE_BRANCH = "baseBranch";
  public static final String ACCOUNT = "account";
  public static final String ORG = "org";
  public static final String PROJECT = "project";
  public static final String REPO_URL = "repo_url";
  private YamlGitConfigDTO yamlGitConfigDTO;
  private GitBranch gitBranch;
  @Inject private GitFullSyncConfigRepository gitFullSyncConfigRepository;
  @Inject private GitFullSyncConfigServiceImpl gitFullSyncConfigService;
  @Mock private GitBranchService gitBranchService;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    yamlGitConfigDTO = YamlGitConfigDTO.builder()
                           .accountIdentifier(ACCOUNT)
                           .projectIdentifier(PROJECT)
                           .organizationIdentifier(ORG)
                           .branch(BRANCH)
                           .repo(REPO_URL)
                           .identifier(YAML_GIT_CONFIG)
                           .gitConnectorType(ConnectorType.GIT)
                           .build();
    gitBranch = GitBranch.builder().branchName(BRANCH).repoURL(REPO_URL).build();
    when(gitBranchService.isBranchExists(any(), any(), any(), any(), any())).thenReturn(true);

    FieldUtils.writeField(gitFullSyncConfigService, "gitBranchService", gitBranchService, true);
  }

  private GitFullSyncConfigDTO create() {
    GitFullSyncConfigRequestDTO gitFullSyncConfigRequestDTO = GitFullSyncConfigRequestDTO.builder()
                                                                  .repoIdentifier(YAML_GIT_CONFIG)
                                                                  .createPullRequest(true)
                                                                  .branch(BRANCH)
                                                                  .prTitle(PR_TITLE)
                                                                  .baseBranch(BASE_BRANCH)
                                                                  .build();

    return gitFullSyncConfigService.createConfig(ACCOUNT, ORG, PROJECT, gitFullSyncConfigRequestDTO);
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testCreateConfig() {
    GitFullSyncConfigDTO gitFullSyncConfigDTO = create();
    assertThat(gitFullSyncConfigDTO).isNotNull();
    assertThat(gitFullSyncConfigDTO.getAccountIdentifier()).isEqualTo(ACCOUNT);
    assertThat(gitFullSyncConfigDTO.getOrgIdentifier()).isEqualTo(ORG);
    assertThat(gitFullSyncConfigDTO.getProjectIdentifier()).isEqualTo(PROJECT);
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testUpdateConfig() {
    create();
    GitFullSyncConfigRequestDTO gitFullSyncConfigRequestDTO =
        GitFullSyncConfigRequestDTO.builder().branch("branch1").repoIdentifier("yamlGitConfig1").build();
    GitFullSyncConfigDTO gitFullSyncConfigDTO =
        gitFullSyncConfigService.updateConfig(ACCOUNT, ORG, PROJECT, gitFullSyncConfigRequestDTO);
    assertThat(gitFullSyncConfigDTO).isNotNull();
    assertThat(gitFullSyncConfigDTO.getBranch()).isEqualTo("branch1");
    assertThat(gitFullSyncConfigDTO.getRepoIdentifier()).isEqualTo("yamlGitConfig1");
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testDeleteConfig() {
    create();
    boolean success = gitFullSyncConfigService.delete(ACCOUNT, ORG, PROJECT);
    assertThat(success).isEqualTo(true);
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testGetConfig() {
    create();
    Optional<GitFullSyncConfigDTO> gitFullSyncConfigDTOOptional = gitFullSyncConfigService.get(ACCOUNT, ORG, PROJECT);
    assertThat(gitFullSyncConfigDTOOptional).isNotEmpty();
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testGet_whenNoConfigExists() {
    assertThat(gitFullSyncConfigService.get(ACCOUNT, ORG, PROJECT)).isNotPresent();
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testUpdateConfig_whenSameConfigIsUpdated() {
    create();
    GitFullSyncConfigRequestDTO gitFullSyncConfigRequestDTO = GitFullSyncConfigRequestDTO.builder()
                                                                  .repoIdentifier(YAML_GIT_CONFIG)
                                                                  .createPullRequest(true)
                                                                  .branch(BRANCH)
                                                                  .prTitle(PR_TITLE)
                                                                  .baseBranch(BASE_BRANCH)
                                                                  .build();
    GitFullSyncConfigDTO gitFullSyncConfigDTO =
        gitFullSyncConfigService.updateConfig(ACCOUNT, ORG, PROJECT, gitFullSyncConfigRequestDTO);
    assertThat(gitFullSyncConfigDTO).isNotNull();
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testCreateConfig_whenNewBranchExist() {
    GitFullSyncConfigRequestDTO gitFullSyncConfigRequestDTO = GitFullSyncConfigRequestDTO.builder()
                                                                  .repoIdentifier(YAML_GIT_CONFIG)
                                                                  .createPullRequest(true)
                                                                  .branch(BRANCH)
                                                                  .prTitle(PR_TITLE)
                                                                  .baseBranch(BASE_BRANCH)
                                                                  .isNewBranch(true)
                                                                  .build();
    assertThatThrownBy(() -> gitFullSyncConfigService.createConfig(ACCOUNT, ORG, PROJECT, gitFullSyncConfigRequestDTO))
        .isInstanceOf(InvalidRequestException.class)
        .matches(ex -> ex.getMessage().equals(String.format("Branch [%s] already exist", BRANCH)));
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testDeleteAll() {
    create();
    gitFullSyncConfigService.deleteAll(ACCOUNT, ORG, PROJECT);
    assertThat(gitFullSyncConfigService.get(ACCOUNT, ORG, PROJECT).isPresent()).isFalse();
  }
}
