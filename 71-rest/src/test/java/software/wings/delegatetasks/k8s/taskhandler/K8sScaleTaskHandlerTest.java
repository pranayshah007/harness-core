package software.wings.delegatetasks.k8s.taskhandler;

import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.YOGESH;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.LONG_TIMEOUT_INTERVAL;
import static software.wings.utils.WingsTestConstants.NAMESPACE;

import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.InvalidArgumentsException;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import software.wings.WingsBaseTest;
import software.wings.beans.InstanceUnitType;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.k8s.K8sTaskHelper;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.request.K8sScaleTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskResponse;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class K8sScaleTaskHandlerTest extends WingsBaseTest {
  @Mock private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Mock private K8sTaskHelper k8sTaskHelper;
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;
  @InjectMocks private K8sScaleTaskHandler k8sScaleTaskHandler;

  private K8sScaleTaskParameters k8sScaleTaskParameters;
  private KubernetesConfig kubernetesConfig;
  private K8sDelegateTaskParams k8sDelegateTaskParams;
  private static final String kubeConfigNamespace = "kubeConfigNamespace";
  private static final String releaseName = "releaseName";
  private static final String workload = "deployment/workload";

  @Before
  public void setup() {
    k8sScaleTaskParameters = K8sScaleTaskParameters.builder()
                                 .accountId(ACCOUNT_ID)
                                 .instanceUnitType(InstanceUnitType.COUNT)
                                 .instances(1)
                                 .releaseName(releaseName)
                                 .workload(workload)
                                 .timeoutIntervalInMin(1)
                                 .build();
    kubernetesConfig = KubernetesConfig.builder().namespace(kubeConfigNamespace).accountId(ACCOUNT_ID).build();
    k8sDelegateTaskParams = K8sDelegateTaskParams.builder().build();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testExecuteForNamespaceFromKubeConfig() throws Exception {
    when(containerDeploymentDelegateHelper.getKubernetesConfig(any(K8sClusterConfig.class)))
        .thenReturn(kubernetesConfig);
    when(k8sTaskHelperBase.getPodDetailsFabric8(
             kubernetesConfig, kubeConfigNamespace, releaseName, LONG_TIMEOUT_INTERVAL))
        .thenReturn(null);
    when(k8sTaskHelperBase.scale(any(Kubectl.class), any(K8sDelegateTaskParams.class), any(KubernetesResourceId.class),
             anyInt(), any(ExecutionLogCallback.class)))
        .thenReturn(false);

    k8sScaleTaskHandler.executeTaskInternal(k8sScaleTaskParameters, k8sDelegateTaskParams);
    verify(k8sTaskHelperBase, times(1))
        .scale(any(Kubectl.class), any(K8sDelegateTaskParams.class), any(KubernetesResourceId.class), anyInt(),
            any(ExecutionLogCallback.class));
    ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(k8sTaskHelperBase, times(1))
        .getPodDetailsFabric8(
            any(KubernetesConfig.class), argumentCaptor.capture(), anyString(), eq(LONG_TIMEOUT_INTERVAL));
    assertThat(argumentCaptor.getValue()).isEqualTo(kubeConfigNamespace);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testExecuteForNamespaceFromWorkload() throws Exception {
    String namespace = "namespace";
    String namespacedWorkload = "namespace/deployment/workload";
    k8sScaleTaskParameters.setWorkload(namespacedWorkload);

    when(containerDeploymentDelegateHelper.getKubernetesConfig(any(K8sClusterConfig.class)))
        .thenReturn(kubernetesConfig);
    when(k8sTaskHelperBase.getPodDetailsFabric8(kubernetesConfig, namespace, releaseName, LONG_TIMEOUT_INTERVAL))
        .thenReturn(null);
    when(k8sTaskHelperBase.scale(any(Kubectl.class), any(K8sDelegateTaskParams.class), any(KubernetesResourceId.class),
             anyInt(), any(ExecutionLogCallback.class)))
        .thenReturn(false);

    k8sScaleTaskHandler.executeTaskInternal(k8sScaleTaskParameters, k8sDelegateTaskParams);
    verify(k8sTaskHelperBase, times(1))
        .scale(any(Kubectl.class), any(K8sDelegateTaskParams.class), any(KubernetesResourceId.class), anyInt(),
            any(ExecutionLogCallback.class));
    ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(k8sTaskHelperBase, times(1))
        .getPodDetailsFabric8(
            any(KubernetesConfig.class), argumentCaptor.capture(), anyString(), eq(LONG_TIMEOUT_INTERVAL));
    assertThat(argumentCaptor.getValue()).isEqualTo(namespace);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testTagNewPods() {
    assertThat(k8sScaleTaskHandler.tagNewPods(emptyList(), emptyList())).isEmpty();
    assertThat(k8sScaleTaskHandler.tagNewPods(emptyList(), null)).isEmpty();
    assertThat(k8sScaleTaskHandler.tagNewPods(null, null)).isEmpty();
    assertThat(k8sScaleTaskHandler.tagNewPods(null, emptyList())).isEmpty();

    List<K8sPod> pods = k8sScaleTaskHandler.tagNewPods(
        asList(podWithName("pod-1")), asList(podWithName("pod-1"), podWithName("pod-2")));

    assertThat(pods).hasSize(2);
    assertThat(pods.stream().filter(K8sPod::isNewPod).map(K8sPod::getName).collect(Collectors.toList()))
        .containsExactly("pod-2");

    pods = k8sScaleTaskHandler.tagNewPods(
        asList(podWithName("pod-1")), asList(podWithName("pod-2"), podWithName("pod-3")));

    assertThat(pods).hasSize(2);
    assertThat(pods.stream().filter(K8sPod::isNewPod).map(K8sPod::getName).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("pod-2", "pod-3");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void invalidTypeOfTaskParams() {
    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(() -> k8sScaleTaskHandler.executeTaskInternal(null, null))
        .withMessageContaining("INVALID_ARGUMENT");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void executeTaskInternalInstanceUnitPercentage() throws Exception {
    Reflect.on(k8sScaleTaskHandler).set("k8sTaskHelper", new K8sTaskHelper());
    when(containerDeploymentDelegateHelper.getKubernetesConfig(any(K8sClusterConfig.class)))
        .thenReturn(kubernetesConfig);
    k8sScaleTaskParameters.setDeprecateFabric8Enabled(true);
    k8sScaleTaskParameters.setInstanceUnitType(InstanceUnitType.PERCENTAGE);

    // max instances is not present
    K8sTaskExecutionResponse response =
        k8sScaleTaskHandler.executeTaskInternal(k8sScaleTaskParameters, k8sDelegateTaskParams);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    verify(k8sTaskHelperBase, times(1))
        .scale(any(Kubectl.class), any(K8sDelegateTaskParams.class), any(KubernetesResourceId.class), anyInt(),
            any(ExecutionLogCallback.class));
    verify(k8sTaskHelperBase, times(1))
        .getPodDetails(any(KubernetesConfig.class), anyString(), anyString(), eq(LONG_TIMEOUT_INTERVAL));

    // max instances is present
    k8sScaleTaskParameters.setMaxInstances(Optional.of(3));
    response = k8sScaleTaskHandler.executeTaskInternal(k8sScaleTaskParameters, k8sDelegateTaskParams);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    verify(k8sTaskHelperBase, times(2))
        .scale(any(Kubectl.class), any(K8sDelegateTaskParams.class), any(KubernetesResourceId.class), anyInt(),
            any(ExecutionLogCallback.class));
    verify(k8sTaskHelperBase, times(2))
        .getPodDetails(any(KubernetesConfig.class), anyString(), anyString(), eq(LONG_TIMEOUT_INTERVAL));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void skipSteadyStateCheckFail() throws Exception {
    when(containerDeploymentDelegateHelper.getKubernetesConfig(any(K8sClusterConfig.class)))
        .thenReturn(kubernetesConfig);
    when(k8sTaskHelperBase.scale(any(Kubectl.class), any(K8sDelegateTaskParams.class), any(KubernetesResourceId.class),
             anyInt(), any(ExecutionLogCallback.class)))
        .thenReturn(true);
    k8sScaleTaskHandler.executeTaskInternal(k8sScaleTaskParameters, k8sDelegateTaskParams);
    verify(containerDeploymentDelegateHelper, times(1)).getKubernetesConfig(any(K8sClusterConfig.class));
    verify(k8sTaskHelperBase, times(1))
        .scale(any(Kubectl.class), any(K8sDelegateTaskParams.class), any(KubernetesResourceId.class), anyInt(),
            any(ExecutionLogCallback.class));
    verify(k8sTaskHelperBase, times(1))
        .doStatusCheck(any(Kubectl.class), any(KubernetesResourceId.class), any(K8sDelegateTaskParams.class),
            any(ExecutionLogCallback.class));
    verify(k8sTaskHelper, times(1))
        .getK8sTaskExecutionResponse(any(K8sTaskResponse.class), any(CommandExecutionStatus.class));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void skipSteadyStateCheckSuccess() throws Exception {
    when(containerDeploymentDelegateHelper.getKubernetesConfig(any(K8sClusterConfig.class)))
        .thenReturn(kubernetesConfig);
    when(k8sTaskHelperBase.scale(any(Kubectl.class), any(K8sDelegateTaskParams.class), any(KubernetesResourceId.class),
             anyInt(), any(ExecutionLogCallback.class)))
        .thenReturn(true);
    when(k8sTaskHelperBase.doStatusCheck(any(Kubectl.class), any(KubernetesResourceId.class),
             any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class)))
        .thenReturn(true);
    k8sScaleTaskHandler.executeTaskInternal(k8sScaleTaskParameters, k8sDelegateTaskParams);
    verify(containerDeploymentDelegateHelper, times(1)).getKubernetesConfig(any(K8sClusterConfig.class));
    verify(k8sTaskHelperBase, times(1))
        .scale(any(Kubectl.class), any(K8sDelegateTaskParams.class), any(KubernetesResourceId.class), anyInt(),
            any(ExecutionLogCallback.class));
    verify(k8sTaskHelperBase, times(1))
        .doStatusCheck(any(Kubectl.class), any(KubernetesResourceId.class), any(K8sDelegateTaskParams.class),
            any(ExecutionLogCallback.class));

    verify(k8sTaskHelper, times(1))
        .getK8sTaskExecutionResponse(any(K8sTaskResponse.class), any(CommandExecutionStatus.class));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testNoWorkloadToScale() throws Exception {
    k8sScaleTaskParameters.setWorkload(null);

    doReturn(kubernetesConfig).when(containerDeploymentDelegateHelper).getKubernetesConfig(any(K8sClusterConfig.class));
    Reflect.on(k8sScaleTaskHandler).set("k8sTaskHelper", new K8sTaskHelper());
    K8sTaskExecutionResponse response =
        k8sScaleTaskHandler.executeTaskInternal(k8sScaleTaskParameters, k8sDelegateTaskParams);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    ExecutionLogCallback executionLogCallback = mock(ExecutionLogCallback.class);
    boolean success =
        k8sScaleTaskHandler.init(k8sScaleTaskParameters, k8sDelegateTaskParams, NAMESPACE, executionLogCallback);
    assertThat(success).isTrue();
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(executionLogCallback, times(2)).saveExecutionLog(captor.capture());
    assertThat(captor.getAllValues()).contains("\nNo Workload found to scale.");
    verify(executionLogCallback, times(1)).saveExecutionLog(captor.capture(), eq(INFO), eq(SUCCESS));
    assertThat(captor.getValue()).contains("\nDone.");
  }

  private K8sPod podWithName(String name) {
    return K8sPod.builder().name(name).build();
  }
}
