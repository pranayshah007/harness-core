/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.aws.asg;

import static io.harness.aws.asg.manifest.AsgManifestType.AsgConfiguration;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgScalingPolicy;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgScheduledUpdateGroupAction;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.Green;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.aws.asg.AsgCommandUnitConstants;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.aws.asg.manifest.AsgManifestHandlerChainFactory;
import io.harness.aws.asg.manifest.AsgManifestHandlerChainState;
import io.harness.aws.asg.manifest.request.AsgConfigurationManifestRequest;
import io.harness.aws.asg.manifest.request.AsgScalingPolicyManifestRequest;
import io.harness.aws.asg.manifest.request.AsgScheduledActionManifestRequest;
import io.harness.aws.beans.AsgLoadBalancerConfig;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.ElbV2Client;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.AsgNGException;
import io.harness.delegate.task.aws.asg.AsgBlueGreenPrepareRollbackDataRequest;
import io.harness.delegate.task.aws.asg.AsgBlueGreenPrepareRollbackDataResponse;
import io.harness.delegate.task.aws.asg.AsgBlueGreenPrepareRollbackDataResult;
import io.harness.delegate.task.aws.asg.AsgCommandRequest;
import io.harness.delegate.task.aws.asg.AsgCommandResponse;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AsgTaskHelper;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.service.impl.AwsUtils;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.TagDescription;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_AMI_ASG})
@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class AsgBlueGreenPrepareRollbackCommandTaskHandler extends AsgCommandTaskNGHandler {
  static final String VERSION_DELIMITER = "__";
  @Inject private AsgTaskHelper asgTaskHelper;
  @Inject private ElbV2Client elbV2Client;
  @Inject private AwsUtils awsUtils;

  @Override
  protected AsgCommandResponse executeTaskInternal(AsgCommandRequest asgCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress)
      throws AsgNGException {
    if (!(asgCommandRequest instanceof AsgBlueGreenPrepareRollbackDataRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("asgCommandRequest", "Must be instance of AsgBlueGreenPrepareRollbackDataRequest"));
    }

    AsgBlueGreenPrepareRollbackDataRequest asgBlueGreenPrepareRollbackDataRequest =
        (AsgBlueGreenPrepareRollbackDataRequest) asgCommandRequest;

    List<AsgLoadBalancerConfig> lbConfigs = isNotEmpty(asgBlueGreenPrepareRollbackDataRequest.getLoadBalancers())
        ? asgBlueGreenPrepareRollbackDataRequest.getLoadBalancers()
        : Arrays.asList(asgBlueGreenPrepareRollbackDataRequest.getAsgLoadBalancerConfig());

    LogCallback logCallback = asgTaskHelper.getLogCallback(
        iLogStreamingTaskClient, AsgCommandUnitConstants.prepareRollbackData.toString(), true, commandUnitsProgress);

    try {
      AsgSdkManager asgSdkManager = asgTaskHelper.getAsgSdkManager(asgCommandRequest, logCallback, elbV2Client);
      AsgInfraConfig asgInfraConfig = asgCommandRequest.getAsgInfraConfig();
      String region = asgInfraConfig.getRegion();
      AwsInternalConfig awsInternalConfig = awsUtils.getAwsInternalConfig(asgInfraConfig.getAwsConnectorDTO(), region);

      asgSdkManager.info("Starting BG Prepare Rollback");

      String asgName = asgTaskHelper.getAsgName(
          asgCommandRequest, asgBlueGreenPrepareRollbackDataRequest.getAsgStoreManifestsContent());

      if (isEmpty(asgName)) {
        throw new InvalidArgumentsException(Pair.of("AutoScalingGroup name", "Must not be empty"));
      }

      // getting data for Prod and Stage ASGs
      AutoScalingGroup asg1 = asgSdkManager.getASG(asgName + VERSION_DELIMITER + 1);
      AutoScalingGroup asg2 = asgSdkManager.getASG(asgName + VERSION_DELIMITER + 2);

      final AsgBlueGreenPrepareRollbackDataResult result;

      if (asg1 == null && asg2 == null) {
        // first deployment
        asgSdkManager.info("Nothing to prepare as this is first BlueGreen deployment");

        String stageAsgName = asgName + VERSION_DELIMITER + 1;
        result = createResult(
            asgSdkManager, logCallback, lbConfigs, null, stageAsgName, null, null, region, awsInternalConfig);
      } else if (asg1 != null && asg2 == null) {
        if (!isProdAsg(asg1)) {
          // clean phantom non Prod service
          asgSdkManager.warn("Found illegal Stage ASG %s. Deleting it.", asg1.getAutoScalingGroupName());
          asgSdkManager.deleteAsg(asg1.getAutoScalingGroupName());

          String stageAsgName = asgName + VERSION_DELIMITER + 1;
          result = createResult(
              asgSdkManager, logCallback, lbConfigs, null, stageAsgName, null, null, region, awsInternalConfig);
        } else {
          asgSdkManager.info("Found only Prod ASG %s", asg1.getAutoScalingGroupName());
          String stageAsgName = asgName + VERSION_DELIMITER + 2;
          result = createResult(asgSdkManager, logCallback, lbConfigs, asg1.getAutoScalingGroupName(), stageAsgName,
              prepareRollbackData(asgSdkManager, asg1.getAutoScalingGroupName()), null, region, awsInternalConfig);
        }
      } else if (asg1 == null && asg2 != null) {
        if (!isProdAsg(asg2)) {
          // clean phantom non Prod service
          asgSdkManager.warn("Found illegal Stage ASG %s. Deleting it.", asg2.getAutoScalingGroupName());
          asgSdkManager.deleteAsg(asg2.getAutoScalingGroupName());

          String stageAsgName = asgName + VERSION_DELIMITER + 1;
          result = createResult(
              asgSdkManager, logCallback, lbConfigs, null, stageAsgName, null, null, region, awsInternalConfig);
        } else {
          asgSdkManager.info("Found only Prod ASG %s", asg2.getAutoScalingGroupName());
          String stageAsgName = asgName + VERSION_DELIMITER + 1;
          result = createResult(asgSdkManager, logCallback, lbConfigs, asg2.getAutoScalingGroupName(), stageAsgName,
              prepareRollbackData(asgSdkManager, asg2.getAutoScalingGroupName()), null, region, awsInternalConfig);
        }
      } else {
        // having both Prod and Stage, but could be buggy combination like [Prod, Prod] or [Stage, Stage]
        boolean isAsg1Prod = isProdAsg(asg1);
        boolean isAsg2Prod = isProdAsg(asg2);

        if (isAsg1Prod && isAsg2Prod) {
          // clean buggy Prod(1) service and keeping last one
          asgSdkManager.warn("Found 2 Prod ASGs %s, %s. Deleting one of them %s.", asg1.getAutoScalingGroupName(),
              asg2.getAutoScalingGroupName(), asg1.getAutoScalingGroupName());
          asgSdkManager.deleteAsg(asg1.getAutoScalingGroupName());

          String stageAsgName = asgName + VERSION_DELIMITER + 1;
          result = createResult(asgSdkManager, logCallback, lbConfigs, asg2.getAutoScalingGroupName(), stageAsgName,
              prepareRollbackData(asgSdkManager, asg2.getAutoScalingGroupName()), null, region, awsInternalConfig);
        } else if (!isAsg1Prod && !isAsg2Prod) {
          // clean buggy Stage(1) and Stage(2) service
          asgSdkManager.warn("Found 2 Stage ASGs %s, %s. Deleting both.", asg1.getAutoScalingGroupName(),
              asg2.getAutoScalingGroupName());
          asgSdkManager.deleteAsg(asg1.getAutoScalingGroupName());
          asgSdkManager.deleteAsg(asg2.getAutoScalingGroupName());

          // make it as first deployment
          String stageAsgName = asgName + VERSION_DELIMITER + 1;
          result = createResult(
              asgSdkManager, logCallback, lbConfigs, null, stageAsgName, null, null, region, awsInternalConfig);
        } else {
          // having both correct Prod and Stage
          String prodAsgName = isAsg1Prod ? asg1.getAutoScalingGroupName() : asg2.getAutoScalingGroupName();
          String stageAsgName = isAsg1Prod ? asg2.getAutoScalingGroupName() : asg1.getAutoScalingGroupName();

          asgSdkManager.info("Found Prod ASG %s and Stage ASG %s", prodAsgName, stageAsgName);

          result = createResult(asgSdkManager, logCallback, lbConfigs, prodAsgName, stageAsgName,
              prepareRollbackData(asgSdkManager, prodAsgName), prepareRollbackData(asgSdkManager, stageAsgName), region,
              awsInternalConfig);
        }
      }

      logCallback.saveExecutionLog(
          color("BG Prepare Rollback Finished Successfully", Green, Bold), INFO, CommandExecutionStatus.SUCCESS);

      return AsgBlueGreenPrepareRollbackDataResponse.builder()
          .asgBlueGreenPrepareRollbackDataResult(result)
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .build();

    } catch (Exception e) {
      logCallback.saveExecutionLog(color(format("BG Prepare Rollback Failed."), LogColor.Red, LogWeight.Bold), ERROR,
          CommandExecutionStatus.FAILURE);
      throw new AsgNGException(e);
    }
  }

  private Map<String, List<String>> prepareRollbackData(AsgSdkManager asgSdkManager, String asgName) {
    asgSdkManager.info("Prepare snapshot for %s", asgName);
    AsgManifestHandlerChainState chainState =
        AsgManifestHandlerChainFactory.builder()
            .initialChainState(AsgManifestHandlerChainState.builder().asgName(asgName).build())
            .asgSdkManager(asgSdkManager)
            .build()
            .addHandler(AsgConfiguration, AsgConfigurationManifestRequest.builder().build())
            .addHandler(AsgScalingPolicy, AsgScalingPolicyManifestRequest.builder().build())
            .addHandler(AsgScheduledUpdateGroupAction, AsgScheduledActionManifestRequest.builder().build())
            .getContent();

    return chainState.getAsgManifestsDataForRollback();
  }

  private boolean isProdAsg(AutoScalingGroup asg) {
    if (isEmpty(asg.getTags())) {
      return false;
    }

    // classic loop in order to break
    for (TagDescription tag : asg.getTags()) {
      if (tag.getKey().equals(AsgSdkManager.BG_VERSION) && tag.getValue().equals(AsgSdkManager.BG_BLUE)) {
        return true;
      }
    }

    return false;
  }

  private AsgBlueGreenPrepareRollbackDataResult createResult(AsgSdkManager asgSdkManager, LogCallback logCallback,
      List<AsgLoadBalancerConfig> lbConfigs, String prodAsgName, String stageAsgName,
      Map<String, List<String>> prodAsgManifestsDataForRollback,
      Map<String, List<String>> stageAsgManifestsDataForRollback, String region, AwsInternalConfig awsInternalConfig) {
    for (AsgLoadBalancerConfig lbCfg : lbConfigs) {
      asgSdkManager.info(format(
          "Checking ListenerArn and ListenerRuleArn for Prod are valid for loadBalancer: %s", lbCfg.getLoadBalancer()));
      checkListenerRuleIsValid(
          asgSdkManager, lbCfg.getProdListenerArn(), lbCfg.getProdListenerRuleArn(), region, awsInternalConfig);

      asgSdkManager.info(format("Fetching TargetGroupArns for Prod for loadBalancer: %s", lbCfg.getLoadBalancer()));
      List<String> prodTargetGroupArns = asgSdkManager.getTargetGroupArnsFromLoadBalancer(region,
          lbCfg.getProdListenerArn(), lbCfg.getProdListenerRuleArn(), lbCfg.getLoadBalancer(), awsInternalConfig);

      asgSdkManager.info(format("Checking ListenerArn and ListenerRuleArn for Stage are valid for loadBalancer: %s",
          lbCfg.getLoadBalancer()));
      checkListenerRuleIsValid(
          asgSdkManager, lbCfg.getStageListenerArn(), lbCfg.getStageListenerRuleArn(), region, awsInternalConfig);

      asgSdkManager.info(format("Fetching TargetGroupArns for Stage for loadBalancer: %s", lbCfg.getLoadBalancer()));
      List<String> stageTargetGroupArns = asgSdkManager.getTargetGroupArnsFromLoadBalancer(region,
          lbCfg.getStageListenerArn(), lbCfg.getStageListenerRuleArn(), lbCfg.getLoadBalancer(), awsInternalConfig);

      lbCfg.setProdTargetGroupArnsList(prodTargetGroupArns);
      lbCfg.setStageTargetGroupArnsList(stageTargetGroupArns);
    }

    return AsgBlueGreenPrepareRollbackDataResult.builder()
        .prodAsgName(prodAsgName)
        .asgName(stageAsgName)
        .asgLoadBalancerConfig(lbConfigs.get(0))
        .loadBalancers(lbConfigs)
        .prodAsgManifestsDataForRollback(prodAsgManifestsDataForRollback)
        .stageAsgManifestsDataForRollback(stageAsgManifestsDataForRollback)
        .build();
  }

  private void checkListenerRuleIsValid(AsgSdkManager asgSdkManager, String listenerArn, String listenerRuleArn,
      String region, AwsInternalConfig awsInternalConfig) {
    List<Rule> rules = asgSdkManager.getListenerRulesForListener(awsInternalConfig, region, listenerArn);
    Optional<Rule> rule = rules.stream().filter(r -> r.ruleArn().equalsIgnoreCase(listenerRuleArn)).findFirst();

    if (rule.isEmpty()) {
      throw new InvalidRequestException(format("Invalid Listener Rule %s", listenerArn));
    }
  }
}
