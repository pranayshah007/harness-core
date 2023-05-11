/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.entities;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.StoreIn;
import io.harness.ccm.commons.beans.recommendation.CCMJiraDetails;
import io.harness.ccm.views.helper.ExecutionSummary;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.bson.types.ObjectId;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@Data
@Builder
@StoreIn(DbAliases.CENG)
@FieldNameConstants(innerTypeName = "RuleRecommendationId")
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity(value = "governanceRecommendation", noClassnameStored = true)
@Schema(description = "This object will contain the complete definition of a Cloud Cost Recommendation")

public final class RuleRecommendation implements PersistentEntity, CreatedAtAware, AccountAccess {
  @Id @Schema(description = "unique id") ObjectId uuid;
  @Schema(description = "name") String name;
  @Schema(description = "resourceType") String resourceType;
  @Schema(description = "actionType") String actionType;
  @Schema(description = "accountId") String accountId;
  @Schema(description = "isValid") Boolean isValid;
  @Schema(description = "executions") List<ExecutionSummary> executions;
  @Schema(description = "JiraDetails") CCMJiraDetails jiraDetails;
  @Schema(description = NGCommonEntityConstants.CREATED_AT_MESSAGE) long createdAt;
  public RuleRecommendation toDTO() {
    return RuleRecommendation.builder()
        .uuid(getUuid())
        .name(getName())
        .resourceType(getResourceType())
        .accountId(getAccountId())
        .isValid(getIsValid())
        .executions(getExecutions())
        .jiraDetails(getJiraDetails())
        .build();
  }
}