/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.code;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.delegate.DelegateResourceValidationResponse;
import io.harness.notification.dtos.NotificationChannelDTO;
import io.harness.notification.notificationclient.NotificationResult;
import io.harness.rest.RestResponse;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.CE)
public interface CodeResourceClient {
  String BASE_API = "code/api/v1";

  @GET(BASE_API + "/spaces/") Call<List<CodeRepoResponse>> listRepos(@Query("accountId") String accountIdentifier);
}
