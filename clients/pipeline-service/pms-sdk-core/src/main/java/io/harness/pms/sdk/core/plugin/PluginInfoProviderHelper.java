/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plugin;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class PluginInfoProviderHelper {
  @Inject private DefaultPluginInfoProvider defaultPluginInfoProvider;

  public PluginCreationResponse getPluginInfo(PluginCreationRequest request) {
    // todo: if we need a registry mech for seperate plugins this acts as a orchestrator
    return defaultPluginInfoProvider.getPluginInfo(request);
  }
}
