/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ff.filters;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;

import java.util.EnumMap;
import java.util.Set;
import java.util.function.Predicate;

@OwnedBy(HarnessTeam.CDP)
public abstract class AbstractFeatureFlagFilter<E> implements FeatureFlagFilter<E> {
  @Override
  public Predicate<? super E> filter(String accountId, FeatureName featureName) {
    return object -> {
      Set<E> filter = getFeatureFlagMap().get(featureName);
      if (!isEmpty(filter) && filter.contains(object)) {
        return isFeatureFlagEnabled(featureName, accountId);
      }
      return true;
    };
  }

  public abstract EnumMap<FeatureName, Set<E>> getFeatureFlagMap();

  public abstract boolean isFeatureFlagEnabled(FeatureName featureName, String accountId);
}
