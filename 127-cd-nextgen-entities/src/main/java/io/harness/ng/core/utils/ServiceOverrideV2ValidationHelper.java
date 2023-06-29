/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.utils;

import static java.lang.Boolean.parseBoolean;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.encryption.Scope;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.scope.ScopeHelper;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.CDC)
public class ServiceOverrideV2ValidationHelper {
  @Inject private NGSettingsClient settingsClient;
  @Inject private NGFeatureFlagHelperService featureFlagHelperService;

  public boolean isOverridesV2Enabled(String accountId, String orgId, String projectId) {
    boolean isOverrideV2SettingEnabled = false;
    Scope scope = ScopeHelper.getScope(accountId, orgId, projectId);
    if (Scope.PROJECT.equals(scope)) {
      isOverrideV2SettingEnabled =
          parseBoolean(NGRestUtils
                           .getResponse(settingsClient.getSetting(
                               SettingIdentifiers.SERVICE_OVERRIDE_V2_IDENTIFIER, accountId, orgId, projectId))
                           .getValue());

    } else if (Scope.ORG.equals(scope)) {
      isOverrideV2SettingEnabled = parseBoolean(
          NGRestUtils
              .getResponse(
                  settingsClient.getSetting(SettingIdentifiers.SERVICE_OVERRIDE_V2_IDENTIFIER, accountId, orgId, null))
              .getValue());
    } else {
      isOverrideV2SettingEnabled = parseBoolean(
          NGRestUtils
              .getResponse(
                  settingsClient.getSetting(SettingIdentifiers.SERVICE_OVERRIDE_V2_IDENTIFIER, accountId, null, null))
              .getValue());
    }

    boolean isServiceOverrideV2Enabled =
        featureFlagHelperService.isEnabled(accountId, FeatureName.CDS_SERVICE_OVERRIDES_2_0);

    return isServiceOverrideV2Enabled && isOverrideV2SettingEnabled;
  }
}