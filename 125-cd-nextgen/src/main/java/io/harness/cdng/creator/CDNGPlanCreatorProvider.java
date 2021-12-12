package io.harness.cdng.creator;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.filters.DeploymentStageFilterJsonCreator;
import io.harness.cdng.creator.plan.stage.DeploymentStagePMSPlanCreator;
import io.harness.cdng.creator.plan.steps.CDPMSStepFilterJsonCreator;
import io.harness.cdng.creator.plan.steps.CDPMSStepFilterJsonCreatorV2;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreator;
import io.harness.cdng.creator.plan.steps.K8sBGSwapServicesPMSStepPlanCreator;
import io.harness.cdng.creator.plan.steps.K8sCanaryDeletePMSStepPlanCreator;
import io.harness.cdng.creator.plan.steps.K8sCanaryStepPlanCreator;
import io.harness.cdng.creator.plan.steps.K8sRollingDeployPMSStepPlanCreator;
import io.harness.cdng.creator.plan.steps.K8sRollingRollbackPMSStepPlanCreator;
import io.harness.cdng.creator.variables.DeploymentStageVariableCreator;
import io.harness.cdng.creator.variables.HelmStepVariableCreator;
import io.harness.cdng.creator.variables.K8sStepVariableCreator;
import io.harness.cdng.provision.terraform.variablecreator.TerraformStepsVariableCreator;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.contracts.steps.StepMetaData;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.sdk.core.pipeline.variables.ExecutionVariableCreator;
import io.harness.pms.sdk.core.plan.creation.creators.PartialPlanCreator;
import io.harness.pms.sdk.core.plan.creation.creators.PipelineServiceInfoProvider;
import io.harness.pms.sdk.core.variables.VariableCreator;
import io.harness.pms.utils.InjectorUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

@OwnedBy(HarnessTeam.CDC)
@Singleton
public class CDNGPlanCreatorProvider implements PipelineServiceInfoProvider {
  private static final String TERRAFORM_STEP_METADATA = "Terraform";
  private static final List<String> TERRAFORM_CATEGORY = Arrays.asList("Kubernetes", "Provisioner");

  @Inject InjectorUtils injectorUtils;
  @Override
  public List<PartialPlanCreator<?>> getPlanCreators() {
    List<PartialPlanCreator<?>> planCreators = new LinkedList<>();
    planCreators.add(new DeploymentStagePMSPlanCreator());
    planCreators.add(new CDPMSStepPlanCreator());
    planCreators.add(new K8sCanaryStepPlanCreator());
    planCreators.add(new K8sRollingRollbackPMSStepPlanCreator());
    planCreators.add(new K8sCanaryDeletePMSStepPlanCreator());
    planCreators.add(new K8sRollingDeployPMSStepPlanCreator());
    planCreators.add(new K8sBGSwapServicesPMSStepPlanCreator());
    planCreators.add(new HelmRollbackStepPlanCreator());
    injectorUtils.injectMembers(planCreators);
    return planCreators;
  }

  @Override
  public List<FilterJsonCreator> getFilterJsonCreators() {
    List<FilterJsonCreator> filterJsonCreators = new ArrayList<>();
    filterJsonCreators.add(new DeploymentStageFilterJsonCreator());
    filterJsonCreators.add(new CDPMSStepFilterJsonCreator());
    filterJsonCreators.add(new CDPMSStepFilterJsonCreatorV2());
    injectorUtils.injectMembers(filterJsonCreators);

    return filterJsonCreators;
  }

  @Override
  public List<VariableCreator> getVariableCreators() {
    List<VariableCreator> variableCreators = new ArrayList<>();
    variableCreators.add(new DeploymentStageVariableCreator());
    variableCreators.add(new ExecutionVariableCreator());
    variableCreators.add(new K8sStepVariableCreator());
    variableCreators.add(new TerraformStepsVariableCreator());
    variableCreators.add(new HelmStepVariableCreator());
    return variableCreators;
  }

