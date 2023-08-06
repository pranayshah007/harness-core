/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plan.creator;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;
import static io.harness.ssca.SscaBeansRegistrar.sscaStepPaletteSteps;
import static io.harness.steps.plugin.ContainerStepConstants.PLUGIN;

import io.harness.ModuleType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.steps.StepSpecTypeConstants;
import io.harness.ci.creator.variables.ActionStepVariableCreator;
import io.harness.ci.creator.variables.ArtifactoryUploadStepVariableCreator;
import io.harness.ci.creator.variables.BackgroundStepVariableCreator;
import io.harness.ci.creator.variables.BuildAndPushACRStepVariableCreator;
import io.harness.ci.creator.variables.BuildAndPushECRStepVariableCreator;
import io.harness.ci.creator.variables.BuildAndPushGCRStepVariableCreator;
import io.harness.ci.creator.variables.CIStageVariableCreator;
import io.harness.ci.creator.variables.CIStepVariableCreator;
import io.harness.ci.creator.variables.DockerStepVariableCreator;
import io.harness.ci.creator.variables.GCSUploadStepVariableCreator;
import io.harness.ci.creator.variables.GitCloneStepVariableCreator;
import io.harness.ci.creator.variables.PluginStepVariableCreator;
import io.harness.ci.creator.variables.RestoreCacheGCSStepVariableCreator;
import io.harness.ci.creator.variables.RestoreCacheS3StepVariableCreator;
import io.harness.ci.creator.variables.RunStepVariableCreator;
import io.harness.ci.creator.variables.RunTestStepVariableCreator;
import io.harness.ci.creator.variables.S3UploadStepVariableCreator;
import io.harness.ci.creator.variables.SaveCacheGCSStepVariableCreator;
import io.harness.ci.creator.variables.SaveCacheS3StepVariableCreator;
import io.harness.ci.creator.variables.SecurityStepVariableCreator;
import io.harness.ci.plan.creator.filter.CIStageFilterJsonCreatorV2;
import io.harness.ci.plan.creator.stage.IntegrationStagePMSPlanCreatorV2;
import io.harness.ci.plan.creator.stage.V3.IntegrationStagePMSPlanCreatorV3;
import io.harness.ci.plan.creator.step.CIPMSStepFilterJsonCreator;
import io.harness.ci.plan.creator.step.CIPMSStepPlanCreator;
import io.harness.ci.plan.creator.step.CIStepFilterJsonCreatorV2;
import io.harness.ci.plan.creator.steps.CIStepsPlanCreator;
import io.harness.ci.plancreator.ActionStepPlanCreator;
import io.harness.ci.plancreator.ArtifactoryUploadStepPlanCreator;
import io.harness.ci.plancreator.BackgroundStepPlanCreator;
import io.harness.ci.plancreator.BitriseStepPlanCreator;
import io.harness.ci.plancreator.BuildAndPushACRStepPlanCreator;
import io.harness.ci.plancreator.BuildAndPushECRStepPlanCreator;
import io.harness.ci.plancreator.BuildAndPushGCRStepPlanCreator;
import io.harness.ci.plancreator.DockerStepPlanCreator;
import io.harness.ci.plancreator.GCSUploadStepPlanCreator;
import io.harness.ci.plancreator.GitCloneStepPlanCreator;
import io.harness.ci.plancreator.InitializeStepPlanCreator;
import io.harness.ci.plancreator.PluginStepPlanCreator;
import io.harness.ci.plancreator.RestoreCacheGCSStepPlanCreator;
import io.harness.ci.plancreator.RestoreCacheS3StepPlanCreator;
import io.harness.ci.plancreator.RunStepPlanCreator;
import io.harness.ci.plancreator.RunTestStepPlanCreator;
import io.harness.ci.plancreator.S3UploadStepPlanCreator;
import io.harness.ci.plancreator.SaveCacheGCSStepPlanCreator;
import io.harness.ci.plancreator.SaveCacheS3StepPlanCreator;
import io.harness.ci.plancreator.SecurityStepPlanCreator;
import io.harness.ci.plancreator.V1.ActionStepPlanCreatorV1;
import io.harness.ci.plancreator.V1.BackgroundStepPlanCreatorV1;
import io.harness.ci.plancreator.V1.BitriseStepPlanCreatorV1;
import io.harness.ci.plancreator.V1.PluginStepPlanCreatorV1;
import io.harness.ci.plancreator.V1.RunStepPlanCreatorV1;
import io.harness.ci.plancreator.V1.TestStepPlanCreator;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.contracts.steps.StepMetaData;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.sdk.core.pipeline.variables.StepGroupVariableCreator;
import io.harness.pms.sdk.core.plan.creation.creators.PartialPlanCreator;
import io.harness.pms.sdk.core.plan.creation.creators.PipelineServiceInfoProvider;
import io.harness.pms.sdk.core.variables.EmptyVariableCreator;
import io.harness.pms.sdk.core.variables.VariableCreator;
import io.harness.pms.utils.InjectorUtils;
import io.harness.ssca.execution.creator.filter.SscaStepsFilterJsonCreator;
import io.harness.ssca.execution.creator.plan.SscaEnforcementStepPlanCreator;
import io.harness.ssca.execution.creator.plan.SscaOrchestrationStepPlanCreator;
import io.harness.ssca.execution.creator.variable.SscaStepVariableCreator;
import io.harness.sto.STOStepType;
import io.harness.sto.creator.variables.STOCommonStepVariableCreator;
import io.harness.sto.plan.creator.step.STOStepFilterJsonCreatorV2;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
@OwnedBy(HarnessTeam.CI)
public class CIPipelineServiceInfoProvider implements PipelineServiceInfoProvider {
  private static final String LITE_ENGINE_TASK = "liteEngineTask";

