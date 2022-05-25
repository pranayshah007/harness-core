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
import io.harness.annotations.ChangeDataCapture;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityName;
import io.harness.data.validator.Trimmed;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.persistance.GitSyncableEntity;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;
import io.harness.persistence.gitaware.GitAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import com.sun.istack.internal.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.Setter;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import lombok.experimental.Wither;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PIPELINE)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "PipelineEntityKeys")
@Entity(value = "pipelinesPMS", noClassnameStored = true)
@Document("pipelinesPMS")
@TypeAlias("pipelinesPMS")
@HarnessEntity(exportable = true)
@StoreIn(DbAliases.PMS)
@ChangeDataCapture(table = "tags_info", dataStore = "pms-harness", fields = {}, handler = "TagsInfoCD")
@ChangeDataCapture(table = "pipelines", dataStore = "ng-harness", fields = {}, handler = "Pipelines")
public class PipelineEntity
    implements GitAware, GitSyncableEntity, PersistentEntity, AccountAccess, UuidAware, CreatedAtAware, UpdatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountId_organizationId_projectId_pipelineId_repo_branch")
                 .unique(true)
                 .field(PipelineEntityKeys.accountId)
                 .field(PipelineEntityKeys.orgIdentifier)
                 .field(PipelineEntityKeys.projectIdentifier)
                 .field(PipelineEntityKeys.identifier)
                 .field(PipelineEntityKeys.yamlGitConfigRef)
                 .field(PipelineEntityKeys.branch)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_organizationId_projectId_lastUpdatedAt")
                 .field(PipelineEntityKeys.accountId)
                 .field(PipelineEntityKeys.orgIdentifier)
                 .field(PipelineEntityKeys.lastUpdatedAt)
                 .field(PipelineEntityKeys.projectIdentifier)
                 .build())
        .add(CompoundMongoIndex.builder().name("lastUpdatedAt_idx").field(PipelineEntityKeys.lastUpdatedAt).build())
        .build();
  }
  @Setter @NonFinal @Id @org.mongodb.morphia.annotations.Id String uuid;

  @NotEmpty String accountId;
  @NotEmpty String orgIdentifier;
  @Trimmed @NotEmpty String projectIdentifier;
  @NotEmpty String identifier;

  @Wither @NotEmpty @NonFinal @Setter String yaml;

  @Setter @NonFinal @SchemaIgnore @FdIndex @CreatedDate long createdAt;
  @Wither @Setter @NonFinal @SchemaIgnore @NotNull @LastModifiedDate long lastUpdatedAt;
  @Wither @Default Boolean deleted = Boolean.FALSE;

  @Wither @EntityName String name;
  @Wither @Size(max = 1024) String description;
  @Wither @Singular @Size(max = 128) List<NGTag> tags;

  @Wither @Version Long version;

  @Wither @Default Map<String, org.bson.Document> filters = new HashMap<>();
  // Todo: Move this to pipelineMetadata
  ExecutionSummaryInfo executionSummaryInfo;
  int runSequence;

  @Wither int stageCount;
  @Wither @Singular List<String> stageNames;

  @Wither Boolean allowStageExecutions;

  @Wither @Setter @NonFinal @Default Boolean templateReference = false;

  // git experience parameters before simplification
  @Wither @Setter @NonFinal String objectIdOfYaml;
  @Setter @NonFinal Boolean isFromDefaultBranch;
  @Setter @NonFinal String branch;
  @Setter @NonFinal String yamlGitConfigRef;
  @Wither @Setter @NonFinal String filePath; // -> also used in git simplification
  @Setter @NonFinal String rootFolder;
  @Getter(AccessLevel.NONE) @Wither @NonFinal Boolean isEntityInvalid;

  // git experience parameters after simplification
  @Wither @Setter @NonFinal StoreType storeType;
  @Wither @Setter @NonFinal String repo;
  @Wither @Setter @NonFinal String connectorRef;

  public String getData() {
    return yaml;
  }

  @Override
  public void setData(String data) {
    yaml = data;
  }

  @Override
  public String getAccountIdentifier() {
    return accountId;
  }

  @Override
  public boolean isEntityInvalid() {
    return Boolean.TRUE.equals(isEntityInvalid);
  }

  @Override
  public void setEntityInvalid(boolean isEntityInvalid) {
    this.isEntityInvalid = isEntityInvalid;
  }

  @Override
  public String getInvalidYamlString() {
    return yaml;
  }
}
