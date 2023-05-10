/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.SKIPPED;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.pcf.ResizeStrategy.RESIZE_NEW_FIRST;

import static software.wings.service.impl.aws.model.AwsConstants.AMI_SERVICE_SETUP_SWEEPING_OUTPUT_NAME;
import static software.wings.service.impl.aws.model.AwsConstants.AWS_AMI_ALL_PHASE_ROLLBACK_NAME;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.SweepingOutputInstance.Scope;
import io.harness.context.ContextElementType;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;

import software.wings.api.AmiServiceDeployElement;
import software.wings.api.AmiServiceSetupElement;
import software.wings.api.AwsAmiDeployStateExecutionData;
import software.wings.api.ContainerServiceData;
import software.wings.api.InstanceElement;
import software.wings.beans.Activity;
import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.AwsConfig;
import software.wings.beans.InstanceUnitType;
import software.wings.beans.SettingAttribute;
import software.wings.service.impl.aws.manager.AwsHelperServiceManager;
import software.wings.service.impl.aws.model.AwsAmiAllPhaseRollbackData;
import software.wings.service.impl.aws.model.AwsAmiPreDeploymentData;
import software.wings.service.impl.aws.model.AwsAmiResizeData;
import software.wings.service.impl.aws.model.AwsAmiServiceDeployResponse;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateType;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@OwnedBy(CDP)
public class AwsAmiServiceRollback extends AwsAmiServiceDeployState {
  @Getter @Setter @Attributes(title = "Rollback all phases at once") private boolean rollbackAllPhasesAtOnce;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private AwsAmiServiceStateHelper awsAmiServiceStateHelper;

  public AwsAmiServiceRollback(String name) {
    super(name, StateType.AWS_AMI_SERVICE_ROLLBACK.name());
  }

  @Override
  protected ExecutionResponse executeInternal(ExecutionContext context) {
    if (allPhaseRollbackDone(context)) {
      return ExecutionResponse.builder().executionStatus(ExecutionStatus.SUCCESS).build();
    }
    AmiServiceSetupElement serviceSetupElement =
        (AmiServiceSetupElement) awsAmiServiceStateHelper.getSetupElementFromSweepingOutput(
            context, AMI_SERVICE_SETUP_SWEEPING_OUTPUT_NAME);
    if (serviceSetupElement == null) {
      return ExecutionResponse.builder()
          .executionStatus(SKIPPED)
          .errorMessage("No service setup element found. Skipping rollback.")
          .build();
    }
    AmiServiceDeployElement amiServiceDeployElement = context.getContextElement(ContextElementType.AMI_SERVICE_DEPLOY);
    if (amiServiceDeployElement == null) {
      return ExecutionResponse.builder()
          .executionStatus(SKIPPED)
          .errorMessage("No service deploy element found. Skipping rollback.")
          .build();
    }
    List<AwsAmiResizeData> oldAsgCounts = Lists.newArrayList();
    String newAgName;
    int newAsgFinalDesiredCount;
    if (rollbackAllPhasesAtOnce) {
      AwsAmiPreDeploymentData preDeploymentData = serviceSetupElement.getPreDeploymentData();
      if (isNotEmpty(preDeploymentData.getOldAsgName())) {
        oldAsgCounts.add(AwsAmiResizeData.builder()
                             .asgName(preDeploymentData.getOldAsgName())
                             .desiredCount(preDeploymentData.getPreDeploymentDesiredCapacity())
                             .build());
      }
      newAgName = serviceSetupElement.getNewAutoScalingGroupName();
      newAsgFinalDesiredCount = 0;
    } else {
      List<ContainerServiceData> oldData = amiServiceDeployElement.getOldInstanceData();
      if (isNotEmpty(oldData)) {
        oldData.forEach(data -> {
          oldAsgCounts.add(
              AwsAmiResizeData.builder().asgName(data.getName()).desiredCount(data.getPreviousCount()).build());
        });
      }
      ContainerServiceData newContainerServiceData = amiServiceDeployElement.getNewInstanceData().get(0);
      newAgName = newContainerServiceData.getName();
      newAsgFinalDesiredCount = newContainerServiceData.getPreviousCount();
    }

    Activity activity = crateActivity(context);

    AwsAmiInfrastructureMapping infrastructureMapping = (AwsAmiInfrastructureMapping) infrastructureMappingService.get(
        activity.getAppId(), context.fetchInfraMappingId());
    SettingAttribute cloudProviderSetting = settingsService.get(infrastructureMapping.getComputeProviderSettingId());

    String region = infrastructureMapping.getRegion();
    AwsConfig awsConfig = (AwsConfig) cloudProviderSetting.getValue();
    AwsHelperServiceManager.setAmazonClientSDKDefaultBackoffStrategyIfExists(context, awsConfig);
    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails(awsConfig, context.getAppId(), context.getWorkflowExecutionId());

    // If resize new first is selected as true by the user in setup state. Then in rollback we want to resize the
    // list of older Asgs first. In that case, the flag sent to the delegate would need to be reversed.
    boolean resizeNewFirst = RESIZE_NEW_FIRST != serviceSetupElement.getResizeStrategy();

    AmiResizeTaskRequestData amiResizeTaskRequestData =
        AmiResizeTaskRequestData.builder()
            .accountId(infrastructureMapping.getAccountId())
            .activityId(activity.getUuid())
            .appId(infrastructureMapping.getAppId())
            .envId(infrastructureMapping.getEnvId())
            .environmentType(context.fetchRequiredEnvironment().getEnvironmentType())
            .awsConfig(awsConfig)
            .encryptionDetails(encryptionDetails)
            .rollback(true)
            .region(region)
            .commandName(getCommandName())
            .resizeNewFirst(resizeNewFirst)
            .newAutoScalingGroupName(newAgName)
            .newAsgFinalDesiredCount(newAsgFinalDesiredCount)
            .resizeData(oldAsgCounts)
            .serviceSetupElement(serviceSetupElement)
            .classicLBs(infrastructureMapping.getClassicLoadBalancers())
            .targetGroupArns(infrastructureMapping.getTargetGroupArns())
            .context(context)
            .build();
    createAndQueueResizeTask(amiResizeTaskRequestData, context);

    AwsAmiDeployStateExecutionData awsAmiDeployStateExecutionData = prepareStateExecutionData(activity.getUuid(),
        serviceSetupElement, amiServiceDeployElement.getInstanceCount(), amiServiceDeployElement.getInstanceUnitType(),
        amiServiceDeployElement.getNewInstanceData(), amiServiceDeployElement.getOldInstanceData());
    awsAmiDeployStateExecutionData.setRollback(true);

    return ExecutionResponse.builder()
        .async(true)
        .stateExecutionData(awsAmiDeployStateExecutionData)
        .executionStatus(ExecutionStatus.SUCCESS)
        .correlationId(activity.getUuid())
        .build();
  }

