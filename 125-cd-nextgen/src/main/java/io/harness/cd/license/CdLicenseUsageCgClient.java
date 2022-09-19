/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cd.license;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdlicense.bean.CgActiveServicesUsageInfo;
import io.harness.rest.RestResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.CDP)
public interface CdLicenseUsageCgClient {
  @GET("cg/cd/license/usage")
  Call<RestResponse<CgActiveServicesUsageInfo>> getActiveServiceUsage(@Query("accountId") String accountId);
}
