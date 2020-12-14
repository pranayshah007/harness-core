package io.harness.pms.sample.cd.creator;

import io.harness.plancreator.execution.ExecutionPMSPlanCreator;
import io.harness.plancreator.pipeline.NGPipelinePlanCreator;
import io.harness.plancreator.stages.StagesPlanCreator;
import io.harness.plancreator.stages.parallel.ParallelPlanCreator;
import io.harness.plancreator.steps.StepGroupPMSPlanCreator;
import io.harness.pms.sample.cd.creator.filters.DeploymentStageFilterCreator;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.sdk.core.pipeline.filters.ParallelFilterJsonCreator;
import io.harness.pms.sdk.core.pipeline.filters.PipelineFilterJsonCreator;
import io.harness.pms.sdk.core.plan.creation.creators.PartialPlanCreator;
import io.harness.pms.sdk.core.plan.creation.creators.PipelineServiceInfoProvider;
import io.harness.pms.steps.StepInfo;
import io.harness.pms.steps.StepMetaData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CdPipelineServiceInfoProvider implements PipelineServiceInfoProvider {
  @Override
  public List<PartialPlanCreator<?>> getPlanCreators() {
    List<PartialPlanCreator<?>> planCreators = new ArrayList<>();
    planCreators.add(new NGPipelinePlanCreator());
    planCreators.add(new StagesPlanCreator());
    planCreators.add(new DeploymentStagePlanCreator());
    planCreators.add(new CdStepPlanCreator());
    planCreators.add(new ParallelPlanCreator());
    planCreators.add(new StepGroupPMSPlanCreator());
    planCreators.add(new ExecutionPMSPlanCreator());
    return planCreators;
  }

  @Override
  public List<FilterJsonCreator> getFilterJsonCreators() {
    List<FilterJsonCreator> filterJsonCreators = new ArrayList<>();
    filterJsonCreators.add(new PipelineFilterJsonCreator());
    filterJsonCreators.add(new ParallelFilterJsonCreator());
    filterJsonCreators.add(new DeploymentStageFilterCreator());

    return filterJsonCreators;
  }

  @Override
  public List<StepInfo> getStepInfo() {
    StepInfo stepInfo =
        StepInfo.newBuilder()
            .setName("Kubernetes")
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Kubernetes").setFolderPath("Kubernetes").build())
            .build();
    return Collections.singletonList(stepInfo);
  }
}
