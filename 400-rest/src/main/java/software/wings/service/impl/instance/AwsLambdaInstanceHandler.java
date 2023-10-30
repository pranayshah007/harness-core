/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.MOVE_AWS_LAMBDA_INSTANCE_SYNC_TO_PERPETUAL_TASK;
import static io.harness.beans.FeatureName.STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AWS_LAMBDA_DEPLOYMENTS;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.infrastructure.instance.InvocationCount.InvocationCountKey.INVOCATION_COUNT_KEY_LIST;
import static software.wings.service.impl.instance.InstanceSyncFlow.NEW_DEPLOYMENT;
import static software.wings.service.impl.instance.InstanceSyncFlow.PERPETUAL_TASK;

import static io.fabric8.utils.Lists.isNullOrEmpty;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.joda.time.Seconds.secondsBetween;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.NoDelegatesException;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.UnexpectedException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.api.AwsLambdaContextElement.FunctionMeta;
import software.wings.api.CommandStepExecutionSummary;
import software.wings.api.DeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.api.PhaseExecutionData;
import software.wings.api.PhaseStepExecutionData;
import software.wings.api.lambda.AwsLambdaDeploymentInfo;
import software.wings.api.ondemandrollback.OnDemandRollbackInfo;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.AwsLambdaInfraStructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.Tag;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.infrastructure.instance.InvocationCount;
import software.wings.beans.infrastructure.instance.InvocationCount.InvocationCountKey;
import software.wings.beans.infrastructure.instance.ServerlessInstance;
import software.wings.beans.infrastructure.instance.ServerlessInstance.ServerlessInstanceBuilder;
import software.wings.beans.infrastructure.instance.ServerlessInstanceType;
import software.wings.beans.infrastructure.instance.info.AwsLambdaInstanceInfo;
import software.wings.beans.infrastructure.instance.info.ServerlessInstanceInfo;
import software.wings.beans.infrastructure.instance.key.AwsLambdaInstanceKey;
import software.wings.beans.infrastructure.instance.key.deployment.AwsLambdaDeploymentKey;
import software.wings.beans.infrastructure.instance.key.deployment.DeploymentKey;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.AwsLambdaInstanceSyncPerpetualTaskCreator;
import software.wings.service.InstanceSyncPerpetualTaskCreator;
import software.wings.service.impl.aws.model.embed.AwsLambdaDetails;
import software.wings.service.impl.aws.model.request.AwsCloudWatchStatisticsRequest;
import software.wings.service.impl.aws.model.request.AwsLambdaDetailsRequest;
import software.wings.service.impl.aws.model.response.AwsCloudWatchStatisticsResponse;
import software.wings.service.impl.aws.model.response.AwsLambdaDetailsMetricsResponse;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.aws.delegate.AwsCloudWatchHelperServiceDelegate;
import software.wings.service.intfc.aws.manager.AwsLambdaHelperServiceManager;
import software.wings.service.intfc.instance.ServerlessInstanceService;
import software.wings.sm.PhaseStepExecutionSummary;

import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.DateTime;

@Slf4j
@Singleton
@OwnedBy(CDP)
public class AwsLambdaInstanceHandler extends InstanceHandler implements InstanceSyncByPerpetualTaskHandler {
  @Inject private AwsLambdaInstanceSyncPerpetualTaskCreator perpetualTaskCreator;
  @Inject private AwsLambdaHelperServiceManager awsLambdaHelperServiceManager;
  @Inject ArtifactService artifactService;
  @Inject ServerlessInstanceService serverlessInstanceService;
  @Inject DelegateProxyFactory delegateProxyFactory;

  @Override
  public void syncInstances(String appId, String infraMappingId, InstanceSyncFlow instanceSyncFlow) {
    syncInstancesInternal(appId, infraMappingId, emptyList(), null, instanceSyncFlow);
  }

  @Override
  protected void validateInstanceType(String infraMappingType) {
    ServerlessInstanceType instanceType = getInstanceType(infraMappingType);
    notNullCheck("InstanceType", instanceType);
  }

  @VisibleForTesting
  Collection<ServerlessInstance> getActiveServerlessInstances(String appId, String infraMappingId) {
    PageRequest<ServerlessInstance> pageRequest = new PageRequest<>();
    pageRequest.addFilter("infraMappingId", Operator.EQ, infraMappingId);
    pageRequest.addFilter("appId", Operator.EQ, appId);
    PageResponse<ServerlessInstance> pageResponse = serverlessInstanceService.list(pageRequest);
    return pageResponse.getResponse();
  }

