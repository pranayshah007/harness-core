/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.utils.PmsConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.resourcerestraint.beans.AcquireMode;
import io.harness.steps.resourcerestraint.beans.HoldingScope;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class QueueSpecParametersTest {
  @Test
  @Owner(developers = OwnerRule.FERNANDOD)
  @Category(UnitTests.class)
  public void shouldAcquireModeHasEnsure() {
    assertThat(createSpecParameters().getAcquireMode()).isEqualTo(AcquireMode.ENSURE);
  }

  @Test
  @Owner(developers = OwnerRule.FERNANDOD)
  @Category(UnitTests.class)
  public void shouldNameHasQueuing() {
    assertThat(createSpecParameters().getName()).isEqualTo(PmsConstants.QUEUING_RC_NAME);
  }

  @Test
  @Owner(developers = OwnerRule.FERNANDOD)
  @Category(UnitTests.class)
  public void shouldPermitsHasOne() {
    assertThat(createSpecParameters().getPermits()).isEqualTo(PmsConstants.QUEUING_RC_PERMITS);
  }

  private QueueSpecParameters createSpecParameters() {
    return new QueueSpecParameters(ParameterField.<String>builder().build(), HoldingScope.PIPELINE);
  }
}
