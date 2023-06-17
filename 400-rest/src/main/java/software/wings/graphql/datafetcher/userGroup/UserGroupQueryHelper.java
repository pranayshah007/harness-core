/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.userGroup;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.mongo.MongoConfig.NO_LIMIT;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.beans.UserInvite;
import software.wings.beans.security.UserGroup;
import software.wings.graphql.datafetcher.AccountThreadLocal;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.usergroup.QLUserGroupFilter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.CriteriaContainer;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Singleton
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class UserGroupQueryHelper {
  @Inject private DataFetcherUtils utils;
  @Inject private HPersistence hPersistence;

  public void setQuery(List<QLUserGroupFilter> filters, Query query) {
    if (isEmpty(filters)) {
      return;
    }

    Query<UserGroup> memberIdsQuery = populateAccountFilter(UserGroup.class);

    CriteriaContainer memberIdsCriteria = null;

    for (QLUserGroupFilter filter : filters) {
      if (filter.getUser() != null) {
        QLIdFilter userFilter = filter.getUser();
        memberIdsCriteria = utils.getIdFilterCriteria(memberIdsQuery, "memberIds", userFilter);
      }
    }

    Query<User> userQuery = hPersistence.createQuery(User.class);
    filters.forEach(filter -> {
      FieldEnd<? extends Query<User>> userField;

      if (filter.getUser() != null) {
        userField = userQuery.field("_id");
        QLIdFilter userQueryFilter = filter.getUser();
        utils.setIdFilter(userField, userQueryFilter);
      }
    });

    List<String> emailIds = new ArrayList<>();
    List<String> pendingAccounts = new ArrayList<>();
    try (HIterator<User> iterator = new HIterator<>(userQuery.fetch())) {
      while (iterator.hasNext()) {
        User user = iterator.next();
        emailIds.add(user.getEmail());
        pendingAccounts.addAll(user.getPendingAccounts().stream().map(Account::getUuid).collect(Collectors.toList()));
      }
    }

    Query<UserInvite> userInviteQuery = populateAccountFilter(UserInvite.class);
    FieldEnd<? extends Query<UserInvite>> userInviteField = userInviteQuery.field("email");
    userInviteField.in(emailIds);
    Set<String> userGroups = new HashSet<>();
    try (HIterator<UserInvite> iterator = new HIterator<>(userInviteQuery.limit(NO_LIMIT).fetch())) {
      while (iterator.hasNext()) {
        UserInvite userInvite = iterator.next();
        if (pendingAccounts.contains(userInvite.getAccountId())) {
          userGroups.addAll(userInvite.getUserGroups().stream().map(UserGroup::getUuid).collect(Collectors.toSet()));
        }
      }
    }

    Query<UserGroup> userGroupsQuery = populateAccountFilter(UserGroup.class);
    CriteriaContainer userGroupsCriteria = userGroupsQuery.criteria("_id").in(userGroups);

    if (memberIdsCriteria == null) {
      return;
    }

    query.or(memberIdsCriteria, userGroupsCriteria);
  }

  public Query populateAccountFilter(Class entityClass) {
    Query query = hPersistence.createQuery(entityClass);
    final String accountId = getAccountId();
    if (accountId != null) {
      query.filter("accountId", accountId);
      return query;
    }
    return query;
  }

  public String getAccountId() {
    return AccountThreadLocal.get();
  }
}
