/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template;

import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.account.AccountClient;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.remote.client.CGRestUtils;
import io.harness.rule.Owner;
import io.harness.template.utils.NGTemplateFeatureFlagHelperService;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class NGTemplateFeatureFlagHelperServiceTest extends CategoryTest {
  @InjectMocks NGTemplateFeatureFlagHelperService ngTemplateFeatureFlagHelperService;
  @Mock AccountClient accountClient;
  private final String ACCOUNT_ID = "accountId";
  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testFeatureFlag() {
    mockStatic(CGRestUtils.class);
    when(CGRestUtils.getResponse(any(), anyString())).thenReturn(true);
    assertThat(
        ngTemplateFeatureFlagHelperService.isFeatureFlagEnabled(ACCOUNT_ID, FeatureName.CDS_OPA_TEMPLATE_GOVERNANCE))
        .isTrue();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testFeatureFlagException() {
    mockStatic(CGRestUtils.class);
    when(CGRestUtils.getResponse(any(), anyString())).thenThrow(InvalidRequestException.class);
    assertThrows(UnexpectedException.class,
        ()
            -> ngTemplateFeatureFlagHelperService.isFeatureFlagEnabled(
                ACCOUNT_ID, FeatureName.CDS_OPA_TEMPLATE_GOVERNANCE));
  }
}
