/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.elastigroup;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.elastigroup.ElastigroupSwapRouteResult.ElastigroupSwapRouteResultBuilder;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.spotinst.model.SpotInstConstants.STAGE_ELASTI_GROUP_NAME_SUFFIX;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotPermanentTokenConfigSpecDTO;
import io.harness.delegate.beans.elastigroup.ElastigroupSwapRouteResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.ElastigroupNGException;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.delegate.task.elastigroup.ElastigroupBGTaskHelper;
import io.harness.delegate.task.elastigroup.ElastigroupCommandTaskNGHelper;
import io.harness.delegate.task.elastigroup.request.AwsConnectedCloudProvider;
import io.harness.delegate.task.elastigroup.request.AwsLoadBalancerConfig;
import io.harness.delegate.task.elastigroup.request.ElastigroupCommandRequest;
import io.harness.delegate.task.elastigroup.request.ElastigroupSwapRouteCommandRequest;
import io.harness.delegate.task.elastigroup.response.ElastigroupCommandResponse;
import io.harness.delegate.task.elastigroup.response.ElastigroupSwapRouteResponse;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.elastigroup.ElastigroupCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.spotinst.SpotInstHelperServiceDelegate;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;
import io.harness.spotinst.model.ElastiGroupRenameRequest;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import java.util.ArrayList;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class ElastigroupSwapRouteCommandTaskHandler extends ElastigroupCommandTaskHandler {
  @Inject private ElastigroupCommandTaskNGHelper elastigroupCommandTaskNGHelper;
  @Inject protected SpotInstHelperServiceDelegate spotInstHelperServiceDelegate;
  @Inject private ElastigroupBGTaskHelper elastigroupDeployTaskHelper;
  @Inject protected TimeLimiter timeLimiter;
  private long timeoutInMillis;

  @Override
  protected ElastigroupCommandResponse executeTaskInternal(ElastigroupCommandRequest elastigroupCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress)
      throws ElastigroupNGException {
    if (!(elastigroupCommandRequest instanceof ElastigroupSwapRouteCommandRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("elastigroupCommandRequest", "Must be instance of ElastigroupSwapRouteCommandRequest"));
    }
    ElastigroupSwapRouteCommandRequest elastigroupSwapRouteCommandRequest =
        (ElastigroupSwapRouteCommandRequest) elastigroupCommandRequest;
    AwsConnectedCloudProvider connectedCloudProvider =
        (AwsConnectedCloudProvider) elastigroupSwapRouteCommandRequest.getConnectedCloudProvider();
    AwsLoadBalancerConfig loadBalancerConfig =
        (AwsLoadBalancerConfig) elastigroupSwapRouteCommandRequest.getLoadBalancerConfig();

    timeoutInMillis = elastigroupSwapRouteCommandRequest.getTimeoutIntervalInMin() * 60000;

    LogCallback deployLogCallback = elastigroupCommandTaskNGHelper.getLogCallback(iLogStreamingTaskClient,
        ElastigroupCommandUnitConstants.SWAP_TARGET_GROUP.toString(), true, commandUnitsProgress);
    try {
      ElastigroupSwapRouteResultBuilder resultBuilder = ElastigroupSwapRouteResult.builder();
      elastigroupCommandTaskNGHelper.decryptAwsCredentialDTO(
          elastigroupSwapRouteCommandRequest.getConnectorInfoDTO().getConnectorConfig(),
          elastigroupSwapRouteCommandRequest.getConnectorEncryptedDetails());
      AwsInternalConfig awsInternalConfig = elastigroupCommandTaskNGHelper.getAwsInternalConfig(
          (AwsConnectorDTO) elastigroupSwapRouteCommandRequest.getConnectorInfoDTO().getConnectorConfig(),
          connectedCloudProvider.getRegion());

      SpotInstConfig spotInstConfig = elastigroupSwapRouteCommandRequest.getSpotInstConfig();
      elastigroupCommandTaskNGHelper.decryptSpotInstConfig(spotInstConfig);
      SpotPermanentTokenConfigSpecDTO spotPermanentTokenConfigSpecDTO =
          (SpotPermanentTokenConfigSpecDTO) spotInstConfig.getSpotConnectorDTO().getCredential().getConfig();
      String spotInstAccountId = spotPermanentTokenConfigSpecDTO.getSpotAccountIdRef().getDecryptedValue() != null
          ? String.valueOf(spotPermanentTokenConfigSpecDTO.getSpotAccountIdRef().getDecryptedValue())
          : spotPermanentTokenConfigSpecDTO.getSpotAccountId();
      String spotInstApiToken = String.valueOf(spotPermanentTokenConfigSpecDTO.getApiTokenRef().getDecryptedValue());

      String prodElastigroupName = elastigroupSwapRouteCommandRequest.getElastigroupNamePrefix();
      ElastiGroup newElastigroup = elastigroupSwapRouteCommandRequest.getNewElastigroup();

      if (newElastigroup != null && isNotEmpty(newElastigroup.getId())) {
        deployLogCallback.saveExecutionLog(format("Sending request to rename Elastigroup with Id: [%s] to [%s]",
            newElastigroup.getId(), prodElastigroupName));
        spotInstHelperServiceDelegate.updateElastiGroup(spotInstApiToken, spotInstAccountId, newElastigroup.getId(),
            ElastiGroupRenameRequest.builder().name(prodElastigroupName).build());
        newElastigroup.setName(prodElastigroupName);
        resultBuilder.ec2InstanceIdsAdded(elastigroupCommandTaskNGHelper.getAllEc2InstanceIdsOfElastigroup(
            spotInstApiToken, spotInstAccountId, newElastigroup));
      }

      String stageElastigroupName = format(
          "%s__%s", elastigroupSwapRouteCommandRequest.getElastigroupNamePrefix(), STAGE_ELASTI_GROUP_NAME_SUFFIX);
      ElastiGroup oldElastigroup = elastigroupSwapRouteCommandRequest.getOldElastigroup();
      if (oldElastigroup != null && isNotEmpty(oldElastigroup.getId())) {
        deployLogCallback.saveExecutionLog(format("Sending request to rename Elastigroup with Id: [%s] to [%s]",
            oldElastigroup.getId(), stageElastigroupName));
        spotInstHelperServiceDelegate.updateElastiGroup(spotInstApiToken, spotInstAccountId, oldElastigroup.getId(),
            ElastiGroupRenameRequest.builder().name(stageElastigroupName).build());
        oldElastigroup.setName(stageElastigroupName);
      }

      String region = connectedCloudProvider.getRegion();

      deployLogCallback.saveExecutionLog(format("Updating Listener Rules for Load Balancer%s",
          loadBalancerConfig.getLoadBalancerDetails().size() > 1 ? "s" : ""));
      for (LoadBalancerDetailsForBGDeployment lb : loadBalancerConfig.getLoadBalancerDetails()) {
        deployLogCallback.saveExecutionLog(format("Load Balancer: %s", lb.getLoadBalancerName()));
        elastigroupCommandTaskNGHelper.swapTargetGroups(region, deployLogCallback, lb, awsInternalConfig);
      }
      deployLogCallback.saveExecutionLog("Route Updated Successfully", INFO, SUCCESS);

      boolean downsizeOldElastigroup;
      if ("true".equalsIgnoreCase(elastigroupSwapRouteCommandRequest.getDownsizeOldElastigroup())
          || "false".equalsIgnoreCase(elastigroupSwapRouteCommandRequest.getDownsizeOldElastigroup())) {
        downsizeOldElastigroup = Boolean.parseBoolean(elastigroupSwapRouteCommandRequest.getDownsizeOldElastigroup());
      } else {
        String errorMessage = format("Exception while parsing downsizeOldElastigroup option: [%s]. Error message: [%s]",
            elastigroupSwapRouteCommandRequest.getDownsizeOldElastigroup(), "Not a boolean value");
        deployLogCallback.saveExecutionLog(errorMessage, ERROR);
        throw new InvalidRequestException(errorMessage);
      }

      if (downsizeOldElastigroup && oldElastigroup != null && isNotEmpty(oldElastigroup.getId())) {
        ElastiGroup oldElastigroupWithCapacityZero = oldElastigroup.clone();
        oldElastigroupWithCapacityZero.setCapacity(
            ElastiGroupCapacity.builder().target(0).minimum(0).maximum(0).build());
        int steadyStateTimeOut =
            elastigroupDeployTaskHelper.getTimeOut(elastigroupSwapRouteCommandRequest.getTimeoutIntervalInMin());
        elastigroupDeployTaskHelper.scaleElastigroup(oldElastigroupWithCapacityZero, spotInstApiToken,
            spotInstAccountId, steadyStateTimeOut, iLogStreamingTaskClient,
            ElastigroupCommandUnitConstants.DOWNSCALE.toString(),
            ElastigroupCommandUnitConstants.DOWNSCALE_STEADY_STATE.toString(), commandUnitsProgress);
        resultBuilder.ec2InstanceIdsExisting(new ArrayList<>());
      } else {
        deployLogCallback = elastigroupCommandTaskNGHelper.getLogCallback(
            iLogStreamingTaskClient, ElastigroupCommandUnitConstants.DOWNSCALE.toString(), true, commandUnitsProgress);
        deployLogCallback.saveExecutionLog("Nothing to downsize.", INFO, SUCCESS);
        deployLogCallback = elastigroupCommandTaskNGHelper.getLogCallback(iLogStreamingTaskClient,
            ElastigroupCommandUnitConstants.DOWNSCALE_STEADY_STATE.toString(), true, commandUnitsProgress);
        deployLogCallback.saveExecutionLog("No downsize required", INFO, SUCCESS);
        resultBuilder.ec2InstanceIdsExisting(elastigroupCommandTaskNGHelper.getAllEc2InstanceIdsOfElastigroup(
            spotInstApiToken, spotInstAccountId, oldElastigroup));
      }

      deployLogCallback.saveExecutionLog(
          "Completed Swap Target Group for Spotinst", LogLevel.INFO, CommandExecutionStatus.SUCCESS);

      setElastigroupResult(resultBuilder, elastigroupSwapRouteCommandRequest);

      return ElastigroupSwapRouteResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .elastigroupSwapRouteResult(resultBuilder.build())
          .build();

    } catch (Exception ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      deployLogCallback.saveExecutionLog(color(format("Swap Routes Step Failed."), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new ElastigroupNGException(sanitizedException);
    }
  }

  private void setElastigroupResult(ElastigroupSwapRouteResultBuilder elastigroupSwapRouteResult,
      ElastigroupSwapRouteCommandRequest elastigroupSwapRouteCommandRequest) {
    elastigroupSwapRouteResult.downsizeOldElastiGroup(elastigroupSwapRouteCommandRequest.getDownsizeOldElastigroup())
        .lbDetails(((AwsLoadBalancerConfig) (elastigroupSwapRouteCommandRequest.getLoadBalancerConfig()))
                       .getLoadBalancerDetails());
    if (elastigroupSwapRouteCommandRequest.getOldElastigroup() != null) {
      elastigroupSwapRouteResult.oldElastiGroupId(elastigroupSwapRouteCommandRequest.getOldElastigroup().getId());
      elastigroupSwapRouteResult.oldElastiGroupName(elastigroupSwapRouteCommandRequest.getOldElastigroup().getName());
    }
    if (elastigroupSwapRouteCommandRequest.getNewElastigroup() != null) {
      elastigroupSwapRouteResult.newElastiGroupId(elastigroupSwapRouteCommandRequest.getNewElastigroup().getId());
      elastigroupSwapRouteResult.newElastiGroupName(elastigroupSwapRouteCommandRequest.getNewElastigroup().getName());
    }
  }
}
