/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.dto.recommendation;

import io.leangen.graphql.annotations.types.GraphQLUnion;

@GraphQLUnion(name = "recommendationDetails", description = "This union of all types of recommendations",
    possibleTypes = {WorkloadRecommendationDTO.class, NodeRecommendationDTO.class, ECSRecommendationDTO.class,
        EC2RecommendationDTO.class, RuleRecommendationDTO.class})
public interface RecommendationDetailsDTO {}
