package io.harness.app;

import io.harness.secrets.SecretsDelegateCacheHelperService;

import java.time.Duration;

public class BasicSecretCacheHelper implements SecretsDelegateCacheHelperService {
  @Override
  public Duration initializeCacheExpiryTTL() {
    return Duration.ofMinutes(5);
  }
}
