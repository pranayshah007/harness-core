/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.limits;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.limits.checkers.LimitChecker;
import io.harness.limits.configuration.NoLimitConfiguredException;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Gets the LimitChecker instance for a given action.
 */
@OwnedBy(PL)
@ParametersAreNonnullByDefault
@TargetModule(HarnessModule._955_ACCOUNT_MGMT)
public interface LimitCheckerFactory {
  /**
   *
   * @param action - action for which to check limit
   * @return limitChecker instance for given action
   * @throws NoLimitConfiguredException in case no limit is configured for the exception.
   *
   */
  @Nonnull LimitChecker getInstance(Action action);
}
