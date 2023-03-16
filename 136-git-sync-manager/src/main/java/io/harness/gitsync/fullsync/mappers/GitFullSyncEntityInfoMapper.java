/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.fullsync.mappers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.EntityType;
import io.harness.beans.EntityReference;
import io.harness.gitsync.core.beans.GitFullSyncEntityInfo;
import io.harness.gitsync.core.beans.GitFullSyncEntityInfo.SyncStatus;
import io.harness.gitsync.fullsync.dtos.GitFullSyncEntityInfoDTO;
import io.harness.ng.core.EntityDetail;

import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class GitFullSyncEntityInfoMapper {
  private EntityType getEntityType(EntityDetail entityDetail) {
    return Optional.ofNullable(entityDetail).map(EntityDetail::getType).orElse(null);
  }

  private String getName(EntityDetail entityDetail) {
    return Optional.ofNullable(entityDetail).map(EntityDetail::getName).orElse(null);
  }

  private String getEntityIdentifier(EntityDetail entityDetail) {
    return Optional.ofNullable(entityDetail)
        .map(EntityDetail::getEntityRef)
        .map(EntityReference::getIdentifier)
        .orElse(null);
  }

  public static GitFullSyncEntityInfoDTO toDTO(@NotNull GitFullSyncEntityInfo gitFullSyncEntityInfo) {
    return GitFullSyncEntityInfoDTO.builder()
        .accountIdentifier(gitFullSyncEntityInfo.getAccountIdentifier())
        .orgIdentifier(gitFullSyncEntityInfo.getOrgIdentifier())
        .projectIdentifier(gitFullSyncEntityInfo.getProjectIdentifier())
        .entityType(getEntityType(gitFullSyncEntityInfo.getEntityDetail()))
        .errorMessage(getLatestErrorMsg(gitFullSyncEntityInfo.getErrorMessage()))
        .branch(gitFullSyncEntityInfo.getBranchName())
        .repoName(gitFullSyncEntityInfo.getRepoName())
        .repoUrl(gitFullSyncEntityInfo.getRepoUrl())
        .name(getName(gitFullSyncEntityInfo.getEntityDetail()))
        .identifier(getEntityIdentifier(gitFullSyncEntityInfo.getEntityDetail()))
        .filePath(gitFullSyncEntityInfo.getFilePath())
        .rootFolder(gitFullSyncEntityInfo.getRootFolder())
        .retryCount(gitFullSyncEntityInfo.getRetryCount())
        .syncStatus(SyncStatus.valueOf(gitFullSyncEntityInfo.getSyncStatus()))
        .build();
  }

  private String getLatestErrorMsg(List<String> errorMessage) {
    if (isEmpty(errorMessage)) {
      return null;
    }
    int size = errorMessage.size();
    return errorMessage.get(size - 1);
  }
}
