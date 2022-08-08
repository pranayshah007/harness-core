/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.spotinst.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.spotinst.model.SpotInstConstants.COMPUTE;
import static io.harness.spotinst.model.SpotInstConstants.ELASTI_GROUP_IMAGE_CONFIG;
import static io.harness.spotinst.model.SpotInstConstants.ELASTI_GROUP_USER_DATA_CONFIG;
import static io.harness.spotinst.model.SpotInstConstants.GROUP_CONFIG_ELEMENT;
import static io.harness.spotinst.model.SpotInstConstants.LAUNCH_SPECIFICATION;
import static io.harness.spotinst.model.SpotInstConstants.LB_TYPE_TG;
import static io.harness.spotinst.model.SpotInstConstants.LOAD_BALANCERS_CONFIG;
import static io.harness.spotinst.model.SpotInstConstants.SETUP_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.STAGE_ELASTI_GROUP_NAME_SUFFIX;
import static io.harness.spotinst.model.SpotInstConstants.elastiGroupsToKeep;

import static com.google.api.client.util.Lists.newArrayList;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.aws.AwsElbListener;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.delegate.task.spotinst.request.SpotInstSetupTaskParameters;
import io.harness.delegate.task.spotinst.request.SpotInstTaskParameters;
import io.harness.delegate.task.spotinst.response.SpotInstSetupTaskResponse;
import io.harness.delegate.task.spotinst.response.SpotInstSetupTaskResponse.SpotInstSetupTaskResponseBuilder;
import io.harness.delegate.task.spotinst.response.SpotInstTaskExecutionResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;
import io.harness.spotinst.model.ElastiGroupLoadBalancer;
import io.harness.spotinst.model.ElastiGroupLoadBalancerConfig;

import software.wings.beans.AwsConfig;
import software.wings.beans.SpotInstConfig;
import software.wings.beans.command.ExecutionLogCallback;

