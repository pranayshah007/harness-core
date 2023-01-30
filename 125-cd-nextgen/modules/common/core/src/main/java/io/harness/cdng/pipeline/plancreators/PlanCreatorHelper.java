/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline.plancreators;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.beans.RollbackNode;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.expression.EngineExpressionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.annotation.Nonnull;

@Singleton
@OwnedBy(HarnessTeam.CDC)
public class PlanCreatorHelper {
  @Inject private EngineExpressionService engineExpressionService;

  public boolean shouldNodeRun(@Nonnull RollbackNode rollbackNode, @Nonnull Ambiance ambiance) {
    if (rollbackNode.isShouldAlwaysRun()) {
      return true;
    }

    String value = engineExpressionService.renderExpression(
        ambiance, format("<+%s.status>", rollbackNode.getDependentNodeIdentifier()), true);
    return !EngineExpressionEvaluator.hasExpressions(value);
  }
}
