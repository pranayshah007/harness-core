/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.registries;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.governance.JsonExpansionHandler;
import io.harness.registries.Registry;
import io.harness.registries.exceptions.DuplicateRegistryException;
import io.harness.registries.exceptions.UnregisteredKeyAccessException;

import com.google.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@OwnedBy(PIPELINE)
public class JsonExpansionHandlerRegistry implements Registry<String, JsonExpansionHandler> {
  Map<String, JsonExpansionHandler> registry = new ConcurrentHashMap<>();

  @Override
  public void register(String registryKey, JsonExpansionHandler expansionHandler) {
    if (registry.containsKey(registryKey)) {
      throw new DuplicateRegistryException(
          getType(), "Json Expansion Handler already registered for key " + registryKey);
    }
    registry.put(registryKey, expansionHandler);
  }

  @Override
  public JsonExpansionHandler obtain(String registryKey) {
    if (registry.containsKey(registryKey)) {
      return registry.get(registryKey);
    }
    throw new UnregisteredKeyAccessException(getType(), "No Json Expansion Handler registered for key: " + registryKey);
  }

  @Override
  public String getType() {
    return RegistryType.JSON_EXPANSION_HANDLERS.name();
  }
}
