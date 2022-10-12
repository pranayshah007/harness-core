package io.harness.ccm.remote.resources.policies;

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
@FieldNameConstants(innerTypeName = "PolicyId")
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity(value = "governancePolicy", noClassnameStored = true)
@Schema(description = "This object will contain the complete definition of a Cloud Cost Policies")

public final class PolicyStore implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess,
                                          CreatedByAware, UpdatedByAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("policy")
                 .field(PolicyId.uuid)
                 .field(PolicyId.accountId)
                 .field(PolicyId.cloudProvider)
                 .build())
        .add(CompoundMongoIndex.builder().name("sort1").field(PolicyId.lastUpdatedAt).build())
        .add(CompoundMongoIndex.builder().name("sort2").field(PolicyId.createdAt).build())
        .build();
  }
  @Id @Schema(description = "unique id") String uuid;
  @Schema(description = "account id") String accountId;
  @Schema(description = "Identifier") String name;
  @Schema(description = NGCommonEntityConstants.RESOURCE) String resource;
  @Schema(description = NGCommonEntityConstants.DESCRIPTION) String description;
  @Schema(description = NGCommonEntityConstants.POLICY) String policyYaml;
  @Schema(description = NGCommonEntityConstants.TAGS) List<String> tags;
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier;
  @Schema(description = "cloudProvider") String cloudProvider;
  @Schema(description = "versionLabel") String versionLabel;
  @Schema(description = "isStablePolicy") String isStablePolicy;
  @Schema(description = "versionLabel") String isOOTBPolicy;
  @Schema(description = NGCommonEntityConstants.CREATED_AT_MESSAGE) long createdAt;
  @Schema(description = NGCommonEntityConstants.UPDATED_AT_MESSAGE) long lastUpdatedAt;
  @Schema(description = "created by") private EmbeddedUser createdBy;
  @Schema(description = "updated by") private EmbeddedUser lastUpdatedBy;

  public PolicyStore toDTO() {
    return PolicyStore.builder()
        .uuid(getUuid())
        .accountId(getAccountId())
        .name(getName())
        .resource(getResource())
        .description(getDescription())
        .policyYaml(getPolicyYaml())
        .cloudProvider(getCloudProvider())
        .versionLabel(getVersionLabel())
        .isStablePolicy(getIsStablePolicy())
        .isOOTBPolicy(getIsOOTBPolicy())
        .tags(getTags())
        .orgIdentifier(getOrgIdentifier())
        .projectIdentifier(getProjectIdentifier())
        .createdAt(getCreatedAt())
        .lastUpdatedAt(getLastUpdatedAt())
        .createdBy(getCreatedBy())
        .lastUpdatedBy(getLastUpdatedBy())
        .build();
  }
}
