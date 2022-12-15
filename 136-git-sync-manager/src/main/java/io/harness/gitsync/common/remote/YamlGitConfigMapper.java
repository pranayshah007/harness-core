/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.remote;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.encryption.ScopeHelper.getScope;

import static io.fabric8.utils.Strings.nullIfEmpty;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.common.beans.YamlGitConfig;
import io.harness.gitsync.common.dtos.GitSyncConfigDTO;
import io.harness.gitsync.common.dtos.GitSyncFolderConfigDTO;
import io.harness.gitsync.common.dtos.GitSyncFolderConfigDTO.GitSyncFolderConfigDTOBuilder;
import io.harness.gitsync.common.impl.GitUtils;
import io.harness.utils.FilePathUtils;

import java.util.List;

@OwnedBy(DX)
public class YamlGitConfigMapper {
  public static final YamlGitConfig toYamlGitConfig(YamlGitConfigDTO yamlGitConfigDTO, String accountId) {
    return YamlGitConfig.builder()
        .accountId(yamlGitConfigDTO.getAccountIdentifier())
        .orgIdentifier(nullIfEmpty(yamlGitConfigDTO.getOrganizationIdentifier()))
        .identifier(nullIfEmpty(yamlGitConfigDTO.getIdentifier()))
        .name(yamlGitConfigDTO.getName())
        .projectIdentifier(nullIfEmpty(yamlGitConfigDTO.getProjectIdentifier()))
        .branch(yamlGitConfigDTO.getBranch())
        .repo(GitUtils.convertToUrlWithGit(yamlGitConfigDTO.getRepo()))
        .gitConnectorRef(yamlGitConfigDTO.getGitConnectorRef())
        .scope(yamlGitConfigDTO.getScope())
        .rootFolders(yamlGitConfigDTO.getRootFolders())
        .defaultRootFolder(yamlGitConfigDTO.getDefaultRootFolder())
        .accountId(accountId)
        .gitConnectorType(yamlGitConfigDTO.getGitConnectorType())
        .gitConnectorsRepo(yamlGitConfigDTO.getGitConnectorsRepo())
        .gitConnectorsBranch(yamlGitConfigDTO.getGitConnectorsBranch())
        .build();
  }

  public static final YamlGitConfigDTO toYamlGitConfigDTO(YamlGitConfig yamlGitConfig) {
    return YamlGitConfigDTO.builder()
        .accountIdentifier(yamlGitConfig.getAccountId())
        .organizationIdentifier(nullIfEmpty(yamlGitConfig.getOrgIdentifier()))
        .identifier(nullIfEmpty(yamlGitConfig.getIdentifier()))
        .name(yamlGitConfig.getName())
        .projectIdentifier(nullIfEmpty(yamlGitConfig.getProjectIdentifier()))
        .branch(yamlGitConfig.getBranch())
        .repo(GitUtils.convertToUrlWithGit(yamlGitConfig.getRepo()))
        .gitConnectorRef(yamlGitConfig.getGitConnectorRef())
        .scope(yamlGitConfig.getScope())
        .rootFolders(yamlGitConfig.getRootFolders())
        .defaultRootFolder(yamlGitConfig.getDefaultRootFolder())
        .gitConnectorType(yamlGitConfig.getGitConnectorType())
        .gitConnectorsRepo(yamlGitConfig.getGitConnectorsRepo())
        .gitConnectorsBranch(yamlGitConfig.getGitConnectorsBranch())
        .build();
  }

