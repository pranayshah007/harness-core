/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitops.syncstep;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.steps.CDAbstractStepInfo;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(GITOPS)
@Data
@NoArgsConstructor
@JsonTypeName(StepSpecTypeConstants.GITOPS_SYNC)
@TypeAlias("SyncStepInfo")
@RecasterAlias("io.harness.cdng.gitops.syncstep.SyncStepInfo")
public class SyncStepInfo extends SyncBaseStepInfo implements CDAbstractStepInfo {
  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return delegateSelectors;
  }

  @Override
  public StepType getStepType() {
    return SyncStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.ASYNC;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return SyncStepParameters.infoBuilder()
        .delegateSelectors(getDelegateSelectors())
        .prune(prune)
        .dryRun(dryRun)
        .applicationsList(applicationsList)
        .applyOnly(applyOnly)
        .forceApply(forceApply)
        .syncOptions(syncOptions)
        .retryStrategy(retryStrategy)
        .build();
  }

  @Builder(builderMethodName = "infoBuilder")
  public SyncStepInfo(ParameterField<List<TaskSelectorYaml>> delegateSelectors, ParameterField<Boolean> prune,
      ParameterField<List<AgentApplicationTargets>> applicationsList, ParameterField<Boolean> dryRun,
      ParameterField<Boolean> applyOnly, ParameterField<Boolean> forceApply, SyncOptions syncOptions,
      SyncRetryStrategy retryStrategy) {
    super(delegateSelectors, prune, applicationsList, dryRun, applyOnly, forceApply, syncOptions, retryStrategy);
    this.prune = prune;
    this.applicationsList = applicationsList;
    this.dryRun = dryRun;
    this.applyOnly = applyOnly;
    this.forceApply = forceApply;
    this.delegateSelectors = delegateSelectors;
    this.syncOptions = syncOptions;
    this.retryStrategy = retryStrategy;
  }
}
