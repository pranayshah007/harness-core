/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.common.NGExpressionUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.timeout.Timeout;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@UtilityClass
@OwnedBy(PIPELINE)
public class TimeoutUtils {
  public final String DEFAULT_TIMEOUT = "10h";
  public final String DEFAULT_STAGE_TIMEOUT = "15w";

  public long getTimeoutInSeconds(Timeout timeout, long defaultTimeoutInSeconds) {
    if (timeout == null) {
      return defaultTimeoutInSeconds;
    }
    long timeoutLong = TimeUnit.MILLISECONDS.toSeconds(timeout.getTimeoutInMillis());
    return timeoutLong > 0 ? timeoutLong : defaultTimeoutInSeconds;
  }

  public long getTimeoutInSeconds(ParameterField<Timeout> timeout, long defaultTimeoutInSeconds) {
    if (timeout == null) {
      return defaultTimeoutInSeconds;
    }
    return getTimeoutInSeconds(timeout.getValue(), defaultTimeoutInSeconds);
  }

  public String getTimeoutString(ParameterField<Timeout> timeout) {
    String timeoutString = DEFAULT_TIMEOUT;
    if (!ParameterField.isNull(timeout)) {
      if (timeout.isExpression()) {
        timeoutString = timeout.getExpressionValue();
      } else if (timeout.getValue() != null) {
        timeoutString = timeout.getValue().getTimeoutString();
      }
    }
    return timeoutString;
  }

  public ParameterField<Timeout> getTimeout(ParameterField<Timeout> timeout) {
    if (ParameterField.isNull(timeout)) {
      return ParameterField.createValueField(Timeout.fromString(DEFAULT_TIMEOUT));
    }
    // If the timeout field is runtime input then use default value
    if (timeout.isExpression() && NGExpressionUtils.matchesInputSetPattern(timeout.getExpressionValue())) {
      return ParameterField.createValueField(Timeout.fromString(DEFAULT_TIMEOUT));
    }
    return timeout;
  }

  public ParameterField<Timeout> getStageTimeout(ParameterField<Timeout> timeout) {
    if (ParameterField.isNull(timeout)) {
      return ParameterField.createValueField(Timeout.fromString(DEFAULT_STAGE_TIMEOUT));
    }
    // If the timeout field is runtime input then use default value
    if (timeout.isExpression() && NGExpressionUtils.matchesInputSetPattern(timeout.getExpressionValue())) {
      return ParameterField.createValueField(Timeout.fromString(DEFAULT_STAGE_TIMEOUT));
    }
    return timeout;
  }

  public ParameterField<Timeout> getTimeoutWithDefaultValue(ParameterField<Timeout> timeout, String defaultValue) {
    if (ParameterField.isNull(timeout)) {
      return ParameterField.createValueField(Timeout.fromString(defaultValue));
    }
    // If the timeout field is runtime input then use default value
    if (timeout.isExpression() && NGExpressionUtils.matchesInputSetPattern(timeout.getExpressionValue())) {
      return ParameterField.createValueField(Timeout.fromString(defaultValue));
    }
    return timeout;
  }

  public ParameterField<String> getTimeoutParameterFieldString(ParameterField<Timeout> timeoutParameterField) {
    ParameterField<Timeout> timeout = getTimeout(timeoutParameterField);
    if (timeout.isExpression()) {
      return ParameterField.createExpressionField(
          true, timeout.getExpressionValue(), timeout.getInputSetValidator(), true);
    } else {
      return ParameterField.createValueField(timeout.getValue().getTimeoutString());
    }
  }

  public ParameterField<String> getTimeoutParameterFieldStringForStage(ParameterField<Timeout> timeoutParameterField) {
    ParameterField<Timeout> timeout = getStageTimeout(timeoutParameterField);
    if (timeout.isExpression()) {
      return ParameterField.createExpressionField(
          true, timeout.getExpressionValue(), timeout.getInputSetValidator(), true);
    } else {
      return ParameterField.createValueField(timeout.getValue().getTimeoutString());
    }
  }
  public ParameterField<String> getTimeoutParameterFieldStringWithDefaultValue(
      ParameterField<Timeout> timeoutParameterField, String defaultValue) {
    ParameterField<Timeout> timeout = getTimeoutWithDefaultValue(timeoutParameterField, defaultValue);
    if (timeout.isExpression()) {
      return ParameterField.createExpressionField(
          true, timeout.getExpressionValue(), timeout.getInputSetValidator(), true);
    } else {
      return ParameterField.createValueField(timeout.getValue().getTimeoutString());
    }
  }
}
