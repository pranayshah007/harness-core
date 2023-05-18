/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContextImpl;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(CDP)
public class AwsAmiServiceStateHelperTest extends WingsBaseTest {
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private SettingsService settingsService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private SecretManager secretManager;
  @Mock private SweepingOutputService sweepingOutputService;

  @InjectMocks @Spy AwsAmiServiceStateHelper awsAmiServiceStateHelper;

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetSetupElementFromSweepingOutput() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    String prefix = "prefix";
    doReturn("name").when(awsAmiServiceStateHelper).getSweepingOutputName(mockContext, prefix);
    doReturn(SweepingOutputInquiry.builder()).when(mockContext).prepareSweepingOutputInquiryBuilder();
    awsAmiServiceStateHelper.getSetupElementFromSweepingOutput(mockContext, prefix);
    verify(sweepingOutputService, times(1)).findSweepingOutput(any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetSweepingOutputName() {
    String suffix = "sufix";
    String prefix = "prefix";
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    PhaseElement phaseElement = mock(PhaseElement.class);
    doReturn(phaseElement).when(mockContext).getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    doReturn(ServiceElement.builder().uuid(suffix).build()).when(phaseElement).getServiceElement();
    String sweepingOutputName = awsAmiServiceStateHelper.getSweepingOutputName(mockContext, prefix);
    assertThat(sweepingOutputName).isEqualTo(prefix + suffix);
  }
}
