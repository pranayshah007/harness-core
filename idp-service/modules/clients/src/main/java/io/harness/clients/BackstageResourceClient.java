/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.clients;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.idp.v1.model.LayoutRequest;

import javax.validation.constraints.NotEmpty;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

@OwnedBy(IDP)
public interface BackstageResourceClient {
  String CATALOG_API = "{accountIdentifier}/idp/api/catalog";
  String LAYOUT_API = "{accountIdentifier}/idp/api/layout";

  @POST(CATALOG_API + "/locations")
  Call<Object> createCatalogLocation(@Path("accountIdentifier") String accountIdentifier,
      @Header("Authorization") String authorization,
      @Body io.harness.clients.BackstageCatalogLocationCreateRequest request);

  @GET(LAYOUT_API)
  Call<Object> getAllLayouts(
      @Header("Authorization") String authorization, @Path("accountIdentifier") String accountIdentifier);

  @GET(LAYOUT_API + "/{layoutId}")
  Call<Object> getLayout(@Header("Authorization") String authorization,
      @Path("accountIdentifier") String accountIdentifier, @Path("layoutId") @NotEmpty String layoutId);

  @GET(LAYOUT_API + "/health")
  Call<Object> getHealth(
      @Header("Authorization") String authorization, @Path("accountIdentifier") String accountIdentifier);

  @POST(LAYOUT_API)
  Call<Object> createLayout(@Body LayoutRequest body, @Header("Authorization") String authorization,
      @Path("accountIdentifier") String accountIdentifier);

  @HTTP(method = "DELETE", path = LAYOUT_API, hasBody = true)
  Call<Object> deleteLayout(@Body LayoutRequest body, @Header("Authorization") String authorization,
      @Path("accountIdentifier") String accountIdentifier);
}
