/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.utils;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.common.NGExpressionUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.timeout.Timeout;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@Slf4j
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class SdkTimeoutObtainmentUtils {
  public static final String MAX_TIMEOUT = "8w";
  public ParameterField<Timeout> getTimeout(
      ParameterField<Timeout> timeout, String maxTimeout, boolean notApplyMaxTimeout) {
    if (notApplyMaxTimeout) {
      if (ParameterField.isBlank(timeout)) {
        return null;
      }
      if (timeout.isExpression() && NGExpressionUtils.matchesInputSetPattern(timeout.getExpressionValue())) {
        return ParameterField.createValueField(Timeout.fromString(MAX_TIMEOUT));
      }
      if (timeout.isExpression()) {
        return ParameterField.createExpressionField(
            true, timeout.getExpressionValue(), timeout.getInputSetValidator(), true);
      }
      return timeout;
    }
    if (ParameterField.isBlank(timeout)) {
      return ParameterField.createValueField(Timeout.fromString(maxTimeout));
    }
    return getTimeoutParameterField(timeout, maxTimeout);
  }

  private ParameterField<Timeout> getTimeoutParameterField(ParameterField<Timeout> givenTimeout, String maxTimeout) {
    if (givenTimeout.isExpression()) {
      return NGExpressionUtils.matchesInputSetPattern(givenTimeout.getExpressionValue())
          ? ParameterField.createValueField(Timeout.fromString(maxTimeout))
          : givenTimeout;
    }
    return getMinTimeoutValue(givenTimeout, maxTimeout);
  }
  private ParameterField<Timeout> getMinTimeoutValue(ParameterField<Timeout> givenTimeout, String maxTimeout) {
    Timeout maxTimeoutValue = Timeout.fromString(maxTimeout);
    if (ParameterField.isBlank(givenTimeout) && null != maxTimeoutValue
        && givenTimeout.getValue().getTimeoutInMillis() > maxTimeoutValue.getTimeoutInMillis()) {
      return ParameterField.createValueField(maxTimeoutValue);
    }
    return givenTimeout;
  }
}
