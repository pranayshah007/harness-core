/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.cyberark;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

@OwnedBy(PL)
public interface CyberArkRestClient {
  String BASE_CYBERARK_URL = "AIMWebService/api/Accounts";

  @GET(BASE_CYBERARK_URL)
  Call<CyberArkReadResponse> readSecret(@Query("AppID") String appId, @Query("Query") String query);
}
