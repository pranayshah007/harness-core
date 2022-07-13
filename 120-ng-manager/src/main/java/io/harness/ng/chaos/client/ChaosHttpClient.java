package io.harness.ng.chaos.client;

import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.tasks.ResponseData;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ChaosHttpClient {
  String CHAOS_ENDPOINT = "/chaos/";

  @POST(CHAOS_ENDPOINT) Call<ResponseDTO<AccessCheckResponseDTO>> pushTaskResponse(@Body ResponseData responseData);
}
