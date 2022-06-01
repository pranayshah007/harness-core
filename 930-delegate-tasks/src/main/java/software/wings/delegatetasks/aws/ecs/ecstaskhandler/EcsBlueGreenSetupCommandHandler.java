/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws.ecs.ecstaskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.aws.AwsElbListener;
import io.harness.delegate.task.aws.AwsElbListenerRuleData;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.TimeoutException;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logging.Misc;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.command.ContainerSetupCommandUnitExecutionData;
import software.wings.beans.command.ContainerSetupCommandUnitExecutionData.ContainerSetupCommandUnitExecutionDataBuilder;
import software.wings.beans.command.EcsSetupParams;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.cloudprovider.aws.EcsContainerService;
import software.wings.helpers.ext.ecs.request.EcsBGServiceSetupRequest;
import software.wings.helpers.ext.ecs.request.EcsCommandRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.helpers.ext.ecs.response.EcsServiceSetupResponse;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.intfc.aws.delegate.AwsElbHelperServiceDelegate;

import com.amazonaws.services.ecs.model.TaskDefinition;
import com.amazonaws.services.elasticloadbalancingv2.model.Action;
import com.amazonaws.services.elasticloadbalancingv2.model.Listener;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class EcsBlueGreenSetupCommandHandler extends EcsCommandTaskHandler {
  private static final String DELIMITER = "__";
  @Inject private AwsHelperService awsHelperService;
  @Inject private EcsSetupCommandTaskHelper ecsSetupCommandTaskHelper;
  @Inject private AwsElbHelperServiceDelegate awsElbHelperServiceDelegate;
  @Inject private EcsContainerService ecsContainerService;

  @Override
  public EcsCommandExecutionResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest,
      List<EncryptedDataDetail> encryptedDataDetails, ExecutionLogCallback executionLogCallback) {
    EcsServiceSetupResponse ecsCommandResponse = EcsServiceSetupResponse.builder()
                                                     .isBlueGreen(true)
                                                     .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                     .build();
    CommandExecutionStatus commandExecutionStatus = CommandExecutionStatus.SUCCESS;

    if (!(ecsCommandRequest instanceof EcsBGServiceSetupRequest)) {
      ecsCommandResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
      ecsCommandResponse.setOutput("Invalid Request Type: Expected was : EcsBGServiceSetupRequest");
      return EcsCommandExecutionResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .ecsCommandResponse(ecsCommandResponse)
          .build();
    }

    try {
      EcsBGServiceSetupRequest ecsBGServiceSetupRequest = (EcsBGServiceSetupRequest) ecsCommandRequest;

      EcsSetupParams setupParams = ecsBGServiceSetupRequest.getEcsSetupParams();
      ContainerSetupCommandUnitExecutionDataBuilder commandExecutionDataBuilder =
          ContainerSetupCommandUnitExecutionData.builder();
      commandExecutionDataBuilder.ecsRegion(setupParams.getRegion());
      setTargetGroupForProdListener(encryptedDataDetails, ecsBGServiceSetupRequest, setupParams, executionLogCallback);
      createStageListenerNewServiceIfRequired(encryptedDataDetails, ecsBGServiceSetupRequest, executionLogCallback);
      commandExecutionDataBuilder.stageEcsListener(setupParams.getStageListenerArn());
      commandExecutionDataBuilder.targetGroupForNewService(setupParams.getTargetGroupArn());
      commandExecutionDataBuilder.targetGroupForExistingService(setupParams.getTargetGroupArn2());
      commandExecutionDataBuilder.isUseSpecificListenerRuleArn(setupParams.isUseSpecificListenerRuleArn());
      commandExecutionDataBuilder.prodListenerRuleArn(setupParams.getProdListenerRuleArn());
      commandExecutionDataBuilder.stageListenerRuleArn(setupParams.getStageListenerRuleArn());

      TaskDefinition taskDefinition = ecsSetupCommandTaskHelper.createTaskDefinition(
          ecsBGServiceSetupRequest.getAwsConfig(), encryptedDataDetails, ecsBGServiceSetupRequest.getServiceVariables(),
          ecsBGServiceSetupRequest.getSafeDisplayServiceVariables(), executionLogCallback, setupParams);

      createServiceForBlueGreen(setupParams, taskDefinition,
          aSettingAttribute().withValue(ecsBGServiceSetupRequest.getAwsConfig()).build(), encryptedDataDetails,
          commandExecutionDataBuilder, executionLogCallback);

      ecsCommandResponse.setSetupData(commandExecutionDataBuilder.build());
    } catch (TimeoutException ex) {
      log.error("Completed operation with errors");
      log.error(ExceptionUtils.getMessage(ex), ex);
      Misc.logAllMessages(ex, executionLogCallback);

      commandExecutionStatus = CommandExecutionStatus.FAILURE;
      ecsCommandResponse.setCommandExecutionStatus(commandExecutionStatus);
      ecsCommandResponse.setOutput(ExceptionUtils.getMessage(ex));
      if (ecsCommandRequest.isTimeoutErrorSupported()) {
        ecsCommandResponse.setTimeoutFailure(true);
      }
    } catch (Exception ex) {
      log.error("Completed operation with errors");
      log.error(ExceptionUtils.getMessage(ex), ex);
      Misc.logAllMessages(ex, executionLogCallback);

      commandExecutionStatus = CommandExecutionStatus.FAILURE;
      ecsCommandResponse.setCommandExecutionStatus(commandExecutionStatus);
      ecsCommandResponse.setOutput(ExceptionUtils.getMessage(ex));
    }
    return EcsCommandExecutionResponse.builder()
        .commandExecutionStatus(commandExecutionStatus)
        .ecsCommandResponse(ecsCommandResponse)
        .build();
  }

  private void createStageListenerNewServiceIfRequired(List<EncryptedDataDetail> encryptedDataDetails,
      EcsBGServiceSetupRequest ecsBGServiceSetupRequest, ExecutionLogCallback executionLogCallback) {
    EcsSetupParams setupParams = ecsBGServiceSetupRequest.getEcsSetupParams();
    String targetGroupForNewService;
    if (isNotBlank(setupParams.getStageListenerArn())) {
      if (setupParams.isUseSpecificListenerRuleArn()) {
        List<AwsElbListener> listeners =
            awsElbHelperServiceDelegate.getElbListenersForLoadBalaner(ecsBGServiceSetupRequest.getAwsConfig(),
                encryptedDataDetails, setupParams.getRegion(), setupParams.getLoadBalancerName());
        AwsElbListener stageListener = getListenerByArn(
            listeners, setupParams.getStageListenerArn(), setupParams.getLoadBalancerName(), executionLogCallback);

        TargetGroup currentStageTargetGroup = awsElbHelperServiceDelegate.fetchTargetGroupForSpecificRules(
            stageListener, setupParams.getStageListenerRuleArn(), executionLogCallback,
            ecsBGServiceSetupRequest.getAwsConfig(), setupParams.getRegion(), encryptedDataDetails);
        targetGroupForNewService = currentStageTargetGroup.getTargetGroupArn();
      } else {
        targetGroupForNewService = getTargetGroupForListener(encryptedDataDetails, ecsBGServiceSetupRequest,
            setupParams, setupParams.getStageListenerArn(), executionLogCallback);
      }
      setupParams.setTargetGroupArn(targetGroupForNewService);
    } else {
      if (isNotBlank(setupParams.getTargetGroupArn())) {
        // This is just validation. Making Sure Target Group to be used with New Service Exists.
        validateTargetGroupProvidedForNewService(
            encryptedDataDetails, ecsBGServiceSetupRequest, setupParams, executionLogCallback);
      } else {
        cloneListenerProdListenerIfRequired(
            encryptedDataDetails, ecsBGServiceSetupRequest, setupParams, executionLogCallback);
      }
    }
  }

  private void cloneListenerProdListenerIfRequired(List<EncryptedDataDetail> encryptedDataDetails,
      EcsBGServiceSetupRequest ecsBGServiceSetupRequest, EcsSetupParams setupParams,
      ExecutionLogCallback executionLogCallback) {
    List<AwsElbListener> listeners =
        awsElbHelperServiceDelegate.getElbListenersForLoadBalaner(ecsBGServiceSetupRequest.getAwsConfig(),
            encryptedDataDetails, setupParams.getRegion(), setupParams.getLoadBalancerName());

    // Check if any listener already exists on port mentioned for stageListener
    Optional<AwsElbListener> optionalListener =
        listeners.stream()
            .filter(listener -> Integer.parseInt(setupParams.getStageListenerPort()) == listener.getPort().intValue())
            .findFirst();

    if (optionalListener.isPresent()) {
      executionLogCallback.saveExecutionLog(new StringBuilder(128)
                                                .append("Found existing Listener on Port:{ ")
                                                .append(setupParams.getStageListenerPort())
                                                .append("}. ListenerARN: ")
                                                .append(optionalListener.get().getListenerArn())
                                                .append(", Protocol: ")
                                                .append(optionalListener.get().getProtocol())
                                                .toString());

      setupParams.setStageListenerArn(optionalListener.get().getListenerArn());
      // Find out target group this listener is forwarding requests to
      String targetGroupArn;
      if (setupParams.isUseSpecificListenerRuleArn()) {
        Optional<AwsElbListenerRuleData> awsElbListenerRuleData =
            optionalListener.get()
                .getRules()
                .stream()
                .filter(rule -> setupParams.getStageListenerRuleArn().equalsIgnoreCase(rule.getRuleArn()))
                .findFirst();
        if (awsElbListenerRuleData.isPresent()) {
          targetGroupArn = awsElbListenerRuleData.get().getRuleTargetGroupArn();
        } else {
          String message = format(
              "Stage listener rule provided for deployment does not exist for listener at port [%d], provided rule arn: [%s]",
              optionalListener.get().getPort(), setupParams.getStageListenerRuleArn());
          executionLogCallback.saveExecutionLog(message);
          throw new InvalidRequestException(message);
        }
      } else {
        Listener listener = awsElbHelperServiceDelegate.getElbListener(ecsBGServiceSetupRequest.getAwsConfig(),
            encryptedDataDetails, ecsBGServiceSetupRequest.getRegion(), optionalListener.get().getListenerArn());
        targetGroupArn = ecsSetupCommandTaskHelper.getTargetGroupForDefaultAction(listener, executionLogCallback);
      }

      setupParams.setTargetGroupArn(targetGroupArn);
      return;
    }

    // First create StageTargetGroup if doesn't exist
    cloneTargetGroupFromProdTargetGroupIfRequired(
        encryptedDataDetails, ecsBGServiceSetupRequest, setupParams, executionLogCallback);
    // Now create Listener
    Listener listener = awsElbHelperServiceDelegate.createStageListener(ecsBGServiceSetupRequest.getAwsConfig(),
        encryptedDataDetails, setupParams.getRegion(), setupParams.getProdListenerArn(),
        Integer.parseInt(setupParams.getStageListenerPort()), setupParams.getTargetGroupArn());
    setupParams.setStageListenerArn(listener.getListenerArn());
  }

  private void cloneTargetGroupFromProdTargetGroupIfRequired(List<EncryptedDataDetail> encryptedDataDetails,
      EcsBGServiceSetupRequest ecsBGServiceSetupRequest, EcsSetupParams setupParams,
      ExecutionLogCallback executionLogCallback) {
    // Get Prod Target Group
    String prodTargetGroupArn = setupParams.getTargetGroupArn2();
    Optional<TargetGroup> optionalProdTargetGroup = awsElbHelperServiceDelegate.getTargetGroup(
        ecsBGServiceSetupRequest.getAwsConfig(), encryptedDataDetails, setupParams.getRegion(), prodTargetGroupArn);
    TargetGroup prodTargetGroup = null;
    if (optionalProdTargetGroup.isPresent()) {
      prodTargetGroup = optionalProdTargetGroup.get();
    } else {
      String message = format(
          "The specified target group for prod listener rule: [%s] and listener arn: [%s] does not exist, target group arn: [%s]",
          setupParams.getProdListenerArn(), setupParams.getProdListenerRuleArn(), prodTargetGroupArn);
      executionLogCallback.saveExecutionLog(message, LogLevel.ERROR);
      throw new InvalidRequestException(message);
    }

    // Check if Stage Target Group already exists
    Optional<TargetGroup> optionalStageTargetGroup =
        awsElbHelperServiceDelegate.getTargetGroupByName(ecsBGServiceSetupRequest.getAwsConfig(), encryptedDataDetails,
            setupParams.getRegion(), format("HarnessClone-%s", prodTargetGroup.getTargetGroupName()));

    if (!optionalStageTargetGroup.isPresent()) {
      executionLogCallback.saveExecutionLog("Creating Target Group To be used with {Green} Service");
      TargetGroup stageTargetGroup = awsElbHelperServiceDelegate.cloneTargetGroup(
          ecsBGServiceSetupRequest.getAwsConfig(), encryptedDataDetails, setupParams.getRegion(), prodTargetGroupArn,
          format("HarnessClone-%s", prodTargetGroup.getTargetGroupName()));
      setupParams.setTargetGroupArn(stageTargetGroup.getTargetGroupArn());
    } else {
      setupParams.setTargetGroupArn(optionalStageTargetGroup.get().getTargetGroupArn());
      executionLogCallback.saveExecutionLog(new StringBuilder(64)
                                                .append("Using Stage Target Group: ")
                                                .append(optionalStageTargetGroup.get().getTargetGroupArn())
                                                .toString());
    }
  }

  private void validateTargetGroupProvidedForNewService(List<EncryptedDataDetail> encryptedDataDetails,
      EcsBGServiceSetupRequest ecsBGServiceSetupRequest, EcsSetupParams setupParams,
      ExecutionLogCallback executionLogCallback) {
    Optional<TargetGroup> optionalGroup =
        awsElbHelperServiceDelegate.getTargetGroup(ecsBGServiceSetupRequest.getAwsConfig(), encryptedDataDetails,
            setupParams.getRegion(), setupParams.getTargetGroupArn());
    if (!optionalGroup.isPresent()) {
      String errorMsg = new StringBuilder(128)
                            .append("Invalid TargetGroupArn mentioned: ")
                            .append(setupParams.getTargetGroupArn())
                            .append(", Could not find any target Group")
                            .toString();

      executionLogCallback.saveExecutionLog(errorMsg, LogLevel.ERROR);
      throw new WingsException(ErrorCode.INVALID_ARGUMENT, errorMsg, USER).addParam("message", errorMsg);
    }
  }

  private void setTargetGroupForProdListener(List<EncryptedDataDetail> encryptedDataDetails,
      EcsBGServiceSetupRequest ecsBGServiceSetupRequest, EcsSetupParams setupParams,
      ExecutionLogCallback executionLogCallback) {
    String currentProdTargetGroupArn;
    if (setupParams.isUseSpecificListenerRuleArn()) {
      List<AwsElbListener> listeners =
          awsElbHelperServiceDelegate.getElbListenersForLoadBalaner(ecsBGServiceSetupRequest.getAwsConfig(),
              encryptedDataDetails, setupParams.getRegion(), setupParams.getLoadBalancerName());
      AwsElbListener prodListener = getListenerByArn(
          listeners, setupParams.getProdListenerArn(), setupParams.getLoadBalancerName(), executionLogCallback);

      // This is targetGroup currently associated with ProdListener for ELB for specific rule specified
      TargetGroup currentProdTargetGroup = awsElbHelperServiceDelegate.fetchTargetGroupForSpecificRules(prodListener,
          setupParams.getProdListenerRuleArn(), executionLogCallback, ecsBGServiceSetupRequest.getAwsConfig(),
          setupParams.getRegion(), encryptedDataDetails);
      currentProdTargetGroupArn = currentProdTargetGroup.getTargetGroupArn();
    } else {
      // This is targetGroupArn currently associated with ProdListener for ELB for default action
      currentProdTargetGroupArn = getTargetGroupForListener(encryptedDataDetails, ecsBGServiceSetupRequest, setupParams,
          setupParams.getProdListenerArn(), executionLogCallback);
    }

    setupParams.setTargetGroupArn2(currentProdTargetGroupArn);
  }

  AwsElbListener getListenerByArn(
      List<AwsElbListener> listeners, String listenerArn, String loadBalancerName, ExecutionLogCallback logCallback) {
    if (isEmpty(listeners)) {
      String message =
          format("Did not find any listeners for load balancer: [%s] with arn: [%s]", loadBalancerName, listenerArn);
      log.error(message);
      logCallback.saveExecutionLog(message);
      throw new InvalidRequestException(message);
    }
    Optional<AwsElbListener> optionalListener =
        listeners.stream().filter(listener -> listenerArn.equalsIgnoreCase(listener.getListenerArn())).findFirst();
    if (!optionalListener.isPresent()) {
      String message =
          format("Did not find any listeners by Arn: [%s] for load balancer: [%s].", listenerArn, loadBalancerName);
      log.error(message);
      logCallback.saveExecutionLog(message);
      throw new InvalidRequestException(message);
    }
    return optionalListener.get();
  }

  private String getTargetGroupForListener(List<EncryptedDataDetail> encryptedDataDetails,
      EcsBGServiceSetupRequest ecsBGServiceSetupRequest, EcsSetupParams setupParams, String listenerArn,
      ExecutionLogCallback executionLogCallback) {
    Listener listener = awsElbHelperServiceDelegate.getElbListener(
        ecsBGServiceSetupRequest.getAwsConfig(), encryptedDataDetails, setupParams.getRegion(), listenerArn);

    Optional<Action> forwardAction =
        listener.getDefaultActions().stream().filter(action -> "forward".equals(action.getType())).findFirst();

    // Listener should have default action set to forward traffic to target group.
    if (!forwardAction.isPresent()) {
      executionLogCallback.saveExecutionLog(
          "No Default Action set for Listener forwarding traffic to Target Group. Invalid Configuration",
          LogLevel.ERROR);

      throw new WingsException(
          "No Default Action set for Listener forwarding traffic to Target Group. Invalid Configuration", USER)
          .addParam("message",
              "No Default Action set for Listener forwarding traffic to Target Group. Invalid Configuration");
    }

    return forwardAction.get().getTargetGroupArn();
  }

  private void createServiceForBlueGreen(EcsSetupParams setupParams, TaskDefinition taskDefinition,
      SettingAttribute cloudProviderSetting, List<EncryptedDataDetail> encryptedDataDetails,
      ContainerSetupCommandUnitExecutionDataBuilder commandExecutionDataBuilder,
      ExecutionLogCallback executionLogCallback) {
    // Delete existing services except Green version of this service.
    ecsSetupCommandTaskHelper.deleteExistingServicesOtherThanBlueVersion(
        setupParams, cloudProviderSetting, encryptedDataDetails, executionLogCallback);

    String containerServiceName = ecsSetupCommandTaskHelper.createEcsService(setupParams, taskDefinition,
        cloudProviderSetting, encryptedDataDetails, commandExecutionDataBuilder, executionLogCallback);
    commandExecutionDataBuilder.targetGroupForNewService(setupParams.getTargetGroupArn());
    commandExecutionDataBuilder.containerServiceName(containerServiceName);

    ecsSetupCommandTaskHelper.storeCurrentServiceNameAndCountInfo((AwsConfig) cloudProviderSetting.getValue(),
        setupParams, encryptedDataDetails, commandExecutionDataBuilder, containerServiceName);

    ecsSetupCommandTaskHelper.backupAutoScalarConfig(setupParams, cloudProviderSetting, encryptedDataDetails,
        containerServiceName, commandExecutionDataBuilder, executionLogCallback);

    ecsSetupCommandTaskHelper.logLoadBalancerInfo(executionLogCallback, setupParams);
  }
}
