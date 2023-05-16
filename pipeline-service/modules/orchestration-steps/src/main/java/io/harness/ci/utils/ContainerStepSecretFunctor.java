/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.FunctorException;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.ng.core.NGAccess;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.variables.SecretNGVariable;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class ContainerStepSecretFunctor implements ExpressionFunctor {
  private long expressionFunctorToken;
  private SecretUtils secretUtils;
  private NGAccess ngAccess;

  @Builder.Default private List<SecretVariableDetails> secretVariableDetails = new ArrayList<>();

  public List<SecretVariableDetails> getSecretVariableDetails() {
    return secretVariableDetails;
  }

  public Object obtain(String secretIdentifier, int token) {
    try {
      SecretRefData secretRefData = SecretRefHelper.createSecretRef(secretIdentifier);
      SecretNGVariable secretNGVariable = SecretNGVariable.builder()
                                              .name(secretRefData.getIdentifier())
                                              .value(ParameterField.createValueField(secretRefData))
                                              .build();

      secretVariableDetails.add(secretUtils.getSecretVariableDetailsWithScope(ngAccess, secretNGVariable));
      return "${ngSecretManager.obtain(\"" + secretIdentifier + "\", " + expressionFunctorToken + ")}";
    } catch (Exception ex) {
      throw new FunctorException("Error occurred while evaluating the secret [" + secretIdentifier + "]", ex);
    }
  }
}
