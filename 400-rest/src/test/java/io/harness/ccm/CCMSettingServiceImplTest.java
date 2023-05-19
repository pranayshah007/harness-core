/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.HANTANG;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ccm.cluster.entities.Cluster;
import io.harness.ccm.cluster.entities.ClusterRecord;
import io.harness.ccm.cluster.entities.DirectKubernetesCluster;
import io.harness.ccm.config.CCMConfig;
import io.harness.ccm.config.CCMSettingServiceImpl;
import io.harness.ccm.config.CloudCostAware;
import io.harness.rule.Owner;

import software.wings.beans.Account;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.SettingsService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CE)
public class CCMSettingServiceImplTest extends CategoryTest {
  private String accountIdWithCCM;
  private String cloudProviderId = "CLOUD_PROVIDER_ID";
  private static final String COMPANY_NAME = "Harness";
  private static final String ACCOUNT_NAME = "Harness";
  private static final String masterUrl = "dummyMasterUrl";
  public static final String username = "dummyUsername";
  public static final String password = "dummyPassword";

  private SettingAttribute cloudProvider;

  private Cluster k8sCluster;
  private ClusterRecord clusterRecord;

  @Mock AccountService accountService;
  @Mock SettingsService settingsService;
  @InjectMocks CCMSettingServiceImpl ccmSettingService;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Before
  public void setUp() {
    Account accountWithCCM = getAccount(true, false);
    when(accountService.get(eq(accountIdWithCCM))).thenReturn(accountWithCCM);

    cloudProvider = getCloudProvider(true, false);
    when(settingsService.get(eq(cloudProviderId))).thenReturn(cloudProvider);

    k8sCluster = DirectKubernetesCluster.builder().cloudProviderId(cloudProviderId).build();
    clusterRecord = ClusterRecord.builder().accountId(accountIdWithCCM).cluster(k8sCluster).build();
  }

  private Account getAccount(boolean cloudCostEnabled, boolean ceK8sEventCollectionEnabled) {
    accountIdWithCCM = "ACCOUNT_WITH_CCM";
    return anAccount()
        .withCompanyName(COMPANY_NAME)
        .withAccountName(ACCOUNT_NAME)
        .withAccountKey("ACCOUNT_KEY")
        .withCloudCostEnabled(cloudCostEnabled)
        .withCeK8sEventCollectionEnabled(ceK8sEventCollectionEnabled)
        .build();
  }

