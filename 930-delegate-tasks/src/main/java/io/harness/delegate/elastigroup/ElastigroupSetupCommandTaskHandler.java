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
import io.harness.delegate.beans.connector.spotconnector.SpotPermanentTokenConfigSpecDTO;
import io.harness.delegate.beans.ecs.EcsCanaryDeployResult;
import io.harness.delegate.beans.elastigroup.ElastigroupSetupResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.ecs.EcsCommandTaskNGHandler;
import io.harness.delegate.exception.EcsNGException;
import io.harness.delegate.exception.ElastigroupNGException;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsInfraConfigHelper;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsCanaryDeployRequest;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.response.EcsCanaryDeployResponse;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.delegate.task.elastigroup.ElastigroupCommandTaskNGHelper;
import io.harness.delegate.task.elastigroup.ElastigroupDelegateTaskHelper;
import io.harness.delegate.task.elastigroup.request.ElastigroupCommandRequest;
import io.harness.delegate.task.elastigroup.request.ElastigroupSetupCommandRequest;
import io.harness.delegate.task.elastigroup.response.ElastigroupCommandResponse;
import io.harness.delegate.task.elastigroup.response.ElastigroupSetupResponse;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.ecs.EcsCommandUnitConstants;
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
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.TaskDefinition;
import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import java.util.List;
import java.util.Map;

