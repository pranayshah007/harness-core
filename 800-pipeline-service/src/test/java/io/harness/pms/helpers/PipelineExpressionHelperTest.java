/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.helpers;

import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.PipelineServiceConfiguration;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineExpressionHelperTest extends CategoryTest {
  @Mock PmsExecutionSummaryService pmsExecutionSummaryService;
  @Mock PipelineServiceConfiguration pipelineServiceConfiguration;
  @Mock AccountClient accountClient;
  @InjectMocks PipelineExpressionHelper pipelineExpressionHelper;
  Ambiance ambiance = null;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    ExecutionMetadata metadata =
        Ambiance.newBuilder().getMetadataBuilder().setPipelineIdentifier("pipeline_test").setModuleType("ci").build();
    ambiance = Ambiance.newBuilder()
                   .putSetupAbstractions(SetupAbstractionKeys.accountId, "__ACCOUNT_ID__")
                   .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "org_test")
                   .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "project_test")
                   .setPlanExecutionId("PLAN_EXECUTION_ID")
                   .setMetadata(metadata)
                   .build();
    when(pipelineServiceConfiguration.getPipelineServiceBaseUrl()).thenReturn("https://app.harness.io/ng/#");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testGenerationOfURLWithoutVanity() throws IOException {
    Call vanityUrlCall = mock(Call.class);
    when(vanityUrlCall.execute()).thenReturn(Response.success(new RestResponse<>("")));
    when(accountClient.getVanityUrl(anyString())).thenReturn(vanityUrlCall);
    assertThat(pipelineExpressionHelper.generateUrl(ambiance))
        .isEqualTo(
            "https://app.harness.io/ng/#/account/__ACCOUNT_ID__/ci/orgs/org_test/projects/project_test/pipelines/pipeline_test/executions/PLAN_EXECUTION_ID/pipeline");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testGenerationOfURLWithVanity() throws IOException {
    Call vanityUrlCall = mock(Call.class);
    when(vanityUrlCall.execute()).thenReturn(Response.success(new RestResponse<>("https://vanity.harness.io")));
    when(accountClient.getVanityUrl(anyString())).thenReturn(vanityUrlCall);
    assertThat(pipelineExpressionHelper.generateUrl(ambiance))
        .isEqualTo(
            "https://vanity.harness.io/ng/#/account/__ACCOUNT_ID__/ci/orgs/org_test/projects/project_test/pipelines/pipeline_test/executions/PLAN_EXECUTION_ID/pipeline");
  }
}
