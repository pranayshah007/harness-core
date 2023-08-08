/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import io.harness.app.beans.dto.CITaskDetails;
import io.harness.app.beans.entities.ExecutionQueueLimit;
import io.harness.app.beans.entities.PluginMetadataConfig;
import io.harness.app.beans.entities.PluginMetadataStatus;
import io.harness.beans.outcomes.VmDetailsOutcome;
import io.harness.beans.steps.StepStatusMetadata;
import io.harness.beans.steps.output.CIStageOutput;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.DliteVmStageInfraDetails;
import io.harness.beans.sweepingoutputs.K8PodDetails;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.PodCleanupDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.StepTaskDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.ci.beans.entities.BuildNumberDetails;
import io.harness.ci.beans.entities.CIBuild;
import io.harness.ci.beans.entities.CIExecutionConfig;
import io.harness.ci.beans.entities.CITelemetrySentStatus;
import io.harness.ci.execution.CIExecutionMetadata;
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
    set.add(CIExecutionConfig.class);
    set.add(ExecutionQueueLimit.class);
    set.add(CITaskDetails.class);
    set.add(K8PodDetails.class);
    set.add(StageDetails.class);
    set.add(StepTaskDetails.class);
    set.add(BuildStandardVariables.class);
    set.add(GitVariables.class);
    set.add(ContextElement.class);
    set.add(K8StageInfraDetails.class);
    set.add(VmStageInfraDetails.class);
    set.add(VmDetailsOutcome.class);
    set.add(CITelemetrySentStatus.class);
    set.add(DliteVmStageInfraDetails.class);
    set.add(CIExecutionMetadata.class);
    set.add(PluginMetadataConfig.class);
    set.add(PluginMetadataStatus.class);
    set.add(CIStageOutput.class);
    set.add(StepStatusMetadata.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    w.put("sweepingoutputs.K8PodDetails", K8PodDetails.class);
    w.put("sweepingoutputs.StageDetails", StageDetails.class);
    w.put("sweepingoutputs.PodCleanupDetails", PodCleanupDetails.class);
    w.put("sweepingoutputs.K8StageInfraDetails", K8StageInfraDetails.class);
    w.put("sweepingoutputs.AwsVmStageInfraDetails", VmStageInfraDetails.class);
  }
}
