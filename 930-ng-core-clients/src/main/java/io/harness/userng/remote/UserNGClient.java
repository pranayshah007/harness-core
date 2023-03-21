/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.userng.remote;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SortOrder;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.user.remote.dto.UserAggregateDTO;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.PL)
public interface UserNGClient {
  String USER_API = "user";
  String IS_EMAIL_IN_ACCOUNT_PATH = "/is-email-in-account";
  String AGGREGATE_PATH = "/aggregate";
  @GET(USER_API + IS_EMAIL_IN_ACCOUNT_PATH)
  Call<ResponseDTO<Boolean>> isEmailIdInAccount(@Query(value = "emailIdentifier") String emailIdentifier,
      @Query(value = "accountIdentifier") String accountIdentifier);

  @POST(USER_API + AGGREGATE_PATH)
  Call<ResponseDTO<PageResponse<UserAggregateDTO>>> getAggregatedUsers(
      @Query(value = "accountIdentifier") String accountIdentifier,
      @Query(value = "orgIdentifier") String orgIdentifier,
      @Query(value = "projectIdentifier") String projectIdentifier, @Query(value = "searchTerm") String searchTerm,
      @Query(value = "pageIndex") int pageIndex, @Query(value = "pageSize") int pageSize,
      @Query(value = "sortOrders") List<SortOrder> sortOrders, @Query(value = "pageToken") String pageToken);
}
