/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.elastigroup;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ExecutionError;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.inject.Inject;
import freemarker.core.NonBooleanException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.concurrent.HTimeLimiter;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotPermanentTokenConfigSpecDTO;
import io.harness.delegate.beans.elastigroup.ElastigroupSetupResult;
import io.harness.delegate.beans.elastigroup.ElastigroupSwapRouteResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.exception.ElastigroupNGException;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.delegate.task.elastigroup.ElastigroupCommandTaskNGHelper;
import io.harness.delegate.task.elastigroup.request.ElastigroupCommandRequest;
import io.harness.delegate.task.elastigroup.request.ElastigroupSetupCommandRequest;
import io.harness.delegate.task.elastigroup.request.ElastigroupSwapRouteCommandRequest;
import io.harness.delegate.task.elastigroup.response.ElastigroupCommandResponse;
import io.harness.delegate.task.elastigroup.response.ElastigroupSetupResponse;
import io.harness.delegate.task.elastigroup.response.ElastigroupSwapRouteResponse;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.elastigroup.ElastigroupCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.TimeoutException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.spotinst.SpotInstHelperServiceDelegate;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;
import io.harness.spotinst.model.ElastiGroupInstanceHealth;
import io.harness.spotinst.model.ElastiGroupRenameRequest;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import static io.harness.threading.Morpheus.sleep;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_MAX_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_MIN_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_TARGET_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DOWN_SCALE_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.STAGE_ELASTI_GROUP_NAME_SUFFIX;
import static io.harness.spotinst.model.SpotInstConstants.defaultSteadyStateTimeout;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static software.wings.beans.LogHelper.color;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class ElastigroupSwapRouteCommandTaskHandler extends ElastigroupCommandTaskNGHandler {
  @Inject private ElastigroupCommandTaskNGHelper elastigroupCommandTaskNGHelper;
  @Inject protected SpotInstHelperServiceDelegate spotInstHelperServiceDelegate;
  @Inject protected TimeLimiter timeLimiter;
  private long timeoutInMillis;

  @Override
  protected ElastigroupCommandResponse executeTaskInternal(ElastigroupCommandRequest elastigroupCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(elastigroupCommandRequest instanceof ElastigroupSwapRouteCommandRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("elastigroupCommandRequest", "Must be instance of ElastigroupSwapRouteCommandRequest"));
    }
    ElastigroupSwapRouteCommandRequest elastigroupSwapRouteCommandRequest =
        (ElastigroupSwapRouteCommandRequest) elastigroupCommandRequest;

    timeoutInMillis = elastigroupSwapRouteCommandRequest.getTimeoutIntervalInMin() * 60000;

    LogCallback deployLogCallback = elastigroupCommandTaskNGHelper.getLogCallback(
        iLogStreamingTaskClient, ElastigroupCommandUnitConstants.createSetup.toString(), true, commandUnitsProgress);
    try {
      elastigroupCommandTaskNGHelper.decryptAwsCredentialDTO(
              elastigroupSwapRouteCommandRequest.getConnectorInfoDTO().getConnectorConfig(),
          ((ElastigroupSetupCommandRequest) elastigroupCommandRequest).getAwsEncryptedDetails());
      AwsInternalConfig awsInternalConfig = elastigroupCommandTaskNGHelper.getAwsInternalConfig(
          (AwsConnectorDTO) elastigroupSwapRouteCommandRequest.getConnectorInfoDTO().getConnectorConfig(),
          ((ElastigroupSetupCommandRequest) elastigroupCommandRequest).getAwsRegion());


      SpotInstConfig spotInstConfig = elastigroupSwapRouteCommandRequest.getSpotInstConfig();
      elastigroupCommandTaskNGHelper.decryptSpotInstConfig(spotInstConfig);
      SpotPermanentTokenConfigSpecDTO spotPermanentTokenConfigSpecDTO =
              (SpotPermanentTokenConfigSpecDTO) spotInstConfig.getSpotConnectorDTO().getCredential().getConfig();
      String spotInstAccountId = spotPermanentTokenConfigSpecDTO.getSpotAccountIdRef().getDecryptedValue() != null
              ? spotPermanentTokenConfigSpecDTO.getSpotAccountIdRef().getDecryptedValue().toString()
              : spotPermanentTokenConfigSpecDTO.getSpotAccountId();
      String spotInstApiTokenRef = new String(spotPermanentTokenConfigSpecDTO.getApiTokenRef().getDecryptedValue());


      String prodElastiGroupName = elastigroupSwapRouteCommandRequest.getElastigroupNamePrefix();
      String stageElastiGroupName =
              format("%s__%s", elastigroupSwapRouteCommandRequest.getElastigroupNamePrefix(), STAGE_ELASTI_GROUP_NAME_SUFFIX);
      ElastiGroup newElastiGroup = elastigroupSwapRouteCommandRequest.getNewElastigroup();
      String newElastiGroupId = (newElastiGroup != null) ? newElastiGroup.getId() : EMPTY;
      ElastiGroup oldElastiGroup = elastigroupSwapRouteCommandRequest.getOldElastigroup();
      String oldElastiGroupId = (oldElastiGroup != null) ? oldElastiGroup.getId() : EMPTY;

      if (isNotEmpty(newElastiGroupId)) {
        deployLogCallback.saveExecutionLog(
                format("Sending request to rename Elastigroup with Id: [%s] to [%s]", newElastiGroupId, prodElastiGroupName));
        spotInstHelperServiceDelegate.updateElastiGroup(spotInstApiTokenRef, spotInstAccountId, newElastiGroupId,
                ElastiGroupRenameRequest.builder().name(prodElastiGroupName).build());
      }

      if (isNotEmpty(oldElastiGroupId)) {
        deployLogCallback.saveExecutionLog(
                format("Sending request to rename Elastigroup with Id: [%s] to [%s]", oldElastiGroup, stageElastiGroupName));
        spotInstHelperServiceDelegate.updateElastiGroup(spotInstApiTokenRef, spotInstAccountId, oldElastiGroupId,
                ElastiGroupRenameRequest.builder().name(stageElastiGroupName).build());
      }

      String region = elastigroupSwapRouteCommandRequest.getAwsRegion();

      deployLogCallback.saveExecutionLog("Updating Listener Rules for Load Balancer");
      for(LoadBalancerDetailsForBGDeployment loadBalancerDetailsForBGDeployment: elastigroupSwapRouteCommandRequest.getLBdetailsForBGDeploymentList()) {
        elastigroupCommandTaskNGHelper.swapTargetGroups(region, deployLogCallback, loadBalancerDetailsForBGDeployment, awsInternalConfig);
      }
      deployLogCallback.saveExecutionLog("Route Updated Successfully", INFO, SUCCESS);

      Boolean downsizeOldElastigroup;
      if("true".equalsIgnoreCase(elastigroupSwapRouteCommandRequest.getDownsizeOldElastigroup()) || "false".equalsIgnoreCase(elastigroupSwapRouteCommandRequest.getDownsizeOldElastigroup())) {
        downsizeOldElastigroup = Boolean.parseBoolean(elastigroupSwapRouteCommandRequest.getDownsizeOldElastigroup());
      } else {
        String errorMessage =
                format("Exception while parsing downsizeOldElastigroup option: [%s]. Error message: [%s]",
                        elastigroupSwapRouteCommandRequest.getDownsizeOldElastigroup(), "Not a boolean value");
        deployLogCallback.saveExecutionLog(errorMessage, ERROR);
        throw new InvalidRequestException(errorMessage);
      }

      if (downsizeOldElastigroup && isNotEmpty(elastigroupSwapRouteCommandRequest.getOldElastigroup().getId())) {
        ElastiGroup temp = ElastiGroup.builder()
                .id(oldElastiGroupId)
                .name(stageElastiGroupName)
                .capacity(ElastiGroupCapacity.builder().minimum(0).maximum(0).target(0).build())
                .build();
        int steadyStateTimeOut = getTimeOut(elastigroupSwapRouteCommandRequest.getSteadyStateTimeOut());
        scaleElastigroup(temp, spotInstApiTokenRef, spotInstAccountId, steadyStateTimeOut, iLogStreamingTaskClient,
                DOWN_SCALE_COMMAND_UNIT, DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT, commandUnitsProgress);
      } else {
        deployLogCallback = elastigroupCommandTaskNGHelper.getLogCallback(iLogStreamingTaskClient, DOWN_SCALE_COMMAND_UNIT, true, commandUnitsProgress);
        deployLogCallback.saveExecutionLog("Nothing to Downsize.", INFO, SUCCESS);
        deployLogCallback = elastigroupCommandTaskNGHelper.getLogCallback(iLogStreamingTaskClient, DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT, true, commandUnitsProgress);
        deployLogCallback.saveExecutionLog("No Downsize was required, Swap Route Successfully Completed", INFO, SUCCESS);
      }

      //---------------------

      deployLogCallback.saveExecutionLog(
          color(format("Completed Swap Routes Step for Spotinst"), LogColor.Green, LogWeight.Bold), LogLevel.INFO,
          CommandExecutionStatus.SUCCESS);

      ElastigroupSwapRouteResult.ElastigroupSwapRouteResultBuilder elastigroupSwapRouteResult = ElastigroupSwapRouteResult.builder();
      elastigroupSwapRouteResult.downsizeOldElastiGroup(elastigroupSwapRouteCommandRequest.getDownsizeOldElastigroup())
                      .lbDetails(elastigroupSwapRouteCommandRequest.getLBdetailsForBGDeploymentList());
      if(elastigroupSwapRouteCommandRequest.getOldElastigroup() != null) {
        elastigroupSwapRouteResult.oldElastiGroupId(elastigroupSwapRouteCommandRequest.getOldElastigroup().getId());
        elastigroupSwapRouteResult.oldElastiGroupName(elastigroupSwapRouteCommandRequest.getOldElastigroup().getName());
      }
      if(elastigroupSwapRouteCommandRequest.getNewElastigroup() != null) {
        elastigroupSwapRouteResult.newElastiGroupId(elastigroupSwapRouteCommandRequest.getNewElastigroup().getId());
        elastigroupSwapRouteResult.newElastiGroupName(elastigroupSwapRouteCommandRequest.getNewElastigroup().getName());
      }

      return ElastigroupSwapRouteResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .elastigroupSwapRouteResult(elastigroupSwapRouteResult.build())
          .build();

    } catch (Exception ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      deployLogCallback.saveExecutionLog(color(format("Swap Routes Step Failed."), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new ElastigroupNGException(sanitizedException);
    }
  }

  @VisibleForTesting
  int getTimeOut(int timeOut) {
    return (timeOut > 0) ? timeOut : defaultSteadyStateTimeout;
  }

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

  public List<String> getAllEc2InstanceIdsOfElastigroup(
          String spotInstToken, String spotInstAccountId, ElastiGroup elastigroup) throws Exception {
    if (elastigroup == null) {
      return emptyList();
    }

    final List<ElastiGroupInstanceHealth> elastigroupInstanceHealths =
            spotInstHelperServiceDelegate.listElastiGroupInstancesHealth(
                    spotInstToken, spotInstAccountId, elastigroup.getId());

    if (isEmpty(elastigroupInstanceHealths)) {
      return emptyList();
    }

    return elastigroupInstanceHealths.stream().map(ElastiGroupInstanceHealth::getInstanceId).collect(Collectors.toList());
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
      String message = format("Did not find Elastigroup: [%s], Id: [%s]", elastiGroup.getName(), elastiGroup.getId());
      log.error(message);
      logCallback.saveExecutionLog(message, ERROR, CommandExecutionStatus.FAILURE);
      throw new InvalidRequestException(message);
    }

    ElastiGroup elastigroupInitial = elastigroupInitialOptional.get();
    logCallback.saveExecutionLog(
            format("Current state of Elastigroup: [%s], Id: [%s], min: [%d], max: [%d], desired: [%d]",
                    elastigroupInitial.getName(), elastigroupInitial.getId(), elastigroupInitial.getCapacity().getMinimum(),
                    elastigroupInitial.getCapacity().getMaximum(), elastigroupInitial.getCapacity().getTarget()));

    checkAndUpdateElastigroup(elastiGroup, logCallback);

    logCallback.saveExecutionLog(format(
            "Sending request to Spotinst to update Elastigroup: [%s], Id: [%s] with min: [%d], max: [%d] and target: [%d]",
            elastiGroup.getName(), elastiGroup.getId(), elastiGroup.getCapacity().getMinimum(),
            elastiGroup.getCapacity().getMaximum(), elastiGroup.getCapacity().getTarget()));

    spotInstHelperServiceDelegate.updateElastiGroupCapacity(
            spotInstToken, spotInstAccountId, elastiGroup.getId(), elastiGroup);

    logCallback.saveExecutionLog("Request Sent to update Elastigroup", INFO, SUCCESS);
  }

  private void waitForSteadyState(ElastiGroup elastiGroup, String spotInstAccountId, String spotInstToken,
                                  int steadyStateTimeOut, LogCallback lLogCallback) {
    lLogCallback.saveExecutionLog(format(
            "Waiting for Elastigroup: [%s], Id: [%s] to reach steady state", elastiGroup.getName(), elastiGroup.getId()));
    try {
      HTimeLimiter.callInterruptible(timeLimiter, Duration.ofMinutes(steadyStateTimeOut), () -> {
        while (true) {
          if (allInstancesHealthy(
                  spotInstToken, spotInstAccountId, elastiGroup, lLogCallback, elastiGroup.getCapacity().getTarget())) {
            return true;
          }
          sleep(Duration.ofSeconds(20));
        }
      });
    } catch (ExecutionException | UncheckedExecutionException | ExecutionError e) {
      String errorMessage =
              format("Exception while waiting for steady state for Elastigroup: [%s], Id: [%s]. Error message: [%s]",
                      elastiGroup.getName(), elastiGroup.getId(), e.getMessage());
      lLogCallback.saveExecutionLog(errorMessage, ERROR);
      throw new InvalidRequestException(errorMessage, e.getCause());
    } catch (TimeoutException | InterruptedException e) {
      String errorMessage = format("Timed out while waiting for steady state for Elastigroup: [%s], Id: [%s]",
              elastiGroup.getName(), elastiGroup.getId());
      lLogCallback.saveExecutionLog(errorMessage, ERROR);
      throw new InvalidRequestException(errorMessage, e);
    } catch (Exception e) {
      String errorMessage =
              format("Exception while waiting for steady state for Elastigroup: [%s], Id: [%s]. Error message: [%s]",
                      elastiGroup.getName(), elastiGroup.getId(), e.getMessage());
      lLogCallback.saveExecutionLog(errorMessage, ERROR);
      throw new InvalidRequestException(errorMessage, e);
    }
  }

  boolean allInstancesHealthy(String spotInstToken, String spotInstAccountId, ElastiGroup elastigroup,
                              LogCallback logCallback, int targetInstances) throws Exception {
    List<ElastiGroupInstanceHealth> instanceHealths = spotInstHelperServiceDelegate.listElastiGroupInstancesHealth(
            spotInstToken, spotInstAccountId, elastigroup.getId());
    int currentTotalCount = isEmpty(instanceHealths) ? 0 : instanceHealths.size();
    int currentHealthyCount = isEmpty(instanceHealths)
            ? 0
            : (int) instanceHealths.stream().filter(health -> "HEALTHY".equals(health.getHealthStatus())).count();
    if (targetInstances == 0) {
      if (currentTotalCount == 0) {
        logCallback.saveExecutionLog(format("Elastigroup: [%s], Id: [%s] does not have any instances.",
                        elastigroup.getName(), elastigroup.getId()),
                INFO, SUCCESS);
        return true;
      } else {
        logCallback.saveExecutionLog(
                format("Elastigroup: [%s], Id: [%s] still has [%d] total and [%d] healthy instances", elastigroup.getName(),
                        elastigroup.getId(), currentTotalCount, currentHealthyCount));
      }
    } else {
      logCallback.saveExecutionLog(format(
              "Desired instances: [%d], Total instances: [%d], Healthy instances: [%d] for Elastigroup: [%s], Id: [%s]",
              targetInstances, currentTotalCount, currentHealthyCount, elastigroup.getName(), elastigroup.getId()));
      if (targetInstances == currentHealthyCount && targetInstances == currentTotalCount) {
        logCallback.saveExecutionLog(
                format("Elastigroup: [%s], Id: [%s] reached steady state", elastigroup.getName(), elastigroup.getId()),
                INFO, SUCCESS);
        return true;
      }
    }
    return false;
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
      logCallback.saveExecutionLog(format("Modifying invalid request to Spotinst to update Elastigroup: [%s], Id: [%s] "
                      + "Original min: [%d], max: [%d] and target: [%d], Modified min: [%d], max: [%d] and target: [%d] ",
              elastigroup.getName(), elastigroup.getId(), min, max, target, capacity.getMinimum(), capacity.getMaximum(),
              capacity.getTarget()));
    }
  }
}
