/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.enforcement.client.annotation.interceptor;

import static io.harness.exception.WingsException.USER_SRE;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.enforcement.client.annotation.FeatureRestrictionCheck;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.exception.InvalidArgumentsException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.lang.reflect.Parameter;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

@Slf4j
@Singleton
public class FeatureRestrictionCheckInterceptor implements MethodInterceptor {
  @Inject EnforcementClientService enforcementClientService;

  @Override
  public Object invoke(MethodInvocation methodInvocation) throws Throwable {
    FeatureRestrictionCheck featureCheck =
        methodInvocation.getMethod().getDeclaredAnnotation(FeatureRestrictionCheck.class);

    Optional<String> accountIdentifierOptional = getAccountIdentifier(methodInvocation);
    if (!accountIdentifierOptional.isPresent()) {
      throw new InvalidArgumentsException("Account id is not marked in the request", USER_SRE);
    }

    String accountIdentifier = accountIdentifierOptional.get();
    FeatureRestrictionName featureRestrictionName = featureCheck.value();

    enforcementClientService.checkAvailability(featureRestrictionName, accountIdentifier);
    return methodInvocation.proceed();
  }

  private Optional<String> getAccountIdentifier(MethodInvocation methodInvocation) {
    Parameter[] parameters = methodInvocation.getMethod().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      Parameter parameter = parameters[i];
      if (parameter.isAnnotationPresent(AccountIdentifier.class)) {
        return Optional.of(String.valueOf(methodInvocation.getArguments()[i]));
      }
    }
    return Optional.empty();
  }
}
