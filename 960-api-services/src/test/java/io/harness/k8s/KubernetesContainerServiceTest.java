/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s;

import static io.harness.k8s.KubernetesContainerServiceImpl.METRICS_SERVER_ABSENT;
import static io.harness.rule.OwnerRule.UTSAV;
import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.apiclient.ApiClientFactoryImpl;
import io.harness.k8s.config.K8sGlobalConfigService;
import io.harness.k8s.kubectl.GetCommand;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.Kind;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.rule.Owner;

import com.google.common.util.concurrent.TimeLimiter;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1SubjectAccessReviewStatus;
import java.io.OutputStream;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.zeroturnaround.exec.ProcessResult;

@OwnedBy(HarnessTeam.CDP)
public class KubernetesContainerServiceTest extends CategoryTest {
  private static final KubernetesConfig KUBERNETES_CONFIG = KubernetesConfig.builder().namespace("default").build();

  @Mock private KubernetesHelperService kubernetesHelperService;
  @Mock private TimeLimiter timeLimiter;
  @Mock private Clock clock;
  @Mock private K8sGlobalConfigService k8sGlobalConfigService;
  @Mock private K8sResourceValidatorImpl k8sResourceValidator;

  @InjectMocks
  private KubernetesContainerServiceImpl kubernetesContainerService = spy(new KubernetesContainerServiceImpl());
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Rule public KubernetesServer server = new KubernetesServer(true, true);

  private KubernetesClient client;
  private ApiClient apiClient;

  List<V1SubjectAccessReviewStatus> response;

  private final String RESULT = "watch not granted on pods.apps, ";

