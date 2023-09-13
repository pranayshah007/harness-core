/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.handler;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.connector.entities.Connector.ConnectorKeys;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;
import static io.harness.security.encryption.AccessType.APP_ROLE;

import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.vaultconnector.VaultConnector;
import io.harness.connector.entities.embedded.vaultconnector.VaultConnector.VaultConnectorKeys;
import io.harness.connector.services.NGVaultService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
@Singleton
@Slf4j
public class NGVaultSecretManagerRenewalHandler implements Handler<VaultConnector> {
  private final PersistenceIteratorFactory persistenceIteratorFactory;
  private final MongoTemplate mongoTemplate;
  private final NGVaultService vaultService;

  @Inject
  public NGVaultSecretManagerRenewalHandler(
      PersistenceIteratorFactory persistenceIteratorFactory, MongoTemplate mongoTemplate, NGVaultService vaultService) {
    this.persistenceIteratorFactory = persistenceIteratorFactory;
    this.mongoTemplate = mongoTemplate;
    this.vaultService = vaultService;
  }

  public void registerIterators(int threadPoolSize) {
    SpringFilterExpander filterExpander = getFilterQuery();
    registerIteratorWithFactory(threadPoolSize, filterExpander);
  }

  private void registerIteratorWithFactory(int threadPoolSize, @NotNull SpringFilterExpander filterExpander) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name(this.getClass().getName())
            .poolSize(threadPoolSize)
            .interval(ofSeconds(5))
            .build(),
        VaultConnector.class,
        MongoPersistenceIterator.<VaultConnector, SpringFilterExpander>builder()
            .clazz(VaultConnector.class)
            .fieldName(VaultConnectorKeys.nextTokenRenewIteration)
            .targetInterval(ofSeconds(31))
            .acceptableExecutionTime(ofSeconds(31))
            .acceptableNoAlertDelay(ofSeconds(62))
            .filterExpander(filterExpander)
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }

  @Override
  public void handle(VaultConnector vaultConnector) {
    log.info("renewing client tokens for {}", vaultConnector.getUuid());
    SecurityContextBuilder.setContext(new ServicePrincipal(NG_MANAGER.getServiceId()));
    if (isRenewalNotNeeded(vaultConnector)) {
      log.info(
          "Vault {} configured with Vault-Agent or Aws Iam Auth. It does not need renewal", vaultConnector.getUuid());
    } else {
      vaultConnector = mongoTemplate.findById(vaultConnector.getId(), VaultConnector.class);
      try {
        long renewalInterval = vaultConnector.getRenewalIntervalMinutes();

        if (renewalInterval <= 0) {
          log.info("Vault {} not configured for renewal.", vaultConnector.getUuid());
          return;
        }
        if (vaultConnector.getRenewalPaused()) {
          log.info("Skipping renewal for vault {} as renewal is paused.", vaultConnector.getUuid());
          return;
        }
        // for existing soft deleted vault entries, do not renew token. Remove this code after existing soft deleted
        // entries are removed from dB
        if (vaultConnector.getDeleted()) {
          return;
        }
        if (!checkIfEligibleForRenewal(vaultConnector.getRenewedAt(), renewalInterval)) {
          log.info("Vault config {} renewed at {} not renewing now", vaultConnector.getUuid(),
              vaultConnector.getRenewedAt());
          return;
        }
        if (vaultConnector.getAccessType() == APP_ROLE) {
          if (vaultConnector.getRenewAppRoleToken()) {
            vaultService.renewAppRoleClientToken(vaultConnector);
          }
        } else {
          vaultService.renewToken(vaultConnector);
        }
      } catch (Exception e) {
        log.info("Failed to renew vault token for vault id {}", vaultConnector.getUuid(), e);
      }
    }
  }

  private boolean isRenewalNotNeeded(VaultConnector vaultConnector) {
    return vaultConnector.isUseVaultAgent() || vaultConnector.getUseAwsIam() || vaultConnector.getUseK8sAuth();
  }

  private SpringFilterExpander getFilterQuery() {
    return query -> {
      Criteria criteria = Criteria.where(ConnectorKeys.type)
                              .in(Sets.newHashSet(ConnectorType.VAULT))
                              .and(ConnectorKeys.deleted)
                              .ne(true);
      query.addCriteria(criteria);
    };
  }

  private boolean checkIfEligibleForRenewal(long renewedAt, long renewalInterval) {
    long currentTime = System.currentTimeMillis();
    return TimeUnit.MILLISECONDS.toMinutes(currentTime - renewedAt) >= renewalInterval;
  }
}
