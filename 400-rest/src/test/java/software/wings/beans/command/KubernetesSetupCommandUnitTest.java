/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.BRETT;
import static io.harness.rule.OwnerRule.PUNEET;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.command.CommandExecutionContext.Builder.aCommandExecutionContext;
import static software.wings.beans.command.KubernetesSetupParams.KubernetesSetupParamsBuilder.aKubernetesSetupParams;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.KubernetesConvention;
import io.harness.k8s.model.ImageDetails;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.GcpConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.command.KubernetesSetupParams.KubernetesSetupParamsBuilder;
import software.wings.beans.container.KubernetesBlueGreenConfig;
import software.wings.beans.container.KubernetesPortProtocol;
import software.wings.beans.container.KubernetesServiceSpecification;
import software.wings.beans.container.KubernetesServiceType;
import software.wings.cloudprovider.gke.GkeClusterService;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscaler;
import java.time.Clock;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class KubernetesSetupCommandUnitTest extends WingsBaseTest {
  @Mock private GkeClusterService gkeClusterService;
  @Mock private KubernetesContainerService kubernetesContainerService;
  @Mock private Clock clock;

  @InjectMocks private KubernetesSetupCommandUnit kubernetesSetupCommandUnit = new KubernetesSetupCommandUnit();

  private KubernetesConfig kubernetesConfig = KubernetesConfig.builder()
                                                  .masterUrl("https://1.1.1.1/")
                                                  .username("admin".toCharArray())
                                                  .password("password".toCharArray())
                                                  .namespace("default")
                                                  .build();
  private KubernetesSetupParams setupParams = aKubernetesSetupParams()
                                                  .withAppName(APP_NAME)
                                                  .withEnvName(ENV_NAME)
                                                  .withServiceName(SERVICE_NAME)
                                                  .withControllerNamePrefix("app-name-service-name-env-name")
                                                  .withImageDetails(ImageDetails.builder()
                                                                        .registryUrl("gcr.io")
                                                                        .sourceName("GCR")
                                                                        .name("exploration-161417/todolist")
                                                                        .tag("v1")
                                                                        .build())
                                                  .withInfraMappingId(INFRA_MAPPING_ID)
                                                  .withPort(80)
                                                  .withProtocol(KubernetesPortProtocol.TCP)
                                                  .withServiceType(KubernetesServiceType.ClusterIP)
                                                  .withTargetPort(8080)
                                                  .withClusterName("cluster")
                                                  .withUseFixedInstances(true)
                                                  .withFixedInstances(3)
                                                  .withReleaseName("release-Name")
                                                  .build();
  private SettingAttribute computeProvider = aSettingAttribute().withValue(GcpConfig.builder().build()).build();
  private CommandExecutionContext context = aCommandExecutionContext()
                                                .cloudProviderSetting(computeProvider.toDTO())
                                                .containerSetupParams(setupParams)
                                                .cloudProviderCredentials(emptyList())
                                                .build();

  /**
   * Set up.
   */
  @Before
  public void setup() {
    ReplicationController replicationController =
        new ReplicationControllerBuilder()
            .withApiVersion("v1")
            .withNewMetadata()
            .withName("backend-ctrl")
            .addToLabels("app", "testApp")
            .addToLabels("tier", "backend")
            .endMetadata()
            .withNewSpec()
            .withReplicas(2)
            .withNewTemplate()
            .withNewMetadata()
            .addToLabels("app", "testApp")
            .addToLabels("tier", "backend")
            .endMetadata()
            .withNewSpec()
            .addNewContainer()
            .withName("server")
            .withImage("gcr.io/exploration-161417/todolist")
            .withArgs("8080")
            .withNewResources()
            .withLimits(ImmutableMap.of("cpu", new Quantity("100m"), "memory", new Quantity("100Mi")))
            .endResources()
            .addNewPort()
            .withContainerPort(8080)
            .endPort()
            .endContainer()
            .endSpec()
            .endTemplate()
            .endSpec()
            .build();

    Secret secret = new SecretBuilder()
                        .withApiVersion("v1")
                        .withKind("Secret")
                        .withData(ImmutableMap.of(".dockercfg", "aaa"))
                        .withNewMetadata()
                        .withName("secret-name")
                        .endMetadata()
                        .build();

    io.fabric8.kubernetes.api.model.Service kubernetesService = new ServiceBuilder()
                                                                    .withApiVersion("v1")
                                                                    .withNewMetadata()
                                                                    .withName("backend-service")
                                                                    .addToLabels("app", "testApp")
                                                                    .addToLabels("tier", "backend")
                                                                    .endMetadata()
                                                                    .withNewSpec()
                                                                    .withType("LoadBalancer")
                                                                    .addNewPort()
                                                                    .withPort(80)
                                                                    .withNewTargetPort()
                                                                    .withValue(8080)
                                                                    .endTargetPort()
                                                                    .endPort()
                                                                    .addToSelector("app", "testApp")
                                                                    .addToSelector("tier", "backend")
                                                                    .withClusterIP("1.2.3.4")
                                                                    .endSpec()
                                                                    .withNewStatus()
                                                                    .withNewLoadBalancer()
                                                                    .addNewIngress()
                                                                    .withIp("5.6.7.8")
                                                                    .endIngress()
                                                                    .endLoadBalancer()
                                                                    .endStatus()
                                                                    .build();

    when(gkeClusterService.getCluster(nullable(software.wings.beans.dto.SettingAttribute.class), eq(emptyList()),
             nullable(String.class), nullable(String.class), nullable(Boolean.class)))
        .thenReturn(kubernetesConfig);
    when(kubernetesContainerService.createOrReplaceController(eq(kubernetesConfig), nullable(HasMetadata.class)))
        .thenReturn(replicationController);
    when(kubernetesContainerService.listControllers(kubernetesConfig)).thenReturn(null);
    when(kubernetesContainerService.createOrReplaceServiceFabric8(
             eq(kubernetesConfig), nullable(io.fabric8.kubernetes.api.model.Service.class)))
        .thenReturn(kubernetesService);
    when(kubernetesContainerService.createOrReplaceSecretFabric8(eq(kubernetesConfig), nullable(Secret.class)))
        .thenReturn(secret);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldExecuteWithLastService() {
    ReplicationController kubernetesReplicationController =
        new ReplicationControllerBuilder()
            .withNewMetadata()
            .withName(KubernetesConvention.getControllerName(
                KubernetesConvention.getControllerNamePrefix("app", "service", "env"), 1))
            .withCreationTimestamp(new Date().toString())
            .endMetadata()
            .build();

    when(kubernetesContainerService.listControllers(kubernetesConfig))
        .thenReturn((List) singletonList(kubernetesReplicationController));

    CommandExecutionStatus status = kubernetesSetupCommandUnit.execute(context);
    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(gkeClusterService)
        .getCluster(nullable(software.wings.beans.dto.SettingAttribute.class), eq(emptyList()), nullable(String.class),
            nullable(String.class), nullable(Boolean.class));
    verify(kubernetesContainerService).createOrReplaceController(eq(kubernetesConfig), nullable(HasMetadata.class));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCreateCustomMetricHorizontalPodAutoscalar() throws Exception {
    ExecutionLogCallback executionLogCallback = mock(ExecutionLogCallback.class);
    doNothing().when(executionLogCallback).saveExecutionLog(nullable(String.class), any());

    Map<String, String> labels = new HashMap<>();
    labels.put("app", "appName");
    labels.put("version", "9");

    String yamlForHPAWithCustomMetric = "apiVersion: autoscaling/v2beta1\n"
        + "kind: HorizontalPodAutoscaler\n"
        + "metadata:\n"
        + "  name: none\n"
        + "  namespace: none\n"
        + "spec:\n"
        + "  scaleTargetRef:\n"
        + "    kind: none\n"
        + "    name: none\n"
        + "  minReplicas: 3\n"
        + "  maxReplicas: 6\n"
        + "  metrics:\n"
        + "  - type: Resource\n"
        + "    resource:\n"
        + "      name: cpu\n"
        + "      targetAverageUtilization: 70\n";

    KubernetesSetupParams setupParams = KubernetesSetupParamsBuilder
                                            .aKubernetesSetupParams()
                                            // use customMetricHPA
                                            .withCustomMetricYamlConfig(yamlForHPAWithCustomMetric)
                                            .build();

    HorizontalPodAutoscaler horizontalPodAutoscaler = kubernetesSetupCommandUnit.createAutoscaler(
        "autoScalerName", "Deployment", "extensions/v1beta1", "default", labels, setupParams, executionLogCallback);

    assertThat(horizontalPodAutoscaler.getApiVersion()).isEqualTo("autoscaling/v2beta1");
    assertThat(horizontalPodAutoscaler.getSpec()).isNotNull();
    assertThat(horizontalPodAutoscaler.getSpec().getScaleTargetRef().getName()).isEqualTo("autoScalerName");
    assertThat(horizontalPodAutoscaler.getSpec().getScaleTargetRef().getKind()).isEqualTo("Deployment");
    assertThat(horizontalPodAutoscaler.getMetadata()).isNotNull();
    assertThat(horizontalPodAutoscaler.getMetadata().getName()).isEqualTo("autoScalerName");
    assertThat(horizontalPodAutoscaler.getMetadata().getNamespace()).isEqualTo("default");
    assertThat(horizontalPodAutoscaler.getMetadata().getLabels()).isNotNull();
    assertThat(horizontalPodAutoscaler.getMetadata().getLabels().containsKey("app")).isTrue();
    assertThat(horizontalPodAutoscaler.getMetadata().getLabels().containsKey("version")).isTrue();
    assertThat(horizontalPodAutoscaler.getMetadata().getLabels().get("app")).isEqualTo("appName");
    assertThat(horizontalPodAutoscaler.getMetadata().getLabels().get("version")).isEqualTo("9");
    assertThat(horizontalPodAutoscaler.getSpec().getMinReplicas()).isEqualTo(Integer.valueOf(3));
    assertThat(horizontalPodAutoscaler.getSpec().getMaxReplicas()).isEqualTo(Integer.valueOf(6));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testBasicHorizontalPodAutoscalar() throws Exception {
    ExecutionLogCallback executionLogCallback = mock(ExecutionLogCallback.class);
    doNothing().when(executionLogCallback).saveExecutionLog(nullable(String.class), any());

    Map labels = new HashMap();
    labels.put("app", "appName");
    labels.put("version", "9");

    KubernetesSetupParams setupParams = KubernetesSetupParamsBuilder
                                            .aKubernetesSetupParams()
                                            // null customMetricConfigYaml, so use basic HPA
                                            .withCustomMetricYamlConfig(null)
                                            .withMinAutoscaleInstances(1)
                                            .withMaxAutoscaleInstances(2)
                                            .withTargetCpuUtilizationPercentage(20)
                                            .build();

    HorizontalPodAutoscaler horizontalPodAutoscaler =
        kubernetesSetupCommandUnit.createAutoscaler("abaris.hpanormal.prod.0", "Deployment", "extensions/v1beta1",
            "default", labels, setupParams, executionLogCallback);

    assertThat(horizontalPodAutoscaler.getApiVersion()).isEqualTo("autoscaling/v1");
    assertThat(horizontalPodAutoscaler.getSpec()).isNotNull();
    assertThat(horizontalPodAutoscaler.getSpec().getScaleTargetRef().getName()).isEqualTo("abaris.hpanormal.prod.0");
    assertThat(horizontalPodAutoscaler.getSpec().getScaleTargetRef().getKind()).isEqualTo("Deployment");
    assertThat(horizontalPodAutoscaler.getMetadata()).isNotNull();
    assertThat(horizontalPodAutoscaler.getMetadata().getName()).isEqualTo("abaris.hpanormal.prod.0");
    assertThat(horizontalPodAutoscaler.getMetadata().getNamespace()).isEqualTo("default");
    assertThat(horizontalPodAutoscaler.getMetadata().getLabels()).isNotNull();
    assertThat(horizontalPodAutoscaler.getMetadata().getLabels().containsKey("app")).isTrue();
    assertThat(horizontalPodAutoscaler.getMetadata().getLabels().containsKey("version")).isTrue();
    assertThat(horizontalPodAutoscaler.getMetadata().getLabels().get("app")).isEqualTo("appName");
    assertThat(horizontalPodAutoscaler.getMetadata().getLabels().get("version")).isEqualTo("9");
    assertThat(horizontalPodAutoscaler.getSpec().getMinReplicas()).isEqualTo(Integer.valueOf(1));
    assertThat(horizontalPodAutoscaler.getSpec().getMaxReplicas()).isEqualTo(Integer.valueOf(2));

    setupParams = KubernetesSetupParamsBuilder
                      .aKubernetesSetupParams()
                      // empty customMetricConfigYaml, so use basic HPA
                      .withCustomMetricYamlConfig("")
                      .withMinAutoscaleInstances(2)
                      .withMaxAutoscaleInstances(3)
                      .withTargetCpuUtilizationPercentage(30)
                      .build();

    horizontalPodAutoscaler = kubernetesSetupCommandUnit.createAutoscaler("abaris.hpanormal.prod-0", "Deployment",
        "extensions/v1beta1", "default", labels, setupParams, executionLogCallback);

    assertThat(horizontalPodAutoscaler.getApiVersion()).isEqualTo("autoscaling/v1");
    assertThat(horizontalPodAutoscaler.getSpec()).isNotNull();
    assertThat(horizontalPodAutoscaler.getSpec().getScaleTargetRef().getName()).isEqualTo("abaris.hpanormal.prod-0");
    assertThat(horizontalPodAutoscaler.getSpec().getScaleTargetRef().getKind()).isEqualTo("Deployment");
    assertThat(horizontalPodAutoscaler.getMetadata()).isNotNull();
    assertThat(horizontalPodAutoscaler.getMetadata().getName()).isEqualTo("abaris.hpanormal.prod-0");
    assertThat(horizontalPodAutoscaler.getMetadata().getNamespace()).isEqualTo("default");
    assertThat(horizontalPodAutoscaler.getMetadata().getLabels()).isNotNull();
    assertThat(horizontalPodAutoscaler.getMetadata().getLabels().containsKey("app")).isTrue();
    assertThat(horizontalPodAutoscaler.getMetadata().getLabels().containsKey("version")).isTrue();
    assertThat(horizontalPodAutoscaler.getMetadata().getLabels().get("app")).isEqualTo("appName");
    assertThat(horizontalPodAutoscaler.getMetadata().getLabels().get("version")).isEqualTo("9");
    assertThat(horizontalPodAutoscaler.getSpec().getMinReplicas()).isEqualTo(Integer.valueOf(2));
    assertThat(horizontalPodAutoscaler.getSpec().getMaxReplicas()).isEqualTo(Integer.valueOf(3));
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void testBlueGreenValidationServiceNotAllowedWithBlueGreen() throws Exception {
    ExecutionLogCallback executionLogCallback = mock(ExecutionLogCallback.class);
    doNothing().when(executionLogCallback).saveExecutionLog(nullable(String.class), any());

    KubernetesSetupParams setupParams = KubernetesSetupParamsBuilder.aKubernetesSetupParams()
                                            .withBlueGreen(true)
                                            .withServiceType(KubernetesServiceType.ClusterIP)
                                            .build();

    try {
      kubernetesSetupCommandUnit.validateBlueGreenConfig(setupParams);
      fail("Exception expected");
    } catch (Exception e) {
      assertThat(e instanceof InvalidRequestException).isTrue();
    }
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void testBlueGreenValidationEmptyBlueGreenConfig() throws Exception {
    ExecutionLogCallback executionLogCallback = mock(ExecutionLogCallback.class);
    doNothing().when(executionLogCallback).saveExecutionLog(nullable(String.class), any());

    KubernetesBlueGreenConfig blueGreenConfig = new KubernetesBlueGreenConfig();
    setupParams = KubernetesSetupParamsBuilder.aKubernetesSetupParams()
                      .withBlueGreen(true)
                      .withBlueGreenConfig(blueGreenConfig)
                      .build();

    try {
      kubernetesSetupCommandUnit.validateBlueGreenConfig(setupParams);
      fail("Exception expected");
    } catch (Exception e) {
      assertThat(e instanceof InvalidRequestException).isTrue();
    }
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void testBlueGreenValidationServiceNonePrimaryService() throws Exception {
    ExecutionLogCallback executionLogCallback = mock(ExecutionLogCallback.class);
    doNothing().when(executionLogCallback).saveExecutionLog(nullable(String.class), any());

    KubernetesServiceSpecification serviceSpec = new KubernetesServiceSpecification();
    serviceSpec.setServiceType(KubernetesServiceType.None);
    KubernetesBlueGreenConfig blueGreenConfig = new KubernetesBlueGreenConfig();
    blueGreenConfig.setPrimaryService(serviceSpec);

    setupParams = KubernetesSetupParamsBuilder.aKubernetesSetupParams()
                      .withBlueGreen(true)
                      .withBlueGreenConfig(blueGreenConfig)
                      .build();

    try {
      kubernetesSetupCommandUnit.validateBlueGreenConfig(setupParams);
      fail("Exception expected");
    } catch (Exception e) {
      assertThat(e instanceof InvalidRequestException).isTrue();
    }
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void testBlueGreenValidationSmokeTest() throws Exception {
    ExecutionLogCallback executionLogCallback = mock(ExecutionLogCallback.class);
    doNothing().when(executionLogCallback).saveExecutionLog(nullable(String.class), any());

    KubernetesServiceSpecification serviceSpec = new KubernetesServiceSpecification();
    serviceSpec.setServiceType(KubernetesServiceType.ClusterIP);
    KubernetesBlueGreenConfig blueGreenConfig = new KubernetesBlueGreenConfig();
    blueGreenConfig.setPrimaryService(serviceSpec);
    blueGreenConfig.setStageService(serviceSpec);

    setupParams = KubernetesSetupParamsBuilder.aKubernetesSetupParams()
                      .withBlueGreen(true)
                      .withBlueGreenConfig(blueGreenConfig)
                      .build();
    kubernetesSetupCommandUnit.validateBlueGreenConfig(setupParams);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldExecuteWithDownsize() {
    String controllerName0 = KubernetesConvention.getControllerName(
        KubernetesConvention.getControllerNamePrefix("app-name", "service-name", "env-name"), 0);
    String controllerName1 = KubernetesConvention.getControllerName(
        KubernetesConvention.getControllerNamePrefix("app-name", "service-name", "env-name"), 1);
    String controllerName2 = KubernetesConvention.getControllerName(
        KubernetesConvention.getControllerNamePrefix("app-name", "service-name", "env-name"), 2);
    ReplicationController controller0 = new ReplicationControllerBuilder()
                                            .withNewMetadata()
                                            .withName(controllerName0)
                                            .withCreationTimestamp(new Date().toString())
                                            .endMetadata()
                                            .withNewSpec()
                                            .withReplicas(2)
                                            .endSpec()
                                            .build();
    ReplicationController controller1 = new ReplicationControllerBuilder()
                                            .withNewMetadata()
                                            .withName(controllerName1)
                                            .withCreationTimestamp(new Date().toString())
                                            .endMetadata()
                                            .withNewSpec()
                                            .withReplicas(4)
                                            .endSpec()
                                            .build();

    LinkedHashMap<String, Integer> active = new LinkedHashMap<>();
    active.put(controllerName0, 2);
    active.put(controllerName1, 4);

    when(kubernetesContainerService.listControllers(kubernetesConfig))
        .thenReturn((List) Arrays.asList(controller0, controller1));
    when(kubernetesContainerService.getActiveServiceCounts(kubernetesConfig, controllerName2)).thenReturn(active);
    when(kubernetesContainerService.getRunningPods(kubernetesConfig, controllerName0)).thenReturn(emptyList());

    CommandExecutionStatus status = kubernetesSetupCommandUnit.execute(context);

    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(gkeClusterService)
        .getCluster(nullable(software.wings.beans.dto.SettingAttribute.class), eq(emptyList()), nullable(String.class),
            nullable(String.class), nullable(Boolean.class));
    verify(kubernetesContainerService).createOrReplaceController(eq(kubernetesConfig), nullable(HasMetadata.class));

    verify(kubernetesContainerService)
        .setControllerPodCount(eq(kubernetesConfig), eq(setupParams.getClusterName()), eq(controllerName0), eq(2),
            eq(0), eq(setupParams.getServiceSteadyStateTimeout()), any());
    verify(kubernetesContainerService)
        .setControllerPodCount(eq(kubernetesConfig), eq(setupParams.getClusterName()), eq(controllerName1), eq(4),
            eq(3), eq(setupParams.getServiceSteadyStateTimeout()), any());
  }
}
