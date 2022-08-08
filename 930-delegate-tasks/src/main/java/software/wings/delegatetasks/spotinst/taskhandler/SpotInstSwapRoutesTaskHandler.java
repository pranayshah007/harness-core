/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.spotinst.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.spotinst.model.SpotInstConstants.DOWN_SCALE_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.RENAME_NEW_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.RENAME_OLD_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.STAGE_ELASTI_GROUP_NAME_SUFFIX;
import static io.harness.spotinst.model.SpotInstConstants.SWAP_ROUTES_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.UP_SCALE_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.aws.AwsElbListener;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.delegate.task.spotinst.request.SpotInstSwapRoutesTaskParameters;
import io.harness.delegate.task.spotinst.request.SpotInstTaskParameters;
import io.harness.delegate.task.spotinst.response.SpotInstTaskExecutionResponse;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;
import io.harness.spotinst.model.ElastiGroupRenameRequest;

import software.wings.beans.AwsConfig;
import software.wings.beans.SpotInstConfig;
import software.wings.beans.command.ExecutionLogCallback;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.model.Action;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeListenersResult;
import com.amazonaws.services.elasticloadbalancingv2.model.Rule;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class SpotInstSwapRoutesTaskHandler extends SpotInstTaskHandler {
  @Override
  protected SpotInstTaskExecutionResponse executeTaskInternal(SpotInstTaskParameters spotInstTaskParameters,
      SpotInstConfig spotInstConfig, AwsConfig awsConfig) throws Exception {
    if (!(spotInstTaskParameters instanceof SpotInstSwapRoutesTaskParameters)) {
      String message =
          format("Parameters of unrecognized class: [%s] found while executing setup step. Workflow execution: [%s]",
              spotInstTaskParameters.getClass().getSimpleName(), spotInstTaskParameters.getWorkflowExecutionId());
      log.error(message);
      return SpotInstTaskExecutionResponse.builder().commandExecutionStatus(FAILURE).errorMessage(message).build();
    }
    String spotInstAccountId = spotInstConfig.getSpotInstAccountId();
    String spotInstToken = String.valueOf(spotInstConfig.getSpotInstToken());
    SpotInstSwapRoutesTaskParameters swapRoutesParameters = (SpotInstSwapRoutesTaskParameters) spotInstTaskParameters;
    if (swapRoutesParameters.isRollback()) {
      return executeRollback(spotInstAccountId, spotInstToken, awsConfig, swapRoutesParameters);
    } else {
      return executeDeploy(spotInstAccountId, spotInstToken, awsConfig, swapRoutesParameters);
    }
  }

  private SpotInstTaskExecutionResponse executeDeploy(String spotInstAccountId, String spotInstToken,
      AwsConfig awsConfig, SpotInstSwapRoutesTaskParameters swapRoutesParameters) throws Exception {
    ExecutionLogCallback logCallback = getLogCallBack(swapRoutesParameters, SWAP_ROUTES_COMMAND_UNIT);
    String prodElastiGroupName = swapRoutesParameters.getElastiGroupNamePrefix();
    String stageElastiGroupName =
        format("%s__%s", swapRoutesParameters.getElastiGroupNamePrefix(), STAGE_ELASTI_GROUP_NAME_SUFFIX);
    ElastiGroup newElastiGroup = swapRoutesParameters.getNewElastiGroup();
    String newElastiGroupId = (newElastiGroup != null) ? newElastiGroup.getId() : EMPTY;
    ElastiGroup oldElastiGroup = swapRoutesParameters.getOldElastiGroup();
    String oldElastiGroupId = (oldElastiGroup != null) ? oldElastiGroup.getId() : EMPTY;

    if (isNotEmpty(newElastiGroupId)) {
      logCallback.saveExecutionLog(
          format("Sending request to rename Elastigroup with Id: [%s] to [%s]", newElastiGroupId, prodElastiGroupName));
      spotInstHelperServiceDelegate.updateElastiGroup(spotInstToken, spotInstAccountId, newElastiGroupId,
          ElastiGroupRenameRequest.builder().name(prodElastiGroupName).build());
    }
    if (isNotEmpty(oldElastiGroupId)) {
      logCallback.saveExecutionLog(
          format("Sending request to rename Elastigroup with Id: [%s] to [%s]", oldElastiGroup, stageElastiGroupName));
      spotInstHelperServiceDelegate.updateElastiGroup(spotInstToken, spotInstAccountId, oldElastiGroupId,
          ElastiGroupRenameRequest.builder().name(stageElastiGroupName).build());
    }

    logCallback.saveExecutionLog("Updating Listener Rules for Load Balancer");
    awsElbHelperServiceDelegate.updateListenersForSpotInstBGDeployment(awsConfig, emptyList(),
        swapRoutesParameters.getLBdetailsForBGDeploymentList(), swapRoutesParameters.getAwsRegion(), logCallback);
    logCallback.saveExecutionLog("Route Updated Successfully", INFO, SUCCESS);

    // Downsize if configured on state
    if (swapRoutesParameters.isDownsizeOldElastiGroup() && isNotEmpty(oldElastiGroupId)) {
      ElastiGroup temp = ElastiGroup.builder()
                             .id(oldElastiGroupId)
                             .name(stageElastiGroupName)
                             .capacity(ElastiGroupCapacity.builder().minimum(0).maximum(0).target(0).build())
                             .build();
      int steadyStateTimeOut = getTimeOut(swapRoutesParameters.getSteadyStateTimeOut());
      updateElastiGroupAndWait(spotInstToken, spotInstAccountId, temp, steadyStateTimeOut, swapRoutesParameters,
          DOWN_SCALE_COMMAND_UNIT, DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT);
    } else {
      logCallback = getLogCallBack(swapRoutesParameters, DOWN_SCALE_COMMAND_UNIT);
      logCallback.saveExecutionLog("Nothing to Downsize.", INFO, SUCCESS);
      logCallback = getLogCallBack(swapRoutesParameters, DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT);
      logCallback.saveExecutionLog("No Downsize was required, Swap Route Successfully Completed", INFO, SUCCESS);
    }
    return SpotInstTaskExecutionResponse.builder().commandExecutionStatus(SUCCESS).build();
  }

  private SpotInstTaskExecutionResponse executeRollback(String spotInstAccountId, String spotInstToken,
      AwsConfig awsConfig, SpotInstSwapRoutesTaskParameters swapRoutesParameters) throws Exception {
    String prodElastiGroupName = swapRoutesParameters.getElastiGroupNamePrefix();
    String stageElastiGroupName =
        format("%s__%s", swapRoutesParameters.getElastiGroupNamePrefix(), STAGE_ELASTI_GROUP_NAME_SUFFIX);
    ElastiGroup newElastiGroup = swapRoutesParameters.getNewElastiGroup();
    String newElastiGroupId = (newElastiGroup != null) ? newElastiGroup.getId() : EMPTY;
    ElastiGroup oldElastiGroup = swapRoutesParameters.getOldElastiGroup();
    String oldElastiGroupId = (oldElastiGroup != null) ? oldElastiGroup.getId() : EMPTY;
    int steadyStateTimeOut = getTimeOut(swapRoutesParameters.getSteadyStateTimeOut());
    ExecutionLogCallback logCallback;

    if (oldElastiGroup != null && isNotEmpty(oldElastiGroupId)) {
      ElastiGroup temp = ElastiGroup.builder()
                             .id(oldElastiGroupId)
                             .name(prodElastiGroupName)
                             .capacity(oldElastiGroup.getCapacity())
                             .build();
      updateElastiGroupAndWait(spotInstToken, spotInstAccountId, temp, steadyStateTimeOut, swapRoutesParameters,
          UP_SCALE_COMMAND_UNIT, UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT);

      logCallback = getLogCallBack(swapRoutesParameters, RENAME_OLD_COMMAND_UNIT);
      logCallback.saveExecutionLog(
          format("Renaming old Elastigroup with id: [%s] to name: [%s]", oldElastiGroupId, prodElastiGroupName));
      spotInstHelperServiceDelegate.updateElastiGroup(spotInstToken, spotInstAccountId, oldElastiGroupId,
          ElastiGroupRenameRequest.builder().name(prodElastiGroupName).build());
      logCallback.saveExecutionLog("Successfully renamed old elastiGroup", INFO, SUCCESS);
    } else {
      createAndFinishEmptyExecutionLog(
          swapRoutesParameters, UP_SCALE_COMMAND_UNIT, "No old Elastigroup found for upscaling");
      createAndFinishEmptyExecutionLog(
          swapRoutesParameters, UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT, "No old Elastigroup found for upscaling");
      createAndFinishEmptyExecutionLog(
          swapRoutesParameters, RENAME_OLD_COMMAND_UNIT, "No old Elastigroup found for renaming");
    }

    restoreRoutesToOriginalStateIfChanged(awsConfig, swapRoutesParameters);

    if (isNotEmpty(newElastiGroupId)) {
      // Downsize new elastiGrup
      ElastiGroup temp = ElastiGroup.builder()
                             .id(newElastiGroupId)
                             .name(stageElastiGroupName)
                             .capacity(ElastiGroupCapacity.builder().minimum(0).maximum(0).target(0).build())
                             .build();
      updateElastiGroupAndWait(spotInstToken, spotInstAccountId, temp, steadyStateTimeOut, swapRoutesParameters,
          DOWN_SCALE_COMMAND_UNIT, DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT);

      // Rename new elastiGroup to STAGE
      logCallback = getLogCallBack(swapRoutesParameters, RENAME_NEW_COMMAND_UNIT);
      logCallback.saveExecutionLog(
          format("Renaming Elastigroup with id: [%s] to name: [%s]", newElastiGroupId, stageElastiGroupName));
      spotInstHelperServiceDelegate.updateElastiGroup(spotInstToken, spotInstAccountId, newElastiGroupId,
          ElastiGroupRenameRequest.builder().name(stageElastiGroupName).build());
      logCallback.saveExecutionLog("Completed Rename", INFO, SUCCESS);
    }

    return SpotInstTaskExecutionResponse.builder().commandExecutionStatus(SUCCESS).build();
  }

  @VisibleForTesting
  void restoreSpecificRulesRoutesIfChanged(LoadBalancerDetailsForBGDeployment details, ExecutionLogCallback logCallback,
      AwsConfig awsConfig, String region) {
    int prodListenerPort = Integer.parseInt(details.getProdListenerPort());
    List<AwsElbListener> listeners = awsElbHelperServiceDelegate.getElbListenersForLoadBalaner(
        awsConfig, emptyList(), region, details.getLoadBalancerName());
    AwsElbListener prodListener =
        getListenerOnPort(listeners, prodListenerPort, details.getLoadBalancerName(), logCallback);
    TargetGroup currentProdTargetGroup = awsElbHelperServiceDelegate.fetchTargetGroupForSpecificRules(
        prodListener, details.getProdRuleArn(), logCallback, awsConfig, region, emptyList());
    if (details.getStageTargetGroupArn().equals(currentProdTargetGroup.getTargetGroupArn())) {
      logCallback.saveExecutionLog("Routes were updated. Swapping routes in Rollback");
      resetRoutesInRollbackSpecificRulesCase(details, logCallback, awsConfig, region);
    }
  }

  private void resetRoutesInRollbackSpecificRulesCase(LoadBalancerDetailsForBGDeployment details,
      ExecutionLogCallback logCallback, AwsConfig awsConfig, String region) {
    List<Rule> prodRules = awsElbHelperServiceDelegate.getListenerRuleFromListenerRuleArn(
        awsConfig, emptyList(), region, details.getProdRuleArn(), logCallback);
    List<Rule> stageRules = awsElbHelperServiceDelegate.getListenerRuleFromListenerRuleArn(
        awsConfig, emptyList(), region, details.getStageRuleArn(), logCallback);

    String prodTargetGroup = prodRules.get(0).getActions().get(0).getTargetGroupArn();
    String stageTargetGroup = stageRules.get(0).getActions().get(0).getTargetGroupArn();

    AmazonElasticLoadBalancing client =
        awsElbHelperServiceDelegate.getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig);

    awsElbHelperServiceDelegate.modifyListenerRule(
        client, details.getProdListenerArn(), details.getProdRuleArn(), stageTargetGroup, logCallback);
    awsElbHelperServiceDelegate.modifyListenerRule(
        client, details.getStageListenerArn(), details.getStageRuleArn(), prodTargetGroup, logCallback);
  }

  private void restoreDefaultRulesRoutesIfChanged(LoadBalancerDetailsForBGDeployment lbDetail,
      ExecutionLogCallback logCallback, AwsConfig awsConfig, SpotInstSwapRoutesTaskParameters swapRoutesParameters) {
    DescribeListenersResult result = awsElbHelperServiceDelegate.describeListenerResult(
        awsConfig, emptyList(), lbDetail.getProdListenerArn(), swapRoutesParameters.getAwsRegion());
    Optional<Action> optionalAction =
        result.getListeners()
            .get(0)
            .getDefaultActions()
            .stream()
            .filter(action -> "forward".equalsIgnoreCase(action.getType()) && isNotEmpty(action.getTargetGroupArn()))
            .findFirst();

    if (optionalAction.isPresent()
        && optionalAction.get().getTargetGroupArn().equals(lbDetail.getStageTargetGroupArn())) {
      logCallback.saveExecutionLog(format("Listener: [%s] is forwarding traffic to: [%s]. Swap routes in rollback",
          lbDetail.getProdListenerArn(), lbDetail.getStageTargetGroupArn()));
      awsElbHelperServiceDelegate.updateDefaultListenersForSpotInstBG(awsConfig, emptyList(),
          lbDetail.getProdListenerArn(), lbDetail.getStageListenerArn(), swapRoutesParameters.getAwsRegion());
    }
  }

  private void restoreRoutesToOriginalStateIfChanged(
      AwsConfig awsConfig, SpotInstSwapRoutesTaskParameters swapRoutesParameters) {
    ExecutionLogCallback logCallback = getLogCallBack(swapRoutesParameters, SWAP_ROUTES_COMMAND_UNIT);
    if (isEmpty(swapRoutesParameters.getLBdetailsForBGDeploymentList())) {
      logCallback.saveExecutionLog("No Action Needed", INFO, SUCCESS);
      return;
    }

    for (LoadBalancerDetailsForBGDeployment lbDetail : swapRoutesParameters.getLBdetailsForBGDeploymentList()) {
      if (lbDetail.isUseSpecificRules()) {
        restoreSpecificRulesRoutesIfChanged(lbDetail, logCallback, awsConfig, swapRoutesParameters.getAwsRegion());
      } else {
        restoreDefaultRulesRoutesIfChanged(lbDetail, logCallback, awsConfig, swapRoutesParameters);
      }
    }
    logCallback.saveExecutionLog("Prod Elastigroup is UP with correct traffic", INFO, SUCCESS);
  }
}
