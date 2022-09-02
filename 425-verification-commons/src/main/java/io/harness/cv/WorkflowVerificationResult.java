/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cv;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.beans.ExecutionStatus;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
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
@FieldNameConstants(innerTypeName = "WorkflowVerificationResultKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "workflowVerificationResults", noClassnameStored = true)
@HarnessEntity(exportable = true)
public class WorkflowVerificationResult
    implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess {
  @Id private String uuid;
  @FdIndex private String accountId;
  private String appId;
  @FdUniqueIndex private String stateExecutionId;
  private String serviceId;
  private String envId;
  private String workflowId;
  // TODO: move it to enum once StateType moves out of 400-rest
  private String stateType;
  private ExecutionStatus executionStatus;
  private boolean analyzed;
  private boolean rollback;
  private String message;
  private String executionUrl;
  @FdIndex private long createdAt;
  @FdIndex private long lastUpdatedAt;

  @JsonIgnore
  @SchemaIgnore
  @Builder.Default
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(6).toInstant());
}
