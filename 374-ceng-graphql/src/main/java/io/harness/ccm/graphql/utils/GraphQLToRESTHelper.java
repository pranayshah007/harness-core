/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.utils;

import static io.harness.ccm.graphql.utils.GraphQLUtils.DEFAULT_LIMIT;
import static io.harness.ccm.graphql.utils.GraphQLUtils.DEFAULT_OFFSET;

import io.harness.NGCommonEntityConstants;
import io.harness.ccm.graphql.dto.recommendation.K8sRecommendationFilterDTO;

import com.google.common.collect.ImmutableMap;
import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import io.leangen.graphql.execution.ResolutionEnvironment;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class GraphQLToRESTHelper {
  public static void setDefaultFilterValues(@NonNull K8sRecommendationFilterDTO filter) {
    if (filter.getMinSaving() == null) {
      filter.setMinSaving(0D);
    }
  }

  public static void setDefaultPaginatedFilterValues(@NonNull K8sRecommendationFilterDTO filter) {
    setDefaultFilterValues(filter);

    if (filter.getOffset() == null) {
      filter.setOffset(DEFAULT_OFFSET);
    }
    if (filter.getLimit() == null) {
      filter.setLimit(DEFAULT_LIMIT);
    }
  }

  public static ResolutionEnvironment createResolutionEnv(String accountId) {
    return createResolutionEnv(accountId, ImmutableMap.of());
  }

  public static ResolutionEnvironment createResolutionEnv(String accountId, K8sRecommendationFilterDTO filter) {
    return createResolutionEnv(accountId, ImmutableMap.of("filter", filter));
  }

  private ResolutionEnvironment createResolutionEnv(String accountId, @Nullable Map<String, Object> variables) {
    final GraphQLContext graphQLContext =
        GraphQLContext.newContext().of(NGCommonEntityConstants.ACCOUNT_KEY, accountId).build();
    final DataFetchingEnvironment dataFetchingEnvironment =
        DataFetchingEnvironmentImpl.newDataFetchingEnvironment().context(graphQLContext).variables(variables).build();

    return new ResolutionEnvironment(null, dataFetchingEnvironment, null, null, null, null);
  }
}