  AwsLambdaInfraStructureMapping getInfraMapping(String infraMappingId, String appId) {
    final InfrastructureMapping infrastructureMapping = infraMappingService.get(appId, infraMappingId);
    notNullCheck("Infra mapping is null for id:" + infraMappingId, infrastructureMapping);

    if (!(infrastructureMapping instanceof AwsLambdaInfraStructureMapping)) {
      throw new GeneralException("Incompatible infra mapping type. Expecting AWS Lambda type. Found:"
          + infrastructureMapping.getInfraMappingType());
    }
    return (AwsLambdaInfraStructureMapping) infrastructureMapping;
  }

  @VisibleForTesting
  SettingAttribute cloudProviderSetting(InfrastructureMapping infrastructureMapping) {
    final SettingAttribute cloudProviderSetting =
        settingsService.get(infrastructureMapping.getComputeProviderSettingId());

    notNullCheck("Cloud Provider Settings is null for id:" + infrastructureMapping.getComputeProviderSettingId(),
        cloudProviderSetting);

    return cloudProviderSetting;
  }
  @VisibleForTesting
  List<EncryptedDataDetail> getEncryptedDataDetails(SettingAttribute cloudProviderSetting) {
    return secretManager.getEncryptionDetails((EncryptableSetting) cloudProviderSetting.getValue(), null, null);
  }
  @VisibleForTesting
  void syncInstancesInternal(String appId, String infraMappingId,
      @NotNull List<DeploymentSummary> newDeploymentSummaries, AwsLambdaDetailsMetricsResponse awsLambdaDetailsResponse,
      InstanceSyncFlow instanceSyncFlow) {
    log.debug("# Performing Aws Lambda Instance Sync");
    final AwsLambdaInfraStructureMapping awsLambdaInfraStructureMapping = getInfraMapping(infraMappingId, appId);
    final SettingAttribute cloudProviderSetting = cloudProviderSetting(awsLambdaInfraStructureMapping);
    final AwsConfig awsConfig = (AwsConfig) cloudProviderSetting.getValue();
    final List<EncryptedDataDetail> encryptedDataDetails = getEncryptedDataDetails(cloudProviderSetting);

    final Collection<ServerlessInstance> activeInstancesInDB =
        emptyIfNull(getActiveServerlessInstances(appId, infraMappingId));
    log.debug("Total no of instances found in DB for AppId: {}, "
            + "No of instances in DB: {}, No of new instances to add: {}",
        appId, activeInstancesInDB.size(), newDeploymentSummaries.size());

    if (PERPETUAL_TASK != instanceSyncFlow) {
      handleNonPerpetualInstanceSync(
          newDeploymentSummaries, awsLambdaInfraStructureMapping, awsConfig, encryptedDataDetails, activeInstancesInDB);
    } else {
      handlePerpetualInstanceSync(awsLambdaDetailsResponse, activeInstancesInDB);
    }
  }

  private void handlePerpetualInstanceSync(
      AwsLambdaDetailsMetricsResponse awsLambdaDetailsResponse, Collection<ServerlessInstance> activeInstancesInDB) {
    log.info("Syncing existing Aws Lambda Instances from perpetual task");
    activeInstancesInDB.stream()
        .filter(awsLambdaInstance -> hasSameFunctionNameAndVersion(awsLambdaInstance, awsLambdaDetailsResponse))
        .forEach(instanceToUpdate -> syncInDBInstanceForPerpetualTask(instanceToUpdate, awsLambdaDetailsResponse));
  }

  private boolean hasSameFunctionNameAndVersion(
      ServerlessInstance awsLambdaInstance, AwsLambdaDetailsMetricsResponse awsLambdaDetailsResponse) {
    boolean hasSameFunctionName = awsLambdaInstance.getLambdaInstanceKey().getFunctionName().equals(
        awsLambdaDetailsResponse.getLambdaDetails().getFunctionName());
    boolean hasSameFunctionVersion = awsLambdaInstance.getLambdaInstanceKey().getFunctionVersion().equals(
        awsLambdaDetailsResponse.getLambdaDetails().getVersion());
    return hasSameFunctionName && hasSameFunctionVersion;
  }

