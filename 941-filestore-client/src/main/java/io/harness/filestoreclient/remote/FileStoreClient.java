/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestoreclient.remote;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.IDENTIFIER_KEY;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.filestore.dto.FileDTO;

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
  String FILE_STORE_NG_API = "file-store";

  @GET(FILE_STORE_NG_API + "/{identifier}")
  Call<ResponseDTO<FileDTO>> getFileNg(@Path(IDENTIFIER_KEY) @NotBlank String identifier,
      @NotEmpty @Query(value = ACCOUNT_KEY) String accountIdentifier, @Query(value = ORG_KEY) String orgIdentifier,
      @Query(value = PROJECT_KEY) String projectIdentifier);

  @GET(FILE_STORE_NG_API + "/files/{scopedFilePath}/content")
  Call<ResponseDTO<String>> getContent(@Path("scopedFilePath") @NotBlank String scopedFilePath,
      @NotEmpty @Query(value = ACCOUNT_KEY) String accountIdentifier, @Query(value = ORG_KEY) String orgIdentifier,
      @Query(value = PROJECT_KEY) String projectIdentifier);
}
