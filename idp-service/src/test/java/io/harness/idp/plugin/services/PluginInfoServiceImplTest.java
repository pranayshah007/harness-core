/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.plugin.services;

import static io.harness.rule.OwnerRule.VIGNESWARA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.FileUtils;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.plugin.beans.ExportsData;
import io.harness.idp.plugin.beans.PluginInfoEntity;
import io.harness.idp.plugin.enums.ExportType;
import io.harness.idp.plugin.repositories.PluginInfoRepository;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.PluginDetailedInfo;
import io.harness.spec.server.idp.v1.model.PluginInfo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.*;

@OwnedBy(HarnessTeam.IDP)
public class PluginInfoServiceImplTest {
  @InjectMocks private PluginInfoServiceImpl pluginInfoServiceImpl;
  @Mock private PluginInfoRepository pluginInfoRepository;
  @Mock private ConfigManagerService configManagerService;
  private final ObjectMapper objectMapper = mock(ObjectMapper.class);

  private static final String ACCOUNT_ID = "123";
  private static final String PAGER_DUTY_NAME = "PagerDuty";
  private static final String PAGER_DUTY_ID = "pager-duty";
  private static final String HARNESS_CI_CD_NAME = "Harnes CI/CD";
  private static final String HARNESS_CI_CD_ID = "harness-ci-cd";
  private static final String INVALID_PLUGIN_ID = "invalid-plugin";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetAllPluginsInfo() {
    List<PluginInfoEntity> pluginInfoEntityList = new ArrayList<>();
    pluginInfoEntityList.add(getPagerDutyInfoEntity());
    pluginInfoEntityList.add(getHarnessCICDInfoEntity());
    when(pluginInfoRepository.findAll()).thenReturn(pluginInfoEntityList);
    Map<String, Boolean> map = new HashMap<>();
    map.put(PAGER_DUTY_ID, false);
    map.put(HARNESS_CI_CD_ID, true);
    when(configManagerService.getAllPluginIdsMap(ACCOUNT_ID)).thenReturn(map);
    List<PluginInfo> pluginDTOs = pluginInfoServiceImpl.getAllPluginsInfo(ACCOUNT_ID);
    assertEquals(2, pluginDTOs.size());
    assertFalse(pluginDTOs.get(0).isEnabled());
    assertTrue(pluginDTOs.get(1).isEnabled());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetPluginDetailedInfo() {
    when(pluginInfoRepository.findByIdentifier(PAGER_DUTY_ID))
        .thenReturn(Optional.ofNullable(getPagerDutyInfoEntity()));
    when(configManagerService.getPluginConfig(ACCOUNT_ID, PAGER_DUTY_ID)).thenReturn(null);
    PluginDetailedInfo pluginDetailedInfo = pluginInfoServiceImpl.getPluginDetailedInfo(PAGER_DUTY_ID, ACCOUNT_ID);
    assertNotNull(pluginDetailedInfo);
    assertFalse(pluginDetailedInfo.getPluginDetails().isEnabled());
    assertEquals(1, (int) pluginDetailedInfo.getExports().getCards());
    assertEquals(0, (int) pluginDetailedInfo.getExports().getPages());
    assertEquals(0, (int) pluginDetailedInfo.getExports().getTabContents());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetPluginDetailedInfoThrowsException() {
    when(pluginInfoRepository.findByIdentifier(INVALID_PLUGIN_ID)).thenReturn(Optional.empty());
    pluginInfoServiceImpl.getPluginDetailedInfo(INVALID_PLUGIN_ID, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveAllPluginInfo() {
    String schema = "identifier: github-pull-requests-board\n"
        + "name: GitHub Pull Requests Board\n"
        + "description: View all open GitHub pull requests owned by your team in Backstage.\n"
        + "createdBy: DAZN\n"
        + "category: Source Control Mgmt\n"
        + "source: https://github.com/backstage/backstage/tree/master/plugins/github-pull-requests-board";
    PluginInfoEntity pluginInfoEntity = PluginInfoEntity.builder().build();
    Mockito.mockStatic(FileUtils.class);
    when(FileUtils.readFile(any(), any(), any())).thenReturn(schema);
    when(pluginInfoRepository.saveOrUpdate(any(PluginInfoEntity.class))).thenReturn(pluginInfoEntity);
    pluginInfoServiceImpl.saveAllPluginInfo();
    verify(pluginInfoRepository, times(7)).saveOrUpdate(any(PluginInfoEntity.class));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteAllPluginInfo() {
    doNothing().when(pluginInfoRepository).deleteAll();
    pluginInfoServiceImpl.deleteAllPluginInfo();
    verify(pluginInfoRepository).deleteAll();
  }

  private PluginInfoEntity getPagerDutyInfoEntity() {
    List<ExportsData.ExportDetails> exportDetails = new ArrayList<>();
    ExportsData.ExportDetails export = new ExportsData.ExportDetails();
    export.setType(ExportType.CARD);
    export.setName("EntityPagerDutyCard");
    exportDetails.add(export);
    ExportsData exportsData = new ExportsData();
    exportsData.setExportDetails(exportDetails);
    return PluginInfoEntity.builder()
        .name(PAGER_DUTY_NAME)
        .identifier(PAGER_DUTY_ID)
        .exports(exportsData)
        .core(false)
        .build();
  }

  private PluginInfoEntity getHarnessCICDInfoEntity() {
    return PluginInfoEntity.builder()
        .name(HARNESS_CI_CD_NAME)
        .identifier(HARNESS_CI_CD_ID)
        .exports(new ExportsData())
        .core(true)
        .build();
  }
}
