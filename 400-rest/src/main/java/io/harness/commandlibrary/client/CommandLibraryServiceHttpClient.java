/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.commandlibrary.client;

import io.harness.commandlibrary.api.dto.CommandEntityDTO;
import io.harness.rest.RestResponse;

import software.wings.api.commandlibrary.EnrichedCommandVersionDTO;

import java.util.Map;
import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.QueryMap;

public interface CommandLibraryServiceHttpClient {
  @GET("command-stores") Call<Object> getCommandStores(@QueryMap Map<String, Object> pageRequestParams);

  @GET("command-stores/{commandStoreName}/commands/tags")
  Call<Object> getCommandTags(
      @Path("commandStoreName") String commandStoreName, @QueryMap Map<String, Object> pageRequestParams);

  @GET("command-stores/{commandStoreName}/commands")
  Call<Object> listCommands(
      @Path("commandStoreName") String commandStoreName, @QueryMap Map<String, Object> pageRequestParams);

  @GET("command-stores/{commandStoreName}/commands/{commandName}")
  Call<RestResponse<CommandEntityDTO>> getCommandDetails(@Path("commandStoreName") String commandStoreName,
      @Path("commandName") String commandName, @QueryMap Map<String, Object> pageRequestParams);

  @GET("command-stores/{commandStoreName}/commands/{commandName}/versions/{version}")
  Call<RestResponse<EnrichedCommandVersionDTO>> getVersionDetails(@Path("commandStoreName") String commandStoreName,
      @Path("commandName") String commandName, @Path("version") String version,
      @QueryMap Map<String, Object> pageRequestParams);

  @Multipart
  @POST("command-stores/{commandStoreName}/commands")
  Call<Object> publishCommand(@Path("commandStoreName") String commandStoreName, @Part MultipartBody.Part file,
      @QueryMap Map<String, Object> pageRequestParams);
}
