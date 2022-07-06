package io.harness.serializer.morphia;

import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;
import io.harness.steps.barriers.BarrierSpecParameters;
import io.harness.steps.barriers.beans.BarrierOutcome;
import io.harness.steps.cf.FlagConfigurationStepParameters;
import io.harness.steps.resourcerestraint.ResourceRestraintSpecParameters;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintOutcome;

import java.util.Set;

public class OrchestrationStepsContractMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    // Nothing to register
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    h.put("steps.barriers.BarrierSpecParameters", BarrierSpecParameters.class);
    h.put("steps.barriers.beans.BarrierOutcome", BarrierOutcome.class);
    h.put("steps.resourcerestraint.ResourceRestraintSpecParameters", ResourceRestraintSpecParameters.class);
    h.put("steps.resourcerestraint.beans.ResourceRestraintOutcome", ResourceRestraintOutcome.class);

    // Feature Flag
    h.put("steps.cf.FlagConfigurationStepParameters", FlagConfigurationStepParameters.class);
  }
}
