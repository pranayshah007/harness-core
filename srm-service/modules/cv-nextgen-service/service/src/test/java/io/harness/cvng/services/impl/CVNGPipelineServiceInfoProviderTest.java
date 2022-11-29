/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.services.impl;

import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cvng.cdng.services.impl.CVNGPipelineServiceInfoProvider;
import io.harness.pms.sdk.PmsSdkInitValidator;
import io.harness.pms.utils.InjectorUtils;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CV)
@RunWith(MockitoJUnitRunner.class)
public class CVNGPipelineServiceInfoProviderTest {
  @InjectMocks private CVNGPipelineServiceInfoProvider serviceInfoProvider;
  @Mock private InjectorUtils injectorUtils;

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidatePlanCreatorFilterAndVariable() {
    PmsSdkInitValidator.validatePlanCreators(serviceInfoProvider);
    verify(injectorUtils, times(2)).injectMembers(notNull());
  }
}
