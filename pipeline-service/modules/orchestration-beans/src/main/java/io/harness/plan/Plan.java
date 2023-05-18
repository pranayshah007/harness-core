/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plan;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.ModuleType;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.pms.contracts.plan.ErrorResponse;
import io.harness.pms.contracts.plan.GraphLayoutInfo;
import io.harness.pms.contracts.plan.PlanNodeProto;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.stepparameters.PmsStepParameters;

import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * This is the plan we want to execute during the execution
 * It contains a list of ExecutionNode and stating point
 * This is contained as a list sorted by the uuid to quick retrieval.
 *
 * Do not want this to be a map as we will lost the ability to query the database by node properties
 * This will be required by the apps to performs some migrations etc.
 *
 * This was a major pain point for the design of our StateMachine.
 *
 * With this approach we can crate iterators over these and perform the migrations
 */

@OwnedBy(PIPELINE)
@Value
@Builder
@FieldNameConstants(innerTypeName = "PlanKeys")
@StoreIn(DbAliases.PMS)
@Document("plans")
@TypeAlias("plan")
@Entity(value = "plans")
public class Plan implements PersistentEntity, Node {
  static final long TTL_MONTHS = 6;

  @Default @Wither @Id @dev.morphia.annotations.Id String uuid = generateUuid();
  @Singular @Deprecated List<PlanNodeProto> nodes;
  @Wither @Singular List<Node> planNodes;

  @NotNull String startingNodeId;

  Map<String, String> setupAbstractions;
  GraphLayoutInfo graphLayoutInfo;

  @Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant());

  @Wither @CreatedDate Long createdAt;
  @Wither @Version Long version;

  @Builder.Default boolean valid = true;
  ErrorResponse errorResponse;
  List<String> preservedNodesInRollbackMode;

  public boolean isEmpty() {
    return EmptyPredicate.isEmpty(nodes);
  }

  public PlanNodeProto fetchStartingNode() {
    return fetchNode(startingNodeId);
  }

  public PlanNodeProto fetchNode(String nodeId) {
    Optional<PlanNodeProto> optional = nodes.stream().filter(pn -> pn.getUuid().equals(nodeId)).findFirst();
    if (optional.isPresent()) {
      return optional.get();
    }
    throw new InvalidRequestException("No node found with Id :" + nodeId);
  }

  public Node fetchStartingPlanNode() {
    return fetchPlanNode(startingNodeId);
  }

  public Node fetchPlanNode(String nodeId) {
    Optional<Node> optional = planNodes.stream().filter(pn -> pn.getUuid().equals(nodeId)).findFirst();
    if (optional.isPresent()) {
      return optional.get();
    }
    throw new InvalidRequestException("No node found with Id :" + nodeId);
  }

  @Override
  public NodeType getNodeType() {
    return NodeType.PLAN;
  }

  @Override
  public String getIdentifier() {
    return getUuid();
  }

  @Override
  public String getStageFqn() {
    return null;
  }

  @Override
  public String getName() {
    return getIdentifier();
  }

  @Override
  public StepType getStepType() {
    return StepType.newBuilder().setType("PLAN").build();
  }

  @Override
  public String getGroup() {
    return "PLAN";
  }

  @Override
  public PmsStepParameters getStepParameters() {
    return null;
  }

  @Override
  public boolean isSkipUnresolvedExpressionsCheck() {
    return true;
  }

  @Override
  public String getServiceName() {
    return ModuleType.PMS.name();
  }
}
