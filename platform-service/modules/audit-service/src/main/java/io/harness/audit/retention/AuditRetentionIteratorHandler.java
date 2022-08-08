/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.retention;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofHours;
import static java.time.Duration.ofMinutes;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.api.AuditService;
import io.harness.audit.api.AuditYamlService;
import io.harness.audit.entities.AuditSettings;
import io.harness.audit.entities.AuditSettings.AuditSettingsKeys;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(PL)
public class AuditRetentionIteratorHandler implements MongoPersistenceIterator.Handler<AuditSettings> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private AuditService auditService;
  @Inject private AuditYamlService auditYamlService;

  @Override
  public void handle(AuditSettings auditSettings) {
    Instant toBeDeletedTillTimestamp = Instant.now().minus(
        Duration.ofSeconds(auditSettings.getRetentionPeriodInMonths() * ChronoUnit.MONTHS.getDuration().getSeconds()));

    auditService.purgeAuditsOlderThanTimestamp(auditSettings.getAccountIdentifier(), toBeDeletedTillTimestamp);
    auditYamlService.purgeYamlDiffOlderThanTimestamp(auditSettings.getAccountIdentifier(), toBeDeletedTillTimestamp);
  }

  public void registerIterators() {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("AuditRetentionIteratorTask")
            .poolSize(2)
            .interval(ofMinutes(2))
            .build(),
        AuditSettings.class,
        MongoPersistenceIterator.<AuditSettings, SpringFilterExpander>builder()
            .clazz(AuditSettings.class)
            .fieldName(AuditSettingsKeys.nextIteration)
            .targetInterval(ofHours(12))
            .acceptableNoAlertDelay(ofHours(14))
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }
}
