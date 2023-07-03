/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitx;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncConstants;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.remote.client.NGRestUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/***
 * This Class fetches settings for GitExperience.
 */
@Slf4j
@OwnedBy(PIPELINE)
@Singleton
public class GitXSettingsHelper {
  @Inject private NGSettingsClient ngSettingsClient;

  public void enforceGitExperienceIfApplicable(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();

    if (gitEntityInfo != null && StoreType.REMOTE.equals(gitEntityInfo.getStoreType())) {
      return;
    }

    if (isGitExperienceEnforcedInSettings(accountIdentifier, orgIdentifier, projectIdentifier)
        && (gitEntityInfo == null || StoreType.INLINE.equals(gitEntityInfo.getStoreType()))) {
      throw new InvalidRequestException(String.format(
          "Git Experience is enforced for the current scope with accountId: [%s], orgIdentifier: [%s] and projIdentifier: [%s]. Hence Interaction with INLINE entities is forbidden.",
          accountIdentifier, orgIdentifier, projectIdentifier));
    }
  }

  public void setConnectorRefForRemoteEntity(String accountIdentifier, String orgIdentifier, String projIdentifier) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    if (GitAwareContextHelper.isRemoteEntity(gitEntityInfo)
        && GitAwareContextHelper.isNullOrDefault(gitEntityInfo.getConnectorRef())) {
      String defaultConnectorForGitX = getDefaultConnectorForGitX(accountIdentifier, orgIdentifier, projIdentifier);

      if (!isEmpty(defaultConnectorForGitX)) {
        gitEntityInfo.setConnectorRef(defaultConnectorForGitX);
        GitAwareContextHelper.updateGitEntityContext(gitEntityInfo);
      }
    }
  }

  public void setDefaultStoreTypeForEntities(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, EntityType entityType) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    if (gitEntityInfo.getStoreType() == null) {
      StoreType defaultStoreTypeForEntities;

      if (EntityType.INPUT_SETS.equals(entityType)) {
        defaultStoreTypeForEntities =
            getDefaultStoreTypeForEntities(accountIdentifier, orgIdentifier, projectIdentifier);
      } else {
        if (isGitExperienceEnforcedInSettings(accountIdentifier, orgIdentifier, projectIdentifier)) {
          defaultStoreTypeForEntities = StoreType.REMOTE;
        } else {
          defaultStoreTypeForEntities =
              getDefaultStoreTypeForEntities(accountIdentifier, orgIdentifier, projectIdentifier);
        }
      }

      gitEntityInfo.setStoreType(defaultStoreTypeForEntities);
      GitAwareContextHelper.updateGitEntityContext(gitEntityInfo);
    }
  }

  @VisibleForTesting
  StoreType getDefaultStoreTypeForEntities(String accountIdentifier, String orgIdentifier, String projIdentifier) {
    String defaultStoreTypeForEntities =
        NGRestUtils
            .getResponse(ngSettingsClient.getSetting(
                GitSyncConstants.DEFAULT_STORE_TYPE_FOR_ENTITIES, accountIdentifier, orgIdentifier, projIdentifier))
            .getValue();
    return StoreType.valueOf(defaultStoreTypeForEntities);
  }

  private String getDefaultConnectorForGitX(String accountIdentifier, String orgIdentifier, String projIdentifier) {
    return NGRestUtils
        .getResponse(ngSettingsClient.getSetting(
            GitSyncConstants.DEFAULT_CONNECTOR_FOR_GIT_EXPERIENCE, accountIdentifier, orgIdentifier, projIdentifier))
        .getValue();
  }

  @VisibleForTesting
  boolean isGitExperienceEnforcedInSettings(String accountIdentifier, String orgIdentifier, String projIdentifier) {
    String isGitExperienceEnforced =
        NGRestUtils
            .getResponse(ngSettingsClient.getSetting(
                GitSyncConstants.ENFORCE_GIT_EXPERIENCE, accountIdentifier, orgIdentifier, projIdentifier))
            .getValue();

    return GitSyncConstants.TRUE_VALUE.equals(isGitExperienceEnforced);
  }

  public List<String> getGitRepoAllowlist(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    String repoAllowlist =
        NGRestUtils
            .getResponse(ngSettingsClient.getSetting(GitSyncConstants.REPO_ALLOWLIST_FOR_GIT_EXPERIENCE,
                accountIdentifier, orgIdentifier, projectIdentifier))
            .getValue();

    // Empty list indicates that setting is not being used.
    if (EmptyPredicate.isEmpty(repoAllowlist)) {
      return Collections.EMPTY_LIST;
    }

    List<String> listOfRepos = List.of(repoAllowlist.split(","));
    return HarnessStringUtils.removeLeadingAndTrailingSpacesInListOfStrings(listOfRepos);
  }
}