  @Inject InjectorUtils injectorUtils;
  @Override
  public List<PartialPlanCreator<?>> getPlanCreators() {
    List<PartialPlanCreator<?>> planCreators = new LinkedList<>();
    planCreators.add(new IntegrationStagePMSPlanCreatorV2());
    planCreators.add(new CIPMSStepPlanCreator());
    planCreators.add(new RunStepPlanCreator());
    planCreators.add(new BackgroundStepPlanCreator());
    planCreators.add(new RunTestStepPlanCreator());
    planCreators.add(new S3UploadStepPlanCreator());
    planCreators.add(new SaveCacheGCSStepPlanCreator());
    planCreators.add(new GCSUploadStepPlanCreator());
    planCreators.add(new RestoreCacheGCSStepPlanCreator());
    planCreators.add(new RestoreCacheS3StepPlanCreator());
    planCreators.add(new PluginStepPlanCreator());
    planCreators.add(new DockerStepPlanCreator());
    planCreators.add(new ArtifactoryUploadStepPlanCreator());
    planCreators.add(new BuildAndPushECRStepPlanCreator());
    planCreators.add(new BuildAndPushACRStepPlanCreator());
    planCreators.add(new BuildAndPushGCRStepPlanCreator());
    planCreators.add(new SaveCacheS3StepPlanCreator());
    planCreators.add(new SecurityStepPlanCreator());
    planCreators.add(new GitCloneStepPlanCreator());
    planCreators.add(new InitializeStepPlanCreator());
    planCreators.add(new ActionStepPlanCreator());
    planCreators.add(new BitriseStepPlanCreator());
    planCreators.add(new SscaOrchestrationStepPlanCreator());
    planCreators.add(new SscaEnforcementStepPlanCreator());

    // add V1 plan creators
    planCreators.add(new IntegrationStagePMSPlanCreatorV3());
    planCreators.add(new CIStepsPlanCreator());
    planCreators.add(new RunStepPlanCreatorV1());
    planCreators.add(new PluginStepPlanCreatorV1());
    planCreators.add(new TestStepPlanCreator());
    planCreators.add(new BackgroundStepPlanCreatorV1());
    planCreators.add(new BitriseStepPlanCreatorV1());
    planCreators.add(new ActionStepPlanCreatorV1());

    // Add STO Steps plan creators
    planCreators.addAll(STOStepType.getPlanCreators());

    injectorUtils.injectMembers(planCreators);
    return planCreators;
  }

