/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.governance.GovernanceMetadata;
import io.harness.network.SafeHttpCall;
import io.harness.opaclient.OpaServiceClient;
import io.harness.opaclient.model.OpaConstants;
import io.harness.opaclient.model.OpaEvaluationResponseHolder;
import io.harness.opaclient.model.PipelineOpaEvaluationContext;
import io.harness.opaclient.model.TemplateOpaEvaluationContext;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import java.io.IOException;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import retrofit2.Call;

@PrepareForTest({GovernanceServiceHelper.class, SafeHttpCall.class})
@OwnedBy(PIPELINE)
public class GovernanceServiceImplTest extends CategoryTest {
  GovernanceService governanceService;
  @Mock PmsFeatureFlagService featureFlagService;
  @Mock OpaServiceClient opaServiceClient;

  String accountId = "acc";
  String orgId = "org";
  String projectId = "proj";
  String action = "onSave";
  String planExecutionId = "";

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.openMocks(this);
    governanceService = new GovernanceServiceImpl(featureFlagService, opaServiceClient);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testEvaluateGovernancePoliciesWithFlagOff() {
    doReturn(false).when(featureFlagService).isEnabled(accountId, FeatureName.OPA_PIPELINE_GOVERNANCE);
    GovernanceMetadata flagOffMetadata =
        governanceService.evaluateGovernancePolicies(null, accountId, null, null, null, null, PipelineVersion.V0);
    assertThat(flagOffMetadata.getDeny()).isFalse();
    assertThat(flagOffMetadata.getMessage()).isEqualTo("FF: [OPA_PIPELINE_GOVERNANCE] is disabled for account: [acc]");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testEvaluateGovernancePoliciesWithInvalidYAML() throws IOException {
    doReturn(true).when(featureFlagService).isEnabled(accountId, FeatureName.OPA_PIPELINE_GOVERNANCE);
    MockedStatic<GovernanceServiceHelper> mockSettings = Mockito.mockStatic(GovernanceServiceHelper.class);
    when(GovernanceServiceHelper.createEvaluationContext("expandedJSON:")).thenThrow(new IOException());
    GovernanceMetadata governanceMetadata = governanceService.evaluateGovernancePolicies(
        "expandedJSON:", accountId, null, null, null, null, PipelineVersion.V0);
    assertThat(governanceMetadata.getDeny()).isTrue();
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testEvaluateGovernancePolicies() throws IOException {
    String expandedJSON = "pipeline:\n"
        + "  identifier: myPipe\n"
        + "  name: my pipe";
    doReturn(true).when(featureFlagService).isEnabled(accountId, FeatureName.OPA_PIPELINE_GOVERNANCE);

    MockedStatic<GovernanceServiceHelper> mockSettings = Mockito.mockStatic(GovernanceServiceHelper.class);

    PipelineOpaEvaluationContext evaluationContext =
        PipelineOpaEvaluationContext.builder().pipeline(Collections.singletonMap("pipeline", "yaml")).build();
    when(GovernanceServiceHelper.createEvaluationContext(expandedJSON)).thenReturn(evaluationContext);

    String entityString = "entityString";
    when(GovernanceServiceHelper.getEntityString(accountId, orgId, projectId, "myPipe")).thenReturn(entityString);

    String entityMetadata = "entityMetadata";
    when(GovernanceServiceHelper.getEntityMetadataString("myPipe", "my pipe", planExecutionId))
        .thenReturn(entityMetadata);

    String userID = "user";
    when(GovernanceServiceHelper.getUserIdentifier()).thenReturn(userID);

    Call<OpaEvaluationResponseHolder> request = mock(Call.class);
    when(opaServiceClient.evaluateWithCredentials(OpaConstants.OPA_EVALUATION_TYPE_PIPELINE, accountId, orgId,
             projectId, action, entityString, entityMetadata, userID, evaluationContext))
        .thenReturn(request);

    MockedStatic<SafeHttpCall> mockSettings1 = Mockito.mockStatic(SafeHttpCall.class);
    OpaEvaluationResponseHolder response = OpaEvaluationResponseHolder.builder().id("id").build();
    when(SafeHttpCall.executeWithExceptions(request)).thenReturn(response);

    GovernanceMetadata expectedResponse = GovernanceMetadata.newBuilder().setDeny(false).setId("someID").build();
    when(GovernanceServiceHelper.mapResponseToMetadata(response)).thenReturn(expectedResponse);

    GovernanceMetadata governanceMetadata = governanceService.evaluateGovernancePolicies(
        expandedJSON, accountId, orgId, projectId, action, planExecutionId, PipelineVersion.V0);
    assertThat(governanceMetadata.getDeny()).isFalse();
    assertThat(governanceMetadata.getId()).isEqualTo("someID");
    mockSettings.close();
    mockSettings1.close();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testEvaluateGovernancePoliciesForV1Yaml() {
    doReturn(true).when(featureFlagService).isEnabled(accountId, FeatureName.OPA_PIPELINE_GOVERNANCE);
    GovernanceMetadata governanceMetadata =
        governanceService.evaluateGovernancePolicies(null, accountId, null, null, null, null, PipelineVersion.V1);
    assertThat(governanceMetadata.getDeny()).isFalse();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testEvaluateGovernancePoliciesTemplateWithInvalidYAML() throws IOException {
    doReturn(true).when(featureFlagService).isEnabled(accountId, FeatureName.OPA_TEMPLATE_GOVERNANCE);
    MockedStatic<GovernanceServiceHelper> mockSettings = Mockito.mockStatic(GovernanceServiceHelper.class);
    when(GovernanceServiceHelper.createEvaluationContextTemplate("expandedJSON:")).thenThrow(new IOException());
    GovernanceMetadata governanceMetadata = governanceService.evaluateGovernancePoliciesForTemplate(
        "expandedJSON:", accountId, null, null, null, OpaConstants.OPA_EVALUATION_TYPE_TEMPLATE);
    assertThat(governanceMetadata.getDeny()).isTrue();
    mockSettings.close();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testEvaluateGovernancePoliciesTemplate() throws IOException {
    String expandedJSON = "template:\n"
        + "  identifier: myPipe\n"
        + "  name: my pipe";
    doReturn(true).when(featureFlagService).isEnabled(accountId, FeatureName.OPA_TEMPLATE_GOVERNANCE);

    MockedStatic<GovernanceServiceHelper> mockSettings = Mockito.mockStatic(GovernanceServiceHelper.class);

    TemplateOpaEvaluationContext evaluationContext =
        TemplateOpaEvaluationContext.builder().template(Collections.singletonMap("template", "yaml")).build();
    when(GovernanceServiceHelper.createEvaluationContextTemplate(expandedJSON)).thenReturn(evaluationContext);

    String entityString = "myPipe";
    String entityMetadata = "entityMetadata";
    when(GovernanceServiceHelper.getEntityMetadata("my pipe")).thenReturn(entityMetadata);

    String userID = "user";
    when(GovernanceServiceHelper.getUserIdentifier()).thenReturn(userID);

    Call<OpaEvaluationResponseHolder> request = mock(Call.class);
    when(opaServiceClient.evaluateWithCredentials(OpaConstants.OPA_EVALUATION_TYPE_TEMPLATE, accountId, orgId,
             projectId, action, entityString, entityMetadata, userID, evaluationContext))
        .thenReturn(request);

    MockedStatic<SafeHttpCall> mockSettings1 = Mockito.mockStatic(SafeHttpCall.class);
    OpaEvaluationResponseHolder response = OpaEvaluationResponseHolder.builder().id("id").build();
    when(SafeHttpCall.executeWithExceptions(request)).thenReturn(response);

    GovernanceMetadata expectedResponse = GovernanceMetadata.newBuilder().setDeny(false).setId("someID").build();
    when(GovernanceServiceHelper.mapResponseToMetadata(response)).thenReturn(expectedResponse);

    GovernanceMetadata governanceMetadata = governanceService.evaluateGovernancePoliciesForTemplate(
        expandedJSON, accountId, orgId, projectId, action, OpaConstants.OPA_EVALUATION_TYPE_TEMPLATE);
    assertThat(governanceMetadata.getDeny()).isFalse();
    assertThat(governanceMetadata.getId()).isEqualTo("someID");
    mockSettings.close();
    mockSettings1.close();
  }
}