  private void handleNonPerpetualInstanceSync(@NotNull List<DeploymentSummary> newDeploymentSummaries,
      AwsLambdaInfraStructureMapping awsLambdaInfraStructureMapping, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptedDataDetails, Collection<ServerlessInstance> activeInstancesInDB) {
    if (isNotEmpty(newDeploymentSummaries)) {
      handleNewDeploymentInternal(
          newDeploymentSummaries, activeInstancesInDB, awsLambdaInfraStructureMapping, awsConfig, encryptedDataDetails);
    } else {
      log.info("Syncing existing Aws Lambda Instances");
      activeInstancesInDB.forEach(instanceToUpdate
          -> syncInDBInstance(awsLambdaInfraStructureMapping, awsConfig, encryptedDataDetails, instanceToUpdate));
    }
  }

  @VisibleForTesting
  void deleteInstances(Collection<ServerlessInstance> instancesInDB) {
    final List<String> instanceIdsToDelete = instancesInDB.stream().map(ServerlessInstance::getUuid).collect(toList());

    if (isNotEmpty(instanceIdsToDelete)) {
      log.info("Deleting instance Ids ={}", instanceIdsToDelete);
      serverlessInstanceService.delete(instanceIdsToDelete);
    }
  }

  @VisibleForTesting
  void handleNewDeploymentInternal(Collection<DeploymentSummary> newDeploymentSummaries,
      Collection<ServerlessInstance> activeInstancesInDB, AwsLambdaInfraStructureMapping infrastructureMapping,
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptedDataDetails) {
    if (isNotEmpty(newDeploymentSummaries)) {
      log.info("Handling new Aws Lambda Deployments with deployment Summary Ids : [{}]",
          newDeploymentSummaries.stream().map(DeploymentSummary::getUuid).collect(joining(",")));
      // functions to delete
      log.info("deleting active instances");
      deleteInstances(activeInstancesInDB);
      // functions to update, we delete all previous versions of that function
      // functions to add
      newDeploymentSummaries.stream()
          .filter(Objects::nonNull)
          .forEach(deploymentSummary
              -> handleNewDeploymentSummary(deploymentSummary, infrastructureMapping, awsConfig, encryptedDataDetails));
    }
  }
  @VisibleForTesting
  void handleNewDeploymentSummary(DeploymentSummary newDeploymentSummary,
      AwsLambdaInfraStructureMapping infrastructureMapping, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptedDataDetails) {
    try {
      final AwsLambdaDeploymentInfo deploymentInfo = (AwsLambdaDeploymentInfo) newDeploymentSummary.getDeploymentInfo();

      log.info("Performing sync for Aws Lambda Function: [{}], version: [{}], Deployment Summary UID =[{}] ",
          deploymentInfo.getFunctionName(), deploymentInfo.getVersion(), newDeploymentSummary.getUuid());

      final AwsLambdaInstanceInfo lambdaInstanceInfo =
          getLambdaInstanceInfo(deploymentInfo.getFunctionName(), deploymentInfo.getVersion(),
              new Date(newDeploymentSummary.getDeployedAt()), infrastructureMapping, awsConfig, encryptedDataDetails);

      if (lambdaInstanceInfo == null) {
        log.warn("Could not find Aws Lambda Function =[{}], version =[{}]. Skipping it",
            deploymentInfo.getFunctionName(), deploymentInfo.getVersion());
      } else {
        final ServerlessInstance serverlessInstance =
            buildInstanceForNewDeployment(infrastructureMapping, newDeploymentSummary, lambdaInstanceInfo);
        // save instance
        final ServerlessInstance savedServerlessInstance = serverlessInstanceService.save(serverlessInstance);
        log.info("Successfully Synced Aws Lambda Function: [{}], version: [{}]. Instance id = [{}] ",
            deploymentInfo.getFunctionName(), deploymentInfo.getVersion(), savedServerlessInstance.getUuid());
      }
    } catch (Exception e) {
      log.error(" Error handling new deployment for aws lambda function. skipping the sync", e);
    }
  }
  @VisibleForTesting
  ServerlessInstance buildInstanceForNewDeployment(AwsLambdaInfraStructureMapping awsLambdaInfraStructureMapping,
      DeploymentSummary newDeploymentSummary, AwsLambdaInstanceInfo lambdaInstanceInfo) {
    final AwsLambdaDeploymentInfo deploymentInfo = (AwsLambdaDeploymentInfo) newDeploymentSummary.getDeploymentInfo();

    // create instance key
    final AwsLambdaInstanceKey lambdaInstanceKey = AwsLambdaInstanceKey.builder()
                                                       .functionName(lambdaInstanceInfo.getFunctionName())
                                                       .functionVersion(lambdaInstanceInfo.getVersion())
                                                       .build();
    // create Instance
    final ServerlessInstanceBuilder instanceBuilder =
        buildServerlessInstance(awsLambdaInfraStructureMapping, newDeploymentSummary);

    return populateWithArtifactDetails(deploymentInfo.getArtifactId(), instanceBuilder)
        .lambdaInstanceKey(lambdaInstanceKey)
        .instanceInfo(lambdaInstanceInfo)
        .build();
  }

