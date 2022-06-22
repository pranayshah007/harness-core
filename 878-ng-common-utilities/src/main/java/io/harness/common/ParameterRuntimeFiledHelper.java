/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.common;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.Scope;
import io.harness.pms.yaml.ParameterField;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class ParameterRuntimeFiledHelper {
  public Optional<Scope> getScopeParameterFieldFinalValue(ParameterField<Scope> scopeField) {
    if (scopeField == null) {
      return Optional.empty();
    }

    try {
      return Optional.of(Scope.fromString(scopeField.fetchFinalValue().toString()));
    } catch (Exception ex) {
      log.error("Failed to create Scope from parameter field", ex);
      return Optional.empty();
    }
  }

  public boolean hasScopeValue(ParameterField<Scope> parameterField, boolean allowExpression) {
    if (ParameterField.isNull(parameterField)) {
      return false;
    }

    if (allowExpression && parameterField.isExpression()) {
      return true;
    }

    return ParameterFieldHelper.getParameterFieldValue(parameterField) != null;
  }
}
