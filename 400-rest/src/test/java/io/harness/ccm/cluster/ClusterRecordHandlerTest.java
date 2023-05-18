/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.cluster;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.HANTANG;

import static software.wings.beans.InfrastructureType.DIRECT_KUBERNETES;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ccm.CEPerpetualTaskManager;
import io.harness.ccm.cluster.entities.ClusterRecord;
import io.harness.ccm.config.CCMSettingService;
import io.harness.rule.Owner;

import software.wings.beans.AwsConfig;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.EcsInfrastructureMapping;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SettingAttribute;
import software.wings.infra.AwsEcsInfrastructure;
import software.wings.infra.DirectKubernetesInfrastructure;
import software.wings.infra.InfrastructureDefinition;
import software.wings.settings.SettingVariableTypes;

import java.util.Arrays;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CE)
public class ClusterRecordHandlerTest extends CategoryTest {
  @Mock CCMSettingService ccmSettingService;
  @Mock ClusterRecordService clusterRecordService;
  @Mock CEPerpetualTaskManager cePerpetualTaskManager;
  @Mock InfrastructureDefinitionDao infrastructureDefinitionDao;
  @Mock InfrastructureMappingDao infrastructureMappingDao;
  @InjectMocks @Spy private ClusterRecordHandler handler;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private String accountId = "ACCOUNT_ID";
  private String cloudProviderId = "CLOUD_PROVIDER_ID";

  private SettingAttribute prevSettingAttribute;
  private SettingAttribute settingAttribute;
  private SettingAttribute awsSettingAttribute;

  private InfrastructureDefinition infrastructureDefinition;
  private InfrastructureMapping infrastructureMapping;

  private InfrastructureDefinition ecsInfrastructureDefinition;
  private InfrastructureMapping ecsInfrastructureMapping;

  @Before
  public void setUp() {
    KubernetesClusterConfig kubernetesClusterConfig = new KubernetesClusterConfig();
    kubernetesClusterConfig.setType(SettingVariableTypes.KUBERNETES_CLUSTER.name());

    prevSettingAttribute = aSettingAttribute()
                               .withUuid(cloudProviderId)
                               .withAccountId(accountId)
                               .withName("PREV_NAME")
                               .withValue(kubernetesClusterConfig)
                               .build();

    settingAttribute = aSettingAttribute()
                           .withUuid(cloudProviderId)
                           .withAccountId(accountId)
                           .withName("CURR_NAME")
                           .withValue(kubernetesClusterConfig)
                           .build();

    awsSettingAttribute = aSettingAttribute()
                              .withUuid(cloudProviderId)
                              .withAccountId(accountId)
                              .withName("CURR_NAME")
                              .withValue(AwsConfig.builder().build())
                              .build();

    infrastructureDefinition =
        InfrastructureDefinition.builder().infrastructure(DirectKubernetesInfrastructure.builder().build()).build();

    infrastructureMapping = DirectKubernetesInfrastructureMapping.builder()
                                .accountId(accountId)
                                .infraMappingType(DIRECT_KUBERNETES)
                                .build();

    ClusterRecord clusterRecord = ClusterRecord.builder().build();

    when(ccmSettingService.isCloudCostEnabled(isA(SettingAttribute.class))).thenReturn(true);
    when(clusterRecordService.from(isA(SettingAttribute.class))).thenReturn(clusterRecord);
    when(clusterRecordService.from(isA(InfrastructureDefinition.class))).thenReturn(clusterRecord);
    when(clusterRecordService.from(isA(InfrastructureMapping.class))).thenReturn(clusterRecord);

    when(clusterRecordService.upsert(isA(ClusterRecord.class))).thenReturn(clusterRecord);
    when(clusterRecordService.delete(anyString(), anyString())).thenReturn(true);

    when(cePerpetualTaskManager.resetPerpetualTasks(isA(SettingAttribute.class))).thenReturn(true);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldUpsertOnSavedK8SCloudProvider() {
    when(ccmSettingService.isCeK8sEventCollectionEnabled(eq(settingAttribute))).thenReturn(true);
    handler.onSaved(settingAttribute);
    verify(clusterRecordService).upsert(isA(ClusterRecord.class));
    verify(cePerpetualTaskManager).createPerpetualTasks(eq(settingAttribute));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldUpsertOnSavedAWSCloudProvider() {
    ecsInfrastructureDefinition =
        InfrastructureDefinition.builder().infrastructure(AwsEcsInfrastructure.builder().build()).build();
    ecsInfrastructureMapping =
        EcsInfrastructureMapping.builder().accountId(accountId).region("REGION").clusterName("CLUSTER_NAME").build();
    when(infrastructureDefinitionDao.list(eq(cloudProviderId)))
        .thenReturn(Arrays.asList(ecsInfrastructureDefinition, null));
    when(infrastructureMappingDao.list(eq(cloudProviderId))).thenReturn(Arrays.asList(ecsInfrastructureMapping, null));

    handler.onSaved(awsSettingAttribute);
    verify(clusterRecordService, times(2)).upsert(isA(ClusterRecord.class));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldNotUpsertOnSavedNonConvertibleCloudProvider() {
    when(infrastructureDefinitionDao.list(eq(cloudProviderId))).thenReturn(Arrays.asList(ecsInfrastructureDefinition));
    when(infrastructureMappingDao.list(eq(cloudProviderId))).thenReturn(Arrays.asList(ecsInfrastructureMapping));
    when(clusterRecordService.from(isA(InfrastructureDefinition.class))).thenReturn(null);
    when(clusterRecordService.from(isA(InfrastructureMapping.class))).thenReturn(null);

    handler.onSaved(awsSettingAttribute);
    verify(clusterRecordService, times(0)).upsert(isA(ClusterRecord.class));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldUpsertOnUpdatedCloudProvider() {
    handler.onUpdated(prevSettingAttribute, settingAttribute);
    verify(clusterRecordService).upsert(isA(ClusterRecord.class));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testOnDeletedCloudProvider() {
    handler.onDeleted(settingAttribute);
    verify(clusterRecordService).deactivate(accountId, cloudProviderId);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testOnSavedInfrastructureDefinition() {
    handler.onSaved(infrastructureDefinition);
    verify(clusterRecordService).upsert(isA(ClusterRecord.class));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testOnUpdatedInfrastructureDefinition() {
    handler.onUpdated(infrastructureDefinition);
    verify(clusterRecordService).upsert(isA(ClusterRecord.class));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testOnSavedInfrastructureMapping() {
    handler.onSaved(infrastructureMapping);
    verify(clusterRecordService).upsert(isA(ClusterRecord.class));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testOnUpdatedInfrastructureMapping() {
    handler.onUpdated(infrastructureMapping);
    verify(clusterRecordService).upsert(isA(ClusterRecord.class));
  }
}
