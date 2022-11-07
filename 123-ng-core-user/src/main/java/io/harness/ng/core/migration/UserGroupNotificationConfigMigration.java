/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.annotations.dev.HarnessTeam.SPG;

import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;
import io.harness.ng.core.entities.EmailConfig;
import io.harness.ng.core.entities.NotificationSettingConfig;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.notification.NotificationChannelType;
import io.harness.repositories.ng.core.spring.UserGroupRepository;
import io.harness.springdata.PersistenceUtils;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.util.CloseableIterator;

@Slf4j
@OwnedBy(SPG)
public class UserGroupNotificationConfigMigration implements NGMigration {
  public static final int BATCH_SIZE = 500;
  private final RetryPolicy<Object> retryPolicy = PersistenceUtils.getRetryPolicy(
      "[Retrying]: Failed migrating User Group; attempt: {}", "[Failed]: Failed migrating User Group; attempt: {}");
  private final UserGroupRepository userGroupRepository;
  private final MongoTemplate mongoTemplate;
  private static final String DEBUG_LINE = "SEND_NOTIFICATION_TO_ALL_MIGRATION";

  @Inject
  public UserGroupNotificationConfigMigration(UserGroupRepository userGroupRepository, MongoTemplate mongoTemplate) {
    this.userGroupRepository = userGroupRepository;
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public void migrate() {
    log.info("{} starts ...", DEBUG_LINE);
    CloseableIterator<UserGroup> iterator = runQueryWithBatch(new Criteria(), BATCH_SIZE);
    while (iterator.hasNext()) {
      UserGroup userGroup = iterator.next();
      handleWithRetries(userGroup);
    }

    log.info("UserGroupSanitizationMigration completed.");
  }

  private void addSendNotificationBoolean(UserGroup userGroup) {
    log.info("UserGroup name is {}", userGroup.getIdentifier());
    try {
      if (userGroup.getNotificationConfigs().size() > 0) {
        List<NotificationSettingConfig> notificationConfigs = userGroup.getNotificationConfigs();
        notificationConfigs.stream().forEach(notificationSettingConfig -> {
          if (NotificationChannelType.EMAIL.equals(notificationSettingConfig.getType())) {
            ((EmailConfig) notificationSettingConfig).setSendEmailToAllUsers(Boolean.TRUE);
            log.info("Notification config of UserGroup {} is migrated", userGroup.getIdentifier());
          }
        });
        userGroupRepository.save(userGroup);
      }
    } catch (Exception e) {
      log.error("UserGroup {}, which belong to accountId {}, orgId {}, project {} was not successfully migrated",
          userGroup.getIdentifier(), userGroup.getAccountIdentifier(), userGroup.getOrgIdentifier(),
          userGroup.getProjectIdentifier());
      log.error("Error faced during migration of usergroup {} is {}, Exception is {}", DEBUG_LINE,
          userGroup.getIdentifier(), e.getMessage());
    }
  }

  private void handleWithRetries(UserGroup userGroup) {
    try {
      Failsafe.with(retryPolicy).run(() -> addSendNotificationBoolean(userGroup));
    } catch (Exception exception) {
      log.error(
          String.format(
              "[UserGroupSanitizationMigration] Unexpected error occurred during migration of user group with account %s, org %s, project %s and identifier %s",
              userGroup.getAccountIdentifier(), userGroup.getOrgIdentifier(), userGroup.getProjectIdentifier(),
              userGroup.getIdentifier()),
          exception);
    }
  }

  private CloseableIterator<UserGroup> runQueryWithBatch(Criteria criteria, int batchSize) {
    Query query = new Query(criteria);
    query.cursorBatchSize(batchSize);
    return mongoTemplate.stream(query, UserGroup.class);
  }
}
