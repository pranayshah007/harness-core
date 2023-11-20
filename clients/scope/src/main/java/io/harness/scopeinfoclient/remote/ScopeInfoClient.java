/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.scopeinfoclient.remote;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.ng.core.dto.ResponseDTO;

import java.util.Optional;
import javax.validation.constraints.NotNull;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

@OwnedBy(PL)
public interface ScopeInfoClient {
  String SCOPE_INFO = "scope-info";

  @GET(SCOPE_INFO)
  Call<ResponseDTO<Optional<ScopeInfo>>> getScopeInfo(@NotNull @Query(value = ACCOUNT_KEY) String accountIdentifier,
      @Query(value = ORG_KEY) String orgIdentifier, @Query(value = PROJECT_KEY) String projectIdentifier);
}
