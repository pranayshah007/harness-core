/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.clients;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

@OwnedBy(IDP)
public interface BackstageCatalogResourceClient {
  String CATALOG_API = "catalog";

  @POST(CATALOG_API + "/locations")
  Call<ResponseDTO<Object>> createCatalogLocation(@Body BackstageCatalogLocationCreateRequest request);
}
