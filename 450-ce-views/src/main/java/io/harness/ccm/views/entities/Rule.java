/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.entities;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.StoreIn;
import io.harness.beans.EmbeddedUser;
import io.harness.ccm.views.helper.RuleCloudProviderType;
import io.harness.ccm.views.helper.RuleStoreType;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Data
@Builder
@StoreIn(DbAliases.CENG)
@FieldNameConstants(innerTypeName = "RuleId")
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity(value = "governanceRule", noClassnameStored = true)
@Document("governanceRule")
@Schema(description = "This object will contain the complete definition of a Cloud Cost Policies")
// to do add index with accid + name
public final class Rule implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess,
                                   CreatedByAware, UpdatedByAware {
  @Id @Schema(description = "unique id") @MongoId(targetType = FieldType.STRING) String uuid;
  @Schema(description = "account id") String accountId;
  @Schema(description = "name") String name;
  @Schema(description = NGCommonEntityConstants.DESCRIPTION) String description;
  @Schema(description = NGCommonEntityConstants.POLICY) String rulesYaml;
  @Schema(description = NGCommonEntityConstants.TAGS) List<String> tags;
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier;
  @Schema(description = "cloudProvider") RuleCloudProviderType cloudProvider;
  @Schema(description = "versionLabel") String versionLabel;
  @Schema(description = "isStablePolicy") Boolean isStablePolicy;
  @Schema(description = "storeType") RuleStoreType storeType;
  @Schema(description = "isOOTB") Boolean isOOTB;
  @Schema(description = "deleted") Boolean deleted;
  @Schema(description = "forRecommendation") Boolean forRecommendation;
  @Schema(description = "resourceType") String resourceType;
  @CreatedDate @Schema(description = NGCommonEntityConstants.CREATED_AT_MESSAGE) long createdAt;
  @LastModifiedDate @Schema(description = NGCommonEntityConstants.UPDATED_AT_MESSAGE) long lastUpdatedAt;
  @CreatedBy @Schema private EmbeddedUser createdBy;
  @LastModifiedBy @Schema private EmbeddedUser lastUpdatedBy;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("rules")
                 .field(RuleId.accountId)
                 .field(RuleId.cloudProvider)
                 .field(RuleId.orgIdentifier)
                 .field(RuleId.projectIdentifier)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("sort1")
                 .field(RuleId.name)
                 .field(RuleId.accountId)
                 .field(RuleId.cloudProvider)
                 .sortField(RuleId.lastUpdatedAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("sort2")
                 .field(RuleId.name)
                 .field(RuleId.accountId)
                 .sortField(RuleId.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("recommendation")
                 .field(RuleId.isOOTB)
                 .field(RuleId.forRecommendation)
                 .build())
        .build();
  }
  public Rule toDTO() {
    return Rule.builder()
        .uuid(getUuid())
        .accountId(getAccountId())
        .name(getName())
        .description(getDescription())
        .rulesYaml(getRulesYaml())
        .cloudProvider(getCloudProvider())
        .versionLabel(getVersionLabel())
        .isStablePolicy(getIsStablePolicy())
        .storeType(getStoreType())
        .isOOTB(getIsOOTB())
        .tags(getTags())
        .deleted(getDeleted())
        .orgIdentifier(getOrgIdentifier())
        .forRecommendation(getForRecommendation())
        .projectIdentifier(getProjectIdentifier())
        .createdAt(getCreatedAt())
        .lastUpdatedAt(getLastUpdatedAt())
        .createdBy(getCreatedBy())
        .lastUpdatedBy(getLastUpdatedBy())
        .build();
  }
}