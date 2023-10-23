/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadata.PlanExecutionMetadataKeys;
import io.harness.expression.LateBindingValue;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.YamlUtils;

import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.HashMap;

@OwnedBy(PIPELINE)
public class InputSetFunctor implements LateBindingValue {
  private final PlanExecutionMetadataService planExecutionMetadataService;

  private final Ambiance ambiance;

  public InputSetFunctor(PlanExecutionMetadataService planExecutionMetadataService, Ambiance ambiance) {
    this.planExecutionMetadataService = planExecutionMetadataService;
    this.ambiance = ambiance;
  }
  @Override
  public Object bind() {
    try {
      PlanExecutionMetadata planExecutionMetadata = planExecutionMetadataService.getWithFieldsIncludedFromSecondary(
          ambiance.getPlanExecutionId(), Sets.newHashSet(PlanExecutionMetadataKeys.inputSetYaml));

      return YamlUtils.read(planExecutionMetadata.getInputSetYaml(), HashMap.class);
    } catch (IOException e) {
      throw new InvalidRequestException("Input Set Yaml could not be converted to a hashmap");
    }
  }
}
