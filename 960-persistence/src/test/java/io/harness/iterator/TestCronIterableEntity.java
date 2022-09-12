/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iterator;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

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
@Entity(value = "!!!testCronIterable")
@FieldNameConstants(innerTypeName = "CronIterableEntityKeys")
@Slf4j
public class TestCronIterableEntity implements PersistentCronIterable {
  @Id private String uuid;
  private String name;
  private String expression;
  private List<Long> nextIterations;

  @Override
  public Long obtainNextIteration(String fieldName) {
    return isEmpty(nextIterations) ? null : nextIterations.get(0);
  }

  @Override
  public List<Long> recalculateNextIterations(String fieldName, boolean skipMissing, long throttled) {
    if (nextIterations == null) {
      nextIterations = new ArrayList<>();
    }

    if (expandNextIterations(skipMissing, throttled, expression, nextIterations)) {
      log.info(expression);
      return nextIterations;
    }

    return null;
  }
}
