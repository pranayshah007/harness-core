/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import io.harness.beans.outcomes.LiteEnginePodDetailsOutcome;
import io.harness.beans.steps.StepStatusMetadata;
import io.harness.beans.steps.output.CIStageOutput;
import io.harness.beans.sweepingoutputs.ContainerPortDetails;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.DliteVmStageInfraDetails;
import io.harness.beans.sweepingoutputs.K8PodDetails;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;

import java.util.Set;

public class ContainerMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(ContainerPortDetails.class);
    set.add(LiteEnginePodDetailsOutcome.class);
    set.add(K8StageInfraDetails.class);
    set.add(StageDetails.class);
    set.add(ContextElement.class);
    set.add(VmStageInfraDetails.class);
    set.add(K8PodDetails.class);
    set.add(StageInfraDetails.class);
    set.add(DliteVmStageInfraDetails.class);
    set.add(CIStageOutput.class);
    set.add(StepStatusMetadata.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {}
}
