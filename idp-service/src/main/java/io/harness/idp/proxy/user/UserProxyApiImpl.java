/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.user;

import static io.harness.remote.client.NGRestUtils.getGeneralResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.annotations.IdpServiceAuthIfHasApiKey;
import io.harness.spec.server.idp.v1.UserProxyApi;
import io.harness.userng.remote.UserNGClient;

import com.google.inject.Inject;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class UserProxyApiImpl implements UserProxyApi {
  UserNGClient userNGClient;

  @IdpServiceAuthIfHasApiKey
  @Override
  public Response getAllUsers(String harnessAccount, Integer page, Integer limit) {
    Object entity =
        getGeneralResponse(userNGClient.getAggregatedUsers(harnessAccount, null, null, null, page, limit, null, null));
    return Response.ok(entity).build();
  }
}
