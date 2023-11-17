package io.harness.cdstage.remote;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.CDStageMetaDataDTO;
import io.harness.ng.core.dto.CdDeployStageMetadataRequestDTO;
import io.harness.ng.core.dto.ResponseDTO;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

@OwnedBy(IDP)
public interface CDStageConfigClient {
  @POST("cdStage/metadata")
  Call<ResponseDTO<CDStageMetaDataDTO>> getCDStageMetaData(@Body CdDeployStageMetadataRequestDTO requestDTO);
}
