/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.utils;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;

import java.lang.reflect.Parameter;
import java.util.Optional;
import lombok.NoArgsConstructor;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

@NoArgsConstructor
@OwnedBy(HarnessTeam.CE)
public class AccountIdentifierLogInterceptor implements MethodInterceptor {
  Optional<String> getAccountId(MethodInvocation methodInvocation) {
    Parameter[] parameters = methodInvocation.getMethod().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      Parameter parameter = parameters[i];
      Object argument = methodInvocation.getArguments()[i];
      if (argument != null && parameter.isAnnotationPresent(AccountIdentifier.class)) {
        return Optional.of((String) argument);
      }
    }

    return Optional.empty();
  }

  @Override
  public Object invoke(MethodInvocation methodInvocation) throws Throwable {
    final Optional<String> accountId = getAccountId(methodInvocation);

    if (accountId.isPresent()) {
      try (AutoLogContext ignore1 = new AccountLogContext(accountId.get(), OVERRIDE_ERROR)) {
        return methodInvocation.proceed();
      }
    }

    return methodInvocation.proceed();
  }
}
