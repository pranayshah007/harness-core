/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.serializer;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.HARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.doReturn;

import io.harness.CiBeansTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.ngexception.CIStageExecutionUserException;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@Slf4j
@OwnedBy(CI)
public class RunTimeInputHandlerTest extends CiBeansTestBase {
  @Mock private ParameterField parameterField;
  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIntegerRunTimeInput() throws IOException {
    assertThat(RunTimeInputHandler.resolveIntegerParameter(
                   ParameterField.<Integer>builder().value(Integer.valueOf(10)).build(), 1))
        .isEqualTo(10);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIntegerRunTimeInvalidInput() throws IOException {
    doReturn("abc").when(parameterField).fetchFinalValue();
    doReturn("abc").when(parameterField).getValue();

    assertThatExceptionOfType(CIStageExecutionUserException.class)
        .isThrownBy(() -> RunTimeInputHandler.resolveIntegerParameter(parameterField, 1));
  }
}
