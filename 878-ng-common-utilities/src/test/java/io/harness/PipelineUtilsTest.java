/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PipelineUtilsTest extends CategoryTest {
  private PipelineUtils pipelineUtils = new PipelineUtils();

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetBuildDetailsURL() {
    String accountId = "accountId";
    String orgId = "orgId";
    String projectId = "projectId";
    String pipelineId = "pipelineId";
    String executionId = "executionId";
    String baseUrl = "localhost";
    String stageSetupId = "abcdefg";
    String stageExecId = "stageExecId";
    String url = baseUrl + "/account/" + accountId + "/ci/orgs/" + orgId + "/projects/" + projectId + "/pipelines/"
        + pipelineId + "/executions/" + executionId + "/pipeline?stage=abcdefg&stageExecId=stageExecId";

    NGAccess ngAccess =
        BaseNGAccess.builder().accountIdentifier(accountId).orgIdentifier(orgId).projectIdentifier(projectId).build();
    String returnedUrl =
        pipelineUtils.getBuildDetailsUrl(ngAccess, pipelineId, executionId, baseUrl, stageSetupId, stageExecId);
    assertEquals(url, returnedUrl);
  }
}
