/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.network.SafeHttpCall;
import io.harness.ng.core.DecryptableEntityWithEncryptionConsumers;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class SecretUtils {
  @Inject private SecretNGManagerClient secretNGManagerClient;

  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private final int MAX_ATTEMPTS = 6;

  public DecryptableEntity decrypt(DecryptableEntity decryptableEntity, List<EncryptedDataDetail> encryptionDetails,
      String accountId, String connectorId) {
    RetryPolicy<Object> retryPolicy =
        getRetryPolicy(format("[Retrying failed call to decrypt ConnectorDisconnectHandler credentials: [%s], attempt: {}", connectorId),
            format("Failed to decrypt ConnectorDisconnectHandler credentials: [%s] after retrying {} times", connectorId));

    DecryptableEntityWithEncryptionConsumers entity = DecryptableEntityWithEncryptionConsumers.builder()
                                                          .decryptableEntity(decryptableEntity)
                                                          .encryptedDataDetailList(encryptionDetails)
                                                          .build();

    return Failsafe.with(retryPolicy)
        .get(() -> SafeHttpCall.execute(secretNGManagerClient.decryptEncryptedDetails(entity, accountId)).getData());
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }
}
