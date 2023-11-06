/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plan.creation.creators;

import io.harness.filters.EmptyAnyFilterJsonCreator;
import io.harness.filters.ExecutionPMSFilterJsonCreator;
import io.harness.filters.ParallelGenericFilterJsonCreator;
import io.harness.filters.StepGroupPmsFilterJsonCreator;
import io.harness.plancreator.execution.ExecutionPmsPlanCreator;
import io.harness.plancreator.stages.parallel.ParallelPlanCreator;
import io.harness.plancreator.stages.parallel.v1.ParallelPlanCreatorV1;
import io.harness.plancreator.steps.NGStageStepsPlanCreator;
import io.harness.plancreator.steps.SpecNodePlanCreator;
import io.harness.plancreator.steps.StepGroupPMSPlanCreator;
import io.harness.plancreator.steps.v1.SpecPlanCreatorV1;
import io.harness.plancreator.steps.v1.StepsPlanCreatorV1;
import io.harness.plancreator.strategy.StrategyConfigPlanCreator;
import io.harness.plancreator.strategy.v1.StrategyConfigPlanCreatorV1;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.sdk.core.pipeline.variables.StepGroupVariableCreator;
import io.harness.pms.sdk.core.variables.EmptyAnyVariableCreator;
import io.harness.pms.sdk.core.variables.StrategyVariableCreator;
import io.harness.pms.sdk.core.variables.VariableCreator;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.variables.ExecutionVariableCreator;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;

public interface PipelineServiceInfoDecorator extends PipelineServiceInfoProvider {
  default List<PartialPlanCreator<?>> getCommonPlanCreators() {
    List<PartialPlanCreator<?>> planCreators = new ArrayList<>();

    planCreators.add(new ParallelPlanCreator());
    planCreators.add(new ParallelPlanCreatorV1());
    planCreators.add(new StepsPlanCreatorV1());
    planCreators.add(new NGStageStepsPlanCreator());
    planCreators.add(new SpecPlanCreatorV1());
    planCreators.add(new ExecutionPmsPlanCreator());
    planCreators.add(new StepGroupPMSPlanCreator());
    planCreators.add(new StrategyConfigPlanCreator());
    planCreators.add(new StrategyConfigPlanCreatorV1());
    planCreators.add(new SpecNodePlanCreator());

    return planCreators;
  }

  default List<FilterJsonCreator> getCommonFilterJsonCreators() {
    List<FilterJsonCreator> filterJsonCreators = new ArrayList<>();

    filterJsonCreators.add(new ExecutionPMSFilterJsonCreator());
    filterJsonCreators.add(new StepGroupPmsFilterJsonCreator());
    filterJsonCreators.add(new ParallelGenericFilterJsonCreator());
    filterJsonCreators.add(new EmptyAnyFilterJsonCreator(Sets.newHashSet(YAMLFieldNameConstants.STRATEGY,
        YAMLFieldNameConstants.STEPS, YAMLFieldNameConstants.SPEC, YAMLFieldNameConstants.STAGES)));

    return filterJsonCreators;
  }

  default List<VariableCreator> getCommonVariableCreators() {
    List<VariableCreator> variableCreators = new ArrayList<>();
    variableCreators.add(new StrategyVariableCreator());
    variableCreators.add(new ExecutionVariableCreator());
    variableCreators.add(new StepGroupVariableCreator());
    variableCreators.add(new EmptyAnyVariableCreator(Sets.newHashSet(YAMLFieldNameConstants.STEPS,
        YAMLFieldNameConstants.SPEC, YAMLFieldNameConstants.PARALLEL, YAMLFieldNameConstants.STAGES)));

    return variableCreators;
  }
}
