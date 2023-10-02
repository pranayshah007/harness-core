/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.service;

import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.mockito.Mockito.*;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.delegateselectors.cache.DelegateSelectorsCache;
import io.harness.idp.configmanager.beans.entity.PluginsProxyInfoEntity;
import io.harness.idp.configmanager.repositories.PluginsProxyInfoRepository;
import io.harness.idp.configmanager.utils.ConfigType;
import io.harness.idp.proxy.envvariable.ProxyEnvVariableServiceWrapper;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.ProxyHostDetail;

import java.util.Collections;
import java.util.List;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.IDP)
public class PluginProxyInfoServiceImplTest extends CategoryTest {
  AutoCloseable openMocks;
  @Mock PluginsProxyInfoRepository pluginsProxyInfoRepository;
  @Mock DelegateSelectorsCache delegateSelectorsCache;
  @Mock ProxyEnvVariableServiceWrapper proxyEnvVariableServiceWrapper;

  @Spy @InjectMocks PluginsProxyInfoServiceImpl pluginsProxyInfoServiceImpl;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  static final String TEST_ID = "test_id";
  static final String TEST_ACCOUNT_IDENTIFIER = "test_account_identifier";
  static final String TEST_PLUGIN_ID = "test_plugin_id";
  static final long TEST_CREATED_AT_TIME = 1681756034;
  static final long TEST_LAST_MODIFIED_AT_TIME = 1681756035;
  static final String TEST_HOST_VALUE_OLD = "test_host_value_old";
  static final String TEST_HOST_VALUE = "test_host_value";
  static final Boolean TEST_PROXY_VALUE = true;
  static final String TEST_DELEGATE_SELECTOR_NAME = "test_delegate_selector";
  static final List<String> TEST_DELEGATE_SELECTOR_LIST = Collections.singletonList(TEST_DELEGATE_SELECTOR_NAME);

  static final String TEST_APP_CONFIG_VALUE = "test_config_value";

  static final String TEST_PLUGIN_NAME = "test_config_name";

