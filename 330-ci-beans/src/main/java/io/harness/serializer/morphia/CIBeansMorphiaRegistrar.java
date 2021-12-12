package io.harness.serializer.morphia;

import io.harness.beans.outcomes.LiteEnginePodDetailsOutcome;
import io.harness.beans.outcomes.VmDetailsOutcome;
import io.harness.beans.sweepingoutputs.ContainerPortDetails;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.K8PodDetails;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.PodCleanupDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.StepTaskDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.ci.beans.entities.BuildNumberDetails;
import io.harness.ci.beans.entities.CIBuild;
import io.harness.ci.stdvars.BuildStandardVariables;
import io.harness.ci.stdvars.GitVariables;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;

import java.util.Set;

public class CIBeansMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(BuildNumberDetails.class);
    set.add(CIBuild.class);
    set.add(K8PodDetails.class);
    set.add(StageDetails.class);
    set.add(StepTaskDetails.class);
    set.add(BuildStandardVariables.class);
    set.add(GitVariables.class);
    set.add(ContextElement.class);
    set.add(K8StageInfraDetails.class);
    set.add(VmStageInfraDetails.class);
    set.add(ContainerPortDetails.class);
    set.add(LiteEnginePodDetailsOutcome.class);
    set.add(VmDetailsOutcome.class);
    set.add(StageInfraDetails.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    w.put("sweepingoutputs.K8PodDetails", K8PodDetails.class);
    w.put("sweepingoutputs.StageDetails", StageDetails.class);
    w.put("sweepingoutputs.PodCleanupDetails", PodCleanupDetails.class);
    w.put("sweepingoutputs.StageInfraDetails", StageInfraDetails.class);
    w.put("sweepingoutputs.K8StageInfraDetails", K8StageInfraDetails.class);
    w.put("sweepingoutputs.AwsVmStageInfraDetails", VmStageInfraDetails.class);
  }
}