  @VisibleForTesting
  ServerlessInstanceBuilder populateWithArtifactDetails(String artifactId, ServerlessInstanceBuilder instanceBuilder) {
    final Artifact artifact = artifactService.get(artifactId);
    if (artifact != null) {
      instanceBuilder.lastArtifactId(artifact.getUuid())
          .lastArtifactName(artifact.getDisplayName())
          .lastArtifactStreamId(artifact.getArtifactStreamId())
          .lastArtifactSourceName(artifact.getArtifactSourceName())
          .lastArtifactBuildNum(artifact.getBuildNo());
    }
    return instanceBuilder;
  }

  @VisibleForTesting
  void syncInDBInstance(AwsLambdaInfraStructureMapping infrastructureMapping, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptedDataDetails, ServerlessInstance instanceToUpdate) {
    final String functionName = instanceToUpdate.getLambdaInstanceKey().getFunctionName();
    final String functionVersion = instanceToUpdate.getLambdaInstanceKey().getFunctionVersion();
    log.debug("Syncing existing instance id=[{}], function name=[{}], function version=[{}]",
        instanceToUpdate.getUuid(), functionName, functionVersion);
    try {
      final AwsLambdaInstanceInfo lambdaInstanceInfo = getLambdaInstanceInfo(functionName, functionVersion,
          new Date(instanceToUpdate.getLastDeployedAt()), infrastructureMapping, awsConfig, encryptedDataDetails);
      syncLambdaInstanceInDB(instanceToUpdate, lambdaInstanceInfo);

    } catch (NoDelegatesException ex) {
      log.warn("error while Syncing Aws Lambda Instance. skipping the sync for it", ex);
    } catch (Exception e) {
      log.info("error while Syncing Aws Lambda Instance. skipping the sync for it", e);
    }
  }

  @VisibleForTesting
  void syncInDBInstanceForPerpetualTask(
      ServerlessInstance instanceToUpdate, AwsLambdaDetailsMetricsResponse awsLambdaDetailsResponse) {
    final String functionName = instanceToUpdate.getLambdaInstanceKey().getFunctionName();
    final String functionVersion = instanceToUpdate.getLambdaInstanceKey().getFunctionVersion();
    log.debug("Syncing existing instance id=[{}], function name=[{}], function version=[{}]",
        instanceToUpdate.getUuid(), functionName, functionVersion);
    try {
      final AwsLambdaInstanceInfo lambdaInstanceInfo = createLambdaInstanceInfo(
          awsLambdaDetailsResponse.getLambdaDetails(), awsLambdaDetailsResponse.getInvocationCountList());
      syncLambdaInstanceInDB(instanceToUpdate, lambdaInstanceInfo);

    } catch (Exception e) {
      log.info("error while Syncing Aws Lambda Instance. skipping the sync for it", e);
    }
  }

  private void syncLambdaInstanceInDB(ServerlessInstance instanceToUpdate, AwsLambdaInstanceInfo lambdaInstanceInfo) {
    if (lambdaInstanceInfo == null) {
      handleFunctionNotExist(instanceToUpdate);
    } else {
      if (!somethingUpdated(lambdaInstanceInfo, instanceToUpdate.getInstanceInfo())) {
        log.info("Lambda details and Invocation count have not changed. Skipping update.");
      } else {
        handleNewUpdatesToInstance(instanceToUpdate, lambdaInstanceInfo);
      }
    }
  }

