/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.registries;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.sdk.core.adviser.Adviser;
import io.harness.registries.Registry;
import io.harness.registries.exceptions.DuplicateRegistryException;
import io.harness.registries.exceptions.UnregisteredKeyAccessException;

import com.google.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.validation.Valid;
import lombok.NonNull;

@SuppressWarnings("rawtypes")
@OwnedBy(CDC)
@Singleton
public class AdviserRegistry implements Registry<AdviserType, Adviser> {
  private Map<AdviserType, Adviser> registry = new ConcurrentHashMap<>();

  public void register(@NonNull AdviserType adviserType, @NonNull Adviser adviser) {
    if (registry.containsKey(adviserType)) {
      throw new DuplicateRegistryException(getType(), "Adviser Already Registered with this type: " + adviserType);
    }
    registry.put(adviserType, adviser);
  }

  public Adviser obtain(@Valid AdviserType adviserType) {
    if (registry.containsKey(adviserType)) {
      return registry.get(adviserType);
    }
    throw new UnregisteredKeyAccessException(getType(), "No Adviser registered for type: " + adviserType);
  }

  @Override
  public String getType() {
    return RegistryType.ADVISER.name();
  }
}
