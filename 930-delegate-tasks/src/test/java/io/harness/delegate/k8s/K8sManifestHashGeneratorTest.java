/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.configuration.KubernetesCliCommandType.GENERATE_HASH;
import static io.harness.delegate.k8s.K8sManifestHashGenerator.MANIFEST_FOR_HASH;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.TARUN_UBA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.KubernetesCliTaskRuntimeException;
import io.harness.filesystem.FileIo;
import io.harness.k8s.ProcessResponse;
import io.harness.k8s.kubectl.CreateCommand;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.AccessLevel;
import lombok.SneakyThrows;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.zeroturnaround.exec.ProcessOutput;
import org.zeroturnaround.exec.ProcessResult;

@OwnedBy(CDP)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class K8sManifestHashGeneratorTest extends CategoryTest {
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;

  @InjectMocks K8sManifestHashGenerator k8sManifestHashGenerator;
  public static String DEPLOYMENT_DIRECT_APPLY_YAML = "apiVersion: apps/v1\n"
      + "kind: Deployment\n"
      + "metadata:\n"
      + "  labels:\n"
      + "    app: nginx\n"
      + "  name: deployment\n"
      + "  namespace: default\n"
      + "spec:\n"
      + "  replicas: 3\n"
      + "  selector:\n"
      + "    matchLabels:\n"
      + "      app: nginx\n"
      + "  template:\n"
      + "    metadata:\n"
      + "      labels:\n"
      + "        app: nginx\n"
      + "    spec:\n"
      + "      containers:\n"
      + "      - image: nginx:1.7.9\n"
      + "        name: nginx\n"
      + "        ports:\n"
      + "        - containerPort: 80";

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGeneratedHash() throws Exception {
    assertThat(k8sManifestHashGenerator.generatedHash(DEPLOYMENT_DIRECT_APPLY_YAML))
        .isEqualTo("ab9ef5a0ea6784e8411df3f4298c90659e670a78");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  @SneakyThrows
  public void testManifestHash() {
    final List<KubernetesResource> kubernetesResources = List.of(
        KubernetesResource.builder()
            .spec("apiVersion: autoscaling/v1\n"
                + "kind: HorizontalPodAutoscaler\n"
                + "metadata:\n"
                + "  name: php-apache\n"
                + "spec:\n"
                + "  scaleTargetRef:\n"
                + "    apiVersion: apps/v1\n"
                + "    kind: Deployment\n"
                + "    name: php-apache\n"
                + "    subresource: scale\n"
                + "  maxReplicas: 1\n"
                + "  minReplicas: 1")
            .resourceId(KubernetesResourceId.builder().kind("HorizontalPodAutoscaler").name("php-apache").build())
            .build(),
        KubernetesResource.builder()
            .spec("apiVersion: apps/v1\n"
                + "kind: Deployment\n"
                + "metadata:\n"
                + "  name: nginx-deployment\n"
                + "  labels:\n"
                + "    app: nginx\n"
                + "spec:\n"
                + "  replicas: 3\n"
                + "  selector:\n"
                + "    matchLabels:\n"
                + "      app: nginx\n"
                + "  template:\n"
                + "    metadata:\n"
                + "      labels:\n"
                + "        app: nginx\n"
                + "    spec:\n"
                + "      containers:\n"
                + "      - name: nginx\n"
                + "        image: nginx:1.14.2\n"
                + "        ports:\n"
                + "        - containerPort: 80")
            .resourceId(KubernetesResourceId.builder().kind("Deployment").name("nginx-deployment").build())
            .build());

    runInTempDirectory(workingDir -> {
      final K8sDelegateTaskParams taskParams =
          K8sDelegateTaskParams.builder().workingDirectory(workingDir.toString()).build();
      final Kubectl client = mock(Kubectl.class);
      final Kubectl overriddenClient = mock(Kubectl.class);
      final CreateCommand createCommand = mock(CreateCommand.class);

      doReturn(overriddenClient).when(k8sTaskHelperBase).getOverriddenClient(client, kubernetesResources, taskParams);
      doReturn(createCommand).when(overriddenClient).create(MANIFEST_FOR_HASH);
      doReturn(ProcessResponse.builder()
                   .processResult(new ProcessResult(
                       0, new ProcessOutput(DEPLOYMENT_DIRECT_APPLY_YAML.getBytes(StandardCharsets.UTF_8))))
                   .build())
          .when(k8sTaskHelperBase)
          .runK8sExecutableSilentlyWithErrorCapture(
              eq(createCommand), eq(taskParams), any(LogCallback.class), eq(LogLevel.ERROR));

      String hash =
          k8sManifestHashGenerator.manifestHash(kubernetesResources, taskParams, mock(LogCallback.class), client);
      assertThat(hash).isEqualTo("ab9ef5a0ea6784e8411df3f4298c90659e670a78");
      assertThat(Files.list(workingDir))
          .isEmpty(); // we should clean up the manifest hash directory after hash has been generated
    });
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  @SneakyThrows
  public void testManifestHashFailed() {
    final List<KubernetesResource> empty = List.of();
    runInTempDirectory(workingDir -> {
      final K8sDelegateTaskParams taskParams =
          K8sDelegateTaskParams.builder().workingDirectory(workingDir.toString()).build();
      final Kubectl client = mock(Kubectl.class);
      final CreateCommand createCommand = mock(CreateCommand.class);

      doReturn(client).when(k8sTaskHelperBase).getOverriddenClient(client, empty, taskParams);
      doReturn(createCommand).when(client).create(MANIFEST_FOR_HASH);
      doReturn(ProcessResponse.builder()
                   .processResult(
                       new ProcessResult(1, new ProcessOutput("Something went wrong".getBytes(StandardCharsets.UTF_8))))
                   .build())
          .when(k8sTaskHelperBase)
          .runK8sExecutableSilentlyWithErrorCapture(
              eq(createCommand), eq(taskParams), any(LogCallback.class), eq(LogLevel.ERROR));
      assertThatThrownBy(
          () -> k8sManifestHashGenerator.manifestHash(empty, taskParams, mock(LogCallback.class), client))
          .isInstanceOf(KubernetesCliTaskRuntimeException.class)
          .matches(e -> {
            KubernetesCliTaskRuntimeException cliTaskRuntimeException = (KubernetesCliTaskRuntimeException) e;
            assertThat(cliTaskRuntimeException.getCommandType()).isEqualTo(GENERATE_HASH);
            assertThat(cliTaskRuntimeException.getProcessResponse().getProcessResult().getExitValue()).isEqualTo(1);
            return true;
          });
      assertThat(Files.list(workingDir)).isEmpty();
    });
  }

  private void runInTempDirectory(ThrowingConsumer<Path> consumer) throws Exception {
    final Path tempWorkingDir = Files.createTempDirectory("K8sManifestHashGeneratorTest");
    try {
      consumer.accept(tempWorkingDir);
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(tempWorkingDir.toString());
    }
  }

  @FunctionalInterface
  private interface ThrowingConsumer<T> {
    void accept(T t) throws Exception;
  }
}
