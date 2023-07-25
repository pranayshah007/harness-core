/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.encoding.EncodingUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.functors.ExpressionFunctor;

import java.util.concurrent.Future;

@OwnedBy(HarnessTeam.CDC)
public class ImageSecretFunctor implements ExpressionFunctor {
  public static final String FUNCTOR_NAME = "imageSecret";
  private static final String REGISTRY_CREDENTIAL_TEMPLATE = "{\"%s\":{\"username\":\"%s\",\"password\":\"%s\"}}";

  public String create(String registryUrl, String userName, String password) {
    if (ExpressionEvaluator.matchesVariablePattern(registryUrl) || ExpressionEvaluator.matchesVariablePattern(userName)
        || ExpressionEvaluator.matchesVariablePattern(password)) {
      throw new InvalidRequestException("Arguments cannot be expression");
    }
    return EncodingUtils.encodeBase64(format(
        REGISTRY_CREDENTIAL_TEMPLATE, registryUrl, userName, password.replaceAll("\n", "").replaceAll("\"", "\\\\\"")));
  }

  // This variation will be used in case of nested secrets, which has not been evaluated yet.
  public String create(String registryUrl, String userName, Future password) {
    String evaluatedPassword;
    try {
      evaluatedPassword = String.valueOf(password.get());
    } catch (Exception e) {
      throw new InvalidRequestException("Unable to interpret Future ", e);
    }
    return create(registryUrl, userName, evaluatedPassword);
  }

  // This variation will be used in case of nested secrets, which has not been evaluated yet.
  public String create(String registryUrl, Future userName, Future password) {
    String evaluatedPassword;
    String evaluatedUsername;
    try {
      evaluatedPassword = String.valueOf(password.get());
      evaluatedUsername = String.valueOf(userName.get());
    } catch (Exception e) {
      throw new InvalidRequestException("Unable to interpret Future ", e);
    }
    return create(registryUrl, evaluatedUsername, evaluatedPassword);
  }
}
