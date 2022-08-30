/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.capability;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.TaskGroup;
import io.harness.iterator.PersistentFibonacciIterable;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@FieldNameConstants(innerTypeName = "CapabilityTaskSelectionDetailsKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "capabilityTaskSelectionDetails", noClassnameStored = true)
@OwnedBy(HarnessTeam.DEL)
@TargetModule(HarnessModule._955_DELEGATE_BEANS)
public class CapabilityTaskSelectionDetails implements PersistentEntity, PersistentFibonacciIterable {
  @Id private String uuid;
  @FdIndex private String accountId;
  @FdIndex private String capabilityId;
  private TaskGroup taskGroup;
  private Map<String, Set<String>> taskSelectors;
  private Map<String, String> taskSetupAbstractions;

  // Marks if these selection details are blocked and should be urgently eveluated, since capability for these selection
  // details has no validated delegates able to execute it
  @FdIndex private boolean blocked;

  List<Long> blockingCheckIterations;

  @FdTtlIndex private Date validUntil;

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (CapabilityTaskSelectionDetailsKeys.blockingCheckIterations.equals(fieldName)) {
      return isEmpty(blockingCheckIterations) ? null : blockingCheckIterations.get(0);
    }

    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public List<Long> recalculateNextIterations(String fieldName, boolean skipMissed, long throttled) {
    if (CapabilityTaskSelectionDetailsKeys.blockingCheckIterations.equals(fieldName)) {
      if (blockingCheckIterations == null) {
        blockingCheckIterations = new ArrayList<>();
      }
      if (recalculateTimestamps(
              blockingCheckIterations, skipMissed, throttled, Duration.ofSeconds(10), Duration.ofMinutes(30))) {
        return blockingCheckIterations;
      }
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }
}
