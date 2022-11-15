/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.elastigroup;

import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.delegate.beans.connector.spotconnector.SpotPermanentTokenConfigSpecDTO;
import io.harness.delegate.beans.elastigroup.ElastigroupSetupResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.ElastigroupNGException;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.delegate.task.elastigroup.ElastigroupCommandTaskNGHelper;
import io.harness.delegate.task.elastigroup.request.ElastigroupBGStageSetupCommandRequest;
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
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import java.util.List;
import java.util.Optional;

import static com.google.api.client.util.Lists.newArrayList;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.spotinst.model.SpotInstConstants.STAGE_ELASTI_GROUP_NAME_SUFFIX;
import static io.harness.spotinst.model.SpotInstConstants.elastiGroupsToKeep;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static software.wings.beans.LogHelper.color;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class ElastigroupBGStageSetupCommandTaskHandler extends ElastigroupCommandTaskNGHandler {
  @Inject private ElastigroupCommandTaskNGHelper elastigroupCommandTaskNGHelper;
  @Inject protected SpotInstHelperServiceDelegate spotInstHelperServiceDelegate;
  private long timeoutInMillis;

  @Override
  protected ElastigroupCommandResponse executeTaskInternal(ElastigroupCommandRequest elastigroupCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(elastigroupCommandRequest instanceof ElastigroupSetupCommandRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("elastigroupCommandRequest", "Must be instance of ElastigroupSetupCommandRequest"));
    }
    ElastigroupBGStageSetupCommandRequest elastigroupBGStageSetupCommandRequest =
        (ElastigroupBGStageSetupCommandRequest) elastigroupCommandRequest;

    timeoutInMillis = elastigroupBGStageSetupCommandRequest.getTimeoutIntervalInMin() * 60000;

    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();

    LogCallback deployLogCallback = elastigroupCommandTaskNGHelper.getLogCallback(
        iLogStreamingTaskClient, ElastigroupCommandUnitConstants.createSetup.toString(), true, commandUnitsProgress);
    try {

      List<LoadBalancerDetailsForBGDeployment> lbDetailList =
              elastigroupCommandTaskNGHelper.fetchAllLoadBalancerDetails(elastigroupBGStageSetupCommandRequest, awsInternalConfig, deployLogCallback);

      // Generate STAGE elastiGroup name
      String stageElastiGroupName =
              format("%s__%s", elastigroupBGStageSetupCommandRequest.getElastigroupNamePrefix(), STAGE_ELASTI_GROUP_NAME_SUFFIX);

      // Generate final json by substituting name, capacity and LBConfig
      String finalJson = elastigroupCommandTaskNGHelper.generateFinalJson(elastigroupBGStageSetupCommandRequest, stageElastiGroupName);
//      // Update lbDetails with fetched details, as they have more data field in
//      setupTaskParameters.setAwsLoadBalancerConfigs(lbDetailList);

      SpotInstConfig spotInstConfig = elastigroupBGStageSetupCommandRequest.getSpotInstConfig();
      elastigroupCommandTaskNGHelper.decryptSpotInstConfig(spotInstConfig);
      SpotPermanentTokenConfigSpecDTO spotPermanentTokenConfigSpecDTO =
              (SpotPermanentTokenConfigSpecDTO) spotInstConfig.getSpotConnectorDTO().getCredential().getConfig();
      String spotInstAccountId = spotPermanentTokenConfigSpecDTO.getSpotAccountIdRef().getDecryptedValue() != null
              ? spotPermanentTokenConfigSpecDTO.getSpotAccountIdRef().getDecryptedValue().toString()
              : spotPermanentTokenConfigSpecDTO.getSpotAccountId();
      String spotInstApiTokenRef = new String(spotPermanentTokenConfigSpecDTO.getApiTokenRef().getDecryptedValue());

      // Check if existing elastigroup with exists with same stage name
      deployLogCallback.saveExecutionLog(format("Querying to find Elastigroup with name: [%s]", stageElastiGroupName));
      Optional<ElastiGroup> stageOptionalElastiGroup =
              spotInstHelperServiceDelegate.getElastiGroupByName(spotInstApiTokenRef, spotInstAccountId, stageElastiGroupName);
      ElastiGroup stageElastiGroup;
      if (stageOptionalElastiGroup.isPresent()) {
        stageElastiGroup = stageOptionalElastiGroup.get();
        deployLogCallback.saveExecutionLog(
                format("Found stage Elastigroup with id: [%s]. Deleting it. ", stageElastiGroup.getId()));
        spotInstHelperServiceDelegate.deleteElastiGroup(spotInstApiTokenRef, spotInstAccountId, stageElastiGroup.getId());
      }

      // Create new elastiGroup
      deployLogCallback.saveExecutionLog(
              format("Sending request to create new Elastigroup with name: [%s]", stageElastiGroupName));
      stageElastiGroup = spotInstHelperServiceDelegate.createElastiGroup(spotInstApiTokenRef, spotInstAccountId, finalJson);
      String stageElastiGroupId = stageElastiGroup.getId();
      deployLogCallback.saveExecutionLog(
              format("Created Elastigroup with name: [%s] and id: [%s]", stageElastiGroupName, stageElastiGroupId));
      builder.newElastiGroup(stageElastiGroup);

      // Prod ELasti Groups
      String prodElastiGroupName = elastigroupBGStageSetupCommandRequest.getElastigroupNamePrefix();
      deployLogCallback.saveExecutionLog(format("Querying Spotinst for Elastigroup with name: [%s]", prodElastiGroupName));
      Optional<ElastiGroup> prodOptionalElastiGroup =
              spotInstHelperServiceDelegate.getElastiGroupByName(spotInstApiTokenRef, spotInstAccountId, prodElastiGroupName);
      List<ElastiGroup> prodElastiGroupList;
      if (prodOptionalElastiGroup.isPresent()) {
        ElastiGroup prodElastiGroup = prodOptionalElastiGroup.get();
        deployLogCallback.saveExecutionLog(format("Found existing Prod Elastigroup with name: [%s] and id: [%s]",
                prodElastiGroup.getName(), prodElastiGroup.getId()));
        prodElastiGroupList = singletonList(prodElastiGroup);
      } else {
        prodElastiGroupList = emptyList();
      }
      builder.groupToBeDownsized(prodElastiGroupList);
      deployLogCallback.saveExecutionLog("Completed Blue green setup for Spotinst", INFO, SUCCESS);




//      // Handle canary and basic
//      String prefix = format("%s__", elastigroupSetupCommandRequest.getElastigroupNamePrefix());
//      int elastiGroupVersion = 1;
//      deployLogCallback.saveExecutionLog(
//          format("Querying Spotinst for existing Elastigroups with prefix: [%s]", prefix));
//      SpotInstConfig spotInstConfig = elastigroupSetupCommandRequest.getSpotInstConfig();
//      elastigroupCommandTaskNGHelper.decryptSpotInstConfig(spotInstConfig);
//      SpotPermanentTokenConfigSpecDTO spotPermanentTokenConfigSpecDTO =
//          (SpotPermanentTokenConfigSpecDTO) spotInstConfig.getSpotConnectorDTO().getCredential().getConfig();
//      String spotInstAccountId = spotPermanentTokenConfigSpecDTO.getSpotAccountIdRef().getDecryptedValue() != null
//          ? spotPermanentTokenConfigSpecDTO.getSpotAccountIdRef().getDecryptedValue().toString()
//          : spotPermanentTokenConfigSpecDTO.getSpotAccountId();
//      String spotInstApiTokenRef = new String(spotPermanentTokenConfigSpecDTO.getApiTokenRef().getDecryptedValue());
//      List<ElastiGroup> elastiGroups = elastigroupCommandTaskNGHelper.listAllElastiGroups(
//          spotInstApiTokenRef, spotInstAccountId, elastigroupSetupCommandRequest.getElastigroupNamePrefix());
//      if (isNotEmpty(elastiGroups)) {
//        elastiGroupVersion =
//            Integer.parseInt(elastiGroups.get(elastiGroups.size() - 1).getName().substring(prefix.length())) + 1;
//      }
//      String newElastiGroupName = format("%s%d", prefix, elastiGroupVersion);
//
//      String finalJson =
//          elastigroupCommandTaskNGHelper.generateFinalJson(elastigroupSetupCommandRequest, newElastiGroupName);
//
//      deployLogCallback.saveExecutionLog(
//          format("Sending request to create Elastigroup with name: [%s]", newElastiGroupName));
//      ElastiGroup elastiGroup =
//          elastigroupCommandTaskNGHelper.createElastiGroup(spotInstApiTokenRef, spotInstAccountId, finalJson);
//      String newElastiGroupId = elastiGroup.getId();
//      deployLogCallback.saveExecutionLog(format("Created Elastigroup with id: [%s]", newElastiGroupId));
//
//      /**
//       * Look at all the Elastigroups except the "LAST" elastigroup.
//       * If they have running instances, we will downscale them to 0.
//       */
//      List<ElastiGroup> groupsWithoutInstances = newArrayList();
//      List<ElastiGroup> groupToDownsizeDuringDeploy = emptyList();
//      if (isNotEmpty(elastiGroups)) {
//        groupToDownsizeDuringDeploy = singletonList(elastiGroups.get(elastiGroups.size() - 1));
//        for (int i = 0; i < elastiGroups.size() - 1; i++) {
//          ElastiGroup elastigroupCurrent = elastiGroups.get(i);
//          ElastiGroupCapacity capacity = elastigroupCurrent.getCapacity();
//          if (capacity == null) {
//            groupsWithoutInstances.add(elastigroupCurrent);
//            continue;
//          }
//          int target = capacity.getTarget();
//          if (target == 0) {
//            groupsWithoutInstances.add(elastigroupCurrent);
//          } else {
//            deployLogCallback.saveExecutionLog(
//                format("Downscaling old Elastigroup with id: [%s] to 0 instances.", elastigroupCurrent.getId()));
//            ElastiGroup temp = ElastiGroup.builder()
//                                   .id(elastigroupCurrent.getId())
//                                   .name(elastigroupCurrent.getName())
//                                   .capacity(ElastiGroupCapacity.builder().minimum(0).maximum(0).target(0).build())
//                                   .build();
//            elastigroupCommandTaskNGHelper.updateElastiGroupCapacity(
//                spotInstApiTokenRef, spotInstAccountId, elastigroupCurrent.getId(), temp);
//          }
//        }
//      }
//
//      int lastIdx = groupsWithoutInstances.size() - elastiGroupsToKeep;
//      for (int i = 0; i < lastIdx; i++) {
//        String nameToDelete = groupsWithoutInstances.get(i).getName();
//        String idToDelete = groupsWithoutInstances.get(i).getId();
//        deployLogCallback.saveExecutionLog(
//            format("Sending request to delete Elastigroup: [%s] with id: [%s]", nameToDelete, idToDelete));
//        elastigroupCommandTaskNGHelper.deleteElastiGroup(spotInstApiTokenRef, spotInstAccountId, idToDelete);
//      }
      //------------

      deployLogCallback.saveExecutionLog(color(format("%n Setup Successful."), LogColor.Green, LogWeight.Bold),
          LogLevel.INFO, CommandExecutionStatus.SUCCESS);

      ElastigroupSetupResult elastigroupSetupResult =
          ElastigroupSetupResult.builder()
              .elastiGroupNamePrefix(elastigroupSetupCommandRequest.getElastigroupNamePrefix())
              .newElastiGroup(elastiGroup)
              .elastigroupOriginalConfig(elastigroupSetupCommandRequest.getElastigroupOriginalConfig())
              .groupToBeDownsized(groupToDownsizeDuringDeploy)
              .elastiGroupNamePrefix(elastigroupSetupCommandRequest.getElastigroupNamePrefix())
              .isBlueGreen(elastigroupSetupCommandRequest.isBlueGreen())
              .useCurrentRunningInstanceCount(
                  ((ElastigroupSetupCommandRequest) elastigroupCommandRequest).isUseCurrentRunningInstanceCount())
              .currentRunningInstanceCount(elastigroupSetupCommandRequest.getCurrentRunningInstanceCount())
              .maxInstanceCount(elastigroupSetupCommandRequest.getMaxInstanceCount())
              .resizeStrategy(elastigroupSetupCommandRequest.getResizeStrategy())
              .build();

      return ElastigroupSetupResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .elastigroupSetupResult(elastigroupSetupResult)
          .build();

    } catch (Exception ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      deployLogCallback.saveExecutionLog(color(format("%n Deployment Failed."), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new ElastigroupNGException(sanitizedException);
    }
  }
}
