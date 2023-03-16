/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terragrunt;

import static io.harness.cdng.provision.terragrunt.TerragruntStepHelper.DEFAULT_TIMEOUT;
import static io.harness.provision.TerragruntConstants.DESTROY;
import static io.harness.provision.TerragruntConstants.FETCH_CONFIG_FILES;

import static software.wings.beans.TaskType.TERRAGRUNT_DESTROY_TASK_NG;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.common.ParameterFieldHelper;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.terragrunt.request.TerragruntCommandType;
import io.harness.delegate.beans.terragrunt.request.TerragruntDestroyTaskParameters;
import io.harness.delegate.beans.terragrunt.request.TerragruntDestroyTaskParameters.TerragruntDestroyTaskParametersBuilder;
import io.harness.delegate.beans.terragrunt.request.TerragruntRunConfiguration;
import io.harness.delegate.beans.terragrunt.request.TerragruntTaskRunType;
import io.harness.delegate.beans.terragrunt.response.TerragruntDestroyTaskResponse;
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

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class TerragruntDestroyStep extends CdTaskExecutable<TerragruntDestroyTaskResponse> {
  public static final StepType STEP_TYPE =
      TerragruntStepHelper.addStepType(ExecutionNodeType.TERRAGRUNT_DESTROY.getYamlType());

  @Inject private TerragruntStepHelper helper;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private StepHelper stepHelper;
  @Inject public TerragruntConfigDAL terragruntConfigDAL;

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

    TerragruntDestroyStepParameters stepParametersSpec = (TerragruntDestroyStepParameters) stepParameters.getSpec();
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
    log.info("Starting execution ObtainTask after Rbac for the Terragrunt Destroy Step");
    TerragruntDestroyStepParameters stepParameters = (TerragruntDestroyStepParameters) stepElementParameters.getSpec();
    helper.validateDestroyStepParamsInline(stepParameters);
    TerragruntStepConfigurationType configurationType = stepParameters.getConfiguration().getType();
    switch (configurationType) {
      case INLINE:
        return obtainInlineTask(ambiance, stepParameters, stepElementParameters);
      case INHERIT_FROM_PLAN:
        return obtainInheritedTask(ambiance, stepParameters, stepElementParameters);
      case INHERIT_FROM_APPLY:
        return obtainLastApplyTask(ambiance, stepParameters, stepElementParameters);
      default:
        throw new InvalidRequestException(
            String.format("Unknown configuration Type: [%s]", configurationType.getDisplayName()));
    }
  }

  private TaskRequest obtainInlineTask(
      Ambiance ambiance, TerragruntDestroyStepParameters stepParameters, StepElementParameters stepElementParameters) {
    log.info("Obtaining Inline Task for Terragrunt Destroy Step");
    helper.validateDestroyStepParamsInline(stepParameters);
    TerragruntStepConfigurationParameters configuration = stepParameters.getConfiguration();
    TerragruntExecutionDataParameters spec = configuration.getSpec();
    TerragruntDestroyTaskParametersBuilder<?, ?> builder = TerragruntDestroyTaskParameters.builder();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    builder.accountId(accountId);
    String provisionerIdentifier =
        ParameterFieldHelper.getParameterFieldValue(stepParameters.getProvisionerIdentifier());
    String entityId = helper.generateFullIdentifier(provisionerIdentifier, ambiance);

    builder.stateFileId(helper.getLatestFileId(entityId))
        .entityId(entityId)
        .tgModuleSourceInheritSSH(helper.isExportCredentialForSourceModule(
            configuration.getSpec().getConfigFiles(), stepElementParameters.getType()))
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
      Ambiance ambiance, TerragruntDestroyStepParameters stepParameters, StepElementParameters stepElementParameters) {
    log.info("Obtaining Inherited Task for the Destroy Step");

    String provisionerIdentifier =
        ParameterFieldHelper.getParameterFieldValue(stepParameters.getProvisionerIdentifier());
    TerragruntInheritOutput inheritOutput =
        helper.getSavedInheritOutput(provisionerIdentifier, TerragruntCommandType.DESTROY.name(), ambiance);

    if (TerragruntTaskRunType.RUN_ALL == inheritOutput.getRunConfiguration().getRunType()) {
      throw new InvalidRequestException(
          "Inheriting from a plan which has used \"All Modules\" at Terragrunt Plan Step is not supported");
    }

    TerragruntDestroyTaskParametersBuilder<?, ?> builder = TerragruntDestroyTaskParameters.builder();
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

  private TaskRequest obtainLastApplyTask(
      Ambiance ambiance, TerragruntDestroyStepParameters stepParameters, StepElementParameters stepElementParameters) {
    log.info("Getting the Last Apply Task for the Destroy Step");

    String provisionerIdentifier =
        ParameterFieldHelper.getParameterFieldValue(stepParameters.getProvisionerIdentifier());

    TerragruntConfig terragruntConfig = helper.getLastSuccessfulApplyConfig(stepParameters, ambiance);

    TerragruntDestroyTaskParametersBuilder<?, ?> builder = TerragruntDestroyTaskParameters.builder();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String entityId = helper.generateFullIdentifier(provisionerIdentifier, ambiance);
    builder.accountId(accountId)
        .entityId(entityId)
        .stateFileId(helper.getLatestFileId(entityId))
        .tgModuleSourceInheritSSH(terragruntConfig.isUseConnectorCredentials())
        .workspace(terragruntConfig.getWorkspace())
        .configFilesStore(helper.getGitFetchFilesConfig(
            terragruntConfig.getConfigFiles().toGitStoreConfig(), ambiance, TerragruntStepHelper.TG_CONFIG_FILES))
        .varFiles(helper.toStoreDelegateVarFilesFromTgConfig(terragruntConfig.getVarFileConfigs(), ambiance))
        .backendFilesStore(helper.getBackendConfigFromTgConfig(terragruntConfig.getBackendConfigFile(), ambiance))
        .runConfiguration(terragruntConfig.getRunConfiguration())
        .targets(terragruntConfig.getTargets())
        .envVars(terragruntConfig.environmentVariables)
        .encryptedDataDetailList(
            helper.getEncryptionDetailsFromTgInheritConfig(terragruntConfig.getConfigFiles().toGitStoreConfig(),
                terragruntConfig.getBackendConfigFile(), terragruntConfig.getVarFileConfigs(), ambiance))
        .timeoutInMillis(StepUtils.getTimeoutMillis(stepElementParameters.getTimeout(), DEFAULT_TIMEOUT));
    builder.build();
    return prepareCDTaskRequest(ambiance, builder, stepElementParameters, stepParameters);
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters, ThrowingSupplier<TerragruntDestroyTaskResponse> responseSupplier)
      throws Exception {
    log.info("Handling Task Result for the Terragrunt Destroy Step");
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();

    TerragruntDestroyStepParameters parameters = (TerragruntDestroyStepParameters) stepElementParameters.getSpec();

    TerragruntDestroyTaskResponse terragruntDestroyTaskResponse = responseSupplier.get();
    if (terragruntDestroyTaskResponse != null) {
      List<UnitProgress> unitProgresses = terragruntDestroyTaskResponse.getUnitProgressData() == null
          ? Collections.emptyList()
          : terragruntDestroyTaskResponse.getUnitProgressData().getUnitProgresses();

      stepResponseBuilder.unitProgressList(unitProgresses);

      terragruntConfigDAL.clearTerragruntConfig(ambiance,
          helper.generateFullIdentifier(
              ParameterFieldHelper.getParameterFieldValue(parameters.getProvisionerIdentifier()), ambiance));
      if (terragruntDestroyTaskResponse.getStateFileId() != null) {
        helper.updateParentEntityIdAndVersion(
            helper.generateFullIdentifier(
                ParameterFieldHelper.getParameterFieldValue(parameters.getProvisionerIdentifier()), ambiance),
            terragruntDestroyTaskResponse.getStateFileId());
      }
    }
    stepResponseBuilder.status(Status.SUCCEEDED);
    return stepResponseBuilder.build();
  }

  private TaskRequest prepareCDTaskRequest(Ambiance ambiance, TerragruntDestroyTaskParametersBuilder<?, ?> builder,
      StepElementParameters stepElementParameters, TerragruntDestroyStepParameters stepParameters) {
    TaskData taskData = TaskData.builder()
                            .async(true)
                            .taskType(TERRAGRUNT_DESTROY_TASK_NG.name())
                            .timeout(StepUtils.getTimeoutMillis(stepElementParameters.getTimeout(), DEFAULT_TIMEOUT))
                            .parameters(new Object[] {builder.build()})
                            .build();

    List<String> commandUnitsList = new ArrayList<>();
    commandUnitsList.add(FETCH_CONFIG_FILES);
    commandUnitsList.add(DESTROY);

    return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer, commandUnitsList,
        TERRAGRUNT_DESTROY_TASK_NG.getDisplayName(),
        TaskSelectorYaml.toTaskSelector(stepParameters.getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));
  }
}
