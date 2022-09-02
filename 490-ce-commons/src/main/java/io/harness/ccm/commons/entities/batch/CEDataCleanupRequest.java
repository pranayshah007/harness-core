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
import io.harness.persistence.CreatedAtAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.CENG)
@Entity(value = "ceDataCleanupRequest", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "CEDataCleanupRequestKeys")
public final class CEDataCleanupRequest
    implements PersistentEntity, UuidAware, AccountAccess, CreatedAtAccess, CreatedAtAware, UpdatedAtAware {
  @Id String uuid;
  String accountId;
  String batchJobType;
  boolean processedRequest;
  int recordCount;
  Instant startAt;
  long createdAt;
  long lastUpdatedAt;
}
