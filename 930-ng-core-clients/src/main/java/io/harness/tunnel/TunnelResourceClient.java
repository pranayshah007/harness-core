/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.tunnel;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.TunnelResponseDTO;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

@OwnedBy(CI)
public interface TunnelResourceClient {
  String TUNNEL_API = "tunnel";

  @GET(TUNNEL_API)
  Call<ResponseDTO<TunnelResponseDTO>> getTunnel(@Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountId);
}