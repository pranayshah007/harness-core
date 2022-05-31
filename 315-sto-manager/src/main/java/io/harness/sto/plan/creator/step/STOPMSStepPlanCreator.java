/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.sto.plan.creator.step;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.GenericStepPMSPlanCreator;
import io.harness.serializer.KryoSerializer;
import io.harness.sto.plan.creator.STOCreatorUtils;

import com.google.inject.Inject;
import java.util.Set;

@OwnedBy(HarnessTeam.STO)
public class STOPMSStepPlanCreator extends GenericStepPMSPlanCreator {
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public Set<String> getSupportedStepTypes() {
    return STOCreatorUtils.getSupportedSteps();
  }
}
