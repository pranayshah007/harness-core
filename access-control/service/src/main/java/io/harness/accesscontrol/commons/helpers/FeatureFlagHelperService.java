/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.commons.helpers;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.remote.client.RestClientUtils;
import io.harness.utils.RetryUtils;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class FeatureFlagHelperService {
  @Inject AccountClient accountClient;
  private static final RetryPolicy<Object> fetchRetryPolicy =
      RetryUtils.getRetryPolicy("Unexpected error, could not fetch the feature flag",
          "Unexpected error, could not fetch the could not fetch the feature flag",
          Lists.newArrayList(InvalidRequestException.class), Duration.ofSeconds(5), 3, log);

  public boolean isEnabled(FeatureName featureName, String accountId) {
    try {
      return Failsafe.with(fetchRetryPolicy)
          .get(() -> RestClientUtils.getResponse(accountClient.isFeatureFlagEnabled(featureName.name(), accountId)));
    } catch (InvalidRequestException e) {
      throw new UnexpectedException("Unexpected error, could not fetch the feature flag");
    }
  }
}
