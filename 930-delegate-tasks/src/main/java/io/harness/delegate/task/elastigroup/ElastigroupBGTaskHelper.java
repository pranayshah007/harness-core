/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.elastigroup;

import static io.harness.delegate.task.elastigroup.ElastigroupCommandTaskNGHelper.getElastigroupString;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_MAX_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_MIN_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_TARGET_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.defaultSteadyStateTimeout;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
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

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class ElastigroupBGTaskHelper {
  @Inject private SpotInstHelperServiceDelegate spotInstHelperServiceDelegate;
  @Inject private ElastigroupCommandTaskNGHelper elastigroupCommandTaskNGHelper;

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
    elastigroupCommandTaskNGHelper.waitForSteadyState(
        elastiGroup, spotInstAccountId, spotInstToken, steadyStateTimeOut, waitLogCallback);
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

      if (elastigroupInitialOptional.isEmpty()) {
        String message = format("Did not find Elastigroup: %s", getElastigroupString(elastiGroup));
        log.error(message);
        logCallback.saveExecutionLog(message, ERROR, CommandExecutionStatus.FAILURE);
        throw new InvalidRequestException(message);
      }

      ElastiGroup elastigroupInitial = elastigroupInitialOptional.get();
      logCallback.saveExecutionLog(format("Current state of Elastigroup: %s, min: [%d], max: [%d], desired: [%d]",
          getElastigroupString(elastigroupInitial), elastigroupInitial.getCapacity().getMinimum(),
          elastigroupInitial.getCapacity().getMaximum(), elastigroupInitial.getCapacity().getTarget()));

      checkAndUpdateElastigroup(elastiGroup, logCallback);

      logCallback.saveExecutionLog(
          format("Sending request to Spotinst to update Elastigroup: %s with min: [%d], max: [%d] and target: [%d]",
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

  @VisibleForTesting
  public int getTimeOut(int timeOut) {
    return (timeOut > 0) ? timeOut : defaultSteadyStateTimeout;
  }
}