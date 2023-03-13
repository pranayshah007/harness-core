/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudConstants.TFC_DESTROY_PLAN_NAME_PREFIX_NG;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudConstants.TFC_OUTPUT_FORMAT;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudConstants.TFC_PLAN_NAME_PREFIX_NG;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudRunType.APPLY;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudRunType.PLAN;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudRunType.PLAN_AND_APPLY;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudRunType.PLAN_AND_DESTROY;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudRunType.PLAN_ONLY;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.listener.NgOrchestrationNotifyEventListener.NG_ORCHESTRATION;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.Scope;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.fileservice.FileServiceClientFactory;
import io.harness.cdng.pipeline.executions.TerraformCloudCleanupTaskNotifyCallback;
import io.harness.cdng.provision.terraform.executions.RunDetails;
import io.harness.cdng.provision.terraform.executions.TerraformCloudPlanExecutionDetails;
import io.harness.cdng.provision.terraform.executions.TerraformCloudPlanExecutionDetails.TerraformCloudPlanExecutionDetailsKeys;
import io.harness.cdng.provision.terraformcloud.dal.TerraformCloudConfig;
import io.harness.cdng.provision.terraformcloud.dal.TerraformCloudConfigDAL;
import io.harness.cdng.provision.terraformcloud.executiondetails.TerraformCloudPlanExecutionDetailsService;
import io.harness.cdng.provision.terraformcloud.output.TerraformCloudPlanOutput;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudApplySpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudPlanAndApplySpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudPlanAndDestroySpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudPlanOnlySpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudPlanSpecParameters;
import io.harness.common.ParameterFieldHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.task.terraformcloud.cleanup.TerraformCloudCleanupTaskParams;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.CGRestUtils;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.TaskType;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

@Slf4j
@Singleton
@OwnedBy(CDP)
public class TerraformCloudStepHelper {
  @Inject private CDStepHelper cdStepHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private TerraformCloudPlanExecutionDetailsService terraformCloudPlanExecutionDetailsService;
  @Inject private TerraformCloudConfigDAL terraformCloudConfigDAL;
  @Inject private FileServiceClientFactory fileService;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private EncryptionHelper encryptionHelper;

  public String generateFullIdentifier(String provisionerIdentifier, Ambiance ambiance) {
    if (Pattern.matches(NGRegexValidatorConstants.IDENTIFIER_PATTERN, provisionerIdentifier)) {
      return format("%s/%s/%s/%s", AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
          AmbianceUtils.getProjectIdentifier(ambiance), provisionerIdentifier);
    } else {
      throw new InvalidRequestException(
          format("Provisioner Identifier cannot contain special characters or spaces: [%s]", provisionerIdentifier));
    }
  }

  public TerraformCloudConnectorDTO getTerraformCloudConnector(
      TerraformCloudRunSpecParameters terraformCloudRunSpecParameters, Ambiance ambiance) {
    ConnectorConfigDTO connectorConfigDTO;
    if (terraformCloudRunSpecParameters.getType() != TerraformCloudRunType.APPLY) {
      connectorConfigDTO =
          terraformCloudRunSpecParameters.extractConnectorRefs()
              .stream()
              .map(connectorField
                  -> cdStepHelper.getConnector(ParameterFieldHelper.getParameterFieldValue(connectorField), ambiance))
              .filter(connectorInfoDTO -> connectorInfoDTO.getConnectorType() == ConnectorType.TERRAFORM_CLOUD)
              .map(ConnectorInfoDTO::getConnectorConfig)
              .findFirst()
              .orElse(null);
    } else {
      TerraformCloudPlanOutput savedTerraformCloudPlanOutput = getSavedTerraformCloudOutput(
          getProvisionIdentifier(terraformCloudRunSpecParameters), PLAN.getDisplayName(), ambiance);

      connectorConfigDTO =
          cdStepHelper.getConnector(savedTerraformCloudPlanOutput.getTerraformCloudConnectorRef(), ambiance)
              .getConnectorConfig();
    }
    return (TerraformCloudConnectorDTO) connectorConfigDTO;
  }

  public TerraformCloudConnectorDTO getTerraformCloudConnectorWithRef(String connectorRef, Ambiance ambiance) {
    return (TerraformCloudConnectorDTO) cdStepHelper.getConnector(connectorRef, ambiance).getConnectorConfig();
  }

