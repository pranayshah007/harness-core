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
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * We need a way to automatically determine the Vault server's secret engine version. The
 * secret engine version could be parsed from the JSON response's "/secret/options/version"
 * field. curl --header "X-Vault-Token: $VAULT_TOKEN" http://HOST:PORT/v1/sys/mounts
 *
 * The secret engine version information is needed for Vault Service to decide which REST
 * API Client is used to talk to the Vault server,
 */

@OwnedBy(PL)
public interface VaultSysAuthRestClient {
  String BASE_VAULT_URL = "v1/sys/mounts";
  String APPROLE_LOGIN_URL = "v1/auth/approle/login";
  String CONFIG_CA_URL = "v1/{secret-engine-name}/config/ca";
  String SIGN_PUBLIC_SSH_KEY_URL = "v1/{secret-engine-name}/sign/{role-name}";
  String TOKEN_RENEW_URL = "v1/auth/token/renew-self";
  String AWS_IAM_LOGIN_URL = "v1/auth/aws/login";
  String K8s_LOGIN_URL = "v1/auth/kubernetes/login";

  /**
   * The JSON response will be returned as a String. The caller of this API need to '/secret/options/version' field in
   * the JSON document to extract the secret engine version.
   */
  @GET(BASE_VAULT_URL)
  Call<SysMountsResponse> getAllMounts(
      @Header("X-Vault-Token") String header, @Header("X-Vault-Namespace") String namespace);

  @POST(APPROLE_LOGIN_URL)
  Call<VaultAppRoleLoginResponse> appRoleLogin(
      @Header("X-Vault-Namespace") String namespace, @Body VaultAppRoleLoginRequest request);

  @POST(AWS_IAM_LOGIN_URL)
  Call<VaultAppRoleLoginResponse> awsIamAuthLogin(
      @Header("X-Vault-Namespace") String namespace, @Body VaultAwsIamAuthLoginRequest request);

  @GET(CONFIG_CA_URL)
  Call<SSHVaultAuthResponse> fetchAuthPublicKey(
      @Path(value = "secret-engine-name") String secretEngineName, @Header("X-Vault-Token") String header);

  @POST(K8s_LOGIN_URL) Call<VaultK8sLoginResponse> k8sAuthLogin(@Body VaultK8sAuthLoginRequest request);

  @POST(SIGN_PUBLIC_SSH_KEY_URL)
  Call<SignedSSHVaultResponse> fetchSignedPublicKey(@Path(value = "secret-engine-name") String secretEngineName,
      @Path(value = "role-name") String roleName, @Header("X-Vault-Token") String header,
      @Body SignedSSHVaultRequest request);

  @POST(TOKEN_RENEW_URL)
  Call<Object> renewToken(@Header("X-Vault-Token") String header, @Header("X-Vault-Namespace") String namespace);
}