  @VisibleForTesting
  AwsLambdaInstanceInfo getLambdaInstanceInfo(String functionName, String version, Date lastDeployedAt,
      AwsLambdaInfraStructureMapping infrastructureMapping, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptedDataDetails) {
    log.info("Fetching details for Aws Lambda Function: [{}] , version =[{}]", functionName, version);

    final AwsLambdaDetails lambdaDetails =
        getFunctionDetails(infrastructureMapping, awsConfig, encryptedDataDetails, functionName, version);

    return getInvocationCountAndCreateLambdaInstanceInfo(
        functionName, lastDeployedAt, infrastructureMapping, awsConfig, encryptedDataDetails, lambdaDetails);
  }

  private AwsLambdaInstanceInfo getInvocationCountAndCreateLambdaInstanceInfo(String functionName, Date lastDeployedAt,
      AwsLambdaInfraStructureMapping infrastructureMapping, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptedDataDetails, AwsLambdaDetails lambdaDetails) {
    if (lambdaDetails == null) {
      return null;
    }
    log.info("Fetching Invocation count for Aws Lambda Function: [{}] ", functionName);
    List<InvocationCount> invocationCountList =
        prepareInvocationCountList(functionName, lastDeployedAt, INVOCATION_COUNT_KEY_LIST,
            infrastructureMapping.getAppId(), infrastructureMapping.getRegion(), awsConfig, encryptedDataDetails);

    return createLambdaInstanceInfo(lambdaDetails, invocationCountList);
  }

  @VisibleForTesting
  void handleFunctionNotExist(ServerlessInstance instanceOfDeletedFunction) {
    final String functionName = instanceOfDeletedFunction.getLambdaInstanceKey().getFunctionName();
    final String version = instanceOfDeletedFunction.getLambdaInstanceKey().getFunctionVersion();
    log.info("Could not find Aws Lambda Function =[{}], version =[{}]. Deleting instance Id =[{}] it", functionName,
        version, instanceOfDeletedFunction.getUuid());
    serverlessInstanceService.delete(Collections.singletonList(instanceOfDeletedFunction.getUuid()));
  }
  @VisibleForTesting
  ServerlessInstance handleNewUpdatesToInstance(ServerlessInstance oldInstance, AwsLambdaInstanceInfo newInstanceInfo) {
    log.info("Handling new updates to function");
    final ServerlessInstance newInstance = copyForUpdate(oldInstance, newInstanceInfo);
    log.info("Deleting instances with Id = [{}]", oldInstance.getUuid());
    serverlessInstanceService.delete(Collections.singletonList(oldInstance.getUuid()));
    final ServerlessInstance savedServerlessInstance = serverlessInstanceService.save(newInstance);
    log.info("Created new instance for  Lambda Function: [{}], version: [{}]. Instance id = [{}] ",
        newInstanceInfo.getFunctionName(), newInstanceInfo.getVersion(), savedServerlessInstance.getUuid());
    return savedServerlessInstance;
  }

  @VisibleForTesting
  boolean somethingUpdated(ServerlessInstanceInfo newInstanceInfo, ServerlessInstanceInfo oldInstanceInfo) {
    return !Objects.equals(oldInstanceInfo, newInstanceInfo);
  }

  private ServerlessInstance copyForUpdate(
      ServerlessInstance oldServerlessInstance, ServerlessInstanceInfo newInstanceInfo) {
    return oldServerlessInstance.toBuilder().uuid(generateUuid()).instanceInfo(newInstanceInfo).build();
  }

  @VisibleForTesting
  AwsLambdaInstanceInfo createLambdaInstanceInfo(
      AwsLambdaDetails lambdaDetails, List<InvocationCount> invocationCountList) {
    return AwsLambdaInstanceInfo.builder()
        .functionName(lambdaDetails.getFunctionName())
        .functionArn(lambdaDetails.getFunctionArn())
        .version(lambdaDetails.getVersion())
        .tags(MapUtils.emptyIfNull(lambdaDetails.getTags())
                  .entrySet()
                  .stream()
                  .map(entry -> Tag.builder().key(entry.getKey()).value(entry.getValue()).build())
                  .collect(toSet()))
        .aliases(ImmutableSet.copyOf(emptyIfNull(lambdaDetails.getAliases())))
        .invocationCountList(invocationCountList)
        .description(lambdaDetails.getDescription())
        .handler(lambdaDetails.getHandler())
        .runtime(lambdaDetails.getRuntime())
        .build();
  }

