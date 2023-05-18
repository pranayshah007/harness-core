/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.rule.OwnerRule.BRETT;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.CLUSTER_NAME;
import static software.wings.utils.WingsTestConstants.ECS_SERVICE_NAME;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.instance.info.ContainerInfo;
import software.wings.cloudprovider.aws.AwsClusterService;
import software.wings.cloudprovider.gke.GkeClusterService;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.impl.ContainerServiceImpl;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.intfc.ContainerService;
import software.wings.service.intfc.security.EncryptionService;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ecs.model.ListTasksResult;
import com.amazonaws.services.ecs.model.Service;
import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerBuilder;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodBuilder;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ContainerServiceTest extends WingsBaseTest {
  private static final String KUBERNETES_REPLICATION_CONTROLLER_NAME = "kubernetes-rc-name.0";
  private static final String KUBERNETES_SERVICE_NAME = "kubernetes-service-name";

  @Mock private GkeClusterService gkeClusterService;
  @Mock private KubernetesContainerService kubernetesContainerService;
  @Mock private AwsClusterService awsClusterService;
  @Mock private AwsHelperService awsHelperService;
  @Mock private EncryptionService encryptionService;

  @InjectMocks private ContainerService containerService = new ContainerServiceImpl();

  @Mock private ListTasksResult listTasksResult;

  private KubernetesConfig kubernetesConfig = KubernetesConfig.builder()
                                                  .masterUrl("masterUrl")
                                                  .namespace("default")
                                                  .username("user".toCharArray())
                                                  .password("pass".toCharArray())
                                                  .accountId(ACCOUNT_ID)
                                                  .build();

  private KubernetesClusterConfig kubernetesClusterConfig = KubernetesClusterConfig.builder()
                                                                .masterUrl("masterUrl")
                                                                .username("user".toCharArray())
                                                                .password("pass".toCharArray())
                                                                .accountId(ACCOUNT_ID)
                                                                .build();

  private ContainerServiceParams gcpParams =
      ContainerServiceParams.builder()
          .settingAttribute(SettingAttribute.Builder.aSettingAttribute()
                                .withValue(GcpConfig.builder()
                                               .serviceAccountKeyFileContent("keyFileContent".toCharArray())
                                               .accountId(ACCOUNT_ID)
                                               .build())
                                .build()
                                .toDTO())
          .encryptionDetails(emptyList())
          .clusterName(CLUSTER_NAME)
          .namespace("default")
          .containerServiceName(KUBERNETES_REPLICATION_CONTROLLER_NAME)
          .build();

  private ContainerServiceParams awsParams =
      ContainerServiceParams.builder()
          .settingAttribute(SettingAttribute.Builder.aSettingAttribute()
                                .withValue(AwsConfig.builder()
                                               .accessKey("accessKey".toCharArray())
                                               .secretKey("secretKey".toCharArray())
                                               .accountId(ACCOUNT_ID)
                                               .build())
                                .build()
                                .toDTO())
          .encryptionDetails(emptyList())
          .clusterName(CLUSTER_NAME)
          .containerServiceName(ECS_SERVICE_NAME)
          .region("us-east-1")
          .build();

  private ContainerServiceParams kubernetesConfigParams =
      ContainerServiceParams.builder()
          .settingAttribute(SettingAttribute.Builder.aSettingAttribute()
                                .aSettingAttribute()
                                .withValue(kubernetesClusterConfig)
                                .build()
                                .toDTO())
          .encryptionDetails(emptyList())
          .clusterName(CLUSTER_NAME)
          .namespace("default")
          .containerServiceName(KUBERNETES_REPLICATION_CONTROLLER_NAME)
          .build();

  @Before
  public void setup() {
    ReplicationController replicationController = new ReplicationControllerBuilder()
                                                      .withApiVersion("v1")
                                                      .withNewMetadata()
                                                      .withName(KUBERNETES_REPLICATION_CONTROLLER_NAME)
                                                      .endMetadata()
                                                      .withNewSpec()
                                                      .withReplicas(2)
                                                      .endSpec()
                                                      .build();
    io.fabric8.kubernetes.api.model.Service kubernetesService = new io.fabric8.kubernetes.api.model.ServiceBuilder()
                                                                    .withApiVersion("v1")
                                                                    .withNewMetadata()
                                                                    .withName(KUBERNETES_SERVICE_NAME)
                                                                    .endMetadata()
                                                                    .build();
    V1Pod pod = new V1PodBuilder()
                    .withApiVersion("v1")
                    .withNewStatus()
                    .withPhase("Running")
                    .endStatus()
                    .withNewMetadata()
                    .addToLabels("app", "MyApp")
                    .endMetadata()
                    .build();
    PodTemplateSpec podTemplateSpec = new PodTemplateSpecBuilder()
                                          .withNewMetadata()
                                          .withLabels(ImmutableMap.of("app", "MyApp"))
                                          .endMetadata()
                                          .build();
    when(gkeClusterService.getCluster(gcpParams.getSettingAttribute(), emptyList(), CLUSTER_NAME, "default", false))
        .thenReturn(kubernetesConfig);
    when(kubernetesContainerService.listControllers(kubernetesConfig))
        .thenReturn((List) singletonList(replicationController));
    when(kubernetesContainerService.getController(eq(kubernetesConfig), anyString())).thenReturn(replicationController);
    when(kubernetesContainerService.getServices(eq(kubernetesConfig), any()))
        .thenReturn(singletonList(kubernetesService));
    when(kubernetesContainerService.getRunningPodsWithLabels(eq(kubernetesConfig), anyString(), any()))
        .thenReturn(singletonList(pod));
    when(kubernetesContainerService.getControllers(eq(kubernetesConfig), any()))
        .thenReturn((List) singletonList(replicationController));
    when(kubernetesContainerService.getControllerPodCount(eq(kubernetesConfig), anyString()))
        .thenReturn(Optional.of(2));
    when(kubernetesContainerService.getControllerPodCount(any(ReplicationController.class))).thenReturn(2);
    when(kubernetesContainerService.getPodTemplateSpec(replicationController)).thenReturn(podTemplateSpec);
    doReturn(null).when(encryptionService).decrypt(eq(kubernetesClusterConfig), any(), eq(false));

    Service ecsService = new Service();
    ecsService.setServiceName(ECS_SERVICE_NAME);
    ecsService.setCreatedAt(new Date());
    ecsService.setDesiredCount(2);
    AwsConfig awsConfig = AwsConfig.builder()
                              .accessKey("accessKey".toCharArray())
                              .secretKey("secretKey".toCharArray())
                              .accountId(ACCOUNT_ID)
                              .build();
    SettingAttribute awsSettingAttribute = aSettingAttribute().withValue(awsConfig).build();
    when(awsClusterService.getServices(
             Regions.US_EAST_1.getName(), awsSettingAttribute.toDTO(), Collections.emptyList(), CLUSTER_NAME))
        .thenReturn(singletonList(ecsService));
    when(awsHelperService.validateAndGetAwsConfig(eq(awsSettingAttribute.toDTO()), any(), eq(false)))
        .thenReturn(awsConfig);
    when(awsHelperService.listTasks(eq("us-east-1"), eq(awsConfig), any(), any(), eq(false)))
        .thenReturn(listTasksResult);
    when(listTasksResult.getTaskArns()).thenReturn(emptyList());
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldGetContainerInfos_Gcp() {
    List<ContainerInfo> result = containerService.getContainerInfos(gcpParams, false);

    assertThat(result.size()).isEqualTo(1);
  }
  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldGetContainerInfos_Aws() {
    List<ContainerInfo> result = containerService.getContainerInfos(awsParams, false);

    assertThat(result.size()).isEqualTo(0);
  }
  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldGetContainerInfos_DirectKube() {
    List<ContainerInfo> result = containerService.getContainerInfos(kubernetesConfigParams, false);

    assertThat(result.size()).isEqualTo(1);
  }
}
