/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;

import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.extended.ci.container.ContainerResource;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PluginInfoProviderHelper {
  private final Integer DEFAULT_CPU_LIMIT = 250;
  private final Integer DEFAULT_MEMORY_LIMIT = 250;

  Integer getCPU(ContainerResource containerResource) {
    if (containerResource != null && ParameterField.isNotNull(containerResource.getRequests().getCpu())) {
      return Integer.parseInt(containerResource.getRequests().getCpu().getValue());
    }
    return DEFAULT_CPU_LIMIT;
  }

  Integer getMemory(ContainerResource containerResource) {
    if (containerResource != null && ParameterField.isNotNull(containerResource.getRequests().getMemory())) {
      return Integer.parseInt(containerResource.getRequests().getMemory().getValue());
    }
    return DEFAULT_MEMORY_LIMIT;
  }
}