  private SettingAttribute getCloudProvider(boolean cloudCostEnabled, boolean skipK8sEventCollection) {
    CCMConfig ccmConfig =
        CCMConfig.builder().cloudCostEnabled(cloudCostEnabled).skipK8sEventCollection(skipK8sEventCollection).build();
    KubernetesClusterConfig kubernetesClusterConfig = KubernetesClusterConfig.builder()
                                                          .masterUrl(masterUrl)
                                                          .username(username.toCharArray())
                                                          .password(password.toCharArray())
                                                          .accountId(accountIdWithCCM)
                                                          .ccmConfig(ccmConfig)
                                                          .build();
    cloudProvider = aSettingAttribute()
                        .withCategory(SettingCategory.CLOUD_PROVIDER)
                        .withAccountId(accountIdWithCCM)
                        .withName("KubernetesCluster-" + System.currentTimeMillis())
                        .withValue(kubernetesClusterConfig)
                        .build();
    return cloudProvider;
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testIsCloudCostEnabledForCloudProvider() {
    boolean result = ccmSettingService.isCloudCostEnabled(cloudProvider);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldNotCollectK8sEventForCloudProvider1() {
    Account accountWithCCM = getAccount(true, false);
    when(accountService.get(eq(accountIdWithCCM))).thenReturn(accountWithCCM);
    SettingAttribute cloudProvider = getCloudProvider(false, false);
    boolean result = ccmSettingService.isCeK8sEventCollectionEnabled(cloudProvider);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldNotCollectK8sEventForCloudProvider2() {
    Account accountWithCCM = getAccount(true, false);
    when(accountService.get(eq(accountIdWithCCM))).thenReturn(accountWithCCM);
    SettingAttribute cloudProvider = getCloudProvider(false, true);
    boolean result = ccmSettingService.isCeK8sEventCollectionEnabled(cloudProvider);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldCollectK8sEventForCloudProvider3() {
    Account accountWithCCM = getAccount(true, false);
    when(accountService.get(eq(accountIdWithCCM))).thenReturn(accountWithCCM);
    SettingAttribute cloudProvider = getCloudProvider(true, true);
    boolean result = ccmSettingService.isCeK8sEventCollectionEnabled(cloudProvider);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldCollectK8sEventForCloudProvider4() {
    Account accountWithCCM = getAccount(true, false);
    when(accountService.get(eq(accountIdWithCCM))).thenReturn(accountWithCCM);
    SettingAttribute cloudProvider = getCloudProvider(true, false);
    boolean result = ccmSettingService.isCeK8sEventCollectionEnabled(cloudProvider);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldCollectK8sEventForCloudProvider5() {
    Account accountWithCCM = getAccount(true, true);
    when(accountService.get(eq(accountIdWithCCM))).thenReturn(accountWithCCM);
    SettingAttribute cloudProvider = getCloudProvider(false, false);
    boolean result = ccmSettingService.isCeK8sEventCollectionEnabled(cloudProvider);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldNotCollectK8sEventForCloudProvider6() {
    Account accountWithCCM = getAccount(true, true);
    when(accountService.get(eq(accountIdWithCCM))).thenReturn(accountWithCCM);
    SettingAttribute cloudProvider = getCloudProvider(false, true);
    boolean result = ccmSettingService.isCeK8sEventCollectionEnabled(cloudProvider);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldCollectK8sEventForCloudProvider7() {
    Account accountWithCCM = getAccount(true, true);
    when(accountService.get(eq(accountIdWithCCM))).thenReturn(accountWithCCM);
    SettingAttribute cloudProvider = getCloudProvider(true, false);
    boolean result = ccmSettingService.isCeK8sEventCollectionEnabled(cloudProvider);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldNotCollectK8sEventForCloudProvider8() {
    Account accountWithCCM = getAccount(true, true);
    when(accountService.get(eq(accountIdWithCCM))).thenReturn(accountWithCCM);
    SettingAttribute cloudProvider = getCloudProvider(true, true);
    boolean result = ccmSettingService.isCeK8sEventCollectionEnabled(cloudProvider);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testIsCloudCostEnabledForCluster() {
    boolean result = ccmSettingService.isCloudCostEnabled(clusterRecord);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldNotMaskCCMConfig() {
    ccmSettingService.maskCCMConfig(cloudProvider);
    assertThat(((CloudCostAware) cloudProvider.getValue()).cloudCostEnabled()).isNotNull();
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldMaskCCMConfig() {
    String accountIdNoCCM = "ACCOUNT_NO_CCM";
    Account accountNoCCM = anAccount()
                               .withCompanyName(COMPANY_NAME)
                               .withAccountName(ACCOUNT_NAME)
                               .withAccountKey("ACCOUNT_KEY")
                               .withCloudCostEnabled(false)
                               .build();
    when(accountService.get(accountIdNoCCM)).thenReturn(accountNoCCM);

    String kubernetesClusterConfigName = "KubernetesCluster-" + System.currentTimeMillis();
    CCMConfig ccmConfig = CCMConfig.builder().cloudCostEnabled(true).build();
    KubernetesClusterConfig kubernetesClusterConfig = KubernetesClusterConfig.builder()
                                                          .masterUrl(masterUrl)
                                                          .username(username.toCharArray())
                                                          .password(password.toCharArray())
                                                          .accountId(accountIdNoCCM)
                                                          .ccmConfig(ccmConfig)
                                                          .build();
    SettingAttribute settingAttribute = aSettingAttribute()
                                            .withCategory(SettingCategory.CLOUD_PROVIDER)
                                            .withName(kubernetesClusterConfigName)
                                            .withAccountId(accountIdNoCCM)
                                            .withValue(kubernetesClusterConfig)
                                            .build();
    ccmSettingService.maskCCMConfig(settingAttribute);
    assertThat(settingAttribute.getValue() instanceof KubernetesClusterConfig).isTrue();
    assertThat(((KubernetesClusterConfig) settingAttribute.getValue()).getCcmConfig()).isNull();
  }
}
