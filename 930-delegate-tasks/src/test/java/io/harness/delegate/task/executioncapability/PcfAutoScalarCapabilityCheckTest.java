/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.executioncapability;

import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.PcfAutoScalarCapability;
import io.harness.pcf.CfCliDelegateResolver;
import io.harness.pcf.PcfUtils;
import io.harness.pcf.PivotalClientApiException;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CDP)
public class PcfAutoScalarCapabilityCheckTest extends CategoryTest {
  @Mock CfCliDelegateResolver cfCliDelegateResolver;
  @Inject @InjectMocks private PcfAutoScalarCapabilityCheck pcfAutoScalarCapabilityCheck;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldPerformCapabilityCheck() throws PivotalClientApiException {
    try (MockedStatic<PcfUtils> ignored = Mockito.mockStatic(PcfUtils.class)) {
      when(PcfUtils.checkIfAppAutoscalarInstalled(anyString(), any())).thenReturn(true);
      when(cfCliDelegateResolver.getAvailableCfCliPathOnDelegate(any())).thenReturn(Optional.of("cf-cli-path"));
      CapabilityResponse capabilityResponse =
          pcfAutoScalarCapabilityCheck.performCapabilityCheck(PcfAutoScalarCapability.builder().build());
      assertThat(capabilityResponse).isNotNull();
      assertThat(capabilityResponse.isValidated()).isTrue();
    }
  }
}
