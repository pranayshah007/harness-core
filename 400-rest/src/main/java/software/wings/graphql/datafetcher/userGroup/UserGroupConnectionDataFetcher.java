/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.userGroup;

import static io.harness.annotations.dev.HarnessTeam.DX;

import static software.wings.graphql.utils.nameservice.NameService.user;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.GraphQLException;
import io.harness.exception.WingsException;

import software.wings.beans.security.UserGroup;
import software.wings.beans.security.UserGroup.UserGroupKeys;
import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.usergroup.QLUserGroup;
import software.wings.graphql.schema.type.usergroup.QLUserGroup.QLUserGroupBuilder;
import software.wings.graphql.schema.type.usergroup.QLUserGroupConnection;
import software.wings.graphql.schema.type.usergroup.QLUserGroupConnection.QLUserGroupConnectionBuilder;
import software.wings.graphql.schema.type.usergroup.QLUserGroupFilter;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(DX)
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class UserGroupConnectionDataFetcher
    extends AbstractConnectionV2DataFetcher<QLUserGroupFilter, QLNoOpSortCriteria, QLUserGroupConnection> {
  @Inject UserGroupController userGroupController;
  @Inject UserGroupQueryHelper userGroupQueryHelper;

  @Override
  @AuthRule(permissionType = PermissionAttribute.PermissionType.USER_PERMISSION_READ)
  public QLUserGroupConnection fetchConnection(List<QLUserGroupFilter> groupFilters,
      QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    Query<UserGroup> query = populateFilters(wingsPersistence, groupFilters, UserGroup.class, true)
                                 .order(Sort.ascending(UserGroupKeys.name));
    QLUserGroupConnectionBuilder connectionBuilder = QLUserGroupConnection.builder();
    connectionBuilder.pageInfo(utils.populate(pageQueryParameters, query, userGroup -> {
      QLUserGroupBuilder builder = QLUserGroup.builder();
      userGroupController.populateUserGroupOutput(userGroup, builder);
      connectionBuilder.node(builder.build());
    }));
    return connectionBuilder.build();
  }

  @Override
  protected void populateFilters(List<QLUserGroupFilter> filters, Query query) {
    userGroupQueryHelper.setQuery(filters, query);
  }

  @Override
  protected QLUserGroupFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    QLIdFilter idFilter = QLIdFilter.builder()
                              .operator(QLIdOperator.EQUALS)
                              .values(new String[] {(String) utils.getFieldValue(environment.getSource(), value)})
                              .build();
    if (user.equals(key)) {
      return QLUserGroupFilter.builder().user(idFilter).build();
    }
    throw new GraphQLException("Unsupported field " + key + " while generating filter", WingsException.SRE);
  }
}
