/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofMillis;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigrateSecretTask;
import io.harness.exception.WingsException;
import io.harness.queue.QueueConsumer;
import io.harness.queue.QueueListener;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by rsingh on 10/6/17.
 */
@OwnedBy(PL)
@Slf4j
public class SecretMigrationEventListener extends QueueListener<MigrateSecretTask> {
  private static final int NUM_OF_RETRIES = 3;
  @Inject private SecretService secretService;

  @Inject
  public SecretMigrationEventListener(QueueConsumer<MigrateSecretTask> queueConsumer) {
    super(queueConsumer, true);
  }

  @Override
  public void onMessage(MigrateSecretTask migrateSecretTask) {
    log.info("Processing secret manager transition event for secret '{}' in account '{}'",
        migrateSecretTask.getSecretId(), migrateSecretTask.getAccountId());
    int failedAttempts = 0;
    while (true) {
      try {
        secretService.migrateSecret(migrateSecretTask);
        break;
      } catch (WingsException e) {
        failedAttempts++;
        log.warn("Transitioning secret '{}' failed. trial num: {}", migrateSecretTask, failedAttempts);
        if (failedAttempts == NUM_OF_RETRIES) {
          log.error("Transitioning secret '{}' failed after {} retries", migrateSecretTask, NUM_OF_RETRIES, e);
          break;
        }
        sleep(ofMillis(1000));
      } catch (IllegalStateException e) {
        log.error("Could not transition secret '{}'", migrateSecretTask, e);
        break;
      }
    }
  }
}
