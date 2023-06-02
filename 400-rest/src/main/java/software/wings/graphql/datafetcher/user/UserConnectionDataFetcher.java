/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.user;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.UnexpectedException;

import software.wings.beans.User;
import software.wings.beans.User.UserKeys;
import software.wings.beans.security.UserGroup;
import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.QLUser;
import software.wings.graphql.schema.type.QLUser.QLUserBuilder;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.aggregation.user.QLUserFilter;
import software.wings.graphql.schema.type.user.QLUserConnection;
import software.wings.graphql.schema.type.user.QLUserConnection.QLUserConnectionBuilder;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.UserGroupService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import graphql.schema.DataFetchingEnvironment;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class UserConnectionDataFetcher
    extends AbstractConnectionV2DataFetcher<QLUserFilter, QLNoOpSortCriteria, QLUserConnection> {
  private UserGroupService userGroupService;
  private UserQueryHelper userQueryHelper;

  @Inject
  public UserConnectionDataFetcher(UserGroupService userGroupService, UserQueryHelper userQueryHelper) {
    this.userGroupService = userGroupService;
    this.userQueryHelper = userQueryHelper;
  }

  @Override
  @AuthRule(permissionType = PermissionType.USER_PERMISSION_READ)
  public QLUserConnection fetchConnection(
      List<QLUserFilter> filters, QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    Query<User> query = populateFilters(wingsPersistence, filters, User.class, false);
    query.order(Sort.ascending(UserKeys.name));

    QLUserConnectionBuilder connectionBuilder = QLUserConnection.builder();
    connectionBuilder.pageInfo(utils.populate(pageQueryParameters, query, user -> {
      QLUserBuilder builder = QLUser.builder();
      QLUser qlUser = UserController.populateUser(user, builder);
      connectionBuilder.node(qlUser);
    }));
    return connectionBuilder.build();
  }

  @Override
  protected void populateFilters(List<QLUserFilter> filters, Query query) {
    userQueryHelper.setAccountFilter(query, getAccountId());
    userQueryHelper.setQuery(filters, query);
    userQueryHelper.setShowDisabledFilter(filters, query);
  }

  @Override
  protected QLUserFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    if ("userGroup".equals(key)) {
      final List<String> userIdsInUserGroup =
          getUserIdsInUserGroup((String) utils.getFieldValue(environment.getSource(), value));
      if (!userIdsInUserGroup.isEmpty()) {
        return QLUserFilter.builder()
            .user(QLIdFilter.builder()
                      .operator(QLIdOperator.IN)
                      .values(userIdsInUserGroup.toArray(new String[0]))
                      .build())
            .build();
      }
      // this is to handle casde when user group does not have any user. We should not return any user then
      return QLUserFilter.builder()
          .user(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"DUMMY_USER_ID"}).build())
          .build();
    }
    throw new UnexpectedException("Unsupported field " + key + " while generating filter");
  }

  private List<String> getUserIdsInUserGroup(String userGroupId) {
    return Optional.ofNullable(userGroupService.get(getAccountId(), userGroupId, false))
        .map(UserGroup::getMemberIds)
        .orElse(Collections.emptyList());
  }
}
