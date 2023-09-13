/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.entities;

import io.harness.annotations.StoreIn;
import io.harness.ng.DbAliases;
import io.harness.notification.NotificationChannelType;
import io.harness.persistence.PersistentEntity;

import dev.morphia.annotations.Entity;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@StoreIn(DbAliases.NOTIFICATION)
@Entity(value = "notificationChannel")
@Document("NotificationChannel")
@TypeAlias("notificationChannel")
public class NotificationChannel implements PersistentEntity {
  @Id @dev.morphia.annotations.Id String uuid;

  String name;
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;

  NotificationChannelType notificationChannelType;
  Channel channel;

  private Status status;

  public enum Status {
    ENABLED,
    DISABLED;
  }
}
