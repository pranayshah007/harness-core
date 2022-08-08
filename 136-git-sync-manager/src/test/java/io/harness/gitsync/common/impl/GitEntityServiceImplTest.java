/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.impl;

import static io.harness.rule.OwnerRule.BHAVYA;
import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.common.EntityReference;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.common.dtos.GitSyncEntityDTO;
import io.harness.gitsync.common.utils.GitSyncFilePathUtils;
import io.harness.ng.core.EntityDetail;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PL)
public class GitEntityServiceImplTest extends GitSyncTestBase {
  @Inject GitEntityServiceImpl gitEntityService;
  EntityDetail entityDetail;
  EntityReference entityReference;
  YamlGitConfigDTO yamlGitConfigDTO;
  public static final String ACCOUNT = "account";
  public static final String ORG = "org";
  public static final String PROJECT = "project";
  public static final String FOLDER_PATH = "folderPath";
  public static final String FILE_PATH = "filePath";
  public static final String BRANCH_NAME = "branchName";
  public static final String COMMIT_ID = "commitId";
  public static final String IDENTIFIER = "identifier";
  public static final String REPO_IDENTIFIER = "repoIdentifier";
  public static final String NAME = "name";
  public static final String REPO_URL = "repo_url";

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    entityReference = IdentifierRef.builder()
                          .repoIdentifier(REPO_IDENTIFIER)
                          .projectIdentifier(PROJECT)
                          .orgIdentifier(ORG)
                          .accountIdentifier(ACCOUNT)
                          .identifier(IDENTIFIER)
                          .isDefault(true)
                          .build();
    entityDetail = EntityDetail.builder().entityRef(entityReference).type(EntityType.CONNECTORS).name(NAME).build();
    yamlGitConfigDTO = YamlGitConfigDTO.builder()
                           .identifier(IDENTIFIER)
                           .accountIdentifier(ACCOUNT)
                           .organizationIdentifier(ORG)
                           .projectIdentifier(PROJECT)
                           .gitConnectorType(ConnectorType.GITHUB)
                           .branch(BRANCH_NAME)
                           .repo(REPO_URL)
                           .build();
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testDeleteAll() {
    gitEntityService.save(ACCOUNT, entityDetail, yamlGitConfigDTO, FOLDER_PATH, FILE_PATH, COMMIT_ID, BRANCH_NAME);
    gitEntityService.deleteAll(ACCOUNT, ORG, PROJECT);
    assertThat(gitEntityService.getDefaultEntities(ACCOUNT, ORG, PROJECT, IDENTIFIER)).hasSize(0);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testSave() {
    GitSyncEntityDTO savedEntity =
        gitEntityService.save(ACCOUNT, entityDetail, yamlGitConfigDTO, FOLDER_PATH, FILE_PATH, COMMIT_ID, BRANCH_NAME);
    assertThat(savedEntity.getRepo()).isEqualTo(REPO_URL);
    assertThat(savedEntity.getBranch()).isEqualTo(BRANCH_NAME);
    assertThat(savedEntity.getEntityIdentifier()).isEqualTo(IDENTIFIER);

    entityReference = IdentifierRef.builder()
                          .repoIdentifier(REPO_IDENTIFIER)
                          .projectIdentifier(PROJECT)
                          .orgIdentifier(ORG)
                          .accountIdentifier(ACCOUNT)
                          .identifier("id1")
                          .build();
    entityDetail = EntityDetail.builder().entityRef(entityReference).type(EntityType.CONNECTORS).name(NAME).build();
    gitEntityService.save(ACCOUNT, entityDetail, yamlGitConfigDTO, FOLDER_PATH, FILE_PATH, "commit1", BRANCH_NAME);

    String completeFilePath = GitSyncFilePathUtils.createFilePath(FOLDER_PATH, FILE_PATH);
    Optional<GitSyncEntityDTO> updatedEntity = gitEntityService.get(ACCOUNT, completeFilePath, REPO_URL, BRANCH_NAME);
    assertThat(updatedEntity.isPresent()).isEqualTo(true);
    assertThat(updatedEntity.get().getRepo()).isEqualTo(REPO_URL);
    assertThat(updatedEntity.get().getBranch()).isEqualTo(BRANCH_NAME);
    assertThat(updatedEntity.get().getEntityIdentifier()).isEqualTo("id1");
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testSaveWithSameBranchAndSameRepoIdButDiffRepo() {
    gitEntityService.save(ACCOUNT, entityDetail, yamlGitConfigDTO, FOLDER_PATH, FILE_PATH, COMMIT_ID, BRANCH_NAME);

    yamlGitConfigDTO = YamlGitConfigDTO.builder()
                           .identifier(IDENTIFIER)
                           .accountIdentifier(ACCOUNT)
                           .organizationIdentifier(ORG)
                           .projectIdentifier("project1")
                           .repo("REPO_URL1")
                           .gitConnectorType(ConnectorType.GITHUB)
                           .build();
    gitEntityService.save(ACCOUNT, entityDetail, yamlGitConfigDTO, FOLDER_PATH, FILE_PATH, COMMIT_ID, BRANCH_NAME);

    String completeFilePath = GitSyncFilePathUtils.createFilePath(FOLDER_PATH, FILE_PATH);
    Optional<GitSyncEntityDTO> entity1 = gitEntityService.get(ACCOUNT, completeFilePath, REPO_URL, BRANCH_NAME);
    Optional<GitSyncEntityDTO> entity2 = gitEntityService.get(ACCOUNT, completeFilePath, "REPO_URL1", BRANCH_NAME);

    assertThat(entity1.isPresent()).isEqualTo(true);
    assertThat(entity1.get().getEntityIdentifier()).isEqualTo(IDENTIFIER);
    assertThat(entity2.isPresent()).isEqualTo(true);
    assertThat(entity2.get().getEntityIdentifier()).isEqualTo(IDENTIFIER);
  }
}
