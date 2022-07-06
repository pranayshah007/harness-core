/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plan.creator.step;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.plan.creator.CICreatorUtils;
import io.harness.plancreator.steps.GenericStepPMSPlanCreator;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import java.util.Set;

@OwnedBy(HarnessTeam.CI)
public class CIPMSStepPlanCreator extends GenericStepPMSPlanCreator {
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public Set<String> getSupportedStepTypes() {
    return CICreatorUtils.getSupportedSteps();
  }
}
