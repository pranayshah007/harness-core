/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.resources;

import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.configmanager.resource.MergedPluginsConfigApiImpl;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.configmanager.service.PluginsProxyInfoService;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.*;

import java.util.Collections;
import java.util.List;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
@PrepareForTest(ConfigManagerService.class)
public class MergedPluginsConfigApiImplTest extends CategoryTest {
  AutoCloseable openMocks;

  ConfigManagerService configManagerService;
  @Mock IdpCommonService idpCommonService;
  @Mock NamespaceService namespaceService;
  @Mock BackstageEnvVariableService backstageEnvVariableService;
  @Mock PluginsProxyInfoService pluginsProxyInfoService;
  String backstageAppBaseUrl;
  String backstageBackendBaseUrl;
  String backstagePostgresHost;

  @InjectMocks MergedPluginsConfigApiImpl mergedPluginsConfigApiImpl;

  @Before
  public void setUp() {
    configManagerService = Mockito.mock(ConfigManagerService.class);
    mergedPluginsConfigApiImpl = new MergedPluginsConfigApiImpl(backstageAppBaseUrl, backstagePostgresHost,
        ServiceHttpClientConfig.builder().build(), namespaceService, idpCommonService, configManagerService,
        backstageEnvVariableService, pluginsProxyInfoService);
    openMocks = MockitoAnnotations.openMocks(this);
  }

  static final String TEST_ACCOUNT_IDENTIFIER = "test-account-id";

  static final String ERROR_TOGGLE_PLUGIN_FOR_ACCOUNT = "Error : Plugin toggle for account is unsuccessful";

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetMergedPluginsConfig() throws Exception {
    MergedPluginConfigs mergedPluginConfigs = new MergedPluginConfigs();
    when(configManagerService.mergeEnabledPluginConfigsForAccount(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(mergedPluginConfigs);
    Response response = mergedPluginsConfigApiImpl.getMergedPluginsConfig(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(mergedPluginConfigs, ((MergedPluginConfigResponse) response.getEntity()).getMergedConfig());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetMergedPluginsConfigError() throws Exception {
    when(configManagerService.mergeEnabledPluginConfigsForAccount(TEST_ACCOUNT_IDENTIFIER))
        .thenThrow(new InvalidRequestException(ERROR_TOGGLE_PLUGIN_FOR_ACCOUNT));
    Response response = mergedPluginsConfigApiImpl.getMergedPluginsConfig(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_TOGGLE_PLUGIN_FOR_ACCOUNT, ((ResponseMessage) response.getEntity()).getMessage());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testUpdateConfigurationEntities() throws Exception {
    ConfigurationEntities configurationEntities = new ConfigurationEntities();
    List<BackstageEnvVariable> backstageEnvVariables = Collections.singletonList(new BackstageEnvVariable());
    List<ProxyHostDetail> proxyHostDetails = Collections.singletonList(new ProxyHostDetail());
    configurationEntities.setEnvVariables(backstageEnvVariables);
    configurationEntities.setProxy(proxyHostDetails);
    when(backstageEnvVariableService.createOrUpdate(any(), any())).thenReturn(backstageEnvVariables);
    when(pluginsProxyInfoService.updateProxyHostDetailsForPlugin(any(), any())).thenReturn(proxyHostDetails);
    Response response = mergedPluginsConfigApiImpl.updateConfigurationEntities(
        getTestConfigurationEntityRequestBody(), TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testUpdateConfigurationEntitiesError() throws Exception {
    ConfigurationEntities configurationEntities = new ConfigurationEntities();
    List<BackstageEnvVariable> backstageEnvVariables = Collections.singletonList(new BackstageEnvVariable());
    List<ProxyHostDetail> proxyHostDetails = Collections.singletonList(new ProxyHostDetail());
    configurationEntities.setEnvVariables(backstageEnvVariables);
    configurationEntities.setProxy(proxyHostDetails);
    when(backstageEnvVariableService.createOrUpdate(any(), any())).thenThrow(new InvalidRequestException("Error"));
    when(pluginsProxyInfoService.updateProxyHostDetailsForPlugin(any(), any())).thenReturn(proxyHostDetails);
    Response response = mergedPluginsConfigApiImpl.updateConfigurationEntities(
        getTestConfigurationEntityRequestBody(), TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  private ConfigurationEntities getTestConfigurationEntityRequestBody() {
    ConfigurationEntities configurationEntities = new ConfigurationEntities();
    configurationEntities.setProxy(Collections.singletonList(new ProxyHostDetail()));
    configurationEntities.setEnvVariables(Collections.singletonList(new BackstageEnvVariable()));
    return configurationEntities;
  }
}
