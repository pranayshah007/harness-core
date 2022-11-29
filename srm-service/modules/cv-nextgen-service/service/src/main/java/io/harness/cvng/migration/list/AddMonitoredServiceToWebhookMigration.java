/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.cvng.activity.entities.Activity.ActivityKeys;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.entities.Webhook;
import io.harness.cvng.core.entities.Webhook.WebhookKeys;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateResults;

@Slf4j
public class AddMonitoredServiceToWebhookMigration implements CVNGMigration {
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    log.info("Begin migration for updating Activity with monitoredServiceIdentifier");
    Query<MonitoredService> monitoredServiceQuery = hPersistence.createQuery(MonitoredService.class);
    try (HIterator<MonitoredService> iterator = new HIterator<>(monitoredServiceQuery.fetch())) {
      while (iterator.hasNext()) {
        MonitoredService monitoredService = iterator.next();
        Query<Webhook> webhookQuery =
            hPersistence.createQuery(Webhook.class, excludeValidate)
                .filter(WebhookKeys.accountId, monitoredService.getAccountId())
                .filter(WebhookKeys.projectIdentifier, monitoredService.getProjectIdentifier())
                .filter(WebhookKeys.orgIdentifier, monitoredService.getOrgIdentifier())
                .filter("serviceIdentifier", monitoredService.getServiceIdentifier())
                .filter("envIdentifier", monitoredService.getEnvironmentIdentifier());

        UpdateResults updateResults = hPersistence.update(webhookQuery,
            hPersistence.createUpdateOperations(Webhook.class)
                .set(ActivityKeys.monitoredServiceIdentifier, monitoredService.getIdentifier()));
        log.info("Updated for Webhook {}, {}, {}", monitoredService.getProjectIdentifier(),
            monitoredService.getIdentifier(), updateResults);
      }
    }
  }

  @Override
  public ChecklistItem whatHappensOnRollback() {
    return ChecklistItem.NA;
  }

  @Override
  public ChecklistItem whatHappensIfOldVersionIteratorPicksMigratedEntity() {
    return ChecklistItem.NA;
  }
}
