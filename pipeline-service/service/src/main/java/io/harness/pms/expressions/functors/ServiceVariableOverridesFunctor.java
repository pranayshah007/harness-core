/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;
import static io.harness.expression.common.ExpressionConstants.EXPR_END;
import static io.harness.expression.common.ExpressionConstants.EXPR_START;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.expression.LateBindingMap;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.ExpressionModeMapper;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT, HarnessModuleComponent.CDS_PIPELINE})
public class ServiceVariableOverridesFunctor extends LateBindingMap {
  private final Ambiance ambiance;
  private final PmsEngineExpressionService pmsEngineExpressionService;
  private static final String EXECUTION = "EXECUTION";

  public ServiceVariableOverridesFunctor(Ambiance ambiance, PmsEngineExpressionService pmsEngineExpressionService) {
    this.ambiance = ambiance;
    this.pmsEngineExpressionService = pmsEngineExpressionService;
  }

  @Override
  public synchronized Object get(Object key) {
    if (!(key instanceof String)) {
      return null;
    }

    String variableName = (String) key;

    List<Level> levels = ambiance.getLevelsList();
    List<Level> subLevels;
    List<String> fqnList = new ArrayList<>();

    Set<String> groups = levels.stream().map(Level::getGroup).collect(Collectors.toSet());
    // step group overrides are rendered within execution context only
    if (!groups.contains(EXECUTION)) {
      // functor will return null to return original expression with mode RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED
      return null;
    }

    // base fqn is serviceVariables.variable_name
    fqnList.add(
        String.format("%s%s.%s%s", EXPR_START, YAMLFieldNameConstants.SERVICE_VARIABLES, variableName, EXPR_END));

    int currentIndex = 0;
    for (Level level : levels) {
      if ("STEP_GROUP".equals(level.getStepType().getType())) {
        // create a sub list starting at pipeline and ending at the step group
        subLevels = levels.subList(0, currentIndex + 1);
        String fqn = AmbianceUtils.getFQNUsingLevels(subLevels);

        // append variables.variable_name
        fqn = String.format("%s.%s.%s", fqn, YAMLFieldNameConstants.VARIABLES, variableName);
        // Create expression for the engine
        fqn = String.format("%s%s%s", EXPR_START, fqn, EXPR_END);

        fqnList.add(fqn);
      }
      currentIndex++;
    }

    String finalValue = null;

    for (int i = fqnList.size() - 1; i >= 0; i--) {
      String fqnRendered = pmsEngineExpressionService.renderExpression(ambiance, fqnList.get(i),
          ExpressionModeMapper.fromExpressionModeProto(ExpressionMode.RETURN_NULL_IF_UNRESOLVED));

      // nearest non-null value needs to be picked up
      if (fqnRendered != null && !Objects.equals(fqnRendered, "null")) {
        finalValue = fqnRendered;
        break;
      }
    }

    return finalValue;
  }
}
