/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.hooks.mapper;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.hooks.ServiceHookOutcome;
import io.harness.cdng.hooks.ServiceHookType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;

import com.google.inject.Singleton;
import java.util.List;

@Singleton
@OwnedBy(CDP)
public final class ServiceHookOutcomeMapper {
  private ServiceHookOutcomeMapper() {}

  public static ServiceHookOutcome toServiceHookOutcome(
      String identifier, ServiceHookType type, List<String> actions, StoreConfig store, int order) {
    return ServiceHookOutcome.builder()
        .identifier(identifier)
        .actions(actions)
        .type(type)
        .store(store)
        .order(order)
        .build();
  }
}
