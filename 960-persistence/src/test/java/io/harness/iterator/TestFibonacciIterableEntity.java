/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iterator;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.time.Duration.ofHours;
import static java.time.Duration.ofMinutes;

import io.harness.annotations.StoreIn;
import io.harness.ng.DbAliases;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@StoreIn(DbAliases.TEST)
@Entity(value = "!!!testFibonacciIterable")
@FieldNameConstants(innerTypeName = "FibonacciIterableEntityKeys")
@Slf4j
public class TestFibonacciIterableEntity implements PersistentFibonacciIterable {
  @Id private String uuid;
  private List<Long> nextIterations;

  @Override
  public List<Long> recalculateNextIterations(String fieldName, boolean skipMissed, long throttled) {
    if (nextIterations == null) {
      nextIterations = new ArrayList<>();
    }
    if (recalculateTimestamps(nextIterations, skipMissed, throttled, ofMinutes(1), ofHours(1))) {
      return nextIterations;
    }

    return null;
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return isEmpty(nextIterations) ? null : nextIterations.get(0);
  }

  @Override
  public String getUuid() {
    return uuid;
  }
}
