/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.policy.step;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.HttpResponseException;
import io.harness.exception.InvalidRequestException;
import io.harness.network.SafeHttpCall;
import io.harness.opaclient.OpaServiceClient;
import io.harness.opaclient.model.OpaEvaluationResponseHolder;
import io.harness.opaclient.model.OpaPolicySetEvaluationResponse;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.policy.PolicyStepSpecParameters;
import io.harness.steps.policy.custom.CustomPolicyStepSpec;
import io.harness.steps.policy.step.outcome.PolicyStepOutcome;
import io.harness.steps.policy.step.outcome.PolicyStepOutcomeMapper;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import retrofit2.Call;

@RunWith(PowerMockRunner.class)
@OwnedBy(PIPELINE)
@PrepareForTest({SafeHttpCall.class, PolicyStepOutcomeMapper.class})
public class PolicyStepTest extends CategoryTest {
  @InjectMocks PolicyStep policyStep;
  @Mock OpaServiceClient opaServiceClient;

  Ambiance ambiance;
  String accountId = "acc";
  String orgId = "org";
  String projectId = "proj";
  String stepName = "step name";
  List<String> projLevelPolicySet;
  StepElementParameters stepParameters;
  Call<OpaEvaluationResponseHolder> request;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    PowerMockito.mockStatic(SafeHttpCall.class);
    PowerMockito.mockStatic(PolicyStepOutcomeMapper.class);
    ambiance = Ambiance.newBuilder()
                   .putSetupAbstractions("accountId", accountId)
                   .putSetupAbstractions("orgIdentifier", orgId)
                   .putSetupAbstractions("projectIdentifier", projectId)
                   .build();
    projLevelPolicySet = Collections.singletonList("ps1");
    request = mock(Call.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testExecuteSyncWithEmptyPolicySet() {
    PolicyStepSpecParameters policyStepSpecParameters =
        PolicyStepSpecParameters.builder()
            .policySets(ParameterField.createValueField(Collections.emptyList()))
            .type("Custom")
            .build();
    stepParameters = StepElementParameters.builder().spec(policyStepSpecParameters).build();
    StepResponse stepResponse = policyStep.executeSync(ambiance, stepParameters, null, null);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getMessage())
        .isEqualTo("List of Policy Sets cannot by empty");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testExecuteSyncWithInvalidCustomPayload() {
    PolicyStepSpecParameters policyStepSpecParameters =
        PolicyStepSpecParameters.builder()
            .policySets(ParameterField.createValueField(projLevelPolicySet))
            .type("Custom")
            .policySpec(CustomPolicyStepSpec.builder().payload(ParameterField.createValueField("12")).build())
            .build();
    stepParameters = StepElementParameters.builder().spec(policyStepSpecParameters).build();
    StepResponse stepResponse = policyStep.executeSync(ambiance, stepParameters, null, null);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getMessage())
        .isEqualTo("Custom payload is not a valid JSON.");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testExecuteSyncWithInvalidStepType() {
    PolicyStepSpecParameters policyStepSpecParameters =
        PolicyStepSpecParameters.builder()
            .policySets(ParameterField.createValueField(projLevelPolicySet))
            .type("NotSupported")
            .policySpec(CustomPolicyStepSpec.builder().payload(ParameterField.createValueField("12")).build())
            .build();
    stepParameters = StepElementParameters.builder().spec(policyStepSpecParameters).build();
    StepResponse stepResponse = policyStep.executeSync(ambiance, stepParameters, null, null);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getMessage())
        .isEqualTo("Policy Step type NotSupported is not supported.");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testExecuteSyncWithApplicationFailure() throws IOException {
    String payload = "{\"this\" : \"that\"}";
    JsonNode payloadObj = YamlUtils.readTree(payload).getNode().getCurrJsonNode();
    PolicyStepSpecParameters policyStepSpecParameters =
        PolicyStepSpecParameters.builder()
            .policySets(ParameterField.createValueField(projLevelPolicySet))
            .type("Custom")
            .policySpec(CustomPolicyStepSpec.builder().payload(ParameterField.createValueField(payload)).build())
            .build();
    stepParameters = StepElementParameters.builder().name(stepName).spec(policyStepSpecParameters).build();

    String urlPolicySets = "ps1";
    when(opaServiceClient.evaluateWithCredentialsByID(accountId, orgId, projectId, urlPolicySets,
             PolicyStepHelper.getEntityMetadataString(stepName), payloadObj))
        .thenReturn(request);
    when(SafeHttpCall.executeWithErrorMessage(request)).thenThrow(new HttpResponseException(400, "My Invalid Request"));
    StepResponse stepResponse = policyStep.executeSync(ambiance, stepParameters, null, null);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getMessage())
        .isEqualTo("Unexpected error occurred while evaluating Policies.");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testExecuteSyncWithPolicyNotFoundError() throws IOException {
    String payload = "{\"this\" : \"that\"}";
    JsonNode payloadObj = YamlUtils.readTree(payload).getNode().getCurrJsonNode();
    PolicyStepSpecParameters policyStepSpecParameters =
        PolicyStepSpecParameters.builder()
            .policySets(ParameterField.createValueField(projLevelPolicySet))
            .type("Custom")
            .policySpec(CustomPolicyStepSpec.builder().payload(ParameterField.createValueField(payload)).build())
            .build();
    stepParameters = StepElementParameters.builder().name(stepName).spec(policyStepSpecParameters).build();

    String urlPolicySets = "ps1";
    when(opaServiceClient.evaluateWithCredentialsByID(accountId, orgId, projectId, urlPolicySets,
             PolicyStepHelper.getEntityMetadataString(stepName), payloadObj))
        .thenReturn(request);
    String errorString = "{\n"
        + "    \"identifier\" : \"thisSet\",\n"
        + "    \"message\" : \"policy set 'thisSet' is disabled.\"\n"
        + "}";
    when(SafeHttpCall.executeWithErrorMessage(request)).thenThrow(new InvalidRequestException(errorString));
    StepResponse stepResponse = policyStep.executeSync(ambiance, stepParameters, null, null);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getMessage())
        .isEqualTo("Policy Set 'thisSet' is disabled.");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testExecuteSyncWithEvaluationFailure() throws IOException {
    String payload = "{\"this\" : \"that\"}";
    JsonNode payloadObj = YamlUtils.readTree(payload).getNode().getCurrJsonNode();
    PolicyStepSpecParameters policyStepSpecParameters =
        PolicyStepSpecParameters.builder()
            .policySets(ParameterField.createValueField(projLevelPolicySet))
            .type("Custom")
            .policySpec(CustomPolicyStepSpec.builder().payload(ParameterField.createValueField(payload)).build())
            .build();
    stepParameters = StepElementParameters.builder().name(stepName).spec(policyStepSpecParameters).build();

    String urlPolicySets = "ps1";
    when(opaServiceClient.evaluateWithCredentialsByID(accountId, orgId, projectId, urlPolicySets,
             PolicyStepHelper.getEntityMetadataString(stepName), payloadObj))
        .thenReturn(request);

    OpaEvaluationResponseHolder evaluationResponse =
        OpaEvaluationResponseHolder.builder()
            .status("error")
            .details(Collections.singletonList(
                OpaPolicySetEvaluationResponse.builder().status("error").name("myName").build()))
            .build();
    when(SafeHttpCall.executeWithErrorMessage(request)).thenReturn(evaluationResponse);
    when(PolicyStepOutcomeMapper.toOutcome(evaluationResponse))
        .thenReturn(PolicyStepOutcome.builder().status("error").build());
    StepResponse stepResponse = policyStep.executeSync(ambiance, stepParameters, null, null);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getMessage())
        .isEqualTo("The following Policy Set was not adhered to: myName");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testExecuteSyncWithEvaluationSuccess() throws IOException {
    String payload = "{\"this\" : \"that\"}";
    JsonNode payloadObj = YamlUtils.readTree(payload).getNode().getCurrJsonNode();
    PolicyStepSpecParameters policyStepSpecParameters =
        PolicyStepSpecParameters.builder()
            .policySets(ParameterField.createValueField(projLevelPolicySet))
            .type("Custom")
            .policySpec(CustomPolicyStepSpec.builder().payload(ParameterField.createValueField(payload)).build())
            .build();
    stepParameters = StepElementParameters.builder().name(stepName).spec(policyStepSpecParameters).build();

    String urlPolicySets = "ps1";
    when(opaServiceClient.evaluateWithCredentialsByID(accountId, orgId, projectId, urlPolicySets,
             PolicyStepHelper.getEntityMetadataString(stepName), payloadObj))
        .thenReturn(request);

    OpaEvaluationResponseHolder evaluationResponse = OpaEvaluationResponseHolder.builder().status("pass").build();
    when(SafeHttpCall.executeWithErrorMessage(request)).thenReturn(evaluationResponse);
    when(PolicyStepOutcomeMapper.toOutcome(evaluationResponse))
        .thenReturn(PolicyStepOutcome.builder().status("pass").build());
    StepResponse stepResponse = policyStep.executeSync(ambiance, stepParameters, null, null);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(policyStep.getStepParametersClass()).isEqualTo(StepElementParameters.class);
  }
}
