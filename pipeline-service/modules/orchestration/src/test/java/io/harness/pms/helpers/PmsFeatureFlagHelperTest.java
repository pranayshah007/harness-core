/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.helpers;

import static io.harness.beans.FeatureName.NG_SVC_ENV_REDESIGN;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class PmsFeatureFlagHelperTest extends CategoryTest {
  private static final String accountId = "accountId";

  @Mock FeatureFlagService featureFlagService;
  @InjectMocks PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @Before
  public void setUp() {
    doReturn(true).when(featureFlagService).isEnabled(NG_SVC_ENV_REDESIGN, accountId);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void isEnabled() throws IOException {
    assertThat(pmsFeatureFlagHelper.isEnabled(accountId, "dd")).isFalse();
    assertThat(pmsFeatureFlagHelper.isEnabled(accountId, NG_SVC_ENV_REDESIGN)).isTrue();
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void validateRefreshCacheForGivenAccountId() {
    assertThatThrownBy(() -> pmsFeatureFlagHelper.refreshCacheForGivenAccountId(accountId))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Cache will be automatically refreshed within 5 mins");
  }
}