  @VisibleForTesting
  AwsLambdaDetails getFunctionDetails(AwsLambdaInfraStructureMapping infrastructureMapping, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptedDataDetails, String functionName, String version) {
    return awsLambdaHelperServiceManager.getFunctionDetails(AwsLambdaDetailsRequest.builder()
                                                                .awsConfig(awsConfig)
                                                                .encryptionDetails(encryptedDataDetails)
                                                                .region(infrastructureMapping.getRegion())
                                                                .functionName(functionName)
                                                                .qualifier(version)
                                                                .loadAliases(true)
                                                                .build());
  }

  @Override
  public void handleNewDeployment(
      List<DeploymentSummary> deploymentSummaries, boolean rollback, OnDemandRollbackInfo onDemandRollbackInfo) {
    log.info(" Handling  new deployment. New Deployment Summary Size =[{}], rollback =[{}]",
        emptyIfNull(deploymentSummaries).size(), rollback);
    if (!isNullOrEmpty(deploymentSummaries)) {
      final String appId = deploymentSummaries.iterator().next().getAppId();
      final String infraMappingId = deploymentSummaries.iterator().next().getInfraMappingId();
      syncInstancesInternal(appId, infraMappingId, deploymentSummaries, null, NEW_DEPLOYMENT);
    }
  }

  @Override
  public Optional<FeatureName> getFeatureFlagToStopIteratorBasedInstanceSync() {
    return Optional.of(STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AWS_LAMBDA_DEPLOYMENTS);
  }

  @VisibleForTesting
  ServerlessInstanceBuilder buildServerlessInstance(
      InfrastructureMapping infraMapping, DeploymentSummary deploymentSummary) {
    final String appId = infraMapping.getAppId();
    Application application = appService.get(appId);
    notNullCheck("Application is null for the given appId: " + appId, application);
    Environment environment = environmentService.get(appId, infraMapping.getEnvId(), false);
    notNullCheck("Environment is null for the given id: " + infraMapping.getEnvId(), environment);
    Service service = serviceResourceService.getWithDetails(appId, infraMapping.getServiceId());
    notNullCheck("Service is null for the given id: " + infraMapping.getServiceId(), service);
    String infraMappingType = infraMapping.getInfraMappingType();

    ServerlessInstanceBuilder builder = ServerlessInstance.builder()
                                            .uuid(generateUuid())
                                            .accountId(application.getAccountId())
                                            .appId(appId)
                                            .appName(application.getName())
                                            .envName(environment.getName())
                                            .envId(infraMapping.getEnvId())
                                            .envType(environment.getEnvironmentType())
                                            .computeProviderId(infraMapping.getComputeProviderSettingId())
                                            .computeProviderName(infraMapping.getComputeProviderName())
                                            .infraMappingId(infraMapping.getUuid())
                                            .infraMappingName(infraMapping.getName())
                                            .infraMappingType(infraMappingType)
                                            .serviceId(infraMapping.getServiceId())
                                            .serviceName(service.getName());
    builder.instanceType(getInstanceType(infraMappingType));

    if (deploymentSummary != null) {
      builder.lastDeployedAt(deploymentSummary.getDeployedAt())
          .lastArtifactSourceName(deploymentSummary.getArtifactSourceName())
          .lastArtifactBuildNum(deploymentSummary.getArtifactBuildNum())
          .lastDeployedByName(deploymentSummary.getDeployedByName())
          .lastDeployedById(deploymentSummary.getDeployedById())
          .lastPipelineExecutionName(deploymentSummary.getPipelineExecutionName())
          .lastArtifactId(deploymentSummary.getArtifactId())
          .lastWorkflowExecutionId(deploymentSummary.getWorkflowExecutionId())
          .lastWorkflowExecutionName(deploymentSummary.getWorkflowExecutionName())
          .lastPipelineExecutionId(deploymentSummary.getPipelineExecutionId())
          .lastPipelineExecutionName(deploymentSummary.getPipelineExecutionName())
          .lastArtifactId(deploymentSummary.getArtifactId())
          .lastArtifactName(deploymentSummary.getArtifactName())
          .lastArtifactStreamId(deploymentSummary.getArtifactStreamId());
    }
    return builder;
  }

  ServerlessInstanceType getInstanceType(String infraMappingType) {
    if (InfrastructureMappingType.AWS_AWS_LAMBDA.name().equals(infraMappingType)) {
      return ServerlessInstanceType.AWS_LAMBDA;
    } else {
      throw new GeneralException("Unsupported infraMapping type:" + infraMappingType);
    }
  }

