/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.instancesyncv2.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.container.ContainerInfo;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesyncv2.CgDeploymentReleaseDetails;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;

import software.wings.api.ContainerDeploymentInfoWithLabels;
import software.wings.api.DeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.api.K8sDeploymentInfo;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SettingAttribute;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.instancesyncv2.model.CgK8sReleaseIdentifier;
import software.wings.instancesyncv2.model.CgReleaseIdentifiers;
import software.wings.instancesyncv2.model.InstanceSyncTaskDetails;
import software.wings.service.intfc.InfrastructureMappingService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class K8SInstanceSyncV2DeploymentHelperCgTest extends CategoryTest {
  @InjectMocks K8sInstanceSyncV2DeploymentHelperCg k8SInstanceSyncV2DeploymentHelperCg;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private ContainerDeploymentManagerHelper containerDeploymentManagerHelper;
  @Mock private KryoSerializer kryoSerializer;

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testFetchInfraConnectorDetails() {
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute()
            .withAccountId("accountId")
            .withAppId("appId")
            .withValue(KubernetesClusterConfig.builder().accountId("accountId").masterUrl("masterURL").build())
            .build();

    doReturn(new byte[] {}).when(kryoSerializer).asBytes(any());
    doReturn(new byte[] {}).when(kryoSerializer).asDeflatedBytes(any());
    PerpetualTaskExecutionBundle perpetualTaskExecutionBundle =
        k8SInstanceSyncV2DeploymentHelperCg.fetchInfraConnectorDetails(settingAttribute);
    assertThat(perpetualTaskExecutionBundle).isNotNull();
    assertThat(perpetualTaskExecutionBundle.getTaskParams().getTypeUrl())
        .isEqualTo("type.googleapis.com/io.harness.perpetualtask.instancesyncv2.CgInstanceSyncTaskParams");
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void mergeReleaseIdentifiers() {
    Set<CgReleaseIdentifiers> existingIdentifiers = Collections.singleton(CgK8sReleaseIdentifier.builder()
                                                                              .releaseName("releaseName")
                                                                              .clusterName("clusterName")
                                                                              .namespace("namespace1")
                                                                              .isHelmDeployment(false)
                                                                              .build());
    Set<CgReleaseIdentifiers> newIdentifiers = Collections.singleton(CgK8sReleaseIdentifier.builder()
                                                                         .releaseName("releaseName")
                                                                         .clusterName("clusterName")
                                                                         .namespace("namespace")
                                                                         .isHelmDeployment(false)
                                                                         .build());
    Set<CgReleaseIdentifiers> result =
        k8SInstanceSyncV2DeploymentHelperCg.mergeReleaseIdentifiers(existingIdentifiers, newIdentifiers);
    assertThat(result).isNotNull();
    assertThat(result.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void prepareTaskDetails() {
    DeploymentSummary deploymentSummary = DeploymentSummary.builder()
                                              .appId("appId")
                                              .infraMappingId("infraMappingId")
                                              .accountId("accountId")
                                              .deploymentInfo(K8sDeploymentInfo.builder()
                                                                  .releaseName("releaseName")
                                                                  .namespace("namespace")
                                                                  .clusterName("clusterName")
                                                                  .build())
                                              .build();
    InstanceSyncTaskDetails instanceSyncTaskDetails =
        k8SInstanceSyncV2DeploymentHelperCg.prepareTaskDetails(deploymentSummary, "cloudProviderId", "perpetualId");
    assertThat(instanceSyncTaskDetails).isNotNull();
    assertThat(instanceSyncTaskDetails.getAccountId()).isEqualTo("accountId");
    assertThat(instanceSyncTaskDetails.getPerpetualTaskId()).isEqualTo("perpetualId");
    assertThat(instanceSyncTaskDetails.getCloudProviderId()).isEqualTo("cloudProviderId");
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void buildReleaseIdentifiers() {
    DeploymentInfo deploymentInfo = K8sDeploymentInfo.builder()
                                        .releaseName("releaseName")
                                        .namespace("namespace")
                                        .clusterName("clusterName")
                                        .build();
    Set<CgReleaseIdentifiers> releaseIdentifiers =
        k8SInstanceSyncV2DeploymentHelperCg.buildReleaseIdentifiers(deploymentInfo);
    assertThat(releaseIdentifiers).isNotNull();
    assertThat(releaseIdentifiers.stream().findAny().get().getClass()).isEqualTo(CgK8sReleaseIdentifier.class);

    deploymentInfo = ContainerDeploymentInfoWithLabels.builder()
                         .releaseName("releaseName")
                         .namespace("namespace")
                         .clusterName("clusterName")
                         .containerInfoList(
                             new ArrayList<>(Arrays.asList(ContainerInfo.builder().containerId("containerId").build())))
                         .build();
    Set<CgReleaseIdentifiers> newIdentifiers =
        k8SInstanceSyncV2DeploymentHelperCg.buildReleaseIdentifiers(deploymentInfo);
    assertThat(newIdentifiers).isNotNull();
    CgK8sReleaseIdentifier identifier = (CgK8sReleaseIdentifier) newIdentifiers.stream().findFirst().get();
    assertThat(identifier.getReleaseName()).isEqualTo("releaseName");
    assertThat(identifier.isHelmDeployment()).isEqualTo(true);
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void getDeploymentReleaseDetails() {
    InstanceSyncTaskDetails instanceSyncTaskDetails =
        InstanceSyncTaskDetails.builder()
            .cloudProviderId("cloudProviderId")
            .perpetualTaskId("perpetualId")
            .appId("appId")
            .accountId("accountId")
            .uuid("uuid")
            .infraMappingId("infraMappingId")
            .releaseIdentifiers(Collections.singleton(CgK8sReleaseIdentifier.builder()
                                                          .releaseName("releaseName")
                                                          .clusterName("clusterName")
                                                          .namespace("namespace1")
                                                          .isHelmDeployment(false)
                                                          .build()))
            .build();

    doReturn(new byte[] {}).when(kryoSerializer).asBytes(any());
    InfrastructureMapping infraMapping = DirectKubernetesInfrastructureMapping.builder()
                                             .appId("appId")
                                             .infraMappingType("K8s")
                                             .accountId("accountId")
                                             .build();
    doReturn(infraMapping).when(infrastructureMappingService).get(anyString(), anyString());
    doReturn(K8sClusterConfig.builder().clusterName("clusterName").build())
        .when(containerDeploymentManagerHelper)
        .getK8sClusterConfig(any(), any());

    List<CgDeploymentReleaseDetails> cgDeploymentReleaseDetails =
        k8SInstanceSyncV2DeploymentHelperCg.getDeploymentReleaseDetails(instanceSyncTaskDetails);
    assertThat(cgDeploymentReleaseDetails).isNotNull();
    assertThat(cgDeploymentReleaseDetails.size()).isEqualTo(1);
    assertThat(cgDeploymentReleaseDetails.get(0).getInfraMappingType()).isEqualTo("K8s");
    assertThat(cgDeploymentReleaseDetails.get(0).getInfraMappingId()).isEqualTo("infraMappingId");
  }
}
