/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.ChangeDataCapture;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.validator.EntityName;
import io.harness.data.validator.Trimmed;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.persistance.GitSyncableEntity;
import io.harness.mongo.collation.CollationLocale;
import io.harness.mongo.collation.CollationStrength;
import io.harness.mongo.index.Collation;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import io.harness.persistence.gitaware.GitAware;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.template.yaml.TemplateRefHelper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;
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
import lombok.experimental.UtilityClass;
import lombok.experimental.Wither;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "PipelineEntityKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "pipelinesPMS", noClassnameStored = true)
@Document("pipelinesPMS")
@TypeAlias("pipelinesPMS")
@HarnessEntity(exportable = true)
@ChangeDataCapture(table = "tags_info_ng", dataStore = "pms-harness", fields = {}, handler = "TagsInfoNGCD")
@ChangeDataCapture(table = "pipelines", dataStore = "ng-harness", fields = {}, handler = "Pipelines")
public class PipelineEntity implements GitAware, GitSyncableEntity, PersistentEntity, AccountAccess, UuidAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList
        .<MongoIndex>builder()
        // pipeline get call
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
        // count pipelines in account
        .add(CompoundMongoIndex.builder()
                 .name("accountId_organizationId_projectId_lastUpdatedAt")
                 .field(PipelineEntityKeys.accountId)
                 .field(PipelineEntityKeys.orgIdentifier)
                 .field(PipelineEntityKeys.lastUpdatedAt)
                 .field(PipelineEntityKeys.projectIdentifier)
                 .build())
        // used by countFileInstances
        .add(CompoundMongoIndex.builder()
                 .name("accountId_repoURL_filePath")
                 .field(PipelineEntityKeys.accountId)
                 .field(PipelineEntityKeys.repoURL)
                 .field(PipelineEntityKeys.filePath)
                 .build())
        // Used by sort in pipeline list api
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_lastUpdatedAt_repo_identifier_idx")
                 .field(PipelineEntityKeys.accountId)
                 .field(PipelineEntityKeys.orgIdentifier)
                 .field(PipelineEntityKeys.projectIdentifier)
                 .descSortField(PipelineEntityKeys.lastUpdatedAt)
                 // Range filters
                 .ascRangeField(PipelineEntityKeys.repo)
                 .ascRangeField(PipelineEntityKeys.identifier)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_name_repo_identifier_WithCollationIdx")
                 .field(PipelineEntityKeys.accountId)
                 .field(PipelineEntityKeys.orgIdentifier)
                 .field(PipelineEntityKeys.projectIdentifier)
                 .descSortField(PipelineEntityKeys.name)
                 // Range filters
                 .ascRangeField(PipelineEntityKeys.repo)
                 .ascRangeField(PipelineEntityKeys.identifier)
                 .collation(
                     Collation.builder().locale(CollationLocale.ENGLISH).strength(CollationStrength.SECONDARY).build())
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_lastExecutedAt_repo_identifier_idx")
                 .field(PipelineEntityKeys.accountId)
                 .field(PipelineEntityKeys.orgIdentifier)
                 .field(PipelineEntityKeys.projectIdentifier)
                 .descSortField(PipelineEntityKeys.lastExecutedAt)
                 // Range filters
                 .ascRangeField(PipelineEntityKeys.repo)
                 .ascRangeField(PipelineEntityKeys.identifier)
                 .build())
        .build();
  }
  @Setter @NonFinal @Id @dev.morphia.annotations.Id String uuid;

  @Setter @NonFinal Set<String> templateModules;

  @NotEmpty String accountId;
  @NotEmpty String orgIdentifier;
  @Trimmed @NotEmpty String projectIdentifier;
  @NotEmpty String identifier;
  @Wither @Setter @NonFinal Boolean isDraft;

  @Wither @NotEmpty @NonFinal @Setter String yaml;

  // Used by PipelineTelemetryPublisher
  @Setter @NonFinal @SchemaIgnore @CreatedDate @Builder.Default Long createdAt = 0L;
  @Wither @Setter @NonFinal @SchemaIgnore @NotNull @LastModifiedDate @Builder.Default Long lastUpdatedAt = 0L;
  @Wither @Default Boolean deleted = Boolean.FALSE;

  @Wither @EntityName String name;
  @Wither @Size(max = 1024) String description;
  @Wither @Singular @Size(max = 128) List<NGTag> tags;

  @Wither @Version Long version;

  @Wither @Default Map<String, org.bson.Document> filters = new HashMap<>();

  /**
   * @deprecated Use {@link RecentExecutionInfo} from {@link PipelineMetadataV2}
   * lastExecutionTs move out from this dto to first class in pipelineEntity for sort filter
   */
  @Deprecated ExecutionSummaryInfo executionSummaryInfo;
  @Builder.Default Integer runSequence = 0;

  @Wither @Builder.Default Integer stageCount = 0;
  @Wither @Singular List<String> stageNames;

  @Wither Boolean allowStageExecutions;

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
  @Wither @Setter @NonFinal String repoURL;

  // to maintain pipeline version
  @Setter @NonFinal String harnessVersion;

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

  public Boolean getTemplateReference() {
    if (EmptyPredicate.isEmpty(getData())) {
      return false;
    }
    return TemplateRefHelper.hasTemplateRefOrCustomDeploymentRef(getData());
  }

  public String getHarnessVersion() {
    if (harnessVersion == null || harnessVersion.equals("V0")) {
      return PipelineVersion.V0;
    }
    return harnessVersion;
  }

  @UtilityClass
  public static class PipelineEntityKeys {
    public static final String lastExecutedAt = PipelineEntityKeys.executionSummaryInfo + "."
        + "lastExecutionTs";
  }
}
