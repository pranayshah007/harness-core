/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.facilitation;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.ExecutionCheck;
import io.harness.plan.Node;
import io.harness.pms.contracts.ambiance.Ambiance;

@OwnedBy(HarnessTeam.PIPELINE)
public abstract class AbstractPreFacilitationChecker {
  protected AbstractPreFacilitationChecker nextChecker;

  public void setNextChecker(AbstractPreFacilitationChecker nextChecker) {
    this.nextChecker = nextChecker;
  }

  public ExecutionCheck check(Ambiance ambiance, Node node) {
    ExecutionCheck preCheck = this.performCheck(ambiance, node);
    if (!preCheck.isProceed()) {
      return preCheck;
    }
    if (nextChecker != null) {
      return nextChecker.check(ambiance, node);
    }
    return ExecutionCheck.builder().proceed(true).reason(null).build();
  }

  protected abstract ExecutionCheck performCheck(Ambiance ambiance, Node planNode);
}
