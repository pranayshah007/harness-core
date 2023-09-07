/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plan;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.UUIDGenerator;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAccess;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PIPELINE)
@Value
@Builder
@FieldNameConstants(innerTypeName = "NodeEntityKeys")
@StoreIn(DbAliases.PMS)
@Document("nodes")
@Entity(value = "nodes")
public class NodeEntity implements PersistentEntity, UuidAccess {
  private static final long TTL_MONTHS = 6;

  @Wither @Id @dev.morphia.annotations.Id String uuid;
  Node node;
  String planId;
  String nodeId;
  @Builder.Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant());

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList
        .<MongoIndex>builder()
        // pipeline get call
        .add(CompoundMongoIndex.builder()
                 .name("planNodeId_nodeId")
                 .field(NodeEntityKeys.planId)
                 .field(NodeEntityKeys.nodeId)
                 .build())
        .add(CompoundMongoIndex.builder().name("nodeId_idx").field(NodeEntityKeys.nodeId).build())
        .build();
  }

  public static NodeEntity fromNode(Node node, String planId, Boolean useNewNodeEntityConfiguration) {
    if (useNewNodeEntityConfiguration) {
      return NodeEntity.builder()
          .node(node)
          .uuid(UUIDGenerator.generateUuid())
          .nodeId(node.getUuid())
          .planId(planId)
          .build();
    } else {
      return NodeEntity.builder().node(node).uuid(node.getUuid()).nodeId(node.getUuid()).planId(planId).build();
    }
  }
}
