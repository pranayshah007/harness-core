/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.elastigroup;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.elastigroup.ElastigroupCommandTaskNGHelper.getElastigroupString;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_MAX_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_MIN_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_TARGET_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DELETE_NEW_ELASTI_GROUP;
import static io.harness.spotinst.model.SpotInstConstants.SWAP_ROUTES_COMMAND_UNIT;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.ElbV2Client;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.spotinst.SpotInstHelperServiceDelegate;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;
import io.harness.spotinst.model.ElastiGroupRenameRequest;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ActionTypeEnum;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Listener;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ModifyListenerRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class ElastigroupDeployTaskHelper {
  @Inject private SpotInstHelperServiceDelegate spotInstHelperServiceDelegate;
  @Inject private ElastigroupCommandTaskNGHelper elastigroupCommandTaskNGHelper;
  @Inject private ElbV2Client elbV2Client;

  public void scaleElastigroup(ElastiGroup elastigroup, String spotInstToken, String spotInstAccountId,
      int steadyStateTimeOut, ILogStreamingTaskClient logStreamingTaskClient, String scaleCommandUnit,
      String waitCommandUnit, CommandUnitsProgress commandUnitsProgress) throws Exception {
    final LogCallback scaleLogCallback = getLogCallback(logStreamingTaskClient, scaleCommandUnit, commandUnitsProgress);
    final LogCallback waitLogCallback = getLogCallback(logStreamingTaskClient, waitCommandUnit, commandUnitsProgress);

    if (elastigroup == null || isEmpty(elastigroup.getId())) {
      scaleLogCallback.saveExecutionLog(
          "No Elastigroup eligible for scaling", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      waitLogCallback.saveExecutionLog(
          "No Elastigroup eligible for scaling", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      return;
    }

    updateElastigroup(spotInstToken, spotInstAccountId, elastigroup, scaleLogCallback);
    elastigroupCommandTaskNGHelper.waitForSteadyState(
        elastigroup, spotInstAccountId, spotInstToken, steadyStateTimeOut, waitLogCallback);
  }

  private LogCallback getLogCallback(ILogStreamingTaskClient logStreamingTaskClient, String commandUnitName,
      CommandUnitsProgress commandUnitsProgress) {
    return new NGDelegateLogCallback(logStreamingTaskClient, commandUnitName, true, commandUnitsProgress);
  }

  private void updateElastigroup(String spotInstToken, String spotInstAccountId, ElastiGroup elastiGroup,
      LogCallback logCallback) throws Exception {
    try {
      Optional<ElastiGroup> elastigroupInitialOptional =
          spotInstHelperServiceDelegate.getElastiGroupById(spotInstToken, spotInstAccountId, elastiGroup.getId());

      if (!elastigroupInitialOptional.isPresent()) {
        String message = format("Did not find Elastigroup: %s", getElastigroupString(elastiGroup));
        log.error(message);
        logCallback.saveExecutionLog(message, ERROR, CommandExecutionStatus.FAILURE);
        throw new InvalidRequestException(message);
      }

      ElastiGroup elastigroupInitial = elastigroupInitialOptional.get();
      elastiGroup.setName(elastigroupInitial.getName());

      logCallback.saveExecutionLog(format("Current state of Elastigroup: %s, min: [%d], max: [%d], desired: [%d]",
          getElastigroupString(elastigroupInitial), elastigroupInitial.getCapacity().getMinimum(),
          elastigroupInitial.getCapacity().getMaximum(), elastigroupInitial.getCapacity().getTarget()));

      checkAndUpdateElastigroup(elastiGroup, logCallback);

      logCallback.saveExecutionLog(
          format("Sending request to update Elastigroup: %s with min: [%d], max: [%d] and target: [%d]",
              getElastigroupString(elastiGroup), elastiGroup.getCapacity().getMinimum(),
              elastiGroup.getCapacity().getMaximum(), elastiGroup.getCapacity().getTarget()));

      spotInstHelperServiceDelegate.updateElastiGroupCapacity(
          spotInstToken, spotInstAccountId, elastiGroup.getId(), elastiGroup);

      logCallback.saveExecutionLog("Request sent to update Elastigroup", INFO, SUCCESS);
    } catch (Exception e) {
      logCallback.saveExecutionLog(format("Exception while updating Elastigroup: %s, Error message: [%s]",
                                       getElastigroupString(elastiGroup), e.getMessage()),
          ERROR, FAILURE);
      throw e;
    }
  }

  /**
   * Checks if condition 0 <= min <= target <= max is followed. If it fails: if target < 0, we
   * update with default values else update min and/or max to target individually.
   */
  private void checkAndUpdateElastigroup(ElastiGroup elastigroup, LogCallback logCallback) {
    ElastiGroupCapacity capacity = elastigroup.getCapacity();
    if (!(0 <= capacity.getMinimum() && capacity.getMinimum() <= capacity.getTarget()
            && capacity.getTarget() <= capacity.getMaximum())) {
      int min = capacity.getMinimum();
      int target = capacity.getTarget();
      int max = capacity.getMaximum();
      if (target < 0) {
        capacity.setMinimum(DEFAULT_ELASTIGROUP_MIN_INSTANCES);
        capacity.setTarget(DEFAULT_ELASTIGROUP_TARGET_INSTANCES);
        capacity.setMaximum(DEFAULT_ELASTIGROUP_MAX_INSTANCES);
      } else {
        if (min > target) {
          capacity.setMinimum(target);
        }
        if (max < target) {
          capacity.setMaximum(target);
        }
      }
      logCallback.saveExecutionLog(format("Modifying invalid request to Spotinst to update Elastigroup: %s "
              + "Original min: [%d], max: [%d] and target: [%d], Modified min: [%d], max: [%d] and target: [%d] ",
          getElastigroupString(elastigroup), min, max, target, capacity.getMinimum(), capacity.getMaximum(),
          capacity.getTarget()));
    }
  }

  public void deleteElastigroup(ElastiGroup elastigroup, String spotInstToken, String spotInstAccountId,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    final LogCallback logCallback =
        getLogCallback(logStreamingTaskClient, DELETE_NEW_ELASTI_GROUP, commandUnitsProgress);
    try {
      if (elastigroup == null || isEmpty(elastigroup.getId())) {
        logCallback.saveExecutionLog("No Elastigroup eligible for deletion.", INFO, SUCCESS);
        return;
      }

      logCallback.saveExecutionLog(format(
          "Sending request to Spotinst to delete newly created Elastigroup: %s", getElastigroupString(elastigroup)));
      spotInstHelperServiceDelegate.deleteElastiGroup(spotInstToken, spotInstAccountId, elastigroup.getId());
      logCallback.saveExecutionLog(
          format("Elastigroup: %s deleted successfully", getElastigroupString(elastigroup)), INFO, SUCCESS);
    } catch (Exception e) {
      logCallback.saveExecutionLog(
          format("Exception while deleting Elastigroup, Error message: [%s]", e.getMessage()), ERROR, FAILURE);
      throw e;
    }
  }

  public void renameElastigroup(ElastiGroup elastigroup, String newName, String spotInstAccountId, String spotInstToken,
      ILogStreamingTaskClient logStreamingTaskClient, String commandUnit, CommandUnitsProgress commandUnitsProgress)
      throws Exception {
    final LogCallback logCallback = getLogCallback(logStreamingTaskClient, commandUnit, commandUnitsProgress);

    if (elastigroup == null || isEmpty(elastigroup.getId())) {
      logCallback.saveExecutionLog("No Elastigroup found for renaming", INFO, SUCCESS);
      return;
    }

    logCallback.saveExecutionLog(
        format("Renaming old Elastigroup: %s to name: [%s]", getElastigroupString(elastigroup), newName));
    spotInstHelperServiceDelegate.updateElastiGroup(spotInstToken, spotInstAccountId, elastigroup.getId(),
        ElastiGroupRenameRequest.builder().name(newName).build());
    logCallback.saveExecutionLog("Successfully renamed Elastigroup", INFO, SUCCESS);
  }

  public void restoreLoadBalancerRoutesIfNeeded(List<LoadBalancerDetailsForBGDeployment> loadBalancerDetails,
      AwsInternalConfig awsInternalConfig, String awsRegion, ILogStreamingTaskClient logStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) {
    final LogCallback logCallback =
        getLogCallback(logStreamingTaskClient, SWAP_ROUTES_COMMAND_UNIT, commandUnitsProgress);
    try {
      if (isEmpty(loadBalancerDetails)) {
        logCallback.saveExecutionLog("No Action Needed", INFO, SUCCESS);
        return;
      }

      for (LoadBalancerDetailsForBGDeployment lbDetail : loadBalancerDetails) {
        if (lbDetail.isUseSpecificRules()) {
          restoreSpecificRulesRoutesIfChanged(lbDetail, logCallback, awsInternalConfig, awsRegion);
        } else {
          restoreDefaultRulesRoutesIfChanged(lbDetail, logCallback, awsInternalConfig, awsRegion);
        }
      }

      logCallback.saveExecutionLog("Prod Elastigroup is UP with correct traffic", INFO, SUCCESS);
    } catch (Exception e) {
      logCallback.saveExecutionLog(
          format("Exception while restoring load balancer routes, Error message: [%s]", e.getMessage()), ERROR,
          FAILURE);
      throw e;
    }
  }

  private void restoreSpecificRulesRoutesIfChanged(LoadBalancerDetailsForBGDeployment lbDetail, LogCallback logCallback,
      AwsInternalConfig awsInternalConfig, String awsRegion) {
    List<Listener> listeners = elastigroupCommandTaskNGHelper.getElbListenersForLoadBalancer(
        lbDetail.getLoadBalancerName(), awsInternalConfig, awsRegion);

    Listener prodListener = elastigroupCommandTaskNGHelper.getListenerByPort(
        lbDetail.getProdListenerPort(), listeners, lbDetail.getLoadBalancerName());

    String targetGroupArn = elastigroupCommandTaskNGHelper.getFirstTargetGroupFromListener(
        awsInternalConfig, awsRegion, prodListener.listenerArn(), lbDetail.getProdRuleArn());

    if (lbDetail.getStageTargetGroupArn().equals(targetGroupArn)) {
      logCallback.saveExecutionLog("Routes were updated, rolling back...");
      resetRoutesInRollbackSpecificRulesCase(lbDetail, logCallback, awsInternalConfig, awsRegion);
    }
  }

  private void resetRoutesInRollbackSpecificRulesCase(
      LoadBalancerDetailsForBGDeployment details, LogCallback logCallback, AwsInternalConfig awsConfig, String region) {
    List<Rule> prodRules = getRulesByArn(awsConfig, details.getProdRuleArn(), region);
    logCallback.saveExecutionLog(format("[%d] rules found when searching the given listener rule arn: [%s]",
        prodRules.size(), details.getProdRuleArn()));

    List<Rule> stageRules = getRulesByArn(awsConfig, details.getStageRuleArn(), region);
    logCallback.saveExecutionLog(format("[%d] rules found when searching the given listener rule arn: [%s]",
        stageRules.size(), details.getStageRuleArn()));

    String prodTargetGroup = prodRules.get(0).actions().get(0).targetGroupArn();
    String stageTargetGroup = stageRules.get(0).actions().get(0).targetGroupArn();

    elastigroupCommandTaskNGHelper.modifyListenerRule(
        region, details.getProdListenerArn(), details.getProdRuleArn(), stageTargetGroup, awsConfig, logCallback);
    elastigroupCommandTaskNGHelper.modifyListenerRule(
        region, details.getStageListenerArn(), details.getStageRuleArn(), prodTargetGroup, awsConfig, logCallback);
  }

  private List<Rule> getRulesByArn(AwsInternalConfig awsConfig, String ruleArn, String region) {
    try {
      List<Rule> rules = new ArrayList<>();
      String nextToken = null;
      do {
        DescribeRulesRequest describeRulesRequest =
            DescribeRulesRequest.builder().ruleArns(ruleArn).marker(nextToken).pageSize(10).build();

        DescribeRulesResponse describeRulesResponse =
            elbV2Client.describeRules(awsConfig, describeRulesRequest, region);

        rules.addAll(describeRulesResponse.rules());
        nextToken = describeRulesResponse.nextMarker();
      } while (nextToken != null);

      return rules;
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error(format("Exception while fetching rules for arn: [%s]", ruleArn), sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  private void restoreDefaultRulesRoutesIfChanged(LoadBalancerDetailsForBGDeployment lbDetail, LogCallback logCallback,
      AwsInternalConfig awsInternalConfig, String awsRegion) {
    getListeners(awsInternalConfig, lbDetail.getProdListenerArn(), awsRegion)
        .get(0)
        .defaultActions()
        .stream()
        .filter(action -> ActionTypeEnum.FORWARD.equals(action.type()) && isNotEmpty(action.targetGroupArn()))
        .findFirst()
        .ifPresent(action -> {
          if (action.targetGroupArn().equals(lbDetail.getStageTargetGroupArn())) {
            logCallback.saveExecutionLog(
                format("Listener: [%s] is forwarding traffic to: [%s]. Swap routes in rollback",
                    lbDetail.getProdListenerArn(), lbDetail.getStageTargetGroupArn()));

            restoreDefaultListeners(
                lbDetail.getProdListenerArn(), lbDetail.getStageListenerArn(), awsInternalConfig, awsRegion);

            logCallback.saveExecutionLog("Listeners rolled back successfully");
          }
        });
  }

  private void restoreDefaultListeners(
      String prodListenerArn, String stageListenerArn, AwsInternalConfig awsInternalConfig, String awsRegion) {
    try {
      Listener prodListener = getListeners(awsInternalConfig, prodListenerArn, awsRegion).get(0);
      Listener stageListener = getListeners(awsInternalConfig, stageListenerArn, awsRegion).get(0);

      elbV2Client.modifyListener(awsInternalConfig,
          ModifyListenerRequest.builder()
              .listenerArn(prodListenerArn)
              .defaultActions(stageListener.defaultActions())
              .build(),
          awsRegion);

      elbV2Client.modifyListener(awsInternalConfig,
          ModifyListenerRequest.builder()
              .listenerArn(stageListenerArn)
              .defaultActions(prodListener.defaultActions())
              .build(),
          awsRegion);

    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception while restoring default listeners", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  private List<Listener> getListeners(AwsInternalConfig awsInternalConfig, String listenerArn, String awsRegion) {
    List<Listener> listeners = new ArrayList<>();
    String nextToken = null;
    do {
      DescribeListenersRequest describeListenersRequest =
          DescribeListenersRequest.builder().listenerArns(listenerArn).marker(nextToken).pageSize(10).build();

      DescribeListenersResponse describeListenersResponse =
          elbV2Client.describeListener(awsInternalConfig, describeListenersRequest, awsRegion);

      listeners.addAll(describeListenersResponse.listeners());
      nextToken = describeListenersResponse.nextMarker();

    } while (nextToken != null);
    return listeners;
  }
}
