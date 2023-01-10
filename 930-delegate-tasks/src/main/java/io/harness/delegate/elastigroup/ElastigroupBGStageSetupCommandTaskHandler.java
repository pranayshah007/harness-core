/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.elastigroup;

import static io.harness.delegate.task.elastigroup.ElastigroupCommandTaskNGHelper.getElastigroupString;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_MAX_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_MIN_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_TARGET_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.STAGE_ELASTI_GROUP_NAME_SUFFIX;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotPermanentTokenConfigSpecDTO;
import io.harness.delegate.beans.elastigroup.ElastigroupSetupResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.ElastigroupNGException;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.delegate.task.elastigroup.ElastigroupBGTaskHelper;
import io.harness.delegate.task.elastigroup.ElastigroupCommandTaskNGHelper;
import io.harness.delegate.task.elastigroup.request.AwsConnectedCloudProvider;
import io.harness.delegate.task.elastigroup.request.AwsLoadBalancerConfig;
import io.harness.delegate.task.elastigroup.request.ElastigroupCommandRequest;
import io.harness.delegate.task.elastigroup.request.ElastigroupSetupCommandRequest;
import io.harness.delegate.task.elastigroup.response.ElastigroupCommandResponse;
import io.harness.delegate.task.elastigroup.response.ElastigroupSetupResponse;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.elastigroup.ElastigroupCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.spotinst.SpotInstHelperServiceDelegate;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class ElastigroupBGStageSetupCommandTaskHandler extends ElastigroupCommandTaskHandler {
  @Inject private ElastigroupCommandTaskNGHelper elastigroupCommandTaskNGHelper;
  @Inject protected SpotInstHelperServiceDelegate spotInstHelperServiceDelegate;
  @Inject private ElastigroupBGTaskHelper elastigroupBGTaskHelper;
  private long timeoutInMillis;

  @Override
  protected ElastigroupCommandResponse executeTaskInternal(ElastigroupCommandRequest elastigroupCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress)
      throws ElastigroupNGException {
    if (!(elastigroupCommandRequest instanceof ElastigroupSetupCommandRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("elastigroupCommandRequest", "Must be instance of ElastigroupSetupCommandRequest"));
    }
    ElastigroupSetupCommandRequest elastigroupSetupCommandRequest =
        (ElastigroupSetupCommandRequest) elastigroupCommandRequest;
    ElastigroupSetupResult elastigroupSetupResult =
        ElastigroupSetupResult.builder()
            .elastigroupNamePrefix(elastigroupSetupCommandRequest.getElastigroupNamePrefix())
            .elastigroupOriginalConfig(elastigroupSetupCommandRequest.getGeneratedElastigroupConfig())
            .elastigroupNamePrefix(elastigroupSetupCommandRequest.getElastigroupNamePrefix())
            .isBlueGreen(elastigroupSetupCommandRequest.isBlueGreen())
            .useCurrentRunningInstanceCount(
                ((ElastigroupSetupCommandRequest) elastigroupCommandRequest).isUseCurrentRunningInstanceCount())
            .maxInstanceCount(elastigroupSetupCommandRequest.getMaxInstanceCount())
            .resizeStrategy(elastigroupSetupCommandRequest.getResizeStrategy())
            .build();

    timeoutInMillis = elastigroupSetupCommandRequest.getTimeoutIntervalInMin() * 60000;

    LogCallback deployLogCallback = elastigroupCommandTaskNGHelper.getLogCallback(iLogStreamingTaskClient,
        ElastigroupCommandUnitConstants.CREATE_ELASTIGROUP.toString(), false, commandUnitsProgress);
    try {
      elastigroupCommandTaskNGHelper.decryptAwsCredentialDTO(
          elastigroupSetupCommandRequest.getConnectorInfoDTO().getConnectorConfig(),
          elastigroupSetupCommandRequest.getConnectorEncryptedDetails());
      AwsInternalConfig awsInternalConfig = elastigroupCommandTaskNGHelper.getAwsInternalConfig(
          (AwsConnectorDTO) elastigroupSetupCommandRequest.getConnectorInfoDTO().getConnectorConfig(),
          ((AwsConnectedCloudProvider) elastigroupSetupCommandRequest.getConnectedCloudProvider()).getRegion());

      List<LoadBalancerDetailsForBGDeployment> lbDetailList =
          elastigroupCommandTaskNGHelper.fetchAllLoadBalancerDetails(
              elastigroupSetupCommandRequest, awsInternalConfig, deployLogCallback);

      // Generate STAGE elastigroup name
      String stageElastigroupName =
          format("%s__%s", elastigroupSetupCommandRequest.getElastigroupNamePrefix(), STAGE_ELASTI_GROUP_NAME_SUFFIX);

      // Update lbDetails with fetched details, as they have more data field in
      ((AwsLoadBalancerConfig) elastigroupSetupCommandRequest.getLoadBalancerConfig())
          .setLoadBalancerDetails(lbDetailList);
      elastigroupSetupResult.setLoadBalancerDetailsForBGDeployments(lbDetailList);

      // Generate final json by substituting name, capacity and LBConfig
      String finalJson =
          elastigroupCommandTaskNGHelper.generateFinalJson(elastigroupSetupCommandRequest, stageElastigroupName);

      SpotInstConfig spotInstConfig = elastigroupSetupCommandRequest.getSpotInstConfig();
      elastigroupCommandTaskNGHelper.decryptSpotInstConfig(spotInstConfig);
      SpotPermanentTokenConfigSpecDTO spotPermanentTokenConfigSpecDTO =
          (SpotPermanentTokenConfigSpecDTO) spotInstConfig.getSpotConnectorDTO().getCredential().getConfig();
      String spotInstAccountId = spotPermanentTokenConfigSpecDTO.getSpotAccountIdRef().getDecryptedValue() != null
          ? String.valueOf(spotPermanentTokenConfigSpecDTO.getSpotAccountIdRef().getDecryptedValue())
          : spotPermanentTokenConfigSpecDTO.getSpotAccountId();
      String spotInstApiToken = String.valueOf(spotPermanentTokenConfigSpecDTO.getApiTokenRef().getDecryptedValue());

      // Check if existing elastigroup with exists with same stage name
      deployLogCallback.saveExecutionLog(format("Querying to find Elastigroup with name: [%s]", stageElastigroupName));
      Optional<ElastiGroup> stageOptionalElastiGroup =
          spotInstHelperServiceDelegate.getElastiGroupByName(spotInstApiToken, spotInstAccountId, stageElastigroupName);
      ElastiGroup stageElastiGroup;
      if (stageOptionalElastiGroup.isPresent()) {
        stageElastiGroup = stageOptionalElastiGroup.get();
        deployLogCallback.saveExecutionLog(
            format("Found stage Elastigroup with id: [%s]. Deleting it. ", stageElastiGroup.getId()));
        spotInstHelperServiceDelegate.deleteElastiGroup(spotInstApiToken, spotInstAccountId, stageElastiGroup.getId());
      }

      // Create new elastigroup
      deployLogCallback.saveExecutionLog(
          format("Sending request to create new Elastigroup with name: [%s]", stageElastigroupName));
      stageElastiGroup =
          spotInstHelperServiceDelegate.createElastiGroup(spotInstApiToken, spotInstAccountId, finalJson);
      String stageElastiGroupId = stageElastiGroup.getId();
      deployLogCallback.saveExecutionLog(
          format("Created Elastigroup with name: [%s] and id: [%s]", stageElastigroupName, stageElastiGroupId));
      elastigroupSetupResult.setNewElastigroup(stageElastiGroup);

      // Prod ELasti Groups
      String prodElastiGroupName = elastigroupSetupCommandRequest.getElastigroupNamePrefix();
      deployLogCallback.saveExecutionLog(
          format("Querying Spotinst for Elastigroup with name: [%s]", prodElastiGroupName));
      Optional<ElastiGroup> prodOptionalElastiGroup =
          spotInstHelperServiceDelegate.getElastiGroupByName(spotInstApiToken, spotInstAccountId, prodElastiGroupName);
      List<ElastiGroup> prodElastiGroupList;
      if (prodOptionalElastiGroup.isPresent()) {
        ElastiGroup prodElastiGroup = prodOptionalElastiGroup.get();
        deployLogCallback.saveExecutionLog(
            format("Found existing Prod Elastigroup %s", getElastigroupString(prodElastiGroup)), LogLevel.INFO,
            CommandExecutionStatus.SUCCESS);
        prodElastiGroupList = singletonList(prodElastiGroup);
      } else {
        deployLogCallback.saveExecutionLog(
            format("Not able to find Prod Elastigroup with name: [%s]", prodElastiGroupName), LogLevel.INFO,
            CommandExecutionStatus.SUCCESS);
        prodElastiGroupList = emptyList();
      }
      elastigroupSetupResult.setGroupToBeDownsized(prodElastiGroupList);
      ElastiGroupCapacity elastigroupCapacity =
          elastigroupSetupCommandRequest.getGeneratedElastigroupConfig().getCapacity();
      int min = elastigroupCapacity.getMinimum();
      int max = elastigroupCapacity.getMaximum();
      int target = elastigroupCapacity.getTarget();
      if (elastigroupSetupCommandRequest.isUseCurrentRunningInstanceCount()) {
        min = DEFAULT_ELASTIGROUP_MIN_INSTANCES;
        max = DEFAULT_ELASTIGROUP_MAX_INSTANCES;
        target = DEFAULT_ELASTIGROUP_TARGET_INSTANCES;
        if (prodOptionalElastiGroup.isPresent()) {
          ElastiGroupCapacity capacity = prodOptionalElastiGroup.get().getCapacity();
          if (capacity != null) {
            min = capacity.getMinimum();
            max = capacity.getMaximum();
            target = capacity.getTarget();
          }
        }
      }
      elastigroupSetupResult.getNewElastigroup().getCapacity().setMaximum(max);
      elastigroupSetupResult.getNewElastigroup().getCapacity().setMinimum(min);
      elastigroupSetupResult.getNewElastigroup().getCapacity().setTarget(target);

      elastigroupBGTaskHelper.scaleElastigroup(stageElastiGroup, spotInstApiToken, spotInstAccountId,
          elastigroupBGTaskHelper.getTimeOut(elastigroupSetupCommandRequest.getTimeoutIntervalInMin()),
          iLogStreamingTaskClient, ElastigroupCommandUnitConstants.UPSCALE.toString(),
          ElastigroupCommandUnitConstants.UPSCALE_STEADY_STATE.toString(), commandUnitsProgress);

      List<String> newEc2Instances = elastigroupCommandTaskNGHelper.getAllEc2InstanceIdsOfElastigroup(
          spotInstApiToken, spotInstAccountId, stageElastiGroup);
      List<String> existingEc2Instances = new ArrayList<>();
      if (!prodElastiGroupList.isEmpty()) {
        existingEc2Instances = elastigroupCommandTaskNGHelper.getAllEc2InstanceIdsOfElastigroup(
            spotInstApiToken, spotInstAccountId, prodElastiGroupList.get(0));
      }
      elastigroupSetupResult.setEc2InstanceIdsAdded(newEc2Instances);
      elastigroupSetupResult.setEc2InstanceIdsExisting(existingEc2Instances);

      return ElastigroupSetupResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .elastigroupSetupResult(elastigroupSetupResult)
          .build();

    } catch (Exception ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      return ElastigroupSetupResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .elastigroupSetupResult(elastigroupSetupResult)
          .errorMessage(sanitizedException.getMessage())
          .build();
    }
  }
}
