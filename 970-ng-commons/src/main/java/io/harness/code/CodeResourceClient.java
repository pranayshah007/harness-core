/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.code;

import static io.harness.annotations.dev.HarnessTeam.CODE;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cistatus.StatusCreationResponse;
import io.harness.ng.core.dto.ResponseDTO;

import java.util.List;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PUT;
import retrofit2.http.Path;

@OwnedBy(CODE)
public interface CodeResourceClient {
  @GET("/accounts/{accountIdentifier}/orgs/{orgIdentifier}/projects/{projectId}/repos")
  Call<ResponseDTO<List<CodeRepoResponseDTO>>> getRepos(
      @NotEmpty @Path(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotEmpty @Path(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotEmpty @Path(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier);

  @PUT(
      "api/v1/accounts/{accountIdentifier}/orgs/{orgIdentifier}/projects/{projectIdentifier}/repos/{repoId}/checks/commits/{commit_sha}")
  Call<StatusCreationResponse>
  sendStatus(@NotEmpty @Path(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotEmpty @Path(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotEmpty @Path(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotEmpty @Path("repoId") String repoId, @Path("commit_sha") String commitSha, @Body HarnessCodePayload payload);
}
