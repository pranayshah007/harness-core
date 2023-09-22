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
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.notification.NotificationChannelType;
import io.harness.persistence.PersistentEntity;

import dev.morphia.annotations.Entity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "NotificationChannelKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.NOTIFICATION)
@Entity(value = "notificationChannel",noClassnameStored = true)
@Document("NotificationChannel")
@TypeAlias("notificationChannel")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.PL)
public class NotificationChannel implements PersistentEntity, PersistentRegularIterable {
  @Id @dev.morphia.annotations.Id String uuid;

  String name;
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;

  NotificationChannelType notificationChannelType;

  EmailChannel emailChannel;
  SlackChannel slackChannel;
  MicrosoftTeamsChannel microsoftTeamsChannel;
  PagerDutyChannel pagerDutyChannel;
  WebhookChannel webhookChannel;

  private Status status;
  @FdIndex
  private long nextIteration;

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
}
