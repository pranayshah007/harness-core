/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.publicaccess;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.accesscontrol.v1.model.PublicAccessRequest;

import javax.validation.constraints.NotNull;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.PL)
public interface PublicAccessClient {
  String PUBLIC_ACCESS_API = "v1/public-access";

  @POST(PUBLIC_ACCESS_API + "/enable")
  Call<Boolean> enable(@Body PublicAccessRequest publicAccessRequest, @Header("Harness-Account") String harnessAccount);

  @POST(PUBLIC_ACCESS_API + "/is-resource-public")
  Call<Boolean> isResourcePublic(
      @Body PublicAccessRequest publicAccessRequest, @Header("Harness-Account") String harnessAccount);

  @PUT(PUBLIC_ACCESS_API + "/disable")
  Call<Boolean> disable(@Query("account") @NotNull String account, @Query("org") @NotNull String org,
      @Query("project") @NotNull String project, @Query("resource_type") @NotNull String resourceType,
      @Query("resource_identifier") @NotNull String resourceIdentifier,
      @Header("Harness-Account") String harnessAccount);
}
