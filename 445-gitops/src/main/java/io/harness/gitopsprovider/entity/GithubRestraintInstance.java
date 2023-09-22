/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitopsprovider.entity;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.distribution.constraint.Constraint;
import io.harness.distribution.constraint.Consumer;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAccess;

import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import lombok.Builder;
import lombok.Data;
import lombok.With;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(CDC)
@Data
@Builder
@FieldNameConstants(innerTypeName = "GithubRestraintInstanceKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "githubRestraintInstances")
@Document("githubRestraintInstances")
@TypeAlias("githubRestraintInstance")
public class GithubRestraintInstance implements PersistentEntity, UuidAccess, PersistentRegularIterable {
  public static final long TTL = 6;

  @Id @dev.morphia.annotations.Id String uuid;
  String claimant;

  String resourceRestraintId;
  String resourceUnit;
  int order;

  Consumer.State state;
  int permits;

  String releaseEntityId;
  String releaseEntityType;

  long acquireAt;

  Long nextIteration;

  // audit fields
  @With @FdIndex @CreatedDate Long createdAt;
  @With @LastModifiedDate Long lastUpdatedAt;
  @Version Long version;

  @Builder.Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL).toInstant());

  @Override
  public Long obtainNextIteration(String fieldName) {
    return null;
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {}

  @Override
  public String getUuid() {
    return null;
  }
}
