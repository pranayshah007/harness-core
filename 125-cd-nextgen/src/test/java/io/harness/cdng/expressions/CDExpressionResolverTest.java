/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.expressions;

import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensaml.xmlsec.signature.P;

public class CDExpressionResolverTest extends CategoryTest {
  @InjectMocks private CDExpressionResolver cdExpressionResolver;
  @Mock private EngineExpressionService engineExpressionService;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "accId")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgId")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projId")
                                        .build();

  private AutoCloseable mocks;
  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(any(), any());
    // behaviour of EngineGrpcExpressionService since proto cannot have null fields
    when(engineExpressionService.renderExpression(any(Ambiance.class), eq(null)))
        .thenThrow(new NullPointerException("cannot set expression as null"));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void renderExpressionTest() {
    String expression = "<+some_expression>";
    when(engineExpressionService.renderExpression(ambiance, expression)).thenReturn("result");

    String result = cdExpressionResolver.renderExpression(ambiance, expression);

    verify(engineExpressionService, times(1)).renderExpression(any(), any());
    assertThat(result).isEqualTo("result");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void renderExpressionTest_Null() {
    assertThat(cdExpressionResolver.renderExpression(ambiance, null)).isNull();
    verify(engineExpressionService, never()).renderExpression(any(), any());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void renderExpressionWithSkipCheckTest_Null() {
    assertThat(cdExpressionResolver.renderExpression(ambiance, null, true)).isNull();
    assertThat(cdExpressionResolver.renderExpression(ambiance, null, false)).isNull();
    verify(engineExpressionService, never()).renderExpression(any(), any());
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }
}
