/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.governanceAccount;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorFilterPropertiesDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;

@OwnedBy(CE)
public interface GovernanceConnectorClient {
  @POST("connectors/listV2")
  Call<ResponseDTO<PageResponse<ConnectorResponseDTO>>> listConnectors(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(NGResourceFilterConstants.PAGE_KEY) int page, @Query(NGResourceFilterConstants.SIZE_KEY) int size,
      @Body ConnectorFilterPropertiesDTO connectorListFilter,
      @Query("getDistinctFromBranches") Boolean getDistinctFromBranches);
}