import com.amazonaws.services.elasticloadbalancingv2.model.Listener;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup;
import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class SpotInstSetupTaskHandler extends SpotInstTaskHandler {
  @Override
  protected SpotInstTaskExecutionResponse executeTaskInternal(SpotInstTaskParameters spotInstTaskParameters,
      SpotInstConfig spotInstConfig, AwsConfig awsConfig) throws Exception {
    if (!(spotInstTaskParameters instanceof SpotInstSetupTaskParameters)) {
      String message =
          format("Parameters of unrecognized class: [%s] found while executing setup step. Workflow execution: [%s]",
              spotInstTaskParameters.getClass().getSimpleName(), spotInstTaskParameters.getWorkflowExecutionId());
      log.error(message);
      return SpotInstTaskExecutionResponse.builder().commandExecutionStatus(FAILURE).errorMessage(message).build();
    }

    String spotInstAccountId = spotInstConfig.getSpotInstAccountId();
    String spotInstToken = String.valueOf(spotInstConfig.getSpotInstToken());
    SpotInstSetupTaskParameters setupTaskParameters = (SpotInstSetupTaskParameters) spotInstTaskParameters;
    ExecutionLogCallback logCallback = getLogCallBack(spotInstTaskParameters, SETUP_COMMAND_UNIT);

    if (setupTaskParameters.isBlueGreen()) {
      // Handle Blue Green
      return executeTaskInternalForBlueGreen(
          setupTaskParameters, spotInstAccountId, spotInstToken, awsConfig, logCallback);
    }

    // Handle canary and basic
    String prefix = format("%s__", setupTaskParameters.getElastiGroupNamePrefix());
    int elastiGroupVersion = 1;
    logCallback.saveExecutionLog(format("Querying Spotinst for existing Elastigroups with prefix: [%s]", prefix));
    List<ElastiGroup> elastiGroups = spotInstHelperServiceDelegate.listAllElastiGroups(
        spotInstToken, spotInstAccountId, setupTaskParameters.getElastiGroupNamePrefix());
    if (isNotEmpty(elastiGroups)) {
      elastiGroupVersion =
          Integer.parseInt(elastiGroups.get(elastiGroups.size() - 1).getName().substring(prefix.length())) + 1;
    }
    String newElastiGroupName = format("%s%d", prefix, elastiGroupVersion);

    String finalJson = generateFinalJson(setupTaskParameters, newElastiGroupName);

    logCallback.saveExecutionLog(format("Sending request to create Elastigroup with name: [%s]", newElastiGroupName));
    ElastiGroup elastiGroup =
        spotInstHelperServiceDelegate.createElastiGroup(spotInstToken, spotInstAccountId, finalJson);
    String newElastiGroupId = elastiGroup.getId();
    logCallback.saveExecutionLog(format("Created Elastigroup with id: [%s]", newElastiGroupId));

    /**
     * Look at all the Elastigroups except the "LAST" elastigroup.
     * If they have running instances, we will downscale them to 0.
     */
    List<ElastiGroup> groupsWithoutInstances = newArrayList();
    List<ElastiGroup> groupToDownsizeDuringDeploy = emptyList();
    if (isNotEmpty(elastiGroups)) {
      groupToDownsizeDuringDeploy = singletonList(elastiGroups.get(elastiGroups.size() - 1));
      for (int i = 0; i < elastiGroups.size() - 1; i++) {
        ElastiGroup elastigroupCurrent = elastiGroups.get(i);
        ElastiGroupCapacity capacity = elastigroupCurrent.getCapacity();
        if (capacity == null) {
          groupsWithoutInstances.add(elastigroupCurrent);
          continue;
        }
        int target = capacity.getTarget();
        if (target == 0) {
          groupsWithoutInstances.add(elastigroupCurrent);
        } else {
          logCallback.saveExecutionLog(
              format("Downscaling old Elastigroup with id: [%s] to 0 instances.", elastigroupCurrent.getId()));
          ElastiGroup temp = ElastiGroup.builder()
                                 .id(elastigroupCurrent.getId())
                                 .name(elastigroupCurrent.getName())
                                 .capacity(ElastiGroupCapacity.builder().minimum(0).maximum(0).target(0).build())
                                 .build();
          spotInstHelperServiceDelegate.updateElastiGroupCapacity(
              spotInstToken, spotInstAccountId, elastigroupCurrent.getId(), temp);
        }
      }
    }

    int lastIdx = groupsWithoutInstances.size() - elastiGroupsToKeep;
    for (int i = 0; i < lastIdx; i++) {
      String nameToDelete = groupsWithoutInstances.get(i).getName();
      String idToDelete = groupsWithoutInstances.get(i).getId();
      logCallback.saveExecutionLog(
          format("Sending request to delete Elastigroup: [%s] with id: [%s]", nameToDelete, idToDelete));
      spotInstHelperServiceDelegate.deleteElastiGroup(spotInstToken, spotInstAccountId, idToDelete);
    }

    logCallback.saveExecutionLog("Completed setup for Spotinst", INFO, SUCCESS);
    return SpotInstTaskExecutionResponse.builder()
        .commandExecutionStatus(SUCCESS)
        .spotInstTaskResponse(SpotInstSetupTaskResponse.builder()
                                  .newElastiGroup(elastiGroup)
                                  .groupToBeDownsized(groupToDownsizeDuringDeploy)
                                  .build())
        .build();
  }

  @VisibleForTesting
  SpotInstTaskExecutionResponse executeTaskInternalForBlueGreen(SpotInstSetupTaskParameters setupTaskParameters,
      String spotInstAccountId, String spotInstToken, AwsConfig awsConfig, ExecutionLogCallback logCallback)
      throws Exception {
    SpotInstSetupTaskResponseBuilder builder = SpotInstSetupTaskResponse.builder();
    List<LoadBalancerDetailsForBGDeployment> lbDetailList =
        fetchAllLoadBalancerDetails(setupTaskParameters, awsConfig, logCallback);
    builder.lbDetailsForBGDeployments(lbDetailList);
    // Update lbDetails with fetched details, as they have more data field in
    setupTaskParameters.setAwsLoadBalancerConfigs(lbDetailList);

    // Generate STAGE elastiGroup name
    String stageElastiGroupName =
        format("%s__%s", setupTaskParameters.getElastiGroupNamePrefix(), STAGE_ELASTI_GROUP_NAME_SUFFIX);

    // Generate final json by substituting name, capacity and LBConfig
    String finalJson = generateFinalJson(setupTaskParameters, stageElastiGroupName);

    // Check if existing elastigroup with exists with same stage name
    logCallback.saveExecutionLog(format("Querying to find Elastigroup with name: [%s]", stageElastiGroupName));
    Optional<ElastiGroup> stageOptionalElastiGroup =
        spotInstHelperServiceDelegate.getElastiGroupByName(spotInstToken, spotInstAccountId, stageElastiGroupName);
    ElastiGroup stageElastiGroup;
    if (stageOptionalElastiGroup.isPresent()) {
      stageElastiGroup = stageOptionalElastiGroup.get();
      logCallback.saveExecutionLog(
          format("Found stage Elastigroup with id: [%s]. Deleting it. ", stageElastiGroup.getId()));
      spotInstHelperServiceDelegate.deleteElastiGroup(spotInstToken, spotInstAccountId, stageElastiGroup.getId());
    }

    // Create new elastiGroup
    logCallback.saveExecutionLog(
        format("Sending request to create new Elastigroup with name: [%s]", stageElastiGroupName));
    stageElastiGroup = spotInstHelperServiceDelegate.createElastiGroup(spotInstToken, spotInstAccountId, finalJson);
    String stageElastiGroupId = stageElastiGroup.getId();
    logCallback.saveExecutionLog(
        format("Created Elastigroup with name: [%s] and id: [%s]", stageElastiGroupName, stageElastiGroupId));
    builder.newElastiGroup(stageElastiGroup);

    // Prod ELasti Groups
    String prodElastiGroupName = setupTaskParameters.getElastiGroupNamePrefix();
    logCallback.saveExecutionLog(format("Querying Spotinst for Elastigroup with name: [%s]", prodElastiGroupName));
    Optional<ElastiGroup> prodOptionalElastiGroup =
        spotInstHelperServiceDelegate.getElastiGroupByName(spotInstToken, spotInstAccountId, prodElastiGroupName);
    List<ElastiGroup> prodElastiGroupList;
    if (prodOptionalElastiGroup.isPresent()) {
      ElastiGroup prodElastiGroup = prodOptionalElastiGroup.get();
      logCallback.saveExecutionLog(format("Found existing Prod Elastigroup with name: [%s] and id: [%s]",
          prodElastiGroup.getName(), prodElastiGroup.getId()));
      prodElastiGroupList = singletonList(prodElastiGroup);
    } else {
      prodElastiGroupList = emptyList();
    }
    builder.groupToBeDownsized(prodElastiGroupList);
    logCallback.saveExecutionLog("Completed Blue green setup for Spotinst", INFO, SUCCESS);
    return SpotInstTaskExecutionResponse.builder()
        .commandExecutionStatus(SUCCESS)
        .spotInstTaskResponse(builder.build())
        .build();
  }

  @VisibleForTesting
  String generateFinalJson(SpotInstSetupTaskParameters setupTaskParameters, String newElastiGroupName) {
    Map<String, Object> jsonConfigMap = getJsonConfigMapFromElastigroupJson(setupTaskParameters.getElastiGroupJson());
    Map<String, Object> elastiGroupConfigMap = (Map<String, Object>) jsonConfigMap.get(GROUP_CONFIG_ELEMENT);

    removeUnsupportedFieldsForCreatingNewGroup(elastiGroupConfigMap);
    updateName(elastiGroupConfigMap, newElastiGroupName);
    updateInitialCapacity(elastiGroupConfigMap);
    updateWithLoadBalancerAndImageConfig(setupTaskParameters.getAwsLoadBalancerConfigs(), elastiGroupConfigMap,
        setupTaskParameters.getImage(), setupTaskParameters.getUserData(), setupTaskParameters.isBlueGreen());
    Gson gson = new Gson();
    return gson.toJson(jsonConfigMap);
  }

  private void updateWithLoadBalancerAndImageConfig(List<LoadBalancerDetailsForBGDeployment> lbDetailList,
      Map<String, Object> elastiGroupConfigMap, String image, String userData, boolean blueGreen) {
    Map<String, Object> computeConfigMap = (Map<String, Object>) elastiGroupConfigMap.get(COMPUTE);
    Map<String, Object> launchSpecificationMap = (Map<String, Object>) computeConfigMap.get(LAUNCH_SPECIFICATION);

    if (blueGreen) {
      launchSpecificationMap.put(LOAD_BALANCERS_CONFIG,
          ElastiGroupLoadBalancerConfig.builder().loadBalancers(generateLBConfigs(lbDetailList)).build());
    }
    launchSpecificationMap.put(ELASTI_GROUP_IMAGE_CONFIG, image);
    if (isNotEmpty(userData)) {
      launchSpecificationMap.put(ELASTI_GROUP_USER_DATA_CONFIG, userData);
    }
  }

  private List<ElastiGroupLoadBalancer> generateLBConfigs(List<LoadBalancerDetailsForBGDeployment> lbDetailList) {
    List<ElastiGroupLoadBalancer> elastiGroupLoadBalancers = new ArrayList<>();
    lbDetailList.forEach(loadBalancerdetail
        -> elastiGroupLoadBalancers.add(ElastiGroupLoadBalancer.builder()
                                            .arn(loadBalancerdetail.getStageTargetGroupArn())
                                            .name(loadBalancerdetail.getStageTargetGroupName())
                                            .type(LB_TYPE_TG)
                                            .build()));
    return elastiGroupLoadBalancers;
  }

  private List<LoadBalancerDetailsForBGDeployment> fetchAllLoadBalancerDetails(
      SpotInstSetupTaskParameters setupTaskParameters, AwsConfig awsConfig, ExecutionLogCallback logCallback) {
    List<LoadBalancerDetailsForBGDeployment> awsLoadBalancerConfigs = setupTaskParameters.getAwsLoadBalancerConfigs();
    List<LoadBalancerDetailsForBGDeployment> lbDetailsWithArnValues = new ArrayList<>();
    try {
      for (LoadBalancerDetailsForBGDeployment awsLoadBalancerConfig : awsLoadBalancerConfigs) {
        logCallback.saveExecutionLog(
            format("Querying aws to get the stage target group details for load balancer: [%s]",
                awsLoadBalancerConfig.getLoadBalancerName()));

        LoadBalancerDetailsForBGDeployment loadBalancerDetailsForBGDeployment =
            getListenerResponseDetails(awsConfig, setupTaskParameters.getAwsRegion(),
                awsLoadBalancerConfig.getLoadBalancerName(), logCallback, awsLoadBalancerConfig);

        lbDetailsWithArnValues.add(loadBalancerDetailsForBGDeployment);

        logCallback.saveExecutionLog(format("Using TargetGroup: [%s], ARN: [%s] with new Elastigroup",
            loadBalancerDetailsForBGDeployment.getStageTargetGroupName(),
            loadBalancerDetailsForBGDeployment.getStageTargetGroupArn()));
      }
    } catch (NumberFormatException numberFormatEx) {
      String errorMessage =
          "Unable to fetch load balancer listener details. Please verify port numbers are entered correctly.";
      throw new InvalidRequestException(errorMessage, numberFormatEx, WingsException.USER);
    } catch (InvalidRequestException e) {
      throw new InvalidRequestException("Failed while fetching TargetGroup Details", e, WingsException.USER);
    }

    return lbDetailsWithArnValues;
  }

  private LoadBalancerDetailsForBGDeployment getListenerResponseDetails(AwsConfig awsConfig, String region,
      String loadBalancerName, ExecutionLogCallback logCallback, LoadBalancerDetailsForBGDeployment originalDetails) {
    int stageListenerPort = Integer.parseInt(originalDetails.getStageListenerPort());
    int prodListenerPort = Integer.parseInt(originalDetails.getProdListenerPort());
    List<AwsElbListener> listeners = awsElbHelperServiceDelegate.getElbListenersForLoadBalaner(
        awsConfig, emptyList(), region, originalDetails.getLoadBalancerName());

    TargetGroup prodTargetGroup;
    AwsElbListener prodListener = getListenerOnPort(listeners, prodListenerPort, loadBalancerName, logCallback);
    if (originalDetails.isUseSpecificRules()) {
      prodTargetGroup = awsElbHelperServiceDelegate.fetchTargetGroupForSpecificRules(
          prodListener, originalDetails.getProdRuleArn(), logCallback, awsConfig, region, emptyList());
    } else {
      prodTargetGroup = fetchTargetGroupForListener(awsConfig, region, logCallback, prodListener);
    }

    TargetGroup stageTargetGroup;
    AwsElbListener stageListener = getListenerOnPort(listeners, stageListenerPort, loadBalancerName, logCallback);
    if (originalDetails.isUseSpecificRules()) {
      stageTargetGroup = awsElbHelperServiceDelegate.fetchTargetGroupForSpecificRules(
          stageListener, originalDetails.getStageRuleArn(), logCallback, awsConfig, region, emptyList());
    } else {
      stageTargetGroup = fetchTargetGroupForListener(awsConfig, region, logCallback, stageListener);
    }

    return LoadBalancerDetailsForBGDeployment.builder()
        .loadBalancerArn(prodListener.getLoadBalancerArn())
        .loadBalancerName(loadBalancerName)
        .prodListenerArn(prodListener.getListenerArn())
        .prodTargetGroupArn(prodTargetGroup.getTargetGroupArn())
        .prodTargetGroupName(prodTargetGroup.getTargetGroupName())
        .stageListenerArn(stageListener.getListenerArn())
        .stageTargetGroupArn(stageTargetGroup.getTargetGroupArn())
        .stageTargetGroupName(stageTargetGroup.getTargetGroupName())
        .prodListenerPort(Integer.toString(prodListenerPort))
        .stageListenerPort(Integer.toString(stageListenerPort))
        .useSpecificRules(originalDetails.isUseSpecificRules())
        .prodRuleArn(originalDetails.getProdRuleArn())
        .stageRuleArn(originalDetails.getStageRuleArn())
        .build();
  }

  private TargetGroup fetchTargetGroupForListener(
      AwsConfig awsConfig, String region, ExecutionLogCallback logCallback, AwsElbListener stageListener) {
    Listener listener =
        awsElbHelperServiceDelegate.getElbListener(awsConfig, emptyList(), region, stageListener.getListenerArn());
    String targetGroupArn = awsElbHelperServiceDelegate.getTargetGroupForDefaultAction(listener, logCallback);
    Optional<TargetGroup> targetGroup =
        awsElbHelperServiceDelegate.getTargetGroup(awsConfig, emptyList(), region, targetGroupArn);
    if (!targetGroup.isPresent()) {
      String message = format("Did not find any target group with arn: [%s]. ", targetGroupArn);
      log.error(message);
      logCallback.saveExecutionLog(message);
      throw new InvalidRequestException(message);
    }
    return targetGroup.get();
  }
}
