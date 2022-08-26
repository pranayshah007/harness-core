/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.cdng.k8s.K8sRollingStepInfo;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.StepSpecType;

import software.wings.yaml.workflow.StepYaml;

public class K8sRollingStepMapperImpl implements StepMapper {
  @Override
  public String getStepType() {
    return null;
  }

  @Override
  public StepSpecType getSpec(StepYaml stepYaml) {
    return K8sRollingStepInfo.infoBuilder().skipDryRun(ParameterField.createValueField(false)).build();
  }
}
