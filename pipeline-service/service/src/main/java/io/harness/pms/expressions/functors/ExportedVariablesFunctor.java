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
import io.harness.beans.FeatureName;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.steps.shellscript.OutputAliasUtils;
import io.harness.utils.PmsFeatureFlagHelper;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_COMMON_STEPS, HarnessModuleComponent.CDS_PIPELINE})
public class ExportedVariablesFunctor implements ExpressionFunctor {
  private final Ambiance ambiance;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private static final String DOT = "\\.";
  public ExportedVariablesFunctor(Ambiance ambiance, PmsFeatureFlagHelper pmsFeatureFlagHelper) {
    this.ambiance = ambiance;
    this.pmsFeatureFlagHelper = pmsFeatureFlagHelper;
  }

  /**
   * Maps the input expression to corresponding sweeping output expression
   */
  public Object getValue(String exportExpression) {
    if (StringUtils.isBlank(exportExpression)
        || !pmsFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_SHELL_VARIABLES_EXPORT)
        || !OutputAliasUtils.validateExpressionFormat(exportExpression)) {
      return null;
    }

    String[] exportExpressionSplit = exportExpression.split(DOT);

    String encodedAlias = OutputAliasUtils.generateSweepingOutputKeyUsingUserAlias(exportExpressionSplit[1], ambiance);
    String sweepingOutputFqn =
        String.format("%s.%s.%s", exportExpressionSplit[0], encodedAlias, YAMLFieldNameConstants.OUTPUT_VARIABLES);
    if (exportExpressionSplit.length == 3) {
      // if leaf expression
      sweepingOutputFqn = String.format("%s.%s", sweepingOutputFqn, exportExpressionSplit[2]);
    }
    return String.format("%s%s%s", EXPR_START, sweepingOutputFqn, EXPR_END);
  }
}
