/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entitysetupusage.helper;

import static io.harness.EntityType.CONNECTORS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.gitsync.interceptor.GitSyncConstants.DEFAULT;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EntityReference;
import io.harness.context.GlobalContextData;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.exception.UnexpectedException;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitysetupusage.entity.EntitySetupUsage;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.DX)
@AllArgsConstructor(access = AccessLevel.PUBLIC, onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class SetupUsageGitInfoPopulator {
  GitInfoPopulatorForConnector gitInfoPopulatorForConnector;
  YamlGitConfigService yamlGitConfigService;

  /*
   * This function assumes that all the setup usages are for the same referredBy entity.
   */
  public void populateRepoAndBranchForSetupUsageEntities(List<EntitySetupUsage> setupUsages) {
    if (isEmpty(setupUsages)) {
      return;
    }
    GitEntityInfo gitEntityInfo = getGitEntityInfoFromContext();
    if (gitEntityInfo == null || doesGitInfoContainsDefaultValue(gitEntityInfo)) {
      return;
    }
    String repoIdentifier = gitEntityInfo.getYamlGitConfigId();
    String branch = gitEntityInfo.getBranch();
    if (isEmpty(repoIdentifier) || isEmpty(branch)) {
      return;
    }
    EntityDetail referredByEntity = setupUsages.stream().map(EntitySetupUsage::getReferredByEntity).findAny().get();
    final EntityReference referredByEntityRef = referredByEntity.getEntityRef();
    Boolean isDefault = false;
    try {
      isDefault = checkWhetherIsDefaultBranch(referredByEntityRef.getAccountIdentifier(),
          referredByEntityRef.getOrgIdentifier(), referredByEntityRef.getProjectIdentifier(), repoIdentifier, branch);
    } catch (Exception exception) {
      // If any git-sync config doesn't exist / exception occurs from upstream, ignore it and return
      return;
    }
    List<EntityDetail> referredEntities =
        setupUsages.stream().map(EntitySetupUsage::getReferredEntity).filter(Objects::nonNull).collect(toList());
    populateRepoBranchInReferredByEntity(referredByEntity, repoIdentifier, branch, isDefault);
    populateRepoBranchInReferredEntities(referredEntities, repoIdentifier, branch, isDefault);
  }

  private boolean doesGitInfoContainsDefaultValue(GitEntityInfo gitBranchInfo) {
    boolean isRepoNull =
        isEmpty(gitBranchInfo.getYamlGitConfigId()) || gitBranchInfo.getYamlGitConfigId().equals(DEFAULT);
    boolean isBranchNull = isEmpty(gitBranchInfo.getBranch()) || gitBranchInfo.getBranch().equals(DEFAULT);
    return isRepoNull || isBranchNull;
  }

  private Boolean checkWhetherIsDefaultBranch(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String repoIdentifier, String branch) {
    YamlGitConfigDTO yamlGitConfigDTO =
        yamlGitConfigService.get(projectIdentifier, orgIdentifier, accountIdentifier, repoIdentifier);
    if (yamlGitConfigDTO == null) {
      throw new UnexpectedException(
          String.format("No git sync config exists with the id %s, in account %s, org %s, project %s", repoIdentifier,
              accountIdentifier, orgIdentifier, projectIdentifier));
    }
    return yamlGitConfigDTO.getBranch().equals(branch);
  }

  private void populateRepoBranchInReferredEntities(
      List<EntityDetail> referredEntities, String repo, String branch, Boolean isReferredByBranchDefault) {
    // Currently in ng-core, we only support branching for the connectors, thus handling connector
    if (isEmpty(referredEntities)) {
      return;
    }
    final List<EntityDetail> connectorEntities =
        referredEntities.stream().filter(entity -> entity.getType() == CONNECTORS).collect(toList());
    gitInfoPopulatorForConnector.populateRepoAndBranchForConnector(
        connectorEntities, branch, repo, isReferredByBranchDefault);
  }

  private void populateRepoBranchInReferredByEntity(
      EntityDetail referredByEntity, String repo, String branch, Boolean isDefault) {
    if (referredByEntity == null) {
      return;
    }
    EntityReference entityRef = referredByEntity.getEntityRef();
    entityRef.setBranch(branch);
    entityRef.setRepoIdentifier(repo);
    entityRef.setIsDefault(isDefault);
  }

  private GitEntityInfo getGitEntityInfoFromContext() {
    // todo @deepak: There won't be repo and branch in this thread, take it from the events framework
    GlobalContextData globalContextData = GlobalContextManager.get(GitSyncBranchContext.NG_GIT_SYNC_CONTEXT);
    if (globalContextData != null) {
      return ((GitSyncBranchContext) Objects.requireNonNull(globalContextData)).getGitBranchInfo();
    }
    return null;
  }
}
