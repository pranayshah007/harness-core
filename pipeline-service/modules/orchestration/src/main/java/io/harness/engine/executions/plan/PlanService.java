/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.plan.Node;
import io.harness.plan.Plan;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@OwnedBy(PIPELINE)
public interface PlanService {
  Plan save(Plan plan);

  // These 2 methods uses the index planNodeId_nodeId in nodeEntity collection
  <T extends Node> T fetchNode(String planId, String nodeId);

  <T extends Node> Set<T> fetchAllNodes(String planId, Set<String> nodeIds);

  List<Node> fetchNodes(String planId);

  Plan fetchPlan(String planId);

  Optional<Plan> fetchPlanOptional(String planId);

  // When retying a failed pipeline from a strategy node, we are saving one IdentityNode for each successful
  // combination.
  List<Node> saveIdentityNodesForMatrix(List<Node> identityNodes, String planId);

  /**
   * Delete all nodeEntity for given uuids
   * Uses - id index
   * @param nodeEntityIds
   */
  // This  method uses the index planNodeId_nodeId in nodeEntity collection
  void deleteNodesForGivenIds(String planId, Set<String> nodeEntityIds);

  /**
   * Updates all nodeEntity for given planId with ttlExpiry
   * Uses - planId_idx index
   * @param planId
   */
  void updateTTLForNodesForGivenPlanId(String planId, Date ttlDate);

  /**
   * Delete all Plans for given uuids
   * Uses - id index
   * @param planIds
   */
  void deletePlansForGivenIds(Set<String> planIds);

  /**
   * Updates TTL for all Plans for given uuids
   * Uses - id index
   * @param planId
   */
  void updateTTLForPlans(String planId, Date ttlDate);
}
