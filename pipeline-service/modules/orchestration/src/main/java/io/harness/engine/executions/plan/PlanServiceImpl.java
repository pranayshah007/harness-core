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
import io.harness.plan.Plan;
import io.harness.plan.PlanNode;
import io.harness.repositories.NodeEntityRepository;
import io.harness.repositories.PlanRepository;
import io.harness.springdata.TransactionHelper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.jodah.failsafe.Failsafe;

@OwnedBy(HarnessTeam.PIPELINE)
public class PlanServiceImpl implements PlanService {
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
    Optional<NodeEntity> nodeEntity = nodeEntityRepository.findById(nodeId);
    if (nodeEntity.isPresent()) {
      return nodeEntity.get().getNode();
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
  public void deleteNodesForGivenIds(Set<String> nodeEntityIds) {
    if (EmptyPredicate.isEmpty(nodeEntityIds)) {
      return;
    }
    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
      nodeEntityRepository.deleteAllByUuidIn(nodeEntityIds);
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
  public <T extends Node> T fetchNode(String nodeId) {
    Optional<NodeEntity> nodeEntity = nodeEntityRepository.findById(nodeId);
    return nodeEntity.map(entity -> (T) entity.getNode()).orElse(null);
  }

  @Override
  public <T extends Node> Set<T> fetchAllNodes(Set<String> nodeIds) {
    Iterable<NodeEntity> nodesEntities = nodeEntityRepository.findAllById(nodeIds);
    Set<T> nodes = new HashSet<>();
    for (NodeEntity nodeEntity : nodesEntities) {
      nodes.add((T) nodeEntity.getNode());
    }
    return nodes;
  }
}
