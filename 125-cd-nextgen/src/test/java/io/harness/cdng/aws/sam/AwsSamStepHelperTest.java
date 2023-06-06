/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.sam;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.SAINATH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.powermock.api.mockito.PowerMockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.AwsSamInfrastructureOutcome;
import io.harness.delegate.beans.instancesync.info.AwsSamServerInstanceInfo;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.rule.Owner;

import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class AwsSamStepHelperTest extends CategoryTest {
  @Mock private OutcomeService outcomeService;

  @InjectMocks @Spy AwsSamStepHelper awsSamStepHelper;

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testGetInfrastructureOutcome() {
    Ambiance ambiance = Ambiance.newBuilder().build();

    AwsSamInfrastructureOutcome awsSamInfrastructureOutcome = AwsSamInfrastructureOutcome.builder().build();
    doReturn(awsSamInfrastructureOutcome).when(outcomeService).resolve(any(), any());
    assertThat(awsSamStepHelper.getInfrastructureOutcome(ambiance)).isEqualTo(awsSamInfrastructureOutcome);
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testUpdateServerInstanceInfoList() {
    AwsSamInfrastructureOutcome awsSamInfrastructureOutcome =
        AwsSamInfrastructureOutcome.builder().infrastructureKey("infrastructureKey").region("region").build();

    AwsSamServerInstanceInfo awsSamServerInstanceInfo = AwsSamServerInstanceInfo.builder().build();

    awsSamStepHelper.updateServerInstanceInfoList(Arrays.asList(awsSamServerInstanceInfo), awsSamInfrastructureOutcome);

    assertThat(awsSamServerInstanceInfo.getRegion()).isEqualTo(awsSamInfrastructureOutcome.getRegion());
    assertThat(awsSamServerInstanceInfo.getInfraStructureKey())
        .isEqualTo(awsSamInfrastructureOutcome.getInfrastructureKey());
  }
}
