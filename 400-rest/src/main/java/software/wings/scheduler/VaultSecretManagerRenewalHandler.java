/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;
import static io.harness.security.encryption.AccessType.APP_ROLE;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.alert.AlertType.InvalidKMS;

import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SecretManagerConfig.SecretManagerConfigKeys;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.secretmanagerclient.NGSecretManagerMetadata.NGSecretManagerMetadataKeys;
import io.harness.security.encryption.EncryptionType;
import io.harness.workers.background.AccountStatusBasedEntityProcessController;

import software.wings.beans.BaseVaultConfig;
import software.wings.beans.alert.KmsSetupAlert;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.security.VaultService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class VaultSecretManagerRenewalHandler implements Handler<SecretManagerConfig> {
  @Inject private AccountService accountService;
  @Inject private VaultService vaultService;
  @Inject private AlertService alertService;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MorphiaPersistenceProvider<SecretManagerConfig> persistenceProvider;

  public void registerIterators(int threadPoolSize) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("VaultSecretManagerRenewalHandler")
            .poolSize(threadPoolSize)
            .interval(ofSeconds(5))
            .build(),
        SecretManagerConfig.class,
        MongoPersistenceIterator.<SecretManagerConfig, MorphiaFilterExpander<SecretManagerConfig>>builder()
            .clazz(SecretManagerConfig.class)
            .fieldName(SecretManagerConfigKeys.nextTokenRenewIteration)
            .targetInterval(ofSeconds(31))
            .acceptableNoAlertDelay(ofSeconds(62))
            .handler(this)
            .entityProcessController(new AccountStatusBasedEntityProcessController<>(accountService))
            .filterExpander(query
                -> query.criteria(SecretManagerConfigKeys.encryptionType)
                       .in(Sets.newHashSet(EncryptionType.VAULT, EncryptionType.VAULT_SSH))
                       .criteria(SecretManagerConfigKeys.ngMetadata + "." + NGSecretManagerMetadataKeys.deleted)
                       .notEqual(true))
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }

  @Override
  public void handle(SecretManagerConfig secretManagerConfig) {
    log.info("renewing client tokens for {}", secretManagerConfig.getUuid());
    BaseVaultConfig baseVaultConfig = (BaseVaultConfig) secretManagerConfig;
    // this should not be needed when we update the query to return only vaults which are needed to be renewed.
    if (baseVaultConfig.isUseVaultAgent()) {
      log.info("Vault {} not configured with Vault Agent and does not need renewal", baseVaultConfig.getUuid());
      return;
    }
    if (baseVaultConfig.isUseK8sAuth()) {
      log.info("Vault {} configured with K8s Auth does not need renewal", baseVaultConfig.getUuid());
      return;
    }
    KmsSetupAlert kmsSetupAlert = vaultService.getRenewalAlert(baseVaultConfig);
    try {
      long renewalInterval = baseVaultConfig.getRenewalInterval();
      if (renewalInterval <= 0 || secretManagerConfig.isTemplatized()) {
        log.info("Vault {} not configured for renewal.", baseVaultConfig.getUuid());
        return;
      }
      if (!checkIfEligibleForRenewal(baseVaultConfig.getRenewedAt(), renewalInterval)) {
        log.info("Vault config {} renewed at {} not renewing now", baseVaultConfig.getUuid(),
            baseVaultConfig.getRenewedAt());
        return;
      }
      if (baseVaultConfig.getAccessType() == APP_ROLE) {
        if (baseVaultConfig.getRenewAppRoleToken()) {
          vaultService.renewAppRoleClientToken(baseVaultConfig);
        }
      } else {
        vaultService.renewToken(baseVaultConfig);
      }
      alertService.closeAlert(baseVaultConfig.getAccountId(), GLOBAL_APP_ID, InvalidKMS, kmsSetupAlert);
    } catch (Exception e) {
      log.info("Failed to renew vault token for vault id {}", secretManagerConfig.getUuid(), e);
      alertService.openAlert(baseVaultConfig.getAccountId(), GLOBAL_APP_ID, InvalidKMS, kmsSetupAlert);
    }
  }

  private boolean checkIfEligibleForRenewal(long renewedAt, long renewalInterval) {
    long currentTime = System.currentTimeMillis();
    return TimeUnit.MILLISECONDS.toMinutes(currentTime - renewedAt) >= renewalInterval;
  }
}
