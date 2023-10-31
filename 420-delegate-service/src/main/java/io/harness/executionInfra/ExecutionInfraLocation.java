/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.executionInfra;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.SecondaryStoreIn;
import io.harness.annotations.StoreIn;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.Date;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.joda.time.DateTime;

@Getter
@Builder
@StoreIn(DbAliases.HARNESS)
@SecondaryStoreIn(DbAliases.DMS)
@Entity(value = "executionInfraLocation", noClassnameStored = true)
@HarnessEntity(exportable = false)
@FieldNameConstants(innerTypeName = "ExecutionInfraLocationKeys")
public class ExecutionInfraLocation implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware {
  @Setter @Id private String uuid;
  private final String delegateGroupName;
  private final String runnerType;
  private final Map<String, String> stepTaskIds; // mapping K8S stepId -> delegate taskId
  private final String createdByDelegateId;
  @Setter private long createdAt;
  @Setter private long lastUpdatedAt;
  @FdTtlIndex @Builder.Default private final Date validUntil = DateTime.now().plusDays(3).toDate();
}
