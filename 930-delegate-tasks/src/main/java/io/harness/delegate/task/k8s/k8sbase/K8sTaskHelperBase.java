/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.GIT;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.HTTP_HELM;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.OCI_HELM;
import static io.harness.delegate.clienttools.ClientTool.OC;
import static io.harness.exception.WingsException.USER;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.filesystem.FileIo.getFilesUnderPath;
import static io.harness.filesystem.FileIo.getFilesUnderPathMatchesFirstLine;
import static io.harness.filesystem.FileIo.waitForDirectoryToBeAccessibleOutOfProcess;
import static io.harness.helm.HelmConstants.HELM_PATH_PLACEHOLDER;
import static io.harness.helm.HelmConstants.HELM_RELEASE_LABEL;
import static io.harness.k8s.K8sConstants.KUBERNETES_CHANGE_CAUSE_ANNOTATION;
import static io.harness.k8s.K8sConstants.RELEASE_NAME_CONFLICTS_WITH_SECRETS_OR_CONFIG_MAPS;
import static io.harness.k8s.K8sConstants.SKIP_FILE_FOR_DEPLOY_PLACEHOLDER_TEXT;
import static io.harness.k8s.KubernetesConvention.ReleaseHistoryKeyName;
import static io.harness.k8s.kubectl.AbstractExecutable.getPrintableCommand;
import static io.harness.k8s.kubectl.Utils.encloseWithQuotesIfNeeded;
import static io.harness.k8s.kubectl.Utils.parseLatestRevisionNumberFromRolloutHistory;
import static io.harness.k8s.manifest.ManifestHelper.getFirstLoadBalancerService;
import static io.harness.k8s.manifest.ManifestHelper.kustomizeFileNameYaml;
import static io.harness.k8s.manifest.ManifestHelper.kustomizeFileNameYml;
import static io.harness.k8s.manifest.ManifestHelper.validateValuesFileContents;
import static io.harness.k8s.manifest.ManifestHelper.values_filename;
import static io.harness.k8s.manifest.ManifestHelper.yaml_file_extension;
import static io.harness.k8s.manifest.ManifestHelper.yml_file_extension;
import static io.harness.k8s.model.K8sExpressions.canaryDestinationExpression;
import static io.harness.k8s.model.K8sExpressions.stableDestinationExpression;
import static io.harness.k8s.model.Kind.ConfigMap;
import static io.harness.k8s.model.Kind.Namespace;
import static io.harness.k8s.model.Kind.Secret;
import static io.harness.k8s.model.KubernetesResourceId.createKubernetesResourceIdFromNamespaceKindName;
import static io.harness.k8s.model.KubernetesResourceId.findScalableKubernetesResourceId;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.Failed;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;
import static io.harness.state.StateConstants.DEFAULT_STEADY_STATE_TIMEOUT;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.LogColor.Gray;
import static software.wings.beans.LogColor.Red;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;
import static software.wings.beans.LogWeight.Normal;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FileData;
import io.harness.concurrent.HTimeLimiter;
import io.harness.configuration.KubernetesCliCommandType;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.container.ContainerInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.progresstaskstreaming.NGDelegateTaskProgressCallback;
import io.harness.delegate.beans.storeconfig.CustomRemoteStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.LocalFileStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfigType;
import io.harness.delegate.beans.taskprogress.TaskProgressCallback;
import io.harness.delegate.clienttools.InstallUtils;
import io.harness.delegate.expression.DelegateExpressionEvaluator;
import io.harness.delegate.k8s.openshift.OpenShiftDelegateService;
import io.harness.delegate.service.ExecutionConfigOverrideFromFileOnDelegate;
import io.harness.delegate.task.git.ScmFetchFilesHelperNG;
import io.harness.delegate.task.helm.CustomManifestFetchTaskHelper;
import io.harness.delegate.task.helm.HelmCommandFlag;
import io.harness.delegate.task.helm.HelmTaskHelperBase;
import io.harness.delegate.task.k8s.client.K8sApiClient;
import io.harness.delegate.task.k8s.client.K8sCliClient;
import io.harness.delegate.task.k8s.client.K8sClient;
import io.harness.delegate.task.k8s.k8sbase.K8sReleaseHandlerFactory;
import io.harness.delegate.task.k8s.k8sbase.KustomizeTaskHelper;
import io.harness.delegate.task.localstore.ManifestFiles;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.HelmClientException;
import io.harness.exception.HelmClientRuntimeException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.KubernetesCliTaskRuntimeException;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.KubernetesValuesException;
import io.harness.exception.KubernetesYamlException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.YamlException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filesystem.FileIo;
import io.harness.helm.HelmCliCommandType;
import io.harness.helm.HelmCommandFlagsUtils;
import io.harness.helm.HelmCommandTemplateFactory;
import io.harness.helm.HelmSubCommandType;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.K8sConstants;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.KubernetesHelperService;
import io.harness.k8s.ProcessResponse;
import io.harness.k8s.exception.KubernetesExceptionExplanation;
import io.harness.k8s.exception.KubernetesExceptionHints;
import io.harness.k8s.exception.KubernetesExceptionMessages;
import io.harness.k8s.kubectl.AbstractExecutable;
import io.harness.k8s.kubectl.ApplyCommand;
import io.harness.k8s.kubectl.DeleteCommand;
import io.harness.k8s.kubectl.DescribeCommand;
import io.harness.k8s.kubectl.GetCommand;
import io.harness.k8s.kubectl.GetJobCommand;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.kubectl.KubectlFactory;
import io.harness.k8s.kubectl.RolloutHistoryCommand;
import io.harness.k8s.kubectl.RolloutStatusCommand;
import io.harness.k8s.kubectl.ScaleCommand;
import io.harness.k8s.kubectl.Utils;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.manifest.VersionUtils;
import io.harness.k8s.model.HarnessAnnotations;
import io.harness.k8s.model.HarnessLabelValues;
import io.harness.k8s.model.HarnessLabels;
import io.harness.k8s.model.HelmVersion;
import io.harness.k8s.model.IstioDestinationWeight;
import io.harness.k8s.model.K8sContainer;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.K8sRequestHandlerContext;
import io.harness.k8s.model.K8sSteadyStateDTO;
import io.harness.k8s.model.Kind;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceComparer;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.model.response.CEK8sDelegatePrerequisite;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.k8s.releasehistory.K8sLegacyRelease;
import io.harness.k8s.releasehistory.K8sReleaseHistoryCleanupDTO;
import io.harness.k8s.releasehistory.K8sReleasePersistDTO;
import io.harness.k8s.releasehistory.ReleaseHistory;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.manifest.CustomManifestService;
import io.harness.manifest.CustomManifestSource;
import io.harness.ng.core.dto.ErrorDetail;
import io.harness.retry.RetryHelper;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.serializer.YamlUtils;
import io.harness.shell.SshSessionConfig;
import io.harness.yaml.BooleanPatchedRepresenter;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.beans.command.ExecutionLogCallback;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.istio.api.networking.v1alpha3.Destination;
import io.fabric8.istio.api.networking.v1alpha3.DestinationRule;
import io.fabric8.istio.api.networking.v1alpha3.DestinationRuleList;
import io.fabric8.istio.api.networking.v1alpha3.HTTPRoute;
import io.fabric8.istio.api.networking.v1alpha3.HTTPRouteDestination;
import io.fabric8.istio.api.networking.v1alpha3.PortSelector;
import io.fabric8.istio.api.networking.v1alpha3.Subset;
import io.fabric8.istio.api.networking.v1alpha3.TCPRoute;
import io.fabric8.istio.api.networking.v1alpha3.TLSRoute;
import io.fabric8.istio.api.networking.v1alpha3.VirtualService;
import io.fabric8.istio.api.networking.v1alpha3.VirtualServiceList;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.github.resilience4j.retry.Retry;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentSpec;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1LoadBalancerIngress;
import io.kubernetes.client.openapi.models.V1LoadBalancerStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1TokenReviewStatus;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.validator.constraints.NotEmpty;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.exec.stream.LogOutputStream;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_K8S, HarnessModuleComponent.CDS_FIRST_GEN})
@Singleton
@Slf4j
@OwnedBy(CDP)
public class K8sTaskHelperBase {
  public static final Set<String> openshiftResources = ImmutableSet.of("Route");
  public static final String patchFieldName = "patchesStrategicMerge";
  public static final String patchYaml = "patches-%d.yaml";
  public static final String kustomizePatchesDirPrefix = "kustomizePatches-";
  public static final String VALUE_MISSING_REPLACEMENT = "<no value>";
  public static final String NOT_FOUND = "not found";
  private static final String GET_DIRECT_APPLY_ANNOTATION =
      "jsonpath='{.metadata.annotations.harness\\.io/direct-apply}'";

  @Inject private TimeLimiter timeLimiter;
  @Inject private KubernetesContainerService kubernetesContainerService;
  @Inject private KubernetesHelperService kubernetesHelperService;
  @Inject private ExecutionConfigOverrideFromFileOnDelegate delegateLocalConfigService;
  @Inject private NGGitService ngGitService;
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private K8sYamlToDelegateDTOMapper k8sYamlToDelegateDTOMapper;
  @Inject private NGErrorHelper ngErrorHelper;
  @Inject private GitDecryptionHelper gitDecryptionHelper;
  @Inject private KustomizeTaskHelper kustomizeTaskHelper;
  @Inject private OpenShiftDelegateService openShiftDelegateService;
  @Inject private HelmTaskHelperBase helmTaskHelperBase;
  @Inject private ScmFetchFilesHelperNG scmFetchFilesHelper;
  @Inject private K8sCliClient kubernetesCliClient;
  @Inject private K8sApiClient kubernetesApiClient;
  @Inject private CustomManifestService customManifestService;
  @Inject private CustomManifestFetchTaskHelper customManifestFetchTaskHelper;
  @Inject private K8sReleaseHandlerFactory releaseHandlerFactory;
  @Inject private K8sTaskManifestValidator k8sTaskManifestValidator;

  private DelegateExpressionEvaluator delegateExpressionEvaluator = new DelegateExpressionEvaluator();

  public static final String ISTIO_DESTINATION_TEMPLATE = "host: $ISTIO_DESTINATION_HOST_NAME\n"
      + "subset: $ISTIO_DESTINATION_SUBSET_NAME";
  private static final String INVALID_RESOURCE_SPEC_HINT =
      "Please check if the rendered manifest is valid and does not contain invalid/missing values.";
  private static final String INVALID_RESOURCE_SPEC_EXPLANATION =
      "Failed to load resource spec as a Kubernetes object.";

  public static LogOutputStream getExecutionLogOutputStream(LogCallback executionLogCallback, LogLevel logLevel) {
    return new LogOutputStream() {
      @Override
      protected void processLine(String line) {
        executionLogCallback.saveExecutionLog(line, logLevel);
      }
    };
  }

  public static LogOutputStream getExecutionLogOutputStream(
      LogCallback executionLogCallback, LogLevel logLevel, ByteArrayOutputStream captureStream) {
    return new LogOutputStream() {
      @SneakyThrows
      @Override
      protected void processLine(String line) {
        executionLogCallback.saveExecutionLog(line, logLevel);
        captureStream.write(line.getBytes(UTF_8));
      }
    };
  }

  public static String getResourcesInStringFormat(List<KubernetesResourceId> resourceIds) {
    StringBuilder sb = new StringBuilder(1024);
    resourceIds.forEach(resourceId -> sb.append("\n- ").append(resourceId.namespaceKindNameRef()));
    return sb.toString();
  }

  public static long getTimeoutMillisFromMinutes(Integer timeoutMinutes) {
    if (timeoutMinutes == null || timeoutMinutes <= 0) {
      timeoutMinutes = DEFAULT_STEADY_STATE_TIMEOUT;
    }

    return ofMinutes(timeoutMinutes).toMillis();
  }

  public static LogOutputStream getEmptyLogOutputStream() {
    return new LogOutputStream() {
      @Override
      protected void processLine(String line) {}
    };
  }

  public static ProcessResult executeCommandSilent(AbstractExecutable command, String workingDirectory)
      throws Exception {
    return command.execute(workingDirectory, null, null, false, Collections.emptyMap());
  }

  public static ProcessResponse executeCommand(AbstractExecutable command, K8sDelegateTaskParams k8sDelegateTaskParams,
      LogCallback executionLogCallback, LogLevel errorLogLevel) throws Exception {
    try (LogOutputStream logOutputStream = getExecutionLogOutputStream(executionLogCallback, INFO);
         ByteArrayOutputStream errorCaptureStream = new ByteArrayOutputStream(1024);
         LogOutputStream logErrorStream =
             getExecutionLogOutputStream(executionLogCallback, errorLogLevel, errorCaptureStream)) {
      return Retry
          .decorateCallable(buildRetryAndRegisterListeners(retryConditionForTimeout(),
                                K8sTaskHelperBase.class.getSimpleName() + ".executeCommand"),
              ()
                  -> getProcessResponse(command, logOutputStream, logErrorStream, errorCaptureStream,
                      k8sDelegateTaskParams.getWorkingDirectory(), k8sDelegateTaskParams.getKubectlPath(), true))
          .call();
    }
  }

  public static ProcessResponse executeCommandSilentlyWithErrorCapture(AbstractExecutable command,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback, LogLevel errorLogLevel)
      throws Exception {
    try (ByteArrayOutputStream errorCaptureStream = new ByteArrayOutputStream(1024);
         LogOutputStream logErrorStream =
             getExecutionLogOutputStream(executionLogCallback, errorLogLevel, errorCaptureStream)) {
      return Retry
          .decorateCallable(buildRetryAndRegisterListeners(retryConditionForTimeout(),
                                K8sTaskHelperBase.class.getSimpleName() + ".executeCommandSilentlyWithErrorCapture"),
              ()
                  -> getProcessResponse(command, null, logErrorStream, errorCaptureStream,
                      k8sDelegateTaskParams.getWorkingDirectory(), k8sDelegateTaskParams.getKubectlPath(), false))
          .call();
    }
  }

  private static ProcessResponse getProcessResponse(AbstractExecutable command, OutputStream output, OutputStream error,
      OutputStream errorCaptureStream, String workingDir, String kubectlPath, boolean printCommand) throws Exception {
    return ProcessResponse.builder()
        .processResult(command.execute(workingDir, output, error, printCommand, Collections.emptyMap()))
        .errorMessage(ExceptionMessageSanitizer.sanitizeMessage(errorCaptureStream.toString()))
        .kubectlPath(kubectlPath)
        .printableCommand(getPrintableCommand(command.command()))
        .build();
  }

  public static String getOcCommandPrefix(String ocPath, String kubeConfigPath) {
    StringBuilder command = new StringBuilder(128);

    if (StringUtils.isNotBlank(ocPath)) {
      command.append(encloseWithQuotesIfNeeded(ocPath));
    } else {
      command.append("oc");
    }

    if (StringUtils.isNotBlank(kubeConfigPath)) {
      command.append(" --kubeconfig=").append(encloseWithQuotesIfNeeded(kubeConfigPath));
    }

    return command.toString();
  }

  public static String getOcCommandPrefix(K8sDelegateTaskParams k8sDelegateTaskParams) {
    String ocPath = getLatestVersionOcPath();
    return getOcCommandPrefix(ocPath, k8sDelegateTaskParams.getKubeconfigPath());
  }

  @VisibleForTesting
  public static String getRelativePath(String filePath, String prefixPath) {
    Path fileAbsolutePath = Paths.get(filePath).toAbsolutePath();
    Path prefixAbsolutePath = Paths.get(prefixPath).toAbsolutePath();
    return prefixAbsolutePath.relativize(fileAbsolutePath).toString();
  }

  public static boolean isValidManifestFile(String filename) {
    return (StringUtils.endsWith(filename, yaml_file_extension) || StringUtils.endsWith(filename, yml_file_extension))
        && !StringUtils.equals(filename, values_filename);
  }

