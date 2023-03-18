/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.user;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.User;
import software.wings.beans.User.UserKeys;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.user.QLUserFilter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import java.util.List;

@Singleton
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class UserQueryHelper {
  private DataFetcherUtils utils;

  @Inject
  public UserQueryHelper(DataFetcherUtils utils) {
    this.utils = utils;
  }
  public void setQuery(List<QLUserFilter> filters, Query query) {
    if (isEmpty(filters)) {
      return;
    }
    filters.forEach(filter -> {
      if (filter.getUser() != null) {
        FieldEnd<? extends Query<User>> field = query.field("_id");
        QLIdFilter userFilter = filter.getUser();
        utils.setIdFilter(field, userFilter);
      }
    });
  }

  public void setAccountFilter(Query<User> query, final String accountId) {
    query.or(query.criteria("accounts").equal(accountId), query.criteria("pendingAccounts").equal(accountId));
  }

  public void setShowDisabledFilter(List<QLUserFilter> filters, Query<User> query) {
    if (isEmpty(filters)) {
      query.criteria(UserKeys.disabled).notEqual(true);
      return;
    }
    filters.forEach(filter -> {
      if (filter.getIncludeDisabled() != null && !filter.getIncludeDisabled()) {
        query.criteria(UserKeys.disabled).notEqual(true);
      }
    });
  }
}
