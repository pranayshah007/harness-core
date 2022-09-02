/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.state.inspection;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.state.StateInspectionUtils;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@OwnedBy(CDC)
@Value
@Builder
@StoreIn(DbAliases.HARNESS)
@Entity(value = "stateInspections", noClassnameStored = true)
@HarnessEntity(exportable = false)
@FieldNameConstants(innerTypeName = "StateInspectionKeys")
public final class StateInspection implements PersistentEntity {
  @Id private final String stateExecutionInstanceId;
  private final Map<String, StateInspectionData> data;

  @FdTtlIndex
  private final Date validUntil = Date.from(OffsetDateTime.now().plus(StateInspectionUtils.TTL).toInstant());
}