  public void saveTerraformCloudPlanOutput(
      TerraformCloudPlanSpecParameters planSpecParameters, String runId, Ambiance ambiance) {
    planSpecParameters.validate();

    TerraformCloudPlanOutput terraformCloudPlanOutput =
        TerraformCloudPlanOutput.builder()
            .runId(runId)
            .terraformCloudConnectorRef(
                ParameterFieldHelper.getParameterFieldValue(planSpecParameters.getConnectorRef()))
            .build();

    String fullEntityId = generateFullIdentifier(
        ParameterFieldHelper.getParameterFieldValue(planSpecParameters.getProvisionerIdentifier()), ambiance);
    String outputName = format(TFC_OUTPUT_FORMAT, PLAN.getDisplayName(), fullEntityId);
    executionSweepingOutputService.consume(
        ambiance, outputName, terraformCloudPlanOutput, StepOutcomeGroup.STAGE.name());
  }

  public String getTerraformPlanName(PlanType planType, Ambiance ambiance, String provisionId) {
    String prefix = PlanType.APPLY == planType ? TFC_PLAN_NAME_PREFIX_NG : TFC_DESTROY_PLAN_NAME_PREFIX_NG;
    return format(prefix, ambiance.getPlanExecutionId(), provisionId).replace("_", "-");
  }

  public String getProvisionIdentifier(TerraformCloudRunSpecParameters runSpec) {
    ParameterField<String> provisionerIdentifier;
    switch (runSpec.getType()) {
      case PLAN_ONLY:
        TerraformCloudPlanOnlySpecParameters planOnlySpecParameters = (TerraformCloudPlanOnlySpecParameters) runSpec;
        provisionerIdentifier = planOnlySpecParameters.getProvisionerIdentifier();
        break;
      case PLAN_AND_APPLY:
        TerraformCloudPlanAndApplySpecParameters planAndApplySpecParameters =
            (TerraformCloudPlanAndApplySpecParameters) runSpec;
        provisionerIdentifier = planAndApplySpecParameters.getProvisionerIdentifier();
        break;
      case PLAN_AND_DESTROY:
        TerraformCloudPlanAndDestroySpecParameters planAndDestroySpecParameters =
            (TerraformCloudPlanAndDestroySpecParameters) runSpec;
        provisionerIdentifier = planAndDestroySpecParameters.getProvisionerIdentifier();
        break;
      case PLAN:
        TerraformCloudPlanSpecParameters planSpecParameters = (TerraformCloudPlanSpecParameters) runSpec;
        provisionerIdentifier = planSpecParameters.getProvisionerIdentifier();
        break;
      case APPLY:
        TerraformCloudApplySpecParameters applySpecParameters = (TerraformCloudApplySpecParameters) runSpec;
        provisionerIdentifier = applySpecParameters.getProvisionerIdentifier();
        break;
      default:
        throw new InvalidRequestException(format("Can't get provisioner for type: [%s]", runSpec.getType()));
    }
    return ParameterFieldHelper.getParameterFieldValue(provisionerIdentifier);
  }

  public void saveTerraformCloudPlanExecutionDetails(Ambiance ambiance, String planFileJsonId,
      String policyCheckFileJsonId, String provisionerIdentifier, RunDetails runDetails) {
    TerraformCloudPlanExecutionDetails terraformPlanExecutionDetails =
        TerraformCloudPlanExecutionDetails.builder()
            .accountIdentifier(AmbianceUtils.getAccountId(ambiance))
            .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
            .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
            .pipelineExecutionId(ambiance.getPlanExecutionId())
            .stageExecutionId(ambiance.getStageExecutionId())
            .provisionerId(provisionerIdentifier)
            .tfPlanJsonFieldId(planFileJsonId)
            .tfPlanFileBucket(FileBucket.TERRAFORM_PLAN_JSON.name())
            .tfcPolicyChecksFileId(policyCheckFileJsonId)
            .tfcPolicyChecksFileBucket(FileBucket.TERRAFORM_CLOUD_POLICY_CHECKS.name())
            .runDetails(runDetails)
            .build();

    terraformCloudPlanExecutionDetailsService.save(terraformPlanExecutionDetails);
  }

  public void updateRunDetails(Ambiance ambiance, String runId) {
    String planExecutionId = ambiance.getPlanExecutionId();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    Map<String, Object> updates = new HashMap<>();
    updates.put(TerraformCloudPlanExecutionDetailsKeys.runDetails, null);

    terraformCloudPlanExecutionDetailsService.updateTerraformCloudPlanExecutionDetails(
        Scope.builder().accountIdentifier(accountId).orgIdentifier(orgId).projectIdentifier(projectId).build(),
        planExecutionId, runId, updates);
  }

