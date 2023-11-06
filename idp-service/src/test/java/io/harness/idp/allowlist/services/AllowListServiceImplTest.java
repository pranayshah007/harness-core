/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.allowlist.services;

import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.YamlUtils;
import io.harness.idp.configmanager.beans.entity.MergedAppConfigEntity;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.configmanager.utils.ConfigType;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.HostInfo;

import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.IDP)
public class AllowListServiceImplTest {
  @InjectMocks private AllowListServiceImpl allowListServiceImpl;
  @Mock private ConfigManagerService configManagerService;
  private static final String ACCOUNT_ID = "123";
  @Captor private ArgumentCaptor<AppConfig> appConfigCaptor;

  @Mock TransactionTemplate transactionTemplate;

  @Mock OutboxService outboxService;
  private static final String schema =
      "{\"definitions\": {}, \"$schema\": \"http://json-schema.org/draft-07/schema#\", "
      + "\"$id\": \"https://example.com/object1686050391.json\", \"title\": \"Root\", \"type\": \"object\", \"required\": [\"backend\"], "
      + "\"properties\": {\"backend\": {\"$id\": \"#root/backend\", \"title\": \"Backend\", \"type\": \"object\",\"required\": [\"reading\"],"
      + "\"properties\": {\"reading\": {\"$id\": \"#root/backend/reading\", \"title\": \"Reading\", \"type\": \"object\",\"additionalProperties\": false,\"required\": [\"allow\"],"
      + "\"properties\": {\"allow\": {\"$id\": \"#root/backend/reading/allow\", \"title\": \"Allow\", \"type\": \"array\",\"default\": []}}}}}}}\n";
  AutoCloseable openMocks;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetAllowList() throws Exception {
    String config = "backend:\n  reading:\n    allow:\n      - host: app.harness.io";
    AppConfig appConfig = new AppConfig();
    appConfig.setConfigs(config);
    when(configManagerService.getAppConfig(ACCOUNT_ID, "allow-list", ConfigType.BACKEND)).thenReturn(appConfig);
    List<HostInfo> hostInfoList = allowListServiceImpl.getAllowList(ACCOUNT_ID);
    assertEquals(hostInfoList.size(), 1);

    when(configManagerService.getAppConfig(ACCOUNT_ID, "allow-list", ConfigType.BACKEND)).thenReturn(null);
    hostInfoList = allowListServiceImpl.getAllowList(ACCOUNT_ID);
    assertEquals(hostInfoList.size(), 0);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveAllowList() throws Exception {
    List<HostInfo> hostInfoList = new ArrayList<>();
    HostInfo hostInfo1 = new HostInfo();
    hostInfo1.setHost("stress.harness.io");
    hostInfo1.setPaths(new ArrayList<>());
    hostInfoList.add(hostInfo1);
    HostInfo hostInfo2 = new HostInfo();
    hostInfo2.setHost("qa.harness.io");
    hostInfo2.setPaths(List.of("/v1/secrets"));
    hostInfoList.add(hostInfo2);
    MockedStatic<YamlUtils> yamlUtilsMockedStatic = Mockito.mockStatic(YamlUtils.class);
    MockedStatic<CommonUtils> commonUtilsMockedStatic = Mockito.mockStatic(CommonUtils.class);

    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));

    String yamlString = "backend:\n  reading:\n    allow: []";
    String allowListString = "- host: stress.harness.io\n  paths: []\n- host: qa.harness.io\n  paths:\n  - /v1/secrets";
    when(YamlUtils.writeObjectAsYaml(any())).thenReturn(allowListString);
    when(CommonUtils.readFileFromClassPath(any())).thenReturn(yamlString).thenReturn(schema);

    when(configManagerService.saveUpdateAndMergeConfigForAccount(any(), any(), any())).thenReturn(new AppConfig());
    when(configManagerService.getAppConfig(any(), any(), any())).thenReturn(new AppConfig());
    allowListServiceImpl.saveAllowList(hostInfoList, ACCOUNT_ID);

    String expectedConfig =
        "---\nbackend:\n  reading:\n    allow:\n    - host: stress.harness.io\n    - host: qa.harness.io\n      paths:\n      - /v1/secrets\n";
    verify(configManagerService).saveUpdateAndMergeConfigForAccount(appConfigCaptor.capture(), any(), any());
    assertEquals(expectedConfig, appConfigCaptor.getValue().getConfigs());
    yamlUtilsMockedStatic.close();
    commonUtilsMockedStatic.close();
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
