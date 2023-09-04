/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.graph.stepDetail;

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
import io.harness.graph.stepDetail.service.PmsGraphStepDetailsService;
import io.harness.observer.Subject;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.repositories.stepDetail.NodeExecutionsInfoRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.UpdateResult;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PmsGraphStepDetailsServiceImpl implements PmsGraphStepDetailsService {
  @Inject NodeExecutionsInfoRepository nodeExecutionsInfoRepository;
  @Inject @Getter private final Subject<StepDetailsUpdateObserver> stepDetailsUpdateObserverSubject = new Subject<>();
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
  public void saveNodeExecutionInfo(String nodeExecutionId, String planExecutionId, PmsStepParameters resolvedInputs) {
    NodeExecutionsInfoBuilder nodeExecutionsInfoBuilder =
        NodeExecutionsInfo.builder().nodeExecutionId(nodeExecutionId).planExecutionId(planExecutionId);
    if (resolvedInputs == null) {
      nodeExecutionsInfoRepository.save(nodeExecutionsInfoBuilder.build());
      return;
    }
    nodeExecutionsInfoBuilder.resolvedInputs(resolvedInputs);
    nodeExecutionsInfoRepository.save(nodeExecutionsInfoBuilder.build());
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
}