  public List<K8sPod> getPodDetailsWithLabels(KubernetesConfig kubernetesConfig, String namespace, String releaseName,
      Map<String, String> labels, long timeoutinMillis) throws Exception {
    return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMillis(timeoutinMillis),
        ()
            -> kubernetesContainerService.getRunningPodsWithLabels(kubernetesConfig, namespace, labels)
                   .stream()
                   .filter(pod
                       -> pod.getMetadata() != null && pod.getStatus() != null
                           && pod.getStatus().getContainerStatuses() != null)
                   .map(pod -> {
                     V1ObjectMeta metadata = pod.getMetadata();
                     return K8sPod.builder()
                         .uid(metadata.getUid())
                         .name(metadata.getName())
                         .podIP(pod.getStatus().getPodIP())
                         .namespace(metadata.getNamespace())
                         .releaseName(releaseName)
                         .containerList(pod.getStatus()
                                            .getContainerStatuses()
                                            .stream()
                                            .map(container
                                                -> K8sContainer.builder()
                                                       .containerId(container.getContainerID())
                                                       .name(container.getName())
                                                       .image(container.getImage())
                                                       .build())
                                            .collect(toList()))
                         // Need to ensure that we're storing labels as registered by kryo map implementation
                         .labels(metadata.getLabels() != null ? new HashMap<>(metadata.getLabels()) : null)
                         .build();
                   })
                   .collect(toList()));
  }

  public List<K8sPod> getPodDetailsWithTrack(KubernetesConfig kubernetesConfig, String namespace, String releaseName,
      String track, long timeoutInMillis) throws Exception {
    Map<String, String> labels = ImmutableMap.of(HarnessLabels.releaseName, releaseName, HarnessLabels.track, track);
    return getPodDetailsWithLabels(kubernetesConfig, namespace, releaseName, labels, timeoutInMillis);
  }

  public List<K8sPod> getPodDetailsWithColor(KubernetesConfig kubernetesConfig, String namespace, String releaseName,
      String color, long timeoutInMillis) throws Exception {
    Map<String, String> labels = ImmutableMap.of(HarnessLabels.releaseName, releaseName, HarnessLabels.color, color);
    return getPodDetailsWithLabels(kubernetesConfig, namespace, releaseName, labels, timeoutInMillis);
  }

  public List<KubernetesResource> getDeploymentContainingTrackStableSelector(KubernetesConfig kubernetesConfig,
      List<KubernetesResource> managedWorkloads, Map.Entry<String, String> selector) {
    List<KubernetesResource> resources = new ArrayList<>();
    for (KubernetesResource deployment : managedWorkloads) {
      KubernetesResourceId resourceId = deployment.getResourceId();
      if (resourceId == null || !resourceId.getKind().equals(Kind.Deployment.name())) {
        continue;
      }
      V1Deployment deploymentFromServer = kubernetesContainerService.getDeployment(kubernetesConfig,
          isBlank(resourceId.getNamespace()) ? kubernetesConfig.getNamespace() : resourceId.getNamespace(),
          resourceId.getName());
      if (deploymentFromServer != null && deploymentContainsHarnessTrackSelector(deploymentFromServer, selector)) {
        resources.add(deployment);
      }
    }
    return resources;
  }

  private boolean deploymentContainsHarnessTrackSelector(
      V1Deployment v1Deployment, Map.Entry<String, String> selector) {
    AtomicBoolean containsHarnessTrackSelector = new AtomicBoolean(false);
    Optional.ofNullable(v1Deployment)
        .map(V1Deployment::getSpec)
        .map(V1DeploymentSpec::getSelector)
        .map(V1LabelSelector::getMatchLabels)
        .ifPresent(selectors -> containsHarnessTrackSelector.set(selectors.entrySet().contains(selector)));

    return containsHarnessTrackSelector.get();
  }

  private V1Service waitForLoadBalancerService(
      KubernetesConfig kubernetesConfig, String serviceName, String namespace, int timeoutInSeconds) {
    return waitForLoadBalancerService(serviceName, () -> {
      V1Service service = kubernetesContainerService.getService(kubernetesConfig, serviceName, namespace);
      if (service.getStatus() != null && service.getStatus().getLoadBalancer() != null) {
        V1LoadBalancerStatus loadBalancerStatus = service.getStatus().getLoadBalancer();
        if (isNotEmpty(loadBalancerStatus.getIngress())) {
          return service;
        }
      }

      return null;
    }, timeoutInSeconds);
  }

  private <T> T waitForLoadBalancerService(String name, Callable<T> getLoadBalancerService, int timeoutInSeconds) {
    try {
      return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(timeoutInSeconds), () -> {
        while (true) {
          T result = getLoadBalancerService.call();
          if (result != null) {
            return result;
          }

          int sleepTimeInSeconds = 5;
          log.info("waitForLoadBalancerService: LoadBalancer Service {} not ready. Sleeping for {} seconds", name,
              sleepTimeInSeconds);
          sleep(ofSeconds(sleepTimeInSeconds));
        }
      });
    } catch (UncheckedTimeoutException e) {
      log.error("Timed out waiting for LoadBalancer service. Moving on.", e);
    } catch (InterruptedException e) {
      log.error("Exception while trying to get LoadBalancer service", e);
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      log.error("Exception while trying to get LoadBalancer service", e);
    }

    return null;
  }

  private String getLoadBalancerEndpoint(String loadBalancerHost, Iterator<Integer> ports) {
    boolean port80Found = false;
    boolean port443Found = false;
    Integer firstPort = null;

    while (ports.hasNext()) {
      firstPort = ports.next();

      if (firstPort == 80) {
        port80Found = true;
      }
      if (firstPort == 443) {
        port443Found = true;
      }
    }

    if (port443Found) {
      return "https://" + loadBalancerHost + "/";
    } else if (port80Found) {
      return "http://" + loadBalancerHost + "/";
    } else if (firstPort != null) {
      return loadBalancerHost + ":" + firstPort;
    } else {
      return loadBalancerHost;
    }
  }

  public String getLoadBalancerEndpoint(KubernetesConfig kubernetesConfig, List<KubernetesResource> resources) {
    KubernetesResource loadBalancerResource = getFirstLoadBalancerService(resources);
    if (loadBalancerResource == null) {
      return null;
    }

    // NOTE(hindwani): We are not using timeOutInMillis for waiting because of the bug: CDP-13872
    V1Service service = waitForLoadBalancerService(kubernetesConfig, loadBalancerResource.getResourceId().getName(),
        loadBalancerResource.getResourceId().getNamespace(), 60);

    if (service == null) {
      log.warn("Could not get the Service Status {} from cluster.", loadBalancerResource.getResourceId().getName());
      return null;
    }

    if (service.getStatus() == null || service.getStatus().getLoadBalancer() == null
        || service.getStatus().getLoadBalancer().getIngress() == null) {
      return null;
    }

    V1LoadBalancerIngress loadBalancerIngress = service.getStatus().getLoadBalancer().getIngress().get(0);
    String loadBalancerHost =
        isNotBlank(loadBalancerIngress.getHostname()) ? loadBalancerIngress.getHostname() : loadBalancerIngress.getIp();
    if (service.getSpec() == null || service.getSpec().getPorts() == null) {
      return loadBalancerHost;
    }

    return getLoadBalancerEndpoint(
        loadBalancerHost, service.getSpec().getPorts().stream().map(V1ServicePort::getPort).iterator());
  }

  public void setNamespaceToKubernetesResourcesIfRequired(
      List<KubernetesResource> kubernetesResources, String namespace) {
    if (isEmpty(kubernetesResources)) {
      return;
    }

    for (KubernetesResource kubernetesResource : kubernetesResources) {
      if (isBlank(kubernetesResource.getResourceId().getNamespace())) {
        kubernetesResource.getResourceId().setNamespace(namespace);
      }
    }
  }

  public List<K8sPod> getPodDetails(
      KubernetesConfig kubernetesConfig, String namespace, String releaseName, long timeoutInMillis) throws Exception {
    if (isEmpty(releaseName)) {
      return Collections.emptyList();
    }
    Map<String, String> labels = ImmutableMap.of(HarnessLabels.releaseName, releaseName);
    return getPodDetailsWithLabels(kubernetesConfig, namespace, releaseName, labels, timeoutInMillis);
  }

  /**
   * This method arranges resources to be deleted in the reverse order of their creation.
   * To see order of create, please refer to KubernetesResourceComparer.kindOrder
   *
   * @param resourceIdsToDelete
   */
  public List<KubernetesResourceId> arrangeResourceIdsInDeletionOrder(List<KubernetesResourceId> resourceIdsToDelete) {
    List<KubernetesResource> kubernetesResources =
        resourceIdsToDelete.stream()
            .map(resourceId -> KubernetesResource.builder().resourceId(resourceId).build())
            .collect(Collectors.toList());
    kubernetesResources =
        kubernetesResources.stream().sorted(new KubernetesResourceComparer().reversed()).collect(Collectors.toList());
    return kubernetesResources.stream()
        .map(kubernetesResource -> kubernetesResource.getResourceId())
        .collect(Collectors.toList());
  }

  public Integer getTargetInstancesForCanary(
      Integer percentInstancesInDelegateRequest, Integer maxInstances, LogCallback logCallback) {
    Integer targetInstances = (int) Math.round(percentInstancesInDelegateRequest * maxInstances / 100.0);
    if (targetInstances < 1) {
      logCallback.saveExecutionLog("\nTarget instances computed to be less than 1. Bumped up to 1");
      targetInstances = 1;
    }
    return targetInstances;
  }

  public List<Subset> generateSubsetsForDestinationRule(List<String> subsetNames) {
    List<Subset> subsets = new ArrayList<>();

    for (String subsetName : subsetNames) {
      Subset subset = new Subset();
      subset.setName(subsetName);

      if (subsetName.equals(HarnessLabelValues.trackCanary)) {
        Map<String, String> labels = new HashMap<>();
        labels.put(HarnessLabels.track, HarnessLabelValues.trackCanary);
        subset.setLabels(labels);
      } else if (subsetName.equals(HarnessLabelValues.trackStable)) {
        Map<String, String> labels = new HashMap<>();
        labels.put(HarnessLabels.track, HarnessLabelValues.trackStable);
        subset.setLabels(labels);
      } else if (subsetName.equals(HarnessLabelValues.colorBlue)) {
        Map<String, String> labels = new HashMap<>();
        labels.put(HarnessLabels.color, HarnessLabelValues.colorBlue);
        subset.setLabels(labels);
      } else if (subsetName.equals(HarnessLabelValues.colorGreen)) {
        Map<String, String> labels = new HashMap<>();
        labels.put(HarnessLabels.color, HarnessLabelValues.colorGreen);
        subset.setLabels(labels);
      }

      subsets.add(subset);
    }

    return subsets;
  }

  private String generateDestination(String host, String subset) {
    return ISTIO_DESTINATION_TEMPLATE.replace("$ISTIO_DESTINATION_HOST_NAME", host)
        .replace("$ISTIO_DESTINATION_SUBSET_NAME", subset);
  }

  private String getDestinationYaml(String destination, String host) {
    if (canaryDestinationExpression.equals(destination)) {
      return generateDestination(host, HarnessLabelValues.trackCanary);
    } else if (stableDestinationExpression.equals(destination)) {
      return generateDestination(host, HarnessLabelValues.trackStable);
    } else {
      return destination;
    }
  }

  private List<HTTPRouteDestination> generateDestinationWeights(
      List<IstioDestinationWeight> istioDestinationWeights, String host, PortSelector portSelector) throws IOException {
    List<HTTPRouteDestination> destinationWeights = new ArrayList<>();

    for (IstioDestinationWeight istioDestinationWeight : istioDestinationWeights) {
      String destinationYaml = getDestinationYaml(istioDestinationWeight.getDestination(), host);
      Destination destination = new YamlUtils().read(destinationYaml, Destination.class);
      destination.setPort(portSelector);

      HTTPRouteDestination destinationWeight = new HTTPRouteDestination();
      destinationWeight.setWeight(Integer.parseInt(istioDestinationWeight.getWeight()));
      destinationWeight.setDestination(destination);

      destinationWeights.add(destinationWeight);
    }

    return destinationWeights;
  }

  private String getHostFromRoute(List<HTTPRouteDestination> routes) {
    if (isEmpty(routes)) {
      throw new InvalidRequestException("No routes exist in VirtualService", USER);
    }

    if (null == routes.get(0).getDestination()) {
      throw new InvalidRequestException("No destination exist in VirtualService", USER);
    }

    if (isBlank(routes.get(0).getDestination().getHost())) {
      throw new InvalidRequestException("No host exist in VirtualService", USER);
    }

    return routes.get(0).getDestination().getHost();
  }

  private PortSelector getPortSelectorFromRoute(List<HTTPRouteDestination> routes) {
    return routes.get(0).getDestination().getPort();
  }

  private void validateRoutesInVirtualService(VirtualService virtualService) {
    List<HTTPRoute> http = virtualService.getSpec().getHttp();
    List<TCPRoute> tcp = virtualService.getSpec().getTcp();
    List<TLSRoute> tls = virtualService.getSpec().getTls();

    if (isEmpty(http)) {
      throw new InvalidRequestException(
          "Http route is not present in VirtualService. Only Http routes are allowed", USER);
    }

    if (isNotEmpty(tcp) || isNotEmpty(tls)) {
      throw new InvalidRequestException("Only Http routes are allowed in VirtualService for Traffic split", USER);
    }

    if (http.size() > 1) {
      throw new InvalidRequestException("Only one route is allowed in VirtualService", USER);
    }
  }

  public void updateVirtualServiceWithDestinationWeights(List<IstioDestinationWeight> istioDestinationWeights,
      VirtualService virtualService, LogCallback executionLogCallback) throws IOException {
    validateRoutesInVirtualService(virtualService);

    executionLogCallback.saveExecutionLog("\nUpdating VirtualService with destination weights");

    List<HTTPRoute> http = virtualService.getSpec().getHttp();
    if (isNotEmpty(http)) {
      String host = getHostFromRoute(http.get(0).getRoute());
      PortSelector portSelector = getPortSelectorFromRoute(http.get(0).getRoute());
      http.get(0).setRoute(generateDestinationWeights(istioDestinationWeights, host, portSelector));
    }
  }

  private VirtualService updateVirtualServiceManifestFilesWithRoutes(List<KubernetesResource> resources,
      KubernetesConfig kubernetesConfig, List<IstioDestinationWeight> istioDestinationWeights,
      LogCallback executionLogCallback) throws IOException {
    List<KubernetesResource> virtualServiceResources =
        resources.stream()
            .filter(
                kubernetesResource -> kubernetesResource.getResourceId().getKind().equals(Kind.VirtualService.name()))
            .filter(KubernetesResource::isManaged)
            .collect(toList());

    if (isEmpty(virtualServiceResources)) {
      return null;
    }

    if (virtualServiceResources.size() > 1) {
      String msg = "\nMore than one VirtualService found. Only one VirtualService can be marked with annotation "
          + HarnessAnnotations.managed + ": true";
      executionLogCallback.saveExecutionLog(msg + "\n", ERROR, FAILURE);
      throw new InvalidRequestException(msg, USER);
    }

    try (KubernetesClient kubernetesClient = kubernetesHelperService.getKubernetesClient(kubernetesConfig)) {
      kubernetesClient.resources(VirtualService.class, VirtualServiceList.class);
      KubernetesResource kubernetesResource = virtualServiceResources.get(0);
      InputStream inputStream = IOUtils.toInputStream(kubernetesResource.getSpec(), UTF_8);
      VirtualService virtualService = (VirtualService) kubernetesClient.load(inputStream).items().get(0);
      updateVirtualServiceWithDestinationWeights(istioDestinationWeights, virtualService, executionLogCallback);

      kubernetesResource.setSpec(KubernetesHelper.toYaml(virtualService));

      return virtualService;
    }
  }

  public VirtualService updateVirtualServiceManifestFilesWithRoutesForCanary(List<KubernetesResource> resources,
      KubernetesConfig kubernetesConfig, LogCallback executionLogCallback) throws IOException {
    List<IstioDestinationWeight> istioDestinationWeights = new ArrayList<>();
    istioDestinationWeights.add(
        IstioDestinationWeight.builder().destination(stableDestinationExpression).weight("100").build());
    istioDestinationWeights.add(
        IstioDestinationWeight.builder().destination(canaryDestinationExpression).weight("0").build());

    return updateVirtualServiceManifestFilesWithRoutes(
        resources, kubernetesConfig, istioDestinationWeights, executionLogCallback);
  }

  public DestinationRule updateDestinationRuleManifestFilesWithSubsets(List<KubernetesResource> resources,
      List<String> subsets, KubernetesConfig kubernetesConfig, LogCallback executionLogCallback) throws IOException {
    List<KubernetesResource> destinationRuleResources =
        resources.stream()
            .filter(
                kubernetesResource -> kubernetesResource.getResourceId().getKind().equals(Kind.DestinationRule.name()))
            .filter(KubernetesResource::isManaged)
            .collect(toList());

    if (isEmpty(destinationRuleResources)) {
      return null;
    }

    if (destinationRuleResources.size() > 1) {
      String msg = "More than one DestinationRule found. Only one DestinationRule can be marked with annotation "
          + HarnessAnnotations.managed + ": true";
      executionLogCallback.saveExecutionLog(msg + "\n", ERROR, FAILURE);
      throw new InvalidRequestException(msg, USER);
    }

    try (KubernetesClient kubernetesClient = kubernetesHelperService.getKubernetesClient(kubernetesConfig)) {
      kubernetesClient.resources(DestinationRule.class, DestinationRuleList.class);

      KubernetesResource kubernetesResource = destinationRuleResources.get(0);
      InputStream inputStream = IOUtils.toInputStream(kubernetesResource.getSpec(), UTF_8);
      DestinationRule destinationRule = (DestinationRule) kubernetesClient.load(inputStream).items().get(0);
      destinationRule.getSpec().setSubsets(generateSubsetsForDestinationRule(subsets));

      kubernetesResource.setSpec(KubernetesHelper.toYaml(destinationRule));

      return destinationRule;
    }
  }

  private String getPodContainerId(K8sPod pod) {
    return isEmpty(pod.getContainerList()) ? EMPTY : pod.getContainerList().get(0).getContainerId();
  }

  private List<K8sPod> getHelmPodDetails(
      KubernetesConfig kubernetesConfig, String namespace, String releaseName, long timeoutInMillis) throws Exception {
    Map<String, String> labels = ImmutableMap.of(HELM_RELEASE_LABEL, releaseName);
    return getPodDetailsWithLabels(kubernetesConfig, namespace, releaseName, labels, timeoutInMillis);
  }

  public List<ContainerInfo> getContainerInfos(
      KubernetesConfig kubernetesConfig, String releaseName, String namespace, long timeoutInMillis) throws Exception {
    List<K8sPod> helmPods = getHelmPodDetails(kubernetesConfig, namespace, releaseName, timeoutInMillis);

    return helmPods.stream()
        .map(pod
            -> ContainerInfo.builder()
                   .hostName(pod.getName())
                   .ip(pod.getPodIP())
                   .containerId(getPodContainerId(pod))
                   .podName(pod.getName())
                   .newContainer(true)
                   .status(ContainerInfo.Status.SUCCESS)
                   .releaseName(releaseName)
                   .namespace(pod.getNamespace())
                   .build())
        .collect(Collectors.toList());
  }

  public List<K8sPod> getHelmPodList(
      long timeoutInMillis, KubernetesConfig kubernetesConfig, String releaseName, LogCallback logCallback) {
    String namespace = kubernetesConfig.getNamespace();
    try {
      logCallback.saveExecutionLog("\nFetching existing pod list.");
      return getHelmPodDetails(kubernetesConfig, namespace, releaseName, timeoutInMillis);
    } catch (Exception e) {
      logCallback.saveExecutionLog(e.getMessage(), ERROR, FAILURE);
    }
    return Collections.emptyList();
  }

  public Kubectl getOverriddenClient(
      Kubectl client, List<KubernetesResource> resources, K8sDelegateTaskParams k8sDelegateTaskParams) {
    List<KubernetesResource> openshiftResourcesList =
        resources.stream()
            .filter(kubernetesResource -> openshiftResources.contains(kubernetesResource.getResourceId().getKind()))
            .collect(Collectors.toList());
    if (isEmpty(openshiftResourcesList)) {
      return client;
    }

    return KubectlFactory.getOpenShiftClient(getLatestVersionOcPath(), k8sDelegateTaskParams.getKubeconfigPath(),
        k8sDelegateTaskParams.getWorkingDirectory());
  }

  public ProcessResponse runK8sExecutable(K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback,
      AbstractExecutable executable) throws Exception {
    return executeCommand(executable, k8sDelegateTaskParams, executionLogCallback, ERROR);
  }

  public ProcessResponse runK8sExecutable(K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback,
      AbstractExecutable executable, LogLevel logLevel) throws Exception {
    return executeCommand(executable, k8sDelegateTaskParams, executionLogCallback, logLevel);
  }
  public void warnIfReleaseNameConflictsWithSecretOrConfigMap(
      List<KubernetesResource> resources, String releaseName, LogCallback executionLogCallback) {
    if (isEmpty(releaseName)) {
      return;
    }
    boolean isConflicting =
        resources.stream()
            .map(KubernetesResource::getResourceId)
            .filter(id -> Secret.name().equals(id.getKind()) || ConfigMap.name().equals(id.getKind()))
            .anyMatch(id -> releaseName.equals(id.getName()));
    if (isConflicting) {
      executionLogCallback.saveExecutionLog(RELEASE_NAME_CONFLICTS_WITH_SECRETS_OR_CONFIG_MAPS, WARN, RUNNING);
    }
  }

  public boolean applyManifests(Kubectl client, List<KubernetesResource> resources,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback, boolean denoteOverallSuccess,
      String commandFlags) throws Exception {
    return applyManifests(
        client, resources, k8sDelegateTaskParams, executionLogCallback, denoteOverallSuccess, false, commandFlags);
  }

  public boolean applyManifests(Kubectl client, List<KubernetesResource> resources,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback, boolean denoteOverallSuccess,
      boolean isErrorFrameworkEnabled, String commandFlags) throws Exception {
    FileIo.writeUtf8StringToFile(
        k8sDelegateTaskParams.getWorkingDirectory() + "/manifests.yaml", ManifestHelper.toYaml(resources));

    Kubectl overriddenClient = getOverriddenClient(client, resources, k8sDelegateTaskParams);

    // We want to set `kubernetes.io/change-cause` annotation only if no any custom value already defined
    boolean recordCommand =
        resources.stream()
            .map(resource -> resource.getMetadataAnnotationValue(KUBERNETES_CHANGE_CAUSE_ANNOTATION))
            .noneMatch(Objects::nonNull);

    final ApplyCommand applyCommand =
        overriddenClient.apply().filename("manifests.yaml").record(recordCommand).commandFlags(commandFlags);
    ProcessResponse response = runK8sExecutable(k8sDelegateTaskParams, executionLogCallback, applyCommand);
    ProcessResult result = response.getProcessResult();
    if (result.getExitValue() != 0) {
      log.error(format("\nFailed. Process terminated with exit value: [%s] and output: [%s]", result.getExitValue(),
          result.outputUTF8()));
      if (isErrorFrameworkEnabled) {
        throw new KubernetesCliTaskRuntimeException(response, KubernetesCliCommandType.APPLY);
      }

      logExecutableFailed(result, executionLogCallback);
      return false;
    }

    if (denoteOverallSuccess) {
      executionLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);
    }

    return true;
  }

  public void deleteManifests(Kubectl client, List<KubernetesResource> resources,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback) throws Exception {
    FileIo.writeUtf8StringToFile(
        k8sDelegateTaskParams.getWorkingDirectory() + "/manifests.yaml", ManifestHelper.toYaml(resources));

    Kubectl overriddenClient = getOverriddenClient(client, resources, k8sDelegateTaskParams);

    final DeleteCommand deleteCommand = overriddenClient.delete().filename("manifests.yaml");
    ProcessResponse response = runK8sExecutable(k8sDelegateTaskParams, executionLogCallback, deleteCommand);
    ProcessResult result = response.getProcessResult();
    if (result.getExitValue() != 0) {
      log.warn("Failed to delete manifests with exit code: {}. Error {}", result.getExitValue(),
          result.hasOutput() ? result.outputUTF8() : "Empty output");
    }

    executionLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);
  }

  @VisibleForTesting
  public StartedProcess getEventWatchProcess(String workingDirectory, GetCommand getEventsCommand,
      LogOutputStream watchInfoStream, LogOutputStream watchErrorStream) throws Exception {
    return getEventsCommand.executeInBackground(workingDirectory, watchInfoStream, watchErrorStream);
  }

  @VisibleForTesting
  public ProcessResult executeCommandUsingUtils(String workingDirectory, LogOutputStream statusInfoStream,
      LogOutputStream statusErrorStream, String command, Map<String, String> environment) throws Exception {
    return Utils.executeScript(workingDirectory, command, statusInfoStream, statusErrorStream, environment);
  }

  public boolean scale(Kubectl client, K8sDelegateTaskParams k8sDelegateTaskParams, KubernetesResourceId resourceId,
      int targetReplicaCount, LogCallback executionLogCallback, boolean isErrorFrameworkEnabled) throws Exception {
    executionLogCallback.saveExecutionLog("\nScaling " + resourceId.kindNameRef());

    final ScaleCommand scaleCommand = client.scale()
                                          .resource(resourceId.kindNameRef())
                                          .replicas(targetReplicaCount)
                                          .namespace(resourceId.getNamespace());
    ProcessResponse response = runK8sExecutable(k8sDelegateTaskParams, executionLogCallback, scaleCommand);
    ProcessResult result = response.getProcessResult();
    if (result.getExitValue() == 0) {
      return true;
    } else {
      logExecutableFailed(result, executionLogCallback);
      log.warn("Failed to scale workload. Error {}", result.hasOutput() ? result.outputUTF8() : "empty output");
      if (isErrorFrameworkEnabled) {
        throw new KubernetesCliTaskRuntimeException(response, KubernetesCliCommandType.SCALE);
      }

      return false;
    }
  }

  public void cleanup(Kubectl client, K8sDelegateTaskParams k8sDelegateTaskParams, ReleaseHistory releaseHistory,
      LogCallback executionLogCallback) throws Exception {
    final int lastSuccessfulReleaseNumber =
        (releaseHistory.getLastSuccessfulRelease() != null) ? releaseHistory.getLastSuccessfulRelease().getNumber() : 0;

    if (lastSuccessfulReleaseNumber == 0) {
      executionLogCallback.saveExecutionLog("\nNo previous successful release found.");
    } else {
      executionLogCallback.saveExecutionLog("\nPrevious Successful Release is " + lastSuccessfulReleaseNumber);
    }

    executionLogCallback.saveExecutionLog("\nCleaning up older and failed releases");

    for (int releaseIndex = releaseHistory.getReleases().size() - 1; releaseIndex >= 0; releaseIndex--) {
      K8sLegacyRelease release = releaseHistory.getReleases().get(releaseIndex);
      if (release.getNumber() < lastSuccessfulReleaseNumber || release.getStatus() == Failed) {
        for (int resourceIndex = release.getResources().size() - 1; resourceIndex >= 0; resourceIndex--) {
          KubernetesResourceId resourceId = release.getResources().get(resourceIndex);
          if (resourceId.isVersioned()) {
            DeleteCommand deleteCommand =
                client.delete().resources(resourceId.kindNameRef()).namespace(resourceId.getNamespace());
            ProcessResponse response = runK8sExecutable(k8sDelegateTaskParams, executionLogCallback, deleteCommand);
            ProcessResult result = response.getProcessResult();
            if (result.getExitValue() != 0) {
              log.warn("Failed to delete resource {}. Error {}", resourceId.kindNameRef(), result.getOutput());
            }
          }
        }
      }
    }
    releaseHistory.getReleases().removeIf(
        release -> release.getNumber() < lastSuccessfulReleaseNumber || release.getStatus() == Failed);
  }

  public void delete(Kubectl client, K8sDelegateTaskParams k8sDelegateTaskParams,
      List<KubernetesResourceId> kubernetesResourceIds, LogCallback executionLogCallback, boolean denoteOverallSuccess)
      throws Exception {
    boolean deleteFailed = false;
    for (KubernetesResourceId resourceId : kubernetesResourceIds) {
      ProcessResult result = executeDeleteCommand(client, k8sDelegateTaskParams, executionLogCallback, resourceId);
      if (result.getExitValue() != 0) {
        log.warn("Failed to delete resource {}. Error {}", resourceId.kindNameRef(), result.getOutput());
        String resultOutput = result.outputUTF8().toLowerCase();
        // if result contains "not found" then we don't fail else we fail the step
        if (!resultOutput.contains(NOT_FOUND)) {
          deleteFailed = true;
        }
      }
    }
    if (deleteFailed) {
      executionLogCallback.saveExecutionLog("Failed", ERROR, FAILURE);
    } else if (denoteOverallSuccess) {
      executionLogCallback.saveExecutionLog("Done", INFO, SUCCESS);
    }
  }

  public List<KubernetesResourceId> executeDeleteHandlingPartialExecution(Kubectl client,
      K8sDelegateTaskParams k8sDelegateTaskParams, List<KubernetesResourceId> kubernetesResourceIds,
      LogCallback executionLogCallback, boolean denoteOverallSuccess) throws Exception {
    List<KubernetesResourceId> deletedResources = new ArrayList<>();
    for (KubernetesResourceId resourceId : kubernetesResourceIds) {
      ProcessResult result = executeDeleteCommand(client, k8sDelegateTaskParams, executionLogCallback, resourceId);
      if (result.getExitValue() == 0) {
        deletedResources.add(resourceId);
      } else {
        log.warn("Failed to delete resource {}. Error {}", resourceId.kindNameRef(), result.getOutput());
      }
    }

    if (denoteOverallSuccess) {
      executionLogCallback.saveExecutionLog("Done", INFO, SUCCESS);
    }
    return deletedResources;
  }

  private ProcessResult executeDeleteCommand(Kubectl client, K8sDelegateTaskParams k8sDelegateTaskParams,
      LogCallback executionLogCallback, KubernetesResourceId resourceId) throws Exception {
    DeleteCommand deleteCommand =
        client.delete().resources(resourceId.kindNameRef()).namespace(resourceId.getNamespace());
    return runK8sExecutable(k8sDelegateTaskParams, executionLogCallback, deleteCommand).getProcessResult();
  }

  public boolean checkIfResourceContainsHarnessDirectApplyAnnotation(Kubectl client,
      K8sDelegateTaskParams k8sDelegateTaskParams, KubernetesResourceId kubernetesResourceId,
      LogCallback executionLogCallback) {
    try {
      ProcessResult result = executeGetWorkloadCommand(
          client, k8sDelegateTaskParams, executionLogCallback, kubernetesResourceId, GET_DIRECT_APPLY_ANNOTATION);
      if (result.getExitValue() == 0) {
        if (result.hasOutput() && isEmpty(result.outputUTF8())) {
          return true;
        }
        return !Boolean.parseBoolean(result.outputUTF8().replaceAll("'", ""));
      }
    } catch (Exception ex) {
      log.warn("Resource {} not found in cluster. Error {}", kubernetesResourceId.kindNameRef(), ex);
    }
    return false;
  }

  private ProcessResult executeGetWorkloadCommand(Kubectl client, K8sDelegateTaskParams k8sDelegateTaskParams,
      LogCallback executionLogCallback, KubernetesResourceId resourceId, String output) throws Exception {
    GetCommand getCommand =
        client.get().resources(resourceId.kindNameRef()).namespace(resourceId.getNamespace()).output(output);
    return runK8sExecutable(k8sDelegateTaskParams, executionLogCallback, getCommand, WARN).getProcessResult();
  }

  public void describe(Kubectl client, K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback)
      throws Exception {
    final DescribeCommand describeCommand = client.describe().filename("manifests.yaml");
    runK8sExecutable(k8sDelegateTaskParams, executionLogCallback, describeCommand);
  }

  public String getRolloutHistoryCommandForDeploymentConfig(
      K8sDelegateTaskParams k8sDelegateTaskParams, KubernetesResourceId resourceId) {
    String namespace = "";
    if (StringUtils.isNotBlank(resourceId.getNamespace())) {
      namespace = "--namespace=" + resourceId.getNamespace() + " ";
    }

    return K8sConstants.ocRolloutHistoryCommand
        .replace("{OC_COMMAND_PREFIX}", getOcCommandPrefix(k8sDelegateTaskParams))
        .replace("{RESOURCE_ID}", resourceId.kindNameRef())
        .replace("{NAMESPACE}", namespace)
        .trim();
  }

  @VisibleForTesting
  public ProcessResult executeCommandUsingUtils(K8sDelegateTaskParams k8sDelegateTaskParams,
      LogOutputStream statusInfoStream, LogOutputStream statusErrorStream, String command,
      Map<String, String> environment) throws Exception {
    if (isNotEmpty(k8sDelegateTaskParams.getGcpKeyFilePath())) {
      addGcpCredentialsToEnvironmentIfExist(k8sDelegateTaskParams.getGcpKeyFilePath(), environment);
    }
    return executeCommandUsingUtils(
        k8sDelegateTaskParams.getWorkingDirectory(), statusInfoStream, statusErrorStream, command, environment);
  }

  private void addGcpCredentialsToEnvironmentIfExist(String filePath, Map<String, String> environment) {
    if (Files.exists(Paths.get(filePath))) {
      environment.put("GOOGLE_APPLICATION_CREDENTIALS", filePath);
    }
  }

  public String getRolloutStatusCommandForDeploymentConfig(
      String ocPath, String kubeConfigPath, KubernetesResourceId resourceId) {
    String namespace = "";
    if (StringUtils.isNotBlank(resourceId.getNamespace())) {
      namespace = "--namespace=" + resourceId.getNamespace() + " ";
    }

    return K8sConstants.ocRolloutStatusCommand
        .replace("{OC_COMMAND_PREFIX}", getOcCommandPrefix(ocPath, kubeConfigPath))
        .replace("{RESOURCE_ID}", resourceId.kindNameRef())
        .replace("{NAMESPACE}", namespace);
  }

  @VisibleForTesting
  public ProcessResult runK8sExecutableSilent(
      K8sDelegateTaskParams k8sDelegateTaskParams, AbstractExecutable executable) throws Exception {
    return executeCommandSilent(executable, k8sDelegateTaskParams.getWorkingDirectory());
  }

  public String getLatestRevision(
      Kubectl client, KubernetesResourceId resourceId, K8sDelegateTaskParams k8sDelegateTaskParams) throws Exception {
    if (Kind.DeploymentConfig.name().equals(resourceId.getKind())) {
      String rolloutHistoryCommand = getRolloutHistoryCommandForDeploymentConfig(k8sDelegateTaskParams, resourceId);

      try (LogOutputStream emptyLogOutputStream = getEmptyLogOutputStream()) {
        ProcessResult result = executeCommandUsingUtils(k8sDelegateTaskParams, emptyLogOutputStream,
            emptyLogOutputStream, rolloutHistoryCommand, Maps.newHashMap());

        if (result.getExitValue() == 0) {
          String[] lines = result.outputUTF8().split("\\r?\\n");
          return lines[lines.length - 1].split("\t")[0];
        }
      }

    } else {
      RolloutHistoryCommand rolloutHistoryCommand =
          client.rollout().history().resource(resourceId.kindNameRef()).namespace(resourceId.getNamespace());
      ProcessResult result = runK8sExecutableSilent(k8sDelegateTaskParams, rolloutHistoryCommand);
      if (result.getExitValue() == 0) {
        return parseLatestRevisionNumberFromRolloutHistory(result.outputUTF8());
      }
    }

    return "";
  }

  public Integer getCurrentReplicas(Kubectl client, KubernetesResourceId resourceId,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback) throws Exception {
    GetCommand getCommand = client.get()
                                .resources(resourceId.kindNameRef())
                                .namespace(resourceId.getNamespace())
                                .output("jsonpath={$.spec.replicas}");
    ProcessResult result = runK8sExecutableSilent(k8sDelegateTaskParams, getCommand);
    if (result.getExitValue() == 0) {
      if (result.hasOutput()) {
        if (isNotEmpty(result.outputUTF8().trim())) {
          return Integer.valueOf(result.outputUTF8());
        }
        executionLogCallback.saveExecutionLog(
            format("Unable to retrieve current replicas count based on path $.spec.replicas. "
                    + "Resource '%s' may not be supported for scaling.",
                resourceId.getKind()),
            WARN);
      }
    }
    return null;
  }

  @VisibleForTesting
  public ProcessResult executeShellCommand(String commandDirectory, String command, LogOutputStream logErrorStream,
      long timeoutInMillis) throws IOException, InterruptedException, TimeoutException {
    ProcessExecutor processExecutor = new ProcessExecutor()
                                          .timeout(timeoutInMillis, TimeUnit.MILLISECONDS)
                                          .directory(new File(commandDirectory))
                                          .command("/bin/sh", "-c", command)
                                          .readOutput(true)
                                          .redirectError(logErrorStream);

    return processExecutor.execute();
  }

  public boolean dryRunManifests(Kubectl client, List<KubernetesResource> resources,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback) {
    try {
      return dryRunManifests(client, resources, k8sDelegateTaskParams, executionLogCallback, false);
    } catch (Exception ignore) {
      // Not expected if error framework is not enabled. Make the compiler happy until will not adopt error framework
      // for all steps
      return false;
    }
  }
  public boolean dryRunManifests(Kubectl client, List<KubernetesResource> resources,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback, boolean isErrorFrameworkEnabled)
      throws Exception {
    try {
      executionLogCallback.saveExecutionLog(color("\nValidating manifests with Dry Run", White, Bold), INFO);

      FileIo.writeUtf8StringToFile(
          k8sDelegateTaskParams.getWorkingDirectory() + "/manifests-dry-run.yaml", ManifestHelper.toYaml(resources));

      Kubectl overriddenClient = getOverriddenClient(client, resources, k8sDelegateTaskParams);

      final ApplyCommand dryrun = overriddenClient.apply().filename("manifests-dry-run.yaml").dryrun(true);
      ProcessResponse response = runK8sExecutable(k8sDelegateTaskParams, executionLogCallback, dryrun);
      ProcessResult result = response.getProcessResult();
      String resultOutput;
      try {
        resultOutput = result.outputUTF8();
      } catch (Exception ex) {
        resultOutput = EMPTY;
      }
      String resourcesCreated = resultOutput.toLowerCase();
      final StringBuilder resourcesNotCreatedBuilder = new StringBuilder();
      resources.forEach(resource -> {
        String resourceName = resource.getResourceId().kindNameRef();
        if (!(resourcesCreated.contains(resourceName.toLowerCase()))) {
          resourcesNotCreatedBuilder.append(resourceName).append('\n');
        }
      });
      if (result.getExitValue() != 0) {
        logExecutableFailed(result, executionLogCallback);
        if (isErrorFrameworkEnabled) {
          throw new KubernetesCliTaskRuntimeException(response, KubernetesCliCommandType.DRY_RUN,
              client.getVersion() != null ? client.getVersion().toString() : "", resourcesNotCreatedBuilder.toString());
        }
        return false;
      }
    } catch (Exception e) {
      log.error("Exception in running dry-run", e);
      if (isErrorFrameworkEnabled) {
        throw e;
      }

      executionLogCallback.saveExecutionLog("\nFailed.", INFO, FAILURE);
      return false;
    }

    executionLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);
    return true;
  }

  public boolean doStatusCheck(K8sDelegateTaskParams k8sDelegateTaskParams, Kubectl client,
      KubernetesResourceId resourceId, LogCallback executionLogCallback, boolean isErrorFrameworkEnabled)
      throws Exception {
    String workingDirectory = k8sDelegateTaskParams.getWorkingDirectory();
    String ocPath = getLatestVersionOcPath();
    String kubeconfigPath = k8sDelegateTaskParams.getKubeconfigPath();
    String kubectlPath = k8sDelegateTaskParams.getKubectlPath();
    final String eventFormat = "%-7s: %s";
    final String statusFormat = "%n%-7s: %s";

    GetCommand getEventsCommand = client.get()
                                      .resources("events")
                                      .namespace(resourceId.getNamespace())
                                      .output(K8sConstants.eventOutputFormat)
                                      .watchOnly(true);

    executionLogCallback.saveExecutionLog(GetCommand.getPrintableCommand(getEventsCommand.command()) + "\n");

    boolean success = false;

    StartedProcess eventWatchProcess = null;
    try (LogOutputStream watchInfoStream =
             new LogOutputStream() {
               @Override
               protected void processLine(String line) {
                 if (line.contains(resourceId.getName())) {
                   executionLogCallback.saveExecutionLog(format(eventFormat, "Event", line), INFO);
                 }
               }
             };
         LogOutputStream watchErrorStream =
             new LogOutputStream() {
               @Override
               protected void processLine(String line) {
                 executionLogCallback.saveExecutionLog(format(eventFormat, "Event", line), ERROR);
               }
             };
         LogOutputStream statusInfoStream =
             new LogOutputStream() {
               @Override
               protected void processLine(String line) {
                 executionLogCallback.saveExecutionLog(format(statusFormat, "Status", line), INFO);
               }
             };
         ByteArrayOutputStream errorCaptureStream = new ByteArrayOutputStream(1024);
         LogOutputStream statusErrorStream =
             new LogOutputStream() {
               @SneakyThrows
               @Override
               protected void processLine(String line) {
                 executionLogCallback.saveExecutionLog(format(statusFormat, "Status", line), ERROR);
                 errorCaptureStream.write(line.getBytes(UTF_8));
               }
             }) {
      eventWatchProcess = getEventWatchProcess(workingDirectory, getEventsCommand, watchInfoStream, watchErrorStream);

      ProcessResult result;
      String printableExecutedCommand;
      if (Kind.DeploymentConfig.name().equals(resourceId.getKind())) {
        String rolloutStatusCommand = getRolloutStatusCommandForDeploymentConfig(ocPath, kubeconfigPath, resourceId);
        printableExecutedCommand = rolloutStatusCommand.substring(rolloutStatusCommand.indexOf("oc --kubeconfig"));

        executionLogCallback.saveExecutionLog(printableExecutedCommand + "\n");

        result = executeCommandUsingUtils(
            workingDirectory, statusInfoStream, statusErrorStream, rolloutStatusCommand, Maps.newHashMap());
      } else {
        RolloutStatusCommand rolloutStatusCommand = client.rollout()
                                                        .status()
                                                        .resource(resourceId.kindNameRef())
                                                        .namespace(resourceId.getNamespace())
                                                        .watch(true);
        printableExecutedCommand = RolloutStatusCommand.getPrintableCommand(rolloutStatusCommand.command());

        executionLogCallback.saveExecutionLog(printableExecutedCommand + "\n");

        result = rolloutStatusCommand.execute(
            workingDirectory, statusInfoStream, statusErrorStream, false, Collections.emptyMap());
      }
      success = result.getExitValue() == 0;

      if (!success) {
        log.warn(result.outputUTF8());
        if (isErrorFrameworkEnabled) {
          ProcessResponse processResponse =
              ProcessResponse.builder()
                  .errorMessage(ExceptionMessageSanitizer.sanitizeMessage(errorCaptureStream.toString()))
                  .processResult(result)
                  .printableCommand(printableExecutedCommand)
                  .kubectlPath(kubectlPath)
                  .build();
          throw new KubernetesCliTaskRuntimeException(processResponse, KubernetesCliCommandType.STEADY_STATE_CHECK);
        }
      }
      return success;
    } catch (Exception e) {
      log.error("Exception while doing statusCheck", e);
      if (isErrorFrameworkEnabled) {
        throw e;
      }

      executionLogCallback.saveExecutionLog("\nFailed.", INFO, FAILURE);
      return false;
    } finally {
      if (eventWatchProcess != null) {
        eventWatchProcess.getProcess().destroyForcibly().waitFor();
      }
      if (success) {
        executionLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);

      } else {
        executionLogCallback.saveExecutionLog("\nFailed.", INFO, FAILURE);
      }
    }
  }

  public boolean doStatusCheck(Kubectl client, KubernetesResourceId resourceId,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback) throws Exception {
    return doStatusCheck(client, resourceId, k8sDelegateTaskParams, executionLogCallback, false);
  }

  public boolean doStatusCheck(Kubectl client, KubernetesResourceId resourceId,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback, boolean isErrorFrameworkEnabled)
      throws Exception {
    return doStatusCheck(k8sDelegateTaskParams, client, resourceId, executionLogCallback, isErrorFrameworkEnabled);
  }

  public boolean getJobStatus(K8sDelegateTaskParams k8sDelegateTaskParams, LogOutputStream statusInfoStream,
      LogOutputStream statusErrorStream, GetJobCommand jobCompleteCommand, GetJobCommand jobFailedCommand,
      GetJobCommand jobStatusCommand, GetJobCommand jobCompletionTimeCommand) throws Exception {
    return getJobStatus(k8sDelegateTaskParams, statusInfoStream, statusErrorStream, jobCompleteCommand,
        jobFailedCommand, jobStatusCommand, jobCompletionTimeCommand, false);
  }

  public boolean getJobStatus(K8sDelegateTaskParams k8sDelegateTaskParams, LogOutputStream statusInfoStream,
      LogOutputStream statusErrorStream, GetJobCommand jobCompleteCommand, GetJobCommand jobFailedCommand,
      GetJobCommand jobStatusCommand, GetJobCommand jobCompletionTimeCommand, boolean isErrorFrameworkEnabled)
      throws Exception {
    while (true) {
      jobStatusCommand.execute(k8sDelegateTaskParams.getWorkingDirectory(), statusInfoStream, statusErrorStream, false,
          Collections.emptyMap());

      ProcessResult result = jobCompleteCommand.execute(
          k8sDelegateTaskParams.getWorkingDirectory(), null, null, false, Collections.emptyMap());

      boolean success = 0 == result.getExitValue();
      if (!success) {
        log.warn(result.outputUTF8());
        if (isErrorFrameworkEnabled) {
          String explanation = isNotEmpty(result.outputUTF8())
              ? format(KubernetesExceptionExplanation.WAIT_FOR_STEADY_STATE_FAILED_OUTPUT,
                  getPrintableCommand(jobCompleteCommand.command()), result.getExitValue(), result.outputUTF8())
              : format(KubernetesExceptionExplanation.WAIT_FOR_STEADY_STATE_FAILED,
                  getPrintableCommand(jobCompleteCommand.command()), result.getExitValue());
          throw NestedExceptionUtils.hintWithExplanationException(
              KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_CLI_FAILED, explanation,
              new KubernetesTaskException(KubernetesExceptionMessages.WAIT_FOR_STEADY_STATE_FAILED));
        }

        return false;
      }

      // cli command outputs with single quotes
      String jobStatus = result.outputUTF8().replace("'", "");
      if ("True".equals(jobStatus)) {
        result = jobCompletionTimeCommand.execute(
            k8sDelegateTaskParams.getWorkingDirectory(), null, null, false, Collections.emptyMap());
        success = 0 == result.getExitValue();
        if (!success) {
          log.warn(result.outputUTF8());
          if (isErrorFrameworkEnabled) {
            String explanation = isNotEmpty(result.outputUTF8())
                ? format(KubernetesExceptionExplanation.WAIT_FOR_STEADY_STATE_FAILED_OUTPUT,
                    getPrintableCommand(jobCompletionTimeCommand.command()), result.getExitValue(), result.outputUTF8())
                : format(KubernetesExceptionExplanation.WAIT_FOR_STEADY_STATE_FAILED,
                    getPrintableCommand(jobCompletionTimeCommand.command()), result.getExitValue());
            throw NestedExceptionUtils.hintWithExplanationException(
                KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_CLI_FAILED, explanation,
                new KubernetesTaskException(KubernetesExceptionMessages.WAIT_FOR_STEADY_STATE_FAILED));
          }

          return false;
        }

        String completionTime = result.outputUTF8().replace("'", "");
        if (isNotBlank(completionTime)) {
          return true;
        }
      }

      result = jobFailedCommand.execute(
          k8sDelegateTaskParams.getWorkingDirectory(), null, null, false, Collections.emptyMap());

      success = 0 == result.getExitValue();
      if (!success) {
        log.warn(result.outputUTF8());

        if (isErrorFrameworkEnabled) {
          String explanation = isNotEmpty(result.outputUTF8())
              ? format(KubernetesExceptionExplanation.WAIT_FOR_STEADY_STATE_FAILED_OUTPUT,
                  getPrintableCommand(jobFailedCommand.command()), result.getExitValue(), result.outputUTF8())
              : format(KubernetesExceptionExplanation.WAIT_FOR_STEADY_STATE_FAILED,
                  getPrintableCommand(jobFailedCommand.command()), result.getExitValue());
          throw NestedExceptionUtils.hintWithExplanationException(
              KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_CLI_FAILED, explanation,
              new KubernetesTaskException(KubernetesExceptionMessages.WAIT_FOR_STEADY_STATE_FAILED));
        }
        return false;
      }

      jobStatus = result.outputUTF8().replace("'", "");
      if ("True".equals(jobStatus)) {
        if (isErrorFrameworkEnabled) {
          throw NestedExceptionUtils.hintWithExplanationException(
              KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_JOB_FAILED,
              KubernetesExceptionExplanation.WAIT_FOR_STEADY_STATE_JOB_FAILED,
              new KubernetesTaskException(KubernetesExceptionMessages.WAIT_FOR_STEADY_STATE_FAILED));
        }
        return false;
      }

      sleep(ofSeconds(5));
    }
  }

  public boolean doStatusCheckForJob(Kubectl client, KubernetesResourceId resourceId,
      K8sDelegateTaskParams k8sDelegateTaskParams, String statusFormat, LogCallback executionLogCallback,
      boolean isErrorFrameworkEnabled) throws Exception {
    try (LogOutputStream statusInfoStream =
             new LogOutputStream() {
               @Override
               protected void processLine(String line) {
                 executionLogCallback.saveExecutionLog(
                     format(statusFormat, "Status", resourceId.getName(), line), INFO);
               }
             };
         LogOutputStream statusErrorStream =
             new LogOutputStream() {
               @Override
               protected void processLine(String line) {
                 executionLogCallback.saveExecutionLog(
                     format(statusFormat, "Status", resourceId.getName(), line), ERROR);
               }
             }) {
      GetJobCommand jobCompleteCommand = client.getJobCommand(resourceId.getName(), resourceId.getNamespace())
                                             .output("jsonpath='{.status.conditions[?(@.type==\"Complete\")].status}'");
      GetJobCommand jobFailedCommand = client.getJobCommand(resourceId.getName(), resourceId.getNamespace())
                                           .output("jsonpath='{.status.conditions[?(@.type==\"Failed\")].status}'");
      GetJobCommand jobStatusCommand =
          client.getJobCommand(resourceId.getName(), resourceId.getNamespace()).output("jsonpath='{.status}'");
      GetJobCommand jobCompletionTimeCommand = client.getJobCommand(resourceId.getName(), resourceId.getNamespace())
                                                   .output("jsonpath='{.status.completionTime}'");

      executionLogCallback.saveExecutionLog(getPrintableCommand(jobStatusCommand.command()) + "\n");

      return getJobStatus(k8sDelegateTaskParams, statusInfoStream, statusErrorStream, jobCompleteCommand,
          jobFailedCommand, jobStatusCommand, jobCompletionTimeCommand, isErrorFrameworkEnabled);
    }
  }

  public boolean doStatusCheckForWorkloads(Kubectl client, KubernetesResourceId resourceId,
      K8sDelegateTaskParams k8sDelegateTaskParams, String statusFormat, LogCallback executionLogCallback,
      boolean isErrorFrameworkEnabled) throws Exception {
    try (ByteArrayOutputStream errorCaptureStream = new ByteArrayOutputStream(1024);
         LogOutputStream statusErrorStream =
             new LogOutputStream() {
               @SneakyThrows
               @Override
               protected void processLine(String line) {
                 executionLogCallback.saveExecutionLog(
                     format(statusFormat, "Status", resourceId.getName(), line), ERROR);
                 errorCaptureStream.write(line.getBytes(UTF_8));
               }
             };
         LogOutputStream statusInfoStream =
             new LogOutputStream() {
               @Override
               protected void processLine(String line) {
                 executionLogCallback.saveExecutionLog(
                     format(statusFormat, "Status", resourceId.getName(), line), INFO);
               }
             }) {
      ProcessResult result;
      String printableExecutedCommand;

      if (Kind.DeploymentConfig.name().equals(resourceId.getKind())) {
        String ocPath = getLatestVersionOcPath();
        String rolloutStatusCommand =
            getRolloutStatusCommandForDeploymentConfig(ocPath, k8sDelegateTaskParams.getKubeconfigPath(), resourceId);

        printableExecutedCommand = rolloutStatusCommand.substring(rolloutStatusCommand.indexOf("oc --kubeconfig"));
        executionLogCallback.saveExecutionLog(printableExecutedCommand + "\n");

        result = executeCommandUsingUtils(
            k8sDelegateTaskParams, statusInfoStream, statusErrorStream, rolloutStatusCommand, Maps.newHashMap());
      } else {
        RolloutStatusCommand rolloutStatusCommand = client.rollout()
                                                        .status()
                                                        .resource(resourceId.kindNameRef())
                                                        .namespace(resourceId.getNamespace())
                                                        .watch(true);

        printableExecutedCommand = getPrintableCommand(rolloutStatusCommand.command());
        executionLogCallback.saveExecutionLog(printableExecutedCommand + "\n");

        Map<String, String> env = new HashMap<>();
        if (isNotEmpty(k8sDelegateTaskParams.getGcpKeyFilePath())) {
          env.put("GOOGLE_APPLICATION_CREDENTIALS", k8sDelegateTaskParams.getGcpKeyFilePath());
        }

        result = rolloutStatusCommand.execute(
            k8sDelegateTaskParams.getWorkingDirectory(), statusInfoStream, statusErrorStream, false, env);
      }

      boolean success = 0 == result.getExitValue();
      if (!success) {
        log.warn(result.outputUTF8());
        if (isErrorFrameworkEnabled) {
          ProcessResponse processResponse =
              ProcessResponse.builder()
                  .errorMessage(ExceptionMessageSanitizer.sanitizeMessage(errorCaptureStream.toString()))
                  .processResult(result)
                  .printableCommand(printableExecutedCommand)
                  .kubectlPath(k8sDelegateTaskParams.getKubectlPath())
                  .build();
          throw new KubernetesCliTaskRuntimeException(processResponse, KubernetesCliCommandType.STEADY_STATE_CHECK);
        }
      }

      return success;
    }
  }

  public boolean doStatusCheckForAllResources(Kubectl client, List<KubernetesResourceId> resourceIds,
      K8sDelegateTaskParams k8sDelegateTaskParams, String namespace, LogCallback executionLogCallback,
      boolean denoteOverallSuccess) throws Exception {
    return doStatusCheckForAllResources(
        client, resourceIds, k8sDelegateTaskParams, namespace, executionLogCallback, denoteOverallSuccess, false);
  }

  public boolean doStatusCheckForAllResources(Kubectl client, List<KubernetesResourceId> resourceIds,
      K8sDelegateTaskParams k8sDelegateTaskParams, String namespace, LogCallback executionLogCallback,
      boolean denoteOverallSuccess, boolean isErrorFrameworkEnabled) throws Exception {
    if (isEmpty(resourceIds)) {
      return true;
    }

    int maxResourceNameLength = 0;
    for (KubernetesResourceId kubernetesResourceId : resourceIds) {
      maxResourceNameLength = Math.max(maxResourceNameLength, kubernetesResourceId.getName().length());
    }

    final String eventErrorFormat = "%-7s: %s";
    final String eventInfoFormat = "%-7s: %-" + maxResourceNameLength + "s   %s";
    final String statusFormat = "%n%-7s: %-" + maxResourceNameLength + "s   %s";

    Set<String> namespaces = resourceIds.stream().map(KubernetesResourceId::getNamespace).collect(toSet());
    namespaces.add(namespace);
    List<GetCommand> getEventCommands = namespaces.stream()
                                            .map(ns
                                                -> client.get()
                                                       .resources("events")
                                                       .namespace(ns)
                                                       .output(K8sConstants.eventWithNamespaceOutputFormat)
                                                       .watchOnly(true))
                                            .collect(toList());

    for (GetCommand cmd : getEventCommands) {
      executionLogCallback.saveExecutionLog(getPrintableCommand(cmd.command()) + "\n");
    }

    boolean success = false;

    List<StartedProcess> eventWatchProcesses = new ArrayList<>();
    try (LogOutputStream watchInfoStream =
             new LogOutputStream() {
               @Override
               protected void processLine(String line) {
                 Optional<KubernetesResourceId> filteredResourceId =
                     resourceIds.parallelStream()
                         .filter(kubernetesResourceId
                             -> line.contains(isNotBlank(kubernetesResourceId.getNamespace())
                                        ? kubernetesResourceId.getNamespace()
                                        : namespace)
                                 && line.contains(kubernetesResourceId.getName()))
                         .findFirst();

                 filteredResourceId.ifPresent(kubernetesResourceId
                     -> executionLogCallback.saveExecutionLog(
                         format(eventInfoFormat, "Event", kubernetesResourceId.getName(), line), INFO));
               }
             };
         LogOutputStream watchErrorStream =
             new LogOutputStream() {
               @Override
               protected void processLine(String line) {
                 executionLogCallback.saveExecutionLog(format(eventErrorFormat, "Event", line), ERROR);
               }
             }) {
      for (GetCommand getEventsCommand : getEventCommands) {
        eventWatchProcesses.add(getEventWatchProcess(
            k8sDelegateTaskParams.getWorkingDirectory(), getEventsCommand, watchInfoStream, watchErrorStream));
      }

      for (KubernetesResourceId kubernetesResourceId : resourceIds) {
        if (Kind.Job.name().equals(kubernetesResourceId.getKind())) {
          success = doStatusCheckForJob(client, kubernetesResourceId, k8sDelegateTaskParams, statusFormat,
              executionLogCallback, isErrorFrameworkEnabled);
        } else {
          success = doStatusCheckForWorkloads(client, kubernetesResourceId, k8sDelegateTaskParams, statusFormat,
              executionLogCallback, isErrorFrameworkEnabled);
        }

        if (!success) {
          break;
        }
      }

      return success;
    } catch (Exception e) {
      log.error("Exception while doing statusCheck", e);
      if (isErrorFrameworkEnabled) {
        throw e;
      }

      executionLogCallback.saveExecutionLog("\nFailed.", INFO, FAILURE);
      return false;
    } finally {
      for (StartedProcess eventWatchProcess : eventWatchProcesses) {
        eventWatchProcess.getProcess().destroyForcibly().waitFor();
      }
      if (success) {
        if (denoteOverallSuccess) {
          executionLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);
        }
      } else {
        executionLogCallback.saveExecutionLog(
            format("%nStatus check for resources in namespace [%s] failed.", namespace), INFO, FAILURE);
      }
    }
  }

  public String getResourcesInTableFormat(List<KubernetesResource> resources) {
    return getResourcesIdsInTableFormat(resources.stream().map(KubernetesResource::getResourceId).collect(toList()));
  }

  public String getResourcesIdsInTableFormat(List<KubernetesResourceId> resourceIds) {
    int maxKindLength = 16;
    int maxNameLength = 36;
    for (KubernetesResourceId id : resourceIds) {
      if (id.getKind().length() > maxKindLength) {
        maxKindLength = id.getKind().length();
      }

      if (id.getName().length() > maxNameLength) {
        maxNameLength = id.getName().length();
      }
    }

    maxKindLength += 4;
    maxNameLength += 4;

    StringBuilder sb = new StringBuilder(1024);
    String tableFormat = "%-" + maxKindLength + "s%-" + maxNameLength + "s%-10s";
    sb.append(System.lineSeparator())
        .append(color(format(tableFormat, "Kind", "Name", "Versioned"), White, Bold))
        .append(System.lineSeparator());

    for (KubernetesResourceId id : resourceIds) {
      sb.append(color(format(tableFormat, id.getKind(), id.getName(), id.isVersioned()), Gray))
          .append(System.lineSeparator());
    }

    return sb.toString();
  }

  @VisibleForTesting
  public String generateTruncatedFileListForLogging(Path basePath, Stream<Path> paths) {
    StringBuilder sb = new StringBuilder(1024);
    AtomicInteger filesTraversed = new AtomicInteger(0);
    paths.filter(Files::isRegularFile).forEach(each -> {
      if (filesTraversed.getAndIncrement() <= K8sConstants.FETCH_FILES_DISPLAY_LIMIT) {
        sb.append(color(format("- %s", getRelativePath(each.toString(), basePath.toString())), Gray))
            .append(System.lineSeparator());
      }
    });
    if (filesTraversed.get() > K8sConstants.FETCH_FILES_DISPLAY_LIMIT) {
      sb.append(color(format("- ..%d more", filesTraversed.get() - K8sConstants.FETCH_FILES_DISPLAY_LIMIT), Gray))
          .append(System.lineSeparator());
    }

    return sb.toString();
  }

  @VisibleForTesting
  public String getManifestFileNamesInLogFormat(String manifestFilesDirectory) throws IOException {
    Path basePath = Paths.get(manifestFilesDirectory);
    try (Stream<Path> paths = Files.walk(basePath)) {
      return generateTruncatedFileListForLogging(basePath, paths);
    }
  }

  public void deleteSkippedManifestFiles(String manifestFilesDirectory, LogCallback executionLogCallback)
      throws Exception {
    List<Path> skippedFilesList;
    Path directory = Paths.get(manifestFilesDirectory);

    try {
      skippedFilesList = getFilesUnderPathMatchesFirstLine(
          directory.toString(), line -> line.contains(SKIP_FILE_FOR_DEPLOY_PLACEHOLDER_TEXT));
    } catch (Exception ex) {
      log.info(ExceptionUtils.getMessage(ex));
      throw new WingsException("Failed to get files. Error: " + ExceptionUtils.getMessage(ex));
    }

    if (isNotEmpty(skippedFilesList)) {
      executionLogCallback.saveExecutionLog("Following manifest files are skipped for applying");
      for (Path path : skippedFilesList) {
        executionLogCallback.saveExecutionLog(color(path.toString(), Yellow, Bold));

        String filePath = Paths.get(manifestFilesDirectory, path.toString()).toString();
        FileIo.deleteFileIfExists(filePath);
      }

      executionLogCallback.saveExecutionLog("\n");
    }
  }

  public List<KubernetesResource> readManifests(List<FileData> manifestFiles, LogCallback executionLogCallback) {
    return readManifests(manifestFiles, executionLogCallback, false);
  }

  public List<KubernetesResource> readManifests(
      List<FileData> manifestFiles, LogCallback executionLogCallback, boolean isErrorFrameworkSupported) {
    List<KubernetesResource> result = new ArrayList<>();

    for (FileData manifestFile : manifestFiles) {
      if (isValidManifestFile(manifestFile.getFileName())) {
        try {
          result.addAll(ManifestHelper.processYaml(manifestFile.getFileContent()));
        } catch (Exception e) {
          executionLogCallback.saveExecutionLog("Exception while processing " + manifestFile.getFileName(), ERROR);
          if (isErrorFrameworkSupported) {
            throwReadManifestExceptionWithHintAndExplanation(e, manifestFile.getFileName());
          }
          throw e;
        }
      }
    }

    return result.stream().sorted(new KubernetesResourceComparer()).collect(toList());
  }

  private void throwReadManifestExceptionWithHintAndExplanation(Exception e, String manifestFileName) {
    String explanation = e.getMessage();
    if (e instanceof KubernetesYamlException) {
      explanation += ":" + ((KubernetesYamlException) e).getParams().get("reason");
    }

    throw NestedExceptionUtils.hintWithExplanationException(KubernetesExceptionHints.READ_MANIFEST_FAILED, explanation,
        new KubernetesTaskException(format(KubernetesExceptionMessages.READ_MANIFEST_FAILED, manifestFileName)));
  }

  public List<FileData> readManifestFilesFromDirectory(String manifestFilesDirectory) {
    List<FileData> fileDataList;
    Path directory = Paths.get(manifestFilesDirectory);

    try {
      fileDataList = getFilesUnderPath(directory.toString());
    } catch (Exception ex) {
      log.error(ExceptionUtils.getMessage(ex));
      throw new WingsException("Failed to get files. Error: " + ExceptionUtils.getMessage(ex));
    }

    List<FileData> manifestFiles = new ArrayList<>();
    for (FileData fileData : fileDataList) {
      if (isValidManifestFile(fileData.getFilePath())) {
        manifestFiles.add(FileData.builder()
                              .fileName(fileData.getFilePath())
                              .fileContent(new String(fileData.getFileBytes(), UTF_8))
                              .build());
      } else {
        log.info("Found file [{}] with unsupported extension", fileData.getFilePath());
      }
    }

    return manifestFiles;
  }

  public List<FileData> replaceManifestPlaceholdersWithLocalDelegateSecrets(List<FileData> manifestFiles) {
    List<FileData> updatedManifestFiles = new ArrayList<>();
    for (FileData manifestFile : manifestFiles) {
      updatedManifestFiles.add(
          FileData.builder()
              .fileName(manifestFile.getFileName())
              .fileContent(delegateLocalConfigService.replacePlaceholdersWithLocalConfig(manifestFile.getFileContent()))
              .build());
    }

    return updatedManifestFiles;
  }

  public List<KubernetesResource> readManifestAndOverrideLocalSecrets(
      List<FileData> manifestFiles, LogCallback executionLogCallback, boolean overrideLocalSecrets) {
    return readManifestAndOverrideLocalSecrets(manifestFiles, executionLogCallback, overrideLocalSecrets, false);
  }

  public List<KubernetesResource> readManifestAndOverrideLocalSecrets(List<FileData> manifestFiles,
      LogCallback executionLogCallback, boolean overrideLocalSecrets, boolean isErrorFrameworkSupported) {
    if (overrideLocalSecrets) {
      manifestFiles = replaceManifestPlaceholdersWithLocalDelegateSecrets(manifestFiles);
    }
    return readManifests(manifestFiles, executionLogCallback, isErrorFrameworkSupported);
  }

  public String writeValuesToFile(String directoryPath, List<String> valuesFiles) throws Exception {
    StringBuilder valuesFilesOptionsBuilder = new StringBuilder(128);

    for (int i = 0; i < valuesFiles.size(); i++) {
      validateValuesFileContents(valuesFiles.get(i));
      String valuesFileName = format("values-%d.yaml", i);
      FileIo.writeUtf8StringToFile(directoryPath + '/' + valuesFileName, valuesFiles.get(i));
      valuesFilesOptionsBuilder.append(" -f ").append(valuesFileName);
    }

    return valuesFilesOptionsBuilder.toString();
  }

  public static JSONObject readAndConvertYamlToJson(String yamlFilePath, LogCallback executionLogCallback)
      throws IOException {
    Yaml yaml = new Yaml();
    Map<String, Object> obj = yaml.load(getYaml(yamlFilePath));
    if (isNull(obj)) {
      executionLogCallback.saveExecutionLog(color("File is Empty in the path " + yamlFilePath, Yellow, Bold), WARN);
    }
    log.debug("Returning json of yaml file  ", yamlFilePath);
    return new JSONObject(obj);
  }

  private static String getYaml(String filePath) throws IOException {
    log.debug("Reading yaml file ", filePath);
    return new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
  }

  public String convertJsonToYaml(JSONObject jsonObject) {
    String prettyJSONString = jsonObject.toString();
    Yaml yaml = new Yaml(new io.kubernetes.client.util.Yaml.CustomConstructor(Object.class, new LoaderOptions()),
        new BooleanPatchedRepresenter());
    Map<String, Object> map = yaml.load(prettyJSONString);
    return yaml.dump(map);
  }

  public JSONArray updatePatchList(JSONObject kustomizationJson, JSONArray patchList) {
    if (kustomizationJson.has(patchFieldName)) {
      JSONArray newPatchList = (JSONArray) kustomizationJson.get(patchFieldName);
      for (Object jsonObject : patchList) {
        newPatchList.put(jsonObject);
      }
      return newPatchList;
    }
    return patchList;
  }

  public void updateKustomizationYaml(String kustomizePath, JSONArray patchList, LogCallback executionLogCallback)
      throws IOException {
    String kustomizationYamlPath = Paths.get(kustomizePath, kustomizeFileNameYaml).toString();
    String kustomizationYmlPath = Paths.get(kustomizePath, kustomizeFileNameYml).toString();
    String kustomizationPath = new File(kustomizationYmlPath).exists() ? kustomizationYmlPath : kustomizationYamlPath;
    JSONObject kustomizationJson = readAndConvertYamlToJson(kustomizationPath, executionLogCallback);

    JSONArray updatedPatchList = updatePatchList(kustomizationJson, patchList);
    kustomizationJson.put(patchFieldName, updatedPatchList);
    JSONObject patchesStrategicMerge = new JSONObject();
    patchesStrategicMerge.put(patchFieldName, updatedPatchList);
    executionLogCallback.saveExecutionLog(
        color("PatchesStrategicMerge Field in Kustomization Yaml after update :\n", White, Bold));
    executionLogCallback.saveExecutionLog(convertJsonToYaml(patchesStrategicMerge));

    String newKustomize = convertJsonToYaml(kustomizationJson);
    FileIo.deleteFileIfExists(kustomizationPath);
    FileIo.writeUtf8StringToFile(kustomizationPath, newKustomize);
  }

  public JSONArray writePatchesToDirectory(String kustomizePath, List<String> patchesFiles) throws IOException {
    StringBuilder patchesFilesOptionsBuilder = new StringBuilder(128);
    JSONArray patchList = new JSONArray();
    String kustomizePatchesDir = kustomizePatchesDirPrefix + RandomStringUtils.randomAlphanumeric(4);
    Path outputTemporaryDir = Files.createDirectories(Paths.get(kustomizePath, kustomizePatchesDir));

    for (int i = 0; i < patchesFiles.size(); i++) {
      validateValuesFileContents(patchesFiles.get(i));
      String patchesFileName = format(patchYaml, i);
      FileIo.writeUtf8StringToFile(
          Paths.get(outputTemporaryDir.toString(), patchesFileName).toString(), patchesFiles.get(i));
      patchesFilesOptionsBuilder.append(" -f ").append(patchesFileName);
      patchList.put(Paths.get(kustomizePatchesDir, patchesFileName));
    }

    log.info("Patches file options: " + patchesFilesOptionsBuilder.toString());
    return patchList;
  }

  public void savingPatchesToDirectory(
      String kustomizePath, List<String> patchesFiles, LogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog("\nUpdating patchesStrategicMerge in Kustomization Yaml :\n");

    if (isEmpty(patchesFiles)) {
      executionLogCallback.saveExecutionLog("\nNo Patches files found. Skipping kustomization.yaml updation\n");
      return;
    }

    try {
      JSONArray patchList = writePatchesToDirectory(kustomizePath, patchesFiles);
      updateKustomizationYaml(kustomizePath, patchList, executionLogCallback);
    } catch (IOException ioException) {
      log.error("Error in Updating kustomization.yaml " + ioException);
      throw new IllegalArgumentException(
          " Unable to find one of 'kustomization.yaml' or 'kustomization.yml'  in directory " + kustomizePath);
    }
  }

  public List<FileData> renderManifestFilesForGoTemplate(K8sDelegateTaskParams k8sDelegateTaskParams,
      List<FileData> manifestFiles, List<String> valuesFiles, LogCallback executionLogCallback, long timeoutInMillis)
      throws Exception {
    if (isEmpty(valuesFiles)) {
      executionLogCallback.saveExecutionLog("No values.yaml file found. Skipping template rendering.");
      return manifestFiles;
    }

    String valuesFileOptions =
        createValuesFileOptions(k8sDelegateTaskParams.getWorkingDirectory(), valuesFiles, executionLogCallback);
    log.info("Values file options: " + valuesFileOptions);

    List<FileData> result = new ArrayList<>();

    executionLogCallback.saveExecutionLog(color("\nRendering manifest files using go template", White, Bold));
    executionLogCallback.saveExecutionLog(
        color("Only manifest files with [.yaml] or [.yml] extension will be processed", White, Bold));

    for (FileData manifestFile : manifestFiles) {
      if (StringUtils.equals(values_filename, manifestFile.getFileName())) {
        continue;
      }

      FileIo.writeUtf8StringToFile(
          k8sDelegateTaskParams.getWorkingDirectory() + "/template.yaml", manifestFile.getFileContent());

      try (ByteArrayOutputStream errorCaptureStream = new ByteArrayOutputStream(1024);
           LogOutputStream logErrorStream =
               getExecutionLogOutputStream(executionLogCallback, ERROR, errorCaptureStream)) {
        String goTemplateCommand = encloseWithQuotesIfNeeded(k8sDelegateTaskParams.getGoTemplateClientPath())
            + " -t template.yaml " + valuesFileOptions;
        ProcessResult processResult = executeShellCommand(
            k8sDelegateTaskParams.getWorkingDirectory(), goTemplateCommand, logErrorStream, timeoutInMillis);

        if (processResult.getExitValue() != 0) {
          throw NestedExceptionUtils.hintWithExplanationException(
              KubernetesExceptionHints.MANIFEST_RENDER_ERROR_GO_TEMPLATE,
              format(KubernetesExceptionExplanation.MANIFEST_RENDER_ERROR_GO_TEMPLATE, errorCaptureStream.toString()),
              new KubernetesTaskException(getErrorMessageIfProcessFailed(
                  format("Failed to render template for %s.", manifestFile.getFileName()), processResult)));
        }

        String fileContent = processResult.outputUTF8();
        logIfRenderHasValuesMissing(executionLogCallback, fileContent);
        result.add(FileData.builder().fileName(manifestFile.getFileName()).fileContent(fileContent).build());
      }
    }

    return result;
  }

  private void logIfRenderHasValuesMissing(LogCallback executionLogCallback, String fileContent) {
    if (fileContent.contains(VALUE_MISSING_REPLACEMENT)) {
      log.debug("Rendered template value missing, replaced with {}!", VALUE_MISSING_REPLACEMENT);
      String logLine = "Rendered template is missing values (replaced with " + VALUE_MISSING_REPLACEMENT + ")!";
      executionLogCallback.saveExecutionLog(color(logLine, Yellow, Bold), WARN);
    }
  }

  public String generateResourceIdentifier(KubernetesResourceId resourceId) {
    return new StringBuilder(128)
        .append(resourceId.getNamespace())
        .append('/')
        .append(resourceId.getKind())
        .append('/')
        .append(resourceId.getName())
        .toString();
  }

  public List<KubernetesResourceId> fetchAllResourcesForRelease(
      String releaseName, KubernetesConfig kubernetesConfig, LogCallback executionLogCallback) throws IOException {
    executionLogCallback.saveExecutionLog("Fetching all resources created for release: " + releaseName);

    final V1ConfigMap releaseConfigMap = kubernetesContainerService.getConfigMap(kubernetesConfig, releaseName);
    final V1Secret releaseSecret = kubernetesContainerService.getSecret(kubernetesConfig, releaseName);

    if (!(releaseHistoryPresent(releaseConfigMap) || releaseHistoryPresent(releaseSecret))) {
      executionLogCallback.saveExecutionLog("No resource history was available");
      return emptyList();
    }

    String releaseHistoryDataString = releaseHistoryPresent(releaseSecret)
        ? kubernetesContainerService.fetchReleaseHistoryValue(releaseSecret)
        : kubernetesContainerService.fetchReleaseHistoryValue(releaseConfigMap);
    ReleaseHistory releaseHistory = ReleaseHistory.createFromData(releaseHistoryDataString);

    if (isEmpty(releaseHistory.getReleases())) {
      return emptyList();
    }

    Map<String, KubernetesResourceId> kubernetesResourceIdMap = new HashMap<>();
    for (K8sLegacyRelease release : releaseHistory.getReleases()) {
      if (isNotEmpty(release.getResources())) {
        release.getResources().forEach(
            resource -> kubernetesResourceIdMap.put(generateResourceIdentifier(resource), resource));
      }
    }

    if (releaseConfigMap != null) {
      KubernetesResourceId harnessGeneratedCMResource = KubernetesResourceId.builder()
                                                            .kind(releaseConfigMap.getKind())
                                                            .name(releaseName)
                                                            .namespace(kubernetesConfig.getNamespace())
                                                            .build();
      kubernetesResourceIdMap.put(generateResourceIdentifier(harnessGeneratedCMResource), harnessGeneratedCMResource);
    }
    if (releaseSecret != null) {
      KubernetesResourceId harnessGeneratedSecretResource = KubernetesResourceId.builder()
                                                                .kind(releaseSecret.getKind())
                                                                .name(releaseName)
                                                                .namespace(kubernetesConfig.getNamespace())
                                                                .build();
      kubernetesResourceIdMap.put(
          generateResourceIdentifier(harnessGeneratedSecretResource), harnessGeneratedSecretResource);
    }
    return new ArrayList<>(kubernetesResourceIdMap.values());
  }

  private boolean releaseHistoryPresent(V1ConfigMap configMap) {
    return configMap != null && isNotEmpty(configMap.getData())
        && isNotBlank(configMap.getData().get(ReleaseHistoryKeyName));
  }

  private boolean releaseHistoryPresent(V1Secret secret) {
    return secret != null && isNotEmpty(secret.getData())
        && ArrayUtils.isNotEmpty(secret.getData().get(ReleaseHistoryKeyName));
  }

  public List<FileData> readFilesFromDirectory(
      String directory, List<String> filePaths, LogCallback executionLogCallback) {
    List<FileData> manifestFiles = new ArrayList<>();

    for (String filepath : filePaths) {
      if (isValidManifestFile(filepath)) {
        Path path = Paths.get(directory, filepath);
        byte[] fileBytes;

        try {
          fileBytes = Files.readAllBytes(path);
        } catch (NoSuchFileException nsfe) {
          log.info(format("Failed to read file at path [%s].%nError: %s", filepath, ExceptionUtils.getMessage(nsfe)));
          executionLogCallback.saveExecutionLog(format("Failed to read file at path [%s]", filepath), INFO);
          executionLogCallback.saveExecutionLog(
              color(format("%nPossible reasons: %n\t 1. File '%s' does not exist!", filepath), Red, Bold), ERROR);
          throw new HintException(format(KubernetesExceptionHints.FAILED_TO_READ_FILE, filepath),
              NestedExceptionUtils.hintWithExplanationException(
                  format(KubernetesExceptionHints.CHECK_IF_FILE_EXIST, filepath),
                  format(KubernetesExceptionExplanation.FAILED_TO_READ_FILE, filepath),
                  new KubernetesTaskException(
                      format(KubernetesExceptionMessages.FAILED_TO_READ_MANIFEST_FILE, filepath))));
        } catch (IOException ioe) {
          log.info(format("Failed to read file at path [%s].%nError: %s", filepath, ExceptionUtils.getMessage(ioe)));
          executionLogCallback.saveExecutionLog(format("Failed to read file at path [%s]", filepath), INFO);
          throw new InvalidRequestException(
              format("Failed to read file at path [%s].%nError: %s", filepath, ExceptionUtils.getMessage(ioe)));
        }

        manifestFiles.add(FileData.builder().fileName(filepath).fileContent(new String(fileBytes, UTF_8)).build());
      } else {
        executionLogCallback.saveExecutionLog(
            color(format("Ignoring file [%s] with unsupported extension", filepath), Yellow, Bold));
      }
    }

    return manifestFiles;
  }

  public boolean doStatusCheckForAllCustomResources(Kubectl client, List<KubernetesResource> resources,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback, boolean denoteOverallSuccess,
      long timeoutInMillis) throws Exception {
    return doStatusCheckForAllCustomResources(
        client, resources, k8sDelegateTaskParams, executionLogCallback, denoteOverallSuccess, timeoutInMillis, false);
  }

  public boolean doStatusCheckForAllCustomResources(Kubectl client, List<KubernetesResource> resources,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback, boolean denoteOverallSuccess,
      long timeoutInMillis, boolean isErrorFrameworkEnabled) throws Exception {
    List<KubernetesResourceId> resourceIds =
        resources.stream().map(KubernetesResource::getResourceId).collect(Collectors.toList());
    if (isEmpty(resourceIds)) {
      return true;
    }

    executionLogCallback.saveExecutionLog("Performing steady check for custom workloads \n");
    int maxResourceNameLength = 0;
    for (KubernetesResourceId kubernetesResourceId : resourceIds) {
      maxResourceNameLength = Math.max(maxResourceNameLength, kubernetesResourceId.getName().length());
    }

    final String eventInfoFormat = "%-7s: %-" + maxResourceNameLength + "s   %s";

    Set<String> namespaces = resourceIds.stream().map(KubernetesResourceId::getNamespace).collect(toSet());
    List<GetCommand> getEventCommands = namespaces.stream()
                                            .map(ns
                                                -> client.get()
                                                       .resources("events")
                                                       .namespace(ns)
                                                       .output(K8sConstants.eventWithNamespaceOutputFormat)
                                                       .watchOnly(true))
                                            .collect(toList());

    for (GetCommand cmd : getEventCommands) {
      executionLogCallback.saveExecutionLog(GetCommand.getPrintableCommand(cmd.command()) + "\n");
    }

    boolean success = false;

    List<StartedProcess> eventWatchProcesses = new ArrayList<>();
    String currentSteadyCondition = null;
    try (LogOutputStream watchInfoStream =
             createFilteredInfoLogOutputStream(resourceIds, executionLogCallback, eventInfoFormat);
         LogOutputStream watchErrorStream = createErrorLogOutputStream(executionLogCallback)) {
      for (GetCommand getEventsCommand : getEventCommands) {
        eventWatchProcesses.add(getEventWatchProcess(
            k8sDelegateTaskParams.getWorkingDirectory(), getEventsCommand, watchInfoStream, watchErrorStream));
      }

      for (KubernetesResource kubernetesResource : resources) {
        String steadyCondition = kubernetesResource.getMetadataAnnotationValue(HarnessAnnotations.steadyStateCondition);
        currentSteadyCondition = steadyCondition;
        success = HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMillis(timeoutInMillis),
            ()
                -> doStatusCheckForCustomResources(client, kubernetesResource.getResourceId(), steadyCondition,
                    k8sDelegateTaskParams, executionLogCallback, isErrorFrameworkEnabled));

        if (!success) {
          break;
        }
      }

      return success;
    } catch (Exception e) {
      log.error("Exception while doing statusCheck", e);
      executionLogCallback.saveExecutionLog("\nFailed to execute the status check of the custom resources.", INFO);
      executionLogCallback.saveExecutionLog(color(
          format(
              "%nPossible reasons: %n\t 1. The steady check condition [%s] is wrong. %n\t 2. The custom controller is not running.",
              currentSteadyCondition),
          Yellow, Bold));

      if (isErrorFrameworkEnabled) {
        if (e instanceof WingsException) {
          throw e;
        }

        throw new HintException(
            format(KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_CRD_FAILED_CHECK_CONDITION, currentSteadyCondition),
            NestedExceptionUtils.hintWithExplanationException(
                KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_CRD_FAILED_CHECK_CONTROLLER,
                format(KubernetesExceptionExplanation.WAIT_FOR_STEADY_STATE_CRD_FAILED, currentSteadyCondition),
                new KubernetesTaskException(KubernetesExceptionMessages.WAIT_FOR_STEADY_STATE_FAILED)));
      }

      executionLogCallback.saveExecutionLog("\nFailed.", INFO, CommandExecutionStatus.FAILURE);
      return false;
    } finally {
      for (StartedProcess eventWatchProcess : eventWatchProcesses) {
        eventWatchProcess.getProcess().destroyForcibly().waitFor();
      }
      if (success) {
        if (denoteOverallSuccess) {
          executionLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);
        }
      } else {
        executionLogCallback.saveExecutionLog(
            format("%nStatus check for resources in namespace [%s] failed.", namespaces), ERROR,
            CommandExecutionStatus.FAILURE);
      }
    }
  }

  public void checkSteadyStateCondition(List<KubernetesResource> customWorkloads) {
    for (KubernetesResource customWorkload : customWorkloads) {
      String steadyCondition = customWorkload.getMetadataAnnotationValue(HarnessAnnotations.steadyStateCondition);
      if (isEmpty(steadyCondition)) {
        throw new InvalidArgumentsException(
            Pair.of(HarnessAnnotations.steadyStateCondition, "Metadata annotation not provided."));
      }
    }
  }

  @VisibleForTesting
  LogOutputStream createFilteredInfoLogOutputStream(
      List<KubernetesResourceId> resourceIds, LogCallback executionLogCallback, String eventInfoFormat) {
    return new LogOutputStream() {
      @Override
      protected void processLine(String line) {
        Optional<KubernetesResourceId> filteredResourceId =
            resourceIds.parallelStream()
                .filter(kubernetesResourceId
                    -> line.contains(kubernetesResourceId.getNamespace())
                        && line.contains(kubernetesResourceId.getName()))
                .findFirst();

        filteredResourceId.ifPresent(kubernetesResourceId
            -> executionLogCallback.saveExecutionLog(
                format(eventInfoFormat, "Event", kubernetesResourceId.getName(), line), INFO));
      }
    };
  }

  @VisibleForTesting
  LogOutputStream createErrorLogOutputStream(LogCallback executionLogCallback) {
    return new LogOutputStream() {
      @Override
      protected void processLine(String line) {
        executionLogCallback.saveExecutionLog(format("%-7s: %s", "Event", line), ERROR);
      }
    };
  }

  boolean doStatusCheckForCustomResources(Kubectl client, KubernetesResourceId resourceId, String steadyCondition,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback) throws Exception {
    return doStatusCheckForCustomResources(
        client, resourceId, steadyCondition, k8sDelegateTaskParams, executionLogCallback, false);
  }

  boolean doStatusCheckForCustomResources(Kubectl client, KubernetesResourceId resourceId, String steadyCondition,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback, boolean isErrorFrameworkEnabled)
      throws Exception {
    GetCommand crdStatusCommand =
        client.get().resources(resourceId.kindNameRef()).namespace(resourceId.getNamespace()).output("json");

    executionLogCallback.saveExecutionLog(getPrintableCommand(crdStatusCommand.command()) + "\n");
    final Map<String, Object> evaluatorResponseContext = new HashMap<>(1);

    Predicate<Object> retryCondition = retryConditionForProcessResult();
    Retry retry = buildRetryAndRegisterListeners(
        retryCondition, K8sTaskHelperBase.class.getSimpleName() + ".doStatusCheckForCustomResources");

    while (true) {
      Callable<ProcessResult> callable = Retry.decorateCallable(retry,
          ()
              -> crdStatusCommand.execute(
                  k8sDelegateTaskParams.getWorkingDirectory(), null, null, false, Collections.emptyMap()));
      ProcessResult result = callable.call();
      boolean success = 0 == result.getExitValue();
      if (!success) {
        log.warn(result.outputUTF8());
        if (isErrorFrameworkEnabled) {
          String explanation = isNotEmpty(result.outputUTF8())
              ? format(KubernetesExceptionExplanation.WAIT_FOR_STEADY_STATE_FAILED_OUTPUT,
                  getPrintableCommand(crdStatusCommand.command()), result.getExitValue(), result.outputUTF8())
              : format(KubernetesExceptionExplanation.WAIT_FOR_STEADY_STATE_FAILED,
                  getPrintableCommand(crdStatusCommand.command()), result.getExitValue());

          throw NestedExceptionUtils.hintWithExplanationException(
              KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_CLI_FAILED, explanation,
              new KubernetesTaskException(KubernetesExceptionMessages.WAIT_FOR_STEADY_STATE_FAILED));
        }
        return false;
      }

      evaluatorResponseContext.put("response", result.outputUTF8());
      String steadyResult = delegateExpressionEvaluator.substitute(steadyCondition, evaluatorResponseContext);
      if (isNotEmpty(steadyResult)) {
        boolean steady = Boolean.parseBoolean(steadyResult);
        if (steady) {
          return true;
        }
      }
      sleep(ofSeconds(1));
    }
  }

  private Predicate<Object> retryConditionForProcessResult() {
    return o -> {
      ProcessResult p = (ProcessResult) o;
      return p.getExitValue() != 0;
    };
  }

  private static Predicate<Object> retryConditionForTimeout() {
    return o -> {
      ProcessResponse p = (ProcessResponse) o;
      return p.getProcessResult().getExitValue() != 0 && p.getErrorMessage() != null
          && p.getErrorMessage().contains("Unable to connect to the server");
    };
  }

  private static Retry buildRetryAndRegisterListeners(Predicate<Object> retryCondition, String listenerName) {
    Retry exponentialRetry = RetryHelper.getExponentialRetry(listenerName, retryCondition);
    RetryHelper.registerEventListeners(exponentialRetry);
    return exponentialRetry;
  }

  @VisibleForTesting
  LogOutputStream createStatusInfoLogOutputStream(LogCallback executionLogCallback, String message, String format) {
    return new LogOutputStream() {
      @Override
      protected void processLine(String line) {
        executionLogCallback.saveExecutionLog(format(format, "Status", message, line), INFO);
      }
    };
  }

  @VisibleForTesting
  LogOutputStream createStatusErrorLogOutputStream(LogCallback executionLogCallback, String message, String format) {
    return new LogOutputStream() {
      @Override
      protected void processLine(String line) {
        executionLogCallback.saveExecutionLog(format(format, "Status", message, line), ERROR);
      }
    };
  }

  public String getReleaseHistoryData(KubernetesConfig kubernetesConfig, String releaseName) throws IOException {
    return getReleaseHistoryDataK8sClient(kubernetesConfig, releaseName);
  }

  private String getReleaseHistoryDataK8sClient(KubernetesConfig kubernetesConfig, String releaseName)
      throws IOException {
    String releaseHistoryData = null;
    try {
      releaseHistoryData = kubernetesContainerService.fetchReleaseHistoryFromSecrets(kubernetesConfig, releaseName);
    } catch (WingsException e) {
      log.warn(e.getMessage());
    }

    if (isEmpty(releaseHistoryData)) {
      releaseHistoryData = kubernetesContainerService.fetchReleaseHistoryFromConfigMap(kubernetesConfig, releaseName);
    }

    return releaseHistoryData;
  }

  public String getReleaseHistoryDataFromConfigMap(KubernetesConfig kubernetesConfig, String releaseName)
      throws IOException {
    return kubernetesContainerService.fetchReleaseHistoryFromConfigMap(kubernetesConfig, releaseName);
  }

  public void saveReleaseHistoryInConfigMap(
      KubernetesConfig kubernetesConfig, String releaseName, String releaseHistoryAsYaml) throws IOException {
    kubernetesContainerService.saveReleaseHistory(kubernetesConfig, releaseName, releaseHistoryAsYaml, false);
  }

  public void saveReleaseHistory(KubernetesConfig kubernetesConfig, String releaseName, String releaseHistory,
      boolean storeInSecrets) throws IOException {
    kubernetesContainerService.saveReleaseHistory(kubernetesConfig, releaseName, releaseHistory, storeInSecrets);
  }

  public String getReleaseHistoryFromSecret(KubernetesConfig kubernetesConfig, String releaseName) throws IOException {
    return kubernetesContainerService.fetchReleaseHistoryFromSecrets(kubernetesConfig, releaseName);
  }

  public LogCallback getLogCallback(ILogStreamingTaskClient logStreamingTaskClient, String commandUnitName,
      boolean shouldOpenStream, CommandUnitsProgress commandUnitsProgress) {
    return new NGDelegateLogCallback(logStreamingTaskClient, commandUnitName, shouldOpenStream, commandUnitsProgress);
  }

  public TaskProgressCallback getTaskProgressCallback(
      ILogStreamingTaskClient taskProgressStreamingTaskClient, String taskId) {
    return new NGDelegateTaskProgressCallback(taskProgressStreamingTaskClient, taskId);
  }

  public List<FileData> renderTemplate(K8sDelegateTaskParams k8sDelegateTaskParams,
      ManifestDelegateConfig manifestDelegateConfig, String manifestFilesDirectory, List<String> manifestOverrideFiles,
      String releaseName, String namespace, LogCallback executionLogCallback, Integer timeoutInMin) throws Exception {
    ManifestType manifestType = manifestDelegateConfig.getManifestType();
    long timeoutInMillis = K8sTaskHelperBase.getTimeoutMillisFromMinutes(timeoutInMin);
    String kubeConfigFile = isNotEmpty(k8sDelegateTaskParams.getKubeconfigPath())
        ? Paths.get(k8sDelegateTaskParams.getWorkingDirectory(), k8sDelegateTaskParams.getKubeconfigPath()).toString()
        : EMPTY;
    switch (manifestType) {
      case K8S_MANIFEST:
        List<FileData> manifestFiles = readManifestFilesFromDirectory(manifestFilesDirectory);
        return renderManifestFilesForGoTemplate(
            k8sDelegateTaskParams, manifestFiles, manifestOverrideFiles, executionLogCallback, timeoutInMillis);

      case HELM_CHART:
        HelmChartManifestDelegateConfig helmChartManifest = (HelmChartManifestDelegateConfig) manifestDelegateConfig;
        int index = helmTaskHelperBase.skipDefaultHelmValuesYaml(manifestFilesDirectory, manifestOverrideFiles,
            helmChartManifest.isSkipApplyHelmDefaultValues(), helmChartManifest.getHelmVersion());
        if (index != -1) {
          manifestOverrideFiles.remove(index);
        }
        return renderTemplateForHelm(k8sDelegateTaskParams.getHelmPath(),
            getManifestDirectoryForHelmChartWithSubCharts(manifestFilesDirectory, helmChartManifest),
            manifestOverrideFiles, releaseName, namespace, executionLogCallback, helmChartManifest.getHelmVersion(),
            timeoutInMillis, helmChartManifest.getHelmCommandFlag(), kubeConfigFile);

      case KUSTOMIZE:
        KustomizeManifestDelegateConfig kustomizeManifest = (KustomizeManifestDelegateConfig) manifestDelegateConfig;

        String kustomizeYamlFolderPath = kustomizeManifest.getKustomizeYamlFolderPath() != null
            ? kustomizeManifest.getKustomizeYamlFolderPath()
            : kustomizeManifest.getKustomizeDirPath();

        String kustomizePath = Paths.get(manifestFilesDirectory, kustomizeYamlFolderPath).toString();
        savingPatchesToDirectory(kustomizePath, manifestOverrideFiles, executionLogCallback);
        return kustomizeTaskHelper.build(manifestFilesDirectory, k8sDelegateTaskParams.getKustomizeBinaryPath(),
            kustomizeManifest.getPluginPath(), kustomizeYamlFolderPath, executionLogCallback,
            kustomizeManifest.getCommandFlags());

      case OPENSHIFT_TEMPLATE:
        OpenshiftManifestDelegateConfig openshiftManifestConfig =
            (OpenshiftManifestDelegateConfig) manifestDelegateConfig;
        String openshiftTemplatePath =
            getOpenshiftTemplatePath(openshiftManifestConfig.getStoreDelegateConfig(), manifestType);
        return openShiftDelegateService.processTemplatization(manifestFilesDirectory, k8sDelegateTaskParams.getOcPath(),
            openshiftTemplatePath, executionLogCallback, manifestOverrideFiles);

      default:
        throw new UnsupportedOperationException(
            String.format("Manifest delegate config type: [%s]", manifestType.name()));
    }
  }

  public List<FileData> renderTemplateForGivenFiles(K8sDelegateTaskParams k8sDelegateTaskParams,
      ManifestDelegateConfig manifestDelegateConfig, String manifestFilesDirectory, @NotEmpty List<String> filesList,
      List<String> manifestOverrideFiles, String releaseName, String namespace, LogCallback executionLogCallback,
      Integer timeoutInMin, boolean skipRendering) throws Exception {
    ManifestType manifestType = manifestDelegateConfig.getManifestType();
    long timeoutInMillis = K8sTaskHelperBase.getTimeoutMillisFromMinutes(timeoutInMin);
    String kubeConfigFile = isNotEmpty(k8sDelegateTaskParams.getKubeconfigPath())
        ? Paths.get(k8sDelegateTaskParams.getWorkingDirectory(), k8sDelegateTaskParams.getKubeconfigPath()).toString()
        : EMPTY;
    switch (manifestType) {
      case K8S_MANIFEST:
        k8sTaskManifestValidator.checkFilesPartOfManifest(
            manifestFilesDirectory, filesList, K8sTaskManifestValidator.IS_YAML_FILE);
        List<FileData> manifestFiles = readFilesFromDirectory(manifestFilesDirectory, filesList, executionLogCallback);
        if (skipRendering) {
          return manifestFiles;
        }
        return renderManifestFilesForGoTemplate(
            k8sDelegateTaskParams, manifestFiles, manifestOverrideFiles, executionLogCallback, timeoutInMillis);

      case HELM_CHART:
        HelmChartManifestDelegateConfig helmChartManifest = (HelmChartManifestDelegateConfig) manifestDelegateConfig;
        int index = helmTaskHelperBase.skipDefaultHelmValuesYaml(manifestFilesDirectory, manifestOverrideFiles,
            helmChartManifest.isSkipApplyHelmDefaultValues(), helmChartManifest.getHelmVersion());
        if (index != -1) {
          manifestOverrideFiles.remove(index);
        }
        return renderTemplateForHelmChartFiles(k8sDelegateTaskParams.getHelmPath(),
            getManifestDirectoryForHelmChartWithSubCharts(manifestFilesDirectory, helmChartManifest), filesList,
            manifestOverrideFiles, releaseName, namespace, executionLogCallback, helmChartManifest.getHelmVersion(),
            timeoutInMillis, helmChartManifest.getHelmCommandFlag(), kubeConfigFile);

      case KUSTOMIZE:
        KustomizeManifestDelegateConfig kustomizeManifest = (KustomizeManifestDelegateConfig) manifestDelegateConfig;
        return kustomizeTaskHelper.buildForApply(k8sDelegateTaskParams.getKustomizeBinaryPath(),
            kustomizeManifest.getPluginPath(), manifestFilesDirectory, filesList, true, manifestOverrideFiles,
            executionLogCallback, kustomizeManifest.getCommandFlags());

      default:
        throw new UnsupportedOperationException(
            String.format("Manifest delegate config type: [%s]", manifestType.name()));
    }
  }

  public List<KubernetesResource> getResourcesFromManifests(K8sDelegateTaskParams k8sDelegateTaskParams,
      ManifestDelegateConfig manifestDelegateConfig, String manifestFilesDirectory, @NotEmpty List<String> filesList,
      List<String> manifestOverrideFiles, String releaseName, String namespace, LogCallback logCallback,
      Integer timeoutInMin, boolean skipRendering) throws Exception {
    List<FileData> manifestFiles =
        renderTemplateForGivenFiles(k8sDelegateTaskParams, manifestDelegateConfig, manifestFilesDirectory, filesList,
            manifestOverrideFiles, releaseName, namespace, logCallback, timeoutInMin, skipRendering);
    if (isEmpty(manifestFiles)) {
      return new ArrayList<>();
    }

    List<KubernetesResource> resources = readManifests(manifestFiles, logCallback);
    setNamespaceToKubernetesResourcesIfRequired(resources, namespace);

    return resources;
  }

  public boolean fetchManifestFilesAndWriteToDirectory(ManifestDelegateConfig manifestDelegateConfig,
      String manifestFilesDirectory, LogCallback executionLogCallback, long timeoutInMillis, String accountId)
      throws Exception {
    return fetchManifestFilesAndWriteToDirectory(
        manifestDelegateConfig, manifestFilesDirectory, executionLogCallback, timeoutInMillis, accountId, true);
  }

  public boolean fetchManifestFilesAndWriteToDirectory(ManifestDelegateConfig manifestDelegateConfig,
      String manifestFilesDirectory, LogCallback executionLogCallback, long timeoutInMillis, String accountId,
      boolean denoteOverallSuccess) throws Exception {
    StoreDelegateConfig storeDelegateConfig = manifestDelegateConfig.getStoreDelegateConfig();
    switch (storeDelegateConfig.getType()) {
      case HARNESS:
        return writeManifestFilesToDirectory(
            storeDelegateConfig, manifestFilesDirectory, executionLogCallback, denoteOverallSuccess);
      case CUSTOM_REMOTE:
        return downloadZippedManifestFilesFormCustomSource(
            storeDelegateConfig, manifestFilesDirectory, executionLogCallback, denoteOverallSuccess);
      case GIT:
        if (manifestDelegateConfig instanceof KustomizeManifestDelegateConfig) {
          KustomizeManifestDelegateConfig kustomizeManifestDelegateConfig =
              (KustomizeManifestDelegateConfig) manifestDelegateConfig;
          if (kustomizeManifestDelegateConfig.getKustomizeYamlFolderPath() != null) {
            executionLogCallback.saveExecutionLog(color(
                "\nUsing Optimized File Fetch For Kustomize, will fetch the subset of files needed for Deployment. ",
                LogColor.White, LogWeight.Bold));
          }
        }
        return downloadManifestFilesFromGit(
            storeDelegateConfig, manifestFilesDirectory, executionLogCallback, accountId, denoteOverallSuccess);
      case HTTP_HELM:
      case S3_HELM:
      case GCS_HELM:
      case OCI_HELM:
        return downloadFilesFromChartRepo(manifestDelegateConfig, manifestFilesDirectory, executionLogCallback,
            timeoutInMillis, denoteOverallSuccess);

      default:
        throw new UnsupportedOperationException(
            String.format("Manifest store config type: [%s]", storeDelegateConfig.getType().name()));
    }
  }

  private boolean downloadZippedManifestFilesFormCustomSource(StoreDelegateConfig delegateManifestConfig,
      String manifestFilesDirectory, LogCallback executionLogCallback, Boolean denoteOverallSuccess) {
    String tempWorkingDir = null;
    try {
      tempWorkingDir = customManifestService.getWorkingDirectory();

      CustomRemoteStoreDelegateConfig customRemoteStoreDelegateConfig =
          (CustomRemoteStoreDelegateConfig) delegateManifestConfig;

      CustomManifestSource customManifestSource = customRemoteStoreDelegateConfig.getCustomManifestSource();
      // handleIncorrectConfiguration(customRemoteStoreDelegateConfig);
      customManifestFetchTaskHelper.downloadAndUnzipCustomSourceManifestFiles(
          tempWorkingDir, customManifestSource.getZippedManifestFileId(), customManifestSource.getAccountId());
      File file = new File(tempWorkingDir);
      if (isEmpty(file.list())) {
        throw new InvalidRequestException("No manifest files found under working directory", USER);
      }
      // preparing legacy directory structure for manifests and values yamls
      File customManifestFolderPath = file.listFiles(pathname -> !file.isHidden())[0];
      copyManifestFilesToWorkingDir(customManifestFolderPath, new File(manifestFilesDirectory));

      executionLogCallback.saveExecutionLog(color("Successfully fetched following files:", White, Bold));
      executionLogCallback.saveExecutionLog(getManifestFileNamesInLogFormat(manifestFilesDirectory));
      if (denoteOverallSuccess) {
        executionLogCallback.saveExecutionLog("Done.", INFO, SUCCESS);
      }
      return true;
    } catch (IOException e) {
      log.error("Failed to get files from manifest directory", ExceptionMessageSanitizer.sanitizeException(e));
      executionLogCallback.saveExecutionLog(
          "Failed to get manifest files from custom source. " + ExceptionUtils.getMessage(e), ERROR,
          CommandExecutionStatus.FAILURE);
      return false;
    } catch (Exception e) {
      log.error("Failed to process custom manifest", ExceptionMessageSanitizer.sanitizeException(e));
      executionLogCallback.saveExecutionLog(
          "Failed to process custom manifest. " + ExceptionUtils.getMessage(e), ERROR, CommandExecutionStatus.FAILURE);
      return false;
    }
  }

  public static void copyManifestFilesToWorkingDir(File src, File dest) throws IOException {
    if (src.isDirectory()) {
      FileUtils.copyDirectory(src, dest);
    } else {
      Path destFilePath = Paths.get(dest.getPath(), src.getName());
      FileUtils.copyFile(src, destFilePath.toFile());
    }
    deleteDirectoryAndItsContentIfExists(src.getAbsolutePath());
    waitForDirectoryToBeAccessibleOutOfProcess(dest.getPath(), 10);
  }

  private boolean downloadManifestFilesFromGit(StoreDelegateConfig storeDelegateConfig, String manifestFilesDirectory,
      LogCallback executionLogCallback, String accountId, Boolean denoteOverallSuccess) throws Exception {
    if (!(storeDelegateConfig instanceof GitStoreDelegateConfig)) {
      throw new InvalidArgumentsException(Pair.of("storeDelegateConfig", "Must be instance of GitStoreDelegateConfig"));
    }

    GitStoreDelegateConfig gitStoreDelegateConfig = (GitStoreDelegateConfig) storeDelegateConfig;

    // ToDo What to set here now as we have a list now?
    //    if (isBlank(gitStoreDelegateConfig.getPaths().getFilePath())) {
    //      delegateManifestConfig.getGitFileConfig().setFilePath(StringUtils.EMPTY);
    //    }

    try {
      printGitConfigInExecutionLogs(gitStoreDelegateConfig, executionLogCallback);

      if (gitStoreDelegateConfig.isOptimizedFilesFetch()) {
        executionLogCallback.saveExecutionLog("Using optimized file fetch");
        secretDecryptionService.decrypt(
            GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(gitStoreDelegateConfig.getGitConfigDTO()),
            gitStoreDelegateConfig.getApiAuthEncryptedDataDetails());
        ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
            GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(gitStoreDelegateConfig.getGitConfigDTO()),
            gitStoreDelegateConfig.getApiAuthEncryptedDataDetails());

        scmFetchFilesHelper.downloadFilesUsingScm(manifestFilesDirectory, gitStoreDelegateConfig, executionLogCallback);
      } else {
        GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO(gitStoreDelegateConfig.getGitConfigDTO());
        gitDecryptionHelper.decryptGitConfig(gitConfigDTO, gitStoreDelegateConfig.getEncryptedDataDetails());
        SshSessionConfig sshSessionConfig = gitDecryptionHelper.getSSHSessionConfig(
            gitStoreDelegateConfig.getSshKeySpecDTO(), gitStoreDelegateConfig.getEncryptedDataDetails());
        ngGitService.downloadFiles(
            gitStoreDelegateConfig, manifestFilesDirectory, accountId, sshSessionConfig, gitConfigDTO);
      }

      executionLogCallback.saveExecutionLog(color("Successfully fetched following files:", White, Bold));
      executionLogCallback.saveExecutionLog(getManifestFileNamesInLogFormat(manifestFilesDirectory));
      if (denoteOverallSuccess) {
        executionLogCallback.saveExecutionLog("Done.", INFO, SUCCESS);
      }
      return true;
    } catch (YamlException e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Failure in fetching files from git", sanitizedException);
      executionLogCallback.saveExecutionLog(
          "Failed to download manifest files from git. " + ExceptionUtils.getMessage(sanitizedException), ERROR,
          CommandExecutionStatus.FAILURE);

      throw new KubernetesTaskException(
          format("Failed while trying to fetch files from git connector: '%s' in manifest with identifier: %s",
              gitStoreDelegateConfig.getConnectorId(), gitStoreDelegateConfig.getManifestId()),
          e.getCause());
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Failure in fetching files from git", sanitizedException);
      executionLogCallback.saveExecutionLog(
          "Failed to download manifest files from git. " + ExceptionUtils.getMessage(sanitizedException), ERROR,
          CommandExecutionStatus.FAILURE);

      throw new KubernetesTaskException(
          format("Failed while trying to fetch files from git connector: '%s' in manifest with identifier: %s",
              gitStoreDelegateConfig.getConnectorId(), gitStoreDelegateConfig.getManifestId()),
          e);
    }
  }

  private void printGitConfigInExecutionLogs(
      GitStoreDelegateConfig gitStoreDelegateConfig, LogCallback executionLogCallback) {
    GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO(gitStoreDelegateConfig.getGitConfigDTO());
    if (isNotEmpty(gitStoreDelegateConfig.getManifestType()) && isNotEmpty(gitStoreDelegateConfig.getManifestId())) {
      executionLogCallback.saveExecutionLog("\n"
          + color(format("Fetching %s files with identifier: %s", gitStoreDelegateConfig.getManifestType(),
                      gitStoreDelegateConfig.getManifestId()),
              White, Bold));
    } else {
      executionLogCallback.saveExecutionLog("\n" + color("Fetching manifest files", White, Bold));
    }
    executionLogCallback.saveExecutionLog("Git connector Url: " + gitConfigDTO.getUrl());

    if (FetchType.BRANCH == gitStoreDelegateConfig.getFetchType()) {
      executionLogCallback.saveExecutionLog("Branch: " + gitStoreDelegateConfig.getBranch());
    } else {
      executionLogCallback.saveExecutionLog("CommitId: " + gitStoreDelegateConfig.getCommitId());
    }

    StringBuilder sb = new StringBuilder(1024);
    sb.append("\nFetching manifest files at path: \n");
    gitStoreDelegateConfig.getPaths().forEach(
        filePath -> sb.append(color(format("- %s", filePath), Gray)).append(System.lineSeparator()));
    executionLogCallback.saveExecutionLog(sb.toString());
  }

  public void copyHelmChartFolderToWorkingDir(String localChartDirectory, String workingDirectory) throws IOException {
    File src = new File(localChartDirectory);
    File dest = new File(workingDirectory);
    deleteDirectoryAndItsContentIfExists(dest.getAbsolutePath());
    FileUtils.copyDirectory(src, dest);
    waitForDirectoryToBeAccessibleOutOfProcess(dest.getPath(), 10);
  }

  public boolean downloadFilesFromChartRepo(ManifestDelegateConfig manifestDelegateConfig, String destinationDirectory,
      LogCallback logCallback, long timeoutInMillis, Boolean denoteOverallSuccess) {
    if (!(manifestDelegateConfig instanceof HelmChartManifestDelegateConfig)) {
      throw new InvalidArgumentsException(
          Pair.of("manifestDelegateConfig", "Must be instance of HelmChartManifestDelegateConfig"));
    }

    try {
      boolean isEnvVarSet = helmTaskHelperBase.isHelmLocalRepoSet();
      String chartName = ((HelmChartManifestDelegateConfig) manifestDelegateConfig).getChartName();
      String chartVersion = ((HelmChartManifestDelegateConfig) manifestDelegateConfig).getChartVersion();
      String repoName = helmTaskHelperBase.getRepoNameNG(manifestDelegateConfig.getStoreDelegateConfig());
      if (isEnvVarSet) {
        String parentDir = helmTaskHelperBase.getHelmLocalRepositoryCompletePath(repoName, chartName, chartVersion);
        helmTaskHelperBase.createAndWaitForDir(parentDir);
        helmTaskHelperBase.populateChartToLocalHelmRepo(
            (HelmChartManifestDelegateConfig) manifestDelegateConfig, timeoutInMillis, logCallback, parentDir);

        String localChartDirectory = HelmTaskHelperBase.getChartDirectory(parentDir, chartName);

        String workingDirectory =
            helmTaskHelperBase.createDirectoryIfNotExist(Paths.get(destinationDirectory, chartName).toString());
        log.info("Copying locally present chart from directory: {} to current working directory: {} \n",
            localChartDirectory, workingDirectory);
        copyHelmChartFolderToWorkingDir(localChartDirectory, workingDirectory);
        logCallback.saveExecutionLog(color("Successfully fetched following files:", White, Bold));
        logCallback.saveExecutionLog(getManifestFileNamesInLogFormat(destinationDirectory));
        if (denoteOverallSuccess) {
          logCallback.saveExecutionLog("Done.", INFO, SUCCESS);
        }
        return true;
      }
      HelmChartManifestDelegateConfig helmChartManifestConfig =
          (HelmChartManifestDelegateConfig) manifestDelegateConfig;
      logCallback.saveExecutionLog(color(format("%nFetching files from helm chart repo"), White, Bold));

      helmTaskHelperBase.initHelm(destinationDirectory, helmChartManifestConfig.getHelmVersion(), timeoutInMillis);
      if (HTTP_HELM == manifestDelegateConfig.getStoreDelegateConfig().getType()) {
        helmTaskHelperBase.downloadChartFilesFromHttpRepo(
            helmChartManifestConfig, destinationDirectory, timeoutInMillis);
      } else if (OCI_HELM == manifestDelegateConfig.getStoreDelegateConfig().getType()) {
        helmTaskHelperBase.downloadChartFilesFromOciRepo(
            helmChartManifestConfig, destinationDirectory, timeoutInMillis);
      } else {
        helmTaskHelperBase.downloadChartFilesUsingChartMuseum(
            helmChartManifestConfig, destinationDirectory, timeoutInMillis);
      }

      helmTaskHelperBase.printHelmChartInfoWithVersionInExecutionLogs(
          destinationDirectory, helmChartManifestConfig, logCallback);
      logCallback.saveExecutionLog(color("Successfully fetched following files:", White, Bold));
      logCallback.saveExecutionLog(getManifestFileNamesInLogFormat(destinationDirectory));
      if (denoteOverallSuccess) {
        logCallback.saveExecutionLog("Done.", INFO, SUCCESS);
      }
    } catch (HelmClientException e) {
      String errorMsg = format("Failed to download manifest files from %s repo. ",
          manifestDelegateConfig.getStoreDelegateConfig().getType());
      logCallback.saveExecutionLog(errorMsg + ExceptionUtils.getMessage(ExceptionMessageSanitizer.sanitizeException(e)),
          ERROR, CommandExecutionStatus.FAILURE);

      throw new HelmClientRuntimeException(e);
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      String errorMsg = format("Failed to download manifest files from %s repo. ",
          manifestDelegateConfig.getStoreDelegateConfig().getType());
      logCallback.saveExecutionLog(
          errorMsg + ExceptionUtils.getMessage(sanitizedException), ERROR, CommandExecutionStatus.FAILURE);
      throw new HelmClientException(errorMsg, sanitizedException, HelmCliCommandType.FETCH);
    }

    return true;
  }

  public ConnectorValidationResult validate(
      ConnectorConfigDTO connector, List<EncryptedDataDetail> encryptionDetailList) {
    KubernetesConfig kubernetesConfig = getKubernetesConfig(connector, encryptionDetailList);
    kubernetesContainerService.validateCredentials(kubernetesConfig);
    return ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build();
  }

  private KubernetesConfig getKubernetesConfig(
      ConnectorConfigDTO connector, List<EncryptedDataDetail> encryptionDetailList) {
    KubernetesClusterConfigDTO kubernetesClusterConfig = (KubernetesClusterConfigDTO) connector;
    if (kubernetesClusterConfig.getCredential().getKubernetesCredentialType()
        == KubernetesCredentialType.MANUAL_CREDENTIALS) {
      KubernetesAuthCredentialDTO kubernetesCredentialAuth = getKubernetesCredentialsAuth(
          (KubernetesClusterDetailsDTO) kubernetesClusterConfig.getCredential().getConfig());
      secretDecryptionService.decrypt(kubernetesCredentialAuth, encryptionDetailList);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(kubernetesCredentialAuth, encryptionDetailList);
    }
    return k8sYamlToDelegateDTOMapper.createKubernetesConfigFromClusterConfig(kubernetesClusterConfig);
  }

  public ConnectorValidationResult validateCEKubernetesCluster(ConnectorConfigDTO connector, String accountIdentifier,
      List<EncryptedDataDetail> encryptionDetailList, List<CEFeatures> featuresEnabled) {
    ConnectivityStatus connectivityStatus = ConnectivityStatus.SUCCESS;
    KubernetesConfig kubernetesConfig = getKubernetesConfig(connector, encryptionDetailList);
    List<ErrorDetail> errorDetails = new ArrayList<>();
    String errorSummary = "";
    try {
      CEK8sDelegatePrerequisite.MetricsServerCheck metricsServerCheck =
          kubernetesContainerService.validateMetricsServer(kubernetesConfig);
      if (!metricsServerCheck.getIsInstalled()) {
        errorDetails.add(ErrorDetail.builder()
                             .message("Please install metrics server on your cluster")
                             .reason("couldn't access metrics server")
                             .build());
        errorSummary += metricsServerCheck.getMessage() + ", ";
      }

      List<CEK8sDelegatePrerequisite.Rule> ruleList =
          kubernetesContainerService.validateCEResourcePermissions(kubernetesConfig);

      if (!ruleList.isEmpty()) {
        errorDetails.addAll(ruleList.stream()
                                .map(e
                                    -> ErrorDetail.builder()
                                           .reason(String.format("'%s' not granted on '%s' in apiGroup:'%s'",
                                               e.getVerbs(), e.getResources(), e.getApiGroups()))
                                           .message(e.getMessage())
                                           .code(0)
                                           .build())
                                .collect(toList()));
        errorSummary += "few of the visibility permissions are missing, ";
      }

      if (!errorDetails.isEmpty()) {
        return ConnectorValidationResult.builder()
            .errorSummary(errorSummary)
            .errors(errorDetails)
            .status(ConnectivityStatus.FAILURE)
            .build();
      }
    } catch (Exception ex) {
      log.info("Exception while validating kubernetes credentials", ExceptionMessageSanitizer.sanitizeException(ex));
      return createConnectivityFailureValidationResult(ExceptionMessageSanitizer.sanitizeException(ex));
    }
    return ConnectorValidationResult.builder().status(connectivityStatus).build();
  }

  public V1TokenReviewStatus fetchTokenReviewStatus(
      KubernetesClusterConfigDTO kubernetesClusterConfigDTO, List<EncryptedDataDetail> encryptionDetailList) {
    KubernetesConfig kubernetesConfig = getKubernetesConfig(kubernetesClusterConfigDTO, encryptionDetailList);
    return kubernetesContainerService.fetchTokenReviewStatus(kubernetesConfig);
  }

  private ConnectorValidationResult createConnectivityFailureValidationResult(Exception ex) {
    String errorMessage = ex.getMessage();

    if (ex instanceof ApiException) {
      errorMessage = ((ApiException) ex).getResponseBody();
    }

    ErrorDetail errorDetail = ngErrorHelper.createErrorDetail(errorMessage);
    String errorSummary = ngErrorHelper.getErrorSummary(errorMessage);

    return ConnectorValidationResult.builder()
        .status(ConnectivityStatus.FAILURE)
        .errors(Collections.singletonList(errorDetail))
        .errorSummary(errorSummary)
        .build();
  }

  private KubernetesAuthCredentialDTO getKubernetesCredentialsAuth(
      KubernetesClusterDetailsDTO kubernetesClusterConfigDTO) {
    return kubernetesClusterConfigDTO.getAuth().getCredentials();
  }

  @VisibleForTesting
  public List<K8sPod> tagNewPods(List<K8sPod> newPods, List<K8sPod> existingPods) {
    Set<String> existingPodNames = existingPods.stream().map(K8sPod::getName).collect(Collectors.toSet());
    List<K8sPod> allPods = new ArrayList<>(newPods);
    allPods.forEach(pod -> {
      if (!existingPodNames.contains(pod.getName())) {
        pod.setNewPod(true);
      }
    });
    return allPods;
  }

  public List<FileData> renderTemplateForHelm(String helmPath, String manifestFilesDirectory, List<String> valuesFiles,
      String releaseName, String namespace, LogCallback executionLogCallback, HelmVersion helmVersion,
      long timeoutInMillis, HelmCommandFlag helmCommandFlag, String kubeConfigPath) throws Exception {
    String valuesFileOptions = createValuesFileOptions(manifestFilesDirectory, valuesFiles, executionLogCallback);
    log.info("Values file options: " + valuesFileOptions);

    printHelmPath(executionLogCallback, helmPath);

    List<FileData> result = new ArrayList<>();
    try (ByteArrayOutputStream errorCaptureStream = new ByteArrayOutputStream(1024);
         LogOutputStream logErrorStream =
             K8sTaskHelperBase.getExecutionLogOutputStream(executionLogCallback, ERROR, errorCaptureStream)) {
      String helmTemplateCommand = getHelmCommandForRender(helmPath, manifestFilesDirectory, releaseName, namespace,
          valuesFileOptions, helmVersion, helmCommandFlag, kubeConfigPath);
      printHelmTemplateCommand(executionLogCallback, helmTemplateCommand);

      ProcessResult processResult =
          executeShellCommand(manifestFilesDirectory, helmTemplateCommand, logErrorStream, timeoutInMillis);
      if (processResult.getExitValue() != 0) {
        throw NestedExceptionUtils.hintWithExplanationException(KubernetesExceptionHints.MANIFEST_RENDER_ERROR_HELM,
            format(KubernetesExceptionExplanation.MANIFEST_RENDER_ERROR_HELM, errorCaptureStream.toString(),
                helmTemplateCommand),
            new HelmClientException(getErrorMessageIfProcessFailed("Failed to render template. ", processResult), USER,
                HelmCliCommandType.RENDER_CHART));
      }
      int index = (helmCommandFlag == null)
          ? -1
          : helmTaskHelperBase.checkForDependencyUpdateFlag(helmCommandFlag.getValueMap(), processResult.outputUTF8());
      result.add(
          FileData.builder()
              .fileName("manifest.yaml")
              .fileContent(index == -1 ? processResult.outputUTF8() : processResult.outputUTF8().substring(index))
              .build());
    }

    return result;
  }

  private String createValuesFileOptions(String workingDirectory, List<String> valuesFiles, LogCallback logCallback)
      throws Exception {
    try {
      return writeValuesToFile(workingDirectory, valuesFiles);
    } catch (KubernetesValuesException exception) {
      String message = exception.getParams().get("reason").toString();
      logCallback.saveExecutionLog(message, ERROR);
      if (isNotEmpty(message) && message.contains(KubernetesExceptionExplanation.EXPECTED_BLOCK_END)) {
        throw NestedExceptionUtils.hintWithExplanationException(KubernetesExceptionHints.INVALID_VALUES_YAML,
            KubernetesExceptionExplanation.INVALID_VALUES_YAML,
            NestedExceptionUtils.hintWithExplanationException(KubernetesExceptionHints.BASE_64_ENCODED_CHECK,
                KubernetesExceptionExplanation.EXPECTED_BLOCK_END,
                new KubernetesValuesException(message, exception.getCause())));
      }
      throw NestedExceptionUtils.hintWithExplanationException(KubernetesExceptionHints.INVALID_VALUES_YAML,
          KubernetesExceptionExplanation.INVALID_VALUES_YAML,
          new KubernetesValuesException(message, exception.getCause()));
    } catch (NoSuchFileException e) {
      String prefixMessage = "There may be an issue with file/folder path, due to which file is not found. ";
      String suffixMessage = "Please check if file exists in the specified path.";
      if (isNotEmpty(e.getFile()) && e.getFile().contains("charts/")) {
        suffixMessage = suffixMessage + " Also check if sub-chart name is correct/valid.";
      }
      throw NestedExceptionUtils.hintWithExplanationException(prefixMessage + suffixMessage, "No such file found", e);
    }
  }

  @VisibleForTesting
  String getErrorMessageIfProcessFailed(String baseMessage, ProcessResult processResult) {
    StringBuilder stringBuilder = new StringBuilder(baseMessage);
    if (EmptyPredicate.isNotEmpty(processResult.getOutput().getUTF8())) {
      stringBuilder.append(String.format(" Error %s", processResult.getOutput().getUTF8()));
    }
    return stringBuilder.toString();
  }

  public List<FileData> renderTemplateForHelmChartFiles(String helmPath, String manifestFilesDirectory,
      List<String> chartFiles, List<String> valuesFiles, String releaseName, String namespace,
      LogCallback executionLogCallback, HelmVersion helmVersion, long timeoutInMillis, HelmCommandFlag helmCommandFlag,
      String kubeConfigPath) throws Exception {
    String valuesFileOptions = createValuesFileOptions(manifestFilesDirectory, valuesFiles, executionLogCallback);
    log.info("Values file options: " + valuesFileOptions);

    printHelmPath(executionLogCallback, helmPath);

    List<FileData> result = new ArrayList<>();

    for (String chartFile : chartFiles) {
      if (K8sTaskHelperBase.isValidManifestFile(chartFile)) {
        chartFile = StringUtils.stripStart(chartFile, "/");
        try (ByteArrayOutputStream errorCaptureStream = new ByteArrayOutputStream(1024);
             LogOutputStream logErrorStream =
                 K8sTaskHelperBase.getExecutionLogOutputStream(executionLogCallback, ERROR, errorCaptureStream)) {
          String helmTemplateCommand = getHelmCommandForRender(helmPath, manifestFilesDirectory, releaseName, namespace,
              valuesFileOptions, chartFile, helmVersion, helmCommandFlag, kubeConfigPath);

          printHelmTemplateCommand(executionLogCallback, helmTemplateCommand);

          ProcessResult processResult =
              executeShellCommand(manifestFilesDirectory, helmTemplateCommand, logErrorStream, timeoutInMillis);
          if (processResult.getExitValue() != 0) {
            k8sTaskManifestValidator.checkFilePartOfManifest(
                manifestFilesDirectory, chartFile, K8sTaskManifestValidator.IS_HELM_TEMPLATE_FILE);
            throw NestedExceptionUtils.hintWithExplanationException(KubernetesExceptionHints.MANIFEST_RENDER_ERROR_HELM,
                format(KubernetesExceptionExplanation.MANIFEST_RENDER_ERROR_HELM, errorCaptureStream.toString(),
                    helmTemplateCommand),
                new HelmClientException(getErrorMessageIfProcessFailed(
                                            format("Failed to render chart file [%s]", chartFile), processResult),
                    USER, HelmCliCommandType.RENDER_CHART));
          }

          result.add(FileData.builder().fileName(chartFile).fileContent(processResult.outputUTF8()).build());
        }
      } else {
        executionLogCallback.saveExecutionLog(
            color(format("Ignoring file [%s] with unsupported extension", chartFile), Yellow, Bold));
      }
    }

    return result;
  }

  private void printHelmPath(LogCallback executionLogCallback, final String helmPath) {
    executionLogCallback.saveExecutionLog(color("Rendering chart files using Helm", White, Bold));
    executionLogCallback.saveExecutionLog(color(format("Using helm binary %s", helmPath), White, Normal));
  }

  private void printHelmTemplateCommand(LogCallback executionLogCallback, final String helmTemplateCommand) {
    executionLogCallback.saveExecutionLog(color("Running Helm command", White, Bold));
    executionLogCallback.saveExecutionLog(color(helmTemplateCommand, White, Normal));
  }

  @VisibleForTesting
  String getHelmCommandForRender(String helmPath, String manifestFilesDirectory, String releaseName, String namespace,
      String valuesFileOptions, String chartFile, HelmVersion helmVersion, HelmCommandFlag helmCommandFlag,
      String kubeConfigPath) {
    HelmCliCommandType commandType = HelmCliCommandType.RENDER_SPECIFIC_CHART_FILE;
    String helmTemplateCommand = HelmCommandTemplateFactory.getHelmCommandTemplate(commandType, helmVersion);
    String command = replacePlaceHoldersInHelmTemplateCommand(
        helmTemplateCommand, helmPath, manifestFilesDirectory, releaseName, namespace, chartFile, valuesFileOptions);
    Map<HelmSubCommandType, String> commandFlagValueMap =
        helmCommandFlag != null ? helmCommandFlag.getValueMap() : null;
    command =
        HelmCommandFlagsUtils.applyHelmCommandFlags(command, commandType.name(), commandFlagValueMap, helmVersion);
    return applyKubeConfigToCommand(command, kubeConfigPath);
  }

  @VisibleForTesting
  String getHelmCommandForRender(String helmPath, String manifestFilesDirectory, String releaseName, String namespace,
      String valuesFileOptions, HelmVersion helmVersion, HelmCommandFlag commandFlag, String kubeConfigPath) {
    HelmCliCommandType commandType = HelmCliCommandType.RENDER_CHART;
    String helmTemplateCommand = HelmCommandTemplateFactory.getHelmCommandTemplate(commandType, helmVersion);
    String command = replacePlaceHoldersInHelmTemplateCommand(
        helmTemplateCommand, helmPath, manifestFilesDirectory, releaseName, namespace, EMPTY, valuesFileOptions);
    Map<HelmSubCommandType, String> commandFlagValueMap = commandFlag != null ? commandFlag.getValueMap() : null;
    command =
        HelmCommandFlagsUtils.applyHelmCommandFlags(command, commandType.name(), commandFlagValueMap, helmVersion);
    return applyKubeConfigToCommand(command, kubeConfigPath);
  }

  private String replacePlaceHoldersInHelmTemplateCommand(String unrenderedCommand, String helmPath,
      String chartLocation, String releaseName, String namespace, String chartFile, String valueOverrides) {
    return unrenderedCommand.replace(HELM_PATH_PLACEHOLDER, helmPath)
        .replace("${CHART_LOCATION}", chartLocation)
        .replace("${CHART_FILE}", chartFile)
        .replace("${RELEASE_NAME}", releaseName)
        .replace("${NAMESPACE}", namespace)
        .replace("${OVERRIDE_VALUES}", valueOverrides);
  }

  private String getManifestDirectoryForHelmChart(
      String baseManifestDirectory, HelmChartManifestDelegateConfig helmChartManifest) {
    if (StoreDelegateConfigType.HARNESS.equals(helmChartManifest.getStoreDelegateConfig().getType())
        || StoreDelegateConfigType.CUSTOM_REMOTE.equals(helmChartManifest.getStoreDelegateConfig().getType())) {
      return baseManifestDirectory;
    }
    if (GIT != helmChartManifest.getStoreDelegateConfig().getType()) {
      return HelmTaskHelperBase.getChartDirectory(baseManifestDirectory, helmChartManifest.getChartName());
    }
    return baseManifestDirectory;
  }

  private String getManifestDirectoryForHelmChartWithSubCharts(
      String baseManifestDirectory, HelmChartManifestDelegateConfig helmChartManifest) {
    String manifestDir = getManifestDirectoryForHelmChart(baseManifestDirectory, helmChartManifest);
    if (isEmpty(helmChartManifest.getSubChartPath())) {
      return manifestDir;
    }
    return Paths.get(manifestDir, helmChartManifest.getSubChartPath()).toString();
  }

  @NotNull
  private List<KubernetesResourceId> getResourcesToBePruned(
      List<KubernetesResource> previousSuccessfulReleaseResources, List<KubernetesResourceId> currentResources) {
    return previousSuccessfulReleaseResources.stream()
        .filter(resource -> !resource.isSkipPruning())
        .map(KubernetesResource::getResourceId)
        .filter(resourceId -> !resourceId.isVersioned())
        .filter(resource -> !currentResources.contains(resource))
        .collect(toList());
  }

  public void logExecutableFailed(ProcessResult result, LogCallback logCallback) {
    String output = result.hasOutput() ? result.outputUTF8() : null;
    if (isNotEmpty(output)) {
      logCallback.saveExecutionLog(
          format("\nFailed with exit code: %d and output: %s.", result.getExitValue(), output), INFO, FAILURE);
    } else {
      logCallback.saveExecutionLog(format("\nFailed with exit code: %d.", result.getExitValue()), INFO, FAILURE);
    }
  }

  public List<KubernetesResourceId> getResourcesToBePrunedInOrder(
      List<KubernetesResource> resourcesFromLastSuccessfulRelease, List<KubernetesResource> resources) {
    List<KubernetesResourceId> currentResources =
        resources.stream().map(KubernetesResource::getResourceId).collect(toList());

    List<KubernetesResourceId> resourceIdsToBeDeleted =
        getResourcesToBePruned(resourcesFromLastSuccessfulRelease, currentResources);
    return arrangeResourceIdsInDeletionOrder(resourceIdsToBeDeleted);
  }

  public void addRevisionNumber(K8sRequestHandlerContext context, int revision) {
    try {
      VersionUtils.addRevisionNumber(context, revision);
    } catch (KubernetesYamlException exception) {
      throw NestedExceptionUtils.hintWithExplanationException(
          INVALID_RESOURCE_SPEC_HINT, INVALID_RESOURCE_SPEC_EXPLANATION, exception);
    }
  }

  public void addSuffixToConfigmapsAndSecrets(
      K8sRequestHandlerContext context, String suffix, LogCallback executionLogCallback) {
    try {
      VersionUtils.addSuffixToConfigmapsAndSecrets(context, suffix, executionLogCallback);
    } catch (KubernetesYamlException exception) {
      throw NestedExceptionUtils.hintWithExplanationException(
          INVALID_RESOURCE_SPEC_HINT, INVALID_RESOURCE_SPEC_EXPLANATION, exception);
    }
  }

  public boolean doStatusCheckAllResourcesForHelm(Kubectl client, List<KubernetesResourceId> resourceIds, String ocPath,
      String workingDir, String namespace, String kubeconfigPath, ExecutionLogCallback executionLogCallback,
      String gcpKeyFilePath) throws Exception {
    return doStatusCheckForAllResources(client, resourceIds,
        K8sDelegateTaskParams.builder()
            .ocPath(ocPath)
            .workingDirectory(workingDir)
            .kubeconfigPath(kubeconfigPath)
            .gcpKeyFilePath(gcpKeyFilePath)
            .build(),
        namespace, executionLogCallback, false);
  }

  public K8sClient getKubernetesClient(boolean useK8sApiForSteadyStateCheck) {
    if (useK8sApiForSteadyStateCheck) {
      return kubernetesApiClient;
    }
    return kubernetesCliClient;
  }

  private boolean writeManifestFilesToDirectory(StoreDelegateConfig storeDelegateConfig, String manifestFilesDirectory,
      LogCallback executionLogCallback, Boolean denoteOverallSuccess) {
    LocalFileStoreDelegateConfig localFileStoreDelegateConfig = (LocalFileStoreDelegateConfig) storeDelegateConfig;
    if (isNotEmpty(localFileStoreDelegateConfig.getManifestType())
        && isNotEmpty(localFileStoreDelegateConfig.getManifestIdentifier())) {
      executionLogCallback.saveExecutionLog("\n"
          + color(format("Fetching %s files with identifier: %s", localFileStoreDelegateConfig.getManifestType(),
                      localFileStoreDelegateConfig.getManifestIdentifier()),
              White, Bold));
      executionLogCallback.saveExecutionLog(color(format("Fetching manifest files at path: "), LogColor.White));
    } else {
      executionLogCallback.saveExecutionLog("\n" + color("Fetching manifest files", White, Bold));
    }
    List<String> scopedFilePathList = localFileStoreDelegateConfig.getFilePaths();
    printFilesFetchedFromHarnessStore(scopedFilePathList, executionLogCallback);
    executionLogCallback.saveExecutionLog(
        color(format("%nSuccessfully fetched following files: "), LogColor.White, LogWeight.Bold));
    String directoryPath = Paths.get(manifestFilesDirectory).toString();
    List<ManifestFiles> manifestFiles = localFileStoreDelegateConfig.getManifestFiles();
    try {
      for (int i = 0; i < manifestFiles.size(); i++) {
        ManifestFiles manifestFile = manifestFiles.get(i);
        if (StringUtils.equals(values_filename, manifestFile.getFileName())) {
          continue;
        }

        Path filePath = Paths.get(directoryPath, manifestFile.getFilePath());
        Path parent = filePath.getParent();
        if (parent == null) {
          throw new WingsException("Failed to create file at path " + filePath.toString());
        }

        createDirectoryIfDoesNotExist(parent.toString());
        if (isNotEmpty(manifestFile.getFileContent())) {
          FileIo.writeUtf8StringToFile(filePath.toString(), manifestFile.getFileContent());
          executionLogCallback.saveExecutionLog(color(format("- %s", manifestFile.getFilePath()), LogColor.White));
        } else {
          executionLogCallback.saveExecutionLog(color(format("- %s is empty", manifestFile.getFilePath()), Yellow));
        }
      }
      if (denoteOverallSuccess) {
        executionLogCallback.saveExecutionLog("Done.", INFO, SUCCESS);
      }
      return true;
    } catch (Exception ex) {
      executionLogCallback.saveExecutionLog(ExceptionUtils.getMessage(ExceptionMessageSanitizer.sanitizeException(ex)),
          ERROR, CommandExecutionStatus.FAILURE);
      return false;
    }
  }

  public void printFilesFetchedFromHarnessStore(List<String> scopedFilePathList, LogCallback logCallback) {
    for (String scopedFilePath : scopedFilePathList) {
      logCallback.saveExecutionLog(color(format("- %s", scopedFilePath), LogColor.White));
    }
  }

  public K8sReleaseHandler getReleaseHandler(boolean useDeclarativeRollback) {
    return releaseHandlerFactory.getK8sReleaseHandler(useDeclarativeRollback);
  }

  public List<KubernetesResourceId> getResourceIdsForDeletion(boolean useDeclarativeRollback, String releaseName,
      KubernetesConfig kubernetesConfig, LogCallback logCallback, boolean deleteNamespaceForRelease)
      throws IOException {
    K8sReleaseHandler releaseHandler = getReleaseHandler(useDeclarativeRollback);
    List<KubernetesResourceId> kubernetesResourceIds =
        releaseHandler.getResourceIdsToDelete(releaseName, kubernetesConfig, logCallback);

    // If namespace deletion is NOT selected,remove all Namespace resources from deletion list
    if (!deleteNamespaceForRelease) {
      kubernetesResourceIds =
          kubernetesResourceIds.stream()
              .filter(kubernetesResourceId -> !Namespace.name().equals(kubernetesResourceId.getKind()))
              .collect(toList());
    }

    return arrangeResourceIdsInDeletionOrder(kubernetesResourceIds);
  }

  public K8sReleaseHistoryCleanupDTO createReleaseHistoryCleanupRequest(String releaseName,
      IK8sReleaseHistory releaseHistory, Kubectl client, KubernetesConfig kubernetesConfig,
      LogCallback executionLogCallback, int currentReleaseNumber, K8sDelegateTaskParams k8sDelegateTaskParams) {
    return K8sReleaseHistoryCleanupDTO.builder()
        .releaseName(releaseName)
        .releaseHistory(releaseHistory)
        .client(client)
        .kubernetesConfig(kubernetesConfig)
        .logCallback(executionLogCallback)
        .currentReleaseNumber(currentReleaseNumber)
        .delegateTaskParams(k8sDelegateTaskParams)
        .build();
  }

  public K8sSteadyStateDTO createSteadyStateCheckRequest(K8sDeployRequest k8sDeployRequest,
      List<KubernetesResourceId> managedWorkloadKubernetesResourceIds, LogCallback waitForeSteadyStateLogCallback,
      K8sDelegateTaskParams k8sDelegateTaskParams, KubernetesConfig kubernetesConfig, boolean denoteOverallSuccess,
      boolean isErrorFrameworkEnabled) {
    return K8sSteadyStateDTO.builder()
        .request(k8sDeployRequest)
        .resourceIds(managedWorkloadKubernetesResourceIds)
        .executionLogCallback(waitForeSteadyStateLogCallback)
        .k8sDelegateTaskParams(k8sDelegateTaskParams)
        .namespace(kubernetesConfig.getNamespace())
        .denoteOverallSuccess(denoteOverallSuccess)
        .isErrorFrameworkEnabled(isErrorFrameworkEnabled)
        .kubernetesConfig(kubernetesConfig)
        .build();
  }

  public K8sReleasePersistDTO createSaveReleaseRequest(KubernetesConfig kubernetesConfig, IK8sRelease release,
      String releaseName, IK8sReleaseHistory releaseHistory, boolean storeInSecrets) {
    return K8sReleasePersistDTO.builder()
        .kubernetesConfig(kubernetesConfig)
        .release(release)
        .releaseName(releaseName)
        .releaseHistory(releaseHistory)
        .storeInSecrets(storeInSecrets)
        .build();
  }

  public void saveRelease(boolean useDeclarativeRollback, boolean storeInSecrets, KubernetesConfig kubernetesConfig,
      IK8sRelease release, IK8sReleaseHistory releaseHistory, String releaseName) throws Exception {
    K8sReleaseHandler releaseHandler = getReleaseHandler(useDeclarativeRollback);
    K8sReleasePersistDTO persistDTO =
        createSaveReleaseRequest(kubernetesConfig, release, releaseName, releaseHistory, storeInSecrets);
    releaseHandler.saveRelease(persistDTO);
  }

  private static String getLatestVersionOcPath() {
    String ocPath = "oc";
    try {
      ocPath = InstallUtils.getLatestVersionPath(OC);
    } catch (Exception ex) {
      log.warn("Unable to fetch OC binary path from delegate. Kindly ensure it is configured as env variable." + ex);
    }
    return ocPath;
  }

  public int getNextReleaseNumberFromOldReleaseHistory(KubernetesConfig kubernetesConfig, String releaseName)
      throws Exception {
    K8sReleaseHandler legacyReleaseHistoryHandler = getReleaseHandler(false);
    IK8sReleaseHistory legacyReleaseHistory =
        legacyReleaseHistoryHandler.getReleaseHistory(kubernetesConfig, releaseName);
    return legacyReleaseHistory.getAndIncrementLastReleaseNumber();
  }

  private String getOpenshiftTemplatePath(StoreDelegateConfig storeDelegateConfig, ManifestType manifestType) {
    String openshiftTemplatePath;
    switch (storeDelegateConfig.getType()) {
      case GIT:
        GitStoreDelegateConfig otGitStoreDelegateConfig = (GitStoreDelegateConfig) storeDelegateConfig;
        openshiftTemplatePath = otGitStoreDelegateConfig.getPaths().get(0);
        break;

      case HARNESS:
        LocalFileStoreDelegateConfig localFileStoreDelegateConfig = (LocalFileStoreDelegateConfig) storeDelegateConfig;
        openshiftTemplatePath = localFileStoreDelegateConfig.getManifestFiles().get(0).getFilePath();
        if (isNotEmpty(openshiftTemplatePath) && openshiftTemplatePath.charAt(0) == '/') {
          openshiftTemplatePath = openshiftTemplatePath.substring(1);
        }
        break;

      case CUSTOM_REMOTE:
        CustomRemoteStoreDelegateConfig customRemoteStoreDelegateConfig =
            (CustomRemoteStoreDelegateConfig) storeDelegateConfig;
        openshiftTemplatePath =
            getFileName(customRemoteStoreDelegateConfig.getCustomManifestSource().getFilePaths().get(0));
        break;

      default:
        throw new UnsupportedOperationException(
            String.format("Manifest delegate config type: [%s] not supported", manifestType.name()));
    }

    if (isEmpty(openshiftTemplatePath)) {
      throw NestedExceptionUtils.hintWithExplanationException(KubernetesExceptionHints.INVALID_TEMPLATE_PATH,
          KubernetesExceptionExplanation.INVALID_TEMPLATE_PATH,
          new InvalidArgumentsException("Invalid path to openshift template file"));
    }
    return openshiftTemplatePath;
  }

  public String applyKubeConfigToCommand(String command, String kubeConfigLocation) {
    if (isNotEmpty(kubeConfigLocation)) {
      return command.replace("${KUBECONFIG_PATH}", kubeConfigLocation);
    } else {
      return command.replace("KUBECONFIG=${KUBECONFIG_PATH}", EMPTY).trim();
    }
  }

  private String getFileName(String path) {
    return path != null ? (new File(path)).getName() : null;
  }

  public KubernetesResourceId findScalableKubernetesResourceIdFromWorkload(String workloadName) {
    List<String> kindNameRefs = Arrays.asList(workloadName.trim().split(","));
    if (kindNameRefs.size() == 1) {
      return createKubernetesResourceIdFromNamespaceKindName(workloadName);
    }
    List<KubernetesResourceId> kubernetesResourceIds = findScalableKubernetesResourceId(kindNameRefs);
    if (kubernetesResourceIds.size() != 1) {
      if (kubernetesResourceIds.isEmpty()) {
        throw new WingsException(
            "Invalid Kubernetes resource name " + String.join(",", kindNameRefs) + ". No workload found");
      }
      throw new WingsException("Invalid Kubernetes resource name " + String.join(",", kindNameRefs)
          + ". More than one workloads found. Others should be marked with annotation" + HarnessAnnotations.directApply
          + ": true");
    }
    return kubernetesResourceIds.get(0);
  }
}