  @Override
  public Optional<List<DeploymentInfo>> getDeploymentInfo(PhaseExecutionData phaseExecutionData,
      PhaseStepExecutionData phaseStepExecutionData, WorkflowExecution workflowExecution,
      InfrastructureMapping infrastructureMapping, String stateExecutionInstanceId, Artifact artifact) {
    final PhaseStepExecutionSummary phaseStepExecutionSummary = phaseStepExecutionData.getPhaseStepExecutionSummary();

    if (phaseStepExecutionSummary == null) {
      return Optional.empty();
    }
    final CommandStepExecutionSummary commandStepExecutionSummary =
        getStepExecutionSummary(phaseStepExecutionSummary)
            .orElseThrow(()
                             -> new GeneralException("Command step execution summary null for workflow: "
                                 + workflowExecution.normalizedName()));

    final List<String> aliases = emptyIfNull(commandStepExecutionSummary.getAliases());
    final List<Tag> tags = emptyIfNull(commandStepExecutionSummary.getTags());
    final List<FunctionMeta> lambdaFunctionMetaList = commandStepExecutionSummary.getLambdaFunctionMetaList();

    if (isNullOrEmpty(lambdaFunctionMetaList)) {
      log.warn("Function Metadata not found for workflow:[{}] Can't create deployment event",
          workflowExecution.normalizedName());
      return Optional.empty();
    }
    return Optional.of(lambdaFunctionMetaList.stream()
                           .map(functionMeta
                               -> AwsLambdaDeploymentInfo.builder()
                                      .aliases(aliases)
                                      .tags(tags)
                                      .functionArn(functionMeta.getFunctionArn())
                                      .functionName(functionMeta.getFunctionName())
                                      .version(functionMeta.getVersion())
                                      .artifactId(commandStepExecutionSummary.getArtifactId())
                                      .build())
                           .collect(toList()));
  }

  Optional<CommandStepExecutionSummary> getStepExecutionSummary(
      @NotNull PhaseStepExecutionSummary phaseStepExecutionSummary) {
    return phaseStepExecutionSummary.getStepExecutionSummaryList()
        .stream()
        .filter(stepExecutionSummary -> stepExecutionSummary instanceof CommandStepExecutionSummary)
        .findFirst()
        .map(stepExecutionSummary -> (CommandStepExecutionSummary) stepExecutionSummary);
  }

  @Override
  public DeploymentKey generateDeploymentKey(DeploymentInfo deploymentInfo) {
    final AwsLambdaDeploymentInfo awsLambdaDeploymentInfo = (AwsLambdaDeploymentInfo) deploymentInfo;
    return AwsLambdaDeploymentKey.builder()
        .functionName(awsLambdaDeploymentInfo.getFunctionName())
        .version(awsLambdaDeploymentInfo.getVersion())
        .build();
  }

  @Override
  protected void setDeploymentKey(DeploymentSummary deploymentSummary, DeploymentKey deploymentKey) {
    if (deploymentKey instanceof AwsLambdaDeploymentKey) {
      deploymentSummary.setAwsLambdaDeploymentKey((AwsLambdaDeploymentKey) deploymentKey);
    } else {
      throw new GeneralException("Invalid deploymentKey passed for AwsLambdaDeploymentKey" + deploymentKey);
    }
  }
  @VisibleForTesting
  AwsCloudWatchStatisticsResponse fetchInvocationCountFromCloudWatch(String functionName, Date startDate, Date endDate,
      String appId, String region, AwsConfig awsConfig, List<EncryptedDataDetail> encryptedDataDetails) {
    final SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                                .accountId(awsConfig.getAccountId())
                                                .appId(appId)
                                                .timeout(DEFAULT_SYNC_CALL_TIMEOUT * 3)
                                                .build();

    final AwsCloudWatchStatisticsRequest request =
        AwsCloudWatchStatisticsRequest.builder()
            .namespace("AWS/Lambda")
            .dimensions(singletonList(new Dimension().withName("FunctionName").withValue(functionName)))
            .metricName("Invocations")
            .statistics(singletonList("Sum"))
            .startTime(startDate)
            .endTime(endDate)
            .period(smallestMultipleOf60GreaterEqualThan(secondsBetweenDates(startDate, endDate)))
            .region(region)
            .awsConfig(awsConfig)
            .encryptionDetails(encryptedDataDetails)
            .build();

    return delegateProxyFactory.getV2(AwsCloudWatchHelperServiceDelegate.class, syncTaskContext)
        .getMetricStatistics(request);
  }

