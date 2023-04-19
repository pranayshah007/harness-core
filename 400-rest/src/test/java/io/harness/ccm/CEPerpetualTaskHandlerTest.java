/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.cluster.entities.ClusterType.DIRECT_KUBERNETES;
import static io.harness.rule.OwnerRule.HANTANG;
import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static software.wings.beans.Account.Builder.anAccount;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ccm.cluster.ClusterRecordService;
import io.harness.ccm.cluster.entities.Cluster;
import io.harness.ccm.cluster.entities.ClusterRecord;
import io.harness.ccm.cluster.entities.DirectKubernetesCluster;
import io.harness.ccm.cluster.entities.EcsCluster;
import io.harness.ccm.config.CCMSettingService;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.beans.Account;
import software.wings.features.CeClusterFeature;
import software.wings.features.api.UsageLimitedFeature;

import com.google.inject.name.Named;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CE)
public class CEPerpetualTaskHandlerTest extends CategoryTest {
  private String accountId = "ACCOUNT_ID";

  private String cloudProviderId = "CLOUD_PROVIDER_ID";
  private static final String masterUrl = "dummyMasterUrl";
  public static final String username = "dummyUsername";
  public static final String password = "dummyPassword";

  private Cluster k8sCluster;
  private ClusterRecord k8sClusterRecord;
  private Cluster ecsCluster;
  private ClusterRecord ecsClusterRecord;

  private static final String COMPANY_NAME = "Harness";
  private static final String ACCOUNT_NAME = "Harness";
  private Account account;

  @Mock CCMSettingService ccmSettingService;
  @Mock private CEPerpetualTaskManager cePerpetualTaskManager;
  @Mock ClusterRecordService clusterRecordService;
  @Mock FeatureFlagService featureFlagService;
  @Mock @Named(CeClusterFeature.FEATURE_NAME) private UsageLimitedFeature ceClusterFeature;
  @InjectMocks CEPerpetualTaskHandler handler;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Before
  public void setUp() {
    k8sCluster = DirectKubernetesCluster.builder().cloudProviderId(cloudProviderId).build();
    ecsCluster = EcsCluster.builder().cloudProviderId(cloudProviderId).build();

    k8sClusterRecord = ClusterRecord.builder().accountId(accountId).cluster(k8sCluster).build();
    ecsClusterRecord = ClusterRecord.builder().accountId(accountId).cluster(ecsCluster).build();

    account = anAccount()
                  .withUuid(accountId)
                  .withCompanyName(COMPANY_NAME)
                  .withAccountName(ACCOUNT_NAME)
                  .withAccountKey("ACCOUNT_KEY")
                  .withCloudCostEnabled(true)
                  .withCeK8sEventCollectionEnabled(true)
                  .build();

    when(ccmSettingService.isCloudCostEnabled(isA(ClusterRecord.class))).thenReturn(true);
    when(ccmSettingService.isCeK8sEventCollectionEnabled(eq(accountId))).thenReturn(true);
    when(cePerpetualTaskManager.createPerpetualTasks(isA(ClusterRecord.class))).thenReturn(true);
    when(cePerpetualTaskManager.createPerpetualTasks(isA(Account.class), anyString())).thenReturn(true);
    when(clusterRecordService.list(eq(accountId), eq(DIRECT_KUBERNETES))).thenReturn(Arrays.asList(k8sClusterRecord));
    when(ceClusterFeature.getMaxUsageAllowedForAccount(accountId)).thenReturn(2);
    when(ceClusterFeature.getUsage(accountId)).thenReturn(1);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldCreatePTask_IfAccountHasCeK8sEventCollectionEnabled_1() {
    account.setCloudCostEnabled(false);
    account.setCeAutoCollectK8sEvents(true);
    handler.onAccountCreated(account);
    verify(cePerpetualTaskManager).createPerpetualTasks(eq(account), eq(DIRECT_KUBERNETES));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldCreatePTask_IfAccountHasCeK8sEventCollectionEnabled_2() {
    account.setCloudCostEnabled(true);
    account.setCeAutoCollectK8sEvents(true);
    handler.onAccountCreated(account);
    verify(cePerpetualTaskManager).createPerpetualTasks(eq(account), eq(DIRECT_KUBERNETES));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldDoNothing_IfAccountHasCloudCostEnabled() {
    account.setCloudCostEnabled(true);
    account.setCeAutoCollectK8sEvents(false);
    handler.onAccountCreated(account);
    verify(cePerpetualTaskManager, times(0)).createPerpetualTasks(eq(account), eq(DIRECT_KUBERNETES));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldDeletePTask_IfAccountHasCloudCostDisabled() {
    account.setCeAutoCollectK8sEvents(false);
    account.setCloudCostEnabled(false);
    handler.onAccountCreated(account);
    verify(cePerpetualTaskManager).deletePerpetualTasks(eq(account), eq(null));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldDeletePTask_IfAccountHasCloudCostEnabled_AndCloudProviderHasCloudCostDisabled() {
    when(ccmSettingService.isCloudCostEnabled(isA(ClusterRecord.class))).thenReturn(false);
    account.setCeAutoCollectK8sEvents(false);
    account.setCloudCostEnabled(true);
    handler.onAccountCreated(account);
    verify(cePerpetualTaskManager).deletePerpetualTasks(eq(k8sClusterRecord));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testOnAccountUpdated() {
    account.setCloudCostEnabled(false);
    handler.onAccountUpdated(account);
    verify(cePerpetualTaskManager).createPerpetualTasks(eq(account), eq(DIRECT_KUBERNETES));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testOnUpserted() {
    handler.onUpserted(ecsClusterRecord);
    verify(cePerpetualTaskManager).createPerpetualTasks(eq(ecsClusterRecord));
    ecsClusterRecord = ClusterRecord.builder().accountId(accountId).cluster(k8sCluster).build();
    handler.onUpserted(ecsClusterRecord);
    verify(cePerpetualTaskManager, times(0)).createPerpetualTasks(eq(ecsClusterRecord));
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testOnUpsertedClusterLimit() {
    when(ceClusterFeature.getMaxUsageAllowedForAccount(accountId)).thenReturn(2);
    when(ceClusterFeature.getUsage(accountId)).thenReturn(3);
    assertThatThrownBy(() -> handler.onUpserted(ecsClusterRecord)).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testOnDeleting() {
    handler.onDeleting(ecsClusterRecord);
    verify(cePerpetualTaskManager).deletePerpetualTasks(eq(ecsClusterRecord));
  }
}
