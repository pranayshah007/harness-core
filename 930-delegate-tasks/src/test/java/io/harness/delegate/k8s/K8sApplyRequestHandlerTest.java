/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.K8sConstants.MANIFEST_FILES_DIR;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ACASIAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.k8s.beans.K8sApplyHandlerConfig;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.K8sApplyRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.ManifestDelegateConfig;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.KubernetesTaskException;
import io.harness.k8s.exception.KubernetesExceptionExplanation;
import io.harness.k8s.exception.KubernetesExceptionHints;
import io.harness.k8s.exception.KubernetesExceptionMessages;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class K8sApplyRequestHandlerTest extends CategoryTest {
  @Mock private K8sApplyBaseHandler k8sApplyBaseHandler;
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;
  @Mock private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;

  @InjectMocks private K8sApplyRequestHandler requestHandler;
  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock private LogCallback logCallback;
  @Mock private K8sInfraDelegateConfig k8sInfraDelegateConfig;
  @Mock private ManifestDelegateConfig manifestDelegateConfig;

  private final Integer timeoutIntervalInMin = 10;
  private final long timeoutIntervalInMillis = 60 * timeoutIntervalInMin * 1000;
  private final String accountId = "accountId";
  private final String namespace = "default";
  private final String releaseName = "test-release";
  private final KubernetesConfig kubernetesConfig = KubernetesConfig.builder().namespace(namespace).build();
  private final String workingDirectory = "manifest";
  private final String manifestFileDirectory = Paths.get(workingDirectory, MANIFEST_FILES_DIR).toString();
  private CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
  private K8sApplyHandlerConfig k8sApplyHandlerConfig;
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doReturn(kubernetesConfig)
        .when(containerDeploymentDelegateBaseHelper)
        .createKubernetesConfig(k8sInfraDelegateConfig, workingDirectory, logCallback);
    doReturn(logCallback)
        .when(k8sTaskHelperBase)
        .getLogCallback(eq(iLogStreamingTaskClient), anyString(), anyBoolean(), any());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .fetchManifestFilesAndWriteToDirectory(
            manifestDelegateConfig, manifestFileDirectory, logCallback, timeoutIntervalInMillis, accountId);
    doReturn(namespace).when(k8sInfraDelegateConfig).getNamespace();
    k8sApplyHandlerConfig = requestHandler.getK8sApplyHandlerConfig();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldApplyManifestsSuccess() throws Exception {
    List<String> valuesList = Collections.emptyList();
    List<String> filePaths = Arrays.asList("deploy.yaml");
    K8sApplyRequest applyRequest = K8sApplyRequest.builder()
                                       .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                       .manifestDelegateConfig(manifestDelegateConfig)
                                       .accountId(accountId)
                                       .releaseName(releaseName)
                                       .filePaths(filePaths)
                                       .timeoutIntervalInMin(timeoutIntervalInMin)
                                       .valuesYamlList(valuesList)
                                       .commandName("K8s Apply")
                                       .skipDryRun(true)
                                       .skipSteadyStateCheck(false)
                                       .build();

    K8sDelegateTaskParams delegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).build();

    List<KubernetesResource> resources = Arrays.asList(
        KubernetesResource.builder()
            .spec("spec")
            .resourceId(
                KubernetesResourceId.builder().namespace(namespace).kind("deployment").name("test-deployment").build())
            .build());
    doReturn(resources)
        .when(k8sTaskHelperBase)
        .getResourcesFromManifests(delegateTaskParams, manifestDelegateConfig, manifestFileDirectory, filePaths,
            valuesList, releaseName, namespace, logCallback, timeoutIntervalInMin, false);
    doReturn(true).when(k8sApplyBaseHandler).prepare(logCallback, false, k8sApplyHandlerConfig, true);
    doReturn(true)
        .when(k8sTaskHelperBase)
        .applyManifests(any(Kubectl.class), eq(resources), eq(delegateTaskParams), eq(logCallback), eq(true), eq(true),
            anyString());
    doReturn(true)
        .when(k8sApplyBaseHandler)
        .steadyStateCheck(false, namespace, delegateTaskParams, timeoutIntervalInMillis, logCallback,
            k8sApplyHandlerConfig, true, true, false);

    K8sDeployResponse response = requestHandler.executeTaskInternal(
        applyRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);

    verify(k8sTaskHelperBase, times(1))
        .getResourcesFromManifests(eq(delegateTaskParams), eq(manifestDelegateConfig), eq(manifestFileDirectory),
            eq(filePaths), eq(valuesList), eq(releaseName), eq(namespace), eq(logCallback), eq(timeoutIntervalInMin),
            eq(false));
    verify(k8sApplyBaseHandler, times(1)).prepare(eq(logCallback), eq(false), eq(k8sApplyHandlerConfig), eq(true));
    verify(k8sTaskHelperBase, times(1))
        .applyManifests(any(Kubectl.class), eq(resources), eq(delegateTaskParams), eq(logCallback), eq(true), eq(true),
            anyString());
    verify(k8sApplyBaseHandler, times(1))
        .steadyStateCheck(false, namespace, delegateTaskParams, timeoutIntervalInMillis, logCallback,
            k8sApplyHandlerConfig, true, true, false);
    verify(k8sApplyBaseHandler, times(1)).wrapUp(eq(delegateTaskParams), eq(logCallback), any(Kubectl.class));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldApplyManifestsSkipDryRunSuccess() throws Exception {
    List<String> valuesList = Collections.emptyList();
    List<String> filePaths = Arrays.asList("deploy.yaml");
    K8sApplyRequest applyRequest = K8sApplyRequest.builder()
                                       .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                       .manifestDelegateConfig(manifestDelegateConfig)
                                       .accountId(accountId)
                                       .releaseName(releaseName)
                                       .filePaths(filePaths)
                                       .timeoutIntervalInMin(timeoutIntervalInMin)
                                       .valuesYamlList(valuesList)
                                       .commandName("K8s Apply")
                                       .skipDryRun(false)
                                       .skipSteadyStateCheck(false)
                                       .build();

    K8sDelegateTaskParams delegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).build();

    List<KubernetesResource> resources = Arrays.asList(
        KubernetesResource.builder()
            .spec("spec")
            .resourceId(
                KubernetesResourceId.builder().namespace(namespace).kind("deployment").name("test-deployment").build())
            .build());
    doReturn(resources)
        .when(k8sTaskHelperBase)
        .getResourcesFromManifests(delegateTaskParams, manifestDelegateConfig, manifestFileDirectory, filePaths,
            valuesList, releaseName, namespace, logCallback, timeoutIntervalInMin, false);
    doReturn(true)
        .when(k8sTaskHelperBase)
        .dryRunManifests(any(Kubectl.class), eq(resources), eq(delegateTaskParams), eq(logCallback), eq(true));
    doReturn(true).when(k8sApplyBaseHandler).prepare(logCallback, false, k8sApplyHandlerConfig, true);
    doReturn(true)
        .when(k8sTaskHelperBase)
        .applyManifests(any(Kubectl.class), eq(resources), eq(delegateTaskParams), eq(logCallback), eq(true), eq(true),
            anyString());
    doReturn(true)
        .when(k8sApplyBaseHandler)
        .steadyStateCheck(false, namespace, delegateTaskParams, timeoutIntervalInMillis, logCallback,
            k8sApplyHandlerConfig, true, true, false);

    K8sDeployResponse response = requestHandler.executeTaskInternal(
        applyRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);

    verify(k8sTaskHelperBase, times(1))
        .getResourcesFromManifests(eq(delegateTaskParams), eq(manifestDelegateConfig), eq(manifestFileDirectory),
            eq(filePaths), eq(valuesList), eq(releaseName), eq(namespace), eq(logCallback), eq(timeoutIntervalInMin),
            eq(false));
    verify(k8sTaskHelperBase, times(1))
        .dryRunManifests(any(Kubectl.class), eq(resources), eq(delegateTaskParams), eq(logCallback), eq(true));
    verify(k8sApplyBaseHandler, times(1)).prepare(eq(logCallback), eq(false), eq(k8sApplyHandlerConfig), eq(true));
    verify(k8sTaskHelperBase, times(1))
        .applyManifests(any(Kubectl.class), eq(resources), eq(delegateTaskParams), eq(logCallback), eq(true), eq(true),
            anyString());
    verify(k8sApplyBaseHandler, times(1))
        .steadyStateCheck(false, namespace, delegateTaskParams, timeoutIntervalInMillis, logCallback,
            k8sApplyHandlerConfig, true, true, false);
    verify(k8sApplyBaseHandler, times(1)).wrapUp(eq(delegateTaskParams), eq(logCallback), any(Kubectl.class));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldApplyManifestsSkipDryRunFailureException() throws Exception {
    List<String> valuesList = Collections.emptyList();
    List<String> filePaths = Arrays.asList("deploy.yaml");
    K8sApplyRequest applyRequest = K8sApplyRequest.builder()
                                       .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                       .manifestDelegateConfig(manifestDelegateConfig)
                                       .accountId(accountId)
                                       .releaseName(releaseName)
                                       .filePaths(filePaths)
                                       .timeoutIntervalInMin(timeoutIntervalInMin)
                                       .valuesYamlList(valuesList)
                                       .commandName("K8s Apply")
                                       .skipDryRun(false)
                                       .skipSteadyStateCheck(false)
                                       .build();

    K8sDelegateTaskParams delegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).build();

    List<KubernetesResource> resources = Arrays.asList(
        KubernetesResource.builder()
            .spec("spec")
            .resourceId(
                KubernetesResourceId.builder().namespace(namespace).kind("deployment").name("test-deployment").build())
            .build());
    doReturn(resources)
        .when(k8sTaskHelperBase)
        .getResourcesFromManifests(delegateTaskParams, manifestDelegateConfig, manifestFileDirectory, filePaths,
            valuesList, releaseName, namespace, logCallback, timeoutIntervalInMin, false);
    RuntimeException exception = new RuntimeException("Dry run manifest failed");
    doThrow(exception)
        .when(k8sTaskHelperBase)
        .dryRunManifests(any(Kubectl.class), eq(resources), eq(delegateTaskParams), eq(logCallback), eq(true));

    assertThatThrownBy(()
                           -> requestHandler.executeTaskInternal(
                               applyRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress))
        .isSameAs(exception);

    verify(k8sTaskHelperBase, times(1))
        .getResourcesFromManifests(eq(delegateTaskParams), eq(manifestDelegateConfig), eq(manifestFileDirectory),
            eq(filePaths), eq(valuesList), eq(releaseName), eq(namespace), eq(logCallback), eq(timeoutIntervalInMin),
            eq(false));
    verify(k8sTaskHelperBase, times(1))
        .dryRunManifests(any(Kubectl.class), eq(resources), eq(delegateTaskParams), eq(logCallback), eq(true));
    verify(k8sApplyBaseHandler, times(0)).prepare(eq(logCallback), eq(false), eq(k8sApplyHandlerConfig), eq(true));
    verify(k8sTaskHelperBase, times(0))
        .applyManifests(
            any(Kubectl.class), eq(resources), eq(delegateTaskParams), eq(logCallback), eq(true), eq(true), eq(null));
    verify(k8sApplyBaseHandler, times(0))
        .steadyStateCheck(false, namespace, delegateTaskParams, timeoutIntervalInMillis, logCallback,
            k8sApplyHandlerConfig, true, false, false);
    verify(k8sApplyBaseHandler, times(0)).wrapUp(eq(delegateTaskParams), eq(logCallback), any(Kubectl.class));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldNotApplyManifestsWhenGetResourcesThrowsException() throws Exception {
    List<String> valuesList = Collections.emptyList();
    List<String> filePaths = Arrays.asList("deploy.yaml");
    K8sApplyRequest applyRequest = K8sApplyRequest.builder()
                                       .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                       .manifestDelegateConfig(manifestDelegateConfig)
                                       .accountId(accountId)
                                       .releaseName(releaseName)
                                       .filePaths(filePaths)
                                       .timeoutIntervalInMin(timeoutIntervalInMin)
                                       .valuesYamlList(valuesList)
                                       .commandName("K8s Apply")
                                       .skipDryRun(false)
                                       .skipSteadyStateCheck(false)
                                       .build();

    K8sDelegateTaskParams delegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).build();

    List<KubernetesResource> resources = Arrays.asList(
        KubernetesResource.builder()
            .spec("spec")
            .resourceId(
                KubernetesResourceId.builder().namespace(namespace).kind("deployment").name("test-deployment").build())
            .build());
    RuntimeException exception = new RuntimeException("Get resources from manifests failed");
    doThrow(exception)
        .when(k8sTaskHelperBase)
        .getResourcesFromManifests(delegateTaskParams, manifestDelegateConfig, manifestFileDirectory, filePaths,
            valuesList, releaseName, namespace, logCallback, timeoutIntervalInMin, false);

    assertThatThrownBy(()
                           -> requestHandler.executeTaskInternal(
                               applyRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress))
        .isSameAs(exception);

    verify(k8sTaskHelperBase, times(1))
        .getResourcesFromManifests(eq(delegateTaskParams), eq(manifestDelegateConfig), eq(manifestFileDirectory),
            eq(filePaths), eq(valuesList), eq(releaseName), eq(namespace), eq(logCallback), eq(timeoutIntervalInMin),
            eq(false));
    verify(k8sTaskHelperBase, times(0))
        .dryRunManifests(any(Kubectl.class), eq(resources), eq(delegateTaskParams), eq(logCallback), eq(true));
    verify(k8sApplyBaseHandler, times(0)).prepare(eq(logCallback), eq(false), eq(k8sApplyHandlerConfig), eq(true));
    verify(k8sTaskHelperBase, times(0))
        .applyManifests(
            any(Kubectl.class), eq(resources), eq(delegateTaskParams), eq(logCallback), eq(true), eq(true), eq(null));
    verify(k8sApplyBaseHandler, times(0))
        .steadyStateCheck(false, namespace, delegateTaskParams, timeoutIntervalInMillis, logCallback,
            k8sApplyHandlerConfig, true, false, false);
    verify(k8sApplyBaseHandler, times(0)).wrapUp(eq(delegateTaskParams), eq(logCallback), any(Kubectl.class));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldNotApplyManifestsIfFilePathIsEmpty() throws Exception {
    List<String> valuesList = Collections.emptyList();
    List<String> filePaths = Collections.emptyList();
    K8sApplyRequest applyRequest = K8sApplyRequest.builder()
                                       .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                       .manifestDelegateConfig(manifestDelegateConfig)
                                       .accountId(accountId)
                                       .releaseName(releaseName)
                                       .filePaths(filePaths)
                                       .timeoutIntervalInMin(timeoutIntervalInMin)
                                       .valuesYamlList(valuesList)
                                       .commandName("K8s Apply")
                                       .skipDryRun(true)
                                       .skipSteadyStateCheck(false)
                                       .build();

    K8sDelegateTaskParams delegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).build();

    assertThatThrownBy(()
                           -> requestHandler.executeTaskInternal(
                               applyRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress))
        .matches(throwable -> {
          HintException hint = ExceptionUtils.cause(HintException.class, throwable);
          ExplanationException explanation = ExceptionUtils.cause(ExplanationException.class, throwable);
          KubernetesTaskException taskException = ExceptionUtils.cause(KubernetesTaskException.class, throwable);
          assertThat(hint).hasMessageContaining(KubernetesExceptionHints.APPLY_NO_FILEPATH_SPECIFIED);
          assertThat(explanation).hasMessageContaining(KubernetesExceptionExplanation.APPLY_NO_FILEPATH_SPECIFIED);
          assertThat(taskException).hasMessageContaining(KubernetesExceptionMessages.APPLY_NO_FILEPATH_SPECIFIED);
          return true;
        });

    verify(k8sTaskHelperBase, times(0))
        .getResourcesFromManifests(eq(delegateTaskParams), eq(manifestDelegateConfig), eq(manifestFileDirectory),
            eq(filePaths), eq(valuesList), eq(releaseName), eq(namespace), eq(logCallback), eq(timeoutIntervalInMin),
            eq(false));
    verify(k8sApplyBaseHandler, times(0)).prepare(eq(logCallback), eq(false), eq(k8sApplyHandlerConfig));
    verify(k8sApplyBaseHandler, times(0))
        .steadyStateCheck(false, namespace, delegateTaskParams, timeoutIntervalInMillis, logCallback,
            k8sApplyHandlerConfig, true, false, false);
    verify(k8sApplyBaseHandler, times(0)).wrapUp(eq(delegateTaskParams), eq(logCallback), any(Kubectl.class));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldNotApplyIfFailToPrepare() throws Exception {
    List<String> valuesList = Collections.emptyList();
    List<String> filePaths = Arrays.asList("deploy.yaml");
    K8sApplyRequest applyRequest = K8sApplyRequest.builder()
                                       .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                       .manifestDelegateConfig(manifestDelegateConfig)
                                       .accountId(accountId)
                                       .releaseName(releaseName)
                                       .filePaths(filePaths)
                                       .timeoutIntervalInMin(timeoutIntervalInMin)
                                       .valuesYamlList(valuesList)
                                       .commandName("K8s Apply")
                                       .skipDryRun(true)
                                       .skipSteadyStateCheck(false)
                                       .build();

    K8sDelegateTaskParams delegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).build();

    List<KubernetesResource> resources = Arrays.asList(
        KubernetesResource.builder()
            .spec("spec")
            .resourceId(
                KubernetesResourceId.builder().namespace(namespace).kind("deployment").name("test-deployment").build())
            .build());
    doReturn(resources)
        .when(k8sTaskHelperBase)
        .getResourcesFromManifests(delegateTaskParams, manifestDelegateConfig, manifestFileDirectory, filePaths,
            valuesList, releaseName, namespace, logCallback, timeoutIntervalInMin, false);
    RuntimeException exception = new RuntimeException("Failed to get the resources in table format");
    doThrow(exception).when(k8sApplyBaseHandler).prepare(logCallback, false, k8sApplyHandlerConfig, true);

    assertThatThrownBy(()
                           -> requestHandler.executeTaskInternal(
                               applyRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress))
        .isSameAs(exception);

    verify(k8sTaskHelperBase, times(1))
        .getResourcesFromManifests(eq(delegateTaskParams), eq(manifestDelegateConfig), eq(manifestFileDirectory),
            eq(filePaths), eq(valuesList), eq(releaseName), eq(namespace), eq(logCallback), eq(timeoutIntervalInMin),
            eq(false));
    verify(k8sApplyBaseHandler, times(1)).prepare(eq(logCallback), eq(false), eq(k8sApplyHandlerConfig), eq(true));
    verify(k8sTaskHelperBase, times(0))
        .applyManifests(any(Kubectl.class), eq(resources), eq(delegateTaskParams), eq(logCallback), eq(true), eq(null));
    verify(k8sApplyBaseHandler, times(0))
        .steadyStateCheck(false, namespace, delegateTaskParams, timeoutIntervalInMillis, logCallback,
            k8sApplyHandlerConfig, true, false, true);
    verify(k8sApplyBaseHandler, times(0)).wrapUp(eq(delegateTaskParams), eq(logCallback), any(Kubectl.class));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldFailIfApplyManifestsFails() throws Exception {
    List<String> valuesList = Collections.emptyList();
    List<String> filePaths = Arrays.asList("deploy.yaml");
    K8sApplyRequest applyRequest = K8sApplyRequest.builder()
                                       .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                       .manifestDelegateConfig(manifestDelegateConfig)
                                       .accountId(accountId)
                                       .releaseName(releaseName)
                                       .filePaths(filePaths)
                                       .timeoutIntervalInMin(timeoutIntervalInMin)
                                       .valuesYamlList(valuesList)
                                       .commandName("K8s Apply")
                                       .skipDryRun(true)
                                       .skipSteadyStateCheck(false)
                                       .build();

    K8sDelegateTaskParams delegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).build();

    List<KubernetesResource> resources = Arrays.asList(
        KubernetesResource.builder()
            .spec("spec")
            .resourceId(
                KubernetesResourceId.builder().namespace(namespace).kind("deployment").name("test-deployment").build())
            .build());
    doReturn(resources)
        .when(k8sTaskHelperBase)
        .getResourcesFromManifests(delegateTaskParams, manifestDelegateConfig, manifestFileDirectory, filePaths,
            valuesList, releaseName, namespace, logCallback, timeoutIntervalInMin, false);
    doReturn(true).when(k8sApplyBaseHandler).prepare(logCallback, false, k8sApplyHandlerConfig, true);
    RuntimeException exception = new RuntimeException("Failed to apply manifests");
    doThrow(exception)
        .when(k8sTaskHelperBase)
        .applyManifests(any(Kubectl.class), eq(resources), eq(delegateTaskParams), eq(logCallback), eq(true), eq(true),
            anyString());

    assertThatThrownBy(()
                           -> requestHandler.executeTaskInternal(
                               applyRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress))
        .isSameAs(exception);

    verify(k8sTaskHelperBase, times(1))
        .getResourcesFromManifests(eq(delegateTaskParams), eq(manifestDelegateConfig), eq(manifestFileDirectory),
            eq(filePaths), eq(valuesList), eq(releaseName), eq(namespace), eq(logCallback), eq(timeoutIntervalInMin),
            eq(false));
    verify(k8sApplyBaseHandler, times(1)).prepare(eq(logCallback), eq(false), eq(k8sApplyHandlerConfig), eq(true));
    verify(k8sTaskHelperBase, times(1))
        .applyManifests(any(Kubectl.class), eq(resources), eq(delegateTaskParams), eq(logCallback), eq(true), eq(true),
            anyString());
    verify(k8sApplyBaseHandler, times(0))
        .steadyStateCheck(false, namespace, delegateTaskParams, timeoutIntervalInMillis, logCallback,
            k8sApplyHandlerConfig, true, false, false);
    verify(k8sApplyBaseHandler, times(0)).wrapUp(eq(delegateTaskParams), eq(logCallback), any(Kubectl.class));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldNotApplyManifestsIfFailsSteadyCheck() throws Exception {
    List<String> valuesList = Collections.emptyList();
    List<String> filePaths = Arrays.asList("deploy.yaml");
    K8sApplyRequest applyRequest = K8sApplyRequest.builder()
                                       .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                       .manifestDelegateConfig(manifestDelegateConfig)
                                       .accountId(accountId)
                                       .releaseName(releaseName)
                                       .filePaths(filePaths)
                                       .timeoutIntervalInMin(timeoutIntervalInMin)
                                       .valuesYamlList(valuesList)
                                       .commandName("K8s Apply")
                                       .skipDryRun(true)
                                       .skipSteadyStateCheck(false)
                                       .build();

    K8sDelegateTaskParams delegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).build();

    List<KubernetesResource> resources = Arrays.asList(
        KubernetesResource.builder()
            .spec("spec")
            .resourceId(
                KubernetesResourceId.builder().namespace(namespace).kind("deployment").name("test-deployment").build())
            .build());
    doReturn(resources)
        .when(k8sTaskHelperBase)
        .getResourcesFromManifests(delegateTaskParams, manifestDelegateConfig, manifestFileDirectory, filePaths,
            valuesList, releaseName, namespace, logCallback, timeoutIntervalInMin, false);
    doReturn(true).when(k8sApplyBaseHandler).prepare(logCallback, false, k8sApplyHandlerConfig, true);
    doReturn(true)
        .when(k8sTaskHelperBase)
        .applyManifests(any(Kubectl.class), eq(resources), eq(delegateTaskParams), eq(logCallback), eq(true), eq(true),
            anyString());
    RuntimeException exception = new RuntimeException("Failed to check the status of the deployment");
    doThrow(exception)
        .when(k8sApplyBaseHandler)
        .steadyStateCheck(false, namespace, delegateTaskParams, timeoutIntervalInMillis, logCallback,
            k8sApplyHandlerConfig, true, true, false);

    assertThatThrownBy(()
                           -> requestHandler.executeTaskInternal(
                               applyRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress))
        .isSameAs(exception);

    verify(k8sTaskHelperBase, times(1))
        .getResourcesFromManifests(eq(delegateTaskParams), eq(manifestDelegateConfig), eq(manifestFileDirectory),
            eq(filePaths), eq(valuesList), eq(releaseName), eq(namespace), eq(logCallback), eq(timeoutIntervalInMin),
            eq(false));
    verify(k8sApplyBaseHandler, times(1)).prepare(eq(logCallback), eq(false), eq(k8sApplyHandlerConfig), eq(true));
    verify(k8sTaskHelperBase, times(1))
        .applyManifests(any(Kubectl.class), eq(resources), eq(delegateTaskParams), eq(logCallback), eq(true), eq(true),
            anyString());
    verify(k8sApplyBaseHandler, times(1))
        .steadyStateCheck(false, namespace, delegateTaskParams, timeoutIntervalInMillis, logCallback,
            k8sApplyHandlerConfig, true, true, false);
    verify(k8sApplyBaseHandler, times(0)).wrapUp(eq(delegateTaskParams), eq(logCallback), any(Kubectl.class));
  }
}
