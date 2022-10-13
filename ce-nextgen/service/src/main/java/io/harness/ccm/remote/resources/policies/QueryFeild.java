package io.harness.ccm.remote.resources.policies;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "QueryFeild", description = "This has the query to list the policies")
public class QueryFeild {
  @Schema(description = "unique id") String uuid;
  @Schema(description = "account id") String accountId;
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier;
  @Schema(description = NGCommonEntityConstants.RESOURCE) String resource;
  @Schema(description = NGCommonEntityConstants.TAGS) String tags;
  @Schema(description = "cloudProvider") String cloudProvider;
  @Schema(description = "isStablePolicy") String isStablePolicy;
  @Schema(description = "isOOTBPolicy") String isOOTBPolicy;

  @Builder
  public QueryFeild toDTO(String uuid, String accountId, String orgIdentifier, String projectIdentifier,
      String resource, String tags, String cloudProvider, String isStablePolicy, String isOOTBPolicy) {
    this.uuid = uuid;
    this.accountId = accountId;
    this.orgIdentifier = orgIdentifier;
    this.projectIdentifier = projectIdentifier;
    this.resource = resource;
    this.tags = tags;
    this.cloudProvider = cloudProvider;
    this.isStablePolicy = isStablePolicy;
    this.isOOTBPolicy = isOOTBPolicy;
    return QueryFeild.this;
  }
}