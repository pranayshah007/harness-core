/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static java.util.Arrays.asList;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class GitStoreTypesTest extends CategoryTest {
  private static final ParameterField<String> BRANCH = ParameterField.createValueField("branch");
  private static final ParameterField<String> BRANCH_OVERRIDE = ParameterField.createValueField("new_branch");
  private static final ParameterField<String> COMMIT = ParameterField.createValueField("commit");
  private static final ParameterField<String> COMMIT_OVERRIDE = ParameterField.createValueField("new_commit");
  private static final ParameterField<String> CONNECTOR = ParameterField.createValueField("connector");
  private static final ParameterField<String> CONNECTOR_OVERRIDE = ParameterField.createValueField("new_connector");
  private static final ParameterField<String> REPONAME = ParameterField.createValueField("reponame");
  private static final ParameterField<String> REPONAME_OVERRIDE = ParameterField.createValueField("new_reponame");
  private static final ParameterField<String> FOLDER_PATH = ParameterField.createValueField("folder_path");
  private static final ParameterField<String> FOLDER_PATH_OVERRIDE = ParameterField.createValueField("folder_path");
  private static final ParameterField<List<String>> PATHS = ParameterField.createValueField(asList("file1", "file2"));
  private static final ParameterField<List<String>> PATHS_OVERRIDE =
      ParameterField.createValueField(asList("file3", "file4"));

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGitOverride() {
    GitStore emptyGitStore = GitStore.builder().build();
    GitStore branchGitStore =
        GitStore.builder().connectorRef(CONNECTOR).gitFetchType(FetchType.BRANCH).branch(BRANCH).paths(PATHS).build();
    GitStore commitGitStore = GitStore.builder()
                                  .connectorRef(CONNECTOR)
                                  .gitFetchType(FetchType.COMMIT)
                                  .commitId(COMMIT)
                                  .folderPath(FOLDER_PATH)
                                  .repoName(REPONAME)
                                  .build();

    testOverride(emptyGitStore,
        GitStore.builder()
            .connectorRef(CONNECTOR_OVERRIDE)
            .gitFetchType(FetchType.BRANCH)
            .branch(BRANCH_OVERRIDE)
            .folderPath(FOLDER_PATH_OVERRIDE)
            .paths(PATHS_OVERRIDE)
            .repoName(REPONAME_OVERRIDE)
            .build(),
        emptyGitStore.withConnectorRef(CONNECTOR_OVERRIDE)
            .withGitFetchType(FetchType.BRANCH)
            .withBranch(BRANCH_OVERRIDE)
            .withFolderPath(FOLDER_PATH_OVERRIDE)
            .withPaths(PATHS_OVERRIDE)
            .withRepoName(REPONAME_OVERRIDE));
    testOverride(branchGitStore, GitStore.builder().connectorRef(CONNECTOR_OVERRIDE).build(),
        branchGitStore.withConnectorRef(CONNECTOR_OVERRIDE));
    testOverride(
        branchGitStore, GitStore.builder().branch(BRANCH_OVERRIDE).build(), branchGitStore.withBranch(BRANCH_OVERRIDE));
    testOverride(commitGitStore, GitStore.builder().commitId(COMMIT_OVERRIDE).build(),
        commitGitStore.withCommitId(COMMIT_OVERRIDE));
    testOverride(branchGitStore, GitStore.builder().gitFetchType(FetchType.COMMIT).commitId(COMMIT_OVERRIDE).build(),
        branchGitStore.withGitFetchType(FetchType.COMMIT).withCommitId(COMMIT_OVERRIDE).withBranch(null));
    testOverride(commitGitStore, GitStore.builder().gitFetchType(FetchType.BRANCH).branch(BRANCH_OVERRIDE).build(),
        commitGitStore.withGitFetchType(FetchType.BRANCH).withBranch(BRANCH_OVERRIDE).withCommitId(null));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGitLabOverride() {
    GitLabStore emptyGitStore = GitLabStore.builder().build();
    GitLabStore branchGitStore = GitLabStore.builder()
                                     .connectorRef(CONNECTOR)
                                     .gitFetchType(FetchType.BRANCH)
                                     .branch(BRANCH)
                                     .paths(PATHS)
                                     .build();
    GitLabStore commitGitStore = GitLabStore.builder()
                                     .connectorRef(CONNECTOR)
                                     .gitFetchType(FetchType.COMMIT)
                                     .commitId(COMMIT)
                                     .folderPath(FOLDER_PATH)
                                     .repoName(REPONAME)
                                     .build();

    testOverride(emptyGitStore,
        GitLabStore.builder()
            .connectorRef(CONNECTOR_OVERRIDE)
            .gitFetchType(FetchType.BRANCH)
            .branch(BRANCH_OVERRIDE)
            .folderPath(FOLDER_PATH_OVERRIDE)
            .paths(PATHS_OVERRIDE)
            .repoName(REPONAME_OVERRIDE)
            .build(),
        emptyGitStore.withConnectorRef(CONNECTOR_OVERRIDE)
            .withGitFetchType(FetchType.BRANCH)
            .withBranch(BRANCH_OVERRIDE)
            .withFolderPath(FOLDER_PATH_OVERRIDE)
            .withPaths(PATHS_OVERRIDE)
            .withRepoName(REPONAME_OVERRIDE));
    testOverride(branchGitStore, GitLabStore.builder().connectorRef(CONNECTOR_OVERRIDE).build(),
        branchGitStore.withConnectorRef(CONNECTOR_OVERRIDE));
    testOverride(branchGitStore, GitLabStore.builder().branch(BRANCH_OVERRIDE).build(),
        branchGitStore.withBranch(BRANCH_OVERRIDE));
    testOverride(commitGitStore, GitLabStore.builder().commitId(COMMIT_OVERRIDE).build(),
        commitGitStore.withCommitId(COMMIT_OVERRIDE));
    testOverride(branchGitStore, GitLabStore.builder().gitFetchType(FetchType.COMMIT).commitId(COMMIT_OVERRIDE).build(),
        branchGitStore.withGitFetchType(FetchType.COMMIT).withCommitId(COMMIT_OVERRIDE).withBranch(null));
    testOverride(commitGitStore, GitLabStore.builder().gitFetchType(FetchType.BRANCH).branch(BRANCH_OVERRIDE).build(),
        commitGitStore.withGitFetchType(FetchType.BRANCH).withBranch(BRANCH_OVERRIDE).withCommitId(null));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGitHubOverride() {
    GithubStore emptyGitStore = GithubStore.builder().build();
    GithubStore branchGitStore = GithubStore.builder()
                                     .connectorRef(CONNECTOR)
                                     .gitFetchType(FetchType.BRANCH)
                                     .branch(BRANCH)
                                     .paths(PATHS)
                                     .build();
    GithubStore commitGitStore = GithubStore.builder()
                                     .connectorRef(CONNECTOR)
                                     .gitFetchType(FetchType.COMMIT)
                                     .commitId(COMMIT)
                                     .folderPath(FOLDER_PATH)
                                     .repoName(REPONAME)
                                     .build();

    testOverride(emptyGitStore,
        GithubStore.builder()
            .connectorRef(CONNECTOR_OVERRIDE)
            .gitFetchType(FetchType.BRANCH)
            .branch(BRANCH_OVERRIDE)
            .folderPath(FOLDER_PATH_OVERRIDE)
            .paths(PATHS_OVERRIDE)
            .repoName(REPONAME_OVERRIDE)
            .build(),
        emptyGitStore.withConnectorRef(CONNECTOR_OVERRIDE)
            .withGitFetchType(FetchType.BRANCH)
            .withBranch(BRANCH_OVERRIDE)
            .withFolderPath(FOLDER_PATH_OVERRIDE)
            .withPaths(PATHS_OVERRIDE)
            .withRepoName(REPONAME_OVERRIDE));
    testOverride(branchGitStore, GithubStore.builder().connectorRef(CONNECTOR_OVERRIDE).build(),
        branchGitStore.withConnectorRef(CONNECTOR_OVERRIDE));
    testOverride(branchGitStore, GithubStore.builder().branch(BRANCH_OVERRIDE).build(),
        branchGitStore.withBranch(BRANCH_OVERRIDE));
    testOverride(commitGitStore, GithubStore.builder().commitId(COMMIT_OVERRIDE).build(),
        commitGitStore.withCommitId(COMMIT_OVERRIDE));
    testOverride(branchGitStore, GithubStore.builder().gitFetchType(FetchType.COMMIT).commitId(COMMIT_OVERRIDE).build(),
        branchGitStore.withGitFetchType(FetchType.COMMIT).withCommitId(COMMIT_OVERRIDE).withBranch(null));
    testOverride(commitGitStore, GithubStore.builder().gitFetchType(FetchType.BRANCH).branch(BRANCH_OVERRIDE).build(),
        commitGitStore.withGitFetchType(FetchType.BRANCH).withBranch(BRANCH_OVERRIDE).withCommitId(null));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testBitbucketOverride() {
    BitbucketStore emptyGitStore = BitbucketStore.builder().build();
    BitbucketStore branchGitStore = BitbucketStore.builder()
                                        .connectorRef(CONNECTOR)
                                        .gitFetchType(FetchType.BRANCH)
                                        .branch(BRANCH)
                                        .paths(PATHS)
                                        .build();
    BitbucketStore commitGitStore = BitbucketStore.builder()
                                        .connectorRef(CONNECTOR)
                                        .gitFetchType(FetchType.COMMIT)
                                        .commitId(COMMIT)
                                        .folderPath(FOLDER_PATH)
                                        .repoName(REPONAME)
                                        .build();

    testOverride(emptyGitStore,
        BitbucketStore.builder()
            .connectorRef(CONNECTOR_OVERRIDE)
            .gitFetchType(FetchType.BRANCH)
            .branch(BRANCH_OVERRIDE)
            .folderPath(FOLDER_PATH_OVERRIDE)
            .paths(PATHS_OVERRIDE)
            .repoName(REPONAME_OVERRIDE)
            .build(),
        emptyGitStore.withConnectorRef(CONNECTOR_OVERRIDE)
            .withGitFetchType(FetchType.BRANCH)
            .withBranch(BRANCH_OVERRIDE)
            .withFolderPath(FOLDER_PATH_OVERRIDE)
            .withPaths(PATHS_OVERRIDE)
            .withRepoName(REPONAME_OVERRIDE));
    testOverride(branchGitStore, BitbucketStore.builder().connectorRef(CONNECTOR_OVERRIDE).build(),
        branchGitStore.withConnectorRef(CONNECTOR_OVERRIDE));
    testOverride(branchGitStore, BitbucketStore.builder().branch(BRANCH_OVERRIDE).build(),
        branchGitStore.withBranch(BRANCH_OVERRIDE));
    testOverride(commitGitStore, BitbucketStore.builder().commitId(COMMIT_OVERRIDE).build(),
        commitGitStore.withCommitId(COMMIT_OVERRIDE));
    testOverride(branchGitStore,
        BitbucketStore.builder().gitFetchType(FetchType.COMMIT).commitId(COMMIT_OVERRIDE).build(),
        branchGitStore.withGitFetchType(FetchType.COMMIT).withCommitId(COMMIT_OVERRIDE).withBranch(null));
    testOverride(commitGitStore,
        BitbucketStore.builder().gitFetchType(FetchType.BRANCH).branch(BRANCH_OVERRIDE).build(),
        commitGitStore.withGitFetchType(FetchType.BRANCH).withBranch(BRANCH_OVERRIDE).withCommitId(null));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testValidateAtRuntime() {
    BitbucketStore emptyGitStore = BitbucketStore.builder().gitFetchType(FetchType.BRANCH).build();

    Set<String> invalidParameters = emptyGitStore.validateAtRuntime();

    assertTrue(invalidParameters.contains("connectorRef"));
    assertTrue(invalidParameters.contains("branch"));
    assertTrue(invalidParameters.contains("folderPath"));
  }

  private void testOverride(GitStoreConfig origin, GitStoreConfig override, GitStoreConfig expected) {
    StoreConfig result = origin.applyOverrides(override);

    assertThat(result).isInstanceOf(GitStoreConfig.class);
    GitStoreConfig resultGitStore = (GitStoreConfig) result;
    assertThat(resultGitStore.getConnectorRef()).isEqualTo(expected.getConnectorRef());
    assertThat(resultGitStore.getBranch()).isEqualTo(expected.getBranch());
    assertThat(resultGitStore.getCommitId()).isEqualTo(expected.getCommitId());
    assertThat(resultGitStore.getFolderPath()).isEqualTo(expected.getFolderPath());
    assertThat(resultGitStore.getGitFetchType()).isEqualTo(expected.getGitFetchType());
    assertThat(resultGitStore.getPaths()).isEqualTo(expected.getPaths());
    assertThat(resultGitStore.getRepoName()).isEqualTo(expected.getRepoName());
  }
}