  public static final YamlGitConfigDTO toYamlGitConfigDTO(GitSyncConfigDTO gitSyncConfigDTO, String accountIdentifier) {
    Scope scope =
        getScope(accountIdentifier, gitSyncConfigDTO.getOrgIdentifier(), gitSyncConfigDTO.getProjectIdentifier());
    return YamlGitConfigDTO.builder()
        .accountIdentifier(accountIdentifier)
        .organizationIdentifier(gitSyncConfigDTO.getOrgIdentifier())
        .projectIdentifier(gitSyncConfigDTO.getProjectIdentifier())
        .scope(scope)
        .rootFolders(toRootFolders(gitSyncConfigDTO.getGitSyncFolderConfigDTOs()))
        .defaultRootFolder(getDefaultRootFolder(gitSyncConfigDTO.getGitSyncFolderConfigDTOs()))
        .gitConnectorRef(gitSyncConfigDTO.getGitConnectorRef())
        .branch(gitSyncConfigDTO.getBranch())
        .repo(GitUtils.convertToUrlWithGit(gitSyncConfigDTO.getRepo()))
        .name(gitSyncConfigDTO.getName())
        .identifier(gitSyncConfigDTO.getIdentifier())
        .gitConnectorType(gitSyncConfigDTO.getGitConnectorType())
        .build();
  }

  private static List<YamlGitConfigDTO.RootFolder> toRootFolders(List<GitSyncFolderConfigDTO> gitSyncFolderConfigDTOS) {
    if (isEmpty(gitSyncFolderConfigDTOS)) {
      return null;
    }
    return gitSyncFolderConfigDTOS.stream().map(YamlGitConfigMapper::getRootFolders).collect(toList());
  }

  private static YamlGitConfigDTO.RootFolder getDefaultRootFolder(
      List<GitSyncFolderConfigDTO> gitSyncFolderConfigDTOS) {
    if (isEmpty(gitSyncFolderConfigDTOS)) {
      return null;
    }
    List<GitSyncFolderConfigDTO> gitSyncFolderDTOList =
        gitSyncFolderConfigDTOS.stream().filter(GitSyncFolderConfigDTO::getIsDefault).collect(toList());
    if (gitSyncFolderDTOList.size() > 1) {
      throw new InvalidRequestException("The repo config cannot have more than one default root folders");
    }
    if (isEmpty(gitSyncFolderDTOList)) {
      return null;
    }
    GitSyncFolderConfigDTO gitSyncFolderDTO = gitSyncFolderDTOList.get(0);
    return getRootFolders(gitSyncFolderDTO);
  }

  private static YamlGitConfigDTO.RootFolder getRootFolders(GitSyncFolderConfigDTO gitSyncFolderConfigDTO) {
    return YamlGitConfigDTO.RootFolder.builder().rootFolder(gitSyncFolderConfigDTO.getRootFolder()).build();
  }

  public static final GitSyncConfigDTO toSetupGitSyncDTO(YamlGitConfigDTO yamlGitConfig) {
    return GitSyncConfigDTO.builder()
        .identifier(yamlGitConfig.getIdentifier())
        .name(yamlGitConfig.getName())
        .orgIdentifier(yamlGitConfig.getOrganizationIdentifier())
        .projectIdentifier(yamlGitConfig.getProjectIdentifier())
        .gitConnectorRef(yamlGitConfig.getGitConnectorRef())
        .branch(yamlGitConfig.getBranch())
        .repo(GitUtils.convertToUrlWithGit(yamlGitConfig.getRepo()))
        .gitSyncFolderConfigDTOs(
            toGitSyncFolderDTO(yamlGitConfig.getRootFolders(), yamlGitConfig.getDefaultRootFolder()))
        .gitConnectorType(yamlGitConfig.getGitConnectorType())
        .build();
  }

  private static List<GitSyncFolderConfigDTO> toGitSyncFolderDTO(
      List<YamlGitConfigDTO.RootFolder> rootFolders, YamlGitConfigDTO.RootFolder defaultRootFolder) {
    if (isEmpty(rootFolders)) {
      return null;
    }
    return rootFolders.stream()
        .map(rootFolder -> {
          GitSyncFolderConfigDTOBuilder gitSyncFolderDTOBuilder =
              GitSyncFolderConfigDTO.builder().isDefault(false).rootFolder(
                  FilePathUtils.addStartingSlashIfMissing(rootFolder.getRootFolder()));
          if (rootFolder.equals(defaultRootFolder)) {
            gitSyncFolderDTOBuilder.isDefault(true);
          }
          return gitSyncFolderDTOBuilder.build();
        })
        .collect(toList());
  }
}
