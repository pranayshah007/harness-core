/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.beans.ExecutionStatus;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import software.wings.common.VerificationConstants;
import software.wings.service.impl.analysis.DataCollectionInfoV2;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import java.time.OffsetDateTime;
import java.util.Date;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.PrePersist;

@FieldNameConstants(innerTypeName = "CVTaskKeys")
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "cvTasks", noClassnameStored = true)
@HarnessEntity(exportable = false)
public final class CVTask implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess {
  @Id private String uuid;

  @NonNull @FdIndex private String accountId;
  private String cvConfigId;
  @FdIndex private String stateExecutionId;
  private String nextTaskId;
  @FdIndex @NonNull private ExecutionStatus status;

  private long createdAt;
  @FdIndex private long lastUpdatedAt;

  private int retryCount;

  private String exception;
  private long validAfter;
  private String correlationId;
  private DataCollectionInfoV2 dataCollectionInfo;

  @JsonIgnore @SchemaIgnore @FdTtlIndex private Date validUntil;
  @PrePersist
  public void onUpdate() {
    // better to add days as plus month can vary and add complications to testing etc.
    validUntil = Date.from(OffsetDateTime.now().plusDays(VerificationConstants.CV_TASK_TTL_MONTHS * 30).toInstant());
  }
}
