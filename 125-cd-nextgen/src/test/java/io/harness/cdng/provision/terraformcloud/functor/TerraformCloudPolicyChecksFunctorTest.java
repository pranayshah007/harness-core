/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud.functor;

import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.provision.terraform.executions.TerraformCloudPlanExecutionDetails;
import io.harness.cdng.provision.terraformcloud.executiondetails.TerraformCloudPlanExecutionDetailsService;
import io.harness.exception.IllegalArgumentException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class TerraformCloudPolicyChecksFunctorTest extends CategoryTest {
  @Mock private TerraformCloudPlanExecutionDetailsService terraformCloudPlanExecutionDetailsService;

  @InjectMocks private TerraformCloudPolicyChecksJsonFunctor terraformCloudPolicyChecksJsonFunctor;

  private final Ambiance ambiance = Ambiance.newBuilder().build();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetInvalidArguments() {
    assertThatThrownBy(() -> terraformCloudPolicyChecksJsonFunctor.get(ambiance))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Inappropriate usage of 'policyChecksJson' functor. Missing policy checks json output argument");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetExecutionDetails() {
    doReturn(Collections.emptyList())
        .when(terraformCloudPlanExecutionDetailsService)
        .listAllPipelineTFCloudPlanExecutionDetails(any(), any());

    assertThatThrownBy(() -> terraformCloudPolicyChecksJsonFunctor.get(ambiance, "outputName"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Missing output: outputName. Terraform Cloud Policy Checks wasn't exported.");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGet() {
    doReturn(List.of(TerraformCloudPlanExecutionDetails.builder()
                         .provisionerId("provisionerId")
                         .tfcPolicyChecksFileId("tfcPolicyChecksFileId")
                         .build()))
        .when(terraformCloudPlanExecutionDetailsService)
        .listAllPipelineTFCloudPlanExecutionDetails(any(), any());

    Object result = terraformCloudPolicyChecksJsonFunctor.get(ambiance, "provisionerId");

    assertThat((String) result)
        .isEqualTo(
            "${delegateTerraformPlan.obtainPolicyChecks(\"tfcPolicyChecksFileId\", 0).policyChecksJsonFilePath()}");
  }
}