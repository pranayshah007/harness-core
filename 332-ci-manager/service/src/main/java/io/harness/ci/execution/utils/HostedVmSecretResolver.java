package io.harness.ci.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.beans.entities.EncryptedDataDetails;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import javax.cache.Cache;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class HostedVmSecretResolver {
  private final SecretManagerClientService secretManagerClientService;

  private final @NonNull Cache<String, EncryptedDataDetails> secretsCache;
  public static final String SECRET_CACHE_KEY = "secretCache";

  @Inject
  public HostedVmSecretResolver(@Named("PRIVILEGED") SecretManagerClientService secretManagerClientService,
      @Named(SECRET_CACHE_KEY) Cache<String, EncryptedDataDetails> secretsCache) {
    this.secretManagerClientService = secretManagerClientService;
    this.secretsCache = secretsCache;
  }

  public void resolve(Ambiance ambiance, Object o) {
    HostedVmSecretEvaluator hostedVmSecretEvaluator = HostedVmSecretEvaluator.builder()
                                                          .ngSecretService(secretManagerClientService)
                                                          .secretsCache(secretsCache)
                                                          .build();
    hostedVmSecretEvaluator.resolve(o, AmbianceUtils.getNgAccess(ambiance), ambiance.getExpressionFunctorToken());
  }
}
