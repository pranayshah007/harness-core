/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.entities;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
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
@FieldNameConstants(innerTypeName = "PolicyExecutionId")
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity(value = "governancePolicyExecution", noClassnameStored = true)
@Schema(description = "This object will contain the complete definition of a Cloud Cost Policy Execution")
public class PolicyExecution implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess {
  @Id @Schema(description = "unique id") String uuid;
  @Schema(description = "account id") String accountId;
  @Schema(description = "identifier") String identifier;
  @Schema(description = "policyEnforcementIdentifier") String policyEnforcementIdentifier;
  @Schema(description = "policyIdentifier") String policyIdentifier;
  @Schema(description = "cloudProvider") String cloudProvider;
  @Schema(description = "isDryRun") Boolean isDryRun;
  @Schema(description = "targetAccounts") List<String> targetAccounts;
  @Schema(description = "targetRegions") List<String> targetRegions;
  @Schema(description = "executionLogPath") String executionLogPath;
  @Schema(description = "executionLogBucketType") String executionLogBucketType;
  @Schema(description = "executionCompletedAt") String executionCompletedAt;
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier;
  @Schema(description = NGCommonEntityConstants.CREATED_AT_MESSAGE) long createdAt;
  @Schema(description = NGCommonEntityConstants.UPDATED_AT_MESSAGE) long lastUpdatedAt;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("PolicyExecution")
                 .field(PolicyExecutionId.identifier)
                 .field(PolicyExecutionId.accountId)
                 .field(PolicyExecutionId.cloudProvider)
                 .field(PolicyExecutionId.policyEnforcementIdentifier)
                 .field(PolicyExecutionId.policyIdentifier)
                 .field(PolicyExecutionId.orgIdentifier)
                 .field(PolicyExecutionId.projectIdentifier)
                 .build())
        .add(CompoundMongoIndex.builder().name("sort1").field(PolicyExecutionId.lastUpdatedAt).build())
        .add(CompoundMongoIndex.builder().name("sort2").field(PolicyExecutionId.createdAt).build())
        .build();
  }

  public PolicyExecution toDTO() {
    return PolicyExecution.builder()
        .uuid(getUuid())
        .accountId(getAccountId())
        .identifier(getIdentifier())
        .policyEnforcementIdentifier(getPolicyEnforcementIdentifier())
        .policyIdentifier(getPolicyIdentifier())
        .cloudProvider(getCloudProvider())
        .isDryRun(getIsDryRun())
        .targetAccounts(getTargetAccounts())
        .targetRegions(getTargetRegions())
        .executionLogPath(getExecutionLogPath())
        .executionLogBucketType(getExecutionLogBucketType())
        .executionCompletedAt(getExecutionCompletedAt())
        .orgIdentifier(getOrgIdentifier())
        .projectIdentifier(getProjectIdentifier())
        .createdAt(getCreatedAt())
        .lastUpdatedAt(getLastUpdatedAt())
        .build();
  }
}