  public void cleanupTfPlanJson(List<TerraformCloudPlanExecutionDetails> terraformCloudPlanExecutionDetailsList) {
    for (TerraformCloudPlanExecutionDetails terraformCloudPlanExecutionDetails :
        terraformCloudPlanExecutionDetailsList) {
      if (isNotEmpty(terraformCloudPlanExecutionDetails.getTfPlanJsonFieldId())
          && isNotEmpty(terraformCloudPlanExecutionDetails.getTfPlanFileBucket())) {
        FileBucket fileBucket = FileBucket.valueOf(terraformCloudPlanExecutionDetails.getTfPlanFileBucket());
        try {
          log.info("Remove terraform cloud plan json file [{}] from bucket [{}] for provisioner [{}]",
              terraformCloudPlanExecutionDetails.getTfPlanJsonFieldId(), fileBucket,
              terraformCloudPlanExecutionDetails.getProvisionerId());
          CGRestUtils.getResponse(
              fileService.get().deleteFile(terraformCloudPlanExecutionDetails.getTfPlanJsonFieldId(), fileBucket));
        } catch (Exception e) {
          log.warn("Failed to remove terraform cloud plan json file [{}] for provisioner [{}]",
              terraformCloudPlanExecutionDetails.getTfPlanJsonFieldId(),
              terraformCloudPlanExecutionDetails.getProvisionerId(), e);
        }
      }
    }
  }

  public void cleanupPolicyCheckJson(List<TerraformCloudPlanExecutionDetails> terraformCloudPlanExecutionDetailsList) {
    for (TerraformCloudPlanExecutionDetails terraformCloudPlanExecutionDetails :
        terraformCloudPlanExecutionDetailsList) {
      if (isNotEmpty(terraformCloudPlanExecutionDetails.getTfcPolicyChecksFileId())
          && isNotEmpty(terraformCloudPlanExecutionDetails.getTfcPolicyChecksFileBucket())) {
        FileBucket fileBucket = FileBucket.valueOf(terraformCloudPlanExecutionDetails.getTfcPolicyChecksFileBucket());
        try {
          log.info("Remove terraform policy check json file [{}] from bucket [{}] for provisioner [{}]",
              terraformCloudPlanExecutionDetails.getTfcPolicyChecksFileId(), fileBucket,
              terraformCloudPlanExecutionDetails.getProvisionerId());
          CGRestUtils.getResponse(
              fileService.get().deleteFile(terraformCloudPlanExecutionDetails.getTfcPolicyChecksFileId(), fileBucket));
        } catch (Exception e) {
          log.warn("Failed to remove terraform policy check json file [{}] for provisioner [{}]",
              terraformCloudPlanExecutionDetails.getTfcPolicyChecksFileId(),
              terraformCloudPlanExecutionDetails.getProvisionerId(), e);
        }
      }
    }
  }

  public List<EncryptedDataDetail> getEncryptionDetail(
      Ambiance ambiance, TerraformCloudConnectorDTO terraformCloudConnector) {
    return encryptionHelper.getEncryptionDetail(terraformCloudConnector.getCredential().getSpec(),
        AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
        AmbianceUtils.getProjectIdentifier(ambiance));
  }

  public void cleanupTerraformCloudRuns(
      List<TerraformCloudPlanExecutionDetails> terraformCloudPlanExecutionDetailsList, Ambiance ambiance) {
    for (TerraformCloudPlanExecutionDetails terraformCloudPlanExecutionDetails :
        terraformCloudPlanExecutionDetailsList) {
      if (terraformCloudPlanExecutionDetails.getRunDetails() != null) {
        try {
          runCleanupTerraformCloudTask(ambiance, terraformCloudPlanExecutionDetails.getRunDetails());
        } catch (Exception e) {
          log.error(String.format(
              "Failed to do cleanup for accountId: %s, organization: %s, project: %s, execution: %s, runId: %s",
              terraformCloudPlanExecutionDetails.getAccountIdentifier(),
              terraformCloudPlanExecutionDetails.getOrgIdentifier(),
              terraformCloudPlanExecutionDetails.getProjectIdentifier(),
              terraformCloudPlanExecutionDetails.getPipelineExecutionId(),
              terraformCloudPlanExecutionDetails.getRunDetails().getRunId()));
        }
      }
    }
  }

