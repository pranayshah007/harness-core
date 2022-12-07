/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.sto.plan.creator;

import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEPS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.steps.StepSpecTypeConstants;
import io.harness.ci.creator.variables.RunStepVariableCreator;
import io.harness.ci.creator.variables.STOCommonStepVariableCreator;
import io.harness.ci.creator.variables.STOStageVariableCreator;
import io.harness.ci.creator.variables.STOStepVariableCreator;
import io.harness.ci.creator.variables.SecurityStepVariableCreator;
import io.harness.ci.plancreator.RunStepPlanCreator;
import io.harness.ci.plancreator.SecurityStepPlanCreator;
import io.harness.filters.EmptyAnyFilterJsonCreator;
import io.harness.filters.ExecutionPMSFilterJsonCreator;
import io.harness.filters.ParallelGenericFilterJsonCreator;
import io.harness.plancreator.execution.ExecutionPmsPlanCreator;
import io.harness.plancreator.stages.parallel.ParallelPlanCreator;
import io.harness.plancreator.steps.NGStageStepsPlanCreator;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.contracts.steps.StepMetaData;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.sdk.core.plan.creation.creators.PartialPlanCreator;
import io.harness.pms.sdk.core.plan.creation.creators.PipelineServiceInfoProvider;
import io.harness.pms.sdk.core.variables.EmptyAnyVariableCreator;
import io.harness.pms.sdk.core.variables.EmptyVariableCreator;
import io.harness.pms.sdk.core.variables.VariableCreator;
import io.harness.pms.utils.InjectorUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.sto.STOStepType;
import io.harness.sto.plan.creator.filter.STOStageFilterJsonCreator;
import io.harness.sto.plan.creator.stage.SecurityStagePMSPlanCreator;
import io.harness.sto.plan.creator.step.STOPMSStepFilterJsonCreator;
import io.harness.sto.plan.creator.step.STOPMSStepPlanCreator;
import io.harness.sto.plan.creator.step.STOStepFilterJsonCreatorV2;
import io.harness.variables.ExecutionVariableCreator;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@OwnedBy(HarnessTeam.STO)
public class STOPipelineServiceInfoProvider implements PipelineServiceInfoProvider {
  private static final String TEST = "Test";
  private static final String PUBLISH_ARTIFACTS = "PublishArtifacts";
  private static final String LITE_ENGINE_TASK = "liteEngineTask";
  private static final String SAVE_CACHE = "SaveCache";
  private static final String CLEANUP = "Cleanup";

  @Inject InjectorUtils injectorUtils;

  @Override
  public List<PartialPlanCreator<?>> getPlanCreators() {
    List<PartialPlanCreator<?>> planCreators = new LinkedList<>();
    planCreators.add(new SecurityStagePMSPlanCreator());
    planCreators.add(new STOPMSStepPlanCreator());

    planCreators.addAll(
        Arrays.asList(STOStepType.values()).stream().map(e -> e.getPlanCreator()).collect(Collectors.toList()));

    planCreators.add(new RunStepPlanCreator());
    planCreators.add(new SecurityStepPlanCreator());
    planCreators.add(new NGStageStepsPlanCreator());
    planCreators.add(new ExecutionPmsPlanCreator());
    planCreators.add(new ParallelPlanCreator());
    injectorUtils.injectMembers(planCreators);
    return planCreators;
  }

  @Override
  public List<FilterJsonCreator> getFilterJsonCreators() {
    List<FilterJsonCreator> filterJsonCreators = new ArrayList<>();
    filterJsonCreators.add(new STOStageFilterJsonCreator());
    filterJsonCreators.add(new STOPMSStepFilterJsonCreator());
    filterJsonCreators.add(new STOStepFilterJsonCreatorV2());
    filterJsonCreators.add(new ExecutionPMSFilterJsonCreator());
    filterJsonCreators.add(new ParallelGenericFilterJsonCreator());
    filterJsonCreators.add(new EmptyAnyFilterJsonCreator(Set.of(STEPS)));
    injectorUtils.injectMembers(filterJsonCreators);

    return filterJsonCreators;
  }

  @Override
  public List<VariableCreator> getVariableCreators() {
    List<VariableCreator> variableCreators = new ArrayList<>();
    variableCreators.add(new STOStageVariableCreator());
    variableCreators.add(new ExecutionVariableCreator());
    variableCreators.add(new STOStepVariableCreator());

    variableCreators.add(new STOCommonStepVariableCreator());
    variableCreators.add(new RunStepVariableCreator());
    variableCreators.add(new SecurityStepVariableCreator());
    variableCreators.add(new EmptyAnyVariableCreator(Set.of(YAMLFieldNameConstants.PARALLEL, STEPS)));
    variableCreators.add(
        new EmptyVariableCreator(STEP, Set.of(TEST, PUBLISH_ARTIFACTS, LITE_ENGINE_TASK, SAVE_CACHE, CLEANUP)));

    return variableCreators;
  }

  private StepInfo createStepInfo(STOStepType stoStepType, String stepCategory) {
    return StepInfo.newBuilder()
        .setName(stoStepType.getName())
        .setType(stoStepType.getName())
        .setFeatureFlag(stoStepType.getFeatureName().name())
        .setStepMetaData(StepMetaData.newBuilder().addFolderPaths(stepCategory).build())
        .build();
  }

  @Override
  public List<StepInfo> getStepInfo() {
    StepInfo securityStepInfo = StepInfo.newBuilder()
                                    .setName("Security")
                                    .setType(StepSpecTypeConstants.SECURITY)
                                    .setFeatureFlag(FeatureName.SECURITY.name())
                                    .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Security").build())
                                    .build();
    StepInfo runStepInfo = StepInfo.newBuilder()
                               .setName("Run")
                               .setType(StepSpecTypeConstants.RUN)
                               .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Build").build())
                               .build();

    List<StepInfo> stepInfos = new ArrayList<>();

    stepInfos.add(securityStepInfo);
    stepInfos.add(runStepInfo);
    Arrays.asList(STOStepType.values())
        .forEach(e -> e.getStepCategories().forEach(category -> stepInfos.add(createStepInfo(e, category))));

    return stepInfos;
  }
}
