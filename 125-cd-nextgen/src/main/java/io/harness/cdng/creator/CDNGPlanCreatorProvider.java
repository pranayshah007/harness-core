/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.azure.webapp.variablecreator.AzureWebAppRollbackStepVariableCreator;
import io.harness.cdng.azure.webapp.variablecreator.AzureWebAppSlotDeploymentStepVariableCreator;
import io.harness.cdng.azure.webapp.variablecreator.AzureWebAppSwapSlotStepVariableCreator;
import io.harness.cdng.azure.webapp.variablecreator.AzureWebAppTrafficShiftStepVariableCreator;
import io.harness.cdng.creator.filters.DeploymentStageFilterJsonCreatorV2;
import io.harness.cdng.creator.plan.CDStepsPlanCreator;
import io.harness.cdng.creator.plan.artifact.ArtifactsPlanCreator;
import io.harness.cdng.creator.plan.artifact.PrimaryArtifactPlanCreator;
import io.harness.cdng.creator.plan.artifact.SideCarArtifactPlanCreator;
import io.harness.cdng.creator.plan.artifact.SideCarListPlanCreator;
import io.harness.cdng.creator.plan.configfile.ConfigFilesPlanCreator;
import io.harness.cdng.creator.plan.configfile.IndividualConfigFilePlanCreator;
import io.harness.cdng.creator.plan.envGroup.EnvGroupPlanCreator;
import io.harness.cdng.creator.plan.environment.EnvironmentPlanCreatorV2;
import io.harness.cdng.creator.plan.execution.CDExecutionPMSPlanCreator;
import io.harness.cdng.creator.plan.manifest.IndividualManifestPlanCreator;
import io.harness.cdng.creator.plan.manifest.ManifestsPlanCreator;
import io.harness.cdng.creator.plan.rollback.ExecutionStepsRollbackPMSPlanCreator;
import io.harness.cdng.creator.plan.service.ServiceDefinitionPlanCreator;
import io.harness.cdng.creator.plan.service.ServicePlanCreator;
import io.harness.cdng.creator.plan.service.ServicePlanCreatorV2;
import io.harness.cdng.creator.plan.stage.DeploymentStagePMSPlanCreatorV2;
import io.harness.cdng.creator.plan.steps.CDPMSStepFilterJsonCreator;
import io.harness.cdng.creator.plan.steps.CDPMSStepFilterJsonCreatorV2;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreator;
import io.harness.cdng.creator.plan.steps.CloudformationCreateStackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.CloudformationDeleteStackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.CloudformationRollbackStackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.CommandStepPlanCreator;
import io.harness.cdng.creator.plan.steps.GitOpsCreatePRStepPlanCreatorV2;
import io.harness.cdng.creator.plan.steps.HelmDeployStepPlanCreatorV2;
import io.harness.cdng.creator.plan.steps.HelmRollbackStepPlanCreatorV2;
import io.harness.cdng.creator.plan.steps.K8sApplyStepPlanCreator;
import io.harness.cdng.creator.plan.steps.K8sBGSwapServicesStepPlanCreator;
import io.harness.cdng.creator.plan.steps.K8sBlueGreenStepPlanCreator;
import io.harness.cdng.creator.plan.steps.K8sCanaryDeleteStepPlanCreator;
import io.harness.cdng.creator.plan.steps.K8sCanaryStepPlanCreator;
import io.harness.cdng.creator.plan.steps.K8sDeleteStepPlanCreator;
import io.harness.cdng.creator.plan.steps.K8sRollingRollbackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.K8sRollingStepPlanCreator;
import io.harness.cdng.creator.plan.steps.K8sScaleStepPlanCreator;
import io.harness.cdng.creator.plan.steps.TerraformApplyStepPlanCreator;
import io.harness.cdng.creator.plan.steps.TerraformDestroyStepPlanCreator;
import io.harness.cdng.creator.plan.steps.TerraformPlanStepPlanCreator;
import io.harness.cdng.creator.plan.steps.TerraformRollbackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.azure.webapp.AzureWebAppRollbackStepPlanCreator;
import io.harness.cdng.creator.plan.steps.azure.webapp.AzureWebAppSlotDeploymentStepPlanCreator;
import io.harness.cdng.creator.plan.steps.azure.webapp.AzureWebAppSlotSwapSlotPlanCreator;
import io.harness.cdng.creator.plan.steps.azure.webapp.AzureWebAppTrafficShiftStepPlanCreator;
import io.harness.cdng.creator.plan.steps.serverless.ServerlessAwsLambdaDeployStepPlanCreator;
import io.harness.cdng.creator.plan.steps.serverless.ServerlessAwsLambdaRollbackStepPlanCreator;
import io.harness.cdng.creator.variables.DeploymentStageVariableCreator;
import io.harness.cdng.creator.variables.GitOpsCreatePRStepVariableCreator;
import io.harness.cdng.creator.variables.HelmDeployStepVariableCreator;
import io.harness.cdng.creator.variables.HelmRollbackStepVariableCreator;
import io.harness.cdng.creator.variables.K8sApplyStepVariableCreator;
import io.harness.cdng.creator.variables.K8sBGSwapServicesVariableCreator;
import io.harness.cdng.creator.variables.K8sBlueGreenStepVariableCreator;
import io.harness.cdng.creator.variables.K8sCanaryDeleteStepVariableCreator;
import io.harness.cdng.creator.variables.K8sCanaryStepVariableCreator;
import io.harness.cdng.creator.variables.K8sDeleteStepVariableCreator;
import io.harness.cdng.creator.variables.K8sRollingRollbackStepVariableCreator;
import io.harness.cdng.creator.variables.K8sRollingStepVariableCreator;
import io.harness.cdng.creator.variables.K8sScaleStepVariableCreator;
import io.harness.cdng.creator.variables.ServerlessAwsLambdaDeployStepVariableCreator;
import io.harness.cdng.creator.variables.ServerlessAwsLambdaRollbackStepVariableCreator;
import io.harness.cdng.creator.variables.SshVariableCreator;
import io.harness.cdng.provision.cloudformation.variablecreator.CloudformationCreateStepVariableCreator;
import io.harness.cdng.provision.cloudformation.variablecreator.CloudformationDeleteStepVariableCreator;
import io.harness.cdng.provision.cloudformation.variablecreator.CloudformationRollbackStepVariableCreator;
import io.harness.cdng.provision.terraform.variablecreator.TerraformApplyStepVariableCreator;
import io.harness.cdng.provision.terraform.variablecreator.TerraformDestroyStepVariableCreator;
import io.harness.cdng.provision.terraform.variablecreator.TerraformPlanStepVariableCreator;
import io.harness.cdng.provision.terraform.variablecreator.TerraformRollbackStepVariableCreator;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.stages.parallel.ParallelPlanCreator;
import io.harness.plancreator.steps.SpecNodePlanCreator;
import io.harness.plancreator.steps.StepGroupPMSPlanCreator;
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
  private static final List<String> CLOUDFORMATION_CATEGORY =
      Arrays.asList("Kubernetes", "Provisioner", "Cloudformation", "Helm");
  private static final String CLOUDFORMATION_STEP_METADATA = "Cloudformation";
  private static final List<String> TERRAFORM_CATEGORY = Arrays.asList("Kubernetes", "Provisioner", "Helm");

  @Inject InjectorUtils injectorUtils;
  @Override
  public List<PartialPlanCreator<?>> getPlanCreators() {
    List<PartialPlanCreator<?>> planCreators = new LinkedList<>();
    planCreators.add(new GitOpsCreatePRStepPlanCreatorV2());
    planCreators.add(new DeploymentStagePMSPlanCreatorV2());
    planCreators.add(new ServicePlanCreatorV2());
    planCreators.add(new CDPMSStepPlanCreator());
    planCreators.add(new K8sCanaryStepPlanCreator());
    planCreators.add(new K8sApplyStepPlanCreator());
    planCreators.add(new K8sBlueGreenStepPlanCreator());
    planCreators.add(new K8sRollingStepPlanCreator());
    planCreators.add(new K8sRollingRollbackStepPlanCreator());
    planCreators.add(new K8sScaleStepPlanCreator());
    planCreators.add(new K8sDeleteStepPlanCreator());
    planCreators.add(new K8sBGSwapServicesStepPlanCreator());
    planCreators.add(new K8sCanaryDeleteStepPlanCreator());
    planCreators.add(new TerraformApplyStepPlanCreator());
    planCreators.add(new TerraformPlanStepPlanCreator());
    planCreators.add(new TerraformDestroyStepPlanCreator());
    planCreators.add(new TerraformRollbackStepPlanCreator());
    planCreators.add(new HelmDeployStepPlanCreatorV2());
    planCreators.add(new HelmRollbackStepPlanCreatorV2());
    planCreators.add(new HelmRollbackStepPlanCreator());
    planCreators.add(new CDExecutionPMSPlanCreator());
    planCreators.add(new ExecutionStepsRollbackPMSPlanCreator());
    planCreators.add(new ServicePlanCreator());
    planCreators.add(new ServiceDefinitionPlanCreator());
    planCreators.add(new EnvironmentPlanCreatorV2());
    planCreators.add(new EnvGroupPlanCreator());
    planCreators.add(new ArtifactsPlanCreator());
    planCreators.add(new PrimaryArtifactPlanCreator());
    planCreators.add(new SideCarListPlanCreator());
    planCreators.add(new SideCarArtifactPlanCreator());
    planCreators.add(new ManifestsPlanCreator());
    planCreators.add(new IndividualManifestPlanCreator());
    planCreators.add(new CDStepsPlanCreator());
    planCreators.add(new StepGroupPMSPlanCreator());
    planCreators.add(new ParallelPlanCreator());
    planCreators.add(new ServerlessAwsLambdaDeployStepPlanCreator());
    planCreators.add(new ServerlessAwsLambdaRollbackStepPlanCreator());
    planCreators.add(new CloudformationCreateStackStepPlanCreator());
    planCreators.add(new CloudformationDeleteStackStepPlanCreator());
    planCreators.add(new CloudformationRollbackStackStepPlanCreator());
    planCreators.add(new IndividualConfigFilePlanCreator());
    planCreators.add(new ConfigFilesPlanCreator());
    planCreators.add(new CommandStepPlanCreator());
    planCreators.add(new SpecNodePlanCreator());
    planCreators.add(new AzureWebAppRollbackStepPlanCreator());
    planCreators.add(new AzureWebAppSlotDeploymentStepPlanCreator());
    planCreators.add(new AzureWebAppSlotSwapSlotPlanCreator());
    planCreators.add(new AzureWebAppTrafficShiftStepPlanCreator());
    injectorUtils.injectMembers(planCreators);
    return planCreators;
  }

  @Override
  public List<FilterJsonCreator> getFilterJsonCreators() {
    List<FilterJsonCreator> filterJsonCreators = new ArrayList<>();
    filterJsonCreators.add(new DeploymentStageFilterJsonCreatorV2());
    filterJsonCreators.add(new CDPMSStepFilterJsonCreator());
    filterJsonCreators.add(new CDPMSStepFilterJsonCreatorV2());
    injectorUtils.injectMembers(filterJsonCreators);

    return filterJsonCreators;
  }

  @Override
  public List<VariableCreator> getVariableCreators() {
    List<VariableCreator> variableCreators = new ArrayList<>();
    variableCreators.add(new GitOpsCreatePRStepVariableCreator());
    variableCreators.add(new DeploymentStageVariableCreator());
    variableCreators.add(new ExecutionVariableCreator());
    variableCreators.add(new K8sApplyStepVariableCreator());
    variableCreators.add(new K8sBGSwapServicesVariableCreator());
    variableCreators.add(new K8sBlueGreenStepVariableCreator());
    variableCreators.add(new K8sCanaryDeleteStepVariableCreator());
    variableCreators.add(new K8sCanaryStepVariableCreator());
    variableCreators.add(new K8sDeleteStepVariableCreator());
    variableCreators.add(new K8sRollingRollbackStepVariableCreator());
    variableCreators.add(new K8sRollingStepVariableCreator());
    variableCreators.add(new K8sScaleStepVariableCreator());
    variableCreators.add(new TerraformApplyStepVariableCreator());
    variableCreators.add(new TerraformPlanStepVariableCreator());
    variableCreators.add(new TerraformDestroyStepVariableCreator());
    variableCreators.add(new TerraformRollbackStepVariableCreator());
    variableCreators.add(new HelmDeployStepVariableCreator());
    variableCreators.add(new HelmRollbackStepVariableCreator());
    variableCreators.add(new ServerlessAwsLambdaDeployStepVariableCreator());
    variableCreators.add(new ServerlessAwsLambdaRollbackStepVariableCreator());
    variableCreators.add(new CloudformationCreateStepVariableCreator());
    variableCreators.add(new CloudformationDeleteStepVariableCreator());
    variableCreators.add(new CloudformationRollbackStepVariableCreator());
    variableCreators.add(new SshVariableCreator());
    variableCreators.add(new AzureWebAppSlotDeploymentStepVariableCreator());
    variableCreators.add(new AzureWebAppTrafficShiftStepVariableCreator());
    variableCreators.add(new AzureWebAppSwapSlotStepVariableCreator());
    variableCreators.add(new AzureWebAppRollbackStepVariableCreator());
    return variableCreators;
  }

  @Override
  public List<StepInfo> getStepInfo() {
    StepInfo gitOpsCreatePR =
        StepInfo.newBuilder()
            .setName("GitOps Create PR")
            .setType(StepSpecTypeConstants.GITOPS_CREATE_PR)
            .setFeatureFlag(FeatureName.NG_GITOPS.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Kubernetes").setFolderPath("GitOps").build())
            .build();

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
    StepInfo delete = StepInfo.newBuilder()
                          .setName("Delete")
                          .setType(StepSpecTypeConstants.K8S_DELETE)
                          .setFeatureRestrictionName(FeatureRestrictionName.K8S_DELETE.name())
                          .setStepMetaData(StepMetaData.newBuilder()
                                               .addCategory("Kubernetes")
                                               .addCategory("Helm")
                                               .addFolderPaths("Kubernetes")
                                               .build())
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

    StepInfo apply = StepInfo.newBuilder()
                         .setName("Apply")
                         .setType(StepSpecTypeConstants.K8S_APPLY)
                         .setFeatureRestrictionName(FeatureRestrictionName.K8S_APPLY.name())
                         .setStepMetaData(StepMetaData.newBuilder()
                                              .addCategory("Kubernetes")
                                              .addCategory("Helm")
                                              .addFolderPaths("Kubernetes")
                                              .build())
                         .build();
    StepInfo scale = StepInfo.newBuilder()
                         .setName("Scale")
                         .setType(StepSpecTypeConstants.K8S_SCALE)
                         .setFeatureRestrictionName(FeatureRestrictionName.K8S_SCALE.name())
                         .setStepMetaData(StepMetaData.newBuilder()
                                              .addCategory("Kubernetes")
                                              .addCategory("Helm")
                                              .addFolderPaths("Kubernetes")
                                              .build())
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
            .setFeatureFlag(FeatureName.NG_NATIVE_HELM.name())
            .build();

    StepInfo helmRollback =
        StepInfo.newBuilder()
            .setName("Helm Rollback")
            .setType(StepSpecTypeConstants.HELM_ROLLBACK)
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Helm").setFolderPath("Helm").build())
            .setFeatureFlag(FeatureName.NG_NATIVE_HELM.name())
            .build();

    StepInfo executeCommand =
        StepInfo.newBuilder()
            .setName("Command")
            .setType(StepSpecTypeConstants.COMMAND)
            .setFeatureRestrictionName(FeatureRestrictionName.COMMAND.name())
            .setFeatureFlag(FeatureName.SSH_NG.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("Ssh").addFolderPaths("Ssh").build())
            .build();

    StepInfo serverlessDeploy =
        StepInfo.newBuilder()
            .setName("Serverless Lambda Deploy")
            .setType(StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_DEPLOY)
            .setStepMetaData(
                StepMetaData.newBuilder().addCategory("ServerlessAwsLambda").setFolderPath("Serverless Lambda").build())
            .setFeatureFlag(FeatureName.SERVERLESS_SUPPORT.name())
            .build();

    StepInfo serverlessRollback =
        StepInfo.newBuilder()
            .setName("Serverless Lambda Rollback")
            .setType(StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_ROLLBACK)
            .setStepMetaData(
                StepMetaData.newBuilder().addCategory("ServerlessAwsLambda").setFolderPath("Serverless Lambda").build())
            .setFeatureFlag(FeatureName.SERVERLESS_SUPPORT.name())
            .build();

    StepInfo createStack = StepInfo.newBuilder()
                               .setName("CloudFormation Create Stack")
                               .setType(StepSpecTypeConstants.CLOUDFORMATION_CREATE_STACK)
                               .setFeatureRestrictionName(FeatureRestrictionName.CREATE_STACK.name())
                               .setStepMetaData(StepMetaData.newBuilder()
                                                    .addAllCategory(CLOUDFORMATION_CATEGORY)
                                                    .addFolderPaths(CLOUDFORMATION_STEP_METADATA)
                                                    .build())
                               .setFeatureFlag(FeatureName.CLOUDFORMATION_NG.name())
                               .build();

    StepInfo deleteStack = StepInfo.newBuilder()
                               .setName("CloudFormation Delete Stack")
                               .setType(StepSpecTypeConstants.CLOUDFORMATION_DELETE_STACK)
                               .setFeatureRestrictionName(FeatureRestrictionName.DELETE_STACK.name())
                               .setStepMetaData(StepMetaData.newBuilder()
                                                    .addAllCategory(CLOUDFORMATION_CATEGORY)
                                                    .addFolderPaths(CLOUDFORMATION_STEP_METADATA)
                                                    .build())
                               .setFeatureFlag(FeatureName.CLOUDFORMATION_NG.name())
                               .build();

    StepInfo rollbackStack = StepInfo.newBuilder()
                                 .setName("CloudFormation Rollback")
                                 .setType(StepSpecTypeConstants.CLOUDFORMATION_ROLLBACK_STACK)
                                 .setFeatureRestrictionName(FeatureRestrictionName.ROLLBACK_STACK.name())
                                 .setStepMetaData(StepMetaData.newBuilder()
                                                      .addAllCategory(CLOUDFORMATION_CATEGORY)
                                                      .addFolderPaths(CLOUDFORMATION_STEP_METADATA)
                                                      .build())
                                 .setFeatureFlag(FeatureName.CLOUDFORMATION_NG.name())
                                 .build();

    StepInfo azureWebAppSlotDeployment =
        StepInfo.newBuilder()
            .setName("Azure Slot Deployment")
            .setType(StepSpecTypeConstants.AZURE_SLOT_DEPLOYMENT)
            .setFeatureRestrictionName(FeatureRestrictionName.AZURE_SLOT_DEPLOYMENT.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("AzureWebApp").addFolderPaths("AzureWebApp").build())
            .setFeatureFlag(FeatureName.AZURE_WEBAPP_NG.name())
            .build();

    StepInfo azureWebAppTrafficShift =
        StepInfo.newBuilder()
            .setName("Azure Traffic Shift")
            .setType(StepSpecTypeConstants.AZURE_TRAFFIC_SHIFT)
            .setFeatureRestrictionName(FeatureRestrictionName.AZURE_TRAFFIC_SHIFT.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("AzureWebApp").addFolderPaths("AzureWebApp").build())
            .setFeatureFlag(FeatureName.AZURE_WEBAPP_NG.name())
            .build();

    StepInfo azureWebAppSwapSlot =
        StepInfo.newBuilder()
            .setName("Azure Swap Slot")
            .setType(StepSpecTypeConstants.AZURE_SWAP_SLOT)
            .setFeatureRestrictionName(FeatureRestrictionName.AZURE_SWAP_SLOT.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("AzureWebApp").addFolderPaths("AzureWebApp").build())
            .setFeatureFlag(FeatureName.AZURE_WEBAPP_NG.name())
            .build();

    StepInfo azureWebAppRollback =
        StepInfo.newBuilder()
            .setName("Azure WebApp Rollback")
            .setType(StepSpecTypeConstants.AZURE_WEBAPP_ROLLBACK)
            .setFeatureRestrictionName(FeatureRestrictionName.AZURE_WEBAPP_ROLLBACK.name())
            .setStepMetaData(StepMetaData.newBuilder().addCategory("AzureWebApp").addFolderPaths("AzureWebApp").build())
            .setFeatureFlag(FeatureName.AZURE_WEBAPP_NG.name())
            .build();

    List<StepInfo> stepInfos = new ArrayList<>();

    stepInfos.add(gitOpsCreatePR);
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
    stepInfos.add(serverlessDeploy);
    stepInfos.add(serverlessRollback);
    stepInfos.add(createStack);
    stepInfos.add(deleteStack);
    stepInfos.add(rollbackStack);
    stepInfos.add(executeCommand);
    stepInfos.add(azureWebAppSlotDeployment);
    stepInfos.add(azureWebAppTrafficShift);
    stepInfos.add(azureWebAppSwapSlot);
    stepInfos.add(azureWebAppRollback);
    return stepInfos;
  }
}