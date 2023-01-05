/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.user;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import software.wings.beans.User;
import software.wings.beans.User.UserKeys;
import software.wings.graphql.datafetcher.AbstractObjectDataFetcher;
import software.wings.graphql.schema.query.QLUserQueryParameters;
import software.wings.graphql.schema.type.QLUser;
import software.wings.graphql.schema.type.QLUser.QLUserBuilder;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;
import dev.morphia.query.CriteriaContainer;
import dev.morphia.query.Query;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PL)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class UserDataFetcher extends AbstractObjectDataFetcher<QLUser, QLUserQueryParameters> {
  public static final String USER_DOES_NOT_EXIST_MSG = "User does not exist";
  @Inject HPersistence persistence;

  @Override
  @AuthRule(permissionType = PermissionAttribute.PermissionType.USER_PERMISSION_READ)
  public QLUser fetch(QLUserQueryParameters qlQuery, String accountId) {
    User user = null;
    if (qlQuery.getId() != null) {
      user = getSearchByIdQuery(accountId, qlQuery.getId()).get();
    }
    if (qlQuery.getName() != null) {
      try (HIterator<User> iterator = new HIterator<>(getSearchByNameQuery(accountId, qlQuery.getName()).fetch())) {
        if (iterator.hasNext()) {
          user = iterator.next();
        }
      }
    }
    if (qlQuery.getEmail() != null) {
      try (HIterator<User> iterator = new HIterator<>(getSearchByEmailQuery(accountId, qlQuery.getEmail()).fetch())) {
        if (iterator.hasNext()) {
          user = iterator.next();
        }
      }
    }
    if (user == null) {
      throw new InvalidRequestException(USER_DOES_NOT_EXIST_MSG, WingsException.USER);
    }
    final QLUserBuilder builder = QLUser.builder();
    UserController.populateUser(user, builder);
    return builder.build();
  }

  private Query<User> getSearchByIdQuery(String accountId, String _id) {
    Query<User> query = persistence.createQuery(User.class, excludeAuthority);
    CriteriaContainer inviteAccepted =
        query.and(query.criteria("_id").equal(_id), query.criteria(UserKeys.accounts).hasThisOne(accountId));
    CriteriaContainer invitePending =
        query.and(query.criteria("_id").equal(_id), query.criteria(UserKeys.pendingAccounts).hasThisOne(accountId));
    query.or(inviteAccepted, invitePending);
    return query;
  }

  private Query<User> getSearchByNameQuery(String accountId, String name) {
    Query<User> query = persistence.createQuery(User.class, excludeAuthority);
    CriteriaContainer inviteAccepted =
        query.and(query.criteria(UserKeys.name).equal(name), query.criteria(UserKeys.accounts).hasThisOne(accountId));
    CriteriaContainer invitePending = query.and(
        query.criteria(UserKeys.name).equal(name), query.criteria(UserKeys.pendingAccounts).hasThisOne(accountId));
    query.or(inviteAccepted, invitePending);
    return query;
  }

  private Query<User> getSearchByEmailQuery(String accountId, String email) {
    email = email.toLowerCase();
    Query<User> query = persistence.createQuery(User.class, excludeAuthority);
    CriteriaContainer inviteAccepted =
        query.and(query.criteria(UserKeys.email).equal(email), query.criteria(UserKeys.accounts).hasThisOne(accountId));
    CriteriaContainer invitePending = query.and(
        query.criteria(UserKeys.email).equal(email), query.criteria(UserKeys.pendingAccounts).hasThisOne(accountId));
    query.or(inviteAccepted, invitePending);
    return query;
  }
}
