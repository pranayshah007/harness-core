/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plan.creation.creators;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.sdk.core.variables.VariableCreator;
import io.harness.pms.utils.InjectorUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class PipelineServiceInfoDecoratorImpl implements PipelineServiceInfoDecorator {
  @Inject PipelineServiceInfoProvider pipelineServiceInfoProvider;

  @Inject InjectorUtils injectorUtils;

  @Override
  public List<PartialPlanCreator<?>> getPlanCreators() {
    List<PartialPlanCreator<?>> partialPlanCreators = new ArrayList<>();
    List<PartialPlanCreator<?>> commonPlanCreators = getCommonPlanCreators();
    injectorUtils.injectMembers(commonPlanCreators);

    partialPlanCreators.addAll(pipelineServiceInfoProvider.getPlanCreators());
    partialPlanCreators.addAll(commonPlanCreators);

    return partialPlanCreators;
  }

  @Override
  public List<FilterJsonCreator> getFilterJsonCreators() {
    List<FilterJsonCreator> filterJsonCreators = new ArrayList<>();
    List<FilterJsonCreator> commonFilterJsonCreators = getCommonFilterJsonCreators();
    injectorUtils.injectMembers(commonFilterJsonCreators);

    filterJsonCreators.addAll(pipelineServiceInfoProvider.getFilterJsonCreators());
    filterJsonCreators.addAll(commonFilterJsonCreators);

    return filterJsonCreators;
  }

  @Override
  public List<VariableCreator> getVariableCreators() {
    List<VariableCreator> variableCreators = new ArrayList<>();
    List<VariableCreator> commonVariableCreators = getCommonVariableCreators();
    injectorUtils.injectMembers(commonVariableCreators);

    variableCreators.addAll(pipelineServiceInfoProvider.getVariableCreators());
    variableCreators.addAll(commonVariableCreators);
    return variableCreators;
  }

  @Override
  public List<StepInfo> getStepInfo() {
    return pipelineServiceInfoProvider.getStepInfo();
  }
}
