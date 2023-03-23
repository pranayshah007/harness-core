package io.harness.dms.client;

import io.harness.NGCommonEntityConstants;
import io.harness.rest.RestResponse;

import javax.validation.constraints.NotNull;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface DelegateSecretManagerClient {
  String SECRET_MANAGER_ENDPOINT = "secret-manager";

  //------------------------ Agent mTLS Endpoint Apis -----------------------------------

  @GET(SECRET_MANAGER_ENDPOINT + "/fetchSecret")
  Call<RestResponse<String>> fetchSecretValue(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query("secretRecordId") @NotNull String secretRecordId);
}