  @Override
  protected List<InstanceElement> handleAsyncInternal(AwsAmiServiceDeployResponse amiServiceDeployResponse,
      ExecutionContext context, AmiServiceSetupElement serviceSetupElement,
      ManagerExecutionLogCallback executionLogCallback) {
    if (rollbackAllPhasesAtOnce && ExecutionStatus.SUCCESS == amiServiceDeployResponse.getExecutionStatus()) {
      markAllPhaseRollbackDone(context);
    }
    return emptyList();
  }

  private void markAllPhaseRollbackDone(ExecutionContext context) {
    sweepingOutputService.save(context.prepareSweepingOutputBuilder(Scope.WORKFLOW)
                                   .name(AWS_AMI_ALL_PHASE_ROLLBACK_NAME)
                                   .output(kryoSerializer.asDeflatedBytes(
                                       AwsAmiAllPhaseRollbackData.builder().allPhaseRollbackDone(true).build()))
                                   .build());
  }

  private boolean allPhaseRollbackDone(ExecutionContext context) {
    SweepingOutputInquiry sweepingOutputInquiry =
        context.prepareSweepingOutputInquiryBuilder().name(AWS_AMI_ALL_PHASE_ROLLBACK_NAME).build();
    SweepingOutputInstance result = sweepingOutputService.find(sweepingOutputInquiry);
    if (result == null) {
      return false;
    }
    return ((AwsAmiAllPhaseRollbackData) kryoSerializer.asInflatedObject(result.getOutput())).isAllPhaseRollbackDone();
  }

  @Override
  @SchemaIgnore
  public String getCommandName() {
    return super.getCommandName();
  }

  @Override
  @SchemaIgnore
  public String getInstanceCount() {
    return super.getInstanceCount();
  }

  @Override
  @SchemaIgnore
  public InstanceUnitType getInstanceUnitType() {
    return super.getInstanceUnitType();
  }

  @Override
  @SchemaIgnore
  public Integer getTimeoutMillis() {
    return super.getTimeoutMillis();
  }
}
