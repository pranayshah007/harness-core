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
import static io.harness.aws.asg.manifest.AsgManifestType.AsgShiftTraffic;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgSwapService;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.Green;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.asg.AsgCommandUnitConstants;
import io.harness.aws.asg.AsgContentParser;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.aws.asg.manifest.AsgManifestHandlerChainFactory;
import io.harness.aws.asg.manifest.AsgManifestHandlerChainState;
import io.harness.aws.asg.manifest.request.AsgConfigurationManifestRequest;
import io.harness.aws.asg.manifest.request.AsgScalingPolicyManifestRequest;
import io.harness.aws.asg.manifest.request.AsgScheduledActionManifestRequest;
import io.harness.aws.asg.manifest.request.AsgShiftTrafficManifestRequest;
import io.harness.aws.asg.manifest.request.AsgSwapServiceManifestRequest;
import io.harness.aws.beans.AsgLoadBalancerConfig;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.ElbV2Client;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.AsgNGException;
import io.harness.delegate.task.aws.asg.AsgBlueGreenRollbackRequest;
import io.harness.delegate.task.aws.asg.AsgBlueGreenRollbackResponse;
import io.harness.delegate.task.aws.asg.AsgBlueGreenRollbackResult;
import io.harness.delegate.task.aws.asg.AsgCommandRequest;
import io.harness.delegate.task.aws.asg.AsgCommandResponse;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AsgTaskHelper;
import io.harness.delegate.task.aws.asg.AutoScalingGroupContainer;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.service.impl.AwsUtils;