  @Override
  public List<FilterJsonCreator> getFilterJsonCreators() {
    List<FilterJsonCreator> filterJsonCreators = new ArrayList<>();
    filterJsonCreators.add(new CIPMSStepFilterJsonCreator());
    filterJsonCreators.add(new CIStepFilterJsonCreatorV2());
    filterJsonCreators.add(new CIStageFilterJsonCreatorV2());
    filterJsonCreators.add(new STOStepFilterJsonCreatorV2());
    filterJsonCreators.add(new SscaStepsFilterJsonCreator());

    injectorUtils.injectMembers(filterJsonCreators);

    return filterJsonCreators;
  }

  @Override
  public List<VariableCreator> getVariableCreators() {
    List<VariableCreator> variableCreators = new ArrayList<>();
    variableCreators.add(new CIStageVariableCreator());
    variableCreators.add(new StepGroupVariableCreator());
    variableCreators.add(new CIStepVariableCreator());
    variableCreators.add(new RunStepVariableCreator());
    variableCreators.add(new BackgroundStepVariableCreator());
    variableCreators.add(new RunTestStepVariableCreator());
    variableCreators.add(new S3UploadStepVariableCreator());
    variableCreators.add(new SaveCacheGCSStepVariableCreator());
    variableCreators.add(new GCSUploadStepVariableCreator());
    variableCreators.add(new RestoreCacheGCSStepVariableCreator());
    variableCreators.add(new RestoreCacheS3StepVariableCreator());
    variableCreators.add(new PluginStepVariableCreator());
    variableCreators.add(new DockerStepVariableCreator());
    variableCreators.add(new ArtifactoryUploadStepVariableCreator());
    variableCreators.add(new BuildAndPushECRStepVariableCreator());
    variableCreators.add(new BuildAndPushACRStepVariableCreator());
    variableCreators.add(new BuildAndPushGCRStepVariableCreator());
    variableCreators.add(new SaveCacheS3StepVariableCreator());
    variableCreators.add(new SecurityStepVariableCreator());
    variableCreators.add(new GitCloneStepVariableCreator());
    variableCreators.add(new ActionStepVariableCreator());
    variableCreators.add(new EmptyVariableCreator(STEP, Set.of(LITE_ENGINE_TASK)));
    variableCreators.add(new SscaStepVariableCreator());
    variableCreators.add(new STOCommonStepVariableCreator());

    return variableCreators;
  }

