/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.gitFileLocation;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.beans.GitFileLocation;
import io.harness.gitsync.common.dtos.GitSyncEntityListDTO;
import io.harness.gitsync.common.dtos.GitSyncRepoFilesDTO;

import com.mongodb.client.result.DeleteResult;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(DX)
public interface GitFileLocationRepositoryCustom {
  List<String> getDistinctEntityName(Criteria criteria, String field);

  Page<GitFileLocation> getGitFileLocation(Criteria criteria, Pageable pageable);

  List<GitSyncRepoFilesDTO> getByProjectIdAndOrganizationIdAndAccountIdAndGitSyncConfigIdentifierListAndEntityTypeList(
      String projectIdentifier, String orgIdentifier, String accountIdentifier,
      List<String> gitSyncConfigIdentifierList, List<EntityType> entityTypeList, String searchTerm, int size);

  List<GitSyncEntityListDTO>
  getByProjectIdAndOrganizationIdAndAccountIdAndGitSyncConfigIdentifierAndEntityTypeListAndBranch(
      String projectIdentifier, String orgIdentifier, String accountIdentifier, String gitSyncConfigIdentifier,
      String branch, List<EntityType> entityTypeList, String searchTerm, int size);

  GitFileLocation update(Query query, Update update);

  DeleteResult deleteAll(Query query);

  GitFileLocation findOne(Criteria criteria);
}