import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class AsgBlueGreenRollbackCommandTaskHandler extends AsgCommandTaskNGHandler {
  @Inject private AsgTaskHelper asgTaskHelper;
  @Inject private ElbV2Client elbV2Client;
  @Inject private AwsUtils awsUtils;

  @Override
  protected AsgCommandResponse executeTaskInternal(AsgCommandRequest asgCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress)
      throws AsgNGException {
    if (!(asgCommandRequest instanceof AsgBlueGreenRollbackRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("asgCommandRequest", "Must be instance of AsgBlueGreenRollbackRequest"));
    }

    AsgBlueGreenRollbackRequest asgBlueGreenRollbackRequest = (AsgBlueGreenRollbackRequest) asgCommandRequest;
    String stageAsgName = asgBlueGreenRollbackRequest.getStageAsgName();
    String prodAsgName = asgBlueGreenRollbackRequest.getProdAsgName();
    Map<String, List<String>> stageAsgManifestsDataForRollback =
        asgBlueGreenRollbackRequest.getStageAsgManifestsDataForRollback();
    Map<String, List<String>> prodAsgManifestsDataForRollback =
        asgBlueGreenRollbackRequest.getProdAsgManifestsDataForRollback();
    List<AsgLoadBalancerConfig> lbConfigs = isNotEmpty(asgBlueGreenRollbackRequest.getLoadBalancers())
        ? asgBlueGreenRollbackRequest.getLoadBalancers()
        : Arrays.asList(asgBlueGreenRollbackRequest.getAsgLoadBalancerConfig());

    LogCallback logCallback = asgTaskHelper.getLogCallback(
        iLogStreamingTaskClient, AsgCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);

    try {
      AsgSdkManager asgSdkManager = asgTaskHelper.getAsgSdkManager(asgCommandRequest, logCallback, elbV2Client);
      AsgInfraConfig asgInfraConfig = asgCommandRequest.getAsgInfraConfig();

      String region = asgInfraConfig.getRegion();
      AwsInternalConfig awsInternalConfig = awsUtils.getAwsInternalConfig(asgInfraConfig.getAwsConnectorDTO(), region);

      asgSdkManager.info("Starting Blue Green Rollback");

      executeRollbackServiceSwap(asgSdkManager, prodAsgName, stageAsgName, lbConfigs, region, awsInternalConfig);
      executeRollbackTrafficShift(asgSdkManager, prodAsgName, stageAsgName, lbConfigs, region, awsInternalConfig);

      // first deployment
      if (prodAsgName == null) {
        asgSdkManager.info("Deleting Stage ASG %s as this is first deployment", stageAsgName);
        asgSdkManager.deleteAsg(stageAsgName);
      } else {
        if (isNotEmpty(stageAsgManifestsDataForRollback)) {
          asgSdkManager.info("Rolling back Stage ASG %s to previous version", stageAsgName);
          executeRollbackVersion(
              asgSdkManager, stageAsgName, stageAsgManifestsDataForRollback, awsInternalConfig, region);
        } else {
          asgSdkManager.info("Deleting Stage ASG %s as it hasn't previous version", stageAsgName);
          asgSdkManager.deleteAsg(stageAsgName);
        }

        if (isNotEmpty(prodAsgManifestsDataForRollback)) {
          asgSdkManager.info("Rolling back Prod ASG %s to previous version", prodAsgName);
          executeRollbackVersion(
              asgSdkManager, prodAsgName, prodAsgManifestsDataForRollback, awsInternalConfig, region);
        }
      }

      AutoScalingGroupContainer prodAutoScalingGroupContainer =
          asgTaskHelper.mapToAutoScalingGroupContainer(asgSdkManager.getASG(prodAsgName));
      AutoScalingGroupContainer stageAutoScalingGroupContainer =
          asgTaskHelper.mapToAutoScalingGroupContainer(asgSdkManager.getASG(stageAsgName));
      AsgBlueGreenRollbackResult asgBlueGreenRollbackResult =
          AsgBlueGreenRollbackResult.builder()
              .prodAutoScalingGroupContainer(prodAutoScalingGroupContainer)
              .stageAutoScalingGroupContainer(stageAutoScalingGroupContainer)
              .build();
      logCallback.saveExecutionLog(
          color("Blue Green Rollback Finished Successfully", Green, Bold), INFO, CommandExecutionStatus.SUCCESS);

      return AsgBlueGreenRollbackResponse.builder()
          .asgBlueGreenRollbackResult(asgBlueGreenRollbackResult)
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .build();
    } catch (Exception e) {
      logCallback.saveExecutionLog(
          color(format("Rollback Failed"), LogColor.Red, LogWeight.Bold), ERROR, CommandExecutionStatus.FAILURE);
      throw new AsgNGException(e);
    }
  }

  private void executeRollbackServiceSwap(AsgSdkManager asgSdkManager, String prodAsgName, String stageAsgName,
      List<AsgLoadBalancerConfig> lbConfigs, String region, AwsInternalConfig awsInternalConfig) {
    List<AsgLoadBalancerConfig> rollbackLbConfigs = createRollbackLbConfigsForSwapping(lbConfigs);

    if (isEmpty(rollbackLbConfigs)) {
      return;
    }

    asgSdkManager.info("Swapping back routing rule for Prod ASG %s and Stage ASG %s", prodAsgName, stageAsgName);
    AsgManifestHandlerChainFactory.builder()
        .initialChainState(AsgManifestHandlerChainState.builder().asgName(stageAsgName).newAsgName(prodAsgName).build())
        .asgSdkManager(asgSdkManager)
        .build()
        .addHandler(AsgSwapService,
            AsgSwapServiceManifestRequest.builder()
                .loadBalancers(rollbackLbConfigs)
                .region(region)
                .awsInternalConfig(awsInternalConfig)
                .build())
        .executeUpsert();
  }

  private void executeRollbackTrafficShift(AsgSdkManager asgSdkManager, String prodAsgName, String stageAsgName,
      List<AsgLoadBalancerConfig> lbConfigs, String region, AwsInternalConfig awsInternalConfig) {
    List<AsgLoadBalancerConfig> rollbackLbConfigs = filterLoadBalancers(lbConfigs, true);

    if (isEmpty(rollbackLbConfigs)) {
      return;
    }

    asgSdkManager.info("Shifting traffic back for stage TGs weight=0");
    AsgManifestHandlerChainFactory.builder()
        .initialChainState(AsgManifestHandlerChainState.builder().asgName(prodAsgName).newAsgName(stageAsgName).build())
        .asgSdkManager(asgSdkManager)
        .build()
        .addHandler(AsgShiftTraffic,
            AsgShiftTrafficManifestRequest.builder()
                .loadBalancers(rollbackLbConfigs)
                .region(region)
                .awsInternalConfig(awsInternalConfig)
                .weight(0)
                .build())
        .executeUpsert();
  }

  private void executeRollbackVersion(AsgSdkManager asgSdkManager, String asgName,
      Map<String, List<String>> asgManifestsDataForRollback, AwsInternalConfig awsInternalConfig, String region) {
    if (isNotEmpty(asgManifestsDataForRollback)) {
      asgSdkManager.info("Rolling back to previous version of ASG %s", asgName);

      // Get the content of all required manifest files
      String asgConfigurationContent = asgTaskHelper.getAsgConfigurationContent(asgManifestsDataForRollback);
      List<String> asgScalingPolicyContent = asgTaskHelper.getAsgScalingPolicyContent(asgManifestsDataForRollback);
      List<String> asgScheduledActionContent = asgTaskHelper.getAsgScheduledActionContent(asgManifestsDataForRollback);

      // Get ASG name from asg configuration manifest
      CreateAutoScalingGroupRequest createAutoScalingGroupRequest =
          AsgContentParser.parseJson(asgConfigurationContent, CreateAutoScalingGroupRequest.class, false);

      AsgManifestHandlerChainState initialChainState = AsgManifestHandlerChainState.builder().asgName(asgName).build();

      if (isNotEmpty(createAutoScalingGroupRequest.getLaunchTemplate().getVersion())) {
        initialChainState.setLaunchTemplateVersion(createAutoScalingGroupRequest.getLaunchTemplate().getVersion());
        initialChainState.setLaunchTemplateName(
            createAutoScalingGroupRequest.getLaunchTemplate().getLaunchTemplateName());
      }
      // Chain factory code to handle each manifest one by one in a chain
      AsgManifestHandlerChainState chainState =
          AsgManifestHandlerChainFactory.builder()
              .initialChainState(initialChainState)
              .asgSdkManager(asgSdkManager)
              .build()
              .addHandler(AsgConfiguration,
                  AsgConfigurationManifestRequest.builder()
                      .manifests(Arrays.asList(asgConfigurationContent))
                      .awsInternalConfig(awsInternalConfig)
                      .region(region)
                      .build())
              .addHandler(AsgScalingPolicy,
                  AsgScalingPolicyManifestRequest.builder().manifests(asgScalingPolicyContent).build())
              .addHandler(AsgScheduledUpdateGroupAction,
                  AsgScheduledActionManifestRequest.builder().manifests(asgScheduledActionContent).build())
              .executeUpsert();

      asgSdkManager.infoBold("Rolled back to previous version of ASG %s successfully", asgName);
    } else {
      asgSdkManager.deleteAsg(asgName);
    }
  }

  private List<AsgLoadBalancerConfig> filterLoadBalancers(
      List<AsgLoadBalancerConfig> lbConfigs, boolean usedForShiftTraffic) {
    Predicate<AsgLoadBalancerConfig> usedForShiftTrafficPredicate = lb -> isEmpty(lb.getStageListenerRuleArn());
    Predicate<AsgLoadBalancerConfig> filterPredicate =
        usedForShiftTraffic ? usedForShiftTrafficPredicate : Predicate.not(usedForShiftTrafficPredicate);
    return lbConfigs.stream().filter(filterPredicate).collect(Collectors.toList());
  }

  private List<AsgLoadBalancerConfig> createRollbackLbConfigsForSwapping(List<AsgLoadBalancerConfig> lbConfigs) {
    return filterLoadBalancers(lbConfigs, false)
        .stream()
        .map(lbConfig
            -> AsgLoadBalancerConfig.builder()
                   .loadBalancer(lbConfig.getLoadBalancer())
                   .stageListenerArn(lbConfig.getStageListenerArn())
                   .stageListenerRuleArn(lbConfig.getStageListenerRuleArn())
                   .stageTargetGroupArnsList(lbConfig.getProdTargetGroupArnsList())
                   .prodListenerArn(lbConfig.getProdListenerArn())
                   .prodListenerRuleArn(lbConfig.getProdListenerRuleArn())
                   .prodTargetGroupArnsList(lbConfig.getStageTargetGroupArnsList())
                   .build())
        .collect(Collectors.toList());
  }
}
