/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitx;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncConstants;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.remote.client.NGRestUtils;

import com.google.inject.Inject;

@OwnedBy(PIPELINE)
public class GitXSettingsHandler {
  @Inject private NGSettingsClient ngSettingsClient;

  public void enforceGitExperienceIfApplicable(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (isGitExperienceEnforcedInSettings(accountIdentifier, orgIdentifier, projectIdentifier)) {
      GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();

      if (gitEntityInfo == null || StoreType.INLINE.equals(gitEntityInfo.getStoreType())) {
        throw new InvalidRequestException(String.format(
            "Git Experience is enforced for the current scope with accountId: %s, orgIdentifier: %s and projIdentifier: %s. Hence Inline Entities cannot be created.",
            accountIdentifier, orgIdentifier, projectIdentifier));
      }
    }
  }

  public String getDefaultConnectorForGitX(String accountIdentifier, String orgIdentifier, String projIdentifier) {
    return NGRestUtils
        .getResponse(ngSettingsClient.getSetting(
            GitSyncConstants.DEFAULT_CONNECTOR_FOR_GIT_EXPERIENCE, accountIdentifier, orgIdentifier, projIdentifier))
        .getValue();
  }

  private boolean isGitExperienceEnforcedInSettings(
      String accountIdentifier, String orgIdentifier, String projIdentifier) {
    String isGitExperienceEnforced =
        NGRestUtils
            .getResponse(ngSettingsClient.getSetting(
                GitSyncConstants.ENFORCE_GIT_EXPERIENCE, accountIdentifier, orgIdentifier, projIdentifier))
            .getValue();
    return GitSyncConstants.TRUE_VALUE.equals(isGitExperienceEnforced);
  }
}
