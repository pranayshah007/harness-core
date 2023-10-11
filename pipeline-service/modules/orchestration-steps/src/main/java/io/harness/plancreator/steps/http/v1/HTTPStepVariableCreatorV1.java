/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.http.v1;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.pipeline.variables.v1.GenericStepVariableCreatorV1;
import io.harness.steps.StepSpecTypeConstantsV1;
import io.harness.steps.http.v1.HttpStepNodeV1;

import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class HTTPStepVariableCreatorV1 extends GenericStepVariableCreatorV1<HttpStepNodeV1> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Set.of(StepSpecTypeConstantsV1.HTTP);
  }

  @Override
  public Class<HttpStepNodeV1> getFieldClass() {
    return HttpStepNodeV1.class;
  }
}
