/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.service;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EntityReference;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.gitsync.common.beans.GitFileLocation;
import io.harness.gitsync.common.dtos.GitSyncEntityDTO;
import io.harness.gitsync.common.dtos.GitSyncEntityListDTO;
import io.harness.gitsync.common.dtos.GitSyncRepoFilesListDTO;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.EntityDetail;

import java.util.List;
import java.util.Optional;

@OwnedBy(DX)
public interface GitEntityService {
  PageResponse<GitSyncEntityListDTO> getPageByType(String projectIdentifier, String organizationIdentifier,
      String accountIdentifier, String gitSyncConfigIdentifier, String branch, EntityType entityType, int page,
      int size);

  GitSyncRepoFilesListDTO listSummary(String projectIdentifier, String organizationIdentifier, String accountIdentifier,
      ModuleType moduleType, String searchTerm, List<String> gitSyncConfigIdentifierList,
      List<EntityType> entityTypeList, int size);

  GitSyncEntityDTO get(EntityReference entityReference, EntityType entityType, String branch);

  GitSyncEntityDTO save(String accountId, EntityDetail entityDetail, YamlGitConfigDTO yamlGitConfig, String folderPath,
      String filePath, String commitId, String branchName);

  List<GitSyncEntityListDTO> listSummaryByRepoAndBranch(String projectIdentifier, String organizationIdentifier,
      String accountIdentifier, ModuleType moduleType, String searchTerm, String gitSyncConfigIdentifier, String branch,
      List<EntityType> entityTypeList, int size);

  List<GitFileLocation> getDefaultEntities(
      String accountIdentifier, String organizationIdentifier, String projectIdentifier, String yamlGitConfigId);

  Optional<GitSyncEntityDTO> get(String accountIdentifier, String completeFilePath, String repoUrl, String branch);

  void updateFilePath(String accountId, String prevFilePath, String repo, String branchName, String newFilePath);

  void deleteAll(String accountIdentifier, String orgIdentifier, String projectIdentifier);
}
