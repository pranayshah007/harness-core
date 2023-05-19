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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.GcpConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SumoConfig;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.stackdriver.StackDriverLogDataCollectionInfo;
import software.wings.service.impl.sumo.SumoDataCollectionInfo;
import software.wings.service.intfc.SettingsService;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class AbstractLogAnalysisStateTest extends WingsBaseTest {
  @Mock private SettingsService settingsService;

  private StackDriverLogState stackDriverLogState;
  private SumoLogicAnalysisState sumoLogicAnalysisState;
  private GcpConfig gcpConfig;
  private SumoConfig sumoConfig;
  private AnalysisContext analysisContext;
  private SettingAttribute gcpSetting;
  private SettingAttribute sumoSetting;
  private String accountId;
  private String appId;
  private String configId;

  @Before
  public void setUp() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);

    accountId = generateUuid();
    appId = generateUuid();
    configId = generateUuid();
    stackDriverLogState = Mockito.spy(new StackDriverLogState("stackDriverLog"));
    sumoLogicAnalysisState = Mockito.spy(new SumoLogicAnalysisState("sumoState"));
    analysisContext = AnalysisContext.builder().appId(appId).accountId(accountId).query("query").build();
    gcpConfig = GcpConfig.builder().accountId(accountId).build();
    sumoConfig = SumoConfig.builder().accountId(accountId).build();
    gcpSetting = SettingAttribute.Builder.aSettingAttribute()
                     .withAccountId(accountId)
                     .withAppId(appId)
                     .withValue(gcpConfig)
                     .build();
    sumoSetting = SettingAttribute.Builder.aSettingAttribute()
                      .withAccountId(accountId)
                      .withAppId(appId)
                      .withValue(sumoConfig)
                      .build();

    FieldUtils.writeField(stackDriverLogState, "settingsService", settingsService, true);
    FieldUtils.writeField(sumoLogicAnalysisState, "settingsService", settingsService, true);

    when(settingsService.get(configId)).thenReturn(gcpSetting);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCreateDataCollectionInfo_stackDriverLog() {
    doReturn(configId).when(stackDriverLogState).getResolvedConnectorId(any(), any(), any());
    doReturn("query").when(stackDriverLogState).getRenderedQuery();
    StackDriverLogDataCollectionInfo stackDriverLogDataCollectionInfo =
        (StackDriverLogDataCollectionInfo) stackDriverLogState.createDataCollectionInfo(analysisContext, null);

    assertThat(stackDriverLogDataCollectionInfo.getAccountId()).isEqualTo(accountId);
    assertThat(stackDriverLogDataCollectionInfo.getApplicationId()).isEqualTo(appId);
    assertThat(stackDriverLogDataCollectionInfo.getQuery()).isEqualTo(analysisContext.getQuery());
    assertThat(stackDriverLogDataCollectionInfo.getGcpConfig()).isEqualTo(gcpConfig);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCreateDataCollectionInfo_sumoLogic() {
    when(settingsService.get(configId)).thenReturn(sumoSetting);
    doReturn(configId).when(sumoLogicAnalysisState).getResolvedConnectorId(any(), any(), any());
    doReturn("query").when(sumoLogicAnalysisState).getRenderedQuery();
    SumoDataCollectionInfo sumoDataCollectionInfo =
        (SumoDataCollectionInfo) sumoLogicAnalysisState.createDataCollectionInfo(analysisContext, null);

    assertThat(sumoDataCollectionInfo.getAccountId()).isEqualTo(accountId);
    assertThat(sumoDataCollectionInfo.getApplicationId()).isEqualTo(appId);
    assertThat(sumoDataCollectionInfo.getQuery()).isEqualTo(analysisContext.getQuery());
    assertThat(sumoDataCollectionInfo.getSumoConfig()).isEqualTo(sumoConfig);
  }
}
