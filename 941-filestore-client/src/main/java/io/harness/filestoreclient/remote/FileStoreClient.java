package io.harness.filestoreclient.remote;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.IDENTIFIER_KEY;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.ng.core.dto.ResponseDTO;

import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(CDP)
public interface FileStoreClient {
  String FILE_STORE_NG_API = "file-store";

  @GET(FILE_STORE_NG_API + "/files/{identifier}")
  Call<ResponseDTO<FileStoreNodeDTO>> getFileNg(@Path(IDENTIFIER_KEY) @NotBlank String identifier,
                                                @NotEmpty @Query(value = ACCOUNT_KEY) String accountIdentifier, @Query(value = ORG_KEY) String orgIdentifier,
                                                @Query(value = PROJECT_KEY) String projectIdentifier, @Query(value = "includeContent") Boolean includeContent);
}
