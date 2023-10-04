/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.notification.NotificationEntity;
import io.harness.notification.NotificationEvent;
import io.harness.persistence.PersistentEntity;

import dev.morphia.annotations.Entity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "NotificationRuleKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.NOTIFICATION)
@Entity(value = "notificationRule", noClassnameStored = true)
@Document("NotificationRule")
@TypeAlias("notificationRule")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.PL)
public class NotificationRule implements PersistentEntity, PersistentRegularIterable {
  @Id @dev.morphia.annotations.Id String uuid;

  String notificationRuleIdentifier;

  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;

  NotificationEntity notificationEntity;

  List<NotificationCondition> notificationConditions;

  private Status status;
  @FdIndex private long nextIteration;

  @CreatedBy private EmbeddedUser createdBy;
  @LastModifiedBy private EmbeddedUser lastUpdatedBy;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;

  @Override
  public Long obtainNextIteration(String fieldName) {
    return nextIteration;
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }

  @Override
  public String logKeyForId() {
    return PersistentRegularIterable.super.logKeyForId();
  }

  public enum Status {
    ENABLED,
    DISABLED;
  }

  @UtilityClass
  public static final class NotificationRuleKeys {}

  public List<NotificationEventConfig> getNotificationEventConfigs() {
    return new ArrayList<>(notificationConditions.get(0).notificationEventConfigs);
  }

  public List<NotificationEvent> getNotificationEvents() {
    return getNotificationEventConfigs()
        .stream()
        .map(NotificationEventConfig::getNotificationEvent)
        .collect(Collectors.toList());
  }

  public List<NotificationEventConfig> getNotificationEventConfigs(NotificationEvent notificationEvent) {
    return getNotificationEventConfigs()
        .stream()
        .filter(notificationEventConfig -> notificationEventConfig.getNotificationEvent().equals(notificationEvent))
        .collect(Collectors.toList());
  }

  public List<NotificationChannel> getNotificationChannelForEvent(NotificationEvent notificationEvent) {
    Optional<NotificationEventConfig> notificationEventConfigOptional =
        getNotificationEventConfigs(notificationEvent).stream().findFirst();
    return notificationEventConfigOptional.isPresent() ? notificationEventConfigOptional.get().notificationChannels
                                                       : Collections.emptyList();
  }
}