  static final Boolean TEST_APP_CONFIG_ENABLED_VALUE = true;
  static final String TEST_ERROR_MESSAGE =
      "Host - test_host_value is already used in plugin - NEW_PLUGIN_ID, please configure it from configurations page.";

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testInsertProxyHostDetailsForPlugin() {
    PluginsProxyInfoEntity pluginsProxyInfoEntity = getTestPluginProxyInfoEntity();
    AppConfig appConfig = getTestAppConfig();
    when(pluginsProxyInfoServiceImpl.getPluginProxyInfoEntities(appConfig, TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Collections.emptyList());
    when(pluginsProxyInfoRepository.findAllByAccountIdentifierAndPluginId(any(), any()))
        .thenReturn(Collections.singletonList(getTestPluginProxyInfoEntity()));
    doNothing().when(delegateSelectorsCache).remove(any(), any());
    doNothing().when(proxyEnvVariableServiceWrapper).removeFromHostProxyEnvVariable(any(), any());
    doNothing().when(pluginsProxyInfoRepository).deleteAllByAccountIdentifierAndPluginId(any(), any());
    when(proxyEnvVariableServiceWrapper.getHostProxyMap(TEST_ACCOUNT_IDENTIFIER)).thenReturn(new JSONObject());
    assertEquals(pluginsProxyInfoServiceImpl
                     .insertProxyHostDetailsForPlugin(appConfig, TEST_ACCOUNT_IDENTIFIER, ConfigType.PLUGIN)
                     .size(),
        0);

    when(pluginsProxyInfoServiceImpl.getPluginProxyInfoEntities(appConfig, TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Collections.singletonList(pluginsProxyInfoEntity));
    when(pluginsProxyInfoServiceImpl.getErrorMessageIfHostIsAlreadyInUse(TEST_ACCOUNT_IDENTIFIER, appConfig))
        .thenReturn(Collections.singletonList(TEST_ERROR_MESSAGE));
    Exception exception = null;
    try {
      pluginsProxyInfoServiceImpl.insertProxyHostDetailsForPlugin(
          appConfig, TEST_ACCOUNT_IDENTIFIER, ConfigType.PLUGIN);
    } catch (InvalidRequestException e) {
      exception = e;
    }
    assertNotNull(exception);

    when(pluginsProxyInfoServiceImpl.getPluginProxyInfoEntities(appConfig, TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Collections.singletonList(pluginsProxyInfoEntity));
    when(pluginsProxyInfoServiceImpl.getErrorMessageIfHostIsAlreadyInUse(TEST_ACCOUNT_IDENTIFIER, appConfig))
        .thenReturn(Collections.emptyList());
    when(pluginsProxyInfoRepository.saveAll(Collections.singletonList(pluginsProxyInfoEntity)))
        .thenReturn(Collections.singletonList(pluginsProxyInfoEntity));
    List<ProxyHostDetail> savedProxyDetails = pluginsProxyInfoServiceImpl.insertProxyHostDetailsForPlugin(
        appConfig, TEST_ACCOUNT_IDENTIFIER, ConfigType.PLUGIN);
    assertEquals(TEST_HOST_VALUE, savedProxyDetails.get(0).getHost());
    assertEquals(TEST_DELEGATE_SELECTOR_NAME, savedProxyDetails.get(0).getSelectors().get(0));
    assertEquals(TEST_PROXY_VALUE, savedProxyDetails.get(0).isProxy());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testUpdateProxyHostDetailsForPlugin() {
    PluginsProxyInfoEntity pluginsProxyInfoEntity = getTestPluginProxyInfoEntity();
    AppConfig appConfig = getTestAppConfig();
    when(pluginsProxyInfoRepository.findAllByAccountIdentifierAndPluginId(any(), any()))
        .thenReturn(Collections.singletonList(getTestPluginProxyInfoEntity()));
    doNothing().when(delegateSelectorsCache).remove(any(), any());
    doNothing().when(proxyEnvVariableServiceWrapper).removeFromHostProxyEnvVariable(any(), any());
    doNothing().when(pluginsProxyInfoRepository).deleteAllByAccountIdentifierAndPluginId(any(), any());
    JSONObject hostProxyMap = new JSONObject();
    hostProxyMap.put(TEST_HOST_VALUE_OLD, false);
    when(proxyEnvVariableServiceWrapper.getHostProxyMap(TEST_ACCOUNT_IDENTIFIER)).thenReturn(hostProxyMap);
    when(pluginsProxyInfoServiceImpl.getPluginProxyInfoEntities(appConfig, TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Collections.emptyList());

    pluginsProxyInfoServiceImpl.updateProxyHostDetailsForPlugin(appConfig, TEST_ACCOUNT_IDENTIFIER, ConfigType.PLUGIN);

    when(pluginsProxyInfoServiceImpl.getPluginProxyInfoEntities(appConfig, TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Collections.singletonList(pluginsProxyInfoEntity));
    when(pluginsProxyInfoServiceImpl.getErrorMessageIfHostIsAlreadyInUse(TEST_ACCOUNT_IDENTIFIER, appConfig))
        .thenReturn(Collections.singletonList(TEST_ERROR_MESSAGE));
    Exception exception = null;
    try {
      pluginsProxyInfoServiceImpl.updateProxyHostDetailsForPlugin(
          appConfig, TEST_ACCOUNT_IDENTIFIER, ConfigType.PLUGIN);
    } catch (InvalidRequestException e) {
      exception = e;
    }
    assertNotNull(exception);

    when(pluginsProxyInfoServiceImpl.getPluginProxyInfoEntities(appConfig, TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Collections.singletonList(pluginsProxyInfoEntity));
    when(pluginsProxyInfoServiceImpl.getErrorMessageIfHostIsAlreadyInUse(TEST_ACCOUNT_IDENTIFIER, appConfig))
        .thenReturn(Collections.emptyList());
    when(pluginsProxyInfoRepository.saveAll(Collections.singletonList(pluginsProxyInfoEntity)))
        .thenReturn(Collections.singletonList(pluginsProxyInfoEntity));

    List<ProxyHostDetail> updatedProxyDetails = pluginsProxyInfoServiceImpl.updateProxyHostDetailsForPlugin(
        appConfig, TEST_ACCOUNT_IDENTIFIER, ConfigType.PLUGIN);

    assertEquals(TEST_HOST_VALUE, updatedProxyDetails.get(0).getHost());
    assertEquals(TEST_DELEGATE_SELECTOR_NAME, updatedProxyDetails.get(0).getSelectors().get(0));
    assertEquals(TEST_PROXY_VALUE, updatedProxyDetails.get(0).isProxy());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetProxyHostDetailsForMultiplePluginIds() throws Exception {
    PluginsProxyInfoEntity pluginsProxyInfoEntity = getTestPluginProxyInfoEntity();
    when(pluginsProxyInfoRepository.findAllByAccountIdentifierAndPluginIds(
             TEST_ACCOUNT_IDENTIFIER, Collections.singletonList(TEST_PLUGIN_ID)))
        .thenReturn(Collections.singletonList(pluginsProxyInfoEntity));
    List<ProxyHostDetail> proxyHostDetailList = pluginsProxyInfoServiceImpl.getProxyHostDetailsForMultiplePluginIds(
        TEST_ACCOUNT_IDENTIFIER, Collections.singletonList(TEST_PLUGIN_ID));
    assertEquals(TEST_HOST_VALUE, proxyHostDetailList.get(0).getHost());
    assertEquals(TEST_PROXY_VALUE, proxyHostDetailList.get(0).isProxy());
    assertEquals(TEST_DELEGATE_SELECTOR_NAME, proxyHostDetailList.get(0).getSelectors().get(0));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testUpdateProxyHostDetailsForHostValues() {
    PluginsProxyInfoEntity pluginsProxyInfoEntity = getTestPluginProxyInfoEntity();
    ProxyHostDetail proxyHostDetail = new ProxyHostDetail();
    proxyHostDetail.setHost(TEST_HOST_VALUE);
    proxyHostDetail.setProxy(TEST_PROXY_VALUE);
    proxyHostDetail.setSelectors(Collections.singletonList(TEST_DELEGATE_SELECTOR_NAME));
    when(pluginsProxyInfoRepository.updatePluginProxyInfo(proxyHostDetail, TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(pluginsProxyInfoEntity);
    List<ProxyHostDetail> proxyHostDetailList = pluginsProxyInfoServiceImpl.updateProxyHostDetailsForHostValues(
        Collections.singletonList(proxyHostDetail), TEST_ACCOUNT_IDENTIFIER);
    assertEquals(TEST_HOST_VALUE, proxyHostDetailList.get(0).getHost());
    assertEquals(TEST_PROXY_VALUE, proxyHostDetailList.get(0).isProxy());
    assertEquals(TEST_DELEGATE_SELECTOR_NAME, proxyHostDetailList.get(0).getSelectors().get(0));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetProxyHostDetailsForMultiplePluginId() {
    PluginsProxyInfoEntity pluginsProxyInfoEntity = getTestPluginProxyInfoEntity();
    when(pluginsProxyInfoRepository.findAllByAccountIdentifierAndPluginId(TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_ID))
        .thenReturn(Collections.singletonList(pluginsProxyInfoEntity));
    List<ProxyHostDetail> proxyHostDetailList =
        pluginsProxyInfoServiceImpl.getProxyHostDetailsForPluginId(TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_ID);
    assertEquals(TEST_HOST_VALUE, proxyHostDetailList.get(0).getHost());
    assertEquals(TEST_PROXY_VALUE, proxyHostDetailList.get(0).isProxy());
    assertEquals(TEST_DELEGATE_SELECTOR_NAME, proxyHostDetailList.get(0).getSelectors().get(0));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testErrorMessageIfHostIsAlreadyInUse() {
    AppConfig appConfig = getTestAppConfig();
    when(pluginsProxyInfoRepository.findByAccountIdentifierAndHost(TEST_ACCOUNT_IDENTIFIER, TEST_HOST_VALUE))
        .thenReturn(null);
    List<String> errorMessages =
        pluginsProxyInfoServiceImpl.getErrorMessageIfHostIsAlreadyInUse(TEST_ACCOUNT_IDENTIFIER, appConfig);
    assertEquals(0, errorMessages.size());

    PluginsProxyInfoEntity pluginsProxyInfoEntity = getTestPluginProxyInfoEntity();
    when(pluginsProxyInfoRepository.findByAccountIdentifierAndHost(TEST_ACCOUNT_IDENTIFIER, TEST_HOST_VALUE))
        .thenReturn(pluginsProxyInfoEntity);
    errorMessages = pluginsProxyInfoServiceImpl.getErrorMessageIfHostIsAlreadyInUse(TEST_ACCOUNT_IDENTIFIER, appConfig);
    assertEquals(0, errorMessages.size());

    pluginsProxyInfoEntity.setPluginId("NEW_PLUGIN_ID");
    errorMessages = pluginsProxyInfoServiceImpl.getErrorMessageIfHostIsAlreadyInUse(TEST_ACCOUNT_IDENTIFIER, appConfig);
    assertEquals(TEST_ERROR_MESSAGE, errorMessages.get(0));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetPluginProxyInfoEntities() {
    AppConfig appConfig = getTestAppConfig();
    List<PluginsProxyInfoEntity> pluginsProxyInfoEntityList =
        pluginsProxyInfoServiceImpl.getPluginProxyInfoEntities(appConfig, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(TEST_HOST_VALUE, pluginsProxyInfoEntityList.get(0).getHost());
    assertEquals(TEST_PROXY_VALUE, pluginsProxyInfoEntityList.get(0).getProxy());
    assertEquals(TEST_DELEGATE_SELECTOR_NAME, pluginsProxyInfoEntityList.get(0).getDelegateSelectors().get(0));

    // test if proxy details are not present
    appConfig.setProxy(null);
    assertEquals(0, pluginsProxyInfoServiceImpl.getPluginProxyInfoEntities(appConfig, TEST_ACCOUNT_IDENTIFIER).size());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteProxyHostDetailsForPlugin() {
    when(pluginsProxyInfoRepository.findAllByAccountIdentifierAndPluginId(any(), any()))
        .thenReturn(Collections.singletonList(getTestPluginProxyInfoEntity()));

    pluginsProxyInfoServiceImpl.deleteProxyHostDetailsForPlugin(TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_ID);

    verify(delegateSelectorsCache).remove(TEST_ACCOUNT_IDENTIFIER, Collections.singleton(TEST_HOST_VALUE));
    verify(proxyEnvVariableServiceWrapper)
        .removeFromHostProxyEnvVariable(TEST_ACCOUNT_IDENTIFIER, Collections.singleton(TEST_HOST_VALUE));
    verify(pluginsProxyInfoRepository).deleteAllByAccountIdentifierAndPluginId(TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_ID);
  }

  private PluginsProxyInfoEntity getTestPluginProxyInfoEntity() {
    return PluginsProxyInfoEntity.builder()
        .id(TEST_ID)
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .pluginId(TEST_PLUGIN_ID)
        .createdAt(TEST_CREATED_AT_TIME)
        .lastModifiedAt(TEST_LAST_MODIFIED_AT_TIME)
        .host(TEST_HOST_VALUE)
        .proxy(TEST_PROXY_VALUE)
        .delegateSelectors(TEST_DELEGATE_SELECTOR_LIST)
        .build();
  }

  private AppConfig getTestAppConfig() {
    AppConfig appConfig = new AppConfig();
    ProxyHostDetail proxyHostDetail = new ProxyHostDetail();
    proxyHostDetail.setSelectors(TEST_DELEGATE_SELECTOR_LIST);
    proxyHostDetail.setProxy(TEST_PROXY_VALUE);
    proxyHostDetail.setHost(TEST_HOST_VALUE);
    appConfig.setProxy(Collections.singletonList(proxyHostDetail));
    appConfig.setConfigId(TEST_PLUGIN_ID);
    appConfig.setConfigName(TEST_PLUGIN_NAME);
    appConfig.setConfigs(TEST_APP_CONFIG_VALUE);
    appConfig.setEnabled(TEST_APP_CONFIG_ENABLED_VALUE);
    return appConfig;
  }
}
