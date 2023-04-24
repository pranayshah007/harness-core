/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitx;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
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
import lombok.extern.slf4j.Slf4j;

/***
 * This Class fetches settings for GitExperience.
 */
@Slf4j
@OwnedBy(PIPELINE)
@Singleton
public class GitXSettingsHelper {
  @Inject private NGSettingsClient ngSettingsClient;

  public static final String COULD_NOT_FETCH_SETTING = "Could not fetch setting: %s";

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
      String defaultConnectorForGitX =
          getDefaultConnectorForGitX(accountIdentifier, orgIdentifier, projIdentifier, gitEntityInfo.getConnectorRef());

      if (!isEmpty(defaultConnectorForGitX)) {
        gitEntityInfo.setConnectorRef(defaultConnectorForGitX);
        GitAwareContextHelper.updateGitEntityContext(gitEntityInfo);
      }
    }
  }

  public void setDefaultStoreTypeForEntities(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    if (gitEntityInfo.getStoreType() == null) {
      StoreType defaultStoreTypeForEntities;

      if (isGitExperienceEnforcedInSettings(accountIdentifier, orgIdentifier, projectIdentifier)) {
        defaultStoreTypeForEntities = StoreType.REMOTE;
      } else {
        defaultStoreTypeForEntities = getDefaultStoreTypeForEntities(
            accountIdentifier, orgIdentifier, projectIdentifier, gitEntityInfo.getStoreType());
      }
      gitEntityInfo.setStoreType(defaultStoreTypeForEntities);
      GitAwareContextHelper.updateGitEntityContext(gitEntityInfo);
    }
  }

  private StoreType getDefaultStoreTypeForEntities(
      String accountIdentifier, String orgIdentifier, String projIdentifier, StoreType storeType) {
    String defaultStoreTypeForEntities;

    // Exceptions are handled for release for backward compatibility for 1 release.
    try {
      defaultStoreTypeForEntities =
          NGRestUtils
              .getResponse(ngSettingsClient.getSetting(
                  GitSyncConstants.DEFAULT_STORE_TYPE_FOR_ENTITIES, accountIdentifier, orgIdentifier, projIdentifier))
              .getValue();
      return StoreType.valueOf(defaultStoreTypeForEntities);
    } catch (Exception ex) {
      log.warn(String.format(COULD_NOT_FETCH_SETTING, GitSyncConstants.DEFAULT_STORE_TYPE_FOR_ENTITIES), ex);
      return storeType;
    }
  }

  private String getDefaultConnectorForGitX(
      String accountIdentifier, String orgIdentifier, String projIdentifier, String connectorRef) {
    String defaultConnectorForGitExperience;

    // Exceptions are handled for release for backward compatibility for 1 release.
    try {
      defaultConnectorForGitExperience =
          NGRestUtils
              .getResponse(ngSettingsClient.getSetting(GitSyncConstants.DEFAULT_CONNECTOR_FOR_GIT_EXPERIENCE,
                  accountIdentifier, orgIdentifier, projIdentifier))
              .getValue();
      return defaultConnectorForGitExperience;
    } catch (Exception ex) {
      log.warn(String.format(COULD_NOT_FETCH_SETTING, GitSyncConstants.DEFAULT_CONNECTOR_FOR_GIT_EXPERIENCE), ex);
      return connectorRef;
    }
  }

  @VisibleForTesting
  boolean isGitExperienceEnforcedInSettings(String accountIdentifier, String orgIdentifier, String projIdentifier) {
    String isGitExperienceEnforced;

    // Exceptions are handled for release for backward compatibility for 1 release.
    try {
      isGitExperienceEnforced = NGRestUtils
                                    .getResponse(ngSettingsClient.getSetting(GitSyncConstants.ENFORCE_GIT_EXPERIENCE,
                                        accountIdentifier, orgIdentifier, projIdentifier))
                                    .getValue();
    } catch (Exception ex) {
      log.warn(String.format(COULD_NOT_FETCH_SETTING, GitSyncConstants.ENFORCE_GIT_EXPERIENCE), ex);
      return false;
    }
    return GitSyncConstants.TRUE_VALUE.equals(isGitExperienceEnforced);
  }
}
