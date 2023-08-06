/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.dtos;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.sdk.GitSyncApiConstants;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@Schema(name = "ScmBranchFilterParams", description = "This contains branch filter params")
@OwnedBy(PIPELINE)
@NoArgsConstructor
@AllArgsConstructor
public class ScmBranchFilterParams {
  @Parameter(description = GitSyncApiConstants.BRANCH_NAME_SEARCH_TERM_PARAM_MESSAGE)
  @QueryParam(NGCommonEntityConstants.BRANCH_NAME_SEARCH_TERM)
  String branchName;
}
