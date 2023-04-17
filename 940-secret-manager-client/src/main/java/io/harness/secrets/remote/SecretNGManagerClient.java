/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DecryptedSecretValue;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.DecryptableEntityWithEncryptionConsumers;
import io.harness.ng.core.NGAccessWithEncryptionConsumer;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.secrets.SecretRequestWrapper;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.kryo.KryoRequest;
import io.harness.serializer.kryo.KryoResponse;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(PL)
public interface SecretNGManagerClient {
  String SECRETS_API = "v2/secrets";

  String SECRET_MANAGERS_API = "secret-managers";

  @GET(SECRETS_API + "/{identifier}")
  Call<ResponseDTO<SecretResponseWrapper>> getSecret(
      @Path(value = NGCommonEntityConstants.IDENTIFIER_KEY) String identifier,
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(value = NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier);

  // list secrets
  @GET(SECRETS_API)
  Call<ResponseDTO<PageResponse<SecretResponseWrapper>>> listSecrets(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(value = NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(value = NGResourceFilterConstants.IDENTIFIERS) List<String> identifiers,
      @Query(value = NGResourceFilterConstants.PAGE_KEY) int page, @Query(NGResourceFilterConstants.SIZE_KEY) int size);

  @POST(SECRETS_API + "/encryption-details")
  @KryoRequest
  @KryoResponse
  Call<ResponseDTO<List<EncryptedDataDetail>>> getEncryptionDetails(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Body NGAccessWithEncryptionConsumer ngAccessWithEncryptionConsumer);

  // get secret manager
  @GET(SECRET_MANAGERS_API + "/{identifier}")
  Call<ResponseDTO<SecretManagerConfigDTO>> getSecretManager(
      @Path(NGCommonEntityConstants.IDENTIFIER_KEY) String identifier,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(NGCommonEntityConstants.MASK_SECRETS) boolean maskSecrets);

  @POST(SECRETS_API + "/decrypt-encryption-details")
  @KryoRequest
  @KryoResponse
  Call<ResponseDTO<DecryptableEntity>> decryptEncryptedDetails(
      @Body DecryptableEntityWithEncryptionConsumers decryptableEntityWithEncryptionConsumers,
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier);

  @GET(SECRETS_API + "/{identifier}/decrypt")
  Call<ResponseDTO<DecryptedSecretValue>> getDecryptedSecretValue(
      @Path(NGCommonEntityConstants.IDENTIFIER_KEY) String identifier,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier);

  @POST(SECRETS_API)
  Call<ResponseDTO<SecretResponseWrapper>> create(@Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query("privateSecret") boolean privateSecret, @Body SecretRequestWrapper dto);
}
