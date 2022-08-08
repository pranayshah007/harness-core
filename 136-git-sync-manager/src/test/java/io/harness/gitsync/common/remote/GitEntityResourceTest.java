/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.remote;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.HARI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.common.beans.GitFileLocation;
import io.harness.gitsync.common.dtos.GitSyncEntityListDTO;
import io.harness.gitsync.common.dtos.GitSyncRepoFilesDTO;
import io.harness.gitsync.common.dtos.GitSyncRepoFilesListDTO;
import io.harness.gitsync.common.dtos.RepoProviders;
import io.harness.gitsync.common.impl.GitEntityServiceImpl;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.repositories.gitFileLocation.GitFileLocationRepository;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(DX)
public class GitEntityResourceTest extends GitSyncTestBase {
  @Inject GitEntityResource gitEntityResource;
  @Inject GitFileLocationRepository gitFileLocationRepository;
  @Inject GitEntityServiceImpl gitEntityService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = HARI)
  @Category(UnitTests.class)
  public void testSummary() {
    final String master = "master";
    final String feature = "feature";
    final String accountId = "accountId";
    final String pipeline = EntityType.PIPELINES.name();
    final String connector = EntityType.CONNECTORS.name();
    final GitFileLocation gitFileLocation = buildGitFileLocation("repo1", master, accountId, pipeline, "id1", true);
    final GitFileLocation gitFileLocation1 = buildGitFileLocation("repo2", feature, accountId, connector, "id2", false);
    final GitFileLocation gitFileLocation2 = buildGitFileLocation("repo1", master, accountId, pipeline, "name1", true);
    final GitFileLocation gitFileLocation3 = buildGitFileLocation("repo2", master, accountId, connector, "id2", true);
    gitFileLocationRepository.saveAll(Arrays.asList(gitFileLocation, gitFileLocation, gitFileLocation, gitFileLocation1,
        gitFileLocation1, gitFileLocation2, gitFileLocation3));
    List<String> gitSyncConfigIdentifierList = Arrays.asList("repo1", "repo2");
    final List<EntityType> entityTypes = Arrays.asList(EntityType.PIPELINES, EntityType.CONNECTORS);
    final int size = 2;
    final GitSyncRepoFilesListDTO gitSyncRepoFilesListDTO = gitEntityService.listSummary(
        null, null, accountId, ModuleType.CORE, "id", gitSyncConfigIdentifierList, entityTypes, size);
    assertThat(gitSyncRepoFilesListDTO).isNotNull();
    assertThat(gitSyncRepoFilesListDTO.getModuleType() == ModuleType.CORE);
    List<GitSyncRepoFilesDTO> gitSyncRepoFilesDTOList = gitSyncRepoFilesListDTO.getGitSyncRepoFilesList();
    assertThat(gitSyncRepoFilesDTOList.size() == 2);
    assertThat(gitSyncRepoFilesDTOList.get(0).getGitSyncConfigIdentifier().equals("repo1"));
    assertThat(gitSyncRepoFilesDTOList.get(0).getGitSyncEntityLists().size() == 1);
    assertThat(gitSyncRepoFilesDTOList.get(0).getGitSyncEntityLists().get(0).getEntityType().getYamlName().equals(
        "Pipelines"));
    assertThat(gitSyncRepoFilesDTOList.get(0).getGitSyncEntityLists().get(0).getCount() == 3);
    assertThat(gitSyncRepoFilesDTOList.get(0).getGitSyncEntityLists().get(0).getGitSyncEntities().size() == size);
    assertThat(gitSyncRepoFilesDTOList.get(1).getGitSyncConfigIdentifier().equals("repo2"));
    assertThat(gitSyncRepoFilesDTOList.get(1).getGitSyncEntityLists().size() == 1);
    assertThat(gitSyncRepoFilesDTOList.get(1).getGitSyncEntityLists().get(0).getEntityType().getYamlName().equals(
        "Connectors"));
    assertThat(gitSyncRepoFilesDTOList.get(1).getGitSyncEntityLists().get(0).getCount() == 1);
    assertThat(gitSyncRepoFilesDTOList.get(1).getGitSyncEntityLists().get(0).getGitSyncEntities().size() == 1);
  }

  @Test
  @Owner(developers = HARI)
  @Category(UnitTests.class)
  public void listSummaryByRepoAndBranch() {
    final String gitSyncConfigId = "repo";
    final String master = "master";
    final String feature = "feature";
    final String accountId = "accountId";
    final String pipeline = EntityType.PIPELINES.name();
    final String id = "id";
    final String connector = EntityType.CONNECTORS.name();
    final String id1 = "id1";
    final String id2 = "id2";
    final ModuleType moduleType = ModuleType.CORE;
    final GitFileLocation gitFileLocation =
        buildGitFileLocation(gitSyncConfigId, master, accountId, pipeline, id, true);
    final GitFileLocation gitFileLocation1 =
        buildGitFileLocation(gitSyncConfigId, feature, accountId, pipeline, id1, false);
    final GitFileLocation gitFileLocation2 =
        buildGitFileLocation(gitSyncConfigId, master, accountId, connector, id2, true);
    gitFileLocationRepository.saveAll(Arrays.asList(gitFileLocation, gitFileLocation1, gitFileLocation2));
    final List<EntityType> entityTypes = Arrays.asList(EntityType.PIPELINES);
    final int size = 5;
    final List<GitSyncEntityListDTO> ngPageResponseResponseDTO = gitEntityService.listSummaryByRepoAndBranch(
        null, null, accountId, moduleType, "", gitSyncConfigId, feature, entityTypes, size);
    assertThat(ngPageResponseResponseDTO).isNotNull();
    assertThat(ngPageResponseResponseDTO.get(0).getGitSyncEntities().size() == 1);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  @Ignore("TODO: Fix AssertionError 'Expecting actual not to be null'")
  public void testListByType() {
    final String gitSyncConfigId = "repo";
    final String branch = "branch";
    final String accountId = "accountId";
    final String pipeline = EntityType.PIPELINES.name();
    final String id = "id";
    final String connector = EntityType.CONNECTORS.name();
    final String id1 = "id1";
    final GitFileLocation gitFileLocation =
        buildGitFileLocation(gitSyncConfigId, branch, accountId, pipeline, id, true);
    final GitFileLocation gitFileLocation1 =
        buildGitFileLocation(gitSyncConfigId, branch, accountId, connector, id1, true);
    gitFileLocationRepository.saveAll(Arrays.asList(gitFileLocation, gitFileLocation, gitFileLocation1));

    final ResponseDTO<PageResponse<GitSyncEntityListDTO>> ngPageResponseResponseDTO =
        gitEntityResource.listByType(null, null, accountId, "repo", "branch", EntityType.PIPELINES, 0, 5, "cd");
    final PageResponse<GitSyncEntityListDTO> data = ngPageResponseResponseDTO.getData();
    assertThat(data).isNotNull();
    assertThat(data.getContent()
                   .stream()
                   .flatMap(gitSyncEntityListDTO -> gitSyncEntityListDTO.getGitSyncEntities().stream())
                   .collect(Collectors.toList())
                   .size())
        .isEqualTo(2);
  }

  public GitFileLocation buildGitFileLocation(
      String gitSyncConfigId, String branch, String accountId, String pipeline, String id, boolean isDefault) {
    return GitFileLocation.builder()
        .gitSyncConfigId(gitSyncConfigId)
        .branch(branch)
        .entityType(pipeline)
        .entityIdentifier(id)
        .accountId(accountId)
        .scope(Scope.ACCOUNT)
        .folderPath("folderPath")
        .entityGitPath("filePath")
        .completeGitPath("folderPath/filePath")
        .repoProvider(RepoProviders.GITHUB)
        .isDefault(isDefault)
        .build();
  }
}
