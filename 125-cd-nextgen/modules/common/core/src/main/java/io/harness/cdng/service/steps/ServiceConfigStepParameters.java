/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.service.steps;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.service.beans.ServiceUseFromStage;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
@TypeAlias("serviceConfigStepParameters")
@RecasterAlias("io.harness.cdng.service.steps.ServiceConfigStepParameters")
public class ServiceConfigStepParameters implements StepParameters {
  ServiceUseFromStage useFromStage;
  ParameterField<String> serviceRef;

  String childNodeId;

  @Override
  public List<String> excludeKeysFromStepInputs() {
    return new LinkedList<>(Arrays.asList("childNodeId"));
  }
}