  @Override
  public List<StepInfo> getStepInfo() {
    StepInfo k8sRolling =
        StepInfo.newBuilder()
            .setName("Rolling Deployment")
            .setType(StepSpecTypeConstants.K8S_ROLLING_DEPLOY)
            .setFeatureRestrictionName(FeatureRestrictionName.K8S_ROLLING_DEPLOY.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Kubernetes").addFolderPaths("Kubernetes").build())
            .build();

    StepInfo canaryDeploy =
        StepInfo.newBuilder()
            .setName("Canary Deployment")
            .setType(StepSpecTypeConstants.K8S_CANARY_DEPLOY)
            .setFeatureRestrictionName(FeatureRestrictionName.K8S_CANARY_DEPLOY.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Kubernetes").addFolderPaths("Kubernetes").build())
            .build();
    StepInfo canaryDelete =
        StepInfo.newBuilder()
            .setName("Canary Delete")
            .setType(StepSpecTypeConstants.K8S_CANARY_DELETE)
            .setFeatureRestrictionName(FeatureRestrictionName.K8S_CANARY_DELETE.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Kubernetes").addFolderPaths("Kubernetes").build())
            .build();
    StepInfo delete =
        StepInfo.newBuilder()
            .setName("Delete")
            .setType(StepSpecTypeConstants.K8S_DELETE)
            .setFeatureRestrictionName(FeatureRestrictionName.K8S_DELETE.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Kubernetes").addFolderPaths("Kubernetes").build())
            .build();

    StepInfo stageDeployment =
        StepInfo.newBuilder()
            .setName("Stage Deployment")
            .setType(StepSpecTypeConstants.K8S_BLUE_GREEN_DEPLOY)
            .setFeatureRestrictionName(FeatureRestrictionName.K8S_BLUE_GREEN_DEPLOY.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Kubernetes").addFolderPaths("Kubernetes").build())
            .build();
    StepInfo bgSwapServices =
        StepInfo.newBuilder()
            .setName("BG Swap Services")
            .setType(StepSpecTypeConstants.K8S_BG_SWAP_SERVICES)
            .setFeatureRestrictionName(FeatureRestrictionName.K8S_BG_SWAP_SERVICES.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Kubernetes").addFolderPaths("Kubernetes").build())
            .build();

    StepInfo apply =
        StepInfo.newBuilder()
            .setName("Apply")
            .setType(StepSpecTypeConstants.K8S_APPLY)
            .setFeatureRestrictionName(FeatureRestrictionName.K8S_APPLY.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Kubernetes").addFolderPaths("Kubernetes").build())
            .build();
    StepInfo scale =
        StepInfo.newBuilder()
            .setName("Scale")
            .setType(StepSpecTypeConstants.K8S_SCALE)
            .setFeatureRestrictionName(FeatureRestrictionName.K8S_SCALE.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Kubernetes").addFolderPaths("Kubernetes").build())
            .build();

    StepInfo k8sRollingRollback =
        StepInfo.newBuilder()
            .setName("Rolling Rollback")
            .setType(StepSpecTypeConstants.K8S_ROLLING_ROLLBACK)
            .setFeatureRestrictionName(FeatureRestrictionName.K8S_ROLLING_ROLLBACK.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Kubernetes").addFolderPaths("Kubernetes").build())
            .build();

    StepInfo terraformApply = StepInfo.newBuilder()
                                  .setName("Terraform Apply")
                                  .setType(StepSpecTypeConstants.TERRAFORM_APPLY)
                                  .setFeatureRestrictionName(FeatureRestrictionName.TERRAFORM_APPLY.name())
                                  .setStepMetaData(StepMetaData.newBuilder()
                                                       .addAllCategory(TERRAFORM_CATEGORY)
                                                       .addFolderPaths(TERRAFORM_STEP_METADATA)
                                                       .build())
                                  .build();
    StepInfo terraformPlan = StepInfo.newBuilder()
                                 .setName("Terraform Plan")
                                 .setType(StepSpecTypeConstants.TERRAFORM_PLAN)
                                 .setFeatureRestrictionName(FeatureRestrictionName.TERRAFORM_PLAN.name())
                                 .setStepMetaData(StepMetaData.newBuilder()
                                                      .addAllCategory(TERRAFORM_CATEGORY)
                                                      .addFolderPaths(TERRAFORM_STEP_METADATA)
                                                      .build())
                                 .build();
    StepInfo terraformDestroy = StepInfo.newBuilder()
                                    .setName("Terraform Destroy")
                                    .setType(StepSpecTypeConstants.TERRAFORM_DESTROY)
                                    .setFeatureRestrictionName(FeatureRestrictionName.TERRAFORM_DESTROY.name())
                                    .setStepMetaData(StepMetaData.newBuilder()
                                                         .addAllCategory(TERRAFORM_CATEGORY)
                                                         .addFolderPaths(TERRAFORM_STEP_METADATA)
                                                         .build())
                                    .build();
    StepInfo terraformRollback = StepInfo.newBuilder()
                                     .setName("Terraform Rollback")
                                     .setType(StepSpecTypeConstants.TERRAFORM_ROLLBACK)
                                     .setFeatureRestrictionName(FeatureRestrictionName.TERRAFORM_ROLLBACK.name())
                                     .setStepMetaData(StepMetaData.newBuilder()
                                                          .addAllCategory(TERRAFORM_CATEGORY)
                                                          .addFolderPaths(TERRAFORM_STEP_METADATA)
                                                          .build())
                                     .build();

    StepInfo helmDeploy =
        StepInfo.newBuilder()
            .setName("Helm Deploy")
            .setType(StepSpecTypeConstants.HELM_DEPLOY)
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Helm").setFolderPath("Helm").build())
            .build();

    StepInfo helmRollback =
        StepInfo.newBuilder()
            .setName("Helm Rollback")
            .setType(StepSpecTypeConstants.HELM_ROLLBACK)
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Helm").setFolderPath("Helm").build())
            .build();

    List<StepInfo> stepInfos = new ArrayList<>();

    stepInfos.add(k8sRolling);
    stepInfos.add(delete);
    stepInfos.add(canaryDeploy);
    stepInfos.add(canaryDelete);
    stepInfos.add(stageDeployment);
    stepInfos.add(bgSwapServices);
    stepInfos.add(apply);
    stepInfos.add(scale);
    stepInfos.add(k8sRollingRollback);
    stepInfos.add(terraformApply);
    stepInfos.add(terraformPlan);
    stepInfos.add(terraformRollback);
    stepInfos.add(terraformDestroy);
    stepInfos.add(helmDeploy);
    stepInfos.add(helmRollback);
    return stepInfos;
  }
}
