/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.app.datafetcher.delegate;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.app.schema.query.delegate.QLDelegateFilter;
import io.harness.app.schema.type.delegate.QLDelegate;
import io.harness.app.schema.type.delegate.QLDelegate.QLDelegateBuilder;
import io.harness.app.schema.type.delegate.QLDelegateList;
import io.harness.app.schema.type.delegate.QLDelegateList.QLDelegateListBuilder;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;

import software.wings.beans.DelegateConnection;
import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;
import software.wings.service.impl.DelegateConnectionDao;

import com.google.inject.Inject;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import org.mongodb.morphia.query.FieldEnd;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;

@OwnedBy(DEL)
public class DelegateListDataFetcher
    extends AbstractConnectionV2DataFetcher<QLDelegateFilter, QLNoOpSortCriteria, QLDelegateList> {
  @Inject private DelegateConnectionDao delegateConnectionDao;

  @Override
  @AuthRule(permissionType = PermissionAttribute.PermissionType.MANAGE_DELEGATES)
  protected QLDelegateList fetchConnection(List<QLDelegateFilter> qlDelegateFilters,
      QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    Query<Delegate> delegateQuery = populateFilters(wingsPersistence, qlDelegateFilters, Delegate.class, true)
                                        .order(Sort.descending(DelegateKeys.createdAt));

    QLDelegateListBuilder delegateListBuilder = QLDelegateList.builder();
    delegateListBuilder.pageInfo(utils.populate(pageQueryParameters, delegateQuery, delegate -> {
      QLDelegateBuilder qlBuilder = QLDelegate.builder();
      List<DelegateConnection> delegateConnections =
          delegateConnectionDao.list(delegate.getAccountId(), delegate.getUuid());
      DelegateController.populateQLDelegate(delegate, qlBuilder, delegateConnections);
      delegateListBuilder.node(qlBuilder.build());
    }));
    return delegateListBuilder.build();
  }

  @Override
  protected void populateFilters(List<QLDelegateFilter> qlDelegateFilters, Query delegateQuery) {
    if (isEmpty(qlDelegateFilters)) {
      return;
    }
    qlDelegateFilters.forEach(qlDelegateFilter -> {
      FieldEnd<? extends Query<Delegate>> delegateField;
      if (!isEmpty(qlDelegateFilter.getAccountId())) {
        utils.setStringFilter(delegateQuery.field(DelegateKeys.accountId), qlDelegateFilter.getAccountId());
      }
      if (!isEmpty(qlDelegateFilter.getDelegateName())) {
        utils.setStringFilter(delegateQuery.field(DelegateKeys.delegateName), qlDelegateFilter.getDelegateName());
      }
      if (qlDelegateFilter.getDelegateStatus() != null) {
        utils.setStringFilter(
            delegateQuery.field(DelegateKeys.status), qlDelegateFilter.getDelegateStatus().getStringValue());
      }
      if (qlDelegateFilter.getDelegateType() != null) {
        delegateField = delegateQuery.field(DelegateKeys.delegateType);
        String type = qlDelegateFilter.getDelegateType().getStringValue();
        utils.setStringFilter(delegateField, type);
      }
    });
  }

  @Override
  protected QLDelegateFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    return null;
  }
}
