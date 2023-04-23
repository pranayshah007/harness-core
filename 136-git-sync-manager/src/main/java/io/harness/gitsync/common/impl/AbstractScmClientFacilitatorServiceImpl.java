/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.IdentifierRef;
import io.harness.beans.gitsync.GitFileDetails;
import io.harness.beans.gitsync.GitFileDetails.GitFileDetailsBuilder;
import io.harness.beans.gitsync.GitFilePathDetails;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.impl.ConnectorErrorMessagesHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.gitsync.common.dtos.CreateGitFileRequestDTO;
import io.harness.gitsync.common.dtos.GitFileContent;
import io.harness.gitsync.common.dtos.UpdateGitFileRequestDTO;
import io.harness.gitsync.common.dtos.UserDetailsResponseDTO;
import io.harness.gitsync.common.helper.GitSyncConnectorHelper;
import io.harness.gitsync.common.helper.UserProfileHelper;
import io.harness.gitsync.common.service.ScmClientFacilitatorService;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.gitsync.helpers.ScmUserHelper;
import io.harness.gitsync.interceptor.GitSyncConstants;
import io.harness.gitsync.scm.ScmGitUtils;
import io.harness.impl.ScmResponseStatusUtils;
import io.harness.ng.userprofile.commons.SCMType;
import io.harness.product.ci.scm.proto.FileContent;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@FieldDefaults(level = AccessLevel.PROTECTED)
@OwnedBy(HarnessTeam.DX)
public abstract class AbstractScmClientFacilitatorServiceImpl implements ScmClientFacilitatorService {
  private ConnectorService connectorService;
  private ConnectorErrorMessagesHelper connectorErrorMessagesHelper;
  private YamlGitConfigService yamlGitConfigService;
  private UserProfileHelper userProfileHelper;
  private GitSyncConnectorHelper gitSyncConnectorHelper;

  @Inject
  protected AbstractScmClientFacilitatorServiceImpl(ConnectorService connectorService,
      ConnectorErrorMessagesHelper connectorErrorMessagesHelper, YamlGitConfigService yamlGitConfigService,
      UserProfileHelper userProfileHelper, GitSyncConnectorHelper gitSyncConnectorHelper) {
    this.connectorService = connectorService;
    this.connectorErrorMessagesHelper = connectorErrorMessagesHelper;
    this.yamlGitConfigService = yamlGitConfigService;
    this.userProfileHelper = userProfileHelper;
    this.gitSyncConnectorHelper = gitSyncConnectorHelper;
  }

  @Override
  public List<String> listBranchesForRepoByGitSyncConfig(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String yamlGitConfigIdentifier, io.harness.ng.beans.PageRequest pageRequest,
      String searchTerm) {
    YamlGitConfigDTO yamlGitConfig =
        yamlGitConfigService.get(projectIdentifier, orgIdentifier, accountIdentifier, yamlGitConfigIdentifier);
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(yamlGitConfig.getGitConnectorRef(),
        accountIdentifier, yamlGitConfig.getOrganizationIdentifier(), yamlGitConfig.getProjectIdentifier());
    return listBranchesForRepoByConnector(identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
        identifierRef.getProjectIdentifier(), identifierRef.getIdentifier(), yamlGitConfig.getRepo(), pageRequest,
        searchTerm);
  }

  ScmConnector getScmConnector(IdentifierRef connectorIdentifierRef) {
    final ConnectorResponseDTO connectorResponseDTO =
        connectorService
            .get(connectorIdentifierRef.getAccountIdentifier(), connectorIdentifierRef.getOrgIdentifier(),
                connectorIdentifierRef.getProjectIdentifier(), connectorIdentifierRef.getIdentifier())
            .orElseThrow(
                ()
                    -> new InvalidRequestException(connectorErrorMessagesHelper.createConnectorNotFoundMessage(
                        connectorIdentifierRef.getAccountIdentifier(), connectorIdentifierRef.getOrgIdentifier(),
                        connectorIdentifierRef.getProjectIdentifier(), connectorIdentifierRef.getIdentifier())));
    return (ScmConnector) connectorResponseDTO.getConnector().getConnectorConfig();
  }

  ScmConnector getSCMConnectorUsedInGitSyncConfig(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String connectorIdentifierRef, String repoWhereConnectorIsStored,
      String connectorBranch) {
    return gitSyncConnectorHelper.getScmConnector(accountIdentifier, orgIdentifier, projectIdentifier,
        connectorIdentifierRef, repoWhereConnectorIsStored, connectorBranch);
  }

  YamlGitConfigDTO getYamlGitConfigDTO(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String yamlGitConfigIdentifier) {
    return yamlGitConfigService.get(projectIdentifier, orgIdentifier, accountIdentifier, yamlGitConfigIdentifier);
  }

  IdentifierRef getConnectorIdentifierRef(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifierRef) {
    return IdentifierRefHelper.getIdentifierRef(
        connectorIdentifierRef, accountIdentifier, orgIdentifier, projectIdentifier);
  }

  void validateFileContentParams(String branch, String commitId) {
    if (commitId == null && branch == null) {
      throw new InvalidRequestException("One of branch or commit id should be present.", USER);
    }
  }

