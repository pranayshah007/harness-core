/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.validation.async.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "PipelineValidationEventKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "pipelineValidationEvent")
@Document("pipelineValidationEvent")
@TypeAlias("pipelineValidationEvent")
@HarnessEntity(exportable = false)
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineValidationEvent implements UuidAware {
  @Id @org.mongodb.morphia.annotations.Id String uuid;

  String fqn; // account/org/project/pipeline for Inline, account/org/project/pipeline/branch for Remote

  ValidationStatus status;
  Action action;

  ValidationParams params;
  ValidationResult result;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("fqn_action")
                 .unique(false)
                 .field(PipelineValidationEventKeys.fqn)
                 .field(PipelineValidationEventKeys.action)
                 .build())
        .build();
  }
}
