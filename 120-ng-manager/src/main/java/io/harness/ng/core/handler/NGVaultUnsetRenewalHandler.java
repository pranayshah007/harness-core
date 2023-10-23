/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.handler;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofHours;
import static java.time.Duration.ofMinutes;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.Connector.ConnectorKeys;
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
import io.harness.security.encryption.AccessType;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
@Singleton
@Slf4j
public class NGVaultUnsetRenewalHandler implements Handler<VaultConnector> {
  private final PersistenceIteratorFactory persistenceIteratorFactory;
  private final MongoTemplate mongoTemplate;
  private final NGVaultService vaultService;

  @Inject
  public NGVaultUnsetRenewalHandler(
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
            .interval(ofMinutes(30))
            .build(),
        VaultConnector.class,
        MongoPersistenceIterator.<VaultConnector, SpringFilterExpander>builder()
            .clazz(VaultConnector.class)
            .fieldName(VaultConnectorKeys.nextTokenLookupIteration)
            .targetInterval(ofHours(6))
            .acceptableExecutionTime(ofMinutes(1))
            .acceptableNoAlertDelay(ofHours(12))
            .filterExpander(filterExpander)
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }

  @Override
  public void handle(VaultConnector vaultConnector) {
    log.info("Running token lookup handler for vault ConnectorDisconnectHandler: {}", vaultConnector.getIdentifier());
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(NG_MANAGER.getServiceId()));

      if (vaultConnector.getDeleted() || !AccessType.TOKEN.equals(vaultConnector.getAccessType())) {
        return;
      }

      long renewalInterval = vaultConnector.getRenewalIntervalMinutes();
      if (renewalInterval <= 0) {
        log.info("Vault {} not configured for renewal. Skipping token lookup", vaultConnector.getIdentifier());
        return;
      }

      vaultService.unsetRenewalInterval(vaultConnector);
    } catch (Exception ex) {
      log.error("Exception occurred while checking if token for Vault {} is eligible for renewal",
          vaultConnector.getIdentifier(), ex);
    }
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
}
