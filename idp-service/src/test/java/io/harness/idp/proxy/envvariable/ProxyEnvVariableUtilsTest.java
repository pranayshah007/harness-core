/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.envvariable;

import static io.harness.idp.common.Constants.PROXY_ENV_NAME;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.BackstageEnvConfigVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ProxyEnvVariableUtilsTest extends CategoryTest {
  private static final String PROXY_MAP1 = "{\"github.com\":true,\"gitlab.com\":false}";
  private static final String PROXY_MAP2 = "{\"github.com\":false,\"gitlab.com\":false}";
  private static final String PROXY_MAP3 = "{\"gitlab.com\":false}";
  private static final String ACCOUNT_IDENTIFIER = "test-account";
  private static final String GITHUB_HOST = "github.com";
  private static final String GITLAB_HOST = "gitlab.com";
  private AutoCloseable openMocks;
  @Mock private BackstageEnvVariableService backstageEnvVariableService;
  @InjectMocks private ProxyEnvVariableUtils proxyEnvVariableUtils;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testCreateOrUpdateHostProxyEnvVariable() {
    BackstageEnvConfigVariable variable = new BackstageEnvConfigVariable();
    variable.type(BackstageEnvVariable.TypeEnum.CONFIG);
    variable.envName(PROXY_ENV_NAME);
    variable.value(PROXY_MAP1);
    when(backstageEnvVariableService.findByEnvNameAndAccountIdentifier(PROXY_ENV_NAME, ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(variable));
    Map<String, Boolean> hostProxyMap = new HashMap<>();
    hostProxyMap.put(GITHUB_HOST, false);
    proxyEnvVariableUtils.createOrUpdateHostProxyEnvVariable(ACCOUNT_IDENTIFIER, hostProxyMap);
    variable.setValue(PROXY_MAP2);
    verify(backstageEnvVariableService).update(variable, ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testCreateOrUpdateHostProxyEnvVariableWhenNotPresent() {
    when(backstageEnvVariableService.findByEnvNameAndAccountIdentifier(PROXY_ENV_NAME, ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.empty());
    Map<String, Boolean> hostProxyMap = new HashMap<>();
    hostProxyMap.put(GITHUB_HOST, true);
    hostProxyMap.put(GITLAB_HOST, false);
    proxyEnvVariableUtils.createOrUpdateHostProxyEnvVariable(ACCOUNT_IDENTIFIER, hostProxyMap);
    BackstageEnvConfigVariable variable = new BackstageEnvConfigVariable();
    variable.type(BackstageEnvVariable.TypeEnum.CONFIG);
    variable.envName(PROXY_ENV_NAME);
    variable.setValue(PROXY_MAP1);
    verify(backstageEnvVariableService).create(variable, ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testRemoveFromHostProxyEnvVariable() {
    BackstageEnvConfigVariable variable = new BackstageEnvConfigVariable();
    variable.type(BackstageEnvVariable.TypeEnum.CONFIG);
    variable.envName(PROXY_ENV_NAME);
    variable.value(PROXY_MAP1);
    when(backstageEnvVariableService.findByEnvNameAndAccountIdentifier(PROXY_ENV_NAME, ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(variable));
    Set<String> hostsToBeRemoved = new HashSet<>();
    hostsToBeRemoved.add(GITHUB_HOST);
    proxyEnvVariableUtils.removeFromHostProxyEnvVariable(ACCOUNT_IDENTIFIER, hostsToBeRemoved);
    variable.type(BackstageEnvVariable.TypeEnum.CONFIG);
    variable.envName(PROXY_ENV_NAME);
    variable.setValue(PROXY_MAP3);
    verify(backstageEnvVariableService).update(variable, ACCOUNT_IDENTIFIER);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
