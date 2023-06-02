/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.TMACARI;

import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.AmiServiceSetupElement;
import software.wings.api.AwsAmiInfoVariables;
import software.wings.api.pcf.InfoVariables;
import software.wings.beans.container.UserDataSpecification;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContextImpl;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

@OwnedBy(CDP)
public class AwsStateHelperTest extends WingsBaseTest {
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @InjectMocks private AwsStateHelper awsStateHelper;

  private static final String USER_DATA = "echo hello";
  private static final String USER_DATA_ENCODED = "ZWNobyBoZWxsbw==";

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testFetchRequiredAsgCapacity() {
    AwsStateHelper helper = spy(AwsStateHelper.class);
    Map<String, Integer> map = ImmutableMap.of("foo__1", 1, "foo__2", 1);
    assertThatThrownBy(() -> helper.fetchRequiredAsgCapacity(map, "foo__3"))
        .isInstanceOf(InvalidRequestException.class);
    assertThat(helper.fetchRequiredAsgCapacity(map, "foo__1")).isEqualTo(1);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetTimeoutFromContext() {
    AwsStateHelper helper = new AwsStateHelper();

    AmiServiceSetupElement setupElement = AmiServiceSetupElement.builder().autoScalingSteadyStateTimeout(10).build();
    assertThat(helper.getAmiStateTimeout(setupElement)).isEqualTo(600000);

    setupElement = AmiServiceSetupElement.builder().autoScalingSteadyStateTimeout(0).build();
    assertThat(helper.getAmiStateTimeout(setupElement)).isEqualTo(null);

    setupElement = AmiServiceSetupElement.builder().autoScalingSteadyStateTimeout(null).build();
    assertThat(helper.getAmiStateTimeout(setupElement)).isEqualTo(null);

    setupElement = AmiServiceSetupElement.builder().autoScalingSteadyStateTimeout(35792).build();
    assertThat(helper.getAmiStateTimeout(setupElement)).isEqualTo(null);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetEncodedUserData() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    UserDataSpecification userDataSpec = UserDataSpecification.builder().build();
    doReturn(userDataSpec).when(serviceResourceService).getUserDataSpecification(APP_ID, SERVICE_ID);
    when(mockContext.renderExpression(nullable(String.class))).thenAnswer((Answer<String>) invocation -> {
      Object[] args = invocation.getArguments();
      return (String) args[0];
    });

    String encodedUserData = awsStateHelper.getEncodedUserData(APP_ID, SERVICE_ID, mockContext);
    assertThat(encodedUserData).isNull();

    userDataSpec.setData(USER_DATA);
    encodedUserData = awsStateHelper.getEncodedUserData(APP_ID, SERVICE_ID, mockContext);
    assertThat(encodedUserData).isEqualTo(USER_DATA_ENCODED);
    verify(mockContext).renderExpression(USER_DATA);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testPopulateAmiVariables() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    AwsAmiInfoVariables awsAmiInfoVariables =
        AwsAmiInfoVariables.builder().newAsgName("ASG1").oldAsgName("ASG2").build();
    doReturn(SweepingOutputInquiry.builder()).when(mockContext).prepareSweepingOutputInquiryBuilder();
    doReturn(SweepingOutputInstance.builder()).when(mockContext).prepareSweepingOutputBuilder(any());
    doReturn(InfoVariables.builder().build()).when(sweepingOutputService).findSweepingOutput(any());
    awsStateHelper.populateAmiVariables(mockContext, awsAmiInfoVariables);

    verify(sweepingOutputService).findSweepingOutput(any());
    verify(sweepingOutputService, times(0)).save(any());
    Mockito.reset(sweepingOutputService);

    doReturn(null).when(sweepingOutputService).findSweepingOutput(any());
    doReturn(true).when(workflowExecutionService).isMultiService(nullable(String.class), nullable(String.class));

    ArgumentCaptor<SweepingOutputInstance> sweepingOutputInstanceCaptor =
        ArgumentCaptor.forClass(SweepingOutputInstance.class);
    doReturn(null).when(sweepingOutputService).save(sweepingOutputInstanceCaptor.capture());
    awsStateHelper.populateAmiVariables(mockContext, awsAmiInfoVariables);

    verify(sweepingOutputService).findSweepingOutput(any());
    verify(workflowExecutionService).isMultiService(nullable(String.class), nullable(String.class));
    SweepingOutputInstance instance = sweepingOutputInstanceCaptor.getValue();
    assertThat(instance.getValue()).isEqualTo(awsAmiInfoVariables);
  }
}
