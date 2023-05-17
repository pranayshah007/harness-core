/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.async.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "TemplateSetupUsageEventKeys")
@StoreIn(DbAliases.TEMPLATE)
@Entity(value = "TemplateSetupUsageEvent")
@Document("templateSetupUsageEvent")
@TypeAlias("templateSetupUsageEvent")
@HarnessEntity(exportable = false)
@OwnedBy(HarnessTeam.CDC)
public class TemplateSetupUsageEvent implements UuidAware, CreatedAtAware {
  public static final Long KEEP_EVENT_IN_DB_DAYS = 14L;

  @Id @dev.morphia.annotations.Id String uuid;

  String fqn; // account/org/project/pipeline for Inline, account/org/project/pipeline/branch for Remote

  SetupUsageEventStatus status;
  Action action;

  SetupUsageParams params;

  @CreatedDate Long startTs;
  Long endTs;

  @Builder.Default
  @FdTtlIndex
  Date validUntil = Date.from(OffsetDateTime.now().plusMonths(KEEP_EVENT_IN_DB_DAYS).toInstant());
  ;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("fqn_action_startTs_idx")
                 .field(TemplateSetupUsageEventKeys.fqn)
                 .field(TemplateSetupUsageEventKeys.action)
                 .descSortField(TemplateSetupUsageEventKeys.startTs)
                 .build())
        .build();
  }

  @Override
  public long getCreatedAt() {
    return startTs;
  }

  @Override
  public void setCreatedAt(long createdAt) {
    startTs = createdAt;
  }
}
