/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdstage.remote;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.dto.ResponseDTO;

import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.CDC)
public interface CDNGStageSummaryResourceClient {
  String CD_STAGE_SUMMARY_ENDPOINT = "cdStageSummary/";

  // Lists summary of execution of deployment stages filtered by stage execution identifiers
  @GET(CD_STAGE_SUMMARY_ENDPOINT + "listStageExecutionFormattedSummary")
  Call<ResponseDTO<Map<String, String>>> listStageExecutionFormattedSummary(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query("stageExecutionIdentifiers") List<String> stageExecutionIdentifiers,
      @Query("rowDelimiter") String rowDelimiter, @Query("keyValueDelimiter") String keyValueDelimiter);

  // Lists summary of deployment stages available at plan creation filtered by stage identifiers
  @GET(CD_STAGE_SUMMARY_ENDPOINT + "listStagePlanCreationFormattedSummary")
  Call<ResponseDTO<Map<String, String>>> listStagePlanCreationFormattedSummary(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(NGCommonEntityConstants.PLAN_KEY) String planExecutionId,
      @Query("stageIdentifiers") List<String> stageIdentifiers, @Query("rowDelimiter") String rowDelimiter,
      @Query("keyValueDelimiter") String keyValueDelimiter);
}
