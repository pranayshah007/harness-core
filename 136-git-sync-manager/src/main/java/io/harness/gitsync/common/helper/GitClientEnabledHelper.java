/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import io.harness.gitsync.interceptor.GitSyncConstants;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.remote.client.NGRestUtils;

import com.google.inject.Inject;

public class GitClientEnabledHelper {
  @Inject private NGSettingsClient ngSettingsClient;
  public boolean isGitClientEnabledInSettings(String accountId) {
    String isGitClientEnabledString = NGRestUtils
                                          .getResponse(ngSettingsClient.getSetting(
                                              GitSyncConstants.GIT_CLIENT_ENABLED_SETTING, accountId, null, null))
                                          .getValue();
    return GitSyncConstants.TRUE_VALUE.equals(isGitClientEnabledString);
  }
}