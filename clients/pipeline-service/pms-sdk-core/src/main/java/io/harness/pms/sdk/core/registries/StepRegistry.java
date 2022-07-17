/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.registries;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.registries.Registry;
import io.harness.registries.exceptions.DuplicateRegistryException;
import io.harness.registries.exceptions.UnregisteredKeyAccessException;

import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.NonNull;

@OwnedBy(CDC)
@Singleton
public class StepRegistry implements Registry<StepType, Step> {
  Map<StepType, Step> registry = new ConcurrentHashMap<>();

  public Map<StepType, Step> getRegistry() {
    return new HashMap<>(registry);
  }

  public void register(@NonNull StepType stepType, @NonNull Step step) {
    if (registry.containsKey(stepType)) {
      throw new DuplicateRegistryException(getType(), "Step Already Registered with this type: " + stepType);
    }
    registry.put(stepType, step);
  }

  public Step obtain(@NonNull StepType stepType) {
    if (registry.containsKey(stepType)) {
      return registry.get(stepType);
    }
    throw new UnregisteredKeyAccessException(getType(), "No Step registered for type: " + stepType);
  }

  @Override
  public String getType() {
    return RegistryType.STEP.name();
  }
}
