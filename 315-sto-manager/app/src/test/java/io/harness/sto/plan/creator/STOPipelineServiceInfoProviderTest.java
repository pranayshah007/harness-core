/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.sto.plan.creator;

import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.sdk.PmsSdkInitValidator;
import io.harness.pms.utils.InjectorUtils;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.STO)
@RunWith(MockitoJUnitRunner.class)
public class STOPipelineServiceInfoProviderTest {
  @InjectMocks private STOPipelineServiceInfoProvider serviceInfoProvider;
  @Mock private InjectorUtils injectorUtils;

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidatePlanCreatorFilterAndVariable() {
    PmsSdkInitValidator.validatePlanCreators(serviceInfoProvider);

    // TO VALIDATE PLAN CREATORS WE DON'T CARE ABOUT InjectorUtils USAGE, BUT WITHOUT
    // THIS VERIFY OPERATION THE TEST FAIL AS NPE IS THROW. WE CAN REMOVE THIS WHEN
    // OTHER TEST SCENARIOS WERE ADDED TO THE TEST CLASS.
    verify(injectorUtils, times(2)).injectMembers(notNull());
  }
}
