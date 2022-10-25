/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secretmanagerclient.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorValidationResult;
import io.harness.kryo.KryoRequest;
import io.harness.rest.RestResponse;
import io.harness.secretmanagerclient.dto.EncryptedDataMigrationDTO;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.secretmanagerclient.dto.SecretManagerConfigUpdateDTO;
import io.harness.secretmanagerclient.dto.SecretManagerMetadataDTO;
import io.harness.secretmanagerclient.dto.SecretManagerMetadataRequestDTO;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(PL)
public interface SecretManagerClient {
  String SECRETS_API = "ng/secrets";
  String SECRET_MANAGERS_API = "ng/secret-managers";

  /*
 GET EncryptedData -> this is to be used for migration purpose
 In case of secret files -> value field will be populated after downloading from file service
  */
  @GET(SECRETS_API + "/migration/{identifier}")
  Call<RestResponse<EncryptedDataMigrationDTO>> getEncryptedDataMigrationDTO(
      @Path(value = "identifier") String identifier,
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @Query("decrypted") Boolean decrypted);

  // create secret manager
  @POST(SECRET_MANAGERS_API)
  @KryoRequest
  Call<RestResponse<SecretManagerConfigDTO>> createSecretManager(@Body SecretManagerConfigDTO secretManagerConfig);

  // validate secret manager
  @GET(SECRET_MANAGERS_API + "/{identifier}/validate")
  Call<RestResponse<ConnectorValidationResult>> validateSecretManager(@Path(value = "identifier") String identifier,
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier);

  // update secret manager
  @PUT(SECRET_MANAGERS_API + "/{identifier}")
  @KryoRequest
  Call<RestResponse<SecretManagerConfigDTO>> updateSecretManager(@Path("identifier") String identifier,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Body SecretManagerConfigUpdateDTO secretManagerConfigUpdateDTO);

  // list secret managers
  @GET(SECRET_MANAGERS_API)
  Call<RestResponse<List<SecretManagerConfigDTO>>> listSecretManagers(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(value = NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier);

  // list secret managers
  @GET(SECRET_MANAGERS_API)
  Call<RestResponse<List<SecretManagerConfigDTO>>> listSecretManagers(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(value = NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(value = NGResourceFilterConstants.IDENTIFIERS) List<String> identifiers);

  // get secret manager
  @GET(SECRET_MANAGERS_API + "/{identifier}")
  Call<RestResponse<SecretManagerConfigDTO>> getSecretManager(@Path("identifier") String identifier,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(NGCommonEntityConstants.MASK_SECRETS) Boolean maskSecrets);

  // get global secret manager
  @GET(SECRET_MANAGERS_API + "/global/{accountIdentifier}")
  Call<RestResponse<SecretManagerConfigDTO>> getGlobalSecretManager(
      @Path(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier);

  // delete secret manager
  @DELETE(SECRET_MANAGERS_API + "/{identifier}")
  Call<RestResponse<Boolean>> deleteSecretManager(@Path("identifier") String identifier,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier);

  @POST(SECRET_MANAGERS_API + "/meta-data")
  Call<RestResponse<SecretManagerMetadataDTO>> getSecretManagerMetadata(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Body SecretManagerMetadataRequestDTO requestDTO);
}
