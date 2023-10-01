/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.expressions;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.expression.EngineExpressionServiceResolver;
import io.harness.pms.expression.ExpressionModeMapper;
import io.harness.pms.expression.ParameterFieldResolverFunctor;
import io.harness.pms.yaml.validation.InputSetValidatorFactory;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class CDExpressionResolver {
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private InputSetValidatorFactory inputSetValidatorFactory;
  @Inject private NGFeatureFlagHelperService ngFeatureFlagHelperService;

  public void updateStoreConfigExpressions(Ambiance ambiance, StoreConfigWrapper storeConfigWrapper) {
    StoreConfig storeConfig = storeConfigWrapper.getSpec();
    storeConfig = (StoreConfig) updateExpressions(ambiance, storeConfig);
    storeConfigWrapper.setSpec(storeConfig);
  }

  public Object updateExpressions(Ambiance ambiance, Object obj) {
    if (obj == null) {
      return obj;
    }
    if (ngFeatureFlagHelperService.isEnabled(
            AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_ENABLE_NEW_PARAMETER_FIELD_PROCESSOR)) {
      return ExpressionEvaluatorUtils.updateExpressions(obj,
          new ParameterFieldResolverFunctor(new EngineExpressionServiceResolver(engineExpressionService, ambiance),
              inputSetValidatorFactory, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED));
    } else {
      return ExpressionEvaluatorUtils.updateExpressions(
          obj, new CDExpressionResolverFunctor(engineExpressionService, ambiance));
    }
  }

  public Object updateExpressions(Ambiance ambiance, Object obj, ExpressionMode expressionMode) {
    if (obj == null) {
      return obj;
    }
    if (ngFeatureFlagHelperService.isEnabled(
            AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_ENABLE_NEW_PARAMETER_FIELD_PROCESSOR)) {
      return ExpressionEvaluatorUtils.updateExpressions(obj,
          new ParameterFieldResolverFunctor(new EngineExpressionServiceResolver(engineExpressionService, ambiance),
              inputSetValidatorFactory, expressionMode));
    } else {
      return ExpressionEvaluatorUtils.updateExpressions(obj,
          new CDExpressionResolverFunctor(
              engineExpressionService, ambiance, ExpressionModeMapper.toExpressionModeProto(expressionMode)));
    }
  }

  public <T> T evaluateExpression(Ambiance ambiance, String expression, Class<T> type) {
    Object result = engineExpressionService.evaluateExpression(ambiance, expression);
    return type.cast(result);
  }

  public String renderExpression(Ambiance ambiance, String expression) {
    return engineExpressionService.renderExpression(ambiance, expression);
  }

  public String renderExpression(Ambiance ambiance, String expression, boolean skipUnresolvedExpressionCheck) {
    return engineExpressionService.renderExpression(ambiance, expression, skipUnresolvedExpressionCheck);
  }
}
