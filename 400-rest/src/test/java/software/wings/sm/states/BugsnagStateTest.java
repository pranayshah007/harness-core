/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.Application;
import software.wings.beans.BugsnagConfig;
import software.wings.beans.SettingAttribute;
import software.wings.service.impl.analysis.CustomLogDataCollectionInfo;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.states.BugsnagState.BugsnagStateKeys;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@Slf4j
public class BugsnagStateTest extends APMStateVerificationTestBase {
  @Mock private SettingsService settingsService;
  @Mock private AppService appService;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private DelegateService delegateService;
  @Mock private SecretManager secretManager;

  private BugsnagState bugsnagState;
  private BugsnagConfig bugsnagConfig;
  private String configId;
  private VerificationStateAnalysisExecutionData executionData;
  private Set<String> hosts;

  @Before
  public void setUp() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);

    String accountId = generateUuid();
    String appId = generateUuid();
    configId = generateUuid();
    bugsnagState = Mockito.spy(new BugsnagState("bugsnagState"));
    bugsnagState.setProjectId(generateUuid());
    bugsnagConfig = BugsnagConfig.builder().accountId(accountId).build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(accountId)
                                            .withAppId(appId)
                                            .withValue(bugsnagConfig)
                                            .build();
    executionData = VerificationStateAnalysisExecutionData.builder().build();
    hosts = new HashSet<>();
    hosts.add("host1");

    Application app = new Application();
    app.setName("name");

    FieldUtils.writeField(bugsnagState, "settingsService", settingsService, true);
    FieldUtils.writeField(bugsnagState, "appService", appService, true);
    FieldUtils.writeField(bugsnagState, "waitNotifyEngine", waitNotifyEngine, true);
    FieldUtils.writeField(bugsnagState, "delegateService", delegateService, true);
    FieldUtils.writeField(bugsnagState, "secretManager", secretManager, true);

    when(settingsService.get(configId)).thenReturn(settingAttribute);
    when(appService.get(any())).thenReturn(app);

    doReturn(configId).when(bugsnagState).getResolvedConnectorId(any(), any(), any());
    doReturn(workflowId).when(bugsnagState).getWorkflowId(any());
    doReturn(serviceId).when(bugsnagState).getPhaseServiceId(any());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testTriggerAnalysisDataCollection_whenConnectorIdIsValid() {
    bugsnagState.triggerAnalysisDataCollection(executionContext, executionData, hosts);

    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(1)).queueTaskV2(delegateTaskArgumentCaptor.capture());

    DelegateTask task = delegateTaskArgumentCaptor.getValue();
    CustomLogDataCollectionInfo dataCollectionInfo = (CustomLogDataCollectionInfo) task.getData().getParameters()[0];

    assertThat(dataCollectionInfo.getAccountId()).isEqualTo(accountId);
    ArgumentCaptor<String> fieldCaptor = ArgumentCaptor.forClass(String.class);
    verify(bugsnagState, times(2)).getResolvedFieldValue(any(), fieldCaptor.capture(), any());

    Set<String> fields = new HashSet<>(fieldCaptor.getAllValues());
    Set<String> expectedFields =
        new HashSet<>(Arrays.asList(BugsnagStateKeys.projectId, BugsnagStateKeys.releaseStage));

    assertThat(fields).isEqualTo(expectedFields);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testTriggerAnalysisDataCollection_whenConnectorIdIsInValid() {
    when(settingsService.get(configId)).thenReturn(null);
    assertThatThrownBy(() -> bugsnagState.triggerAnalysisDataCollection(executionContext, executionData, hosts))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("No connector found with id " + configId);
  }
}
