/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.vault;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * It appears that the latest Vault (v0.11) was switched to use the v2 key/value secret
 * engine by default. The endpoint for KV v2 is /secret/data/$PATH$ Therefore to write
 * secretes at /secret/customer/acme, the API endpoints becomes /secret/data/customer/acme.
 *
 * Please refer to the following Hashicorp documentation for details on secret engine v2.
 * https://www.vaultproject.io/guides/secret-mgmt/versioned-kv.html
 *
 * To handle the newer version of Vault, we will need a new REST client to accommodate the
 * differences compared with v1 Vault.
 */

@OwnedBy(PL)
public interface VaultRestClientV2 {
  String BASE_VAULT_URL = "v1/";

  @POST(BASE_VAULT_URL + "{secretEngine}/data/{path}")
  Call<Void> writeSecret(@Header("X-Vault-Token") String header, @Header("X-Vault-Namespace") String namespace,
      @Path("secretEngine") String secretEngine, @Path("path") String fullPath, @Body VaultSecretValue value);

  @DELETE(BASE_VAULT_URL + "{secretEngine}/data/{path}")
  Call<Void> deleteSecret(@Header("X-Vault-Token") String header, @Header("X-Vault-Namespace") String namespace,
      @Path("secretEngine") String secretEngine, @Path("path") String fullPath);

  @DELETE(BASE_VAULT_URL + "{secretEngine}/metadata/{path}")
  Call<Void> deleteSecretPermanentely(
      @Header("X-Vault-Token") String header, @Path("secretEngine") String secretEngine, @Path("path") String fullPath);

  @GET(BASE_VAULT_URL + "{secretEngine}/data/{path}")
  Call<VaultReadResponseV2> readSecret(@Header("X-Vault-Token") String header,
      @Header("X-Vault-Namespace") String namespace, @Path("secretEngine") String secretEngine,
      @Path("path") String fullPath);

  @GET(BASE_VAULT_URL + "{secretEngine}/metadata/{path}")
  Call<VaultMetadataReadResponse> readSecretMetadata(@Header("X-Vault-Token") String header,
      @Header("X-Vault-Namespace") String namespace, @Path("secretEngine") String secretEngine,
      @Path("path") String fullPath);
}
