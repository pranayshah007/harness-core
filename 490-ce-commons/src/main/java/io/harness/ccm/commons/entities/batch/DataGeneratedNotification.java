/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.entities.batch;

import io.harness.annotation.StoreIn;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.checkerframework.common.aliasing.qual.Unique;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@StoreIn(DbAliases.CENG)
@Entity(value = "dataGeneratedNotification", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "DataGeneratedNotificationKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
public final class DataGeneratedNotification implements PersistentEntity, UuidAware, CreatedAtAware, AccountAccess {
  @Id String uuid;
  @Unique String accountId;
  boolean mailSent;
  List<String> clusterIds;
  long createdAt;
}
