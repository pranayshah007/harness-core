/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.userng.remote;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ActiveProjectsCountDTO;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ResponseDTO;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.PL)
public interface UserNGClient {
  String ALL_PROJECTS_ACCESSIBLE_TO_USER_API = "user/all-projects";
  String COUNT_OF_ACCESSIBLE_PROJECTS_API = "user/projects-count";

  @GET(ALL_PROJECTS_ACCESSIBLE_TO_USER_API)
  Call<ResponseDTO<List<ProjectDTO>>> getUserAllProjectsInfo(
      @Query(value = "accountId") String accountId, @Query(value = "userId") String userId);

  @GET(COUNT_OF_ACCESSIBLE_PROJECTS_API)
  Call<ResponseDTO<ActiveProjectsCountDTO>> getAccessibleProjectsCount(
      @Query(value = "accountIdentifier") String accountId, @Query(value = "userId") String userId,
      @Query(value = "startTime") long startInterval, @Query(value = "endTime") long endInterval);
}