  @Override
  public List<StepInfo> getStepInfo() {
    StepInfo runStepInfo =
        StepInfo.newBuilder()
            .setName("Run")
            .setType(StepSpecTypeConstants.RUN)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Build").build())
            .build();

    StepInfo backgroundStepInfo =
        StepInfo.newBuilder()
            .setName("Background")
            .setType(StepSpecTypeConstants.BACKGROUND)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Build").build())
            .build();

    StepInfo runTestsStepInfo = StepInfo.newBuilder()
                                    .setName("Run Tests")
                                    .setType(StepSpecTypeConstants.RUN_TEST)
                                    .setFeatureRestrictionName(FeatureRestrictionName.TEST_INTELLIGENCE.name())
                                    .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Build").build())
                                    .build();

    StepInfo pluginStepInfo =
        StepInfo.newBuilder()
            .setName("Plugin")
            .setType(StepSpecTypeConstants.PLUGIN)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Build").build())
            .build();

    StepInfo gitCloneStepInfo =
        StepInfo.newBuilder()
            .setName("Git Clone")
            .setType(StepSpecTypeConstants.GIT_CLONE)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Build").build())
            .build();

    StepInfo restoreCacheFromGCS = StepInfo.newBuilder()
                                       .setName("Restore Cache From GCS")
                                       .setType(StepSpecTypeConstants.RESTORE_CACHE_GCS)
                                       .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Build").build())
                                       .build();

    StepInfo restoreCacheFromS3 = StepInfo.newBuilder()
                                      .setName("Restore Cache From S3")
                                      .setType(StepSpecTypeConstants.RESTORE_CACHE_S3)
                                      .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Build").build())
                                      .build();

    StepInfo saveCacheToS3 = StepInfo.newBuilder()
                                 .setName("Save Cache to S3")
                                 .setType(StepSpecTypeConstants.SAVE_CACHE_S3)
                                 .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Build").build())
                                 .build();

    StepInfo saveCacheToGCS = StepInfo.newBuilder()
                                  .setName("Save Cache to GCS")
                                  .setType(StepSpecTypeConstants.SAVE_CACHE_GCS)
                                  .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Build").build())
                                  .build();

    StepInfo securityStepInfo = StepInfo.newBuilder()
                                    .setName("Security")
                                    .setType(StepSpecTypeConstants.SECURITY)
                                    .setFeatureRestrictionName(FeatureRestrictionName.SECURITY.name())
                                    .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Security").build())
                                    .build();

    StepInfo actionStepInfo =
        StepInfo.newBuilder()
            .setName("Github Action plugin")
            .setType(StepSpecTypeConstants.ACTION)
            .setStepMetaData(StepMetaData.newBuilder().addCategory(PLUGIN).addFolderPaths("Build").build())
            .build();

    StepInfo bitriseStepInfo = StepInfo.newBuilder()
                                   .setName("Bitrise plugin")
                                   .setType(StepSpecTypeConstants.BITRISE)
                                   .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Build").build())
                                   .build();

    StepInfo ecrPushBuilds =
        StepInfo.newBuilder()
            .setName("Build and Push to ECR")
            .setType(StepSpecTypeConstants.BUILD_AND_PUSH_ECR)
            .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Artifacts").addFolderPaths("Build").build())
            .build();

    StepInfo acrPushBuilds =
        StepInfo.newBuilder()
            .setName("Build and Push to ACR")
            .setType(StepSpecTypeConstants.BUILD_AND_PUSH_ACR)
            .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Artifacts").addFolderPaths("Build").build())
            .build();

    StepInfo uploadArtifactsToJfrogBuild =
        StepInfo.newBuilder()
            .setName("Upload Artifacts to JFrog Artifactory")
            .setType(StepSpecTypeConstants.ARTIFACTORY_UPLOAD)
            .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Artifacts").addFolderPaths("Build").build())
            .build();

    StepInfo dockerPushBuild =
        StepInfo.newBuilder()
            .setName("Build and Push an image to Docker Registry")
            .setType(StepSpecTypeConstants.BUILD_AND_PUSH_DOCKER_REGISTRY)
            .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Artifacts").addFolderPaths("Build").build())
            .build();

    StepInfo gcrPushBuilds =
        StepInfo.newBuilder()
            .setName("Build and Push to GCR")
            .setType(StepSpecTypeConstants.BUILD_AND_PUSH_GCR)
            .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Artifacts").addFolderPaths("Build").build())
            .build();

    StepInfo uploadToGCS = StepInfo.newBuilder()
                               .setName("Upload Artifacts to GCS")
                               .setType(StepSpecTypeConstants.GCS_UPLOAD)
                               .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Artifacts").build())
                               .build();

    StepInfo uploadToS3 = StepInfo.newBuilder()
                              .setName("Upload Artifacts to S3")
                              .setType(StepSpecTypeConstants.S3_UPLOAD)
                              .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Artifacts").build())
                              .build();

    List<StepInfo> stepInfos = new ArrayList<>();

    stepInfos.add(runStepInfo);
    stepInfos.add(backgroundStepInfo);
    stepInfos.add(uploadToGCS);
    stepInfos.add(ecrPushBuilds);
    stepInfos.add(uploadToS3);
    stepInfos.add(gcrPushBuilds);
    stepInfos.add(acrPushBuilds);
    stepInfos.add(restoreCacheFromGCS);
    stepInfos.add(runTestsStepInfo);
    stepInfos.add(pluginStepInfo);
    stepInfos.add(securityStepInfo);
    stepInfos.add(restoreCacheFromS3);
    stepInfos.add(dockerPushBuild);
    stepInfos.add(uploadArtifactsToJfrogBuild);
    stepInfos.add(saveCacheToGCS);
    stepInfos.add(gitCloneStepInfo);
    stepInfos.add(saveCacheToS3);
    stepInfos.add(actionStepInfo);
    stepInfos.add(bitriseStepInfo);

    stepInfos.addAll(STOStepType.getStepInfos(ModuleType.CI));

    stepInfos.addAll(sscaStepPaletteSteps);

    return stepInfos;
  }
}
