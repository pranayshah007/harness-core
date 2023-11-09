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
import io.harness.notification.NotificationChannelType;
import io.harness.persistence.PersistentEntity;

import dev.morphia.annotations.Entity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "NotificationChannelKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.NOTIFICATION)
@Entity(value = "notificationChannel", noClassnameStored = true)
@Document("NotificationChannel")
@TypeAlias("notificationChannel")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.PL)
@Slf4j
public class NotificationChannel implements PersistentEntity, PersistentRegularIterable, Channel {
  @Id @dev.morphia.annotations.Id String uuid;

  String identifier;

  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;

  NotificationChannelType notificationChannelType;
  Status status;

  Channel channel;

  @CreatedBy EmbeddedUser createdBy;
  @LastModifiedBy EmbeddedUser lastUpdatedBy;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;

  @FdIndex long nextIteration;

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

  @Override
  public Object toObjectofProtoSchema() {
    // No implementation
    return null;
  }

  @Override
  public NotificationChannelType getChannelType() {
    return notificationChannelType;
  }

  public enum Status {
    ENABLED,
    DISABLED;
  }
}
