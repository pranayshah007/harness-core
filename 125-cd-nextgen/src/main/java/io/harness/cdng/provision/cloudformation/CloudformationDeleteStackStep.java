/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.cloudformation;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.provision.cloudformation.beans.CloudFormationInheritOutput;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.task.cloudformation.CloudformationCommandUnit;
import io.harness.delegate.task.cloudformation.CloudformationTaskNGParameters;
import io.harness.delegate.task.cloudformation.CloudformationTaskNGParameters.CloudformationTaskNGParametersBuilder;
import io.harness.delegate.task.cloudformation.CloudformationTaskNGResponse;
import io.harness.delegate.task.cloudformation.CloudformationTaskType;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.utils.IdentifierRefHelper;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class CloudformationDeleteStackStep extends CdTaskExecutable<CloudformationTaskNGResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.CLOUDFORMATION_DELETE_STACK.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject private CloudformationStepHelper cloudFormationStepHelper;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private StepHelper stepHelper;
  @Inject private CloudformationConfigDAL cloudformationConfigDAL;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    List<EntityDetail> entityDetailList = new ArrayList<>();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

    CloudformationDeleteStackStepParameters parameters =
        (CloudformationDeleteStackStepParameters) stepParameters.getSpec();
    if (parameters.getConfiguration().getType().equals(CloudformationDeleteStackStepConfigurationTypes.Inline)) {
      String connectorRef = ((InlineCloudformationDeleteStackStepConfiguration) parameters.getConfiguration().getSpec())
                                .getConnectorRef()
                                .getValue();
      IdentifierRef identifierRef =
          IdentifierRefHelper.getIdentifierRef(connectorRef, accountId, orgIdentifier, projectIdentifier);
      EntityDetail entityDetail = EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(identifierRef).build();
      entityDetailList.add(entityDetail);
      pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetailList, true);
    }
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepElementParameters stepParameters, ThrowingSupplier<CloudformationTaskNGResponse> responseDataSupplier)
      throws Exception {
    log.info("Handling Task Result With Security Context for the DeleteStack Step");
    StepResponseBuilder builder = StepResponse.builder();
    CloudformationTaskNGResponse response = responseDataSupplier.get();
    List<UnitProgress> unitProgresses = response.getUnitProgressData() == null
        ? Collections.emptyList()
        : response.getUnitProgressData().getUnitProgresses();
    builder.unitProgressList(unitProgresses);
    if (CommandExecutionStatus.SUCCESS == response.getCommandExecutionStatus()) {
      builder.status(Status.SUCCEEDED);
    } else {
      builder.status(Status.FAILED);
    }
    return builder.build();
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    CloudformationDeleteStackStepParameters parameters =
        (CloudformationDeleteStackStepParameters) stepParameters.getSpec();
    log.info("Starting execution Obtain Task after Rbac for the DeleteStack Step");

    String connectorRef;
    String region;
    String roleArn;
    String stackName;
    if (parameters.getConfiguration().getType().equals(CloudformationDeleteStackStepConfigurationTypes.Inline)) {
      log.info("Obtaining task using Inline configuration");
      InlineCloudformationDeleteStackStepConfiguration configuration =
          (InlineCloudformationDeleteStackStepConfiguration) parameters.getConfiguration().getSpec();
      configuration.validateParams();

      connectorRef = getParameterFieldValue(configuration.getConnectorRef());
      region = getParameterFieldValue(configuration.getRegion());
      roleArn = getParameterFieldValue(configuration.getRoleArn());
      stackName = getParameterFieldValue(configuration.getStackName());
    } else if (parameters.getConfiguration().getType().equals(
                   CloudformationDeleteStackStepConfigurationTypes.Inherited)) {
      log.info("Obtaining task using Inherited configuration");
      String provisionerIdentifier = getParameterFieldValue(
          ((InheritedCloudformationDeleteStackStepConfiguration) parameters.getConfiguration().getSpec())
              .getProvisionerIdentifier());
      CloudFormationInheritOutput cloudFormationInheritOutput =
          cloudFormationStepHelper.getSavedCloudFormationInheritOutput(provisionerIdentifier, ambiance);
      if (cloudFormationInheritOutput == null) {
        throw new InvalidRequestException(
            format("Did not find any successfully executed Create Stack step for provisioner identifier: [%s]",
                provisionerIdentifier));
      }

      connectorRef = cloudFormationInheritOutput.getConnectorRef();
      region = cloudFormationInheritOutput.getRegion();
      roleArn = cloudFormationInheritOutput.getRoleArn();
      stackName = cloudFormationInheritOutput.getStackName();
    } else {
      String errorMessage = format("Invalid configuration type: %s ", parameters.getConfiguration().getType());
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }

    CloudformationTaskNGParametersBuilder builder = CloudformationTaskNGParameters.builder();
    ConnectorInfoDTO connectorInfoDTO = cloudFormationStepHelper.getConnectorDTO(connectorRef, ambiance);
    AwsConnectorDTO connectorDTO = (AwsConnectorDTO) connectorInfoDTO.getConnectorConfig();
    if (isNotEmpty(roleArn)) {
      builder.cloudFormationRoleArn(cloudFormationStepHelper.renderValue(ambiance, roleArn));
    }

    List<EncryptedDataDetail> encryptionDetails =
        cloudFormationStepHelper.getAwsEncryptionDetails(ambiance, connectorDTO);
    builder.accountId(AmbianceUtils.getAccountId(ambiance))
        .taskType(CloudformationTaskType.DELETE_STACK)
        .cfCommandUnit(CloudformationCommandUnit.DeleteStack)
        .awsConnector(connectorDTO)
        .region(region)
        .encryptedDataDetails(encryptionDetails)
        .stackName(stackName);

    TaskData taskData =
        TaskData.builder()
            .async(true)
            .taskType(TaskType.CLOUDFORMATION_TASK_NG.name())
            .timeout(StepUtils.getTimeoutMillis(stepParameters.getTimeout(), CloudformationStepHelper.DEFAULT_TIMEOUT))
            .parameters(new Object[] {builder.build()})
            .build();

    return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
        Collections.singletonList(CloudformationCommandUnit.DeleteStack.name()),
        TaskType.CLOUDFORMATION_TASK_NG.getDisplayName(),
        TaskSelectorYaml.toTaskSelector(emptyIfNull(getParameterFieldValue(parameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
