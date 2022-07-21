/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.registries;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.execution.expression.SdkFunctor;
import io.harness.registries.Registry;
import io.harness.registries.exceptions.DuplicateRegistryException;
import io.harness.registries.exceptions.UnregisteredKeyAccessException;

import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class FunctorRegistry implements Registry<String, SdkFunctor> {
  Map<String, SdkFunctor> registry = new ConcurrentHashMap<>();

  public Map<String, SdkFunctor> getRegistry() {
    return new HashMap<>(registry);
  }

  @Override
  public void register(String functorKey, SdkFunctor functorEntity) {
    if (registry.containsKey(functorKey)) {
      throw new DuplicateRegistryException(getType(), "Functor Already Registered with this key: " + functorKey);
    }
    registry.put(functorKey, functorEntity);
  }

  @Override
  public SdkFunctor obtain(String s) {
    if (registry.containsKey(s)) {
      return registry.get(s);
    }
    throw new UnregisteredKeyAccessException(getType(), "No Functor registered for key: " + s);
  }

  @Override
  public String getType() {
    return RegistryType.SDK_FUNCTOR.name();
  }
}
