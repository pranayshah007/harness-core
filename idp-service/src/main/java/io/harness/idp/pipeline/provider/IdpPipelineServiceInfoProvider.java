/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.pipeline.provider;

import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;
import static io.harness.steps.plugin.ContainerStepConstants.PLUGIN;

import io.harness.ci.creator.variables.GitCloneStepVariableCreator;
import io.harness.ci.creator.variables.PluginStepVariableCreator;
import io.harness.ci.creator.variables.RunStepVariableCreator;
import io.harness.ci.plancreator.GitCloneStepPlanCreator;
import io.harness.ci.plancreator.InitializeStepPlanCreator;
import io.harness.ci.plancreator.PluginStepPlanCreator;
import io.harness.ci.plancreator.RunStepPlanCreator;
import io.harness.idp.pipeline.stages.filtercreator.IDPStageFilterCreator;
import io.harness.idp.pipeline.stages.filtercreator.IDPStepFilterJsonCreator;
import io.harness.idp.pipeline.stages.plancreator.IDPStagePlanCreator;
import io.harness.idp.pipeline.stages.plancreator.IDPStepPlanCreator;
import io.harness.idp.pipeline.stages.variablecreator.IDPStageVariableCreator;
import io.harness.idp.pipeline.stages.variablecreator.IDPStepVariableCreator;
import io.harness.idp.pipeline.step.StepSpecTypeConstants;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.contracts.steps.StepMetaData;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.sdk.core.plan.creation.creators.PartialPlanCreator;
import io.harness.pms.sdk.core.plan.creation.creators.PipelineServiceInfoProvider;
import io.harness.pms.sdk.core.variables.EmptyVariableCreator;
import io.harness.pms.sdk.core.variables.VariableCreator;
import io.harness.pms.utils.InjectorUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class IdpPipelineServiceInfoProvider implements PipelineServiceInfoProvider {
  @Inject InjectorUtils injectorUtils;
  private static final String LITE_ENGINE_TASK = "liteEngineTask";

  @Override
  public List<PartialPlanCreator<?>> getPlanCreators() {
    // Needs to be modified based on steps
    List<PartialPlanCreator<?>> planCreators = new LinkedList<>();
    planCreators.add(new IDPStagePlanCreator());
    planCreators.add(new IDPStepPlanCreator());
    planCreators.add(new RunStepPlanCreator());
    planCreators.add(new PluginStepPlanCreator());
    planCreators.add(new GitCloneStepPlanCreator());
    planCreators.add(new InitializeStepPlanCreator());
    injectorUtils.injectMembers(planCreators);
    return planCreators;
  }

  @Override
  public List<FilterJsonCreator> getFilterJsonCreators() {
    // Needs to be modified based on steps
    List<FilterJsonCreator> filterJsonCreators = new ArrayList<>();
    filterJsonCreators.add(new IDPStageFilterCreator());
    filterJsonCreators.add(new IDPStepFilterJsonCreator());
    injectorUtils.injectMembers(filterJsonCreators);
    return filterJsonCreators;
  }

  @Override
  public List<VariableCreator> getVariableCreators() {
    // Needs to be modified based on steps
    List<VariableCreator> variableCreators = new ArrayList<>();
    variableCreators.add(new IDPStageVariableCreator());
    variableCreators.add(new IDPStepVariableCreator());
    variableCreators.add(new RunStepVariableCreator());
    variableCreators.add(new PluginStepVariableCreator());
    variableCreators.add(new GitCloneStepVariableCreator());
    variableCreators.add(new EmptyVariableCreator(STEP, Set.of(LITE_ENGINE_TASK)));

    return variableCreators;
  }

  @Override
  public List<StepInfo> getStepInfo() {
    // Needs to be modified based on steps
    StepInfo runStepInfo = StepInfo.newBuilder()
                               .setName("Run")
                               .setType(StepSpecTypeConstants.RUN)
                               .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Build").build())
                               .build();

    StepInfo pluginStepInfo = StepInfo.newBuilder()
                                  .setName("Plugin")
                                  .setType(StepSpecTypeConstants.PLUGIN)
                                  .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Build").build())
                                  .build();

    StepInfo gitCloneStepInfo =
        StepInfo.newBuilder()
            .setName("Git Clone")
            .setType(StepSpecTypeConstants.GIT_CLONE)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Build").build())
            .build();

    ArrayList<StepInfo> stepInfos = new ArrayList<>();
    stepInfos.add(runStepInfo);
    stepInfos.add(pluginStepInfo);
    stepInfos.add(gitCloneStepInfo);
    return stepInfos;
  }
}
