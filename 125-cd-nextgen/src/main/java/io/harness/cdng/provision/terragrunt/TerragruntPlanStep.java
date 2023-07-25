/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/*

  * Copyright 2022 Harness Inc. All rights reserved.
  * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
  * that can be found in the licenses directory at the root of this repository, also available at
  * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

 */

package io.harness.cdng.provision.terragrunt;
import static io.harness.provision.TerragruntConstants.FETCH_CONFIG_FILES;
import static io.harness.provision.TerragruntConstants.PLAN;

import static software.wings.beans.TaskType.TERRAGRUNT_PLAN_TASK_NG;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.provision.terraform.TerraformStepHelper;
import io.harness.cdng.provision.terraform.functor.TerraformPlanJsonFunctor;
import io.harness.cdng.provision.terragrunt.outcome.TerragruntPlanOutcome;
import io.harness.cdng.provision.terragrunt.outcome.TerragruntPlanOutcome.TerragruntPlanOutcomeBuilder;
import io.harness.common.ParameterFieldHelper;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.terragrunt.request.TerragruntCommandType;
import io.harness.delegate.beans.terragrunt.request.TerragruntPlanTaskParameters;
import io.harness.delegate.beans.terragrunt.request.TerragruntPlanTaskParameters.TerragruntPlanTaskParametersBuilder;
import io.harness.delegate.beans.terragrunt.request.TerragruntRunConfiguration;
import io.harness.delegate.beans.terragrunt.request.TerragruntTaskRunType;
import io.harness.delegate.beans.terragrunt.response.TerragruntPlanTaskResponse;
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
import io.harness.pms.yaml.ParameterField;
import io.harness.security.encryption.EncryptionConfig;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_INFRA_PROVISIONERS})
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class TerragruntPlanStep extends CdTaskExecutable<TerragruntPlanTaskResponse> {
  public static final StepType STEP_TYPE =
      TerragruntStepHelper.addStepType(ExecutionNodeType.TERRAGRUNT_PLAN.getYamlType());
  public static final String DEFAULT_TIMEOUT = "10m";

  @Inject private TerragruntStepHelper helper;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private StepHelper stepHelper;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private TerraformStepHelper terraformStepHelper;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;

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

    TerragruntPlanStepParameters stepParametersSpec = (TerragruntPlanStepParameters) stepParameters.getSpec();

    String connectorRef =
        stepParametersSpec.configuration.configFiles.store.getSpec().getConnectorReference().getValue();
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(connectorRef, accountId, orgIdentifier, projectIdentifier);
    EntityDetail entityDetail = EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(identifierRef).build();
    entityDetailList.add(entityDetail);

    LinkedHashMap<String, TerragruntVarFile> varFiles = stepParametersSpec.getConfiguration().getVarFiles();
    List<EntityDetail> varFilesEntityDetails =
        TerragruntStepHelper.prepareEntityDetailsForVarFiles(accountId, orgIdentifier, projectIdentifier, varFiles);
    entityDetailList.addAll(varFilesEntityDetails);

    TerragruntBackendConfig backendConfig = stepParametersSpec.getConfiguration().getBackendConfig();
    Optional<EntityDetail> bcFileEntityDetails = TerragruntStepHelper.prepareEntityDetailForBackendConfigFiles(
        accountId, orgIdentifier, projectIdentifier, backendConfig);
    bcFileEntityDetails.ifPresent(entityDetailList::add);

    String secretManagerRef = stepParametersSpec.getConfiguration().getSecretManagerRef().getValue();
    IdentifierRef secretManagerIdentifierRef =
        IdentifierRefHelper.getIdentifierRef(secretManagerRef, accountId, orgIdentifier, projectIdentifier);
    entityDetail = EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(secretManagerIdentifierRef).build();
    entityDetailList.add(entityDetail);

    terraformStepHelper.validateSecretManager(ambiance, secretManagerIdentifierRef);
    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetailList, true);
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    log.info("Starting execution ObtainTask after Rbac for the Terragrunt Plan Step");
    TerragruntPlanStepParameters planStepParameters = (TerragruntPlanStepParameters) stepParameters.getSpec();
    helper.validatePlanStepConfigFiles(planStepParameters);
    TerragruntPlanExecutionDataParameters configuration = planStepParameters.getConfiguration();

    TerragruntPlanTaskParametersBuilder<?, ?> builder = TerragruntPlanTaskParameters.builder();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    builder.accountId(accountId);
    String entityId = helper.generateFullIdentifier(
        ParameterFieldHelper.getParameterFieldValue(planStepParameters.getProvisionerIdentifier()), ambiance);
    builder.tgModuleSourceInheritSSH(
        helper.isExportCredentialForSourceModule(configuration.getConfigFiles(), stepParameters.getType()));
    ParameterField<Boolean> exportTgPlanJsonField = planStepParameters.getConfiguration().getExportTerragruntPlanJson();

    EncryptionConfig planSecretManagerConfig = helper.getEncryptionConfig(ambiance, planStepParameters);
    builder.entityId(entityId)
        .workspace(ParameterFieldHelper.getParameterFieldValue(configuration.getWorkspace()))
        .configFilesStore(helper.getGitFetchFilesConfig(
            configuration.getConfigFiles().getStore().getSpec(), ambiance, TerragruntStepHelper.TG_CONFIG_FILES))
        .varFiles(helper.toStoreDelegateVarFiles(configuration.getVarFiles(), ambiance))
        .backendFilesStore(helper.getBackendConfig(configuration.getBackendConfig(), ambiance))
        .runConfiguration(
            TerragruntRunConfiguration.builder()
                .runType(planStepParameters.getConfiguration().getTerragruntModuleConfig().getTerragruntRunType()
                            == TerragruntRunType.RUN_ALL
                        ? TerragruntTaskRunType.RUN_ALL
                        : TerragruntTaskRunType.RUN_MODULE)
                .path(planStepParameters.getConfiguration().getTerragruntModuleConfig().path.getValue())
                .build())
        .targets(ParameterFieldHelper.getParameterFieldValue(configuration.getTargets()))
        .envVars(helper.getEnvironmentVariablesMap(configuration.getEnvironmentVariables()))
        .commandType(TerragruntPlanCommand.APPLY == planStepParameters.getConfiguration().getCommand()
                ? TerragruntCommandType.APPLY
                : TerragruntCommandType.DESTROY)
        .exportJsonPlan(!ParameterField.isNull(exportTgPlanJsonField)
            && ParameterFieldHelper.getBooleanParameterFieldValue(exportTgPlanJsonField))
        .planSecretManager(planSecretManagerConfig)
        .stateFileId(helper.getLatestFileId(entityId))
        .planName(helper.getTerragruntPlanName(planStepParameters.getConfiguration().getCommand(), ambiance,
            planStepParameters.getProvisionerIdentifier().getValue()))
        .timeoutInMillis(StepUtils.getTimeoutMillis(stepParameters.getTimeout(), DEFAULT_TIMEOUT))
        .encryptedDataDetailList(helper.getEncryptionDetails(configuration.getConfigFiles().getStore().getSpec(),
            configuration.getBackendConfig(), configuration.getVarFiles(), ambiance))
        .encryptDecryptPlanForHarnessSMOnManager(helper.tfPlanEncryptionOnManager(accountId, planSecretManagerConfig))
        .build();

    TaskData taskData = TaskData.builder()
                            .async(true)
                            .taskType(TERRAGRUNT_PLAN_TASK_NG.name())
                            .timeout(StepUtils.getTimeoutMillis(stepParameters.getTimeout(), DEFAULT_TIMEOUT))
                            .parameters(new Object[] {builder.build()})
                            .build();

    List<String> commandUnitsList = new ArrayList<>();
    commandUnitsList.add(FETCH_CONFIG_FILES);
    commandUnitsList.add(PLAN);

    return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer, commandUnitsList,
        TERRAGRUNT_PLAN_TASK_NG.getDisplayName(),
        TaskSelectorYaml.toTaskSelector(planStepParameters.getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters, ThrowingSupplier<TerragruntPlanTaskResponse> responseSupplier)
      throws Exception {
    log.info("Handling Task result with Security Context for the Terragrunt Plan Step");
    TerragruntPlanStepParameters planStepParameters = (TerragruntPlanStepParameters) stepElementParameters.getSpec();
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();
    TerragruntPlanTaskResponse terragruntTaskNGResponse = responseSupplier.get();

    List<UnitProgress> unitProgresses = terragruntTaskNGResponse.getUnitProgressData() == null
        ? Collections.emptyList()
        : terragruntTaskNGResponse.getUnitProgressData().getUnitProgresses();
    stepResponseBuilder.unitProgressList(unitProgresses);

    TerragruntPlanOutcomeBuilder tgPlanOutcomeBuilder = TerragruntPlanOutcome.builder();
    String provisionerIdentifier =
        ParameterFieldHelper.getParameterFieldValue(planStepParameters.getProvisionerIdentifier());

    helper.saveTerragruntInheritOutput(planStepParameters, terragruntTaskNGResponse, ambiance);

    if (terragruntTaskNGResponse.getStateFileId() != null) {
      helper.updateParentEntityIdAndVersion(
          helper.generateFullIdentifier(
              ParameterFieldHelper.getParameterFieldValue(planStepParameters.getProvisionerIdentifier()), ambiance),
          terragruntTaskNGResponse.getStateFileId());
    }

    ParameterField<Boolean> exportTgPlanJsonField = planStepParameters.getConfiguration().getExportTerragruntPlanJson();
    boolean exportTgPlanJson = !ParameterField.isNull(exportTgPlanJsonField)
        && ParameterFieldHelper.getBooleanParameterFieldValue(exportTgPlanJsonField);

    if (exportTgPlanJson && terragruntTaskNGResponse.getEncryptedPlan() != null) {
      helper.saveTerragruntPlanExecutionDetails(
          ambiance, terragruntTaskNGResponse, provisionerIdentifier, planStepParameters);

      String planJsonOutputName =
          helper.saveTerraformPlanJsonOutput(ambiance, terragruntTaskNGResponse, provisionerIdentifier);

      if (planJsonOutputName != null) {
        tgPlanOutcomeBuilder.jsonFilePath(
            TerraformPlanJsonFunctor.getExpression(planStepParameters.getStepFqn(), planJsonOutputName));
      }
    }

    stepResponseBuilder.stepOutcome(StepResponse.StepOutcome.builder()
                                        .name(TerragruntPlanOutcome.OUTCOME_NAME)
                                        .outcome(tgPlanOutcomeBuilder.build())
                                        .build());
    stepResponseBuilder.status(Status.SUCCEEDED);

    return stepResponseBuilder.build();
  }
}
