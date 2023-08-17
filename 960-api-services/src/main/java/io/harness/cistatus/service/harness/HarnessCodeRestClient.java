/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cistatus.service.harness;

import io.harness.cistatus.StatusCreationResponse;

import retrofit2.Call;
import retrofit2.http.Header;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface HarnessCodeRestClient {
  @PUT("api/v1/repos/{repo_ref}/checks/commits/{commit_sha}")
  Call<StatusCreationResponse> sendStatus(@Header("Authorization") String authToken, @Path("repo_ref") String repoRef,
      @Path("commit_sha") String commitSha, HarnessCodePayload payload);
}