import static com.google.api.client.util.Lists.newArrayList;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.spotinst.model.SpotInstConstants.elastiGroupsToKeep;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static software.wings.beans.LogHelper.color;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class ElastigroupSetupCommandTaskHandler extends ElastigroupCommandTaskNGHandler {
  @Inject private EcsTaskHelperBase ecsTaskHelperBase;
  @Inject private EcsInfraConfigHelper ecsInfraConfigHelper;
  @Inject private ElastigroupCommandTaskNGHelper elastigroupCommandTaskNGHelper;
  private EcsInfraConfig ecsInfraConfig;
  private long timeoutInMillis;

  @Override
  protected ElastigroupCommandResponse executeTaskInternal(ElastigroupCommandRequest elastigroupCommandRequest,
                                                           ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(elastigroupCommandRequest instanceof ElastigroupSetupCommandRequest)) {
      throw new InvalidArgumentsException(Pair.of("elastigroupCommandRequest", "Must be instance of ElastigroupSetupCommandRequest"));
    }
    ElastigroupSetupCommandRequest elastigroupSetupCommandRequest = (ElastigroupSetupCommandRequest) elastigroupCommandRequest;

    timeoutInMillis = elastigroupSetupCommandRequest.getTimeoutIntervalInMin() * 60000;
//    ecsInfraConfig = elastigroupSetupCommandRequest.getEcsInfraConfig();

    LogCallback deployLogCallback = ecsTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, ElastigroupCommandUnitConstants.createSetup.toString(), true, commandUnitsProgress);
    try {
//      String ecsTaskDefinitionManifestContent = ecsCanaryDeployRequest.getEcsTaskDefinitionManifestContent();
//      String ecsServiceDefinitionManifestContent = ecsCanaryDeployRequest.getEcsServiceDefinitionManifestContent();
//      List<String> ecsScalableTargetManifestContentList =
//          ecsCanaryDeployRequest.getEcsScalableTargetManifestContentList();
//      List<String> ecsScalingPolicyManifestContentList =
//          ecsCanaryDeployRequest.getEcsScalingPolicyManifestContentList();
//
//      RegisterTaskDefinitionRequest.Builder registerTaskDefinitionRequestBuilder =
//          ecsCommandTaskHelper.parseYamlAsObject(
//              ecsTaskDefinitionManifestContent, RegisterTaskDefinitionRequest.serializableBuilderClass());
//      RegisterTaskDefinitionRequest registerTaskDefinitionRequest = registerTaskDefinitionRequestBuilder.build();
//      CreateServiceRequest.Builder createServiceRequestBuilder = ecsCommandTaskHelper.parseYamlAsObject(
//          ecsServiceDefinitionManifestContent, CreateServiceRequest.serializableBuilderClass());
//
//      deployLogCallback.saveExecutionLog(
//          format("Creating Task Definition with family %s %n", registerTaskDefinitionRequest.family()), LogLevel.INFO);
//
//      RegisterTaskDefinitionResponse registerTaskDefinitionResponse = ecsCommandTaskHelper.createTaskDefinition(
//          registerTaskDefinitionRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());
//      TaskDefinition taskDefinition = registerTaskDefinitionResponse.taskDefinition();
//      String taskDefinitionName = taskDefinition.family() + ":" + taskDefinition.revision();
//      String taskDefinitionArn = taskDefinition.taskDefinitionArn();
//
//      deployLogCallback.saveExecutionLog(
//          format("Created Task Definition %s with Arn %s..%n", taskDefinitionName, taskDefinitionArn), LogLevel.INFO);
//
//      // replace cluster and task definition
//      createServiceRequestBuilder.cluster(ecsInfraConfig.getCluster())
//          .taskDefinition(registerTaskDefinitionResponse.taskDefinition().taskDefinitionArn())
//          .build();
//
//      CreateServiceRequest createServiceRequest = createServiceRequestBuilder.build();
//
//      // update canary details in create service request
//      String canaryServiceName = createServiceRequest.serviceName() + ecsCanaryDeployRequest.getEcsServiceNameSuffix();
//      createServiceRequest =
//          createServiceRequest.toBuilder()
//              .serviceName(canaryServiceName)
//              .desiredCount(Integer.parseInt(Long.toString(ecsCanaryDeployRequest.getDesiredCountOverride())))
//              .build();
//
//      ecsCommandTaskHelper.createCanaryService(createServiceRequest, ecsScalableTargetManifestContentList,
//          ecsScalingPolicyManifestContentList, ecsInfraConfig, deployLogCallback, timeoutInMillis);
//
//      EcsCanaryDeployResult ecsCanaryDeployResult =
//          EcsCanaryDeployResult.builder()
//              .region(ecsInfraConfig.getRegion())
//              .ecsTasks(ecsCommandTaskHelper.getRunningEcsTasks(ecsInfraConfig.getAwsConnectorDTO(),
//                  ecsInfraConfig.getCluster(), createServiceRequest.serviceName(), ecsInfraConfig.getRegion()))
//              .canaryServiceName(canaryServiceName)
//              .build();

      // Handle canary and basic
      String prefix = format("%s__", elastigroupSetupCommandRequest.getElastigroupNamePrefix());
      int elastiGroupVersion = 1;
      deployLogCallback.saveExecutionLog(format("Querying Spotinst for existing Elastigroups with prefix: [%s]", prefix));
      SpotInstConfig spotInstConfig = elastigroupSetupCommandRequest.getSpotInstConfig();
      elastigroupCommandTaskNGHelper.decryptSpotInstConfig(spotInstConfig);
      SpotPermanentTokenConfigSpecDTO spotPermanentTokenConfigSpecDTO = (SpotPermanentTokenConfigSpecDTO) spotInstConfig.getSpotConnectorDTO().getCredential().getConfig();
      String spotInstAccountId = spotPermanentTokenConfigSpecDTO.getSpotAccountIdRef().getDecryptedValue() != null ? spotPermanentTokenConfigSpecDTO.getSpotAccountIdRef().getDecryptedValue().toString()
              : spotPermanentTokenConfigSpecDTO.getSpotAccountId();
      String spotInstApiTokenRef = new String(spotPermanentTokenConfigSpecDTO.getApiTokenRef().getDecryptedValue());
      List<ElastiGroup> elastiGroups = elastigroupCommandTaskNGHelper.listAllElastiGroups(
              spotInstApiTokenRef, spotInstAccountId, elastigroupSetupCommandRequest.getElastigroupNamePrefix());
      if (isNotEmpty(elastiGroups)) {
        elastiGroupVersion =
                Integer.parseInt(elastiGroups.get(elastiGroups.size() - 1).getName().substring(prefix.length())) + 1;
      }
      String newElastiGroupName = format("%s%d", prefix, elastiGroupVersion);

      Map<String, Object> finalJson = elastigroupCommandTaskNGHelper.generateFinalJson(elastigroupSetupCommandRequest, newElastiGroupName);

      deployLogCallback.saveExecutionLog(format("Sending request to create Elastigroup with name: [%s]", newElastiGroupName));
      ElastiGroup elastiGroup =
              elastigroupCommandTaskNGHelper.createElastiGroup(spotInstApiTokenRef, spotInstAccountId, finalJson);
      String newElastiGroupId = elastiGroup.getId();
      deployLogCallback.saveExecutionLog(format("Created Elastigroup with id: [%s]", newElastiGroupId));

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
            deployLogCallback.saveExecutionLog(
                    format("Downscaling old Elastigroup with id: [%s] to 0 instances.", elastigroupCurrent.getId()));
            ElastiGroup temp = ElastiGroup.builder()
                    .id(elastigroupCurrent.getId())
                    .name(elastigroupCurrent.getName())
                    .capacity(ElastiGroupCapacity.builder().minimum(0).maximum(0).target(0).build())
                    .build();
            elastigroupCommandTaskNGHelper.updateElastiGroupCapacity(
                    spotInstApiTokenRef, spotInstAccountId, elastigroupCurrent.getId(), temp);
          }
        }
      }

      int lastIdx = groupsWithoutInstances.size() - elastiGroupsToKeep;
      for (int i = 0; i < lastIdx; i++) {
        String nameToDelete = groupsWithoutInstances.get(i).getName();
        String idToDelete = groupsWithoutInstances.get(i).getId();
        deployLogCallback.saveExecutionLog(
                format("Sending request to delete Elastigroup: [%s] with id: [%s]", nameToDelete, idToDelete));
        elastigroupCommandTaskNGHelper.deleteElastiGroup(spotInstApiTokenRef, spotInstAccountId, idToDelete);
      }
//------------

      deployLogCallback.saveExecutionLog(color(format("%n Setup Successful."), LogColor.Green, LogWeight.Bold),
          LogLevel.INFO, CommandExecutionStatus.SUCCESS);

      ElastigroupSetupResult elastigroupSetupResult = ElastigroupSetupResult.builder()
              .elstiGroupNamePrefix(elastigroupSetupCommandRequest.getElastigroupNamePrefix())
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
