/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.beans.entity;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.validator.EntityName;
import io.harness.data.validator.Trimmed;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.persistance.GitSyncableEntity;
import io.harness.mongo.collation.CollationLocale;
import io.harness.mongo.collation.CollationStrength;
import io.harness.mongo.index.Collation;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import io.harness.persistence.gitaware.GitAware;
import io.harness.pms.yaml.PipelineVersion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
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
@FieldNameConstants(innerTypeName = "InputSetEntityKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "inputSetsPMS", noClassnameStored = true)
@Document("inputSetsPMS")
@TypeAlias("inputSetsPMS")
@HarnessEntity(exportable = true)
public class InputSetEntity implements GitAware, GitSyncableEntity, PersistentEntity, AccountAccess, UuidAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList
        .<MongoIndex>builder()
        //            TODO: remove the index once the old git sync is sunset
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountId_organizationId_projectId_pipelineId_inputSetId_repo_branch")
                 .unique(true)
                 .field(InputSetEntityKeys.accountId)
                 .field(InputSetEntityKeys.orgIdentifier)
                 .field(InputSetEntityKeys.projectIdentifier)
                 .field(InputSetEntityKeys.pipelineIdentifier)
                 .field(InputSetEntityKeys.identifier)
                 .field(InputSetEntityKeys.yamlGitConfigRef)
                 .field(InputSetEntityKeys.branch)
                 .build())
        //            This index will be used for get inputSet call and repo filter
        .add(CompoundMongoIndex.builder()
                 .name("gitx_accountId_organizationId_projectId_pipelineId_inputSetId_repo")
                 .field(InputSetEntityKeys.accountId)
                 .field(InputSetEntityKeys.orgIdentifier)
                 .field(InputSetEntityKeys.projectIdentifier)
                 .field(InputSetEntityKeys.pipelineIdentifier)
                 .field(InputSetEntityKeys.identifier)
                 .field(InputSetEntityKeys.repo)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_repoURL_filePath")
                 .field(InputSetEntityKeys.accountId)
                 .field(InputSetEntityKeys.repoURL)
                 .field(InputSetEntityKeys.filePath)
                 .build())
        // for full sync
        //            TODO: remove the index once the old git sync is sunset
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_organizationId_projectId_repo_branch_WithCollationIdx")
                 .field(InputSetEntityKeys.accountId)
                 .field(InputSetEntityKeys.orgIdentifier)
                 .field(InputSetEntityKeys.projectIdentifier)
                 .field(InputSetEntityKeys.yamlGitConfigRef)
                 .field(InputSetEntityKeys.branch)
                 .descRangeField(InputSetEntityKeys.lastUpdatedAt)
                 .collation(
                     Collation.builder().locale(CollationLocale.ENGLISH).strength(CollationStrength.SECONDARY).build())
                 .build())
        .build();
  }
  @Setter @NonFinal @Id @dev.morphia.annotations.Id String uuid;

  @Wither @NotEmpty @NonFinal @Setter String yaml;

  @NotEmpty String accountId;
  @NotEmpty String orgIdentifier;
  @Trimmed @NotEmpty String projectIdentifier;
  @Trimmed @NotEmpty String pipelineIdentifier;

  @NotEmpty String identifier;
  @Wither @EntityName String name;
  @Wither @Size(max = 1024) String description;
  @Wither @Singular @Size(max = 128) List<NGTag> tags;

  @NotEmpty InputSetEntityType inputSetEntityType;
  @Wither List<String> inputSetReferences;

  @Setter @NonFinal @SchemaIgnore @FdIndex @CreatedDate @Builder.Default Long createdAt = 0L;
  @Wither @Setter @NonFinal @SchemaIgnore @NotNull @LastModifiedDate @Builder.Default Long lastUpdatedAt = 0L;
  @Wither @Builder.Default Boolean deleted = Boolean.FALSE;
  @Wither @Version Long version;

  @Wither @Setter @NonFinal String objectIdOfYaml;
  @Setter @NonFinal Boolean isFromDefaultBranch;
  @Setter @NonFinal String branch;
  @Setter @NonFinal String yamlGitConfigRef;
  @Setter @NonFinal String filePath;
  @Setter @NonFinal String rootFolder;
  @Getter(AccessLevel.NONE) @Wither @NonFinal Boolean isEntityInvalid;

  // git experience parameters after simplification
  @Wither @Setter @NonFinal StoreType storeType;
  @Setter @NonFinal String repo;
  @Setter @NonFinal String connectorRef;
  @Wither @Setter @NonFinal String repoURL;
  @Wither @Setter @NonFinal String fallBackBranch;

  @Wither @Builder.Default Boolean isInvalid = Boolean.FALSE;

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

  @NonNull
  public Boolean getIsInvalid() {
    if (isInvalid == null) {
      return Boolean.FALSE;
    }
    return isInvalid;
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

  public String getHarnessVersion() {
    if (harnessVersion == null || harnessVersion.equals("0")) {
      return PipelineVersion.V0;
    }
    return harnessVersion;
  }
}
