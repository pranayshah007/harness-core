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
import static io.harness.spotinst.model.SpotInstConstants.COMPUTE;
import static io.harness.spotinst.model.SpotInstConstants.ELASTI_GROUP_IMAGE_CONFIG;
import static io.harness.spotinst.model.SpotInstConstants.ELASTI_GROUP_USER_DATA_CONFIG;
import static io.harness.spotinst.model.SpotInstConstants.GROUP_CONFIG_ELEMENT;
import static io.harness.spotinst.model.SpotInstConstants.LAUNCH_SPECIFICATION;
import static io.harness.spotinst.model.SpotInstConstants.LB_TYPE_TG;
import static io.harness.spotinst.model.SpotInstConstants.LOAD_BALANCERS_CONFIG;
import static io.harness.spotinst.model.SpotInstConstants.SETUP_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.STAGE_ELASTI_GROUP_NAME_SUFFIX;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.aws.LbDetailsForAlbTrafficShift;
import io.harness.delegate.task.spotinst.request.SpotInstTaskParameters;
import io.harness.delegate.task.spotinst.request.SpotinstTrafficShiftAlbSetupParameters;
import io.harness.delegate.task.spotinst.response.SpotInstTaskExecutionResponse;
import io.harness.delegate.task.spotinst.response.SpotinstTrafficShiftAlbSetupResponse;
import io.harness.delegate.task.spotinst.response.SpotinstTrafficShiftAlbSetupResponse.SpotinstTrafficShiftAlbSetupResponseBuilder;
import io.harness.exception.InvalidRequestException;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupLoadBalancer;
import io.harness.spotinst.model.ElastiGroupLoadBalancerConfig;

import software.wings.beans.AwsConfig;
import software.wings.beans.SpotInstConfig;
import software.wings.beans.command.ExecutionLogCallback;

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
public class SpotinstTrafficShiftAlbSetupTaskHandler extends SpotInstTaskHandler {
  @Override
  protected SpotInstTaskExecutionResponse executeTaskInternal(SpotInstTaskParameters spotinstTaskParameters,
      SpotInstConfig spotinstConfig, AwsConfig awsConfig) throws Exception {
    if (!(spotinstTaskParameters instanceof SpotinstTrafficShiftAlbSetupParameters)) {
      String message = format("Parameters of unrecognized class: [%s] found while executing setup step.",
          spotinstTaskParameters.getClass().getSimpleName());
      log.error(message);
      return SpotInstTaskExecutionResponse.builder().commandExecutionStatus(FAILURE).errorMessage(message).build();
    }

    String spotinstAccountId = spotinstConfig.getSpotInstAccountId();
    String spotinstToken = String.valueOf(spotinstConfig.getSpotInstToken());
    SpotinstTrafficShiftAlbSetupParameters parameters = (SpotinstTrafficShiftAlbSetupParameters) spotinstTaskParameters;
    ExecutionLogCallback logCallback = getLogCallBack(parameters, SETUP_COMMAND_UNIT);

    SpotinstTrafficShiftAlbSetupResponseBuilder builder = SpotinstTrafficShiftAlbSetupResponse.builder();
    List<LbDetailsForAlbTrafficShift> detailsWithTargetGroups =
        loadTargetGroupDetails(awsConfig, parameters.getAwsRegion(), parameters.getLbDetails(), logCallback);
    builder.lbDetailsWithTargetGroups(detailsWithTargetGroups);

    String stageElastigroupName =
        format("%s__%s", parameters.getElastigroupNamePrefix(), STAGE_ELASTI_GROUP_NAME_SUFFIX);

    String finalElastigroupJson =
        generateFinalJsonForElastigroupCreate(parameters, stageElastigroupName, detailsWithTargetGroups);

    logCallback.saveExecutionLog(format("Deleting Elastigroup with name: [%s] if it exists", stageElastigroupName));
    Optional<ElastiGroup> existingStageElastigroup =
        spotInstHelperServiceDelegate.getElastiGroupByName(spotinstToken, spotinstAccountId, stageElastigroupName);
    if (existingStageElastigroup.isPresent()) {
      logCallback.saveExecutionLog(format("Found elastigroup with id: [%s]", existingStageElastigroup.get().getId()));
      spotInstHelperServiceDelegate.deleteElastiGroup(
          spotinstToken, spotinstAccountId, existingStageElastigroup.get().getId());
    }

    logCallback.saveExecutionLog(format("Creating new Elastigroup with name: [%s]", stageElastigroupName));
    ElastiGroup newElastigroup =
        spotInstHelperServiceDelegate.createElastiGroup(spotinstToken, spotinstAccountId, finalElastigroupJson);
    logCallback.saveExecutionLog(format("Id of new Elastigroup: [%s]", newElastigroup.getId()));
    builder.newElastigroup(newElastigroup);

    logCallback.saveExecutionLog(
        format("Getting data for Prod elastigroup with name: [%s]", parameters.getElastigroupNamePrefix()));
    Optional<ElastiGroup> prodElastigroup = spotInstHelperServiceDelegate.getElastiGroupByName(
        spotinstToken, spotinstAccountId, parameters.getElastigroupNamePrefix());
    if (prodElastigroup.isPresent()) {
      logCallback.saveExecutionLog(format("Found existing Elastigroup with id: [%s]", prodElastigroup.get().getId()));
      builder.elastiGroupsToBeDownsized(singletonList(prodElastigroup.get()));
    }

    logCallback.saveExecutionLog("Completed Blue green setup for Spotinst Traffic Shift.", INFO, SUCCESS);
    return SpotInstTaskExecutionResponse.builder()
        .commandExecutionStatus(SUCCESS)
        .spotInstTaskResponse(builder.build())
        .build();
  }

