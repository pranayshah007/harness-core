/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.beans.GitConnectivityParams;
import io.harness.gitsync.common.dtos.GitBranchesResponseDTO;
import io.harness.gitsync.common.dtos.GitRepositoryResponseDTO;
import io.harness.gitsync.common.dtos.ScmCommitFileResponseDTO;
import io.harness.gitsync.common.dtos.ScmCreateFileRequestDTO;
import io.harness.gitsync.common.dtos.ScmCreatePRRequestDTO;
import io.harness.gitsync.common.dtos.ScmCreatePRResponseDTO;
import io.harness.gitsync.common.dtos.ScmGetFileByBranchRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileByCommitIdRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileResponseDTO;
import io.harness.gitsync.common.dtos.ScmUpdateFileRequestDTO;
import io.harness.gitsync.common.dtos.UserRepoResponse;
import io.harness.ng.beans.PageRequest;

import java.util.List;

@OwnedBy(HarnessTeam.PL)
public interface ScmFacilitatorService {
  List<String> listBranchesUsingConnector(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String connectorIdentifierRef, String repoURL, PageRequest pageRequest, String searchTerm);

  List<GitRepositoryResponseDTO> listReposByRefConnector(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String connectorRef, PageRequest pageRequest, String searchTerm,
      GitConnectivityParams gitConnectivityParams);

  ScmCommitFileResponseDTO createFile(ScmCreateFileRequestDTO scmCommitRequestDTO);

  ScmCommitFileResponseDTO updateFile(ScmUpdateFileRequestDTO scmUpdateFileRequestDTO);

  ScmCreatePRResponseDTO createPR(ScmCreatePRRequestDTO scmCreatePRRequestDTO);

  ScmGetFileResponseDTO getFileByBranch(ScmGetFileByBranchRequestDTO scmGetFileByBranchRequestDTO);

  List<UserRepoResponse> listAllReposByRefConnector(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef);

  ScmGetFileResponseDTO getFileByCommitId(ScmGetFileByCommitIdRequestDTO scmGetFileByCommitIdRequestDTO);

  GitBranchesResponseDTO listBranchesV2(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String connectorRef, String repoName, PageRequest pageRequest, String searchTerm,
      GitConnectivityParams gitConnectivityParams);

  String getDefaultBranch(String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef,
      String repoName, GitConnectivityParams gitConnectivityParams);
}
