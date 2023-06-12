/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.expressions;

import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.engine.expressions.NGTriggerExpressionEvaluator;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class NGTriggerExpressionEvaluatorProviderTest extends CategoryTest {
  @InjectMocks NGTriggerExpressionEvaluatorProvider ngTriggerExpressionEvaluatorProvider;
  Ambiance ambiance;
  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }
  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGetNGTriggerExpressionEvaluator() throws IOException {
    ambiance = Ambiance.newBuilder().build();
    assertThat(ngTriggerExpressionEvaluatorProvider.get(ambiance)).isInstanceOf(NGTriggerExpressionEvaluator.class);
  }
}
