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

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.opaclient.model.OpaEvaluationResponseHolder;
import io.harness.opaclient.model.OpaPolicySetEvaluationResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PolicyStepHelperTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPolicySetsStringForQueryParam() {
    List<String> l1 = Collections.singletonList("ps1");
    String l1S = PolicyStepHelper.getPolicySetsStringForQueryParam(l1);
    assertThat(l1S).isEqualTo("ps1");
    List<String> l2 = Arrays.asList("ps1", "ps2");
    String l2S = PolicyStepHelper.getPolicySetsStringForQueryParam(l2);
    assertThat(l2S).isEqualTo("ps1,ps2");
    List<String> l3 = Arrays.asList("acc.ps1", "ps1", "org.ps1");
    String l3S = PolicyStepHelper.getPolicySetsStringForQueryParam(l3);
    assertThat(l3S).isEqualTo("acc.ps1,ps1,org.ps1");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testIsInvalidPayload() {
    String valid = "{\"this\" : \"that\"}";
    assertThat(PolicyStepHelper.isInvalidPayload(valid)).isFalse();
    String invalid = "{\"this\" : \"that";
    assertThat(PolicyStepHelper.isInvalidPayload(invalid)).isTrue();
    String number = "12";
    assertThat(PolicyStepHelper.isInvalidPayload(number)).isTrue();
    String string = "string";
    assertThat(PolicyStepHelper.isInvalidPayload(string)).isTrue();
    String arrayOfObjects = "[{\"s\": \"d\"},{\"s\": \"d\"}]";
    assertThat(PolicyStepHelper.isInvalidPayload(arrayOfObjects)).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildFailureStepResponse() {
    StepResponse stepResponse = PolicyStepHelper.buildFailureStepResponse(
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
    String entityMetadataString = PolicyStepHelper.getEntityMetadataString(stepName);
    assertThat(entityMetadataString).isEqualTo("%7B%22entityName%22%3A%22noSpaces%22%7D");

    stepName = "has Spaces";
    entityMetadataString = PolicyStepHelper.getEntityMetadataString(stepName);
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
    String singleErrorSingleResponse = PolicyStepHelper.buildPolicyEvaluationFailureMessage(evaluationResponse0);
    assertThat(singleErrorSingleResponse).isEqualTo("The following Policy Set was not adhered to: myName");

    OpaEvaluationResponseHolder evaluationResponse1 =
        OpaEvaluationResponseHolder.builder()
            .status("error")
            .details(Arrays.asList(OpaPolicySetEvaluationResponse.builder().status("error").name("myName").build(),
                OpaPolicySetEvaluationResponse.builder().status("pass").name("my name").build()))
            .build();
    String singleErrorMultipleResponse = PolicyStepHelper.buildPolicyEvaluationFailureMessage(evaluationResponse1);
    assertThat(singleErrorMultipleResponse).isEqualTo("The following Policy Set was not adhered to: myName");

    OpaEvaluationResponseHolder evaluationResponse2 =
        OpaEvaluationResponseHolder.builder()
            .status("error")
            .details(Arrays.asList(OpaPolicySetEvaluationResponse.builder().status("error").name("myName").build(),
                OpaPolicySetEvaluationResponse.builder().status("pass").name("my Name").build(),
                OpaPolicySetEvaluationResponse.builder().status("error").name("my name").build()))
            .build();
    String multipleErrors = PolicyStepHelper.buildPolicyEvaluationFailureMessage(evaluationResponse2);
    assertThat(multipleErrors).isEqualTo("The following Policy Sets were not adhered to: myName, my name");
  }
}
