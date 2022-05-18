/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.remote;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.gitsync.sdk.GitSyncApiConstants;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.RefreshRequestDTO;
import io.harness.ng.core.template.RefreshResponseDTO;
import io.harness.ng.core.template.TemplateApplyRequestDTO;
import io.harness.ng.core.template.TemplateListType;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.TemplateSummaryResponseDTO;
import io.harness.template.TemplateFilterPropertiesDTO;
import io.harness.template.beans.refresh.ValidateTemplateInputsResponseDTO;

import java.util.List;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.CDC)
public interface TemplateResourceClient {
  String TEMPLATE_ENDPOINT = "templates/";
  String TEMPLATE_REFRESH_ENDPOINT = "refresh-template/";

  // list templates
  @POST(TEMPLATE_ENDPOINT + "list")
  Call<ResponseDTO<PageResponse<TemplateSummaryResponseDTO>>> listTemplates(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(value = NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(value = "templateListType") TemplateListType templateListType,
      @Query(value = NGResourceFilterConstants.PAGE_KEY) int page, @Query(NGResourceFilterConstants.SIZE_KEY) int size,
      @Body TemplateFilterPropertiesDTO filterProperties);

  @POST(TEMPLATE_ENDPOINT + "applyTemplates")
  Call<ResponseDTO<TemplateMergeResponseDTO>> applyTemplatesOnGivenYaml(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(value = NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(value = GitSyncApiConstants.BRANCH_KEY) String branch,
      @Query(value = GitSyncApiConstants.REPO_IDENTIFIER_KEY) String repoIdentifier,
      @Query(value = GitSyncApiConstants.DEFAULT_FROM_OTHER_REPO) Boolean defaultFromOtherRepo,
      @Body TemplateApplyRequestDTO templateApplyRequestDTO);

  @POST(TEMPLATE_ENDPOINT + "templateReferences")
  Call<ResponseDTO<List<EntityDetailProtoDTO>>> getTemplateReferenceForGivenYaml(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(value = NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(value = GitSyncApiConstants.BRANCH_KEY) String branch,
      @Query(value = GitSyncApiConstants.REPO_IDENTIFIER_KEY) String repoIdentifier,
      @Query(value = GitSyncApiConstants.DEFAULT_FROM_OTHER_REPO) Boolean defaultFromOtherRepo, @Body String yaml);

  // Refresh Template APIs
  @POST(TEMPLATE_REFRESH_ENDPOINT + "internal")
  Call<ResponseDTO<RefreshResponseDTO>> getRefreshedYaml(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(value = NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(value = GitSyncApiConstants.BRANCH_KEY) String branch,
      @Query(value = GitSyncApiConstants.REPO_IDENTIFIER_KEY) String repoIdentifier,
      @Query(value = GitSyncApiConstants.DEFAULT_FROM_OTHER_REPO) Boolean defaultFromOtherRepo,
      @Body RefreshRequestDTO refreshRequest);

  @POST(TEMPLATE_REFRESH_ENDPOINT + "validate-template-inputs/internal")
  Call<ResponseDTO<ValidateTemplateInputsResponseDTO>> validateTemplateInputsForGivenYaml(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(value = NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(value = GitSyncApiConstants.BRANCH_KEY) String branch,
      @Query(value = GitSyncApiConstants.REPO_IDENTIFIER_KEY) String repoIdentifier,
      @Query(value = GitSyncApiConstants.DEFAULT_FROM_OTHER_REPO) Boolean defaultFromOtherRepo,
      @Body RefreshRequestDTO refreshRequest);
}
