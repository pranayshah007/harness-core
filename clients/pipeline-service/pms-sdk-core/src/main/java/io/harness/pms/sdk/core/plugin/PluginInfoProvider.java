/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plugin;

import io.harness.pms.contracts.plan.PluginCreationBatchRequest;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginCreationResponseList;
import io.harness.pms.contracts.plan.PluginCreationResponseV2;

public interface PluginInfoProvider {
  PluginCreationResponseV2 getPluginInfo(PluginCreationRequest request);

  default PluginCreationResponseList getPluginInfoList(PluginCreationRequest request) {
    return null;
  }

  boolean isSupported(String stepType);

  default boolean willReturnMultipleContainers() {
    return false;
  }
}
