/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static org.apache.commons.lang3.StringUtils.substringBefore;

import io.harness.delegate.beans.Delegate;
import io.harness.security.DelegateTokenAuthenticator;
import io.harness.service.intfc.DelegateAuthService;
import io.harness.service.intfc.DelegateCache;

import com.google.inject.Inject;

public class DelegateAuthServiceImpl implements DelegateAuthService {
  @Inject private DelegateTokenAuthenticator delegateTokenAuthenticator;
  @Inject private DelegateCache delegateCache;
  private static final String UNREGISTERED = "Unregistered";

  @Override
  public void validateDelegateToken(String accountId, String tokenString, String delegateId, String delegateTokenName,
      String agentMtlAuthority, boolean shouldSetTokenNameInGlobalContext) {
    final String authHeader = substringBefore(tokenString, ".").trim();
    boolean isNg = isRequestFromNGDelegate(delegateId, accountId);
    if (authHeader.contains("HS256")) {
      delegateTokenAuthenticator.validateDelegateAuth2Token(accountId, tokenString, agentMtlAuthority, isNg);
    } else {
      delegateTokenAuthenticator.validateDelegateToken(accountId, tokenString, delegateId, delegateTokenName,
          agentMtlAuthority, shouldSetTokenNameInGlobalContext, isNg);
    }
  }

  private boolean isRequestFromNGDelegate(String delegateId, String accountId) {
    if (isEmpty(delegateId) || delegateId.equals(UNREGISTERED)) {
      return false;
    }
    Delegate delegate = delegateCache.get(accountId, delegateId, false);
    return delegate != null && delegate.isNg();
  }
}
