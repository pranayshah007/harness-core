/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.utils;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.remote.client.CGRestUtils;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class NGTemplateFeatureFlagHelperService {
  private static final String ERROR_MESSAGE = "Unexpected error, could not fetch the feature flag";

  @Inject AccountClient accountClient;

  public boolean isFeatureFlagEnabled(String accountId, FeatureName featureName) {
    try {
      return CGRestUtils.getResponse(accountClient.isFeatureFlagEnabled(featureName.name(), accountId), ERROR_MESSAGE);
    } catch (InvalidRequestException e) {
      throw new UnexpectedException(ERROR_MESSAGE);
    }
  }
}
