/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform.functor;

import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.provision.terraform.output.TerraformPlanJsonOutput;
import io.harness.exception.IllegalArgumentException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;
import io.harness.terraform.expression.TerraformPlanExpressionInterface;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class TerraformPlanJsonFunctorTest extends CategoryTest {
  @Mock private ExecutionSweepingOutputService sweepingOutputService;

  @InjectMocks private TerraformPlanJsonFunctor terraformPlanJsonFunctor;

  private final Ambiance ambiance = Ambiance.newBuilder().build();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetInvalidArguments() {
    assertThatThrownBy(() -> terraformPlanJsonFunctor.get(ambiance)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetNoSweepingOutput() {
    final OptionalSweepingOutput optionalSweepingOutput = OptionalSweepingOutput.builder().found(false).build();

    doReturn(optionalSweepingOutput)
        .when(sweepingOutputService)
        .resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject("outputName"));

    assertThatThrownBy(() -> terraformPlanJsonFunctor.get(ambiance, "outputName"))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGet() {
    final OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder()
            .found(true)
            .output(TerraformPlanJsonOutput.builder().tfPlanFileId("fileId").build())
            .build();

    doReturn(optionalSweepingOutput)
        .when(sweepingOutputService)
        .resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject("outputName"));

    assertThat(terraformPlanJsonFunctor.get(ambiance, "outputName"))
        .isEqualTo(String.format(TerraformPlanExpressionInterface.DELEGATE_EXPRESSION, "fileId",
            ambiance.getExpressionFunctorToken(), "jsonFilePath"));
  }
}