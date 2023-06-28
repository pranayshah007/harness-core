/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.spotinst.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eraro.ErrorCode.INIT_TIMEOUT;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.spotinst.model.SpotInstConstants.CAPACITY;
import static io.harness.spotinst.model.SpotInstConstants.CAPACITY_MAXIMUM_CONFIG_ELEMENT;
import static io.harness.spotinst.model.SpotInstConstants.CAPACITY_MINIMUM_CONFIG_ELEMENT;
import static io.harness.spotinst.model.SpotInstConstants.CAPACITY_TARGET_CONFIG_ELEMENT;
import static io.harness.spotinst.model.SpotInstConstants.CAPACITY_UNIT_CONFIG_ELEMENT;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_MAX_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_MIN_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_TARGET_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DEPLOYMENT_ERROR;
import static io.harness.spotinst.model.SpotInstConstants.ELASTI_GROUP_CREATED_AT;
import static io.harness.spotinst.model.SpotInstConstants.ELASTI_GROUP_ID;
import static io.harness.spotinst.model.SpotInstConstants.ELASTI_GROUP_UPDATED_AT;
import static io.harness.spotinst.model.SpotInstConstants.NAME_CONFIG_ELEMENT;
import static io.harness.spotinst.model.SpotInstConstants.UNIT_INSTANCE;
import static io.harness.spotinst.model.SpotInstConstants.defaultSteadyStateTimeout;
import static io.harness.threading.Morpheus.sleep;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.concurrent.HTimeLimiter;
import io.harness.delegate.task.aws.AwsElbListener;
import io.harness.delegate.task.spotinst.request.SpotInstTaskParameters;
import io.harness.delegate.task.spotinst.response.SpotInstTaskExecutionResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.spotinst.SpotInstHelperServiceDelegate;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;
import io.harness.spotinst.model.ElastiGroupInstanceHealth;

import software.wings.beans.AwsConfig;
import software.wings.beans.SpotInstConfig;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.service.intfc.aws.delegate.AwsEc2HelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsElbHelperServiceDelegate;

