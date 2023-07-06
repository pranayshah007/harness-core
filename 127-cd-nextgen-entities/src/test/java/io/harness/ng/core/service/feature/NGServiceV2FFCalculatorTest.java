/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.feature;

import static io.harness.rule.OwnerRule.PRATYUSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;

import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class NGServiceV2FFCalculatorTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private NGFeatureFlagHelperService featureFlagHelperService;
  @InjectMocks private NGServiceV2FFCalculator ngServiceV2FFCalculator;
  Map<FeatureName, Boolean> featureFlags = new HashMap<>();
  private static String ACCOUNT_ID = "accountId";
  AutoCloseable mocks;

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    featureFlags.put(FeatureName.CDS_HELM_MULTIPLE_MANIFEST_SUPPORT_NG, true);
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testComputeFlags() {
    doReturn(true)
        .when(featureFlagHelperService)
        .isEnabled(ACCOUNT_ID, FeatureName.CDS_HELM_MULTIPLE_MANIFEST_SUPPORT_NG);
    assertThat(ngServiceV2FFCalculator.computeFlags(ACCOUNT_ID)).isEqualTo(featureFlags);
  }
}
