/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.usergroupsng.remote;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SortOrder;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.UserGroupAggregateDTO;
import io.harness.ng.core.usergroups.filter.UserGroupFilterType;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.PL)
public interface UserGroupsNGClient {
  String USER_GROUPS_API = "aggregate/acl/usergroups";

  @GET(USER_GROUPS_API)
  Call<ResponseDTO<PageResponse<UserGroupAggregateDTO>>> getUserGroupAggregateList(
      @Query(value = "accountIdentifier") String accountIdentifier,
      @Query(value = "orgIdentifier") String orgIdentifier,
      @Query(value = "projectIdentifier") String projectIdentifier, @Query(value = "pageIndex") int pageIndex,
      @Query(value = "pageSize") int pageSize, @Query(value = "sortOrders") List<SortOrder> sortOrders,
      @Query(value = "pageToken") String pageToken, @Query(value = "searchTerm") String searchTerm,
      @Query(value = "filterType") UserGroupFilterType filterType, @Query(value = "userSize") int userSize);

  @GET(USER_GROUPS_API + "/{identifier}")
  Call<ResponseDTO<UserGroupAggregateDTO>> getUserGroupAggregate(@Path(value = "identifier") String identifier,
      @Query(value = "accountIdentifier") String accountIdentifier,
      @Query(value = "orgIdentifier") String orgIdentifier,
      @Query(value = "projectIdentifier") String projectIdentifier);
}