  GitFilePathDetails getGitFilePathDetails(
      String filePath, String branch, String commitId, boolean getOnlyFileContent) {
    // If commit id is present, branch is ignored
    branch = isEmpty(commitId) ? branch : null;
    return GitFilePathDetails.builder()
        .filePath(filePath)
        .branch(branch)
        .ref(commitId)
        .getOnlyFileContent(getOnlyFileContent)
        .build();
  }

  GitFileContent validateAndGetGitFileContent(FileContent fileContent) {
    ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(fileContent.getStatus(), fileContent.getError());
    return GitFileContent.builder().content(fileContent.getContent()).objectId(fileContent.getBlobId()).build();
  }

  ConnectorResponseDTO getConnectorResponseDTO(YamlGitConfigDTO gitSyncConfigDTO, String accountId) {
    final String connectorRef = gitSyncConfigDTO.getGitConnectorRef();
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
        connectorRef, accountId, gitSyncConfigDTO.getOrganizationIdentifier(), gitSyncConfigDTO.getProjectIdentifier());
    Optional<ConnectorResponseDTO> connectorDTO =
        gitSyncConnectorHelper.getConnectorFromDefaultBranchElseFromGitBranch(accountId,
            identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), identifierRef.getIdentifier(),
            gitSyncConfigDTO.getGitConnectorsRepo(), gitSyncConfigDTO.getGitConnectorsBranch());
    return connectorDTO.orElseThrow(
        ()
            -> new UnexpectedException(
                String.format("No connector found with the id %s, accountId %s, orgId %s, projectId %s",
                    gitSyncConfigDTO.getIdentifier(), accountId, gitSyncConfigDTO.getOrganizationIdentifier(),
                    gitSyncConfigDTO.getProjectIdentifier())));
  }

  void checkAndSetUserFromUserProfile(
      boolean useUserFromToken, YamlGitConfigDTO yamlGitConfigDTO, ConnectorResponseDTO connectorResponseDTO) {
    if (useUserFromToken) {
      userProfileHelper.setConnectorDetailsFromUserProfile(yamlGitConfigDTO, connectorResponseDTO);
    }
  }

  GitFileDetailsBuilder getGitFileDetails(String accountId, String yaml, String filePath, String folderPath,
      String commitMsg, String branch, SCMType scmType, String commitId) {
    final EmbeddedUser currentUser = ScmUserHelper.getCurrentUser();
    String filePathForPush = ScmGitUtils.createFilePath(folderPath, filePath);
    String scmUserName = getScmUserName(accountId, scmType);
    return GitFileDetails.builder()
        .branch(branch)
        .commitMessage(isEmpty(commitMsg) ? GitSyncConstants.COMMIT_MSG : commitMsg)
        .fileContent(yaml)
        .filePath(filePathForPush)
        .userEmail(currentUser.getEmail())
        .commitId(commitId)
        .userName(isEmpty(scmUserName) ? currentUser.getName() : scmUserName);
  }

  private String getScmUserName(String accountId, SCMType scmType) {
    String scmUserName = "";
    try {
      scmUserName = userProfileHelper.getScmUserName(accountId, scmType);
    } catch (Exception ex) {
      log.error("Error occurred while getting scm user", ex);
    }
    return scmUserName;
  }

  GitFileDetails getGitFileDetails(
      CreateGitFileRequestDTO createGitFileRequestDTO, Optional<UserDetailsResponseDTO> userDTO) {
    final EmbeddedUser currentUser = ScmUserHelper.getCurrentUser();
    String email = currentUser.getEmail();
    String userName = currentUser.getName();
    if (userDTO.isPresent()) {
      email = userDTO.get().getUserEmail();
      userName = userDTO.get().getUserName();
    }
    return GitFileDetails.builder()
        .branch(createGitFileRequestDTO.getBranchName())
        .commitMessage(isEmpty(createGitFileRequestDTO.getCommitMessage()) ? GitSyncConstants.COMMIT_MSG
                                                                           : createGitFileRequestDTO.getCommitMessage())
        .fileContent(createGitFileRequestDTO.getFileContent())
        .filePath(createGitFileRequestDTO.getFilePath())
        .userEmail(email)
        .userName(userName)
        .build();
  }

  GitFileDetails getGitFileDetails(
      UpdateGitFileRequestDTO updateGitFileRequestDTO, Optional<UserDetailsResponseDTO> userDTO) {
    final EmbeddedUser currentUser = ScmUserHelper.getCurrentUser();
    String email = currentUser.getEmail();
    String userName = currentUser.getName();
    if (userDTO.isPresent()) {
      email = userDTO.get().getUserEmail();
      userName = userDTO.get().getUserName();
    }
    return GitFileDetails.builder()
        .branch(updateGitFileRequestDTO.getBranchName())
        .commitMessage(isEmpty(updateGitFileRequestDTO.getCommitMessage()) ? GitSyncConstants.COMMIT_MSG
                                                                           : updateGitFileRequestDTO.getCommitMessage())
        .fileContent(updateGitFileRequestDTO.getFileContent())
        .filePath(updateGitFileRequestDTO.getFilePath())
        .commitId(updateGitFileRequestDTO.getOldCommitId())
        .oldFileSha(updateGitFileRequestDTO.getOldFileSha())
        .userEmail(email)
        .userName(userName)
        .build();
  }
}
