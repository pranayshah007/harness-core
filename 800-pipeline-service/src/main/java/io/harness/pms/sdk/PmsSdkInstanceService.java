package io.harness.pms.sdk;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.data.mongodb.core.query.Update.update;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.pms.contracts.plan.InitializeSdkRequest;
import io.harness.pms.contracts.plan.InitializeSdkResponse;
import io.harness.pms.contracts.plan.PmsServiceGrpc.PmsServiceImplBase;
import io.harness.pms.contracts.plan.Types;
import io.harness.pms.contracts.steps.SdkStep;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.exception.InitializeSdkException;
import io.harness.pms.pipeline.StepPalleteInfo;
import io.harness.pms.pipeline.service.yamlschema.SchemaFetcher;
import io.harness.pms.sdk.PmsSdkInstance.PmsSdkInstanceKeys;
import io.harness.repositories.sdk.PmsSdkInstanceRepository;
import io.harness.springdata.TransactionHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
@Singleton
public class PmsSdkInstanceService extends PmsServiceImplBase {
  private static final String LOCK_NAME_PREFIX = "PmsSdkInstanceService-";
  private final PmsSdkInstanceRepository pmsSdkInstanceRepository;
  private final MongoTemplate mongoTemplate;
  private final PersistentLocker persistentLocker;
  private final SchemaFetcher schemaFetcher;
  Cache<String, PmsSdkInstance> instanceCache;
  TransactionHelper transactionHelper;
  boolean shouldUseInstanceCache;

  @Inject
  public PmsSdkInstanceService(PmsSdkInstanceRepository pmsSdkInstanceRepository, MongoTemplate mongoTemplate,
      PersistentLocker persistentLocker, SchemaFetcher schemaFetcher,
      @Named("pmsSdkInstanceCache") Cache<String, PmsSdkInstance> instanceCache,
      @Named("shouldUseInstanceCache") boolean shouldUseInstanceCache, TransactionHelper transactionHelper) {
    this.pmsSdkInstanceRepository = pmsSdkInstanceRepository;
    this.mongoTemplate = mongoTemplate;
    this.persistentLocker = persistentLocker;
    this.schemaFetcher = schemaFetcher;
    this.instanceCache = instanceCache;
    this.shouldUseInstanceCache = shouldUseInstanceCache;
    this.transactionHelper = transactionHelper;
  }

