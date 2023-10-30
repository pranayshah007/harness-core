/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.featureflag;

import static io.harness.annotations.dev.HarnessTeam.CF;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;

import java.util.List;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

@OwnedBy(CF)
public interface FeatureFlagResourceClient {
  String FEATUREFLAG_API = "featureFlags";
  @GET(FEATUREFLAG_API)
  Call<ResponseDTO<List<FeatureFlagResourceResponseDTO>>> getFeatureFlags(
      @NotEmpty @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotEmpty @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotEmpty @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier);
}
