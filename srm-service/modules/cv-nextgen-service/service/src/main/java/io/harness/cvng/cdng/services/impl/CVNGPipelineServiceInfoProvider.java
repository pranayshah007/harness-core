/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import static io.harness.pms.yaml.YAMLFieldNameConstants.SPEC;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEPS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP_GROUP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STRATEGY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cvng.cdng.beans.CVNGStepType;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.filters.EmptyAnyFilterJsonCreator;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.contracts.steps.StepMetaData;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.sdk.core.plan.creation.creators.PartialPlanCreator;
import io.harness.pms.sdk.core.plan.creation.creators.PipelineServiceInfoProvider;
import io.harness.pms.sdk.core.variables.EmptyAnyVariableCreator;
import io.harness.pms.sdk.core.variables.VariableCreator;
import io.harness.pms.utils.InjectorUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@OwnedBy(HarnessTeam.CV)
public class CVNGPipelineServiceInfoProvider implements PipelineServiceInfoProvider {
  @Inject InjectorUtils injectorUtils;
  @Override
  public List<PartialPlanCreator<?>> getPlanCreators() {
    List<PartialPlanCreator<?>> planCreators = new LinkedList<>();
    planCreators.add(new CVNGPlanCreatorV2());
    planCreators.add(new CVNGAnalyzeDeploymentPlanCreator());
    injectorUtils.injectMembers(planCreators);
    return planCreators;
  }

  @Override
  public List<FilterJsonCreator> getFilterJsonCreators() {
    List<FilterJsonCreator> filterJsonCreators = new ArrayList<>();
    filterJsonCreators.add(new CVNGStepFilterJsonCreator());
    filterJsonCreators.add(new CVNGAnalyzeDeploymentStepFilterJsonCreator());
    filterJsonCreators.add(new EmptyAnyFilterJsonCreator(Set.of(STEPS, SPEC, STRATEGY))); // ??
    injectorUtils.injectMembers(filterJsonCreators);
    return filterJsonCreators;
  }

  @Override
  public List<VariableCreator> getVariableCreators() {
    List<VariableCreator> variableCreators = new ArrayList<>();
    variableCreators.add(new CVNGStepVariableCreator());
    variableCreators.add(new CVNGAnalyzeDeploymentStepVariableCreator());
    variableCreators.add(
        new EmptyAnyVariableCreator(Set.of(YAMLFieldNameConstants.PARALLEL, STEPS, SPEC, STEP_GROUP))); // ??
    return variableCreators;
  }

  @Override
  public List<StepInfo> getStepInfo() {
    ArrayList<StepInfo> stepInfos = new ArrayList<>();
    StepInfo verification = StepInfo.newBuilder()
                                .setName(CVNGStepType.CVNG_VERIFY.getDisplayName())
                                .setType(CVNGStepType.CVNG_VERIFY.getType())
                                .setStepMetaData(StepMetaData.newBuilder()
                                                     .addCategory(CVNGStepType.CVNG_VERIFY.getFolderPath())
                                                     .addFolderPaths(CVNGStepType.CVNG_VERIFY.getFolderPath())
                                                     .build())
                                .build();
    StepInfo analyzeDeployment =
        StepInfo.newBuilder()
            .setName(CVNGStepType.CVNG_ANALYZE_DEPLOYMENT.getDisplayName())
            .setType(CVNGStepType.CVNG_ANALYZE_DEPLOYMENT.getType())
            .setFeatureFlag(FeatureName.SRM_ENABLE_ANALYZE_DEPLOYMENT_STEP.name())
            .setFeatureRestrictionName(FeatureRestrictionName.ANALYZE_DEPLOYMENT_STEP.name())
            .setStepMetaData(StepMetaData.newBuilder()
                                 .addCategory(CVNGStepType.CVNG_ANALYZE_DEPLOYMENT.getFolderPath())
                                 .addFolderPaths(CVNGStepType.CVNG_ANALYZE_DEPLOYMENT.getFolderPath())
                                 .build())
            .build();
    stepInfos.add(verification);
    stepInfos.add(analyzeDeployment);
    return stepInfos;
  }
}