  @Before
  public void setUp() {
    client = server.getClient();
    when(kubernetesHelperService.getKubernetesClient(KUBERNETES_CONFIG)).thenReturn(client);
    apiClient = ApiClientFactoryImpl.fromKubernetesConfig(KUBERNETES_CONFIG, null);

    response = Arrays.asList(new V1SubjectAccessReviewStatus());
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldValidateWithCEMetricsServerAbsent() throws ApiException {
    doReturn(false).when(k8sResourceValidator).validateMetricsServer(any(ApiClient.class));

    assertThatThrownBy(() -> kubernetesContainerService.validateCEPermissions(KUBERNETES_CONFIG))
        .isExactlyInstanceOf(InvalidRequestException.class)
        .hasMessage(METRICS_SERVER_ABSENT);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldValidateWithCEMetricsServerPresent() throws ApiException {
    doReturn(true).when(k8sResourceValidator).validateMetricsServer(any());
    doNothing().when(kubernetesContainerService).validateCEResourcePermission(any());

    assertThatCode(() -> kubernetesContainerService.validateCEPermissions(KUBERNETES_CONFIG))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldValidateWithCEAllPermissionGranted() throws ApiException {
    doReturn(true).when(k8sResourceValidator).validateMetricsServer(any(ApiClient.class));
    doReturn("").when(k8sResourceValidator).validateCEPermissions(any(ApiClient.class));

    assertThatCode(() -> kubernetesContainerService.validateCEResourcePermission(apiClient)).doesNotThrowAnyException();

    verify(k8sResourceValidator, times(1)).validateCEPermissions(any(ApiClient.class));
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldValidateWithCEOnePermissionNotGranted() throws ApiException {
    doReturn(true).when(k8sResourceValidator).validateMetricsServer(any(ApiClient.class));
    doReturn(RESULT).when(k8sResourceValidator).validateCEPermissions(any(ApiClient.class));

    assertThatThrownBy(() -> kubernetesContainerService.validateCEResourcePermission(mock(ApiClient.class)))
        .hasMessageContaining(RESULT);

    verify(k8sResourceValidator, times(1)).validateCEPermissions(any(ApiClient.class));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void validate() throws Exception {
    validateIfCanGetReplicaSets();
    validateIfCanGetStatefulSets();
    validateIfCanGetDaemonSets();
    validateIfCanGetDeployments();
    throwIfCannotGetAnyWorkloadKind();
  }

  private void throwIfCannotGetAnyWorkloadKind() throws Exception {
    final Kubectl client = Kubectl.client("kubectl", "kubeconfig");
    Kubectl mockClient = Mockito.spy(client);
    GetCommand mockGetCommand = Mockito.spy(client.get());
    doReturn(mockClient).when(kubernetesContainerService).getKubectlClient(anyBoolean());
    doReturn(mockGetCommand).when(mockClient).get();
    doReturn(new ProcessResult(1, null))
        .when(mockGetCommand)
        .execute(anyString(), any(), any(OutputStream.class), anyBoolean(), any());
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().namespace("harness").build();

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> kubernetesContainerService.validate(kubernetesConfig, true));
  }

  private void validateIfCanGetDeployments() throws Exception {
    final Kubectl client = Kubectl.client("kubectl", "kubeconfig");
    Kubectl mockClient = Mockito.spy(client);
    GetCommand mockGetCommand = Mockito.spy(client.get());
    doReturn(mockClient).when(kubernetesContainerService).getKubectlClient(anyBoolean());
    doReturn(mockGetCommand).when(mockClient).get();
    List<String> executeCommands = new ArrayList<>();
    setupGetCommand(mockGetCommand, executeCommands, Kind.Deployment);
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().namespace("harness").build();

    kubernetesContainerService.validate(kubernetesConfig, true);

    assertThat(executeCommands).contains("kubectl --kubeconfig=kubeconfig get StatefulSet --namespace=harness");
    assertThat(executeCommands).contains("kubectl --kubeconfig=kubeconfig get ReplicaSet --namespace=harness");
    assertThat(executeCommands).contains("kubectl --kubeconfig=kubeconfig get DaemonSet --namespace=harness");
    assertThat(executeCommands).contains("kubectl --kubeconfig=kubeconfig get Deployment --namespace=harness");
  }

  private void validateIfCanGetDaemonSets() throws Exception {
    final Kubectl client = Kubectl.client("kubectl", "kubeconfig");
    Kubectl mockClient = Mockito.spy(client);
    GetCommand mockGetCommand = Mockito.spy(client.get());
    doReturn(mockClient).when(kubernetesContainerService).getKubectlClient(anyBoolean());
    doReturn(mockGetCommand).when(mockClient).get();
    List<String> executeCommands = new ArrayList<>();
    setupGetCommand(mockGetCommand, executeCommands, Kind.DaemonSet);
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().namespace("harness").build();

    kubernetesContainerService.validate(kubernetesConfig, true);

    assertThat(executeCommands).contains("kubectl --kubeconfig=kubeconfig get StatefulSet --namespace=harness");
    assertThat(executeCommands).contains("kubectl --kubeconfig=kubeconfig get ReplicaSet --namespace=harness");
    assertThat(executeCommands).contains("kubectl --kubeconfig=kubeconfig get DaemonSet --namespace=harness");
  }

  private void validateIfCanGetStatefulSets() throws Exception {
    final Kubectl client = Kubectl.client("kubectl", "kubeconfig");
    Kubectl mockClient = Mockito.spy(client);
    GetCommand mockGetCommand = Mockito.spy(client.get());
    doReturn(mockClient).when(kubernetesContainerService).getKubectlClient(anyBoolean());
    doReturn(mockGetCommand).when(mockClient).get();
    List<String> executeCommands = new ArrayList<>();
    setupGetCommand(mockGetCommand, executeCommands, Kind.StatefulSet);
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().namespace("harness").build();

    kubernetesContainerService.validate(kubernetesConfig, true);

    assertThat(executeCommands).contains("kubectl --kubeconfig=kubeconfig get StatefulSet --namespace=harness");
    assertThat(executeCommands).contains("kubectl --kubeconfig=kubeconfig get ReplicaSet --namespace=harness");
  }

  private void validateIfCanGetReplicaSets() throws Exception {
    final Kubectl client = Kubectl.client("kubectl", "kubeconfig");
    Kubectl mockClient = Mockito.spy(client);
    GetCommand mockGetCommand = Mockito.spy(client.get());
    doReturn(mockClient).when(kubernetesContainerService).getKubectlClient(anyBoolean());
    doReturn(mockGetCommand).when(mockClient).get();
    List<String> executeCommands = new ArrayList<>();
    setupGetCommand(mockGetCommand, executeCommands, Kind.ReplicaSet);
    doReturn(new ProcessResult(0, null)).when(mockGetCommand).execute(anyString(), any(), any(), anyBoolean(), any());
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().namespace("harness").build();

    kubernetesContainerService.validate(kubernetesConfig, true);

    assertThat(mockGetCommand.command())
        .isEqualTo("kubectl --kubeconfig=kubeconfig get ReplicaSet --namespace=harness");
  }

  private void setupGetCommand(GetCommand mockGetCommand, List<String> executeCommands, Kind kind) throws Exception {
    doAnswer(invocation -> {
      String command = mockGetCommand.command();
      executeCommands.add(command);
      if (command.contains(kind.name())) {
        return new ProcessResult(0, null);
      }
      return new ProcessResult(1, null);
    })
        .when(mockGetCommand)
        .execute(any(), any(), any(), anyBoolean(), any());
  }
}
