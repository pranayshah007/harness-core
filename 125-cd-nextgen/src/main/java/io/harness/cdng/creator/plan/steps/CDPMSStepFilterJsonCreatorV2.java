package io.harness.cdng.creator.plan.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.CDCreatorUtils;
import io.harness.filters.GenericStepPMSFilterJsonCreatorV2;

import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class CDPMSStepFilterJsonCreatorV2 extends GenericStepPMSFilterJsonCreatorV2 {
  @Override
  public Set<String> getSupportedStepTypes() {
    return CDCreatorUtils.getSupportedStepsV2();
  }
}
