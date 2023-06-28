/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.TMACARI;

import static software.wings.utils.WingsTestConstants.APP_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfInBuiltVariablesUpdateValues;
import io.harness.delegate.beans.pcf.CfRouteUpdateRequestConfigData;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.delegate.task.pcf.response.CfRouteUpdateCommandResponse;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.api.pcf.PcfRouteUpdateStateExecutionData;
import software.wings.api.pcf.SetupSweepingOutputPcf;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
public class PcfSwitchBlueGreenRoutesTest extends WingsBaseTest {
  @Mock private AppService appService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private SecretManager secretManager;
  @Mock private SettingsService settingsService;
  @Mock private ActivityService activityService;
  @Mock private PcfStateHelper pcfStateHelper;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private ExecutionContextImpl context;

  @InjectMocks private PcfSwitchBlueGreenRoutes pcfSwitchBlueGreenRoutes;

  @Before
  public void setup() throws IllegalAccessException {
    pcfSwitchBlueGreenRoutes.setName("name");
    doReturn(APP_ID).when(context).getAppId();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetNewApplicationName() {
    PcfSwitchBlueGreenRoutes pcfSwitchBlueGreenRoutes = new PcfSwitchBlueGreenRoutes("");
    assertThat(pcfSwitchBlueGreenRoutes.getNewApplicationName(null)).isEqualTo(StringUtils.EMPTY);
    assertThat(pcfSwitchBlueGreenRoutes.getNewApplicationName(SetupSweepingOutputPcf.builder().build()))
        .isEqualTo(StringUtils.EMPTY);
    assertThat(pcfSwitchBlueGreenRoutes.getNewApplicationName(
                   SetupSweepingOutputPcf.builder()
                       .newPcfApplicationDetails(CfAppSetupTimeDetails.builder().applicationName("name").build())
                       .build()))
        .isEqualTo("name");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetTimeoutMillis() throws IllegalAccessException {
    doReturn(10).when(pcfStateHelper).getStateTimeoutMillis(context, 5, false);
    assertThat(pcfSwitchBlueGreenRoutes.getTimeoutMillis(context)).isEqualTo(10);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    Map<String, ResponseData> response = new HashMap<>();
    PcfRouteUpdateStateExecutionData stateExecutionData =
        PcfRouteUpdateStateExecutionData.builder()
            .pcfRouteUpdateRequestConfigData(CfRouteUpdateRequestConfigData.builder().build())
            .build();
    CfInBuiltVariablesUpdateValues updatedValues =
        CfInBuiltVariablesUpdateValues.builder().newAppName("newApp").oldAppName("oldApp").build();
    CfRouteUpdateCommandResponse cfRouteUpdateCommandResponse =
        CfRouteUpdateCommandResponse.builder().updateValues(updatedValues).build();
    CfCommandExecutionResponse commandExecutionResponse = CfCommandExecutionResponse.builder()
                                                              .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                              .errorMessage("ERROR_MESSAGE")
                                                              .pcfCommandResponse(cfRouteUpdateCommandResponse)
                                                              .build();
    response.put("1", commandExecutionResponse);
    doReturn(stateExecutionData).when(context).getStateExecutionData();
    doReturn("TEST_NAME").when(pcfStateHelper).obtainSwapRouteSweepingOutputName(context, false);
    doReturn(SweepingOutputInstance.builder()).when(context).prepareSweepingOutputBuilder(any());

    ExecutionResponse executionResponse = pcfSwitchBlueGreenRoutes.handleAsyncResponse(context, response);

    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getErrorMessage()).isEqualTo("ERROR_MESSAGE");
    assertThat(executionResponse.getStateExecutionData()).isEqualTo(stateExecutionData);
    verify(pcfStateHelper, times(1)).updateInfoVariables(context, stateExecutionData, commandExecutionResponse, false);
  }
}
