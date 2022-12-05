/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.limits.checker;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.limits.Action;
import io.harness.limits.checkers.StaticLimitChecker;

/**
 * Limit checker which lets you decrement the count as well. Useful for handling delete operations.
 */
@OwnedBy(PL)
public interface StaticLimitCheckerWithDecrement extends StaticLimitChecker {
  boolean decrement();

  Action getAction();
}
