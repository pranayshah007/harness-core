/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.impl;

import static java.util.Base64.getUrlDecoder;
import static org.apache.commons.lang3.StringUtils.substringBefore;

import io.harness.security.DelegateTokenAuthenticator;
import io.harness.service.intfc.DelegateAuthService;

import com.google.inject.Inject;
import java.util.Base64;

public class DelegateAuthServiceImpl implements DelegateAuthService {
  @Inject private DelegateTokenAuthenticator delegateTokenAuthenticator;
  @Override
  public void validateDelegateToken(String accountId, String tokenString, String delegateId, String delegateTokenName,
      String agentMtlAuthority, boolean shouldSetTokenNameInGlobalContext) {
    Base64.Decoder decoder = getUrlDecoder();
    final String authHeader = substringBefore(tokenString, ".").trim();
    if (authHeader.contains("HS256")) {
      delegateTokenAuthenticator.validateDelegateAuth2Token(accountId, tokenString, agentMtlAuthority);
    } else {
      delegateTokenAuthenticator.validateDelegateToken(
          accountId, tokenString, delegateId, delegateTokenName, agentMtlAuthority, shouldSetTokenNameInGlobalContext);
    }
  }
}
