/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.Trimmed;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitsync.sdk.EntityGitDetails.EntityGitDetailsKeys;
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
@FieldNameConstants(innerTypeName = "PipelineMetadataKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "pipelineMetadata", noClassnameStored = true)
@Document("pipelineMetadata")
@TypeAlias("pipelineMetadata")
@HarnessEntity(exportable = true)
@Deprecated
public class PipelineMetadata {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("account_org_project_pipeline_yaml_git_config_branch_idx")
                 .unique(true)
                 .field(PipelineMetadataKeys.accountIdentifier)
                 .field(PipelineMetadataKeys.orgIdentifier)
                 .field(PipelineMetadataKeys.projectIdentifier)
                 .field(PipelineMetadataKeys.identifier)
                 .field(PipelineMetadataKeys.entityGitDetails + "." + EntityGitDetailsKeys.branch)
                 .field(PipelineMetadataKeys.entityGitDetails + "." + EntityGitDetailsKeys.repoIdentifier)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("account_org_project_pipeline_run_sequence_idx")
                 .unique(true)
                 .field(PipelineMetadataKeys.accountIdentifier)
                 .field(PipelineMetadataKeys.orgIdentifier)
                 .field(PipelineMetadataKeys.projectIdentifier)
                 .field(PipelineMetadataKeys.identifier)
                 .descRangeField(PipelineMetadataKeys.runSequence)
                 .build())
        .build();
  }

  @Setter @NonFinal @Id @org.mongodb.morphia.annotations.Id String uuid;
  @NotEmpty String accountIdentifier;
  @NotEmpty String orgIdentifier;
  @Trimmed @NotEmpty String projectIdentifier;
  @NotEmpty String identifier;

  ExecutionSummaryInfo executionSummaryInfo;
  EntityGitDetails entityGitDetails;
  int runSequence;
}
