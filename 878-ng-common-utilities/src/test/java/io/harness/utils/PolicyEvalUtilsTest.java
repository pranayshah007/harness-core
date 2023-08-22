/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.exception.HttpResponseException;
import io.harness.exception.InvalidRequestException;
import io.harness.network.SafeHttpCall;
import io.harness.opaclient.OpaServiceClient;
import io.harness.opaclient.model.OpaEvaluationResponseHolder;
import io.harness.opaclient.model.OpaPolicySetEvaluationResponse;
import io.harness.plancreator.policy.PolicyConfig;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;

@OwnedBy(PIPELINE)
public class PolicyEvalUtilsTest extends CategoryTest {
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
    MockitoAnnotations.openMocks(this);
    Mockito.mockStatic(SafeHttpCall.class);
    Mockito.mockStatic(PolicyStepOutcomeMapper.class);
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
  public void testGetPolicySetsStringForQueryParam() {
    List<String> l1 = Collections.singletonList("ps1");
    String l1S = PolicyEvalUtils.getPolicySetsStringForQueryParam(l1);
    assertThat(l1S).isEqualTo("ps1");
    List<String> l2 = Arrays.asList("ps1", "ps2");
    String l2S = PolicyEvalUtils.getPolicySetsStringForQueryParam(l2);
    assertThat(l2S).isEqualTo("ps1,ps2");
    List<String> l3 = Arrays.asList("acc.ps1", "ps1", "org.ps1");
    String l3S = PolicyEvalUtils.getPolicySetsStringForQueryParam(l3);
    assertThat(l3S).isEqualTo("acc.ps1,ps1,org.ps1");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testIsInvalidPayload() {
    String valid = "{\"this\" : \"that\"}";
    assertThat(PolicyEvalUtils.isInvalidPayload(valid)).isFalse();
    String invalid = "{\"this\" : \"that";
    assertThat(PolicyEvalUtils.isInvalidPayload(invalid)).isTrue();
    String number = "12";
    assertThat(PolicyEvalUtils.isInvalidPayload(number)).isTrue();
    String string = "string";
    assertThat(PolicyEvalUtils.isInvalidPayload(string)).isTrue();
    String arrayOfObjects = "[{\"s\": \"d\"},{\"s\": \"d\"}]";
    assertThat(PolicyEvalUtils.isInvalidPayload(arrayOfObjects)).isFalse();

    // One of the array element is invalid
    arrayOfObjects = "[{\"s\": \"d\"},{\"s\": \"d\"]";
    assertThat(PolicyEvalUtils.isInvalidPayload(arrayOfObjects)).isTrue();

    String arrayOfArrayObjects = "[{\"s\": \"d\"},{\"s\": \"d\"}, [{\"s\": \"d\"},{\"s\": \"d\"}]]";
    assertThat(PolicyEvalUtils.isInvalidPayload(arrayOfArrayObjects)).isFalse();

    arrayOfArrayObjects = "[{\"s\": \"d\"},{\"s\": \"d\"}, [{\"s\": \"d\"},{\"s\": \"d\"]]";
    assertThat(PolicyEvalUtils.isInvalidPayload(arrayOfArrayObjects)).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildFailureStepResponse() {
    StepResponse stepResponse = PolicyEvalUtils.buildFailureStepResponse(
        ErrorCode.INVALID_JSON_PAYLOAD, "Custom payload is not a valid JSON.", FailureType.UNKNOWN_FAILURE);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    FailureInfo failureInfo = stepResponse.getFailureInfo();
    assertThat(failureInfo.getFailureDataCount()).isEqualTo(1);
    FailureData failureData = failureInfo.getFailureData(0);
    assertThat(failureData.getCode()).isEqualTo("INVALID_JSON_PAYLOAD");
    assertThat(failureData.getLevel()).isEqualTo("ERROR");
    assertThat(failureData.getMessage()).isEqualTo("Custom payload is not a valid JSON.");
    assertThat(failureData.getFailureTypesList()).containsExactly(FailureType.UNKNOWN_FAILURE);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetEntityMetadataString() {
    String stepName = "noSpaces";
    String entityMetadataString = PolicyEvalUtils.getEntityMetadataString(stepName);
    assertThat(entityMetadataString).isEqualTo("%7B%22entityName%22%3A%22noSpaces%22%7D");

    stepName = "has Spaces";
    entityMetadataString = PolicyEvalUtils.getEntityMetadataString(stepName);
    assertThat(entityMetadataString).isEqualTo("%7B%22entityName%22%3A%22has+Spaces%22%7D");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildPolicyEvaluationFailureMessage() {
    OpaEvaluationResponseHolder evaluationResponse0 =
        OpaEvaluationResponseHolder.builder()
            .status("error")
            .details(Collections.singletonList(
                OpaPolicySetEvaluationResponse.builder().status("error").name("myName").build()))
            .build();
    String singleErrorSingleResponse = PolicyEvalUtils.buildPolicyEvaluationFailureMessage(evaluationResponse0);
    assertThat(singleErrorSingleResponse).isEqualTo("The following Policy Set was not adhered to: myName");

    OpaEvaluationResponseHolder evaluationResponse1 =
        OpaEvaluationResponseHolder.builder()
            .status("error")
            .details(Arrays.asList(OpaPolicySetEvaluationResponse.builder().status("error").name("myName").build(),
                OpaPolicySetEvaluationResponse.builder().status("pass").name("my name").build()))
            .build();
    String singleErrorMultipleResponse = PolicyEvalUtils.buildPolicyEvaluationFailureMessage(evaluationResponse1);
    assertThat(singleErrorMultipleResponse).isEqualTo("The following Policy Set was not adhered to: myName");

    OpaEvaluationResponseHolder evaluationResponse2 =
        OpaEvaluationResponseHolder.builder()
            .status("error")
            .details(Arrays.asList(OpaPolicySetEvaluationResponse.builder().status("error").name("myName").build(),
                OpaPolicySetEvaluationResponse.builder().status("pass").name("my Name").build(),
                OpaPolicySetEvaluationResponse.builder().status("error").name("my name").build()))
            .build();
    String multipleErrors = PolicyEvalUtils.buildPolicyEvaluationFailureMessage(evaluationResponse2);
    assertThat(multipleErrors).isEqualTo("The following Policy Sets were not adhered to: myName, my name");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testExecuteSyncWithApplicationFailure() throws IOException {
    stepParameters =
        StepElementParameters.builder()
            .name(stepName)
            .enforce(PolicyConfig.builder().policySets(ParameterField.createValueField(projLevelPolicySet)).build())
            .build();

    String urlPolicySets = "ps1";
    when(opaServiceClient.evaluateWithCredentialsByID(accountId, orgId, projectId, urlPolicySets,
             PolicyEvalUtils.getEntityMetadataString(stepName), Collections.emptyList()))
        .thenReturn(request);
    when(SafeHttpCall.executeWithErrorMessage(request)).thenThrow(new HttpResponseException(400, "My Invalid Request"));
    StepResponse stepResponse = PolicyEvalUtils.evalPolicies(
        ambiance, stepParameters, StepResponse.builder().status(Status.SUCCEEDED).build(), opaServiceClient);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getMessage())
        .isEqualTo("Unexpected error occurred while evaluating Policies.");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testExecuteSyncWithPolicyNotFoundError() throws IOException {
    stepParameters =
        StepElementParameters.builder()
            .name(stepName)
            .enforce(PolicyConfig.builder().policySets(ParameterField.createValueField(projLevelPolicySet)).build())
            .build();

    String urlPolicySets = "ps1";
    when(opaServiceClient.evaluateWithCredentialsByID(accountId, orgId, projectId, urlPolicySets,
             PolicyEvalUtils.getEntityMetadataString(stepName), Collections.emptyList()))
        .thenReturn(request);
    String errorString = "{\n"
        + "    \"identifier\" : \"thisSet\",\n"
        + "    \"message\" : \"policy set 'thisSet' is disabled.\"\n"
        + "}";
    when(SafeHttpCall.executeWithErrorMessage(request)).thenThrow(new InvalidRequestException(errorString));
    StepResponse stepResponse = PolicyEvalUtils.evalPolicies(
        ambiance, stepParameters, StepResponse.builder().status(Status.SUCCEEDED).build(), opaServiceClient);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getMessage())
        .isEqualTo("Policy Set 'thisSet' is disabled.");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testExecuteSyncWithEvaluationFailure() throws IOException {
    stepParameters =
        StepElementParameters.builder()
            .name(stepName)
            .enforce(PolicyConfig.builder().policySets(ParameterField.createValueField(projLevelPolicySet)).build())
            .build();

    String urlPolicySets = "ps1";
    when(opaServiceClient.evaluateWithCredentialsByID(accountId, orgId, projectId, urlPolicySets,
             PolicyEvalUtils.getEntityMetadataString(stepName), Collections.emptyList()))
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
    StepResponse stepResponse = PolicyEvalUtils.evalPolicies(
        ambiance, stepParameters, StepResponse.builder().status(Status.SUCCEEDED).build(), opaServiceClient);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getMessage())
        .isEqualTo("The following Policy Set was not adhered to: myName");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testExecuteSyncWithEvaluationSuccess() throws IOException {
    stepParameters =
        StepElementParameters.builder()
            .name(stepName)
            .enforce(PolicyConfig.builder().policySets(ParameterField.createValueField(projLevelPolicySet)).build())
            .build();

    String urlPolicySets = "ps1";
    when(opaServiceClient.evaluateWithCredentialsByID(accountId, orgId, projectId, urlPolicySets,
             PolicyEvalUtils.getEntityMetadataString(stepName), Collections.emptyList()))
        .thenReturn(request);

    OpaEvaluationResponseHolder evaluationResponse = OpaEvaluationResponseHolder.builder().status("pass").build();
    when(SafeHttpCall.executeWithErrorMessage(request)).thenReturn(evaluationResponse);
    when(PolicyStepOutcomeMapper.toOutcome(evaluationResponse))
        .thenReturn(PolicyStepOutcome.builder().status("pass").build());
    StepResponse stepResponse = PolicyEvalUtils.evalPolicies(
        ambiance, stepParameters, StepResponse.builder().status(Status.SUCCEEDED).build(), opaServiceClient);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testEvalPoliciesWithEmptyPolicySetsList() throws IOException {
    stepParameters =
        StepElementParameters.builder()
            .name(stepName)
            .enforce(PolicyConfig.builder().policySets(ParameterField.createValueField(new ArrayList<>())).build())
            .build();
    StepResponse stepResponse = StepResponse.builder().status(Status.SUCCEEDED).build();
    StepResponse stepResponse1 = PolicyEvalUtils.evalPolicies(ambiance, stepParameters, stepResponse, opaServiceClient);
    assertEquals(stepResponse1, stepResponse);
  }
}
