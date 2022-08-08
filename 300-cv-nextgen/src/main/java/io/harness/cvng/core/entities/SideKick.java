/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "SidekickKeys")
@Entity(value = "sidekicks", noClassnameStored = true)
@HarnessEntity(exportable = false)
@StoreIn(DbAliases.CVNG)
public final class SideKick implements UuidAware, CreatedAtAware, UpdatedAtAware, PersistentEntity {
  @Id private String uuid;
  @FdIndex private long createdAt;
  private long lastUpdatedAt;
  @FdTtlIndex @Builder.Default private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(1).toInstant());

  SideKickData sideKickData;
  Instant runAfter;
  Status status;
  String exception;
  String stacktrace;
  int retryCount;

  public interface SideKickData {
    Type getType();
  }
  public enum Type {
    DEMO_DATA_ACTIVITY_CREATOR,
    RETRY_CHANGE_SOURCE_HANDLE_DELETE,
    VERIFICATION_TASK_CLEANUP,
    VERIFICATION_JOB_INSTANCE_CLEANUP
  }

  public enum Status { QUEUED, RUNNING, SUCCESS, FAILED }
}
