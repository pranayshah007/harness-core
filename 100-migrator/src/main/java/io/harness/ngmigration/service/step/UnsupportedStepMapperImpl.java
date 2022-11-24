/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.plancreator.steps.AbstractStepNode;

import software.wings.sm.State;
import software.wings.yaml.workflow.StepYaml;

import org.apache.commons.lang3.NotImplementedException;

public class UnsupportedStepMapperImpl implements StepMapper {
  @Override
  public String getStepType(StepYaml stepYaml) {
    throw new NotImplementedException("Unsupported step");
  }

  @Override
  public State getState(StepYaml stepYaml) {
    return null;
  }

  @Override
  public AbstractStepNode getSpec(StepYaml stepYaml) {
    throw new NotImplementedException("Unsupported step");
  }

  @Override
  public boolean areSimilar(StepYaml stepYaml1, StepYaml stepYaml2) {
    return false;
  }
}
