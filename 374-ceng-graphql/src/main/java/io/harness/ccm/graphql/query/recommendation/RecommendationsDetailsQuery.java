/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.query.recommendation;

import static io.harness.annotations.dev.HarnessTeam.CE;

import static com.google.common.base.MoreObjects.firstNonNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.beans.recommendation.NodePoolId;
import io.harness.ccm.commons.beans.recommendation.ResourceType;
import io.harness.ccm.commons.beans.recommendation.models.RecommendNodePoolClusterRequest;
import io.harness.ccm.graphql.core.recommendation.EC2RecommendationService;
import io.harness.ccm.graphql.core.recommendation.ECSRecommendationService;
import io.harness.ccm.graphql.core.recommendation.NodeRecommendationService;
import io.harness.ccm.graphql.core.recommendation.RuleRecommendationService;
import io.harness.ccm.graphql.core.recommendation.WorkloadRecommendationService;
import io.harness.ccm.graphql.dto.recommendation.RecommendationDetailsDTO;
import io.harness.ccm.graphql.dto.recommendation.RecommendationItemDTO;
import io.harness.ccm.graphql.utils.GraphQLUtils;
import io.harness.ccm.graphql.utils.annotations.GraphQLApi;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLContext;
import io.leangen.graphql.annotations.GraphQLEnvironment;
import io.leangen.graphql.annotations.GraphQLNonNull;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.execution.ResolutionEnvironment;
import java.time.OffsetDateTime;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@GraphQLApi
@OwnedBy(CE)
public class RecommendationsDetailsQuery {
  @Inject private GraphQLUtils graphQLUtils;
  @Inject private WorkloadRecommendationService workloadRecommendationService;
  @Inject private NodeRecommendationService nodeRecommendationService;
  @Inject private ECSRecommendationService ecsRecommendationService;
  @Inject private EC2RecommendationService ec2RecommendationService;
  @Inject private RuleRecommendationService ruleRecommendationService;

  /**
   * Note: If this query becomes slow due to n+1 serial calls in future. Then,
   * we can use {@code CompletableFuture<RecommendationDetailsDTO>} to parallelize it,
   * with optional dataLoader to make 1+1 db calls instead of n+1.
   */
  @GraphQLQuery(description = "recommendation details/drillDown")
  public RecommendationDetailsDTO recommendationDetails(@GraphQLContext RecommendationItemDTO nodeDTO,
      @GraphQLArgument(name = "startTime", description = "defaults to Now().minusDays(7)") OffsetDateTime startTime,
      @GraphQLArgument(name = "endTime", description = "defaults to Now()") OffsetDateTime endTime,
      @GraphQLArgument(name = "bufferPercentage", description = "defaults to zero", defaultValue = "0")
      Long bufferPercentage, @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountIdentifier = graphQLUtils.getAccountIdentifier(env);

    return recommendationDetailsInternal(
        accountIdentifier, nodeDTO.getResourceType(), nodeDTO.getId(), startTime, endTime, bufferPercentage);
  }

  @GraphQLQuery(description = "recommendation details/drillDown")
  public RecommendationDetailsDTO recommendationDetails(@GraphQLNonNull @GraphQLArgument(name = "id") String id,
      @GraphQLNonNull @GraphQLArgument(name = "resourceType") ResourceType resourceType,
      @GraphQLArgument(name = "startTime", description = "defaults to Now().minusDays(7)") OffsetDateTime startTime,
      @GraphQLArgument(name = "endTime", description = "defaults to Now()") OffsetDateTime endTime,
      @GraphQLArgument(name = "bufferPercentage", description = "defaults to zero", defaultValue = "0")
      Long bufferPercentage, @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountIdentifier = graphQLUtils.getAccountIdentifier(env);

    return recommendationDetailsInternal(accountIdentifier, resourceType, id, startTime, endTime, bufferPercentage);
  }

  @GraphQLQuery(name = "nodeRecommendationRequest")
  public RecommendNodePoolClusterRequest nodeRecommendationRequest(
      @GraphQLNonNull @GraphQLArgument(name = "nodePoolId") NodePoolId nodePoolId,
      @GraphQLArgument(name = "startTime", description = "defaults to Now().minusDays(7)") OffsetDateTime startTime,
      @GraphQLArgument(name = "endTime", description = "defaults to Now()") OffsetDateTime endTime,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountIdentifier = graphQLUtils.getAccountIdentifier(env);

    return nodeRecommendationService.constructRecommendationRequest(accountIdentifier, nodePoolId,
        firstNonNull(startTime, OffsetDateTime.now().minusDays(7)), firstNonNull(endTime, OffsetDateTime.now()));
  }

  private RecommendationDetailsDTO recommendationDetailsInternal(@NotNull final String accountIdentifier,
      @NotNull ResourceType resourceType, @NotNull String id, @Nullable OffsetDateTime startTime,
      @Nullable OffsetDateTime endTime, @Nullable Long bufferPercentage) {
    switch (resourceType) {
      case WORKLOAD:
        return workloadRecommendationService.getWorkloadRecommendationById(accountIdentifier, id,
            firstNonNull(startTime, OffsetDateTime.now().minusDays(7)), firstNonNull(endTime, OffsetDateTime.now()));
      case NODE_POOL:
        return nodeRecommendationService.getRecommendation(accountIdentifier, id);
      case ECS_SERVICE:
        return ecsRecommendationService.getECSRecommendationById(accountIdentifier, id,
            firstNonNull(startTime, OffsetDateTime.now().minusDays(7)), firstNonNull(endTime, OffsetDateTime.now()),
            bufferPercentage);
      case EC2_INSTANCE:
        return ec2RecommendationService.getEC2RecommendationById(accountIdentifier, id);
      case GOVERNANCE:
        return ruleRecommendationService.getRuleRecommendation(id, accountIdentifier);
      default:
        throw new InvalidRequestException(String.format("Recommendation not yet implemented for %s", resourceType));
    }
  }
}
