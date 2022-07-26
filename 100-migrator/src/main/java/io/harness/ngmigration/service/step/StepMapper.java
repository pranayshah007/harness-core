/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.data.structure.CollectionUtils;
import io.harness.yaml.core.StepSpecType;

import software.wings.yaml.workflow.StepYaml;

import java.util.Map;

public interface StepMapper {
  String getStepType();

  StepSpecType getSpec(StepYaml stepYaml);

  default String getTimeout(StepYaml stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    return properties.getOrDefault("stateTimeoutInMinutes", "10") + "m";
  }

  default Map<String, Object> getProperties(StepYaml stepYaml) {
    return CollectionUtils.emptyIfNull(stepYaml.getProperties());
  }
}
