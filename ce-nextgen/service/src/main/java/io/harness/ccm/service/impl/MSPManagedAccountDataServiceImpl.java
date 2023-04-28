/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import static io.harness.ccm.views.graphql.QLCEViewFilterOperator.IN;

import io.harness.ccm.commons.entities.CCMField;
import io.harness.ccm.graphql.query.perspectives.PerspectivesQuery;
import io.harness.ccm.remote.utils.GraphQLToRESTHelper;
import io.harness.ccm.remote.utils.RESTToGraphQLHelper;
import io.harness.ccm.service.intf.MSPManagedAccountDataService;
import io.harness.ccm.views.graphql.QLCEViewFieldInput;
import io.harness.ccm.views.graphql.QLCEViewFilter;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;

import com.google.inject.Inject;
import io.leangen.graphql.execution.ResolutionEnvironment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MSPManagedAccountDataServiceImpl implements MSPManagedAccountDataService {
  @Inject private PerspectivesQuery perspectivesQuery;

  @Override
  public List<String> getEntityList(String managedAccountId, CCMField entity, Integer limit, Integer offset) {
    try {
      final ResolutionEnvironment env = GraphQLToRESTHelper.createResolutionEnv(managedAccountId);
      QLCEViewFieldInput entityConvertedToFieldInput = RESTToGraphQLHelper.getViewFieldInputFromCCMField(entity);
      List<QLCEViewFilterWrapper> filters = new ArrayList<>();
      filters.add(QLCEViewFilterWrapper.builder()
                      .idFilter(QLCEViewFilter.builder()
                                    .field(entityConvertedToFieldInput)
                                    .operator(IN)
                                    .values(Collections.singletonList("").toArray(new String[0]))
                                    .build())
                      .build());
      return perspectivesQuery
          .perspectiveFilters(Collections.emptyList(), filters, Collections.emptyList(), Collections.emptyList(), limit,
              offset, false, env)
          .getValues();
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }
}