  @Override
  public void initializeSdk(InitializeSdkRequest request, StreamObserver<InitializeSdkResponse> responseObserver) {
    if (EmptyPredicate.isEmpty(request.getName())) {
      throw new InvalidRequestException("Name is empty");
    }

    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireLock(LOCK_NAME_PREFIX + request.getName(), Duration.ofMinutes(2))) {
      if (lock == null) {
        throw new InitializeSdkException("Could not acquire lock");
      }
      saveSdkInstance(request);

      schemaFetcher.invalidateCache(ModuleType.fromString(request.getName()));
    }
    responseObserver.onNext(InitializeSdkResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  @VisibleForTesting
  protected void saveSdkInstance(InitializeSdkRequest request) {
    Map<String, Set<String>> supportedTypes = new HashMap<>();
    if (EmptyPredicate.isNotEmpty(request.getSupportedTypesMap())) {
      for (Map.Entry<String, Types> entry : request.getSupportedTypesMap().entrySet()) {
        if (EmptyPredicate.isEmpty(entry.getKey()) || EmptyPredicate.isEmpty(entry.getValue().getTypesList())) {
          continue;
        }
        supportedTypes.put(entry.getKey(),
            entry.getValue().getTypesList().stream().filter(EmptyPredicate::isNotEmpty).collect(Collectors.toSet()));
      }
    }

    Query query = query(where(PmsSdkInstanceKeys.name).is(request.getName()));
    Update update =
        update(PmsSdkInstanceKeys.supportedTypes, supportedTypes)
            .set(PmsSdkInstanceKeys.supportedSdkSteps, request.getSupportedStepsList())
            .set(PmsSdkInstanceKeys.interruptConsumerConfig, request.getInterruptConsumerConfig())
            .set(PmsSdkInstanceKeys.staticAliases, request.getStaticAliasesMap())
            .set(PmsSdkInstanceKeys.sdkFunctors, request.getSdkFunctorsList())
            .set(PmsSdkInstanceKeys.expandableFields, request.getExpandableFieldsList())
            .set(PmsSdkInstanceKeys.orchestrationEventConsumerConfig, request.getOrchestrationEventConsumerConfig())
            .set(PmsSdkInstanceKeys.active, true)
            .set(PmsSdkInstanceKeys.sdkModuleInfo, request.getSdkModuleInfo())
            .set(PmsSdkInstanceKeys.lastUpdatedAt, System.currentTimeMillis())
            .set(PmsSdkInstanceKeys.facilitatorEventConsumerConfig, request.getFacilitatorEventConsumerConfig())
            .set(PmsSdkInstanceKeys.nodeStartEventConsumerConfig, request.getNodeStartEventConsumerConfig())
            .set(PmsSdkInstanceKeys.progressEventConsumerConfig, request.getProgressEventConsumerConfig())
            .set(PmsSdkInstanceKeys.nodeAdviseEventConsumerConfig, request.getNodeAdviseEventConsumerConfig())
            .set(PmsSdkInstanceKeys.nodeResumeEventConsumerConfig, request.getNodeResumeEventConsumerConfig())
            .set(PmsSdkInstanceKeys.startPlanCreationEventConsumerConfig, request.getPlanCreationEventConsumerConfig());
    transactionHelper.performTransaction(() -> {
      PmsSdkInstance instance = mongoTemplate.findAndModify(
          query, update, new FindAndModifyOptions().upsert(true).returnNew(true), PmsSdkInstance.class);
      if (instance != null) {
        instanceCache.put(request.getName(), instance);
      }
      return null;
    });
  }

  public Map<String, Map<String, Set<String>>> getInstanceNameToSupportedTypes() {
    Map<String, Map<String, Set<String>>> instances = new HashMap<>();
    Map<String, PmsSdkInstance> cacheValueMap = getSdkInstanceCacheValue();
    for (Map.Entry<String, PmsSdkInstance> entry : cacheValueMap.entrySet()) {
      instances.put(entry.getKey(), entry.getValue().getSupportedTypes());
    }
    return instances;
  }

  public Map<String, StepPalleteInfo> getModuleNameToStepPalleteInfo() {
    Map<String, StepPalleteInfo> instances = new HashMap<>();
    Map<String, PmsSdkInstance> cacheValueMap = getSdkInstanceCacheValue();
    for (Map.Entry<String, PmsSdkInstance> entry : cacheValueMap.entrySet()) {
      List<StepInfo> stepTypes = new ArrayList<>();
      for (SdkStep sdkStep : entry.getValue().getSupportedSdkSteps()) {
        if (!sdkStep.getIsPartOfStepPallete()) {
          continue;
        }
        stepTypes.add(sdkStep.getStepInfo());
      }
      instances.put(entry.getKey(),
          StepPalleteInfo.builder()
              .moduleName(entry.getValue().getSdkModuleInfo().getDisplayName())
              .stepTypes(stepTypes)
              .build());
    }
    return instances;
  }

  public Map<String, Set<SdkStep>> getSdkSteps() {
    Map<String, PmsSdkInstance> sdkInstanceCacheValues = getSdkInstanceCacheValue();
    Map<String, Set<SdkStep>> cachedSdkSteps = new HashMap<>();
    for (String key : sdkInstanceCacheValues.keySet()) {
      cachedSdkSteps.put(key, new HashSet<>(sdkInstanceCacheValues.get(key).getSupportedSdkSteps()));
    }
    return cachedSdkSteps;
  }

  public Map<String, PmsSdkInstance> getSdkInstanceCacheValue() {
    if (!shouldUseInstanceCache) {
      Map<String, PmsSdkInstance> sdkSteps = new HashMap<>();
      pmsSdkInstanceRepository.findByActive(true).forEach(instance -> { sdkSteps.put(instance.getName(), instance); });
      return sdkSteps;
    } else {
      Map<String, PmsSdkInstance> cachedSdkSteps = new HashMap<>();
      for (Cache.Entry<String, PmsSdkInstance> stringPmsSdkInstanceEntry : instanceCache) {
        cachedSdkSteps.put(stringPmsSdkInstanceEntry.getKey(), stringPmsSdkInstanceEntry.getValue());
      }
      return cachedSdkSteps;
    }
  }

  public Set<String> getActiveInstanceNames() {
    if (!shouldUseInstanceCache) {
      Set<String> instanceNames = new HashSet<>();
      pmsSdkInstanceRepository.findByActive(true).forEach(instance -> instanceNames.add(instance.getName()));
      return instanceNames;
    }
    Set<String> instanceNames = new HashSet<>();
    for (Cache.Entry<String, PmsSdkInstance> stringPmsSdkInstanceEntry : instanceCache) {
      instanceNames.add(stringPmsSdkInstanceEntry.getKey());
    }
    return instanceNames;
  }

  public List<PmsSdkInstance> getActiveInstances() {
    return new ArrayList<>(getSdkInstanceCacheValue().values());
  }
}
