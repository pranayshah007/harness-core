/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export.request;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.lang.String.format;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.CreatedByType;
import io.harness.beans.EmbeddedUser;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.FieldNameConstants;

@OwnedBy(CDC)
@Data
@Builder
@FieldNameConstants(innerTypeName = "ExportExecutionsRequestKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "exportExecutionsRequests", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class ExportExecutionsRequest
    implements PersistentRegularIterable, UuidAware, CreatedAtAware, CreatedByAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_status")
                 .field(ExportExecutionsRequestKeys.accountId)
                 .field(ExportExecutionsRequestKeys.status)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("status_nextIteration")
                 .field(ExportExecutionsRequestKeys.status)
                 .field(ExportExecutionsRequestKeys.nextIteration)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("status_expiresAt_nextCleanupIteration")
                 .field(ExportExecutionsRequestKeys.status)
                 .field(ExportExecutionsRequestKeys.expiresAt)
                 .field(ExportExecutionsRequestKeys.nextCleanupIteration)
                 .build())
        .build();
  }
  public enum OutputFormat { JSON }
  public enum Status { QUEUED, READY, FAILED, EXPIRED }

  @Id private String uuid;

  @NonNull private String accountId;
  @NonNull private OutputFormat outputFormat;
  @NonNull private ExportExecutionsRequestQuery query;

  private boolean notifyOnlyTriggeringUser;
  private List<String> userGroupIds;

  @NonNull private Status status;
  private long totalExecutions;
  private long expiresAt;

  // For status = READY
  private String fileId;

  // For status = FAILED
  private String errorMessage;

  @Builder.Default
  @JsonIgnore
  @FdTtlIndex
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private Date validUntil = Date.from(OffsetDateTime.now().plusHours(12).toInstant());

  private long createdAt;
  private CreatedByType createdByType;
  private EmbeddedUser createdBy;

  private Long nextIteration;
  private Long nextCleanupIteration;

  public Long obtainNextIteration(String fieldName) {
    if (ExportExecutionsRequestKeys.nextCleanupIteration.equals(fieldName)) {
      return nextCleanupIteration;
    } else if (ExportExecutionsRequestKeys.nextIteration.equals(fieldName)) {
      return nextIteration;
    }

    throw new IllegalStateException(format("Unknown field name for iteration: %s", fieldName));
  }

  public void updateNextIteration(String fieldName, long nextIteration) {
    if (ExportExecutionsRequestKeys.nextCleanupIteration.equals(fieldName)) {
      this.nextCleanupIteration = nextIteration;
      return;
    } else if (ExportExecutionsRequestKeys.nextIteration.equals(fieldName)) {
      this.nextIteration = nextIteration;
      return;
    }

    throw new IllegalStateException(format("Unknown field name for iteration: %s", fieldName));
  }
}
