/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.executables.CdTaskChainExecutable;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExectionDetailsService;
import io.harness.cdng.provision.terraform.functor.TerraformHumanReadablePlanFunctor;
import io.harness.cdng.provision.terraform.functor.TerraformPlanJsonFunctor;
import io.harness.cdng.provision.terraform.outcome.TerraformPlanOutcome;
import io.harness.cdng.provision.terraform.outcome.TerraformPlanOutcome.TerraformPlanOutcomeBuilder;
import io.harness.common.ParameterFieldHelper;
import io.harness.delegate.task.terraform.TFTaskType;
import io.harness.delegate.task.terraform.TerraformCommand;
import io.harness.delegate.task.terraform.TerraformCommandUnit;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters.TerraformTaskNGParametersBuilder;
import io.harness.delegate.task.terraform.TerraformTaskNGResponse;
import io.harness.delegate.task.terraform.TerraformVarFileInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.provision.TerraformConstants;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class TerraformPlanStepV2 extends CdTaskChainExecutable {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.TERRAFORM_PLAN_V2.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private TerraformStepHelper helper;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private StepHelper stepHelper;
  @Inject private CDFeatureFlagHelper featureFlagHelper;
  @Inject TerraformPlanExectionDetailsService terraformPlanExectionDetailsService;

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

    List<EntityDetail> entityDetailList = new ArrayList<>();

    TerraformPlanStepParameters stepParametersSpec = (TerraformPlanStepParameters) stepParameters.getSpec();

    // Config Files connector
    String connectorRef =
        stepParametersSpec.configuration.configFiles.store.getSpec().getConnectorReference().getValue();
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(connectorRef, accountId, orgIdentifier, projectIdentifier);
    EntityDetail entityDetail = EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(identifierRef).build();
    entityDetailList.add(entityDetail);

    // Var Files connectors
    LinkedHashMap<String, TerraformVarFile> varFiles = stepParametersSpec.getConfiguration().getVarFiles();
    List<EntityDetail> varFilesEntityDetails =
        TerraformStepHelper.prepareEntityDetailsForVarFiles(accountId, orgIdentifier, projectIdentifier, varFiles);
    entityDetailList.addAll(varFilesEntityDetails);

    // Backend Config connector
    TerraformBackendConfig backendConfig = stepParametersSpec.getConfiguration().getBackendConfig();
    Optional<EntityDetail> bcFileEntityDetails = TerraformStepHelper.prepareEntityDetailForBackendConfigFiles(
        accountId, orgIdentifier, projectIdentifier, backendConfig);
    bcFileEntityDetails.ifPresent(entityDetailList::add);

    // Secret Manager Connector
    if (!stepParametersSpec.getConfiguration().getIsTerraformCloudCli().getValue()) {
      String secretManagerRef = stepParametersSpec.getConfiguration().getSecretManagerRef().getValue();
      IdentifierRef secretManagerIdentifierRef =
          IdentifierRefHelper.getIdentifierRef(secretManagerRef, accountId, orgIdentifier, projectIdentifier);
      entityDetail = EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(secretManagerIdentifierRef).build();
      entityDetailList.add(entityDetail);
      helper.validateSecretManager(ambiance, secretManagerIdentifierRef);
    }

    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetailList, true);
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    log.info("Starting execution ObtainTask after Rbac for the Plan Step");
    TerraformPlanStepParameters planStepParameters = (TerraformPlanStepParameters) stepElementParameters.getSpec();
    helper.validatePlanStepConfigFiles(planStepParameters);

    TerraformPlanExecutionDataParameters configuration = planStepParameters.getConfiguration();
    List<TerraformVarFileInfo> varFilesInfo = helper.getRemoteVarFilesInfo(configuration.getVarFiles(), ambiance);

    boolean hasGitVarFiles = helper.hasGitVarFiles(varFilesInfo);
    boolean hasS3VarFiles = helper.hasS3VarFiles(varFilesInfo);

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().hasGitFiles(hasGitVarFiles).hasS3Files(hasS3VarFiles).build();

    TerraformTaskNGParametersBuilder tfParametersBuilder =
        getTerraformTaskNGParametersBuilder(ambiance, stepElementParameters);
    terraformPassThroughData.setTerraformTaskNGParametersBuilder(tfParametersBuilder);

    if (hasGitVarFiles || hasS3VarFiles) {
      return helper.fetchRemoteVarFiles(terraformPassThroughData, varFilesInfo, ambiance, stepElementParameters,
          TerraformCommandUnit.Plan.name(), planStepParameters.getDelegateSelectors());
    }

    return helper.executeTerraformTask(tfParametersBuilder.build(), stepElementParameters, ambiance,
        terraformPassThroughData, planStepParameters.getDelegateSelectors(), TerraformCommandUnit.Plan.name());
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters, StepInputPackage inputPackage, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    TerraformPlanStepParameters planStepParameters = (TerraformPlanStepParameters) stepElementParameters.getSpec();

    return helper.executeNextLink(ambiance, responseDataSupplier, passThroughData,
        planStepParameters.getDelegateSelectors(), stepElementParameters, TerraformCommandUnit.Plan.name());
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    if (passThroughData instanceof StepExceptionPassThroughData) {
      StepExceptionPassThroughData stepExceptionPassThroughData = (StepExceptionPassThroughData) passThroughData;
      return helper.handleStepExceptionFailure(stepExceptionPassThroughData);
    }

    TerraformPassThroughData terraformPassThroughData = (TerraformPassThroughData) passThroughData;

    log.info("Handling Task result with Security Context for the Plan Step");
    TerraformPlanStepParameters planStepParameters = (TerraformPlanStepParameters) stepElementParameters.getSpec();
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();
    TerraformTaskNGResponse terraformTaskNGResponse = (TerraformTaskNGResponse) responseDataSupplier.get();

    List<UnitProgress> unitProgresses = new ArrayList<>(terraformTaskNGResponse.getUnitProgressData() == null
            ? Collections.emptyList()
            : terraformTaskNGResponse.getUnitProgressData().getUnitProgresses());

    /*
    commandUnits needs to have fallback, because of older delegates not considering previous commandUnits like fetch
    files, we can check passThroughData if it hasGit or hasS3 then to put the command units from passthroughdata
    */

    boolean responseHasFetchFiles = false;
    if (terraformTaskNGResponse.getUnitProgressData() != null) {
      responseHasFetchFiles = terraformTaskNGResponse.getUnitProgressData().getUnitProgresses().stream().anyMatch(
          unitProgress -> unitProgress.getUnitName().equalsIgnoreCase("Fetch Files"));
    }

    if (!responseHasFetchFiles && (terraformPassThroughData.hasGitFiles() || terraformPassThroughData.hasS3Files())) {
      unitProgresses.addAll(terraformPassThroughData.getUnitProgresses());
    }

    stepResponseBuilder.unitProgressList(unitProgresses);

    switch (terraformTaskNGResponse.getCommandExecutionStatus()) {
      case SUCCESS:
        stepResponseBuilder.status(Status.SUCCEEDED);
        break;
      case FAILURE:
        stepResponseBuilder.status(Status.FAILED);
        break;
      case RUNNING:
        stepResponseBuilder.status(Status.RUNNING);
        break;
      case QUEUED:
        stepResponseBuilder.status(Status.QUEUED);
        break;
      default:
        throw new InvalidRequestException(
            "Unhandled type CommandExecutionStatus: " + terraformTaskNGResponse.getCommandExecutionStatus().name(),
            WingsException.USER);
    }

    if (CommandExecutionStatus.SUCCESS == terraformTaskNGResponse.getCommandExecutionStatus()) {
      TerraformPlanOutcomeBuilder tfPlanOutcomeBuilder = TerraformPlanOutcome.builder();
      helper.updateParentEntityIdAndVersion(
          helper.generateFullIdentifier(
              ParameterFieldHelper.getParameterFieldValue(planStepParameters.getProvisionerIdentifier()), ambiance),
          terraformTaskNGResponse.getStateFileId());
      tfPlanOutcomeBuilder.detailedExitCode(terraformTaskNGResponse.getDetailedExitCode());

      if (!planStepParameters.getConfiguration().getIsTerraformCloudCli().getValue()) {
        String provisionerIdentifier =
            ParameterFieldHelper.getParameterFieldValue(planStepParameters.getProvisionerIdentifier());
        helper.saveTerraformInheritOutput(
            planStepParameters, terraformTaskNGResponse, ambiance, terraformPassThroughData);

        ParameterField<Boolean> exportTfPlanJsonField =
            planStepParameters.getConfiguration().getExportTerraformPlanJson();
        boolean exportTfPlanJson = !ParameterField.isNull(exportTfPlanJsonField)
            && ParameterFieldHelper.getBooleanParameterFieldValue(exportTfPlanJsonField);

        ParameterField<Boolean> exportTfHumanReadablePlanField =
            planStepParameters.getConfiguration().getExportTerraformHumanReadablePlan();
        boolean exportHumanReadablePlan = !ParameterField.isNull(exportTfHumanReadablePlanField)
            && ParameterFieldHelper.getBooleanParameterFieldValue(exportTfHumanReadablePlanField);

        helper.saveTerraformPlanExecutionDetails(
            ambiance, terraformTaskNGResponse, provisionerIdentifier, planStepParameters);

        if (exportHumanReadablePlan || exportTfPlanJson) {
          // First we save the terraform plan execution detail

          String stepFqn = AmbianceUtils.getFQNUsingLevels(ambiance.getLevelsList());
          if (exportHumanReadablePlan) {
            String humanReadableOutputName =
                helper.saveTerraformPlanHumanReadableOutput(ambiance, terraformTaskNGResponse, provisionerIdentifier);

            if (humanReadableOutputName != null) {
              tfPlanOutcomeBuilder.humanReadableFilePath(
                  TerraformHumanReadablePlanFunctor.getExpression(stepFqn, humanReadableOutputName));
            }
          }

          if (exportTfPlanJson) {
            String planJsonOutputName =
                helper.saveTerraformPlanJsonOutput(ambiance, terraformTaskNGResponse, provisionerIdentifier);

            if (planJsonOutputName != null) {
              tfPlanOutcomeBuilder.jsonFilePath(TerraformPlanJsonFunctor.getExpression(stepFqn, planJsonOutputName));
            }
          }
        }
      }

      stepResponseBuilder.stepOutcome(StepResponse.StepOutcome.builder()
                                          .name(TerraformPlanOutcome.OUTCOME_NAME)
                                          .outcome(tfPlanOutcomeBuilder.build())
                                          .build());
    }
    return stepResponseBuilder.build();
  }

  private TerraformTaskNGParametersBuilder getTerraformTaskNGParametersBuilder(
      Ambiance ambiance, StepElementParameters stepElementParameters) {
    TerraformPlanStepParameters planStepParameters = (TerraformPlanStepParameters) stepElementParameters.getSpec();
    helper.validatePlanStepConfigFiles(planStepParameters);
    TerraformPlanExecutionDataParameters configuration = planStepParameters.getConfiguration();

    TerraformTaskNGParametersBuilder builder = TerraformTaskNGParameters.builder();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    builder.accountId(accountId);
    String entityId = helper.generateFullIdentifier(
        ParameterFieldHelper.getParameterFieldValue(planStepParameters.getProvisionerIdentifier()), ambiance);

    ParameterField<Boolean> exportTfPlanJsonField;
    ParameterField<Boolean> exportTfHumanReadablePlanField;

    boolean isTerraformCloudCli = planStepParameters.getConfiguration().getIsTerraformCloudCli().getValue();

    if (!isTerraformCloudCli) {
      EncryptionConfig secretManagerEncryptionConfig = helper.getEncryptionConfig(ambiance, planStepParameters);
      exportTfPlanJsonField = planStepParameters.getConfiguration().getExportTerraformPlanJson();
      exportTfHumanReadablePlanField = planStepParameters.getConfiguration().getExportTerraformHumanReadablePlan();
      builder.saveTerraformStateJson(!ParameterField.isNull(exportTfPlanJsonField) && exportTfPlanJsonField.getValue());
      builder.saveTerraformHumanReadablePlan(
          !ParameterField.isNull(exportTfHumanReadablePlanField) && exportTfHumanReadablePlanField.getValue());
      builder.encryptionConfig(secretManagerEncryptionConfig);
      builder.workspace(ParameterFieldHelper.getParameterFieldValue(configuration.getWorkspace()));
      builder.encryptDecryptPlanForHarnessSMOnManager(
          helper.tfPlanEncryptionOnManager(accountId, secretManagerEncryptionConfig));
    }
    ParameterField<Boolean> skipTerraformRefreshCommand =
        planStepParameters.getConfiguration().getSkipTerraformRefresh();
    builder.skipTerraformRefresh(ParameterFieldHelper.getBooleanParameterFieldValue(skipTerraformRefreshCommand));

    builder.terraformCommandFlags(
        helper.getTerraformCliFlags(planStepParameters.getConfiguration().getCliOptionFlags()));

    builder.taskType(TFTaskType.PLAN)
        .terraformCommandUnit(TerraformCommandUnit.Plan)
        .entityId(entityId)
        .tfModuleSourceInheritSSH(
            helper.isExportCredentialForSourceModule(configuration.getConfigFiles(), stepElementParameters.getType()))
        .currentStateFileId(helper.getLatestFileId(entityId))
        .configFile(helper.getGitFetchFilesConfig(
            configuration.getConfigFiles().getStore().getSpec(), ambiance, TerraformStepHelper.TF_CONFIG_FILES))
        .fileStoreConfigFiles(helper.getFileStoreFetchFilesConfig(
            configuration.getConfigFiles().getStore().getSpec(), ambiance, TerraformStepHelper.TF_CONFIG_FILES))
        .varFileInfos(helper.toTerraformVarFileInfoWithIdentifierAndManifest(configuration.getVarFiles(), ambiance))
        .backendConfig(helper.getBackendConfig(configuration.getBackendConfig()))
        .backendConfigFileInfo(helper.toTerraformBackendFileInfo(configuration.getBackendConfig(), ambiance))
        .targets(ParameterFieldHelper.getParameterFieldValue(configuration.getTargets()))
        .environmentVariables(helper.getEnvironmentVariablesMap(configuration.getEnvironmentVariables()) == null
                ? new HashMap<>()
                : helper.getEnvironmentVariablesMap(configuration.getEnvironmentVariables()))
        .terraformCommand(TerraformPlanCommand.APPLY == planStepParameters.getConfiguration().getCommand()
                ? TerraformCommand.APPLY
                : TerraformCommand.DESTROY)
        .planName(helper.getTerraformPlanName(planStepParameters.getConfiguration().getCommand(), ambiance,
            planStepParameters.getProvisionerIdentifier().getValue()))
        .timeoutInMillis(
            StepUtils.getTimeoutMillis(stepElementParameters.getTimeout(), TerraformConstants.DEFAULT_TIMEOUT))
        .useOptimizedTfPlan(true)
        .isTerraformCloudCli(isTerraformCloudCli);

    return builder;
  }
}
