/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terragrunt;

import static io.harness.cdng.provision.terragrunt.TerragruntStepHelper.DEFAULT_TIMEOUT;
import static io.harness.provision.TerragruntConstants.APPLY;
import static io.harness.provision.TerragruntConstants.FETCH_CONFIG_FILES;

import static software.wings.beans.TaskType.TERRAGRUNT_APPLY_TASK_NG;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.common.ParameterFieldHelper;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.terragrunt.request.TerragruntApplyTaskParameters;
import io.harness.delegate.beans.terragrunt.request.TerragruntApplyTaskParameters.TerragruntApplyTaskParametersBuilder;
import io.harness.delegate.beans.terragrunt.request.TerragruntCommandType;
import io.harness.delegate.beans.terragrunt.request.TerragruntRunConfiguration;
import io.harness.delegate.beans.terragrunt.request.TerragruntTaskRunType;
import io.harness.delegate.beans.terragrunt.response.TerragruntApplyTaskResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.UnitProgress;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class TerragruntApplyStep extends CdTaskExecutable<TerragruntApplyTaskResponse> {
  public static final StepType STEP_TYPE =
      TerragruntStepHelper.addStepType(ExecutionNodeType.TERRAGRUNT_APPLY.getYamlType());

  @Inject private TerragruntStepHelper helper;

  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private StepHelper stepHelper;

  @Override
  public Class getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    List<EntityDetail> entityDetailList = new ArrayList<>();

    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

    TerragruntApplyStepParameters stepParametersSpec = (TerragruntApplyStepParameters) stepParameters.getSpec();
    if (stepParametersSpec.getConfiguration().getType() == TerragruntStepConfigurationType.INLINE) {
      String connectorRef = stepParametersSpec.getConfiguration()
                                .getSpec()
                                .getConfigFiles()
                                .getStore()
                                .getSpec()
                                .getConnectorReference()
                                .getValue();
      IdentifierRef identifierRef =
          IdentifierRefHelper.getIdentifierRef(connectorRef, accountId, orgIdentifier, projectIdentifier);
      EntityDetail entityDetail = EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(identifierRef).build();
      entityDetailList.add(entityDetail);

      LinkedHashMap<String, TerragruntVarFile> varFiles = stepParametersSpec.getConfiguration().getSpec().getVarFiles();
      List<EntityDetail> varFilesEntityDetails =
          TerragruntStepHelper.prepareEntityDetailsForVarFiles(accountId, orgIdentifier, projectIdentifier, varFiles);
      entityDetailList.addAll(varFilesEntityDetails);

      TerragruntBackendConfig backendConfig = stepParametersSpec.getConfiguration().getSpec().getBackendConfig();
      Optional<EntityDetail> bcFileEntityDetails = TerragruntStepHelper.prepareEntityDetailForBackendConfigFiles(
          accountId, orgIdentifier, projectIdentifier, backendConfig);
      bcFileEntityDetails.ifPresent(entityDetailList::add);
    }

    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetailList, true);
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    log.info("Starting execution ObtainTask after Rbac for the Terragrunt Apply Step");
    TerragruntApplyStepParameters stepParameters = (TerragruntApplyStepParameters) stepElementParameters.getSpec();
    helper.validateApplyStepParamsInline(stepParameters);
    TerragruntStepConfigurationType configurationType = stepParameters.getConfiguration().getType();
    switch (configurationType) {
      case INLINE:
        return obtainInlineTask(ambiance, stepParameters, stepElementParameters);
      case INHERIT_FROM_PLAN:
        return obtainInheritedTask(ambiance, stepParameters, stepElementParameters);
      default:
        throw new InvalidRequestException(
            String.format("Unknown configuration Type: [%s]", configurationType.getDisplayName()));
    }
  }

  private TaskRequest obtainInlineTask(
      Ambiance ambiance, TerragruntApplyStepParameters stepParameters, StepElementParameters stepElementParameters) {
    log.info("Obtaining Inline Task for the Apply Step");
    helper.validateApplyStepConfigFilesInline(stepParameters);
    TerragruntStepConfigurationParameters configuration = stepParameters.getConfiguration();
    TerragruntExecutionDataParameters spec = configuration.getSpec();
    TerragruntApplyTaskParametersBuilder<?, ?> builder = TerragruntApplyTaskParameters.builder();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    builder.accountId(accountId);
    String provisionerIdentifier =
        ParameterFieldHelper.getParameterFieldValue(stepParameters.getProvisionerIdentifier());
    String entityId = helper.generateFullIdentifier(provisionerIdentifier, ambiance);

    builder.tgModuleSourceInheritSSH(
        helper.isExportCredentialForSourceModule(spec.getConfigFiles(), stepElementParameters.getType()));

    builder.stateFileId(helper.getLatestFileId(entityId))
        .entityId(entityId)
        .workspace(ParameterFieldHelper.getParameterFieldValue(spec.getWorkspace()))
        .configFilesStore(helper.getGitFetchFilesConfig(
            spec.getConfigFiles().getStore().getSpec(), ambiance, TerragruntStepHelper.TG_CONFIG_FILES))
        .varFiles(helper.toStoreDelegateVarFiles(spec.getVarFiles(), ambiance))
        .backendFilesStore(helper.getBackendConfig(spec.getBackendConfig(), ambiance))
        .runConfiguration(
            TerragruntRunConfiguration.builder()
                .runType(spec.getTerragruntModuleConfig().getTerragruntRunType() == TerragruntRunType.RUN_ALL
                        ? TerragruntTaskRunType.RUN_ALL
                        : TerragruntTaskRunType.RUN_MODULE)
                .path(spec.getTerragruntModuleConfig().path.getValue())
                .build())
        .envVars(helper.getEnvironmentVariablesMap(spec.getEnvironmentVariables()))
        .targets(ParameterFieldHelper.getParameterFieldValue(spec.getTargets()))
        .timeoutInMillis(StepUtils.getTimeoutMillis(stepElementParameters.getTimeout(), DEFAULT_TIMEOUT))
        .encryptedDataDetailList(helper.getEncryptionDetails(
            spec.getConfigFiles().getStore().getSpec(), spec.getBackendConfig(), spec.getVarFiles(), ambiance))
        .build();

    return prepareCDTaskRequest(ambiance, builder, stepElementParameters, stepParameters);
  }

  private TaskRequest obtainInheritedTask(
      Ambiance ambiance, TerragruntApplyStepParameters stepParameters, StepElementParameters stepElementParameters) {
    log.info("Obtaining Inherited Task for the Apply Step");

    String provisionerIdentifier =
        ParameterFieldHelper.getParameterFieldValue(stepParameters.getProvisionerIdentifier());
    TerragruntInheritOutput inheritOutput =
        helper.getSavedInheritOutput(provisionerIdentifier, TerragruntCommandType.APPLY.name(), ambiance);

    if (TerragruntTaskRunType.RUN_ALL == inheritOutput.getRunConfiguration().getRunType()) {
      throw new InvalidRequestException(
          "Inheriting from a plan which has used \"All Modules\" at Terragrunt Plan Step is not supported");
    }

    TerragruntApplyTaskParametersBuilder<?, ?> builder = TerragruntApplyTaskParameters.builder();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String entityId = helper.generateFullIdentifier(provisionerIdentifier, ambiance);
    builder.accountId(accountId)
        .entityId(entityId)
        .tgModuleSourceInheritSSH(inheritOutput.isUseConnectorCredentials())
        .stateFileId(helper.getLatestFileId(entityId))
        .workspace(inheritOutput.getWorkspace())
        .configFilesStore(helper.getGitFetchFilesConfig(
            inheritOutput.getConfigFiles(), ambiance, TerragruntStepHelper.TG_CONFIG_FILES))
        .varFiles(helper.toStoreDelegateVarFilesFromTgConfig(inheritOutput.getVarFileConfigs(), ambiance))
        .backendFilesStore(helper.getBackendConfigFromTgConfig(inheritOutput.getBackendConfigFile(), ambiance))
        .runConfiguration(inheritOutput.getRunConfiguration())
        .targets(inheritOutput.getTargets())
        .planSecretManager(inheritOutput.encryptionConfig)
        .encryptedTfPlan(inheritOutput.encryptedPlan)
        .envVars(inheritOutput.environmentVariables)
        .encryptedDataDetailList(helper.getEncryptionDetailsFromTgInheritConfig(inheritOutput.getConfigFiles(),
            inheritOutput.getBackendConfigFile(), inheritOutput.getVarFileConfigs(), ambiance))
        .timeoutInMillis(StepUtils.getTimeoutMillis(stepElementParameters.getTimeout(), DEFAULT_TIMEOUT));
    builder.build();

    return prepareCDTaskRequest(ambiance, builder, stepElementParameters, stepParameters);
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters, ThrowingSupplier<TerragruntApplyTaskResponse> responseSupplier)
      throws Exception {
    log.info("Handling Task Result With Security Context for the Terragrunt Apply Step");
    TerragruntApplyStepParameters stepParameters = (TerragruntApplyStepParameters) stepElementParameters.getSpec();
    TerragruntStepConfigurationType configurationType = stepParameters.getConfiguration().getType();
    switch (configurationType) {
      case INLINE:
        return handleTaskResultInline(ambiance, stepParameters, responseSupplier);
      case INHERIT_FROM_PLAN:
        return handleTaskResultInherited(ambiance, stepParameters, responseSupplier);
      default:
        throw new InvalidRequestException(
            String.format("Unknown configuration Type: [%s]", configurationType.getDisplayName()));
    }
  }

  private StepResponse handleTaskResultInline(Ambiance ambiance, TerragruntApplyStepParameters stepParameters,
      ThrowingSupplier<TerragruntApplyTaskResponse> responseSupplier) throws Exception {
    log.info("Handling Task Result Inline for the Terragrunt Apply Step");
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();

    TerragruntApplyTaskResponse terragruntApplyTaskResponse = responseSupplier.get();
    if (terragruntApplyTaskResponse != null) {
      List<UnitProgress> unitProgresses = terragruntApplyTaskResponse.getUnitProgressData() == null
          ? Collections.emptyList()
          : terragruntApplyTaskResponse.getUnitProgressData().getUnitProgresses();

      stepResponseBuilder.unitProgressList(unitProgresses);
      helper.saveRollbackDestroyConfigInline(stepParameters, terragruntApplyTaskResponse, ambiance);
      addStepOutcomeToStepResponse(stepResponseBuilder, terragruntApplyTaskResponse);
      if (terragruntApplyTaskResponse.getStateFileId() != null) {
        helper.updateParentEntityIdAndVersion(
            helper.generateFullIdentifier(
                ParameterFieldHelper.getParameterFieldValue(stepParameters.getProvisionerIdentifier()), ambiance),
            terragruntApplyTaskResponse.getStateFileId());
      }
    }

    stepResponseBuilder.status(Status.SUCCEEDED);
    return stepResponseBuilder.build();
  }

  private StepResponse handleTaskResultInherited(Ambiance ambiance, TerragruntApplyStepParameters stepParameters,
      ThrowingSupplier<TerragruntApplyTaskResponse> responseSupplier) throws Exception {
    log.info("Handling Task Result Inherited for the Terragrunt Apply Step");
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();

    TerragruntApplyTaskResponse terragruntApplyTaskResponse = responseSupplier.get();
    if (terragruntApplyTaskResponse != null) {
      List<UnitProgress> unitProgresses = terragruntApplyTaskResponse.getUnitProgressData() == null
          ? Collections.emptyList()
          : terragruntApplyTaskResponse.getUnitProgressData().getUnitProgresses();
      stepResponseBuilder.unitProgressList(unitProgresses);
      helper.saveRollbackDestroyConfigInherited(stepParameters, ambiance);

      addStepOutcomeToStepResponse(stepResponseBuilder, terragruntApplyTaskResponse);
      if (terragruntApplyTaskResponse.getStateFileId() != null) {
        helper.updateParentEntityIdAndVersion(
            helper.generateFullIdentifier(
                ParameterFieldHelper.getParameterFieldValue(stepParameters.getProvisionerIdentifier()), ambiance),
            terragruntApplyTaskResponse.getStateFileId());
      }
    }

    stepResponseBuilder.status(Status.SUCCEEDED);
    return stepResponseBuilder.build();
  }

  private TaskRequest prepareCDTaskRequest(Ambiance ambiance, TerragruntApplyTaskParametersBuilder<?, ?> builder,
      StepElementParameters stepElementParameters, TerragruntApplyStepParameters stepParameters) {
    TaskData taskData = TaskData.builder()
                            .async(true)
                            .taskType(TERRAGRUNT_APPLY_TASK_NG.name())
                            .timeout(StepUtils.getTimeoutMillis(stepElementParameters.getTimeout(), DEFAULT_TIMEOUT))
                            .parameters(new Object[] {builder.build()})
                            .build();

    List<String> commandUnitsList = new ArrayList<>();
    commandUnitsList.add(FETCH_CONFIG_FILES);
    commandUnitsList.add(APPLY);

    return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer, commandUnitsList,
        TERRAGRUNT_APPLY_TASK_NG.getDisplayName(),
        TaskSelectorYaml.toTaskSelector(stepParameters.getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));
  }

  private void addStepOutcomeToStepResponse(
      StepResponseBuilder stepResponseBuilder, TerragruntApplyTaskResponse terragruntApplyTaskResponse) {
    stepResponseBuilder.stepOutcome(StepResponse.StepOutcome.builder()
                                        .name(OutcomeExpressionConstants.OUTPUT)
                                        .outcome(new TerragruntApplyOutcome(
                                            helper.parseTerragruntOutputs(terragruntApplyTaskResponse.getOutputs())))
                                        .build());
  }
}
