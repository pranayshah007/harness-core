/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.filestore.remote;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.NGResourceFilterConstants.IDENTIFIERS;
import static io.harness.NGResourceFilterConstants.PAGE_KEY;
import static io.harness.NGResourceFilterConstants.SIZE_KEY;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.filestore.dto.FileDTO;

import java.util.List;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRADITIONAL})
@OwnedBy(CDP)
public interface FileStoreClient {
  String FILE_STORE_API = "file-store";

  @GET(FILE_STORE_API)
  Call<PageResponse<FileDTO>> listFilesAndFolders(@Query(value = ACCOUNT_KEY) String accountIdentifier,
      @Query(value = ORG_KEY) String orgIdentifier, @Query(value = PROJECT_KEY) String projectIdentifier,
      @Query(value = IDENTIFIERS) List<String> identifiers, @Query(value = PAGE_KEY) int page,
      @Query(SIZE_KEY) int size);

  @GET(FILE_STORE_API + "/files/{scopedFilePath}/content")
  Call<ResponseDTO<String>> getContent(@Path("scopedFilePath") @NotBlank String scopedFilePath,
      @NotEmpty @Query(value = ACCOUNT_KEY) String accountIdentifier, @Query(value = ORG_KEY) String orgIdentifier,
      @Query(value = PROJECT_KEY) String projectIdentifier);
}
