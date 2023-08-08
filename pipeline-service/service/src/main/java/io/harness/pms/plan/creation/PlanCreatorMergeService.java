/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.creation;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.async.plan.PlanNotifyEventConsumer.PMS_PLAN_CREATION;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.YamlException;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.logging.AutoLogContext;
import io.harness.pms.async.plan.PartialPlanResponseCallback;
import io.harness.pms.contracts.plan.CreatePartialPlanEvent;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.ErrorResponse;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.PartialPlanResponse;
import io.harness.pms.contracts.plan.PlanCreationBlobRequest;
import io.harness.pms.contracts.plan.PlanCreationBlobResponse;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.plan.PlanCreationResponse;
import io.harness.pms.events.base.PmsEventCategory;
import io.harness.pms.exception.PmsExceptionUtils;
import io.harness.pms.plan.creation.validator.PlanCreationValidator;
import io.harness.pms.sdk.PmsSdkHelper;
import io.harness.pms.utils.CompletableFutures;
import io.harness.pms.utils.PmsGrpcClientUtils;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class PlanCreatorMergeService {
  private static final int MAX_DEPTH = 10;

  private final Executor executor;

  private final PmsSdkHelper pmsSdkHelper;
  private final WaitNotifyEngine waitNotifyEngine;
  PmsEventSender pmsEventSender;
  PlanCreationValidator planCreationValidator;
  private final Integer planCreatorMergeServiceDependencyBatch;
  private final PmsFeatureFlagService pmsFeatureFlagService;
  private final KryoSerializer kryoSerializer;

  @Inject
  public PlanCreatorMergeService(PmsSdkHelper pmsSdkHelper, PmsEventSender pmsEventSender,
      WaitNotifyEngine waitNotifyEngine, PlanCreationValidator planCreationValidator,
      @Named("PlanCreatorMergeExecutorService") Executor executor,
      @Named("planCreatorMergeServiceDependencyBatch") Integer planCreatorMergeServiceDependencyBatch,
      PmsFeatureFlagService pmsFeatureFlagService, KryoSerializer kryoSerializer) {
    this.pmsSdkHelper = pmsSdkHelper;
    this.pmsEventSender = pmsEventSender;
    this.waitNotifyEngine = waitNotifyEngine;
    this.planCreationValidator = planCreationValidator;
    this.executor = executor;
    this.planCreatorMergeServiceDependencyBatch = planCreatorMergeServiceDependencyBatch;
    this.pmsFeatureFlagService = pmsFeatureFlagService;
    this.kryoSerializer = kryoSerializer;
  }

  public String getPublisher() {
    return PMS_PLAN_CREATION;
  }

  // This is not used currently for future considerations which uses redis/waitNotify for planCreation
  public void createPlanV2(String accountId, String orgIdentifier, String projectIdentifier, String planUuid,
      ExecutionMetadata metadata, PlanExecutionMetadata planExecutionMetadata) throws IOException {
    log.info("Starting plan creation");
    YamlField pipelineField = YamlUtils.extractPipelineField(planExecutionMetadata.getProcessedYaml());
    String notifyId = generateUuid();
    Dependencies dependencies =
        Dependencies.newBuilder()
            .setYaml(planExecutionMetadata.getProcessedYaml())
            .putDependencies(pipelineField.getNode().getUuid(), pipelineField.getNode().getYamlPath())
            .build();
    pmsEventSender.sendEvent(CreatePartialPlanEvent.newBuilder()
                                 .setDeps(dependencies)
                                 .putAllContext(createInitialPlanCreationContext(
                                     accountId, orgIdentifier, projectIdentifier, metadata, planExecutionMetadata))
                                 .setNotifyId(notifyId)
                                 .build()
                                 .toByteString(),
        new HashMap<>(), PmsEventCategory.CREATE_PARTIAL_PLAN, "pms");
    waitNotifyEngine.waitForAllOnInList(getPublisher(),
        PartialPlanResponseCallback.builder()
            .planUuid(planUuid)
            .depth(0)
            .finalResponse(PartialPlanResponse.newBuilder()
                               .setBlobResponse(PlanCreationBlobResponse.newBuilder().setDeps(dependencies).build())
                               .build())
            .build(),
        Collections.singletonList(notifyId), Duration.ofMinutes(2));
  }

  public PlanCreationBlobResponse createPlanVersioned(String accountId, String orgIdentifier, String projectIdentifier,
      String version, ExecutionMetadata metadata, PlanExecutionMetadata planExecutionMetadata) throws IOException {
    try (AutoLogContext ignore =
             PlanCreatorUtils.autoLogContext(metadata, accountId, orgIdentifier, projectIdentifier)) {
      log.info("[PMS_PlanCreatorMergeService] Starting plan creation");
      Map<String, PlanCreatorServiceInfo> services = pmsSdkHelper.getServices();

      YamlField pipelineField;
      switch (version) {
        case PipelineVersion.V1:
          pipelineField = YamlUtils.readTree(planExecutionMetadata.getProcessedYaml());
          break;
        case PipelineVersion.V0:
          pipelineField = YamlUtils.extractPipelineField(planExecutionMetadata.getProcessedYaml());
          break;
        default:
          throw new InvalidYamlException("Invalid version");
      }

      if (pipelineField.getNode().getUuid() == null) {
        throw new YamlException("Processed pipeline yaml does not have uuid for the pipeline field");
      }

      Dependencies dependencies =
          Dependencies.newBuilder()
              .setYaml(planExecutionMetadata.getProcessedYaml())
              .putDependencies(pipelineField.getNode().getUuid(), pipelineField.getNode().getYamlPath())
              .build();

      PlanCreationBlobResponse finalResponse = createPlanForDependenciesRecursive(
          accountId, orgIdentifier, projectIdentifier, services, dependencies, metadata, planExecutionMetadata);
      planCreationValidator.validate(accountId, finalResponse);
      planExecutionMetadata.setExecutionInputConfigured(finalResponse.getNodesMap().values().stream().anyMatch(
          o -> !EmptyPredicate.isEmpty(o.getExecutionInputTemplate())));
      return finalResponse;
    }
  }

  @VisibleForTesting
  Map<String, PlanCreationContextValue> createInitialPlanCreationContext(String accountId, String orgIdentifier,
      String projectIdentifier, ExecutionMetadata metadata, PlanExecutionMetadata planExecutionMetadata) {
    String pipelineVersion = metadata != null && EmptyPredicate.isNotEmpty(metadata.getHarnessVersion())
        ? metadata.getHarnessVersion()
        : PipelineVersion.V0;
    // TODO(BRIJESH): Remove the isExecutionInputEnabled field from PlanCreationContextValue. Once the change to remove
    // its usages is deployed in all services.
    Map<String, PlanCreationContextValue> planCreationContextMap = new HashMap<>();
    PlanCreationContextValue.Builder builder = PlanCreationContextValue.newBuilder()
                                                   .setAccountIdentifier(accountId)
                                                   .setOrgIdentifier(orgIdentifier)
                                                   .setProjectIdentifier(projectIdentifier)
                                                   .setIsExecutionInputEnabled(true);
    if (metadata != null) {
      builder.setMetadata(metadata);
    }
    if (planExecutionMetadata != null) {
      if (planExecutionMetadata.getTriggerPayload() != null) {
        builder.setTriggerPayload(planExecutionMetadata.getTriggerPayload());
      }
      Dependency globalDependency = PlanCreatorUtils.createGlobalDependency(
          kryoSerializer, pipelineVersion, planExecutionMetadata.getProcessedYaml());
      if (globalDependency != null) {
        builder.setGlobalDependency(globalDependency);
      }
    }
    planCreationContextMap.put("metadata", builder.build());
    return planCreationContextMap;
  }

  PlanCreationBlobResponse createPlanForDependenciesRecursive(String accountId, String orgIdentifier,
      String projectIdentifier, Map<String, PlanCreatorServiceInfo> services, Dependencies initialDependencies,
      ExecutionMetadata metadata, PlanExecutionMetadata planExecutionMetadata) {
    PlanCreationBlobResponse.Builder finalResponseBuilder =
        PlanCreationBlobResponse.newBuilder().setDeps(initialDependencies);
    if (EmptyPredicate.isEmpty(services) || EmptyPredicate.isEmpty(initialDependencies.getDependenciesMap())) {
      return finalResponseBuilder.build();
    }

    finalResponseBuilder.putAllContext(
        createInitialPlanCreationContext(accountId, orgIdentifier, projectIdentifier, metadata, planExecutionMetadata));

    try {
      for (int i = 0; i < MAX_DEPTH && EmptyPredicate.isNotEmpty(finalResponseBuilder.getDeps().getDependenciesMap());
           i++) {
        String version = metadata.getHarnessVersion();
        YamlField fullYamlField = YamlUtils.readTree(finalResponseBuilder.getDeps().getYaml());
        PlanCreationBlobResponse currIterationResponse =
            createPlanForDependencies(services, finalResponseBuilder, fullYamlField, version);
        PlanCreationBlobResponseUtils.addNodes(finalResponseBuilder, currIterationResponse.getNodesMap());
        PlanCreationBlobResponseUtils.mergeStartingNodeId(
            finalResponseBuilder, currIterationResponse.getStartingNodeId());
        PlanCreationBlobResponseUtils.mergeLayoutNodeInfo(finalResponseBuilder, currIterationResponse);
        PlanCreationBlobResponseUtils.mergePreservedNodesInRollbackMode(finalResponseBuilder, currIterationResponse);
        PlanCreationBlobResponseUtils.mergeServiceAffinityMap(finalResponseBuilder, currIterationResponse);
        if (EmptyPredicate.isNotEmpty(finalResponseBuilder.getDeps().getDependenciesMap())) {
          throw new InvalidRequestException(
              PmsExceptionUtils.getUnresolvedDependencyPathsErrorMessage(finalResponseBuilder.getDeps()));
        }
        PlanCreationBlobResponseUtils.mergeContext(finalResponseBuilder, currIterationResponse.getContextMap());
        PlanCreationBlobResponseUtils.addDependenciesV2(finalResponseBuilder, currIterationResponse);
      }
    } catch (IOException e) {
      throw new UnexpectedException(e.getMessage(), e);
    }

    return finalResponseBuilder.build();
  }

  private PlanCreationBlobResponse createPlanForDependencies(Map<String, PlanCreatorServiceInfo> services,
      PlanCreationBlobResponse.Builder responseBuilder, YamlField fullYamlField, String harnessVersion) {
    PlanCreationBlobResponse.Builder currIterationResponseBuilder = PlanCreationBlobResponse.newBuilder();
    CompletableFutures<PlanCreationResponse> completableFutures = new CompletableFutures<>(executor);
    PlanCreationContextValue metadata = responseBuilder.getContextMap().get("metadata");

    try (AutoLogContext ignore = PlanCreatorUtils.autoLogContext(metadata.getMetadata(),
             metadata.getAccountIdentifier(), metadata.getOrgIdentifier(), metadata.getProjectIdentifier())) {
      long start = System.currentTimeMillis();
      Map<Map.Entry<String, PlanCreatorServiceInfo>, List<Map.Entry<String, String>>> serviceToDependencyMap =
          new HashMap<>();
      getServiceToDependenciesMap(services, responseBuilder, fullYamlField, serviceToDependencyMap, harnessVersion);

      // Sending batch dependency requests for a single service in a async fashion.
      executeCreatePlanInBatchDependency(responseBuilder, completableFutures, serviceToDependencyMap);

      // Collecting results for all completable futures at one go, thus it will wait till all dependencies are resolved.
      List<ErrorResponse> errorResponses;
      try {
        List<PlanCreationResponse> planCreationResponses = completableFutures.allOf().get(5, TimeUnit.MINUTES);
        errorResponses = planCreationResponses.stream()
                             .filter(resp -> resp.getResponseCase() == PlanCreationResponse.ResponseCase.ERRORRESPONSE)
                             .map(PlanCreationResponse::getErrorResponse)
                             .collect(Collectors.toList());
        if (EmptyPredicate.isEmpty(errorResponses)) {
          planCreationResponses.forEach(
              resp -> PlanCreationBlobResponseUtils.merge(currIterationResponseBuilder, resp.getBlobResponse()));
        }
      } catch (Exception ex) {
        throw new UnexpectedException("Error fetching plan creation response from service", ex);
      } finally {
        log.info("[PMS_PlanCreatorMergeService_Time] Sdk plan creators done took {}ms for initial dependencies size {}",
            System.currentTimeMillis() - start, responseBuilder.getDeps().getDependenciesMap().size());
      }
      PmsExceptionUtils.checkAndThrowPlanCreatorException(errorResponses);
      return currIterationResponseBuilder.build();
    }
  }

  // Sending all dependencies in batch manner in async fashion
  private void executeCreatePlanInBatchDependency(PlanCreationBlobResponse.Builder responseBuilder,
      CompletableFutures<PlanCreationResponse> completableFutures,
      Map<Map.Entry<String, PlanCreatorServiceInfo>, List<Map.Entry<String, String>>> serviceToDependencyMap) {
    for (Map.Entry<Map.Entry<String, PlanCreatorServiceInfo>, List<Map.Entry<String, String>>> serviceDependencyEntry :
        serviceToDependencyMap.entrySet()) {
      Map.Entry<String, PlanCreatorServiceInfo> serviceInfo = serviceDependencyEntry.getKey();
      List<Map.Entry<String, String>> dependencyList = serviceDependencyEntry.getValue();
      Map<String, String> dependencyBatch = new HashMap<>();
      for (Map.Entry<String, String> dependency : dependencyList) {
        dependencyBatch.put(dependency.getKey(), dependency.getValue());
        if (dependencyBatch.size() >= planCreatorMergeServiceDependencyBatch) {
          Dependencies batchDependency = PmsSdkHelper.createBatchDependency(responseBuilder.getDeps(), dependencyBatch);
          Map<String, String> batchServiceAffinityMap = PmsSdkHelper.createBatchServiceAffinityMap(
              dependencyBatch.keySet(), responseBuilder.getServiceAffinityMap());
          executeDependenciesAsync(completableFutures, serviceInfo, batchDependency, batchServiceAffinityMap,
              responseBuilder.getContextMap());
          dependencyBatch = new HashMap<>();
        }
      }

      // call completable future for leftover batch
      if (dependencyBatch.size() > 0) {
        Dependencies batchDependency = PmsSdkHelper.createBatchDependency(responseBuilder.getDeps(), dependencyBatch);
        Map<String, String> batchServiceAffinityMap = PmsSdkHelper.createBatchServiceAffinityMap(
            dependencyBatch.keySet(), responseBuilder.getServiceAffinityMap());
        executeDependenciesAsync(
            completableFutures, serviceInfo, batchDependency, batchServiceAffinityMap, responseBuilder.getContextMap());
      }
    }
  }

  // Collecting which dependencies are supported with which service as a map.
  private void getServiceToDependenciesMap(Map<String, PlanCreatorServiceInfo> services,
      PlanCreationBlobResponse.Builder responseBuilder, YamlField fullYamlField,
      Map<Map.Entry<String, PlanCreatorServiceInfo>, List<Map.Entry<String, String>>> serviceToDependencyMap,
      String harnessVersion) {
    // Initializing the responseMap
    for (Map.Entry<String, PlanCreatorServiceInfo> serviceEntry : services.entrySet()) {
      serviceToDependencyMap.put(serviceEntry, new LinkedList<>());
    }

    addDependencyToServiceDependencyMapBasedOnPriority(
        services, responseBuilder, fullYamlField, serviceToDependencyMap, harnessVersion);
  }

  private void addDependencyToServiceDependencyMapBasedOnPriority(Map<String, PlanCreatorServiceInfo> services,
      PlanCreationBlobResponse.Builder responseBuilder, YamlField fullYamlField,
      Map<Map.Entry<String, PlanCreatorServiceInfo>, List<Map.Entry<String, String>>> serviceToDependencyMap,
      String harnessVersion) {
    for (Map.Entry<String, String> dependencyEntry : responseBuilder.getDeps().getDependenciesMap().entrySet()) {
      // Always first check  -
      // 1. Affinity service
      // 2. pipeline-service dependencies
      Map.Entry<String, PlanCreatorServiceInfo> pmsPlanCreatorService =
          services.entrySet()
              .stream()
              .filter(PmsSdkHelper::isPipelineService)
              .findFirst()
              .orElseThrow(
                  () -> new InvalidRequestException("Pipeline Service service provider information is missing."));

      String affinityService =
          PmsSdkHelper.getServiceAffinityForGivenDependency(responseBuilder.getServiceAffinityMap(), dependencyEntry);
      Map.Entry<String, PlanCreatorServiceInfo> affinityServicePlanCreatorService =
          services.entrySet()
              .stream()
              .filter(s -> PmsSdkHelper.getServiceForGivenAffinity(s, affinityService))
              .findFirst()
              .orElse(null);

      if (PmsSdkHelper.checkIfGivenServiceSupportsPath(
              affinityServicePlanCreatorService, dependencyEntry, fullYamlField, harnessVersion)) {
        serviceToDependencyMap.get(affinityServicePlanCreatorService).add(dependencyEntry);
      } else if (PmsSdkHelper.checkIfGivenServiceSupportsPath(
                     pmsPlanCreatorService, dependencyEntry, fullYamlField, harnessVersion)) {
        serviceToDependencyMap.get(pmsPlanCreatorService).add(dependencyEntry);
      } else {
        for (Map.Entry<String, PlanCreatorServiceInfo> serviceInfoEntry : services.entrySet()) {
          if (PmsSdkHelper.isPipelineService(serviceInfoEntry)) {
            continue;
          }
          if (PmsSdkHelper.checkIfGivenServiceSupportsPath(
                  serviceInfoEntry, dependencyEntry, fullYamlField, harnessVersion)) {
            serviceToDependencyMap.get(serviceInfoEntry).add(dependencyEntry);
          }
        }
      }
    }
  }

  // Sending batch dependency requests for a single service in a async fashion.
  private void executeDependenciesAsync(CompletableFutures<PlanCreationResponse> completableFutures,
      Map.Entry<String, PlanCreatorServiceInfo> serviceInfo, Dependencies batchDependency,
      Map<String, String> batchServiceAffinityMap, Map<String, PlanCreationContextValue> contextMap) {
    PlanCreationContextValue metadata = contextMap.get("metadata");
    completableFutures.supplyAsync(() -> {
      try (AutoLogContext ignore = PlanCreatorUtils.autoLogContext(metadata.getMetadata(),
               metadata.getAccountIdentifier(), metadata.getOrgIdentifier(), metadata.getProjectIdentifier())) {
        try {
          return PmsGrpcClientUtils.retryAndProcessException(serviceInfo.getValue().getPlanCreationClient()::createPlan,
              PlanCreationBlobRequest.newBuilder()
                  .setDeps(batchDependency)
                  .putAllContext(contextMap)
                  .putAllServiceAffinity(batchServiceAffinityMap)
                  .build());
        } catch (StatusRuntimeException ex) {
          log.error(
              String.format("Error connecting with service: [%s]. Is this service Running?", serviceInfo.getKey()), ex);
          return PlanCreationResponse.newBuilder()
              .setErrorResponse(
                  ErrorResponse.newBuilder()
                      .addMessages(String.format("Error connecting with service: [%s]", serviceInfo.getKey()))
                      .build())
              .build();
        }
      }
    });
  }
}
