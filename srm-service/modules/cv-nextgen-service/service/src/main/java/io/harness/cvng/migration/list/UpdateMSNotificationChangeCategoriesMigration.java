/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.beans.change.ChangeCategory;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.cvng.notification.beans.MonitoredServiceChangeEventType;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceNotificationRuleCondition;
import io.harness.cvng.notification.entities.NotificationRule;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UpdateMSNotificationChangeCategoriesMigration implements CVNGMigration {
  @Inject HPersistence hPersistence;

  @Override
  public void migrate() {
    log.info("Begin migration for adding changeCategories to conditions in MS type notifications");
    Query<NotificationRule> notificationRuleQuery = hPersistence.createQuery(NotificationRule.class);
    try (HIterator<NotificationRule> iterator = new HIterator<>(notificationRuleQuery.fetch())) {
      while (iterator.hasNext()) {
        try {
          NotificationRule notificationRule = iterator.next();
          List<MonitoredServiceNotificationRuleCondition> conditions =
              ((MonitoredServiceNotificationRule) notificationRule).getConditions();
          conditions.forEach(condition -> {
            if (condition instanceof MonitoredServiceNotificationRule.MonitoredServiceChangeImpactCondition) {
              MonitoredServiceNotificationRule.MonitoredServiceChangeImpactCondition changeImpactCondition =
                  (MonitoredServiceNotificationRule.MonitoredServiceChangeImpactCondition) condition;
              List<ChangeCategory> changeCategories =
                  changeImpactCondition.getChangeEventTypes()
                      .stream()
                      .map(MonitoredServiceChangeEventType::convertMonitoredServiceChangeEventTypeToChangeCategory)
                      .collect(Collectors.toList());
              changeImpactCondition.setChangeCategories(changeCategories);
              changeImpactCondition.setChangeEventTypes(null);
            } else if (condition instanceof MonitoredServiceNotificationRule.MonitoredServiceChangeObservedCondition) {
              MonitoredServiceNotificationRule.MonitoredServiceChangeObservedCondition changeObservedCondition =
                  (MonitoredServiceNotificationRule.MonitoredServiceChangeObservedCondition) condition;
              List<ChangeCategory> changeCategories =
                  changeObservedCondition.getChangeEventTypes()
                      .stream()
                      .map(MonitoredServiceChangeEventType::convertMonitoredServiceChangeEventTypeToChangeCategory)
                      .collect(Collectors.toList());
              changeObservedCondition.setChangeCategories(changeCategories);
              changeObservedCondition.setChangeEventTypes(null);
            }
          });
          ((MonitoredServiceNotificationRule) notificationRule).setConditions(conditions);
          hPersistence.save(notificationRule);
          log.info("Updated notification rule");
        } catch (Exception ex) {
          log.error("Exception occurred while updating status of activity", ex);
        }
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
