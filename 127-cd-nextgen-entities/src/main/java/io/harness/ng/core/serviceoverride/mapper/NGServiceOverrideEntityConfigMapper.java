/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverride.mapper;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideInfoConfig;
import io.harness.utils.YamlPipelineUtils;
import io.harness.yaml.core.variables.NGVariable;

import java.io.IOException;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(CDC)
@UtilityClass
public class NGServiceOverrideEntityConfigMapper {
  public String toYaml(NGServiceOverrideConfig serviceOverrideConfig) {
    try {
      return YamlPipelineUtils.getYamlString(serviceOverrideConfig);
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create Service Override entity due to " + e.getMessage());
    }
  }

  public NGServiceOverrideConfig toNGServiceOverrideConfig(NGServiceOverridesEntity serviceOverridesEntity) {
    List<NGVariable> variableOverride = null;
    if (isNotEmpty(serviceOverridesEntity.getYaml())) {
      try {
        final NGServiceOverrideConfig config =
            YamlPipelineUtils.read(serviceOverridesEntity.getYaml(), NGServiceOverrideConfig.class);
        variableOverride = config.getServiceOverrideInfoConfig().getVariables();
      } catch (IOException e) {
        throw new InvalidRequestException("Cannot create service ng service config due to " + e.getMessage());
      }
    }
    return NGServiceOverrideConfig.builder()
        .serviceOverrideInfoConfig(NGServiceOverrideInfoConfig.builder()
                                       .environmentRef(serviceOverridesEntity.getEnvironmentRef())
                                       .serviceRef(serviceOverridesEntity.getServiceRef())
                                       .variables(variableOverride)
                                       .build())
        .build();
  }
}
