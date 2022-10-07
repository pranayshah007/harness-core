/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iterator;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.Arrays.asList;

import io.harness.annotations.StoreIn;
import io.harness.ng.DbAliases;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@StoreIn(DbAliases.TEST)
@Entity(value = "!!!testIrregularIterable")
@FieldNameConstants(innerTypeName = "TestIrregularIterableEntityKeys")
public class TestIrregularIterableEntity implements PersistentIrregularIterable {
  @Id private String uuid;
  private String name;
  private List<Long> nextIterations;

  @Override
  public Long obtainNextIteration(String fieldName) {
    return isEmpty(nextIterations) ? null : nextIterations.get(0);
  }

  @Override
  public List<Long> recalculateNextIterations(String fieldName, boolean skipMissing, long throttled) {
    if (nextIterations != null && nextIterations.size() > 1) {
      // If no new items are provided we need to remove the first item to prevent it being added again with merge
      nextIterations.remove(0);
      return null;
    }

    final long millis = System.currentTimeMillis();
    nextIterations = asList(millis + 1000, millis + 2000, millis + 3000, millis + 4000);
    return nextIterations;
  }
}