import com.amazonaws.services.ec2.model.Instance;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public abstract class SpotInstTaskHandler {
  @Inject protected DelegateLogService delegateLogService;
  @Inject protected SpotInstHelperServiceDelegate spotInstHelperServiceDelegate;
  @Inject protected AwsElbHelperServiceDelegate awsElbHelperServiceDelegate;
  @Inject protected TimeLimiter timeLimiter;
  @Inject protected AwsEc2HelperServiceDelegate awsEc2HelperServiceDelegate;

  public SpotInstTaskExecutionResponse executeTask(
      SpotInstTaskParameters spotInstTaskParameters, SpotInstConfig spotInstConfig, AwsConfig awsConfig) {
    try {
      SpotInstTaskExecutionResponse response = executeTaskInternal(spotInstTaskParameters, spotInstConfig, awsConfig);
      if (!spotInstTaskParameters.isSyncTask()) {
        ExecutionLogCallback logCallback = getLogCallBack(spotInstTaskParameters, DEPLOYMENT_ERROR);
        logCallback.saveExecutionLog("No deployment error. Execution success", INFO, SUCCESS);
      }
      return response;
    } catch (Exception ex) {
      if (ex instanceof WingsException) {
        WingsException we = (WingsException) ex;
        if (spotInstTaskParameters.isTimeoutSupported() && INIT_TIMEOUT.equals(we.getCode())) {
          return SpotInstTaskExecutionResponse.builder()
              .commandExecutionStatus(FAILURE)
              .errorMessage("Timed out while waiting for task to complete")
              .isTimeoutError(true)
              .build();
        }
      }
      if (spotInstTaskParameters.isSyncTask()) {
        throw new InvalidRequestException(ex.getMessage(), ex);
      } else {
        String message = ex.getMessage();
        ExecutionLogCallback logCallback = getLogCallBack(spotInstTaskParameters, DEPLOYMENT_ERROR);
        logCallback.saveExecutionLog(message, ERROR, FAILURE);
        log.error(format("Exception: [%s] while processing spotinst task: [%s]. Workflow execution id: [%s]", message,
                      spotInstTaskParameters.getCommandType().name(), spotInstTaskParameters.getWorkflowExecutionId()),
            ex);
        return SpotInstTaskExecutionResponse.builder().commandExecutionStatus(FAILURE).errorMessage(message).build();
      }
    }
  }

  protected ExecutionLogCallback getLogCallBack(SpotInstTaskParameters parameters, String commandUnit) {
    return new ExecutionLogCallback(
        delegateLogService, parameters.getAccountId(), parameters.getAppId(), parameters.getActivityId(), commandUnit);
  }

  protected void updateElastiGroupAndWait(String spotInstToken, String spotInstAccountId, ElastiGroup elastiGroup,
      int steadyStateTimeOut, SpotInstTaskParameters parameters, String scaleCommandUnitName,
      String waitCommandUnitName) throws Exception {
    String workflowExecutionId = parameters.getWorkflowExecutionId();
    ExecutionLogCallback logCallback = getLogCallBack(parameters, scaleCommandUnitName);
    Optional<ElastiGroup> elastiGroupIntialOptional =
        spotInstHelperServiceDelegate.getElastiGroupById(spotInstToken, spotInstAccountId, elastiGroup.getId());
    if (!elastiGroupIntialOptional.isPresent()) {
      String message = format(
          "Did not find Elastigroup with id: [%s]. Workflow execution: [%s]", elastiGroup.getId(), workflowExecutionId);
      log.error(message);
      logCallback.saveExecutionLog(message);
      throw new InvalidRequestException(message);
    }
    ElastiGroup elastiGroupIntial = elastiGroupIntialOptional.get();
    logCallback.saveExecutionLog(format(
        "Current state of Elastigroup: [%s], min: [%d], max: [%d], desired: [%d], Id: [%s]", elastiGroupIntial.getId(),
        elastiGroupIntial.getCapacity().getMinimum(), elastiGroupIntial.getCapacity().getMaximum(),
        elastiGroupIntial.getCapacity().getTarget(), elastiGroupIntial.getId()));
    checkAndUpdateElastiGroup(elastiGroup, logCallback);
    int minInstances = elastiGroup.getCapacity().getMinimum();
    int maxInstances = elastiGroup.getCapacity().getMaximum();
    int targetInstances = elastiGroup.getCapacity().getTarget();
    String elastiGroupId = elastiGroup.getId();
    logCallback.saveExecutionLog(
        format("Sending request to Spotinst to update Elastigroup: [%s] with min: [%d], max: [%d] and target: [%d]",
            elastiGroupId, minInstances, maxInstances, targetInstances));
    spotInstHelperServiceDelegate.updateElastiGroupCapacity(
        spotInstToken, spotInstAccountId, elastiGroupId, elastiGroup);
    logCallback.saveExecutionLog("Request Sent to update Elastigroup", INFO, SUCCESS);

    ExecutionLogCallback waitLogCallback = getLogCallBack(parameters, waitCommandUnitName);
    waitLogCallback.saveExecutionLog(format("Waiting for Elastigroup: [%s] to reach steady state", elastiGroupId));
    try {
      HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMinutes(steadyStateTimeOut), () -> {
        while (true) {
          if (allInstancesHealthy(spotInstToken, spotInstAccountId, elastiGroupId, waitLogCallback, targetInstances)) {
            return true;
          }
          sleep(ofSeconds(20));
        }
      });
    } catch (UncheckedTimeoutException e) {
      String errorMessage =
          format("Timed out while waiting for steady state for Elastigroup: [%s]. Workflow execution: [%s]",
              elastiGroupId, workflowExecutionId);
      waitLogCallback.saveExecutionLog(errorMessage, ERROR);
      throw new WingsException(INIT_TIMEOUT).addParam("message", errorMessage);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(
          format("Timed out while waiting for steady state for Elastigroup: [%s]. Workflow execution: [%s]",
              elastiGroupId, workflowExecutionId),
          e);
    }
  }

  /**
   * Checks if condition 0 <= min <= target <= max is followed.
   * If it fails:
   *  if target < 0, we update with default values
   *  else update min and/or max to target individually.
   * @param elastiGroup
   * @param logCallback
   */
  private void checkAndUpdateElastiGroup(ElastiGroup elastiGroup, ExecutionLogCallback logCallback) {
    ElastiGroupCapacity capacity = elastiGroup.getCapacity();
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
      logCallback.saveExecutionLog(format("Modifying invalid request to Spotinst to update Elastigroup:[%s] "
              + "Original min: [%d], max: [%d] and target: [%d], Modified min: [%d], max: [%d] and target: [%d] ",
          elastiGroup.getId(), min, max, target, capacity.getMinimum(), capacity.getMaximum(), capacity.getTarget()));
    }
  }

  AwsElbListener getListenerOnPort(
      List<AwsElbListener> listeners, int port, String loadBalancerName, ExecutionLogCallback logCallback) {
    if (isEmpty(listeners)) {
      String message = format("Did not find any listeners for load balancer: [%s]", loadBalancerName);
      log.error(message);
      logCallback.saveExecutionLog(message);
      throw new InvalidRequestException(message);
    }
    Optional<AwsElbListener> optionalListener =
        listeners.stream().filter(listener -> port == listener.getPort()).findFirst();
    if (!optionalListener.isPresent()) {
      String message =
          format("Did not find any listeners on port: [%d] for load balancer: [%s].", port, loadBalancerName);
      log.error(message);
      logCallback.saveExecutionLog(message);
      throw new InvalidRequestException(message);
    }
    return optionalListener.get();
  }

  @VisibleForTesting
  boolean allInstancesHealthy(String spotInstToken, String spotInstAccountId, String elastiGroupId,
      ExecutionLogCallback waitLogCallback, int targetInstances) throws Exception {
    List<ElastiGroupInstanceHealth> instanceHealths =
        spotInstHelperServiceDelegate.listElastiGroupInstancesHealth(spotInstToken, spotInstAccountId, elastiGroupId);
    int currentTotalCount = isEmpty(instanceHealths) ? 0 : instanceHealths.size();
    int currentHealthyCount = isEmpty(instanceHealths)
        ? 0
        : (int) instanceHealths.stream().filter(health -> "HEALTHY".equals(health.getHealthStatus())).count();
    if (targetInstances == 0) {
      if (currentTotalCount == 0) {
        waitLogCallback.saveExecutionLog(
            format("Elastigroup: [%s] does not have any instances.", elastiGroupId), INFO, SUCCESS);
        return true;
      } else {
        waitLogCallback.saveExecutionLog(format("Elastigroup: [%s] still has [%d] total and [%d] healthy instances",
            elastiGroupId, currentTotalCount, currentHealthyCount));
      }
    } else {
      waitLogCallback.saveExecutionLog(
          format("Desired instances: [%d], Total instances: [%d], Healthy instances: [%d] for Elastigroup: [%s]",
              targetInstances, currentTotalCount, currentHealthyCount, elastiGroupId));
      if (targetInstances == currentHealthyCount && targetInstances == currentTotalCount) {
        waitLogCallback.saveExecutionLog(
            format("Elastigroup: [%s] reached steady state", elastiGroupId), INFO, SUCCESS);
        return true;
      }
    }
    return false;
  }

  @VisibleForTesting
  int getTimeOut(int timeOut) {
    return (timeOut > 0) ? timeOut : defaultSteadyStateTimeout;
  }

  @VisibleForTesting
  List<Instance> getAllEc2InstancesOfElastiGroup(AwsConfig awsConfig, String awsRegion, String spotInstToken,
      String spotInstAccountId, String elastiGroupId) throws Exception {
    List<ElastiGroupInstanceHealth> instanceHealths =
        spotInstHelperServiceDelegate.listElastiGroupInstancesHealth(spotInstToken, spotInstAccountId, elastiGroupId);
    if (isEmpty(instanceHealths)) {
      return emptyList();
    }
    List<String> instanceIds = instanceHealths.stream().map(ElastiGroupInstanceHealth::getInstanceId).collect(toList());
    return awsEc2HelperServiceDelegate.listEc2Instances(awsConfig, emptyList(), instanceIds, awsRegion, false);
  }

  @VisibleForTesting
  void createAndFinishEmptyExecutionLog(
      SpotInstTaskParameters taskParameters, String upScaleCommandUnit, String message) {
    ExecutionLogCallback logCallback;
    logCallback = getLogCallBack(taskParameters, upScaleCommandUnit);
    logCallback.saveExecutionLog(message, INFO, SUCCESS);
  }

  Map<String, Object> getJsonConfigMapFromElastigroupJson(String elastigroupJson) {
    Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
    Gson gson = new Gson();

    // Map<"group": {...entire config...}>, this is elastiGroupConfig json that spotinst exposes
    return gson.fromJson(elastigroupJson, mapType);
  }

  @VisibleForTesting
  void removeUnsupportedFieldsForCreatingNewGroup(Map<String, Object> elastiGroupConfigMap) {
    if (elastiGroupConfigMap.containsKey(ELASTI_GROUP_ID)) {
      elastiGroupConfigMap.remove(ELASTI_GROUP_ID);
    }

    if (elastiGroupConfigMap.containsKey(ELASTI_GROUP_CREATED_AT)) {
      elastiGroupConfigMap.remove(ELASTI_GROUP_CREATED_AT);
    }

    if (elastiGroupConfigMap.containsKey(ELASTI_GROUP_UPDATED_AT)) {
      elastiGroupConfigMap.remove(ELASTI_GROUP_UPDATED_AT);
    }
  }

  void updateName(Map<String, Object> elastiGroupConfigMap, String stageElastiGroupName) {
    elastiGroupConfigMap.put(NAME_CONFIG_ELEMENT, stageElastiGroupName);
  }

  void updateInitialCapacity(Map<String, Object> elastiGroupConfigMap) {
    Map<String, Object> capacityConfig = (Map<String, Object>) elastiGroupConfigMap.get(CAPACITY);

    capacityConfig.put(CAPACITY_MINIMUM_CONFIG_ELEMENT, 0);
    capacityConfig.put(CAPACITY_TARGET_CONFIG_ELEMENT, 0);
    capacityConfig.put(CAPACITY_MAXIMUM_CONFIG_ELEMENT, 0);

    if (!capacityConfig.containsKey(CAPACITY_UNIT_CONFIG_ELEMENT)) {
      capacityConfig.put(CAPACITY_UNIT_CONFIG_ELEMENT, UNIT_INSTANCE);
    }
  }

  protected abstract SpotInstTaskExecutionResponse executeTaskInternal(SpotInstTaskParameters spotInstTaskParameters,
      SpotInstConfig spotInstConfig, AwsConfig awsConfig) throws Exception;
}
