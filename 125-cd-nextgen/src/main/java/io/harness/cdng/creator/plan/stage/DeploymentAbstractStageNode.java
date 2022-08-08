/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage;

import io.harness.plancreator.stages.stage.AbstractStageNode;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;

import java.util.List;
import lombok.Data;

@Data
public abstract class DeploymentAbstractStageNode extends AbstractStageNode {
  @VariableExpression(skipVariableExpression = true) List<FailureStrategyConfig> failureStrategies;
}
