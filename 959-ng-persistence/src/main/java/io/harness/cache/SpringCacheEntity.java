/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cache;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.time.Duration.ofDays;

import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.persistence.PersistentEntity;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Date;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(CDC)
@Value
@Builder
@FieldNameConstants(innerTypeName = "SpringCacheEntityKeys")
@Entity(value = "cacheEntities")
@Document("cacheEntities")
public class SpringCacheEntity implements PersistentEntity {
  public static final Duration TTL = ofDays(183);
  public static final long TTL_MONTHS = 6;

  @Id @org.mongodb.morphia.annotations.Id String canonicalKey;
  long contextValue;

  byte[] entity;

  // audit fields
  @Wither @FdIndex @CreatedDate Long createdAt;
  @Wither @LastModifiedDate Long lastUpdatedAt;
  Long entityUpdatedAt;

  @Version Long version;
  @Builder.Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant());
}
