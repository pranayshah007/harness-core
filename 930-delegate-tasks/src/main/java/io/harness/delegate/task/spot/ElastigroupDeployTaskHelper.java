/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.spot;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_MAX_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_MIN_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_TARGET_INSTANCES;
import static io.harness.threading.Morpheus.sleep;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.concurrent.HTimeLimiter;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.spotinst.SpotInstHelperServiceDelegate;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;
import io.harness.spotinst.model.ElastiGroupInstanceHealth;

import com.google.common.util.concurrent.ExecutionError;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class ElastigroupDeployTaskHelper {
  @Inject private SpotInstHelperServiceDelegate spotInstHelperServiceDelegate;
  @Inject private TimeLimiter timeLimiter;

  public void scaleElastigroup(ElastiGroup elastiGroup, String spotInstToken, String spotInstAccountId,
      int steadyStateTimeOut, ILogStreamingTaskClient logStreamingTaskClient, String scaleCommandUnit,
      String waitCommandUnit, CommandUnitsProgress commandUnitsProgress) throws Exception {
    final LogCallback scaleLogCallback = getLogCallback(logStreamingTaskClient, scaleCommandUnit, commandUnitsProgress);
    final LogCallback waitLogCallback = getLogCallback(logStreamingTaskClient, waitCommandUnit, commandUnitsProgress);

    if (elastiGroup == null) {
      scaleLogCallback.saveExecutionLog(
          "No Elastigroup eligible for scaling", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      waitLogCallback.saveExecutionLog(
          "No Elastigroup eligible for scaling", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      return;
    }

    updateElastigroup(spotInstToken, spotInstAccountId, elastiGroup, scaleLogCallback);
    waitForSteadyState(elastiGroup, spotInstAccountId, spotInstToken, steadyStateTimeOut, waitLogCallback);
  }

  private LogCallback getLogCallback(ILogStreamingTaskClient logStreamingTaskClient, String commandUnitName,
      CommandUnitsProgress commandUnitsProgress) {
    return new NGDelegateLogCallback(logStreamingTaskClient, commandUnitName, true, commandUnitsProgress);
  }

  private void updateElastigroup(String spotInstToken, String spotInstAccountId, ElastiGroup elastiGroup,
      LogCallback logCallback) throws Exception {
    Optional<ElastiGroup> elastigroupInitialOptional =
        spotInstHelperServiceDelegate.getElastiGroupById(spotInstToken, spotInstAccountId, elastiGroup.getId());

    if (!elastigroupInitialOptional.isPresent()) {
      String message = format("Did not find Elastigroup with id: [%s]", elastiGroup.getId());
      log.error(message);
      logCallback.saveExecutionLog(message, ERROR, CommandExecutionStatus.FAILURE);
      throw new InvalidRequestException(message);
    }

    ElastiGroup elastigroupInitial = elastigroupInitialOptional.get();
    logCallback.saveExecutionLog(format(
        "Current state of Elastigroup: [%s], min: [%d], max: [%d], desired: [%d], Id: [%s]", elastigroupInitial.getId(),
        elastigroupInitial.getCapacity().getMinimum(), elastigroupInitial.getCapacity().getMaximum(),
        elastigroupInitial.getCapacity().getTarget(), elastigroupInitial.getId()));

    checkAndUpdateElastigroup(elastiGroup, logCallback);

    logCallback.saveExecutionLog(
        format("Sending request to Spotinst to update Elastigroup: [%s] with min: [%d], max: [%d] and target: [%d]",
            elastiGroup.getId(), elastiGroup.getCapacity().getMinimum(), elastiGroup.getCapacity().getMaximum(),
            elastiGroup.getCapacity().getTarget()));

    spotInstHelperServiceDelegate.updateElastiGroupCapacity(
        spotInstToken, spotInstAccountId, elastiGroup.getId(), elastiGroup);

    logCallback.saveExecutionLog("Request Sent to update Elastigroup", INFO, SUCCESS);
  }

  private void waitForSteadyState(ElastiGroup elastiGroup, String spotInstAccountId, String spotInstToken,
      int steadyStateTimeOut, LogCallback lLogCallback) {
    lLogCallback.saveExecutionLog(format("Waiting for Elastigroup: [%s] to reach steady state", elastiGroup.getId()));
    try {
      HTimeLimiter.callInterruptible(timeLimiter, Duration.ofMinutes(steadyStateTimeOut), () -> {
        while (true) {
          if (allInstancesHealthy(spotInstToken, spotInstAccountId, elastiGroup.getId(), lLogCallback,
                  elastiGroup.getCapacity().getTarget())) {
            return true;
          }
          sleep(ofSeconds(20));
        }
      });
    } catch (ExecutionException | UncheckedExecutionException | ExecutionError e) {
      String errorMessage =
          format("Exception while waiting for steady state for Elastigroup: [%s]. Error message: [%s]",
              elastiGroup.getId(), e.getMessage());
      lLogCallback.saveExecutionLog(errorMessage, ERROR);
      throw new InvalidRequestException(errorMessage, e.getCause());
    } catch (TimeoutException | InterruptedException e) {
      String errorMessage =
          format("Timed out while waiting for steady state for Elastigroup: [%s]", elastiGroup.getId());
      lLogCallback.saveExecutionLog(errorMessage, ERROR);
      throw new InvalidRequestException(errorMessage, e);
    } catch (Exception e) {
      String errorMessage =
          format("Exception while waiting for steady state for Elastigroup: [%s]. Error message: [%s]",
              elastiGroup.getId(), e.getMessage());
      lLogCallback.saveExecutionLog(errorMessage, ERROR);
      throw new InvalidRequestException(errorMessage, e);
    }
  }

  boolean allInstancesHealthy(String spotInstToken, String spotInstAccountId, String elastigroupId,
      LogCallback logCallback, int targetInstances) throws Exception {
    List<ElastiGroupInstanceHealth> instanceHealths =
        spotInstHelperServiceDelegate.listElastiGroupInstancesHealth(spotInstToken, spotInstAccountId, elastigroupId);
    int currentTotalCount = isEmpty(instanceHealths) ? 0 : instanceHealths.size();
    int currentHealthyCount = isEmpty(instanceHealths)
        ? 0
        : (int) instanceHealths.stream().filter(health -> "HEALTHY".equals(health.getHealthStatus())).count();
    if (targetInstances == 0) {
      if (currentTotalCount == 0) {
        logCallback.saveExecutionLog(
            format("Elastigroup: [%s] does not have any instances.", elastigroupId), INFO, SUCCESS);
        return true;
      } else {
        logCallback.saveExecutionLog(format("Elastigroup: [%s] still has [%d] total and [%d] healthy instances",
            elastigroupId, currentTotalCount, currentHealthyCount));
      }
    } else {
      logCallback.saveExecutionLog(
          format("Desired instances: [%d], Total instances: [%d], Healthy instances: [%d] for Elastigroup: [%s]",
              targetInstances, currentTotalCount, currentHealthyCount, elastigroupId));
      if (targetInstances == currentHealthyCount && targetInstances == currentTotalCount) {
        logCallback.saveExecutionLog(format("Elastigroup: [%s] reached steady state", elastigroupId), INFO, SUCCESS);
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if condition 0 <= min <= target <= max is followed.
   * If it fails:
   *  if target < 0, we update with default values
   *  else update min and/or max to target individually.
   * @param elastigroup
   * @param logCallback
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
      logCallback.saveExecutionLog(format("Modifying invalid request to Spotinst to update Elastigroup:[%s] "
              + "Original min: [%d], max: [%d] and target: [%d], Modified min: [%d], max: [%d] and target: [%d] ",
          elastigroup.getId(), min, max, target, capacity.getMinimum(), capacity.getMaximum(), capacity.getTarget()));
    }
  }
}
