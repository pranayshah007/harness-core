/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.sm.StepType;

import com.google.inject.Inject;
import com.google.inject.Injector;
import lombok.extern.slf4j.Slf4j;

@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class StepYamlBuilderFactory {
  @Inject private Injector injector;

  public StepYamlBuilder getStepYamlBuilderForStepType(StepType stepType) {
    StepYamlBuilder validator = null;
    try {
      if (stepType.getYamlValidatorClass() != null) {
        validator = injector.getInstance(stepType.getYamlValidatorClass());
      } else {
        log.debug("Can not get step yaml builder for step type {} as the YAML validator class is null", stepType);
      }
    } catch (Exception e) {
      log.warn("Can not get step yaml builder for step type" + stepType.getType(), e);
    }
    return validator;
  }
}
