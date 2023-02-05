package io.harness.service.impl;

import static java.util.Base64.getUrlDecoder;

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
    final String authHeader = new String(decoder.decode(tokenString.split("\\.")[0]));
    if (authHeader.contains("HS256")) {
      delegateTokenAuthenticator.validateDelegateAuth2Token(accountId, tokenString, agentMtlAuthority);
    } else {
      delegateTokenAuthenticator.validateDelegateToken(
          accountId, tokenString, delegateId, delegateTokenName, agentMtlAuthority, shouldSetTokenNameInGlobalContext);
    }
  }
}
