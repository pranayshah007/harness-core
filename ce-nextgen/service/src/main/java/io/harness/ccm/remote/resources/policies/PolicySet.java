package io.harness.ccm.remote.resources.policies;


import com.google.common.collect.ImmutableList;
import io.harness.NGCommonEntityConstants;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;
import io.harness.annotations.StoreIn;
import io.harness.ng.DbAliases;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

import java.util.List;

@Data
@Builder
@StoreIn(DbAliases.CENG)
@FieldNameConstants(innerTypeName = "PolicySetId")
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity(value = "governancePolicySets", noClassnameStored = true)
@Schema(description = "This object will contain the complete definition of a Cloud Cost Policy set")

public final class PolicySet implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess,
        CreatedByAware, UpdatedByAware{

    @Id
    @Schema(description = "unique id") String uuid;
    @Schema(description = "account id") String accountId;
    @Schema(description = "Identifier") String name;
    @Schema(description = NGCommonEntityConstants.DESCRIPTION) String description;
    @Schema(description = NGCommonEntityConstants.TAGS)
    List<String> tags;
    @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) String orgIdentifier;
    @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier;
    @Schema(description = "cloudProvider") String cloudProvider;
    @Schema(description = "policySetPolicies") List<String> policySetPolicies;
    @Schema(description = "policySetExecutionCron") String policySetExecutionCron;
    @Schema(description = "policySetTargetAccounts") List<String>  policySetTargetAccounts;
    @Schema(description = "policySetTargetRegions") List<String>  policySetTargetRegions;
    @Schema(description = "deleted") String deleted;
    @Schema(description = "isEnabled") String isEnabled;
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
    public PolicySet toDTO() {
        return PolicySet.builder()
                .uuid(getUuid())
                .accountId(getAccountId())
                .name(getName())
                .description(getDescription())
                .orgIdentifier(getOrgIdentifier())
                .projectIdentifier(getProjectIdentifier())
                .cloudProvider(getCloudProvider())
                .tags(getTags())
                .policySetPolicies(getPolicySetPolicies())
                .policySetExecutionCron(getPolicySetExecutionCron())
                .policySetTargetAccounts(getPolicySetTargetAccounts())
                .policySetTargetRegions(getPolicySetTargetRegions())
                .deleted(getDeleted())
                .isEnabled(getIsEnabled())
                .createdAt(getCreatedAt())
                .lastUpdatedAt(getLastUpdatedAt())
                .createdBy(getCreatedBy())
                .lastUpdatedBy(getLastUpdatedBy())
                .build();
    }

}
