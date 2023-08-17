/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.v1.AbstractStepNodeV1;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.timeout.Timeout;

import java.util.List;
import lombok.Data;

@Data
@OwnedBy(PIPELINE)
public abstract class PmsAbstractStepNodeV1 extends AbstractStepNodeV1 {
  ParameterField<List<FailureStrategyConfig>> failureStrategies;
  ParameterField<Timeout> timeout;
}
