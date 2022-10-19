/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.governance;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.StoreIn;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@StoreIn(DbAliases.CENG)
@FieldNameConstants(innerTypeName = "PolicySetId")
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity(value = "governancePolicyPack", noClassnameStored = true)
@Schema(description = "This object will contain the complete definition of a Cloud Cost Policy set")

public final class PolicyPack implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess,
                                        CreatedByAware, UpdatedByAware {
  @Id @Schema(description = "unique id") String uuid;
  @Schema(description = "account id") String accountId;
  @Schema(description = "Identifier") String name;
  @Schema(description = NGCommonEntityConstants.DESCRIPTION) String description;
  @Schema(description = NGCommonEntityConstants.TAGS) List<String> tags;
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier;
  @Schema(description = "cloudProvider") String cloudProvider;
  @Schema(description = "List of policies identifiers from governancePolicy collection") List<String> policiesIdentifier;
//  @Schema(description = "policySetExecutionCron") String policySetExecutionCron;
//  @Schema(description = "policySetTargetAccounts") List<String> policySetTargetAccounts;
//  @Schema(description = "policySetTargetRegions") List<String> policySetTargetRegions;
  @Schema(description = "is OOTB flag") Boolean isOOTB;
//  @Schema(description = "isEnabled") String isEnabled;
  @Schema(description = NGCommonEntityConstants.CREATED_AT_MESSAGE) long createdAt;
  @Schema(description = NGCommonEntityConstants.UPDATED_AT_MESSAGE) long lastUpdatedAt;
  @Schema(description = "created by") private EmbeddedUser createdBy;
  @Schema(description = "updated by") private EmbeddedUser lastUpdatedBy;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("policy")
                 .field(Policy.PolicyId.uuid)
                 .field(Policy.PolicyId.accountId)
                 .field(Policy.PolicyId.cloudProvider)
                 .build())
        .add(CompoundMongoIndex.builder().name("sort1").field(Policy.PolicyId.lastUpdatedAt).build())
        .add(CompoundMongoIndex.builder().name("sort2").field(Policy.PolicyId.createdAt).build())
        .build();
  }
  public PolicyPack toDTO() {
    return PolicyPack.builder()
        .uuid(getUuid())
        .accountId(getAccountId())
        .name(getName())
        .description(getDescription())
        .orgIdentifier(getOrgIdentifier())
        .projectIdentifier(getProjectIdentifier())
        .cloudProvider(getCloudProvider())
        .tags(getTags())
        .policiesIdentifier(getPoliciesIdentifier())
//        .policySetExecutionCron(getPolicySetExecutionCron())
//        .policySetTargetAccounts(getPolicySetTargetAccounts())
//        .policySetTargetRegions(getPolicySetTargetRegions())
        .isOOTB(getIsOOTB())
        .createdAt(getCreatedAt())
        .lastUpdatedAt(getLastUpdatedAt())
        .createdBy(getCreatedBy())
        .lastUpdatedBy(getLastUpdatedBy())
        .build();
  }
}
