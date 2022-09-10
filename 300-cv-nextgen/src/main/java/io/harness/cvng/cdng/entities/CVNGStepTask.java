/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@FieldNameConstants(innerTypeName = "CVNGStepTaskKeys")
@EqualsAndHashCode
@StoreIn(DbAliases.CVNG)
@Entity(value = "cvngStepTasks", noClassnameStored = true)
@HarnessEntity(exportable = true)
public class CVNGStepTask
    implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess, PersistentRegularIterable {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("iterator")
                 .unique(false)
                 .field(CVNGStepTaskKeys.status)
                 .field(CVNGStepTaskKeys.asyncTaskIteration)
                 .build())
        .build();
  }
  @Id private String uuid;
  private String accountId;
  private String orgIdentifier;
  private String projectIdentifier;
  private String serviceIdentifier;
  private String environmentIdentifier;
  String deploymentTag;

  private long createdAt;
  private long lastUpdatedAt;
  private String activityId;
  // verifyStepExecutionId
  @FdIndex private String callbackId;
  private boolean skip;
  private Status status;
  @EqualsAndHashCode.Exclude
  @FdTtlIndex
  @Builder.Default
  private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(6).toInstant());
  private long asyncTaskIteration;
  private String verificationJobInstanceId;
  private Instant deploymentStartTime;

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (CVNGStepTaskKeys.asyncTaskIteration.equals(fieldName)) {
      this.asyncTaskIteration = nextIteration;
      return;
    }

    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (CVNGStepTaskKeys.asyncTaskIteration.equals(fieldName)) {
      return this.asyncTaskIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  public static List<Status> getNonFinalStatues() {
    return Collections.singletonList(Status.IN_PROGRESS);
  }
  public void validate() {
    Preconditions.checkNotNull(accountId);
    if (!skip) {
      Preconditions.checkNotNull(activityId);
    }
    Preconditions.checkNotNull(callbackId);
    Preconditions.checkNotNull(status);
  }
  public enum Status { IN_PROGRESS, DONE }
}
