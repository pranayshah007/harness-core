/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.entities;

import io.harness.ConnectorConstants;
import io.harness.NGCommonEntityConstants;
import io.harness.annotations.StoreIn;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
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
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotBlank;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

@Data
@Builder
@StoreIn(DbAliases.CENG)
@FieldNameConstants(innerTypeName = "GovernancePolicySetKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity(value = "governancePolicySet", noClassnameStored = true)
@Schema(description = "This object will contain the complete definition of a ccm governance policy set")
public class GovernancePolicySet implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess,
        CreatedByAware, UpdatedByAware {
    public static List<MongoIndex> mongoIndexes() {
        return ImmutableList.<MongoIndex>builder()
                .add(CompoundMongoIndex.builder().name("accountId").field(GovernancePolicySet.GovernancePolicySetKeys.accountId).build())
                .build();
    }

    @Id
    @Schema(description = "unique id") String uuid;
    @Schema(description = "account id") String accountId;
    @NotNull @NotBlank @NGEntityName @Schema(description = ConnectorConstants.CONNECTOR_NAME) String name;
    @NotNull @NotBlank @EntityIdentifier @Schema(description = ConnectorConstants.CONNECTOR_IDENTIFIER_MSG) String identifier;
    @Schema(description = NGCommonEntityConstants.DESCRIPTION) String description;
    @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) String orgIdentifier;
    @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier;

    @Schema(description = NGCommonEntityConstants.TAGS) Map<String, String> tags;

    @Schema(description = "pinned") boolean pinned;
    @Schema(description = "Cron interval at which to execute the policies") String policySetExecutionCron;
    @Schema(description = "list of policy Ids part of this policy set") List<String> policySetPolicies;
    @Schema(description = "list of target regions on which to execute the policies") List<String> policySetTargetRegions;
    @Schema(description = "list of target accounts on which to execute the policies") List<String> policySetTargetAccounts;
    @Schema(description = "isDeleted") boolean isDeleted;
    @Schema(description = "isEnabled") boolean isEnabled;
    // TOOD: Create/use existing type here
    @Schema(description = "cloud provider (aws only)")  String cloudProvider;

    @Schema(description = NGCommonEntityConstants.CREATED_AT_MESSAGE) long createdAt;
    @Schema(description = NGCommonEntityConstants.UPDATED_AT_MESSAGE) long lastUpdatedAt;
    @Schema(description = "created by") private EmbeddedUser createdBy;
    @Schema(description = "updated by") private EmbeddedUser lastUpdatedBy;

    // Adding custom setters for Jackson to set empty string as null
    public void setOrgIdentifier(String orgIdentifier) {
        this.orgIdentifier = isEmpty(orgIdentifier) ? null : orgIdentifier;
    }

    public void setProjectIdentifier(String projectIdentifier) {
        this.projectIdentifier = isEmpty(projectIdentifier) ? null : projectIdentifier;
    }

    public GovernancePolicySet toDTO() {
        return GovernancePolicySet.builder()
                .uuid(getUuid())
                .accountId(getAccountId())
                .orgIdentifier(getOrgIdentifier())
                .projectIdentifier(getProjectIdentifier())
                .name(getName())
                .pinned(isPinned())
                .tags(getTags())
                .description(getDescription())
                .policySetPolicies(getPolicySetPolicies())
                .policySetTargetRegions(getPolicySetTargetRegions())
                .policySetTargetAccounts(getPolicySetTargetAccounts())
                .policySetExecutionCron(getPolicySetExecutionCron())
                .cloudProvider(getCloudProvider())
                .isDeleted(isDeleted())
                .isEnabled(isEnabled())
                .createdAt(getCreatedAt())
                .lastUpdatedAt(getLastUpdatedAt())
                .createdBy(getCreatedBy())
                .lastUpdatedBy(getLastUpdatedBy())
                .build();
    }

}