  private void runCleanupTerraformCloudTask(Ambiance ambiance, RunDetails runDetails) {
    TerraformCloudConnectorDTO terraformCloudConnector =
        (TerraformCloudConnectorDTO) cdStepHelper.getConnector(runDetails.getConnectorRef(), ambiance)
            .getConnectorConfig();
    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .taskParameters(TerraformCloudCleanupTaskParams.builder()
                                .terraformCloudConnectorDTO(terraformCloudConnector)
                                .encryptionDetails(getEncryptionDetail(ambiance, terraformCloudConnector))
                                .runId(runDetails.getRunId())
                                .build())
            .taskType(TaskType.TERRAFORM_CLOUD_CLEANUP_TASK_NG.name())
            .executionTimeout(Duration.ofMinutes(10))
            .taskSetupAbstraction(SetupAbstractionKeys.ng, "true")
            .logStreamingAbstractions(new LinkedHashMap<>() {
              { put(SetupAbstractionKeys.accountId, AmbianceUtils.getAccountId(ambiance)); }
            })
            .build();

    String taskId = delegateGrpcClientWrapper.submitAsyncTaskV2(delegateTaskRequest, Duration.ZERO);
    log.info("Task Successfully queued with taskId: {}", taskId);
    waitNotifyEngine.waitForAllOn(NG_ORCHESTRATION, new TerraformCloudCleanupTaskNotifyCallback(), taskId);
  }

  private TerraformCloudPlanOutput getSavedTerraformCloudOutput(
      String provisionerIdentifier, String command, Ambiance ambiance) {
    String fullEntityId = generateFullIdentifier(provisionerIdentifier, ambiance);
    OptionalSweepingOutput output = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(format(TFC_OUTPUT_FORMAT, command, fullEntityId)));
    if (!output.isFound()) {
      throw new InvalidRequestException(
          format("Did not find any Plan step for provisioner identifier: [%s]", provisionerIdentifier));
    }

    return (TerraformCloudPlanOutput) output.getOutput();
  }

  public String getPlanRunId(String provisionerId, Ambiance ambiance) {
    return getSavedTerraformCloudOutput(provisionerId, PLAN.getDisplayName(), ambiance).getRunId();
  }

  public Map<String, Object> parseTerraformOutputs(String terraformOutputString) {
    Map<String, Object> outputs = new LinkedHashMap<>();
    if (isEmpty(terraformOutputString)) {
      return outputs;
    }
    try {
      TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {};
      Map<String, Object> json = new ObjectMapper().readValue(IOUtils.toInputStream(terraformOutputString), typeRef);

      json.forEach((key, object) -> outputs.put(key, ((Map<String, Object>) object).get("value")));

    } catch (IOException exception) {
      log.error("", exception);
    }
    return outputs;
  }

  public void saveTerraformCloudConfig(
      TerraformCloudRunSpecParameters spec, String workspaceId, String lastSuccessfulRun, Ambiance ambiance) {
    String connectorRef;
    String provisionIdentifier = getProvisionIdentifier(spec);
    TerraformCloudRunType type = spec.getType();
    if (type == PLAN_AND_APPLY) {
      TerraformCloudPlanAndApplySpecParameters planAndApplySpecParameters =
          (TerraformCloudPlanAndApplySpecParameters) spec;
      connectorRef = ParameterFieldHelper.getParameterFieldValue(planAndApplySpecParameters.getConnectorRef());
    } else if (type == PLAN_AND_DESTROY) {
      TerraformCloudPlanAndDestroySpecParameters planAndDestroySpecParameters =
          (TerraformCloudPlanAndDestroySpecParameters) spec;
      connectorRef = ParameterFieldHelper.getParameterFieldValue(planAndDestroySpecParameters.getConnectorRef());
    } else if (type == APPLY) {
      TerraformCloudPlanOutput savedTerraformCloudPlanOutput =
          getSavedTerraformCloudOutput(provisionIdentifier, PLAN.getDisplayName(), ambiance);
      connectorRef = savedTerraformCloudPlanOutput.getTerraformCloudConnectorRef();
    } else {
      throw new InvalidRequestException(format("Can't save Terraform Cloud Config for type: [%s]", type));
    }
    terraformCloudConfigDAL.saveTerraformCloudConfig(TerraformCloudConfig.builder()
                                                         .accountId(AmbianceUtils.getAccountId(ambiance))
                                                         .orgId(AmbianceUtils.getOrgIdentifier(ambiance))
                                                         .projectId(AmbianceUtils.getProjectIdentifier(ambiance))
                                                         .provisionerIdentifier(provisionIdentifier)
                                                         .stageExecutionId(ambiance.getStageExecutionId())
                                                         .connectorRef(connectorRef)
                                                         .lastSuccessfulRun(lastSuccessfulRun)
                                                         .workspaceId(workspaceId)
                                                         .build());
  }
}
