/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.core.services.api.UpdatableEntity;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.channelDetails.CVNGNotificationChannel;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.query.UpdateOperations;

@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "NotificationRuleKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Entity(value = "notificationRules")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CV)
@StoreIn(DbAliases.CVNG)
public abstract class NotificationRule
    implements PersistentEntity, UuidAware, AccountAccess, UpdatedAtAware, CreatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_query_idx")
                 .unique(true)
                 .field(NotificationRuleKeys.accountId)
                 .field(NotificationRuleKeys.orgIdentifier)
                 .field(NotificationRuleKeys.projectIdentifier)
                 .field(NotificationRuleKeys.identifier)
                 .build())
        .build();
  }

  String accountId;
  String projectIdentifier;
  String orgIdentifier;
  @Id private String uuid;
  String identifier;
  String name;

  private long lastUpdatedAt;
  private long createdAt;
  private int version;

  NotificationRuleType type;
  CVNGNotificationChannel notificationMethod;

  public abstract static class NotificationRuleUpdatableEntity<T extends NotificationRule, D extends NotificationRule>
      implements UpdatableEntity<T, D> {
    protected void setCommonOperations(UpdateOperations<T> updateOperations, D notificationRule) {
      updateOperations.set(NotificationRuleKeys.identifier, notificationRule.getIdentifier())
          .set(NotificationRuleKeys.name, notificationRule.getName())
          .set(NotificationRuleKeys.type, notificationRule.getType())
          .set(NotificationRuleKeys.notificationMethod, notificationRule.getNotificationMethod())
          .inc(NotificationRuleKeys.version);
    }
  }
}
