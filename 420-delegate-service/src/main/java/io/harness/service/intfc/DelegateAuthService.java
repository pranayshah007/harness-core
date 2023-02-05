package io.harness.service.intfc;

public interface DelegateAuthService {
  void validateDelegateToken(String accountId, String tokenString, String delegateId, String delegateTokenName,
      String agentMtlAuthority, boolean shouldSetTokenNameInGlobalContext);
}
