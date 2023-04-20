/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.BugsnagConfig;
import software.wings.beans.SettingAttribute;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.service.impl.bugsnag.BugsnagApplication;
import software.wings.service.impl.bugsnag.BugsnagDelegateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.analysis.LogVerificationServiceImpl;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.StateType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class LogVerificationServiceImplTest extends CategoryTest {
  @Mock private SettingsService mockSettingsService;
  @Mock private DelegateProxyFactory mockDelegateProxyFactory;
  @Mock private BugsnagDelegateService mockBugsnagDelegateService;
  @Mock private SecretManager mockSecretManager;
  @InjectMocks LogVerificationServiceImpl service;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetBugsnagOrgs() {
    BugsnagConfig config = new BugsnagConfig();
    config.setUrl("testBugsnagURL");
    config.setAccountId("myaccountId");
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(config);

    BugsnagApplication application = BugsnagApplication.builder().id("orgId").name("orgName").build();

    // setup
    when(mockSettingsService.get(anyString())).thenReturn(attribute);
    when(mockDelegateProxyFactory.getV2(any(), any())).thenReturn(mockBugsnagDelegateService);
    when(mockSecretManager.getEncryptionDetails(any(EncryptableSetting.class), anyString(), anyString()))
        .thenReturn(null);
    when(mockBugsnagDelegateService.getOrganizations(config, new ArrayList<>(), null))
        .thenReturn(new TreeSet<>(Arrays.asList(application)));

    // execute
    Set<BugsnagApplication> appList = service.getOrgProjectListBugsnag("setting", "", StateType.BUG_SNAG, false);

    assertThat(appList).hasSize(1);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetBugsnagApplications() {
    BugsnagConfig config = new BugsnagConfig();
    config.setUrl("testBugsnagURL");
    config.setAccountId("myaccountId");
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(config);

    BugsnagApplication application = BugsnagApplication.builder().id("orgId").name("orgName").build();

    // setup
    when(mockSettingsService.get(anyString())).thenReturn(attribute);
    when(mockDelegateProxyFactory.getV2(any(), any())).thenReturn(mockBugsnagDelegateService);
    when(mockSecretManager.getEncryptionDetails(any(EncryptableSetting.class), anyString(), anyString()))
        .thenReturn(null);
    when(mockBugsnagDelegateService.getProjects(config, "orgId", new ArrayList<>(), null))
        .thenReturn(new TreeSet<>(Arrays.asList(application)));

    // execute
    Set<BugsnagApplication> appList = service.getOrgProjectListBugsnag("setting", "orgId", StateType.BUG_SNAG, true);

    assertThat(appList).hasSize(1);
  }

  @Test(expected = WingsException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetBugsnagApplicationsBadState() {
    BugsnagConfig config = new BugsnagConfig();
    config.setUrl("testBugsnagURL");
    config.setAccountId("myaccountId");
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(config);

    BugsnagApplication application = BugsnagApplication.builder().id("orgId").name("orgName").build();

    // setup
    when(mockSettingsService.get(anyString())).thenReturn(attribute);
    when(mockDelegateProxyFactory.getV2(any(), any())).thenReturn(mockBugsnagDelegateService);
    when(mockSecretManager.getEncryptionDetails(any(EncryptableSetting.class), anyString(), anyString()))
        .thenReturn(null);
    when(mockBugsnagDelegateService.getOrganizations(config, null, null))
        .thenReturn(new TreeSet<>(Arrays.asList(application)));

    // execute
    Set<BugsnagApplication> appList = service.getOrgProjectListBugsnag("setting", "", StateType.NEW_RELIC, false);

    assertThat(appList).hasSize(1);
  }
}
