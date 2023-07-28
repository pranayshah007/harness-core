/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.service;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.BranchFilterParameters;
import io.harness.beans.RepoFilterParameters;
import io.harness.beans.Scope;
import io.harness.gitsync.common.dtos.GitBranchesResponseDTO;
import io.harness.gitsync.common.dtos.GitListBranchesResponse;
import io.harness.gitsync.common.dtos.GitListRepositoryResponse;
import io.harness.gitsync.common.dtos.GitRepositoryResponseDTO;
import io.harness.gitsync.common.dtos.ScmCommitFileResponseDTO;
import io.harness.gitsync.common.dtos.ScmCreateFileRequestDTO;
import io.harness.gitsync.common.dtos.ScmCreatePRRequestDTO;
import io.harness.gitsync.common.dtos.ScmCreatePRResponseDTO;
import io.harness.gitsync.common.dtos.ScmGetBatchFilesByBranchRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetBatchFilesResponseDTO;
import io.harness.gitsync.common.dtos.ScmGetBranchHeadCommitRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetBranchHeadCommitResponseDTO;
import io.harness.gitsync.common.dtos.ScmGetFileByBranchRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileByCommitIdRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileResponseDTO;
import io.harness.gitsync.common.dtos.ScmGetFileUrlRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileUrlResponseDTO;
import io.harness.gitsync.common.dtos.ScmListFilesRequestDTO;
import io.harness.gitsync.common.dtos.ScmListFilesResponseDTO;
import io.harness.gitsync.common.dtos.ScmUpdateFileRequestDTO;
import io.harness.gitsync.common.dtos.UserDetailsRequestDTO;
import io.harness.gitsync.common.dtos.UserDetailsResponseDTO;
import io.harness.gitsync.common.dtos.UserRepoResponse;
import io.harness.ng.beans.PageRequest;

import java.util.List;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_GITX, HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PL)
public interface ScmFacilitatorService {
  List<String> listBranchesUsingConnector(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String connectorIdentifierRef, String repoURL, PageRequest pageRequest, String searchTerm);

  List<GitRepositoryResponseDTO> listReposByRefConnector(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String connectorRef, PageRequest pageRequest, RepoFilterParameters repoFilterParameters,
      boolean applyGitXRepoAllowListFilter);

  GitListRepositoryResponse listReposV2(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String connectorRef, PageRequest pageRequest, RepoFilterParameters repoFilterParameters);

  ScmCommitFileResponseDTO createFile(ScmCreateFileRequestDTO scmCommitRequestDTO);

  ScmCommitFileResponseDTO updateFile(ScmUpdateFileRequestDTO scmUpdateFileRequestDTO);

  ScmCreatePRResponseDTO createPR(ScmCreatePRRequestDTO scmCreatePRRequestDTO);

  ScmGetFileResponseDTO getFileByBranch(ScmGetFileByBranchRequestDTO scmGetFileByBranchRequestDTO);

  ScmGetFileResponseDTO getFileByBranchV2(ScmGetFileByBranchRequestDTO scmGetFileByBranchRequestDTO);

  ScmGetBatchFilesResponseDTO getBatchFilesByBranch(
      ScmGetBatchFilesByBranchRequestDTO scmGetBatchFilesByBranchRequestDTO);

  List<UserRepoResponse> listAllReposForOnboardingFlow(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef);

  ScmGetFileResponseDTO getFileByCommitId(ScmGetFileByCommitIdRequestDTO scmGetFileByCommitIdRequestDTO);

  GitBranchesResponseDTO listBranchesV2(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String connectorRef, String repoName, PageRequest pageRequest, BranchFilterParameters branchFilterParameters);

  GitListBranchesResponse listBranchesV3(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String connectorRef, String repoName, PageRequest pageRequest, BranchFilterParameters branchFilterParameters);

  String getDefaultBranch(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef, String repoName);

  String getRepoUrl(Scope scope, String connectorRef, String repoName);

  ScmGetBranchHeadCommitResponseDTO getBranchHeadCommitDetails(
      ScmGetBranchHeadCommitRequestDTO scmGetBranchHeadCommitRequestDTO);

  ScmListFilesResponseDTO listFiles(ScmListFilesRequestDTO scmListFilesRequestDTO);

  ScmGetFileUrlResponseDTO getFileUrl(ScmGetFileUrlRequestDTO scmGetFileUrlRequestDTO);

  UserDetailsResponseDTO getUserDetails(UserDetailsRequestDTO authenticatedUserRequestDTO);

  void validateRepo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef, String repoName);
}
