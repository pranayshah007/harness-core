/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.shellscript;

import static io.harness.rule.OwnerRule.NAMANG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.rule.Owner;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class OutputAliasUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGenerateSweepingOutputKeyUsingUserAlias() {
    Ambiance ambiance = Ambiance.newBuilder().setExpressionFunctorToken(1234L).build();
    assertThatThrownBy(() -> OutputAliasUtils.generateSweepingOutputKeyUsingUserAlias("  ", ambiance))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Blank value provided for output alias. Please provide a valid value.");

    String toBeEncrypted = String.format("%s_%s", "1234", "test");
    String expectedEncodedValue = Hashing.murmur3_32_fixed()
                                      .hashString(toBeEncrypted, StandardCharsets.UTF_8)
                                      .toString()
                                      .replaceAll(AmbianceUtils.SPECIAL_CHARACTER_REGEX, "");
    assertThat(OutputAliasUtils.generateSweepingOutputKeyUsingUserAlias("test", ambiance))
        .isEqualTo(expectedEncodedValue);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testIsDuplicateKeyException() {
    assertThat(OutputAliasUtils.isDuplicateKeyException(new InvalidRequestException(""), " ")).isFalse();
    assertThat(OutputAliasUtils.isDuplicateKeyException(new GeneralException("  "), " ")).isFalse();
    assertThat(OutputAliasUtils.isDuplicateKeyException(
                   new GeneralException("Sweeping output with name uuid is already saved"), "uuid"))
        .isTrue();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testValidateExpressionFormat() {
    assertThat(OutputAliasUtils.validateExpressionFormat("a.b")).isFalse();
    assertThat(OutputAliasUtils.validateExpressionFormat("a.b.c.d")).isFalse();
    assertThat(OutputAliasUtils.validateExpressionFormat("a.b")).isFalse();
    assertThat(OutputAliasUtils.validateExpressionFormat("pipeline.   .c")).isFalse();
    assertThat(OutputAliasUtils.validateExpressionFormat("  .b")).isFalse();
    assertThat(OutputAliasUtils.validateExpressionFormat("a.b.c")).isFalse();
    assertThat(OutputAliasUtils.validateExpressionFormat("   .b.c")).isFalse();
    assertThat(OutputAliasUtils.validateExpressionFormat("pipeline.b.c")).isTrue();
    assertThat(OutputAliasUtils.validateExpressionFormat("stepGroup.b.c")).isTrue();
    assertThat(OutputAliasUtils.validateExpressionFormat("stage.b.c")).isTrue();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testToStepOutcomeGroup() {
    assertThatThrownBy(() -> OutputAliasUtils.toStepOutcomeGroup("  "))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Empty scope constant provided, can't be mapped to step outcome.");
    assertThatThrownBy(() -> OutputAliasUtils.toStepOutcomeGroup("random"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unsupported scope constant value : random");
    assertThat(OutputAliasUtils.toStepOutcomeGroup("pipeline")).isEqualTo(StepOutcomeGroup.PIPELINE.name());
    assertThat(OutputAliasUtils.toStepOutcomeGroup("stage")).isEqualTo(StepOutcomeGroup.STAGE.name());
    assertThat(OutputAliasUtils.toStepOutcomeGroup("stepGroup")).isEqualTo(StepOutcomeGroup.STEP_GROUP.name());
  }
}
