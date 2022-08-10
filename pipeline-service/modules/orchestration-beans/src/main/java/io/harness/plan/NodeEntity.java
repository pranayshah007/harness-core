/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plan;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAccess;

import java.time.OffsetDateTime;
import java.util.Date;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PIPELINE)
@Value
@Builder
@FieldNameConstants(innerTypeName = "NodeEntityKeys")
@Document("nodes")
@Entity(value = "nodes")
@StoreIn(DbAliases.PMS)
public class NodeEntity implements PersistentEntity, UuidAccess {
  private static final long TTL_MONTHS = 6;

  @Wither @Id @org.mongodb.morphia.annotations.Id String uuid;
  Node node;
  String planId;
  @Builder.Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant());

  public static NodeEntity fromNode(Node node, String planId) {
    return NodeEntity.builder().node(node).uuid(node.getUuid()).planId(planId).build();
  }
}
