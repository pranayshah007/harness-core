/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.datafetcher.delegate;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.app.schema.query.delegate.QLDelegateScopeFilter;
import io.harness.app.schema.type.delegate.QLDelegateScope;
import io.harness.app.schema.type.delegate.QLDelegateScope.QLDelegateScopeBuilder;
import io.harness.app.schema.type.delegate.QLDelegateScopeList;
import io.harness.app.schema.type.delegate.QLDelegateScopeList.QLDelegateScopeListBuilder;
import io.harness.delegate.beans.DelegateScope;
import io.harness.delegate.beans.DelegateScope.DelegateScopeKeys;

import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.DelegateScopeService;

import com.google.inject.Inject;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.List;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;

public class DelegateScopeListDataFetcher
    extends AbstractConnectionV2DataFetcher<QLDelegateScopeFilter, QLNoOpSortCriteria, QLDelegateScopeList> {
  @Inject private DelegateScopeService delegateScopeService;

  @Override
  @AuthRule(permissionType = PermissionAttribute.PermissionType.MANAGE_DELEGATES)
  protected QLDelegateScopeList fetchConnection(List<QLDelegateScopeFilter> filters,
      QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    Query<DelegateScope> query = populateFilters(wingsPersistence, filters, DelegateScope.class, true)
                                     .order(Sort.descending(DelegateScopeKeys.createdAt));
    QLDelegateScopeListBuilder delegateScopeListBuilder = QLDelegateScopeList.builder();
    delegateScopeListBuilder.nodes(new ArrayList<>());
    delegateScopeListBuilder.pageInfo(utils.populate(pageQueryParameters, query, delegateScope -> {
      QLDelegateScopeBuilder qlDelegateScopeBuilder = QLDelegateScope.builder();
      DelegateController.populateQLDelegateScope(delegateScope, qlDelegateScopeBuilder);
      delegateScopeListBuilder.build().getNodes().add(qlDelegateScopeBuilder.build());
    }));
    return delegateScopeListBuilder.build();
  }

  @Override
  protected void populateFilters(List<QLDelegateScopeFilter> filters, Query query) {
    if (isEmpty(filters)) {
      return;
    }
    filters.forEach(qlFilter -> {
      if (!isEmpty(qlFilter.getAccountId())) {
        utils.setStringFilter(query.field(DelegateScopeKeys.accountId), qlFilter.getAccountId());
      }
      if (!isEmpty(qlFilter.getScopeName())) {
        utils.setStringFilter(query.field(DelegateScopeKeys.name), qlFilter.getScopeName());
      }
    });
  }
  @Override
  protected QLDelegateScopeFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    return null;
  }
}
