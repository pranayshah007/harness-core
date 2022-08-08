/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.amazons3;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.AWSTemporaryCredentials;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

@OwnedBy(CDP)
public interface AWSTemporaryCredentialsRestClient {
  @GET("/latest/meta-data/iam/security-credentials/") Call<ResponseBody> getRoleName();

  @GET("/latest/meta-data/iam/security-credentials/{roleName}")
  Call<AWSTemporaryCredentials> getTemporaryCredentials(@Path("roleName") String roleName);
}
