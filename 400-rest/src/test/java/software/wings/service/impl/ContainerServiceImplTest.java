/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.UTSAV;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.response.CEK8sDelegatePrerequisite;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.WingsBaseTest;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.instance.info.ContainerInfo;
import software.wings.beans.infrastructure.instance.info.KubernetesContainerInfo;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.settings.SettingValue;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodStatus;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
public class ContainerServiceImplTest extends WingsBaseTest {
  @Mock private KubernetesContainerService kubernetesContainerService;
  @Mock private EncryptionService encryptionService;
  @InjectMocks private ContainerServiceImpl containerService;

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void getContainerInfos() {
    ContainerServiceParams containerServiceParams =
        buildContainerSvcParams(null, KubernetesClusterConfig.builder().build());
    doReturn(asList(buildPod("p-1", "i-1"), buildPod("p-2", "i-2")))
        .when(kubernetesContainerService)
        .getRunningPodsWithLabels(
            any(KubernetesConfig.class), eq("default"), eq(ImmutableMap.of("release", "release-name")));

    final List<ContainerInfo> containerInfos = containerService.getContainerInfos(containerServiceParams, false);

    assertThat(containerInfos.stream().map(ContainerInfo::getClusterName).collect(Collectors.toList()))
        .containsExactly("test", "test");
    assertThat(
        containerInfos.stream().map(v -> ((KubernetesContainerInfo) v).getPodName()).collect(Collectors.toList()))
        .containsExactly("p-1", "p-2");
    assertThat(containerInfos.stream().map(v -> ((KubernetesContainerInfo) v).getIp()).collect(Collectors.toList()))
        .containsExactly("i-1", "i-2");
    assertThat(
        containerInfos.stream().map(v -> ((KubernetesContainerInfo) v).getReleaseName()).collect(Collectors.toList()))
        .containsExactly("release-name", "release-name");
    assertThat(
        containerInfos.stream().map(v -> ((KubernetesContainerInfo) v).getNamespace()).collect(Collectors.toList()))
        .containsExactly("default", "default");
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void shouldGetEmptyContainerInfosWhenReleaseLabelIsMissing() {
    ContainerServiceParams containerServiceParams =
        buildContainerSvcParams(null, KubernetesClusterConfig.builder().build());
    containerServiceParams.setReleaseName(null);
    final List<ContainerInfo> containerInfosWithReleaseNameNull =
        containerService.getContainerInfos(containerServiceParams, false);
    assertThat(containerInfosWithReleaseNameNull.isEmpty()).isTrue();

    containerServiceParams.setReleaseName("");
    final List<ContainerInfo> containerInfosWithReleaseNameEmpty =
        containerService.getContainerInfos(containerServiceParams, false);
    assertThat(containerInfosWithReleaseNameEmpty.isEmpty()).isTrue();
  }

  private ContainerServiceParams buildContainerSvcParams(String containerSvcName, SettingValue value) {
    return ContainerServiceParams.builder()
        .containerServiceName(containerSvcName)
        .settingAttribute(SettingAttribute.Builder.aSettingAttribute().withValue(value).build().toDTO())
        .releaseName("release-name")
        .clusterName("test")
        .namespace("default")
        .build();
  }

  private V1Pod buildPod(String name, String ip) {
    V1Pod pod = new V1Pod();
    V1ObjectMeta meta = new V1ObjectMeta();
    meta.setName(name);
    V1PodStatus status = new V1PodStatus();
    status.setPodIP(ip);
    pod.setMetadata(meta);
    pod.setStatus(status);
    return pod;
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidate() {
    ContainerServiceParams containerServiceParams =
        ContainerServiceParams.builder()
            .settingAttribute(SettingAttribute.Builder.aSettingAttribute()
                                  .withValue(KubernetesClusterConfig.builder().build())
                                  .build()
                                  .toDTO())
            .build();

    doNothing().when(kubernetesContainerService).validate(any(KubernetesConfig.class), eq(false));
    assertThat(containerService.validate(containerServiceParams, false)).isTrue();

    containerServiceParams.setSettingAttribute(SettingAttribute.Builder.aSettingAttribute()
                                                   .withValue(KubernetesClusterConfig.builder().build())
                                                   .build()
                                                   .toDTO());
    doNothing().when(kubernetesContainerService).validate(any(KubernetesConfig.class), eq(true));
    assertThat(containerService.validate(containerServiceParams, true)).isTrue();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testValidateCE() {
    ContainerServiceParams containerServiceParams =
        ContainerServiceParams.builder()
            .settingAttribute(SettingAttribute.Builder.aSettingAttribute()
                                  .withValue(KubernetesClusterConfig.builder().build())
                                  .build()
                                  .toDTO())
            .build();

    doNothing().when(kubernetesContainerService).validateCEPermissions(any(KubernetesConfig.class));
    assertThat(containerService.validateCE(containerServiceParams)).isTrue();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testValidateCEthrowsException() {
    final String MESSAGE = "MESSAGE";
    ContainerServiceParams containerServiceParams =
        ContainerServiceParams.builder()
            .settingAttribute(SettingAttribute.Builder.aSettingAttribute()
                                  .withValue(KubernetesClusterConfig.builder().build())
                                  .build()
                                  .toDTO())
            .build();

    doThrow(new InvalidRequestException(MESSAGE))
        .when(kubernetesContainerService)
        .validateCEPermissions(any(KubernetesConfig.class));
    assertThatThrownBy(() -> containerService.validateCE(containerServiceParams))
        .isExactlyInstanceOf(InvalidRequestException.class)
        .hasMessage(MESSAGE);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetControllerNames() {
    HasMetadata controller_1 = mock(Deployment.class);
    ObjectMeta metaData_1 = mock(ObjectMeta.class);
    when(controller_1.getKind()).thenReturn("Deployment");
    when(controller_1.getMetadata()).thenReturn(metaData_1);
    when(metaData_1.getName()).thenReturn("deployment-name");
    List<? extends HasMetadata> controllers = asList(controller_1);
    doReturn(controllers).when(kubernetesContainerService).getControllers(any(KubernetesConfig.class), anyMap());

    ContainerServiceParams containerServiceParams =
        ContainerServiceParams.builder()
            .settingAttribute(SettingAttribute.Builder.aSettingAttribute()
                                  .withValue(KubernetesClusterConfig.builder().build())
                                  .build()
                                  .toDTO())
            .build();

    Set<String> controllerNames = containerService.getControllerNames(containerServiceParams, emptyMap());
    assertThat(controllerNames).contains("deployment-name");
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testValidateCEK8sDelegate() {
    ContainerServiceParams containerServiceParams =
        ContainerServiceParams.builder()
            .settingAttribute(SettingAttribute.Builder.aSettingAttribute()
                                  .withValue(KubernetesClusterConfig.builder().build())
                                  .build()
                                  .toDTO())
            .build();

    when(kubernetesContainerService.validateMetricsServer(any())).thenReturn(null);
    when(kubernetesContainerService.validateCEResourcePermissions(any())).thenReturn(null);

    CEK8sDelegatePrerequisite cek8sDelegatePrerequisite =
        containerService.validateCEK8sDelegate(containerServiceParams);

    assertThat(cek8sDelegatePrerequisite).isNotNull();
    assertThat(cek8sDelegatePrerequisite.getMetricsServer()).isNull();
    assertThat(cek8sDelegatePrerequisite.getPermissions()).isNull();
  }
}