  @VisibleForTesting
  int secondsBetweenDates(Date from, Date to) {
    final DateTime fromDateTime = new DateTime(from);
    final DateTime toDateTime = new DateTime(to);
    return secondsBetween(fromDateTime, toDateTime).getSeconds();
  }

  @VisibleForTesting
  int smallestMultipleOf60GreaterEqualThan(int n) {
    if (n == 0) {
      return 60;
    }
    if (n % 60 == 0) {
      return n;
    }
    return ((n / 60) + 1) * 60;
  }
  @VisibleForTesting
  Date getStartDate(Date endDate, Date deployedAt, InvocationCountKey invocationCountKey) {
    switch (invocationCountKey) {
      case LAST_30_DAYS:
        return DateUtils.addDays(endDate, -30);
      case SINCE_LAST_DEPLOYED:
        return deployedAt;
      default:
        throw new InvalidArgumentsException(Pair.of("invocationCountKey", invocationCountKey.name()));
    }
  }

  @VisibleForTesting
  @NotNull
  List<InvocationCount> prepareInvocationCountList(String functionName, Date deployedAt,
      @NotNull List<InvocationCountKey> invocationCountKeyList, String appId, String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptedDataDetails) {
    return invocationCountKeyList.stream()
        .map(invocationCountKey
            -> getInvocationCountForKey(
                functionName, deployedAt, invocationCountKey, appId, region, awsConfig, encryptedDataDetails))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(toList());
  }

  @NotNull
  @VisibleForTesting
  Optional<InvocationCount> getInvocationCountForKey(String functionName, Date deployedAt,
      InvocationCountKey invocationCountKey, String appId, String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptedDataDetails) {
    try {
      final Date now = new Date();
      final Date startDate = getStartDate(now, deployedAt, invocationCountKey);
      log.info(
          "Fetching Invocation count for AWS Lambda Function =[{}], InvocationCountKey =[{}], start Date =[{}], End Date=[{}]",
          functionName, invocationCountKey, startDate, now);

      final AwsCloudWatchStatisticsResponse invocationCountResponse = fetchInvocationCountFromCloudWatch(
          functionName, startDate, now, appId, region, awsConfig, encryptedDataDetails);
      final Optional<Double> invocationCountOpt =
          emptyIfNull(invocationCountResponse.getDatapoints()).stream().map(Datapoint::getSum).reduce(Double::sum);
      log.info("Invocation Count received =[{}]", invocationCountOpt);
      return invocationCountOpt.map(invocationCount
          -> InvocationCount.builder()
                 .key(invocationCountKey)
                 .count(invocationCount.longValue())
                 .from(startDate.toInstant())
                 .to(now.toInstant())
                 .build());

    } catch (Exception e) {
      throw new UnexpectedException("Error while fetching Invocation count", e);
    }
  }

  @Override
  public Optional<FeatureName> getFeatureFlagToEnablePerpetualTaskForInstanceSync() {
    return Optional.of(MOVE_AWS_LAMBDA_INSTANCE_SYNC_TO_PERPETUAL_TASK);
  }

  @Override
  public InstanceSyncPerpetualTaskCreator getInstanceSyncPerpetualTaskCreator() {
    return perpetualTaskCreator;
  }

  @Override
  public void processInstanceSyncResponseFromPerpetualTask(
      InfrastructureMapping infrastructureMapping, DelegateResponseData response) {
    syncInstancesInternal(infrastructureMapping.getAppId(), infrastructureMapping.getUuid(), emptyList(),
        (AwsLambdaDetailsMetricsResponse) response, PERPETUAL_TASK);
  }

  @Override
  public Status getStatus(InfrastructureMapping infrastructureMapping, DelegateResponseData response) {
    AwsLambdaDetailsMetricsResponse awsLambdaDetailsResponse = (AwsLambdaDetailsMetricsResponse) response;
    boolean success = awsLambdaDetailsResponse.getExecutionStatus() == ExecutionStatus.SUCCESS;
    boolean deleteTask = success && isNull(awsLambdaDetailsResponse.getLambdaDetails());
    String errorMessage = success ? null : awsLambdaDetailsResponse.getErrorMessage();

    return Status.builder().retryable(!deleteTask).errorMessage(errorMessage).success(success).build();
  }
}
