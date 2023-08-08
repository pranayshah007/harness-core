/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.matrix;

import io.harness.annotation.RecasterAlias;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.plancreator.strategy.StrategyType;
import io.harness.pms.yaml.ParameterField;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@RecasterAlias("io.harness.steps.matrix.StrategyStepParameters")
public class StrategyStepParameters extends StrategyAbstractStepParameters {
  StrategyConfig strategyConfig;
  String childNodeId;
  ParameterField<Integer> maxConcurrency;
  StrategyType strategyType;
  Boolean shouldProceedIfFailed;
}