/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PIPELINE)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "PipelineMetadataV2Keys")
@Entity(value = "pipelineMetadataV2", noClassnameStored = true)
@Document("pipelineMetadataV2")
@TypeAlias("pipelineMetadataV2")
@HarnessEntity(exportable = true)
@StoreIn(DbAliases.PMS)
public class PipelineMetadataV2 {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("account_org_project_pipeline")
                 .unique(true)
                 .field(PipelineMetadataV2Keys.accountIdentifier)
                 .field(PipelineMetadataV2Keys.orgIdentifier)
                 .field(PipelineMetadataV2Keys.projectIdentifier)
                 .field(PipelineMetadataV2Keys.identifier)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("account_org_project_last_executed")
                 .field(PipelineMetadataV2Keys.accountIdentifier)
                 .field(PipelineMetadataV2Keys.orgIdentifier)
                 .field(PipelineMetadataV2Keys.projectIdentifier)
                 .descRangeField(PipelineMetadataV2Keys.lastExecutedAt)
                 .build())
        .build();
  }

  @Setter @NonFinal @Id @org.mongodb.morphia.annotations.Id String uuid;
  @NotEmpty String accountIdentifier;
  @NotEmpty String orgIdentifier;
  @Trimmed @NotEmpty String projectIdentifier;
  @NotEmpty String identifier;

  int runSequence;
  // the zeroth element will be the most recent execution
  List<RecentExecutionInfo> recentExecutionInfoList;
  Long lastExecutedAt;
}
