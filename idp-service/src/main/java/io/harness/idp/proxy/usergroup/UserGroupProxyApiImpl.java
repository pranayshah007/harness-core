/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.usergroup;

import static io.harness.remote.client.NGRestUtils.getGeneralResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.annotations.IdpServiceAuthIfHasApiKey;
import io.harness.ng.core.usergroups.filter.UserGroupFilterType;
import io.harness.spec.server.idp.v1.UserGroupProxyApi;
import io.harness.usergroupsng.remote.UserGroupsNGClient;

import com.google.inject.Inject;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class UserGroupProxyApiImpl implements UserGroupProxyApi {
  UserGroupsNGClient userGroupsNGClient;

  @IdpServiceAuthIfHasApiKey
  @Override
  public Response getAllUsergroups(String harnessAccount, Integer page, Integer limit, String filterType) {
    Object entity = getGeneralResponse(userGroupsNGClient.getUserGroupAggregateList(
        harnessAccount, null, null, page, limit, null, null, null, UserGroupFilterType.valueOf(filterType), 6));
    return Response.ok(entity).build();
  }

  @IdpServiceAuthIfHasApiKey
  @Override
  public Response getUserGroup(String userGroupId, String harnessAccount) {
    Object entity =
        getGeneralResponse(userGroupsNGClient.getUserGroupAggregate(userGroupId, harnessAccount, null, null));
    return Response.ok(entity).build();
  }
}
