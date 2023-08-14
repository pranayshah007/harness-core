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
@FieldNameConstants(innerTypeName = "RuleSetId")
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity(value = "governanceRuleSet", noClassnameStored = true)
@Document("governanceRuleSet")
@Schema(description = "This object will contain the complete definition of a Cloud Cost Policy set")

public final class RuleSet implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess,
                                      CreatedByAware, UpdatedByAware {
  @Id @Schema(description = "unique id") @MongoId(targetType = FieldType.STRING) String uuid;
  @Schema(description = "account id") String accountId;
  @Schema(description = "name") String name;
  @Schema(description = NGCommonEntityConstants.DESCRIPTION) String description;
  @Schema(description = NGCommonEntityConstants.TAGS) List<String> tags;
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier;
  @Schema(description = "cloudProvider") RuleCloudProviderType cloudProvider;
  @Schema(description = "List of rules identifiers from governancePolicy collection") List<String> rulesIdentifier;
  @Schema(description = "is OOTB flag") Boolean isOOTB;
  @CreatedDate @Schema(description = NGCommonEntityConstants.CREATED_AT_MESSAGE) long createdAt;
  @LastModifiedDate @Schema(description = NGCommonEntityConstants.UPDATED_AT_MESSAGE) long lastUpdatedAt;
  @CreatedBy @Schema(description = "created by") private EmbeddedUser createdBy;
  @LastModifiedBy @Schema(description = "updated by") private EmbeddedUser lastUpdatedBy;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("ruleSet")
                 .field(RuleSetId.accountId)
                 .field(RuleSetId.name)
                 .field(RuleSetId.cloudProvider)
                 .field(RuleSetId.projectIdentifier)
                 .field(RuleSetId.orgIdentifier)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("sort1")
                 .field(RuleSetId.accountId)
                 .field(RuleSetId.name)
                 .field(RuleSetId.cloudProvider)
                 .sortField(RuleSetId.lastUpdatedAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("sort2")
                 .field(RuleSetId.accountId)
                 .field(RuleSetId.name)
                 .field(RuleSetId.cloudProvider)
                 .sortField(RuleSetId.createdAt)
                 .build())
        .build();
  }

  public RuleSet toDTO() {
    return RuleSet.builder()
        .uuid(getUuid())
        .accountId(getAccountId())
        .name(getName())
        .description(getDescription())
        .orgIdentifier(getOrgIdentifier())
        .projectIdentifier(getProjectIdentifier())
        .cloudProvider(getCloudProvider())
        .tags(getTags())
        .rulesIdentifier(getRulesIdentifier())
        .isOOTB(getIsOOTB())
        .createdAt(getCreatedAt())
        .lastUpdatedAt(getLastUpdatedAt())
        .createdBy(getCreatedBy())
        .lastUpdatedBy(getLastUpdatedBy())
        .build();
  }
}
