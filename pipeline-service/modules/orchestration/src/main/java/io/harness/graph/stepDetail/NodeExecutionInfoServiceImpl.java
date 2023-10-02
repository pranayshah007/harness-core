/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.graph.stepDetail;

import static io.harness.plancreator.strategy.StrategyConstants.ITEM;
import static io.harness.plancreator.strategy.StrategyConstants.ITERATION;
import static io.harness.plancreator.strategy.StrategyConstants.ITERATIONS;
import static io.harness.plancreator.strategy.StrategyConstants.MATRIX;
import static io.harness.plancreator.strategy.StrategyConstants.PARTITION;
import static io.harness.plancreator.strategy.StrategyConstants.REPEAT;
import static io.harness.plancreator.strategy.StrategyConstants.TOTAL_ITERATIONS;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stepDetail.NodeExecutionDetailsInfo;
import io.harness.beans.stepDetail.NodeExecutionsInfo;
import io.harness.beans.stepDetail.NodeExecutionsInfo.NodeExecutionsInfoBuilder;
import io.harness.beans.stepDetail.NodeExecutionsInfo.NodeExecutionsInfoKeys;
import io.harness.concurrency.ConcurrentChildInstance;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.observers.StepDetailsUpdateInfo;
import io.harness.engine.observers.StepDetailsUpdateObserver;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.observer.Subject;
import io.harness.plancreator.strategy.IterationVariables;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.LevelUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.repositories.stepDetail.NodeExecutionsInfoRepository;
import io.harness.serializer.KryoSerializer;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.bson.types.Binary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class NodeExecutionInfoServiceImpl implements NodeExecutionInfoService {
  @Inject NodeExecutionsInfoRepository nodeExecutionsInfoRepository;
  @Inject @Getter private final Subject<StepDetailsUpdateObserver> stepDetailsUpdateObserverSubject = new Subject<>();
  @Inject KryoSerializer kryoSerializer;
  @Inject private MongoTemplate mongoTemplate;

  @Override
  public void addStepDetail(String nodeExecutionId, String planExecutionId, PmsStepDetails stepDetails, String name) {
    Update update = new Update().addToSet(NodeExecutionsInfoKeys.nodeExecutionDetailsInfoList,
        NodeExecutionDetailsInfo.builder().name(name).stepDetails(stepDetails).build());
    Criteria criteria = Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).is(nodeExecutionId);
    mongoTemplate.findAndModify(new Query(criteria), update, NodeExecutionsInfo.class);
    stepDetailsUpdateObserverSubject.fireInform(StepDetailsUpdateObserver::onStepDetailsUpdate,
        StepDetailsUpdateInfo.builder().nodeExecutionId(nodeExecutionId).planExecutionId(planExecutionId).build());
  }

  // TODO: Make this better this should be called from no where else
  @Override
  public void saveNodeExecutionInfo(String nodeExecutionId, String planExecutionId, StrategyMetadata metadata) {
    NodeExecutionsInfoBuilder nodeExecutionsInfoBuilder =
        NodeExecutionsInfo.builder().nodeExecutionId(nodeExecutionId).planExecutionId(planExecutionId);
    if (metadata == null) {
      nodeExecutionsInfoRepository.save(nodeExecutionsInfoBuilder.build());
      return;
    }
    nodeExecutionsInfoBuilder.strategyMetadata(metadata);
    nodeExecutionsInfoRepository.save(nodeExecutionsInfoBuilder.build());
  }

  @Override
  public void addStepInputs(String nodeExecutionId, PmsStepParameters resolvedInputs, String planExecutionId) {
    // TODO (Sahil) : This is a hack right now to serialize in binary as findAndModify is not honoring converter
    // for maps Find a better way to do this
    Update update = new Update().set(
        NodeExecutionsInfoKeys.resolvedInputs, new Binary(kryoSerializer.asDeflatedBytes(resolvedInputs)));
    Criteria criteria = Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).is(nodeExecutionId);
    mongoTemplate.findAndModify(new Query(criteria), update, NodeExecutionsInfo.class);
    stepDetailsUpdateObserverSubject.fireInform(StepDetailsUpdateObserver::onStepInputsAdd,
        StepDetailsUpdateInfo.builder().nodeExecutionId(nodeExecutionId).planExecutionId(planExecutionId).build());
  }

  @Override
  public PmsStepParameters getStepInputs(String planExecutionId, String nodeExecutionId) {
    Optional<NodeExecutionsInfo> nodeExecutionsInfo =
        nodeExecutionsInfoRepository.findByNodeExecutionId(nodeExecutionId);
    if (nodeExecutionsInfo.isPresent()) {
      return nodeExecutionsInfo.get().getResolvedInputs();
    } else {
      log.warn("Could not find nodeExecutionsInfo with the given nodeExecutionId: " + nodeExecutionId);
      return new PmsStepParameters(new HashMap<>());
    }
  }

  @Override
  public PmsStepParameters getStepInputsRecasterPruned(String planExecutionId, String nodeExecutionId) {
    PmsStepParameters stepInputs = getStepInputs(planExecutionId, nodeExecutionId);
    return PmsStepParameters.parse(RecastOrchestrationUtils.pruneRecasterAdditions(stepInputs));
  }

  @Override
  public NodeExecutionsInfo getNodeExecutionsInfo(String nodeExecutionId) {
    return nodeExecutionsInfoRepository.findByNodeExecutionId(nodeExecutionId).orElse(null);
  }

  @Override
  public Map<String, PmsStepDetails> getStepDetails(String planExecutionId, String nodeExecutionId) {
    Optional<NodeExecutionsInfo> nodeExecutionsInfo =
        nodeExecutionsInfoRepository.findByNodeExecutionId(nodeExecutionId);
    return nodeExecutionsInfo
        .map(executionsInfo
            -> executionsInfo.getNodeExecutionDetailsInfoList().stream().collect(
                Collectors.toMap(NodeExecutionDetailsInfo::getName, NodeExecutionDetailsInfo::getStepDetails)))
        .orElseGet(HashMap::new);
  }

  @Override
  public void copyStepDetailsForRetry(
      String planExecutionId, String originalNodeExecutionId, String newNodeExecutionId) {
    Optional<NodeExecutionsInfo> originalStepDetailInstances =
        nodeExecutionsInfoRepository.findByNodeExecutionId(originalNodeExecutionId);
    if (originalStepDetailInstances.isPresent()) {
      NodeExecutionsInfo originalExecutionInfo = originalStepDetailInstances.get();
      NodeExecutionsInfo newNodeExecutionsInfo =
          NodeExecutionsInfo.builder()
              .nodeExecutionDetailsInfoList(originalExecutionInfo.getNodeExecutionDetailsInfoList())
              .nodeExecutionId(newNodeExecutionId)
              .planExecutionId(planExecutionId)
              .resolvedInputs(originalExecutionInfo.getResolvedInputs())
              .strategyMetadata(originalExecutionInfo.getStrategyMetadata())
              .build();
      nodeExecutionsInfoRepository.save(newNodeExecutionsInfo);
      stepDetailsUpdateObserverSubject.fireInform(StepDetailsUpdateObserver::onStepInputsAdd,
          StepDetailsUpdateInfo.builder().nodeExecutionId(newNodeExecutionId).planExecutionId(planExecutionId).build());
    }
  }

  @Override
  public void addConcurrentChildInformation(ConcurrentChildInstance concurrentChildInstance, String nodeExecutionId) {
    Update update = new Update().set(NodeExecutionsInfoKeys.concurrentChildInstance, concurrentChildInstance);
    Criteria criteria = Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).is(nodeExecutionId);
    mongoTemplate.findAndModify(new Query(criteria), update, NodeExecutionsInfo.class);
  }

  @Override
  public ConcurrentChildInstance incrementCursor(String nodeExecutionId, Status status) {
    Update update = new Update();
    update.inc(NodeExecutionsInfoKeys.concurrentChildInstance + ".cursor");
    update.addToSet(NodeExecutionsInfoKeys.concurrentChildInstance + ".childStatuses", status);
    Criteria criteria = Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).is(nodeExecutionId);
    NodeExecutionsInfo nodeExecutionsInfo =
        mongoTemplate.findAndModify(new Query(criteria), update, NodeExecutionsInfo.class);
    if (nodeExecutionsInfo == null) {
      return null;
    }
    return nodeExecutionsInfo.getConcurrentChildInstance();
  }

  @Override
  public ConcurrentChildInstance fetchConcurrentChildInstance(String nodeExecutionId) {
    Criteria criteria = Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).is(nodeExecutionId);
    NodeExecutionsInfo nodeExecutionsInfo = mongoTemplate.findOne(new Query(criteria), NodeExecutionsInfo.class);
    if (nodeExecutionsInfo == null) {
      return null;
    }
    return nodeExecutionsInfo.getConcurrentChildInstance();
  }

  @Override
  public void deleteNodeExecutionInfoForGivenIds(Set<String> nodeExecutionIds) {
    if (EmptyPredicate.isEmpty(nodeExecutionIds)) {
      return;
    }
    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
      nodeExecutionsInfoRepository.deleteAllByNodeExecutionIdIn(nodeExecutionIds);
      return true;
    });
  }

  @Override
  public void updateTTLForNodesForGivenPlanExecutionId(String planExecutionId, Date ttlDate) {
    if (EmptyPredicate.isEmpty(planExecutionId)) {
      return;
    }

    Criteria criteria = Criteria.where(NodeExecutionsInfoKeys.planExecutionId).is(planExecutionId);
    Query query = new Query(criteria);
    Update ops = new Update();
    ops.set(NodeExecutionsInfoKeys.validUntil, ttlDate);

    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
      UpdateResult updateResult = mongoTemplate.updateMulti(query, ops, NodeExecutionsInfo.class);
      if (!updateResult.wasAcknowledged()) {
        log.warn("No nodeExecutionInfo could be marked as updated TTL for given planExecutionId - " + planExecutionId);
      }
      return true;
    });
  }

  @Override
  public Map<String, Object> fetchStrategyObjectMap(Level level, boolean useMatrixFieldName) {
    Map<String, Object> strategyObjectMap = new HashMap<>();
    if (level.hasStrategyMetadata()) {
      return fetchStrategyObjectMap(Lists.newArrayList(level), useMatrixFieldName);
    }
    strategyObjectMap.put(ITERATION, 0);
    strategyObjectMap.put(ITERATIONS, 1);
    strategyObjectMap.put(TOTAL_ITERATIONS, 1);
    return strategyObjectMap;
  }

  @Override
  public Map<String, Object> fetchStrategyObjectMap(
      List<Level> levelsWithStrategyMetadata, boolean useMatrixFieldName) {
    Map<String, Object> strategyObjectMap = new HashMap<>();
    Map<String, Object> matrixValuesMap = new HashMap<>();
    Map<String, Object> repeatValuesMap = new HashMap<>();

    List<String> nodeExecutionIds =
        levelsWithStrategyMetadata.stream().map(Level::getRuntimeId).collect(Collectors.toList());
    Map<String, StrategyMetadata> strategyMetadataMap = fetchStrategyMetadata(nodeExecutionIds);

    List<IterationVariables> levels = new ArrayList<>();
    for (Level level : levelsWithStrategyMetadata) {
      StrategyMetadata strategyMetadata;
      // This is to ensure backward compatibility
      strategyMetadata = getCorrespondingStrategyMetadata(strategyMetadataMap, level);

      levels.add(IterationVariables.builder()
                     .currentIteration(strategyMetadata.getCurrentIteration())
                     .totalIterations(strategyMetadata.getTotalIterations())
                     .build());

      if (strategyMetadata.hasMatrixMetadata()) {
        // MatrixMapLocal can contain either a string as value or a json as value.
        Map<String, String> matrixMapLocal = strategyMetadata.getMatrixMetadata().getMatrixValuesMap();
        matrixValuesMap.putAll(StrategyUtils.getMatrixMapFromCombinations(matrixMapLocal));
      }
      if (strategyMetadata.hasForMetadata()) {
        repeatValuesMap.put(ITEM, strategyMetadata.getForMetadata().getValue());
        repeatValuesMap.put(PARTITION, strategyMetadata.getForMetadata().getPartitionList());
      }

      if (LevelUtils.isStepLevel(level)) {
        StrategyUtils.fetchGlobalIterationsVariablesForStrategyObjectMap(strategyObjectMap, levels);
      }

      strategyObjectMap.put(ITERATION, strategyMetadata.getCurrentIteration());
      strategyObjectMap.put(ITERATIONS, strategyMetadata.getTotalIterations());
      strategyObjectMap.put(TOTAL_ITERATIONS, strategyMetadata.getTotalIterations());
      strategyObjectMap.put(
          "identifierPostFix", AmbianceUtils.getStrategyPostFixUsingMetadata(strategyMetadata, useMatrixFieldName));
    }
    strategyObjectMap.put(MATRIX, matrixValuesMap);
    strategyObjectMap.put(REPEAT, repeatValuesMap);

    return strategyObjectMap;
  }

  private StrategyMetadata getCorrespondingStrategyMetadata(
      Map<String, StrategyMetadata> strategyMetadataMap, Level level) {
    StrategyMetadata strategyMetadata;
    if (strategyMetadataMap.containsKey(level.getRuntimeId())) {
      strategyMetadata = strategyMetadataMap.get(level.getRuntimeId());
    } else {
      // This should be removed in November release.
      strategyMetadata = level.getStrategyMetadata();
    }
    return strategyMetadata;
  }

  @Override
  public Map<String, StrategyMetadata> fetchStrategyMetadata(List<String> nodeExecutionIds) {
    Criteria criteria = Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).in(nodeExecutionIds);
    Query query = new Query(criteria);
    query.fields().include(NodeExecutionsInfoKeys.strategyMetadata);
    query.fields().include(NodeExecutionsInfoKeys.nodeExecutionId);

    List<NodeExecutionsInfo> nodeExecutionsInfo = mongoTemplate.find(query, NodeExecutionsInfo.class);
    if (EmptyPredicate.isEmpty(nodeExecutionsInfo)) {
      return new HashMap<>();
    }
    return nodeExecutionsInfo.stream().collect(
        Collectors.toMap(NodeExecutionsInfo::getNodeExecutionId, NodeExecutionsInfo::getStrategyMetadata));
  }
}