  private String generateFinalJsonForElastigroupCreate(SpotinstTrafficShiftAlbSetupParameters parameters,
      String elastigroupName, List<LbDetailsForAlbTrafficShift> detailsWithTargetGroups) {
    Map<String, Object> jsonConfigMap = getJsonConfigMapFromElastigroupJson(parameters.getElastigroupJson());
    Map<String, Object> elastigroupConfigMap = (Map<String, Object>) jsonConfigMap.get(GROUP_CONFIG_ELEMENT);

    removeUnsupportedFieldsForCreatingNewGroup(elastigroupConfigMap);
    updateName(elastigroupConfigMap, elastigroupName);
    updateInitialCapacity(elastigroupConfigMap);

    Map<String, Object> computeConfigMap = (Map<String, Object>) elastigroupConfigMap.get(COMPUTE);
    Map<String, Object> launchSpecificationMap = (Map<String, Object>) computeConfigMap.get(LAUNCH_SPECIFICATION);
    launchSpecificationMap.put(ELASTI_GROUP_IMAGE_CONFIG, parameters.getImage());
    if (isNotEmpty(parameters.getUserData())) {
      launchSpecificationMap.put(ELASTI_GROUP_USER_DATA_CONFIG, parameters.getUserData());
    }
    List<ElastiGroupLoadBalancer> loadBalancers = new ArrayList<>();
    for (LbDetailsForAlbTrafficShift details : detailsWithTargetGroups) {
      loadBalancers.add(ElastiGroupLoadBalancer.builder()
                            .arn(details.getStageTargetGroupArn())
                            .name(details.getStageTargetGroupName())
                            .type(LB_TYPE_TG)
                            .build());
    }
    launchSpecificationMap.put(
        LOAD_BALANCERS_CONFIG, ElastiGroupLoadBalancerConfig.builder().loadBalancers(loadBalancers).build());

    Gson gson = new Gson();
    return gson.toJson(jsonConfigMap);
  }

  private List<LbDetailsForAlbTrafficShift> loadTargetGroupDetails(AwsConfig awsConfig, String region,
      List<LbDetailsForAlbTrafficShift> originalLbDetails, ExecutionLogCallback logCallback) {
    if (isEmpty(originalLbDetails)) {
      throw new InvalidRequestException("No load balancers found for traffic shifting.");
    }
    List<LbDetailsForAlbTrafficShift> detailsWithTargetGroups = new ArrayList<>();
    for (LbDetailsForAlbTrafficShift originalLbDetail : originalLbDetails) {
      detailsWithTargetGroups.add(awsElbHelperServiceDelegate.loadTrafficShiftTargetGroupData(
          awsConfig, region, emptyList(), originalLbDetail, logCallback));
    }
    return detailsWithTargetGroups;
  }
}
