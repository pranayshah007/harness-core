/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.elastigroup;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.elastigroup.ElastigroupCommandTaskNGHelper.getElastigroupString;
import static io.harness.spotinst.model.SpotInstConstants.elastiGroupsToKeep;

import static software.wings.beans.LogHelper.color;

import static com.google.api.client.util.Lists.newArrayList;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.spotconnector.SpotPermanentTokenConfigSpecDTO;
import io.harness.delegate.beans.elastigroup.ElastigroupSetupResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.ElastigroupNGException;
import io.harness.delegate.task.elastigroup.ElastigroupCommandTaskNGHelper;
import io.harness.delegate.task.elastigroup.request.ElastigroupCommandRequest;
import io.harness.delegate.task.elastigroup.request.ElastigroupSetupCommandRequest;
import io.harness.delegate.task.elastigroup.response.ElastigroupCommandResponse;
import io.harness.delegate.task.elastigroup.response.ElastigroupSetupResponse;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.elastigroup.ElastigroupCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.exception.sanitizer.SpotInstRestException;
import io.harness.exception.sanitizer.SpotInstRestExceptionHandler;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.spotinst.SpotInstHelperServiceDelegate;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class ElastigroupSetupCommandTaskHandler extends ElastigroupCommandTaskHandler {
  @Inject private ElastigroupCommandTaskNGHelper elastigroupCommandTaskNGHelper;
  @Inject protected SpotInstHelperServiceDelegate spotInstHelperServiceDelegate;

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

    LogCallback deployLogCallback = elastigroupCommandTaskNGHelper.getLogCallback(iLogStreamingTaskClient,
        ElastigroupCommandUnitConstants.CREATE_ELASTIGROUP.toString(), false, commandUnitsProgress);
    try {
      // Handle canary and basic
      String prefix = format("%s__", elastigroupSetupCommandRequest.getElastigroupNamePrefix());
      int elastigroupVersion = 1;
      deployLogCallback.saveExecutionLog(
          format("Querying Spotinst for existing Elastigroups with prefix: [%s]", prefix));
      SpotInstConfig spotInstConfig = elastigroupSetupCommandRequest.getSpotInstConfig();
      elastigroupCommandTaskNGHelper.decryptSpotInstConfig(spotInstConfig);
      SpotPermanentTokenConfigSpecDTO spotPermanentTokenConfigSpecDTO =
          (SpotPermanentTokenConfigSpecDTO) spotInstConfig.getSpotConnectorDTO().getCredential().getConfig();
      String spotInstAccountId = spotPermanentTokenConfigSpecDTO.getSpotAccountIdRef().getDecryptedValue() != null
          ? String.valueOf(spotPermanentTokenConfigSpecDTO.getSpotAccountIdRef().getDecryptedValue())
          : spotPermanentTokenConfigSpecDTO.getSpotAccountId();
      String spotInstApiToken = String.valueOf(spotPermanentTokenConfigSpecDTO.getApiTokenRef().getDecryptedValue());
      List<ElastiGroup> elastigroupsBeforeDeletion = spotInstHelperServiceDelegate.listAllElastiGroups(
          spotInstApiToken, spotInstAccountId, elastigroupSetupCommandRequest.getElastigroupNamePrefix());
      elastigroupCommandTaskNGHelper.logElastigroups(elastigroupsBeforeDeletion, deployLogCallback);
      if (isNotEmpty(elastigroupsBeforeDeletion)) {
        elastigroupVersion = Integer.parseInt(elastigroupsBeforeDeletion.get(elastigroupsBeforeDeletion.size() - 1)
                                                  .getName()
                                                  .substring(prefix.length()))
            + 1;
      }
      String newElastiGroupName = format("%s%d", prefix, elastigroupVersion);

      String finalJson =
          elastigroupCommandTaskNGHelper.generateFinalJson(elastigroupSetupCommandRequest, newElastiGroupName);

      deployLogCallback.saveExecutionLog(
          format("Sending request to create Elastigroup with name: [%s]", newElastiGroupName));
      ElastiGroup elastigroup =
          spotInstHelperServiceDelegate.createElastiGroup(spotInstApiToken, spotInstAccountId, finalJson);
      deployLogCallback.saveExecutionLog(format("Created Elastigroup %s", getElastigroupString(elastigroup)));

      /**
       * Look at all the last consecutive Elastigroups with 0 target. Delete them. Useful when the step is aborted
       */
      List<ElastiGroup> elastigroups = deleteLastConsecutiveElastigroupsWithZeroCapacity(
          elastigroupsBeforeDeletion, deployLogCallback, spotInstAccountId, spotInstApiToken);

      /**
       * Look at all the Elastigroups except the "LAST" elastigroup.
       * If they have running instances, we will downscale them to 0.
       */
      List<ElastiGroup> groupsWithoutInstances = newArrayList();
      List<ElastiGroup> groupToDownsizeDuringDeploy = emptyList();
      if (isNotEmpty(elastigroups)) {
        groupToDownsizeDuringDeploy = singletonList(elastigroups.get(elastigroups.size() - 1));
        for (int i = 0; i < elastigroups.size() - 1; i++) {
          ElastiGroup elastigroupCurrent = elastigroups.get(i);
          ElastiGroupCapacity capacity = elastigroupCurrent.getCapacity();
          if (capacity == null) {
            groupsWithoutInstances.add(elastigroupCurrent);
            continue;
          }
          int target = capacity.getTarget();
          if (target == 0) {
            groupsWithoutInstances.add(elastigroupCurrent);
          } else {
            deployLogCallback.saveExecutionLog(
                format("Downscaling old Elastigroup: %s to 0 instances.", getElastigroupString(elastigroupCurrent)));
            ElastiGroup temp = ElastiGroup.builder()
                                   .id(elastigroupCurrent.getId())
                                   .name(elastigroupCurrent.getName())
                                   .capacity(ElastiGroupCapacity.builder().minimum(0).maximum(0).target(0).build())
                                   .build();
            spotInstHelperServiceDelegate.updateElastiGroupCapacity(
                spotInstApiToken, spotInstAccountId, elastigroupCurrent.getId(), temp);
          }
        }
      }

      int lastIdx = groupsWithoutInstances.size() - elastiGroupsToKeep;
      for (int i = 0; i < lastIdx; i++) {
        ElastiGroup elastigroupToDelete = groupsWithoutInstances.get(i);
        String idToDelete = elastigroupToDelete.getId();
        deployLogCallback.saveExecutionLog(
            format("Sending request to delete Elastigroup: %s", getElastigroupString(elastigroupToDelete)));
        spotInstHelperServiceDelegate.deleteElastiGroup(spotInstApiToken, spotInstAccountId, idToDelete);
      }

      ElastigroupSetupResult elastigroupSetupResult =
          ElastigroupSetupResult.builder()
              .elastigroupNamePrefix(elastigroupSetupCommandRequest.getElastigroupNamePrefix())
              .newElastigroup(elastigroup)
              .elastigroupOriginalConfig(elastigroupSetupCommandRequest.getGeneratedElastigroupConfig())
              .groupToBeDownsized(groupToDownsizeDuringDeploy)
              .elastigroupNamePrefix(elastigroupSetupCommandRequest.getElastigroupNamePrefix())
              .isBlueGreen(elastigroupSetupCommandRequest.isBlueGreen())
              .useCurrentRunningInstanceCount(
                  ((ElastigroupSetupCommandRequest) elastigroupCommandRequest).isUseCurrentRunningInstanceCount())
              .maxInstanceCount(elastigroupSetupCommandRequest.getMaxInstanceCount())
              .resizeStrategy(elastigroupSetupCommandRequest.getResizeStrategy())
              .build();

      deployLogCallback.saveExecutionLog("Successfully completed", LogLevel.INFO, CommandExecutionStatus.SUCCESS);

      return ElastigroupSetupResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .elastigroupSetupResult(elastigroupSetupResult)
          .build();

    } catch (SpotInstRestException e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      deployLogCallback.saveExecutionLog(sanitizedException.getMessage());
      Exception handledException = SpotInstRestExceptionHandler.handleException(sanitizedException);
      deployLogCallback.saveExecutionLog(
          color("Deployment Failed.", LogColor.Red, LogWeight.Bold), LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new ElastigroupNGException(handledException);
    } catch (Exception ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      deployLogCallback.saveExecutionLog(sanitizedException.getMessage());
      deployLogCallback.saveExecutionLog(
          color("Deployment Failed.", LogColor.Red, LogWeight.Bold), LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new ElastigroupNGException(sanitizedException);
    }
  }

  private List<ElastiGroup> deleteLastConsecutiveElastigroupsWithZeroCapacity(List<ElastiGroup> elastigroups,
      LogCallback deployLogCallback, String spotInstAccountId, String spotInstApiTokenRef) throws Exception {
    List<ElastiGroup> result = newArrayList();
    if (isNotEmpty(elastigroups)) {
      boolean lastElastigroupWithZeroInstances = true;
      for (int i = elastigroups.size() - 1; i >= 0; i--) {
        ElastiGroup elastigroupCurrent = elastigroups.get(i);
        if (lastElastigroupWithZeroInstances) {
          ElastiGroupCapacity capacity = elastigroupCurrent.getCapacity();
          if (capacity == null || capacity.getTarget() == 0) {
            deployLogCallback.saveExecutionLog(format(
                "Sending request to delete Elastigroup: %s with capacity 0", getElastigroupString(elastigroupCurrent)));
            spotInstHelperServiceDelegate.deleteElastiGroup(
                spotInstApiTokenRef, spotInstAccountId, elastigroupCurrent.getId());
          } else {
            lastElastigroupWithZeroInstances = false;
            result.add(elastigroupCurrent);
          }
        } else {
          result.add(elastigroupCurrent);
        }
      }
    }
    // Required for next cleanup stage
    Collections.reverse(result);

    return result;
  }
}
