/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.fileservice;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.FileBucket;
import io.harness.rest.RestResponse;

import javax.validation.constraints.NotNull;
import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(CDP)
public interface FileServiceClient {
  String FILE_SERVICE_API = "ng/file-service";

  @GET(FILE_SERVICE_API + "/latestFileId")
  Call<RestResponse<String>> getLatestFileId(
      @Query(value = "entityId") @NotNull String entityId, @Query(value = "fileBucket") @NotNull FileBucket fileBucket);

  @POST(FILE_SERVICE_API + "/parentEntityIdAndVersion")
  Call<RestResponse<Boolean>> updateParentEntityIdAndVersion(@Query(value = "entityId") @NotNull String entityId,
      @Query(value = "stateFileId") @NotNull String stateFileId,
      @Query(value = "fileBucket") @NotNull FileBucket fileBucket);

  @DELETE(FILE_SERVICE_API + "/{fileId}")
  Call<RestResponse<Void>> deleteFile(@Path("fileId") String fileId, @Query("bucket") FileBucket fileBucket);
}
