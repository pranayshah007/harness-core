/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.managerclient;

import io.harness.delegate.beans.DelegateConfiguration;
import io.harness.delegate.beans.DelegateScripts;
import io.harness.logging.AccessTokenBean;
import io.harness.rest.RestResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ManagerClientV2 {
  @GET("agent/delegates/delegateScripts")
  Call<RestResponse<DelegateScripts>> getDelegateScripts(@Query("accountId") String accountId,
      @Query("delegateVersion") String delegateVersion, @Query("patchVersion") String patchVersion,
      @Query("delegateName") String delegateName);

  @GET("agent/delegates/delegateScriptsNg")
  Call<RestResponse<DelegateScripts>> getDelegateScriptsNg(@Query("accountId") String accountId,
      @Query("delegateVersion") String delegateVersion, @Query("patchVersion") String patchVersion,
      @Query("delegateType") String delegateType);
  @GET("agent/delegates/configuration")
  Call<RestResponse<DelegateConfiguration>> getDelegateConfiguration(@Query("accountId") String accountId);

  @GET("agent/delegates/jreVersion")
  Call<RestResponse<String>> getJREVersion(
      @Query("accountId") String accountId, @Query("isDelegate") boolean isDelegate);

  @GET("agent/infra-download/delegate-auth/delegate/{version}")
  Call<RestResponse<String>> getDelegateDownloadUrl(
      @Path("version") String version, @Query("accountId") String accountId);

  @GET("agent/infra-download/delegate-auth/watcher/{version}")
  Call<RestResponse<String>> getWatcherDownloadUrl(
      @Path("version") String version, @Query("accountId") String accountId);

  @GET("account/{accountId}/status") Call<RestResponse<String>> getAccountStatus(@Path("accountId") String accountId);

  @GET("agent/infra-download/delegate-auth/delegate/logging-token")
  Call<RestResponse<AccessTokenBean>> getLoggingToken(@Query("accountId") String accountId);

  @GET("version/watcher") Call<RestResponse<String>> getWatcherVersion(@Query("accountId") String accountId);
}
