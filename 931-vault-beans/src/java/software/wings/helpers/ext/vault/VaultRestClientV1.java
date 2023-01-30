/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.vault;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

@OwnedBy(PL)
public interface VaultRestClientV1 {
  String BASE_VAULT_URL = "v1/";

  @POST(BASE_VAULT_URL + "{secretEngine}/{path}")
  Call<Void> writeSecret(@Header("X-Vault-Token") String header, @Header("X-Vault-Namespace") String namespace,
      @Path("secretEngine") String secretEngine, @Path("path") String fullPath, @Body Map<String, String> value);

  @DELETE(BASE_VAULT_URL + "{secretEngine}/{path}")
  Call<Void> deleteSecret(@Header("X-Vault-Token") String header, @Header("X-Vault-Namespace") String namespace,
      @Path("secretEngine") String secretEngine, @Path("path") String fullPath);

  @DELETE(BASE_VAULT_URL + "{secretEngine}/{path}")
  Call<Void> deleteSecretPermanentely(
      @Header("X-Vault-Token") String header, @Path("secretEngine") String secretEngine, @Path("path") String fullPath);

  @GET(BASE_VAULT_URL + "{secretEngine}/{path}")
  Call<VaultReadResponse> readSecret(@Header("X-Vault-Token") String header,
      @Header("X-Vault-Namespace") String namespace, @Path("secretEngine") String secretEngine,
      @Path("path") String fullPath);
}
