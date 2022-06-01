/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.gcs;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;

@OwnedBy(CDC)
public interface GcsRestClient {
  @GET("v1/b/{bucketName}/o/log-{fileName}.txt?alt=media")
  Call<ResponseBody> fetchLogs(@Header("Authorization") String bearerAuthHeader,
      @Path(value = "bucketName") String bucket, @Path(value = "fileName") String buildId);
}
