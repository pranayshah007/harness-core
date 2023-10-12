/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.plan.Node;
import io.harness.plan.NodeEntity;
import io.harness.plan.NodeEntity.NodeEntityKeys;
import io.harness.plan.Plan;
import io.harness.plan.Plan.PlanKeys;
import io.harness.plan.PlanNode;
import io.harness.repositories.NodeEntityRepository;
import io.harness.repositories.PlanRepository;
import io.harness.springdata.TransactionHelper;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class PlanServiceImpl implements PlanService {
  @Inject MongoTemplate mongoTemplate;
  @Inject private PlanRepository planRepository;
  @Inject private NodeEntityRepository nodeEntityRepository;
  @Inject private TransactionHelper transactionHelper;

  @Override
  public Plan fetchPlan(String planId) {
    Optional<Plan> planOptional = planRepository.findById(planId);
    if (planOptional.isEmpty()) {
      throw new InvalidRequestException("Plan not found for id" + planId);
    }
    return planOptional.get();
  }

  @Override
  public Optional<Plan> fetchPlanOptional(String planId) {
    return planRepository.findById(planId);
  }

  @Override
  public List<Node> saveIdentityNodesForMatrix(List<Node> identityNodes, String planId) {
    List<Node> nodes = new ArrayList<>();
    nodeEntityRepository
        .saveAll(identityNodes.stream().map(o -> NodeEntity.fromNode(o, planId)).collect(Collectors.toList()))
        .iterator()
        .forEachRemaining(nodeEntity -> nodes.add(nodeEntity.getNode()));
    return nodes;
  }

  @Override
  public Plan save(Plan plan) {
    return transactionHelper.performTransaction(() -> {
      List<NodeEntity> nodeEntities =
          plan.getPlanNodes().stream().map(pn -> NodeEntity.fromNode(pn, plan.getUuid())).collect(Collectors.toList());
      nodeEntityRepository.saveAll(nodeEntities);
      if (!planRepository.existsById(plan.getUuid())) {
        return planRepository.save(plan.withPlanNodes(new ArrayList<>()));
      } else {
        return plan;
      }
    });
  }

  @Override
  public Node fetchNode(String planId, String nodeId) {
    Optional<NodeEntity> nodeEntity = nodeEntityRepository.findByPlanIdAndNodeId(planId, nodeId);
    if (nodeEntity.isPresent()) {
      return nodeEntity.get().getNode();
    }
    // This is a fallback mechanism added. In case of execution that were running while deployment nodes would have
    // already been created according to old setup that is uuid of the entity will be same as node id hence for those
    // executions the above query will not work and it won't fetch any nodes , hence we have added a fallback query
    // which fetches node based on assumption that nodeId is set as uuid for that node.
    nodeEntity = nodeEntityRepository.findById(nodeId);
    if (nodeEntity.isPresent()) {
      log.info("Unable to find node from node Id and planId . Most likely this was an older running execution");
      return nodeEntity.get().getNode();
    }

    // We have added this code so that in case for some step while plan creation the uuid of planNode is generated
    // everytime instead of being formed from yaml in such a case while retry the nodeId of the node the nodeId in the
    // next step handler of the previous step will be different. To handle such a case we are getting node based on
    // nodeId which will get the node from some previous planId.  Example for InfraStructureStep in
    // InfrastructurePmsPlanCreator.java
    List<NodeEntity> nodeEntitiesByNodeId = nodeEntityRepository.findNodeEntityByNodeId(nodeId);
    if (isNotEmpty(nodeEntitiesByNodeId)) {
      if (nodeEntitiesByNodeId.size() > 1) {
        log.warn("Multiple nodes found for nodeId {}", nodeId);
      }
      return nodeEntitiesByNodeId.get(0).getNode();
    }

    Plan plan = fetchPlan(planId);
    if (isNotEmpty(plan.getPlanNodes())) {
      return plan.fetchPlanNode(nodeId);
    }
    return PlanNode.fromPlanNodeProto(fetchPlan(planId).fetchNode(nodeId));
  }

  @Override
  public List<Node> fetchNodes(String planId) {
    return nodeEntityRepository.findNodeEntityByPlanId(planId)
        .stream()
        .map(NodeEntity::getNode)
        .collect(Collectors.toList());
  }
  @Override
  public void deleteNodesForGivenIds(String planId, Set<String> nodeEntityIds) {
    if (EmptyPredicate.isEmpty(nodeEntityIds)) {
      return;
    }
    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
      Query query =
          new Query(Criteria.where(NodeEntityKeys.planId).is(planId).and(NodeEntityKeys.nodeId).in(nodeEntityIds));
      nodeEntityRepository.deleteAllByPlanIdNodeIds(query);
      // This is also for fallback. In case if the execution was done before the latest deployment
      nodeEntityRepository.deleteAllByUuidIn(nodeEntityIds);
      return true;
    });
  }

  @Override
  public void updateTTLForNodesForGivenPlanId(String planId, Date ttlDate) {
    if (EmptyPredicate.isEmpty(planId)) {
      return;
    }
    Criteria criteria = Criteria.where(NodeEntityKeys.planId).is(planId);
    Query query = new Query(criteria);
    Update ops = new Update();
    ops.set(NodeEntityKeys.validUntil, ttlDate);
    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
      UpdateResult updateResult = mongoTemplate.updateMulti(query, ops, NodeEntity.class);
      if (!updateResult.wasAcknowledged()) {
        log.warn("NodeEntity could be marked as updated TTL for given planIds - " + planId);
      }
      return true;
    });
  }

  @Override
  public void deletePlansForGivenIds(Set<String> planIds) {
    if (EmptyPredicate.isEmpty(planIds)) {
      return;
    }
    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
      planRepository.deleteAllByUuidIn(planIds);
      return true;
    });
  }

  @Override
  public void updateTTLForPlans(String planId, Date ttlDate) {
    if (EmptyPredicate.isEmpty(planId)) {
      return;
    }

    Criteria criteria = Criteria.where(PlanKeys.uuid).is(planId);
    Query query = new Query(criteria);
    Update ops = new Update();
    ops.set(PlanKeys.validUntil, ttlDate);

    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
      UpdateResult updateResult = mongoTemplate.updateMulti(query, ops, Plan.class);
      if (!updateResult.wasAcknowledged()) {
        log.warn("No Plans could be marked as updated TTL for given planIds - " + planId);
      }
      return true;
    });
  }

  @Override
  public <T extends Node> Set<T> fetchAllNodes(String planId, Set<String> nodeIds) {
    Query query = new Query(Criteria.where(NodeEntityKeys.planId).is(planId).and(NodeEntityKeys.nodeId).in(nodeIds));
    Iterable<NodeEntity> nodesEntities = nodeEntityRepository.findByPlanIdAndNodeIds(query);
    Set<T> nodes = new HashSet<>();
    Set<String> nodeIdsFound = new HashSet<>();
    for (NodeEntity nodeEntity : nodesEntities) {
      nodes.add((T) nodeEntity.getNode());
      nodeIdsFound.add(nodeEntity.getNodeId());
    }

    Set<String> remainingNodeIds = Sets.difference(nodeIds, nodeIdsFound);
    if (isNotEmpty(remainingNodeIds)) {
      // This is a fallback mechanism added. In case of execution that were running while deployment nodes would have
      // already been created according to old setup that is uuid of the entity will be same as node id hence for those
      // executions the above query will not work and it won't fetch any nodes , hence we have added a fallback query
      // which fetches nodes based on assumption that nodeIds is set as uuid for those nodes.
      log.info("Unable to find some nodes from node Id and planId . Most likely this was an older running execution");
      nodesEntities = nodeEntityRepository.findAllById(remainingNodeIds);
      for (NodeEntity nodeEntity : nodesEntities) {
        nodes.add((T) nodeEntity.getNode());
      }
    }
    return nodes;
  }
}
