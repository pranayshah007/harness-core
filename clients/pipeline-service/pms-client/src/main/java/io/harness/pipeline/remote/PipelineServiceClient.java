/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pipeline.remote;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.sdk.GitSyncApiConstants;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.inputset.MergeInputSetRequestDTOPMS;
import io.harness.pms.inputset.MergeInputSetResponseDTOPMS;
import io.harness.pms.inputset.MergeInputSetTemplateRequestDTO;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.pms.pipeline.PMSPipelineSummaryResponseDTO;
import io.harness.pms.pipeline.PipelineFilterPropertiesDto;
import io.harness.pms.pipeline.TemplatesResolvedPipelineResponseDTO;

import java.util.List;
import javax.ws.rs.DefaultValue;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(PIPELINE)
public interface PipelineServiceClient {
  String PIPELINE_ENDPOINT = "pipelines/";
  String PIPELINE_INPUT_SET_ENDPOINT = "inputSets/";

  @POST(PIPELINE_ENDPOINT + "list/")
  Call<ResponseDTO<PageResponse<PMSPipelineSummaryResponseDTO>>> listPipelines(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(value = NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query("page") @DefaultValue("0") int page, @Query("size") @DefaultValue("25") int size,
      @Query("sort") List<String> sort, @Query(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Query("module") String module, @Query("filterIdentifier") String filterIdentifier,
      @Body PipelineFilterPropertiesDto filterProperties);

  @POST(PIPELINE_INPUT_SET_ENDPOINT + "mergeWithTemplateYaml/")
  Call<ResponseDTO<MergeInputSetResponseDTOPMS>> getMergeInputSetFromPipelineTemplate(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(value = NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(value = NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @Query("pipelineBranch") String pipelineBranch, @Query("pipelineRepoID") String pipelineRepoID,
      @Query("branch") String branch, @Query("repoIdentifier") String yamlGitConfigId,
      @Query("getDefaultFromOtherRepo") Boolean defaultFromOtherRepo,
      @Body MergeInputSetTemplateRequestDTO mergeInputSetTemplateRequestDTO);

  @GET(PIPELINE_ENDPOINT + "{pipelineIdentifier}")
  Call<ResponseDTO<PMSPipelineResponseDTO>> getPipelineByIdentifier(
      @Path(value = NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(value = NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(GitSyncApiConstants.BRANCH_KEY) String branch,
      @Query(GitSyncApiConstants.REPO_IDENTIFIER_KEY) String yamlGitConfigId,
      @Query(GitSyncApiConstants.DEFAULT_FROM_OTHER_REPO) Boolean defaultFromOtherRepo);

  @GET(PIPELINE_ENDPOINT + "resolved-templates-pipeline-yaml/{pipelineIdentifier}")
  Call<ResponseDTO<TemplatesResolvedPipelineResponseDTO>> getResolvedTemplatesPipelineByIdentifier(
      @Path(value = NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(value = NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(GitSyncApiConstants.BRANCH_KEY) String branch,
      @Query(GitSyncApiConstants.REPO_IDENTIFIER_KEY) String yamlGitConfigId,
      @Query(GitSyncApiConstants.DEFAULT_FROM_OTHER_REPO) Boolean defaultFromOtherRepo);

  /**
   * this is only for non git synced and simplified git experience pipelines/input sets
   */
  @POST(PIPELINE_INPUT_SET_ENDPOINT + "merge/")
  Call<ResponseDTO<MergeInputSetResponseDTOPMS>> getMergeInputSetFromPipelineTemplate(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(value = NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(value = NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @Query(GitSyncApiConstants.BRANCH_KEY) String branch, @Body MergeInputSetRequestDTOPMS mergeInputSetRequestDTO);
}
