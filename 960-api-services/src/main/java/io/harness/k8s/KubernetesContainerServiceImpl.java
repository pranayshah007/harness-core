/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.encoding.EncodingUtils.compressString;
import static io.harness.data.encoding.EncodingUtils.deCompressString;
import static io.harness.data.encoding.EncodingUtils.decodeBase64;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.ACCESS_DENIED;
import static io.harness.eraro.ErrorCode.INVALID_CREDENTIAL;
import static io.harness.exception.WingsException.USER;
import static io.harness.filesystem.FileIo.writeUtf8StringToFile;
import static io.harness.k8s.K8sConstants.AZURE_KUBE_CONFIG_TEMPLATE;
import static io.harness.k8s.K8sConstants.CLIENT_ID_KEY;
import static io.harness.k8s.K8sConstants.CLIENT_SECRET_KEY;
import static io.harness.k8s.K8sConstants.GCP_KUBE_CONFIG_TEMPLATE;
import static io.harness.k8s.K8sConstants.HARNESS_KUBERNETES_REVISION_LABEL_KEY;
import static io.harness.k8s.K8sConstants.ID_TOKEN_KEY;
import static io.harness.k8s.K8sConstants.ISSUER_URL_KEY;
import static io.harness.k8s.K8sConstants.KUBE_CONFIG_OIDC_TEMPLATE;
import static io.harness.k8s.K8sConstants.KUBE_CONFIG_TEMPLATE;
import static io.harness.k8s.K8sConstants.MASTER_URL;
import static io.harness.k8s.K8sConstants.NAME;
import static io.harness.k8s.K8sConstants.NAMESPACE;
import static io.harness.k8s.K8sConstants.NAMESPACE_KEY;
import static io.harness.k8s.K8sConstants.OIDC_AUTH_NAME;
import static io.harness.k8s.K8sConstants.OIDC_AUTH_NAME_VAL;
import static io.harness.k8s.K8sConstants.OIDC_CLIENT_ID;
import static io.harness.k8s.K8sConstants.OIDC_CLIENT_SECRET;
import static io.harness.k8s.K8sConstants.OIDC_ID_TOKEN;
import static io.harness.k8s.K8sConstants.OIDC_ISSUER_URL;
import static io.harness.k8s.K8sConstants.OIDC_RERESH_TOKEN;
import static io.harness.k8s.K8sConstants.REFRESH_TOKEN;
import static io.harness.k8s.KubernetesConvention.CompressedReleaseHistoryFlag;
import static io.harness.k8s.KubernetesConvention.DASH;
import static io.harness.k8s.KubernetesConvention.ReleaseHistoryKeyName;
import static io.harness.k8s.KubernetesConvention.getPrefixFromControllerName;
import static io.harness.k8s.KubernetesConvention.getRevisionFromControllerName;
import static io.harness.k8s.KubernetesConvention.getServiceNameFromControllerName;
import static io.harness.k8s.model.ContainerApiVersions.KUBERNETES_V1;
import static io.harness.network.Http.connectableHttpUrl;
import static io.harness.state.StateConstants.DEFAULT_STEADY_STATE_TIMEOUT;
import static io.harness.threading.Morpheus.sleep;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Comparator.comparingInt;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;

import io.harness.annotations.dev.OwnedBy;
import io.harness.concurrent.HTimeLimiter;
import io.harness.container.ContainerInfo;
import io.harness.container.ContainerInfo.ContainerInfoBuilder;
import io.harness.container.ContainerInfo.Status;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UrlNotProvidedException;
import io.harness.exception.UrlNotReachableException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filesystem.FileIo;
import io.harness.k8s.apiclient.KubernetesApiCall;
import io.harness.k8s.config.K8sGlobalConfigService;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.Kind;
import io.harness.k8s.model.KubernetesClusterAuthType;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.response.CEK8sDelegatePrerequisite;
import io.harness.k8s.oidc.OidcTokenRetriever;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.logging.Misc;
import io.harness.oidc.model.OidcTokenRequestData;
import io.harness.retry.RetryHelper;
import io.harness.supplier.ThrowingSupplier;

import com.github.scribejava.apis.openid.OpenIdOAuth2AccessToken;
import com.google.api.client.util.Charsets;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.istio.api.networking.v1alpha3.DestinationRule;
import io.fabric8.istio.api.networking.v1alpha3.DestinationRuleBuilder;
import io.fabric8.istio.api.networking.v1alpha3.DestinationRuleList;
import io.fabric8.istio.api.networking.v1alpha3.HTTPRouteDestination;
import io.fabric8.istio.api.networking.v1alpha3.VirtualService;
import io.fabric8.istio.api.networking.v1alpha3.VirtualServiceList;
import io.fabric8.istio.api.networking.v1alpha3.VirtualServiceSpec;
import io.fabric8.istio.client.IstioClient;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStateRunning;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.ContainerStateWaiting;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerList;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetList;
import io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.extensions.DaemonSet;
import io.fabric8.kubernetes.api.model.extensions.DaemonSetList;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentList;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.DeploymentConfigList;
import io.fabric8.openshift.client.dsl.DeployableScalableResource;
import io.github.resilience4j.retry.Retry;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.AuthenticationV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.VersionApi;
import io.kubernetes.client.openapi.auth.ApiKeyAuth;
import io.kubernetes.client.openapi.auth.Authentication;
import io.kubernetes.client.openapi.auth.HttpBasicAuth;
import io.kubernetes.client.openapi.auth.HttpBearerAuth;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1SelfSubjectAccessReview;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.openapi.models.V1TokenReview;
import io.kubernetes.client.openapi.models.V1TokenReviewBuilder;
import io.kubernetes.client.openapi.models.V1TokenReviewStatus;
import io.kubernetes.client.openapi.models.VersionInfo;
import io.kubernetes.client.util.Watch;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Type;
import java.net.ConnectException;
import java.net.SocketException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.Deflater;
import javax.validation.constraints.NotNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.internal.http2.ConnectionShutdownException;
import okhttp3.internal.http2.StreamResetException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.DateTime;
import org.zeroturnaround.exec.ProcessResult;

/**
 * Created by brett on 2/9/17
 */
@Singleton
@Slf4j
@OwnedBy(CDP)
public class KubernetesContainerServiceImpl implements KubernetesContainerService {
  private static final String RUNNING = "Running";
  private static final String RESOURCE_NAME_FIELD = "metadata.name";
  private static final String K8S_SELECTOR_FORMAT = "%s=%s";
  private static final String K8S_SELECTOR_DELIMITER = ",";
  public static final String METRICS_SERVER_ABSENT = "CE.MetricsServerCheck: Please install metrics server.";
  public static final String RESOURCE_PERMISSION_REQUIRED =
      "CE: The provided serviceaccount is missing the following permissions: %n %s. Please grant these to the service account.";
  public static final Integer WATCH_CALL_TIMEOUT_SECONDS = 300;

  @Inject private KubernetesHelperService kubernetesHelperService = new KubernetesHelperService();
  @Inject private TimeLimiter timeLimiter;
  @Inject private Clock clock;
  @Inject private K8sResourceValidatorImpl k8sResourceValidator;
  @Inject private OidcTokenRetriever oidcTokenRetriever;
  @Inject private K8sGlobalConfigService k8sGlobalConfigService;

  private final Retry retry = buildRetryAndRegisterListeners();

  @Override
  public HasMetadata createOrReplaceController(KubernetesConfig kubernetesConfig, HasMetadata definition) {
    String name = definition.getMetadata().getName();
    log.info("Creating {} {}", definition.getKind(), name);

    // TODO - Use definition.getKind()
    HasMetadata controller = null;
    if (definition instanceof ReplicationController) {
      controller = rcOperations(kubernetesConfig, kubernetesConfig.getNamespace())
                       .createOrReplace((ReplicationController) definition);
    } else if (definition instanceof Deployment) {
      controller = deploymentOperations(kubernetesConfig, kubernetesConfig.getNamespace())
                       .createOrReplace((Deployment) definition);
    } else if (definition instanceof ReplicaSet) {
      controller =
          replicaOperations(kubernetesConfig, kubernetesConfig.getNamespace()).createOrReplace((ReplicaSet) definition);
    } else if (definition instanceof StatefulSet) {
      HasMetadata existing = getController(kubernetesConfig, name);
      if (existing != null && existing.getKind().equals("StatefulSet")) {
        controller = statefulOperations(kubernetesConfig, kubernetesConfig.getNamespace())
                         .withName(name)
                         .patch((StatefulSet) definition);
      } else {
        controller =
            statefulOperations(kubernetesConfig, kubernetesConfig.getNamespace()).create((StatefulSet) definition);
      }
    } else if (definition instanceof DaemonSet) {
      controller =
          daemonOperations(kubernetesConfig, kubernetesConfig.getNamespace()).createOrReplace((DaemonSet) definition);
    }
    return controller;
  }

  @Override
  public HasMetadata getController(KubernetesConfig kubernetesConfig, String name) {
    return getController(kubernetesConfig, name, kubernetesConfig.getNamespace());
  }

  @Override
  public HasMetadata getController(KubernetesConfig kubernetesConfig, String name, String namespace) {
    try {
      Callable<HasMetadata> controller = getControllerInternal(kubernetesConfig, name, namespace);
      return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMinutes(2), controller);
    } catch (WingsException e) {
      throw e;
    } catch (UncheckedTimeoutException e) {
      throw new WingsException(ErrorCode.GENERAL_ERROR, e).addParam("message", "Timed out while getting controller");
    } catch (Exception e) {
      throw new WingsException(ErrorCode.GENERAL_ERROR, e).addParam("message", "Error while getting controller");
    }
  }

  @SuppressWarnings("squid:S3776")
  private Callable<HasMetadata> getControllerInternal(
      KubernetesConfig kubernetesConfig, String name, String namespace) {
    return () -> {
      HasMetadata controller = null;
      log.info("Trying to get controller for name {}", name);
      if (isNotBlank(name)) {
        boolean success = false;
        boolean allFailed = true;
        while (!success) {
          try {
            try {
              controller = rcOperations(kubernetesConfig, namespace).withName(name).get();
              allFailed = false;
            } catch (Exception e) {
              // Ignore
            }
            if (controller == null) {
              try {
                controller = deploymentOperations(kubernetesConfig, namespace).withName(name).get();
                allFailed = false;
              } catch (Exception e) {
                // Ignore
              }
            }
            if (controller == null) {
              try {
                controller = replicaOperations(kubernetesConfig, namespace).withName(name).get();
                allFailed = false;
              } catch (Exception e) {
                // Ignore
              }
            }
            if (controller == null) {
              try {
                controller = statefulOperations(kubernetesConfig, namespace).withName(name).get();
                allFailed = false;
              } catch (Exception e) {
                // Ignore
              }
            }
            if (controller == null) {
              try {
                controller = daemonOperations(kubernetesConfig, namespace).withName(name).get();
                allFailed = false;
              } catch (Exception e) {
                // Ignore
              }
            }
            if (controller == null) {
              try {
                controller = deploymentConfigOperations(kubernetesConfig, namespace).withName(name).get();
                allFailed = false;
              } catch (Exception e) {
                // Ignore
              }
            }
            if (allFailed) {
              controller = deploymentOperations(kubernetesConfig, namespace).withName(name).get();
            } else {
              success = true;
            }
          } catch (Exception e) {
            log.warn("Exception while getting controller {}: {}:{}", name, e.getClass().getSimpleName(),
                ExceptionUtils.getMessage(e));
            if (e.getCause() != null) {
              log.warn("Caused by: {}:{}", e.getCause().getClass().getSimpleName(), e.getCause().getMessage());
            }

            // Special handling of k8s client 401/403 error. No need to retry...
            if (e instanceof KubernetesClientException) {
              KubernetesClientException clientException = (KubernetesClientException) e;
              int code = clientException.getCode();
              // error code 0 means connectivity issue. It will retry.
              switch (code) {
                case SC_UNAUTHORIZED:
                  throw new InvalidRequestException("Invalid credentials", e, INVALID_CREDENTIAL, USER);
                case SC_FORBIDDEN:
                  throw new InvalidRequestException("Access Denied", e, ACCESS_DENIED, USER);
                default:
                  log.warn("Got KubernetesClientException with error code {}", code);
                  break;
              }
            }

            sleep(ofSeconds(1));
            log.info("Retrying getController {} ...", name);
          }
        }
      }
      log.info("Got controller for name {}", name);
      return controller;
    };
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<? extends HasMetadata> getControllers(KubernetesConfig kubernetesConfig, Map<String, String> labels) {
    List<? extends HasMetadata> controllers = new ArrayList<>();
    boolean allFailed = true;
    try {
      controllers.addAll(
          (List) rcOperations(kubernetesConfig, kubernetesConfig.getNamespace()).withLabels(labels).list().getItems());
      allFailed = false;
    } catch (RuntimeException e) {
      // Ignore
    }
    try {
      controllers.addAll((List) deploymentOperations(kubernetesConfig, kubernetesConfig.getNamespace())
                             .withLabels(labels)
                             .list()
                             .getItems());
      allFailed = false;
    } catch (RuntimeException e) {
      // Ignore
    }
    try {
      controllers.addAll((List) replicaOperations(kubernetesConfig, kubernetesConfig.getNamespace())
                             .withLabels(labels)
                             .list()
                             .getItems());
      allFailed = false;
    } catch (RuntimeException e) {
      // Ignore
    }
    try {
      controllers.addAll((List) statefulOperations(kubernetesConfig, kubernetesConfig.getNamespace())
                             .withLabels(labels)
                             .list()
                             .getItems());
      allFailed = false;
    } catch (RuntimeException e) {
      // Ignore
    }
    try {
      controllers.addAll((List) daemonOperations(kubernetesConfig, kubernetesConfig.getNamespace())
                             .withLabels(labels)
                             .list()
                             .getItems());
      allFailed = false;
    } catch (RuntimeException e) {
      // Ignore
    }
    try {
      controllers.addAll((List) deploymentConfigOperations(kubernetesConfig, kubernetesConfig.getNamespace())
                             .withLabels(labels)
                             .list()
                             .getItems());
      allFailed = false;
    } catch (RuntimeException e) {
      // Ignore
    }
    if (allFailed) {
      controllers.addAll((List) deploymentOperations(kubernetesConfig, kubernetesConfig.getNamespace())
                             .withLabels(labels)
                             .list()
                             .getItems());
    }
    return controllers;
  }

  @Override
  public void validate(KubernetesConfig kubernetesConfig, boolean useNewKubectlVersion) {
    tryListControllersKubectl(kubernetesConfig, useNewKubectlVersion);
  }

  @Override
  public void validateMasterUrl(KubernetesConfig kubernetesConfig) {
    final String url = kubernetesConfig.getMasterUrl();
    if (url == null) {
      throw new UrlNotProvidedException("Url does not exist in the config");
    }
    final boolean isHostConnectable = connectableHttpUrl(url);
    if (!isHostConnectable) {
      throw new UrlNotReachableException("Could not connect to the master url: " + url);
    }
  }

  @Override
  public void validateCredentials(KubernetesConfig kubernetesConfig) {
    final Supplier<Void> versionApiCall = () -> {
      ApiClient apiClient = kubernetesHelperService.getApiClientWithReadTimeout(kubernetesConfig);
      KubernetesApiCall.call(apiClient, () -> new VersionApi(apiClient).getCodeCall(null));
      return null;
    };
    retry.executeSupplier(versionApiCall);
  }

  @Override
  public void tryListControllersKubectl(final KubernetesConfig kubernetesConfig, boolean useNewKubectlVersion) {
    ProcessResult result = null;
    final File kubeConfigDir = Files.createTempDir();
    try (ByteArrayOutputStream errStream = new ByteArrayOutputStream()) {
      persistKubernetesConfig(kubernetesConfig, kubeConfigDir.getPath());
      final Kubectl client = getKubectlClient(useNewKubectlVersion);

      for (final String workloadType : Arrays.asList(
               Kind.ReplicaSet.name(), Kind.StatefulSet.name(), Kind.DaemonSet.name(), Kind.Deployment.name())) {
        errStream.reset();
        result = client.get()
                     .resources(workloadType)
                     .namespace(kubernetesConfig.getNamespace())
                     .execute(kubeConfigDir.getPath(), null, errStream, false, Collections.emptyMap());
        if (0 == result.getExitValue()) {
          return;
        }
      }
      throw new InvalidRequestException(errStream.toString("UTF-8"), USER);
    } catch (IOException ex) {
      throw new GeneralException("Could not list deployments because kubeconfig could not be created", ex);
    } catch (WingsException ex) {
      throw ex;
    } catch (KubernetesClientException ex) {
      throw new InvalidRequestException(ex.getMessage(), ex, USER);
    } catch (Exception ex) {
      log.error("Failed to list Deployments", ex);
      throw new InvalidRequestException("Failed to List Deployments", USER);
    } finally {
      cleanupDir(kubeConfigDir);
    }
  }

  @Override
  public void persistKubernetesConfig(KubernetesConfig config, String dir) throws IOException {
    persistKubernetesConfigFile(config, dir);
    persistGcpJsonKeyFileIfNeeded(config, dir);
  }

  private void persistGcpJsonKeyFileIfNeeded(KubernetesConfig kubernetesConfig, String dir) throws IOException {
    if (kubernetesConfig.getGcpAccountKeyFileContent().isPresent()) {
      Path gcpKeyFilePath = Paths.get(dir, K8sConstants.GCP_JSON_KEY_FILE_NAME);
      writeUtf8StringToFile(gcpKeyFilePath.toString(), kubernetesConfig.getGcpAccountKeyFileContent().get());
    }
  }

  private void persistKubernetesConfigFile(KubernetesConfig config, String dir) throws IOException {
    String configFileContent = getConfigFileContent(config);
    writeUtf8StringToFile(Paths.get(dir, K8sConstants.KUBECONFIG_FILENAME).toString(), configFileContent);
  }

  @VisibleForTesting
  Kubectl getKubectlClient(boolean useNewKubectlVersion) {
    return Kubectl.client(
        k8sGlobalConfigService.getKubectlPath(useNewKubectlVersion), K8sConstants.KUBECONFIG_FILENAME);
  }

  private void cleanupDir(File kubeConfigDir) {
    try {
      FileIo.deleteDirectoryAndItsContentIfExists(kubeConfigDir.getPath());
    } catch (IOException e) {
      log.warn(format("Failed to cleanup directory %s", kubeConfigDir.getPath()), e);
    }
  }

  public void validateCEPermissions(KubernetesConfig kubernetesConfig) {
    ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
    validateCEMetricsServer(apiClient);
    validateCEResourcePermission(apiClient);
  }

  public void validateCEMetricsServer(ApiClient apiClient) {
    try {
      if (!k8sResourceValidator.validateMetricsServer(apiClient)) {
        throw new InvalidRequestException(METRICS_SERVER_ABSENT);
      }
    } catch (ApiException e) {
      log.error("Error validating Metrics Server", e);
      throw new InvalidRequestException(
          format("CE.MetricsServerCheck: code=%s message=%s. Try again, if it persists contact Harness Support.",
              e.getCode(), e.getResponseBody()));
    }
  }

  public void validateCEResourcePermission(ApiClient apiClient) {
    String result = k8sResourceValidator.validateCEPermissions(apiClient);
    if (!result.isEmpty()) {
      throw new InvalidRequestException(format(RESOURCE_PERMISSION_REQUIRED, result));
    }
  }

  @Override
  public CEK8sDelegatePrerequisite.MetricsServerCheck validateMetricsServer(KubernetesConfig kubernetesConfig) {
    try {
      boolean isInstalled =
          k8sResourceValidator.validateMetricsServer(kubernetesHelperService.getApiClient(kubernetesConfig));
      return CEK8sDelegatePrerequisite.MetricsServerCheck.builder().isInstalled(isInstalled).build();
    } catch (ApiException ex) {
      log.error("validateMetricsServer:ApiException ", ex);
      return CEK8sDelegatePrerequisite.MetricsServerCheck.builder()
          .isInstalled(false)
          .message(ex.getCode() + ":" + ex.getMessage())
          .build();
    }
  }

  @Override
  public List<CEK8sDelegatePrerequisite.Rule> validateCEResourcePermissions(KubernetesConfig kubernetesConfig) {
    return k8sResourceValidator.validateCEPermissions2(kubernetesHelperService.getApiClient(kubernetesConfig));
  }

  @Override
  @NotNull
  public List<V1SelfSubjectAccessReview> validateLightwingResourcePermissions(KubernetesConfig kubernetesConfig)
      throws Exception {
    List<V1SelfSubjectAccessReview> statuses = k8sResourceValidator.validateLightwingResourcePermissions(
        kubernetesHelperService.getApiClient(kubernetesConfig));
    log.info("Validated validateLightwingResourcePermissions, returning failed checks of size: {}",
        statuses.stream().filter(x -> FALSE.equals(x.getStatus().getAllowed())).count());
    return statuses;
  }

  @Override
  @NotNull
  public List<V1Status> validateLightwingResourceExists(KubernetesConfig kubernetesConfig) throws Exception {
    List<V1Status> statuses =
        k8sResourceValidator.validateLightwingResourceExists(kubernetesHelperService.getApiClient(kubernetesConfig));
    log.info("Validated validateLightwingResourceExists, returning failed checks of size: {}", statuses.size());
    return statuses;
  }

  @SneakyThrows
  @Override
  public V1TokenReviewStatus fetchTokenReviewStatus(KubernetesConfig kubernetesConfig) {
    ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);

    String token = kubernetesConfig.getServiceAccountTokenSupplier() == null
        ? kubernetesConfig.getServiceAccountTokenSupplier().get()
        : "";

    for (String key : apiClient.getAuthentications().keySet()) {
      log.info("ApiClint.Authentications key: [{}]", key);
      Authentication authentication = apiClient.getAuthentications().get(key);

      if (authentication instanceof HttpBearerAuth) {
        log.warn("HttpBearerAuth: seeing this first time");
      } else if (authentication instanceof HttpBasicAuth) {
        log.warn("HttpBasicAuth: seeing this first time");
      } else if (authentication instanceof ApiKeyAuth) {
        ApiKeyAuth apiKey = (ApiKeyAuth) authentication;
        log.debug("ApiKeyAuth: [{}, {}]", apiKey.getApiKeyPrefix(), apiKey.getApiKey());
        token = apiKey.getApiKey();
      }
    }

    AuthenticationV1Api api = new AuthenticationV1Api(apiClient);
    try {
      V1TokenReview tokenReview = api.createTokenReview(
          new V1TokenReviewBuilder().withNewSpec().withToken(token).endSpec().build(), null, null, null, null);

      log.info("V1TokenReviewStatus: [{}]", tokenReview.getStatus());

      return tokenReview.getStatus();
    } catch (ApiException ex) {
      throw new InvalidRequestException(ex.getResponseBody());
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<? extends HasMetadata> listControllers(KubernetesConfig kubernetesConfig) {
    List<? extends HasMetadata> controllers = new ArrayList<>();
    boolean allFailed = true;
    try {
      controllers.addAll((List) rcOperations(kubernetesConfig, kubernetesConfig.getNamespace()).list().getItems());
      allFailed = false;
    } catch (RuntimeException e) {
      // Ignore
    }
    try {
      controllers.addAll(
          (List) deploymentOperations(kubernetesConfig, kubernetesConfig.getNamespace()).list().getItems());
      allFailed = false;
    } catch (RuntimeException e) {
      // Ignore
    }
    try {
      controllers.addAll((List) replicaOperations(kubernetesConfig, kubernetesConfig.getNamespace()).list().getItems());
      allFailed = false;
    } catch (RuntimeException e) {
      // Ignore
    }
    try {
      controllers.addAll(
          (List) statefulOperations(kubernetesConfig, kubernetesConfig.getNamespace()).list().getItems());
      allFailed = false;
    } catch (RuntimeException e) {
      // Ignore
    }
    try {
      controllers.addAll((List) daemonOperations(kubernetesConfig, kubernetesConfig.getNamespace()).list().getItems());
      allFailed = false;
    } catch (RuntimeException e) {
      // Ignore
    }
    if (allFailed) {
      controllers.addAll(
          (List) deploymentOperations(kubernetesConfig, kubernetesConfig.getNamespace()).list().getItems());
    }
    return controllers;
  }

  @Override
  public void deleteController(KubernetesConfig kubernetesConfig, String name) {
    log.info("Deleting controller {}", name);
    if (isNotBlank(name)) {
      HasMetadata controller = getController(kubernetesConfig, name);
      if (controller instanceof ReplicationController) {
        rcOperations(kubernetesConfig, kubernetesConfig.getNamespace()).withName(name).delete();
      } else if (controller instanceof Deployment) {
        deploymentOperations(kubernetesConfig, kubernetesConfig.getNamespace()).withName(name).delete();
      } else if (controller instanceof ReplicaSet) {
        replicaOperations(kubernetesConfig, kubernetesConfig.getNamespace()).withName(name).delete();
      } else if (controller instanceof StatefulSet) {
        statefulOperations(kubernetesConfig, kubernetesConfig.getNamespace()).withName(name).delete();
      } else if (controller instanceof DaemonSet) {
        daemonOperations(kubernetesConfig, kubernetesConfig.getNamespace()).withName(name).delete();
      }
    }
  }

  @Override
  public HasMetadata createOrReplaceAutoscaler(KubernetesConfig kubernetesConfig, String autoscalerYaml) {
    if (isNotBlank(autoscalerYaml)) {
      HasMetadata hasMetadata;
      try {
        hasMetadata = KubernetesHelper.loadYaml(autoscalerYaml);
        hasMetadata.getMetadata().setResourceVersion(null);
      } catch (Exception e) {
        throw new WingsException(ErrorCode.INVALID_ARGUMENT, USER)
            .addParam("args", "Couldn't parse horizontal pod autoscaler YAML: " + autoscalerYaml);
      }
      String api = kubernetesHelperService.trimVersion(hasMetadata.getApiVersion());

      if (KUBERNETES_V1.getVersionName().equals(api)) {
        HorizontalPodAutoscaler v1Hpa = (HorizontalPodAutoscaler) hasMetadata;
        return kubernetesHelperService.hpaOperations(kubernetesConfig).createOrReplace(v1Hpa);
      } else {
        io.fabric8.kubernetes.api.model.autoscaling.v2beta1.HorizontalPodAutoscaler v2Beta1Hpa =
            (io.fabric8.kubernetes.api.model.autoscaling.v2beta1.HorizontalPodAutoscaler) hasMetadata;
        return kubernetesHelperService.hpaOperationsForCustomMetricHPA(kubernetesConfig, api)
            .createOrReplace(v2Beta1Hpa);
      }
    }
    return null;
  }

  @Override
  public HasMetadata getAutoscaler(KubernetesConfig kubernetesConfig, String name, String apiVersion) {
    if (KUBERNETES_V1.getVersionName().equals(apiVersion) || isEmpty(apiVersion)) {
      return kubernetesHelperService.hpaOperations(kubernetesConfig).withName(name).get();
    } else {
      return kubernetesHelperService.hpaOperationsForCustomMetricHPA(kubernetesConfig, apiVersion).withName(name).get();
    }
  }

  @Override
  public void deleteAutoscaler(KubernetesConfig kubernetesConfig, String name) {
    kubernetesHelperService.hpaOperations(kubernetesConfig).withName(name).delete();
  }

  @Override
  public List<ContainerInfo> setControllerPodCount(KubernetesConfig kubernetesConfig, String clusterName,
      String controllerName, int previousCount, int desiredCount, int serviceSteadyStateTimeout,
      LogCallback logCallback) {
    boolean sizeChanged = previousCount != desiredCount;
    long startTime = clock.millis();
    List<Pod> originalPods = getRunningPods(kubernetesConfig, controllerName);
    if (sizeChanged) {
      logCallback.saveExecutionLog(format("Resizing controller [%s] in cluster [%s] from %s to %s instances",
          controllerName, clusterName, previousCount, desiredCount));
      HasMetadata controller = getController(kubernetesConfig, controllerName);

      if (controller == null) {
        throw new WingsException(ErrorCode.INVALID_ARGUMENT)
            .addParam("args", "Could not find a controller named " + controllerName);
      }
      if (controller instanceof ReplicationController) {
        rcOperations(kubernetesConfig, kubernetesConfig.getNamespace()).withName(controllerName).scale(desiredCount);
      } else if (controller instanceof Deployment) {
        deploymentOperations(kubernetesConfig, kubernetesConfig.getNamespace())
            .withName(controllerName)
            .scale(desiredCount);
      } else if (controller instanceof ReplicaSet) {
        replicaOperations(kubernetesConfig, kubernetesConfig.getNamespace())
            .withName(controllerName)
            .scale(desiredCount);
      } else if (controller instanceof StatefulSet) {
        statefulOperations(kubernetesConfig, kubernetesConfig.getNamespace())
            .withName(controllerName)
            .scale(desiredCount);
      } else if (controller instanceof DaemonSet) {
        throw new WingsException(ErrorCode.INVALID_ARGUMENT)
            .addParam("args", "DaemonSet runs one instance per cluster node and cannot be scaled.");
      }

      log.info("Scaled controller {} in cluster {} from {} to {} instances", controllerName, clusterName, previousCount,
          desiredCount);
    } else {
      logCallback.saveExecutionLog(
          format("Controller [%s] in cluster [%s] stays at %s instances", controllerName, clusterName, previousCount));
    }
    return getContainerInfosWhenReady(kubernetesConfig, controllerName, previousCount, desiredCount,
        serviceSteadyStateTimeout, originalPods, false, logCallback, sizeChanged, startTime,
        kubernetesConfig.getNamespace());
  }

  @Override
  @SuppressWarnings("squid:S3776")
  public List<ContainerInfo> getContainerInfosWhenReady(KubernetesConfig kubernetesConfig, String controllerName,
      int previousCount, int desiredCount, int serviceSteadyStateTimeout, List<Pod> originalPods,
      boolean isNotVersioned, LogCallback logCallback, boolean wait, long startTime, String namespace) {
    List<Pod> pods = wait ? waitForPodsToBeRunning(kubernetesConfig, controllerName, previousCount, desiredCount,
                         serviceSteadyStateTimeout, originalPods, isNotVersioned, startTime, namespace, logCallback)
                          : originalPods;

    HasMetadata controllerInfo = getController(kubernetesConfig, controllerName, namespace);
    if (controllerInfo == null) {
      throw new InvalidRequestException(format("Could not find a controller named %s", controllerName));
    }
    int controllerDesiredCount = getControllerPodCount(controllerInfo);

    if (desiredCount == -1) {
      // This indicates wait for all pods to be in steady state. In case of HPA you won't know absolute numbers
      desiredCount = controllerDesiredCount;
    }

    Set<String> originalPodNames = originalPods.stream().map(pod -> pod.getMetadata().getName()).collect(toSet());
    List<ContainerInfo> containerInfos = new ArrayList<>();
    boolean hasErrors = false;
    if (wait && (pods.size() != desiredCount || controllerDesiredCount != desiredCount)) {
      hasErrors = true;
      String msg = "";
      if (controllerDesiredCount != desiredCount) {
        msg = format("Controller replica count is set to %d instead of %d. ", controllerDesiredCount, desiredCount);
      }
      if (pods.size() != desiredCount) {
        msg += format("Pod count did not reach desired count (%d/%d)", pods.size(), desiredCount);
      }
      log.error(msg);
      logCallback.saveExecutionLog(msg, LogLevel.ERROR);
    }
    for (Pod pod : pods) {
      String podName = pod.getMetadata().getName();
      String containerId = !pod.getStatus().getContainerStatuses().isEmpty()
          ? StringUtils.substring(pod.getStatus().getContainerStatuses().get(0).getContainerID(), 9, 21)
          : "";
      ContainerInfoBuilder containerInfoBuilder = ContainerInfo.builder()
                                                      .hostName(podName)
                                                      .ip(pod.getStatus().getPodIP())
                                                      .containerId(containerId)
                                                      .workloadName(controllerName)
                                                      .podName(podName)
                                                      .newContainer(!originalPodNames.contains(podName));

      HasMetadata controller = getController(kubernetesConfig, controllerName, namespace);
      PodTemplateSpec podTemplateSpec = null;
      if (null != controller) {
        podTemplateSpec = getPodTemplateSpec(controller);
      } else {
        log.warn("podTemplateSpec is null.");
      }
      Set<String> images = emptySet();
      if (null != podTemplateSpec) {
        images = getControllerImages(podTemplateSpec);
      } else {
        log.warn("Images is null.");
      }

      if (desiredCount > 0 && !podHasImages(pod, images)) {
        hasErrors = true;
        String msg = format("Pod %s does not have image %s", podName, images);
        log.error(msg);
        logCallback.saveExecutionLog(msg, LogLevel.ERROR);
      }

      if (isNotVersioned || desiredCount > previousCount) {
        if (!isRunning(pod)) {
          hasErrors = true;
          String msg = format("Pod %s failed to start", podName);
          log.error(msg);
          logCallback.saveExecutionLog(msg, LogLevel.ERROR);
        }

        if (!inSteadyState(pod)) {
          hasErrors = true;
          String msg = format("Pod %s failed to reach steady state", podName);
          log.error(msg);
          logCallback.saveExecutionLog(msg, LogLevel.ERROR);
        }
      }

      if (!hasErrors) {
        containerInfoBuilder.status(Status.SUCCESS);
        log.info("Pod {} started successfully", podName);
        logCallback.saveExecutionLog(format("Pod [%s] is running. Host IP: %s. Pod IP: %s", podName,
            pod.getStatus().getHostIP(), pod.getStatus().getPodIP()));
      } else {
        containerInfoBuilder.status(Status.FAILURE);
        String containerMessage = Joiner.on("], [").join(
            pod.getStatus().getContainerStatuses().stream().map(this::getContainerStatusMessage).collect(toList()));
        String conditionMessage = Joiner.on("], [").join(
            pod.getStatus().getConditions().stream().map(this::getPodConditionMessage).collect(toList()));
        String reason = Joiner.on("], [").join(pod.getStatus()
                                                   .getContainerStatuses()
                                                   .stream()
                                                   .map(containerStatus
                                                       -> containerStatus.getState().getTerminated() != null
                                                           ? containerStatus.getState().getTerminated().getReason()
                                                           : containerStatus.getState().getWaiting() != null
                                                           ? containerStatus.getState().getWaiting().getReason()
                                                           : RUNNING)
                                                   .collect(toList()));
        String msg =
            format("Pod [%s] has state [%s]. Current status: phase - %s. Container status: [%s]. Condition: [%s].",
                podName, reason, pod.getStatus().getPhase(), containerMessage, conditionMessage);
        log.error(msg);
        logCallback.saveExecutionLog(msg, LogLevel.ERROR);
        logCallback.saveExecutionLog("\nCheck Kubernetes console for more information");
      }
      containerInfos.add(containerInfoBuilder.build());
    }
    return containerInfos;
  }

  @Override
  public LinkedHashMap<String, Integer> getActiveServiceCounts(
      KubernetesConfig kubernetesConfig, String containerServiceName) {
    LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
    String controllerNamePrefix = getPrefixFromControllerName(containerServiceName);
    listControllers(kubernetesConfig)
        .stream()
        .filter(ctrl -> controllerNamePrefix.equals(getPrefixFromControllerName(ctrl.getMetadata().getName())))
        .filter(ctrl -> !(ctrl.getKind().equals("ReplicaSet") && ctrl.getMetadata().getOwnerReferences() != null))
        .filter(ctrl -> getControllerPodCount(ctrl) > 0)
        .sorted(comparingInt(ctrl -> getRevisionFromControllerName(ctrl.getMetadata().getName()).orElse(-1)))
        .forEach(ctrl -> result.put(ctrl.getMetadata().getName(), getControllerPodCount(ctrl)));
    return result;
  }

  @Override
  public LinkedHashMap<String, Integer> getActiveServiceCountsWithLabels(
      KubernetesConfig kubernetesConfig, Map<String, String> labels) {
    LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
    getControllers(kubernetesConfig, labels)
        .stream()
        .filter(ctrl -> !(ctrl.getKind().equals("ReplicaSet") && ctrl.getMetadata().getOwnerReferences() != null))
        .filter(ctrl -> getControllerPodCount(ctrl) > 0)
        .sorted(comparingInt(
            ctrl -> Integer.parseInt(ctrl.getMetadata().getLabels().get(HARNESS_KUBERNETES_REVISION_LABEL_KEY))))
        .forEach(ctrl -> result.put(ctrl.getMetadata().getName(), getControllerPodCount(ctrl)));
    return result;
  }

  @Override
  public Map<String, String> getActiveServiceImages(
      KubernetesConfig kubernetesConfig, String containerServiceName, String imagePrefix) {
    Map<String, String> result = new HashMap<>();
    String controllerNamePrefix = getPrefixFromControllerName(containerServiceName);
    listControllers(kubernetesConfig)
        .stream()
        .filter(ctrl -> !(ctrl.getKind().equals("ReplicaSet") && ctrl.getMetadata().getOwnerReferences() != null))
        .filter(ctrl -> ctrl.getMetadata().getName().startsWith(controllerNamePrefix))
        .filter(ctrl -> getControllerPodCount(ctrl) > 0)
        .filter(ctrl -> getRevisionFromControllerName(ctrl.getMetadata().getName()).isPresent())
        .forEach(ctrl
            -> result.put(ctrl.getMetadata().getName(),
                requireNonNull(getPodTemplateSpec(ctrl))
                    .getSpec()
                    .getContainers()
                    .stream()
                    .map(Container::getImage)
                    .filter(image -> image.startsWith(imagePrefix + ":"))
                    .findFirst()
                    .orElse("none")));
    return result;
  }

  private boolean inSteadyState(Pod pod) {
    List<PodCondition> conditions = pod.getStatus().getConditions();
    return isNotEmpty(conditions)
        && conditions.stream().allMatch(podCondition -> "True".equals(podCondition.getStatus()));
  }

  private boolean isRunning(Pod pod) {
    return pod.getStatus().getPhase().equals(RUNNING);
  }

  private boolean podHasImages(Pod pod, Set<String> images) {
    return pod.getSpec().getContainers().stream().map(Container::getImage).collect(toList()).containsAll(images);
  }

  private String getContainerStatusMessage(ContainerStatus status) {
    ContainerStateWaiting waiting = status.getState().getWaiting();
    ContainerStateTerminated terminated = status.getState().getTerminated();
    ContainerStateRunning running = status.getState().getRunning();
    String msg = status.getName();
    if (running != null) {
      msg += ": Started at " + running.getStartedAt();
    } else if (terminated != null) {
      msg += ": " + terminated.getReason() + " - " + terminated.getMessage();
    } else if (waiting != null) {
      msg += ": " + waiting.getReason() + " - " + waiting.getMessage();
    }
    return msg;
  }

  private String getPodConditionMessage(PodCondition cond) {
    String msg = cond.getType() + ": " + cond.getStatus();
    if (cond.getReason() != null) {
      msg += " - " + cond.getReason();
    }
    if (cond.getMessage() != null) {
      msg += " - " + cond.getMessage();
    }
    return msg;
  }

  @Override
  public Optional<Integer> getControllerPodCount(KubernetesConfig kubernetesConfig, String name) {
    HasMetadata controller = getController(kubernetesConfig, name);
    if (controller != null) {
      Integer count = getControllerPodCount(controller);
      return count == null ? Optional.empty() : Optional.of(count);
    }
    return Optional.empty();
  }

  @Override
  public Integer getControllerPodCount(HasMetadata controller) {
    if (controller instanceof ReplicationController) {
      return ((ReplicationController) controller).getSpec().getReplicas();
    } else if (controller instanceof Deployment) {
      return ((Deployment) controller).getSpec().getReplicas();
    } else if (controller instanceof ReplicaSet) {
      return ((ReplicaSet) controller).getSpec().getReplicas();
    } else if (controller instanceof StatefulSet) {
      return ((StatefulSet) controller).getSpec().getReplicas();
    } else if (controller instanceof DaemonSet) {
      return ((DaemonSet) controller).getStatus().getDesiredNumberScheduled();
    } else if (controller instanceof DeploymentConfig) {
      return ((DeploymentConfig) controller).getSpec().getReplicas();
    } else {
      throw new InvalidRequestException(
          format("Unhandled kubernetes resource type [%s] for getting the pod count", controller.getKind()));
    }
  }

  @Override
  public PodTemplateSpec getPodTemplateSpec(HasMetadata controller) {
    PodTemplateSpec podTemplateSpec = null;
    if (controller instanceof ReplicationController) {
      podTemplateSpec = ((ReplicationController) controller).getSpec().getTemplate();
    } else if (controller instanceof Deployment) {
      podTemplateSpec = ((Deployment) controller).getSpec().getTemplate();
    } else if (controller instanceof DaemonSet) {
      podTemplateSpec = ((DaemonSet) controller).getSpec().getTemplate();
    } else if (controller instanceof ReplicaSet) {
      podTemplateSpec = ((ReplicaSet) controller).getSpec().getTemplate();
    } else if (controller instanceof StatefulSet) {
      podTemplateSpec = ((StatefulSet) controller).getSpec().getTemplate();
    } else if (controller instanceof DeploymentConfig) {
      podTemplateSpec = ((DeploymentConfig) controller).getSpec().getTemplate();
    }
    return podTemplateSpec;
  }

  private NonNamespaceOperation<ReplicationController, ReplicationControllerList,
      RollableScalableResource<ReplicationController>>
  rcOperations(KubernetesConfig kubernetesConfig, String namespace) {
    namespace = isNotBlank(namespace) ? namespace : kubernetesConfig.getNamespace();
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .replicationControllers()
        .inNamespace(namespace);
  }

  private NonNamespaceOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> deploymentOperations(
      KubernetesConfig kubernetesConfig, String namespace) {
    namespace = isNotBlank(namespace) ? namespace : kubernetesConfig.getNamespace();
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .extensions()
        .deployments()
        .inNamespace(namespace);
  }

  private NonNamespaceOperation<ReplicaSet, ReplicaSetList, RollableScalableResource<ReplicaSet>> replicaOperations(
      KubernetesConfig kubernetesConfig, String namespace) {
    namespace = isNotBlank(namespace) ? namespace : kubernetesConfig.getNamespace();
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .extensions()
        .replicaSets()
        .inNamespace(namespace);
  }

  private NonNamespaceOperation<DaemonSet, DaemonSetList, Resource<DaemonSet>> daemonOperations(
      KubernetesConfig kubernetesConfig, String namespace) {
    namespace = isNotBlank(namespace) ? namespace : kubernetesConfig.getNamespace();
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .extensions()
        .daemonSets()
        .inNamespace(namespace);
  }

  private NonNamespaceOperation<StatefulSet, StatefulSetList, RollableScalableResource<StatefulSet>> statefulOperations(
      KubernetesConfig kubernetesConfig, String namespace) {
    namespace = isNotBlank(namespace) ? namespace : kubernetesConfig.getNamespace();
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig).apps().statefulSets().inNamespace(namespace);
  }

  private NonNamespaceOperation<DeploymentConfig, DeploymentConfigList, DeployableScalableResource<DeploymentConfig>>
  deploymentConfigOperations(KubernetesConfig kubernetesConfig, String namespace) {
    namespace = isNotBlank(namespace) ? namespace : kubernetesConfig.getNamespace();
    return kubernetesHelperService.getOpenShiftClient(kubernetesConfig).deploymentConfigs().inNamespace(namespace);
  }

  @Override
  public Service createOrReplaceServiceFabric8(KubernetesConfig kubernetesConfig, Service definition) {
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .services()
        .inNamespace(kubernetesConfig.getNamespace())
        .createOrReplace(definition);
  }

  @Override
  public V1Service createOrReplaceService(KubernetesConfig kubernetesConfig, V1Service definition) {
    String name = definition.getMetadata().getName();
    V1Service service = getService(kubernetesConfig, name);
    return service == null ? createService(kubernetesConfig, definition) : replaceService(kubernetesConfig, definition);
  }

  @VisibleForTesting
  V1Service createService(KubernetesConfig kubernetesConfig, V1Service definition) {
    String name = definition.getMetadata().getName();
    log.info("Creating service [{}]", name);
    final Supplier<V1Service> v1ServiceMapSupplier = Retry.decorateSupplier(retry, () -> {
      ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
      try {
        return new CoreV1Api(apiClient).createNamespacedService(
            kubernetesConfig.getNamespace(), definition, null, null, null, null);
      } catch (ApiException exception) {
        String serviceDef = definition.getMetadata() != null && isNotEmpty(definition.getMetadata().getName())
            ? format("%s/Service/%s", kubernetesConfig.getNamespace(), definition.getMetadata().getName())
            : "Service";
        String message = format(
            "Failed to create %s. Code: %s, message: %s", serviceDef, exception.getCode(), getErrorMessage(exception));
        log.error(message);
        throw new InvalidRequestException(message, exception, USER);
      }
    });
    return v1ServiceMapSupplier.get();
  }

  @VisibleForTesting
  V1Service replaceService(KubernetesConfig kubernetesConfig, V1Service definition) {
    String name = definition.getMetadata().getName();
    log.info("Replacing service [{}]", name);
    final Supplier<V1Service> v1ServiceSupplier = Retry.decorateSupplier(retry, () -> {
      ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
      try {
        return new CoreV1Api(apiClient).replaceNamespacedService(
            name, kubernetesConfig.getNamespace(), definition, null, null, null, null);
      } catch (ApiException exception) {
        String serviceDef = definition.getMetadata() != null && isNotEmpty(definition.getMetadata().getName())
            ? format("%s/Service/%s", kubernetesConfig.getNamespace(), definition.getMetadata().getName())
            : "Service";
        String message = format(
            "Failed to replace %s. Code: %s, message: %s", serviceDef, exception.getCode(), getErrorMessage(exception));
        log.error(message);
        throw new InvalidRequestException(message, exception, USER);
      }
    });

    return v1ServiceSupplier.get();
  }

  @Override
  public Service getServiceFabric8(KubernetesConfig kubernetesConfig, String name) {
    return isNotBlank(name) ? kubernetesHelperService.getKubernetesClient(kubernetesConfig)
                                  .services()
                                  .inNamespace(kubernetesConfig.getNamespace())
                                  .withName(name)
                                  .get()
                            : null;
  }

  @Override
  public V1Service getService(KubernetesConfig kubernetesConfig, String name, String namespace) {
    if (kubernetesConfig == null || isBlank(name)) {
      return null;
    }
    final Supplier<V1Service> v1ServiceSupplier = Retry.decorateSupplier(retry, () -> {
      try {
        ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
        return new CoreV1Api(apiClient).readNamespacedService(name, namespace, null);
      } catch (ApiException exception) {
        if (isResourceNotFoundException(exception.getCode())) {
          return null;
        }
        String message = format("Unable to get %s/Service/%s. Code: %s, message: %s", namespace, name,
            exception.getCode(), getErrorMessage(exception));
        log.error(message);
        throw new InvalidRequestException(message, exception, USER);
      }
    });
    return v1ServiceSupplier.get();
  }

  private boolean isResourceNotFoundException(int code) {
    return code == 404;
  }

  @Override
  public V1Service getService(KubernetesConfig kubernetesConfig, String name) {
    return kubernetesConfig == null ? null : getService(kubernetesConfig, name, kubernetesConfig.getNamespace());
  }

  @Override
  public List<Service> getServices(KubernetesConfig kubernetesConfig, Map<String, String> labels) {
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .services()
        .inNamespace(kubernetesConfig.getNamespace())
        .withLabels(labels)
        .list()
        .getItems();
  }

  @Override
  public void deleteService(KubernetesConfig kubernetesConfig, String name) {
    log.info("Deleting service {}", name);
    kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .services()
        .inNamespace(kubernetesConfig.getNamespace())
        .withName(name)
        .delete();
  }

  @Override
  public Ingress createOrReplaceIngress(KubernetesConfig kubernetesConfig, Ingress definition) {
    String name = definition.getMetadata().getName();
    Ingress ingress = kubernetesHelperService.getKubernetesClient(kubernetesConfig)
                          .extensions()
                          .ingresses()
                          .inNamespace(kubernetesConfig.getNamespace())
                          .withName(name)
                          .get();
    log.info("{} ingress [{}]", ingress == null ? "Creating" : "Replacing", name);
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .extensions()
        .ingresses()
        .inNamespace(kubernetesConfig.getNamespace())
        .createOrReplace(definition);
  }

  @Override
  public Ingress getIngress(KubernetesConfig kubernetesConfig, String name) {
    return isNotBlank(name) ? kubernetesHelperService.getKubernetesClient(kubernetesConfig)
                                  .extensions()
                                  .ingresses()
                                  .inNamespace(kubernetesConfig.getNamespace())
                                  .withName(name)
                                  .get()
                            : null;
  }

  @Override
  public void deleteIngress(KubernetesConfig kubernetesConfig, String name) {
    log.info("Deleting service {}", name);
    kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .extensions()
        .ingresses()
        .inNamespace(kubernetesConfig.getNamespace())
        .withName(name)
        .delete();
  }

  @Override
  public ConfigMap createOrReplaceConfigMapFabric8(KubernetesConfig kubernetesConfig, ConfigMap definition) {
    String name = definition.getMetadata().getName();
    ConfigMap configMap = kubernetesHelperService.getKubernetesClient(kubernetesConfig)
                              .configMaps()
                              .inNamespace(kubernetesConfig.getNamespace())
                              .withName(name)
                              .get();
    log.info("{} config map [{}]", configMap == null ? "Creating" : "Replacing", name);
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .configMaps()
        .inNamespace(kubernetesConfig.getNamespace())
        .createOrReplace(definition);
  }

  @VisibleForTesting
  V1ConfigMap replaceConfigMap(KubernetesConfig kubernetesConfig, V1ConfigMap definition) {
    String name = definition.getMetadata().getName();
    log.info("Replacing config map [{}]", name);
    final Supplier<V1ConfigMap> v1ConfigMapSupplier = Retry.decorateSupplier(retry, () -> {
      ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
      try {
        return new CoreV1Api(apiClient).replaceNamespacedConfigMap(
            name, kubernetesConfig.getNamespace(), definition, null, null, null, null);
      } catch (ApiException exception) {
        String configMapDef = definition.getMetadata() != null && isNotEmpty(definition.getMetadata().getName())
            ? format("%s/ConfigMap/%s", kubernetesConfig.getNamespace(), definition.getMetadata().getName())
            : "ConfigMap";
        String message = format("Failed to replace %s. Code: %s, message: %s", configMapDef, exception.getCode(),
            getErrorMessage(exception));
        log.error(message);
        throw new InvalidRequestException(message, exception, USER);
      }
    });

    return v1ConfigMapSupplier.get();
  }

  private V1ConfigMap createConfigMap(KubernetesConfig kubernetesConfig, V1ConfigMap definition) {
    String name = definition.getMetadata().getName();
    log.info("Creating config map [{}]", name);
    final Supplier<V1ConfigMap> v1ConfigMapSupplier = Retry.decorateSupplier(retry, () -> {
      ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
      try {
        return new CoreV1Api(apiClient).createNamespacedConfigMap(
            kubernetesConfig.getNamespace(), definition, null, null, null, null);
      } catch (ApiException exception) {
        String configMapDef = definition.getMetadata() != null && isNotEmpty(definition.getMetadata().getName())
            ? format("%s/ConfigMap/%s", kubernetesConfig.getNamespace(), definition.getMetadata().getName())
            : "ConfigMap";
        String message = format("Failed to create %s. Code: %s, message: %s", configMapDef, exception.getCode(),
            getErrorMessage(exception));
        log.error(message);
        throw new InvalidRequestException(message, exception, USER);
      }
    });
    return v1ConfigMapSupplier.get();
  }

  @Override
  @Deprecated
  public ConfigMap getConfigMapFabric8(KubernetesConfig kubernetesConfig, String name) {
    try {
      return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
          .configMaps()
          .inNamespace(kubernetesConfig.getNamespace())
          .withName(name)
          .get();
    } catch (Exception e) {
      log.error("Failed to get ConfigMap/{}", name, e);
      return null;
    }
  }

  @Override
  public V1ConfigMap getConfigMap(KubernetesConfig kubernetesConfig, String name) {
    final Supplier<V1ConfigMap> v1ConfigMapSupplier = Retry.decorateSupplier(retry, () -> {
      try {
        ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
        return new CoreV1Api(apiClient).readNamespacedConfigMap(name, kubernetesConfig.getNamespace(), null);
      } catch (ApiException exception) {
        if (isResourceNotFoundException(exception.getCode())) {
          return null;
        }
        String message = format("Failed to get %s/ConfigMap/%s. Code: %s, message: %s", kubernetesConfig.getNamespace(),
            name, exception.getCode(), getErrorMessage(exception));
        log.error(message);
        throw new InvalidRequestException(message, exception, USER);
      }
    });
    return v1ConfigMapSupplier.get();
  }

  private String getErrorMessage(ApiException apiException) {
    String errMsg = apiException.getMessage();
    String responseBody = apiException.getResponseBody();
    if (isEmpty(errMsg)) {
      if (apiException.getCause() != null) {
        errMsg = apiException.getCause().getMessage();
      } else {
        errMsg = "";
      }
    }

    if (!isEmpty(responseBody)) {
      errMsg = errMsg + " Response body: " + responseBody;
    }
    return errMsg;
  }

  @Override
  @Deprecated
  public void deleteConfigMapFabric8(KubernetesConfig kubernetesConfig, String name) {
    kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .configMaps()
        .inNamespace(kubernetesConfig.getNamespace())
        .withName(name)
        .delete();
  }

  @Override
  public void deleteConfigMap(KubernetesConfig kubernetesConfig, String name) {
    ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
    retry.executeRunnable(() -> {
      try {
        new CoreV1Api(apiClient).deleteNamespacedConfigMap(
            name, kubernetesConfig.getNamespace(), null, null, null, null, null, null);
      } catch (ApiException exception) {
        String message = format("Failed to delete %s/ConfigMap/%s. Code: %s, message: %s",
            kubernetesConfig.getNamespace(), name, exception.getCode(), getErrorMessage(exception));
        log.error(message);
        throw new InvalidRequestException(message, exception, USER);
      }
    });
  }

  @Override
  public VirtualService createOrReplaceFabric8IstioVirtualService(
      KubernetesConfig kubernetesConfig, VirtualService definition) {
    String name = definition.getMetadata().getName();
    String kind = definition.getKind();
    log.info("Registering {} [{}]", kind, name);
    IstioClient fabric8IstioClient = kubernetesHelperService.getFabric8IstioClient(kubernetesConfig);
    return fabric8IstioClient.v1alpha3().virtualServices().createOrReplace(definition);
  }

  @Override
  public DestinationRule createOrReplaceFabric8IstioDestinationRule(
      KubernetesConfig kubernetesConfig, DestinationRule definition) {
    String name = definition.getMetadata().getName();
    String kind = definition.getKind();
    log.info("Registering {} [{}]", kind, name);
    IstioClient fabric8IstioClient = kubernetesHelperService.getFabric8IstioClient(kubernetesConfig);
    return fabric8IstioClient.v1alpha3().destinationRules().createOrReplace(definition);
  }

  @Override
  public VirtualService getFabric8IstioVirtualService(KubernetesConfig kubernetesConfig, String name) {
    KubernetesClient kubernetesClient = kubernetesHelperService.getKubernetesClient(kubernetesConfig);
    try {
      return kubernetesClient.resources(VirtualService.class, VirtualServiceList.class)
          .inNamespace(kubernetesConfig.getNamespace())
          .withName(name)
          .get();
    } catch (Exception e) {
      log.error("Failed to get istio VirtualService/{}", name, e);
      return null;
    }
  }

  @Override
  public DestinationRule getFabric8IstioDestinationRule(KubernetesConfig kubernetesConfig, String name) {
    KubernetesClient kubernetesClient = kubernetesHelperService.getKubernetesClient(kubernetesConfig);
    try {
      DestinationRule destinationRule = new DestinationRuleBuilder().build();
      return kubernetesClient.resources(DestinationRule.class, DestinationRuleList.class)
          .inNamespace(kubernetesConfig.getNamespace())
          .withName(name)
          .get();
    } catch (Exception e) {
      log.error("Failed to get istio DestinationRule/{}", name, e);
      return null;
    }
  }

  @Override
  public void deleteIstioDestinationRule(KubernetesConfig kubernetesConfig, String name) {
    IstioClient istioClient = kubernetesHelperService.getFabric8IstioClient(kubernetesConfig);
    try {
      istioClient.v1alpha3().destinationRules().inNamespace(kubernetesConfig.getNamespace()).withName(name).delete();
    } catch (Exception e) {
      log.info(e.getMessage());
    }
  }

  @Override
  public void deleteIstioVirtualService(KubernetesConfig kubernetesConfig, String name) {
    IstioClient istioClient = kubernetesHelperService.getFabric8IstioClient(kubernetesConfig);
    try {
      istioClient.v1alpha3().virtualServices().inNamespace(kubernetesConfig.getNamespace()).withName(name).delete();
    } catch (Exception e) {
      log.info(e.getMessage());
    }
  }

  @Override
  public int getTrafficPercent(KubernetesConfig kubernetesConfig, String controllerName) {
    String serviceName = getServiceNameFromControllerName(controllerName);
    VirtualService virtualService = getFabric8IstioVirtualService(kubernetesConfig, serviceName);
    Optional<Integer> revision = getRevisionFromControllerName(controllerName);
    if (virtualService == null || !revision.isPresent()) {
      return 0;
    }
    VirtualServiceSpec virtualServiceSpec = ((VirtualService) virtualService).getSpec();
    if (isEmpty(virtualServiceSpec.getHttp()) || isEmpty(virtualServiceSpec.getHttp().get(0).getRoute())) {
      return 0;
    }

    return virtualServiceSpec.getHttp()
        .get(0)
        .getRoute()
        .stream()
        .filter(dw -> Integer.toString(revision.get()).equals(dw.getDestination().getSubset()))
        .map(HTTPRouteDestination::getWeight)
        .findFirst()
        .orElse(0);
  }

  @Override
  public Map<String, Integer> getTrafficWeights(KubernetesConfig kubernetesConfig, String controllerName) {
    String serviceName = getServiceNameFromControllerName(controllerName);
    String controllerNamePrefix = getPrefixFromControllerName(controllerName);
    VirtualService virtualService = getFabric8IstioVirtualService(kubernetesConfig, serviceName);
    if (virtualService == null) {
      return new HashMap<>();
    }

    VirtualServiceSpec virtualServiceSpec = ((VirtualService) virtualService).getSpec();
    if (isEmpty(virtualServiceSpec.getHttp()) || isEmpty(virtualServiceSpec.getHttp().get(0).getRoute())) {
      return new HashMap<>();
    }
    List<HTTPRouteDestination> destinationWeights = virtualServiceSpec.getHttp().get(0).getRoute();
    return destinationWeights.stream().collect(
        toMap(dw -> controllerNamePrefix + DASH + dw.getDestination().getSubset(), HTTPRouteDestination::getWeight));
  }

  @Override
  public void createNamespaceIfNotExist(KubernetesConfig kubernetesConfig) {
    try {
      Namespace namespace = kubernetesHelperService.getKubernetesClient(kubernetesConfig)
                                .namespaces()
                                .withName(kubernetesConfig.getNamespace())
                                .get();
      if (namespace == null) {
        log.info("Creating namespace [{}]", kubernetesConfig.getNamespace());
        kubernetesHelperService.getKubernetesClient(kubernetesConfig)
            .namespaces()
            .create(new NamespaceBuilder()
                        .withNewMetadata()
                        .withName(kubernetesConfig.getNamespace())
                        .endMetadata()
                        .build());
      }
    } catch (Exception e) {
      log.error("Couldn't get or create namespace {}", kubernetesConfig.getNamespace(), e);
    }
  }

  @Override
  @Deprecated
  public Secret getSecretFabric8(KubernetesConfig kubernetesConfig, String secretName) {
    return isNotBlank(secretName) ? kubernetesHelperService.getKubernetesClient(kubernetesConfig)
                                        .secrets()
                                        .inNamespace(kubernetesConfig.getNamespace())
                                        .withName(secretName)
                                        .get()
                                  : null;
  }

  @Override
  public V1Secret getSecret(KubernetesConfig kubernetesConfig, String secretName) {
    if (isBlank(secretName)) {
      return null;
    }

    final Supplier<V1Secret> v1SecretSupplier = Retry.decorateSupplier(retry, () -> {
      ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
      try {
        return new CoreV1Api(apiClient).readNamespacedSecret(secretName, kubernetesConfig.getNamespace(), null);
      } catch (ApiException exception) {
        if (isResourceNotFoundException(exception.getCode())) {
          return null;
        }
        String message = format("Failed to get %s/Secret/%s. Code: %s, message: %s", kubernetesConfig.getNamespace(),
            secretName, exception.getCode(), getErrorMessage(exception));
        log.error(message);
        throw new InvalidRequestException(message, exception, USER);
      }
    });
    return v1SecretSupplier.get();
  }

  @Override
  @Deprecated
  public void deleteSecretFabric8(KubernetesConfig kubernetesConfig, String secretName) {
    kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .secrets()
        .inNamespace(kubernetesConfig.getNamespace())
        .withName(secretName)
        .delete();
  }

  @Override
  public void deleteSecret(KubernetesConfig kubernetesConfig, String secretName) {
    retry.executeRunnable(() -> {
      ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
      try {
        new CoreV1Api(apiClient).deleteNamespacedSecret(
            secretName, kubernetesConfig.getNamespace(), null, null, null, null, null, null);
      } catch (ApiException exception) {
        String message = format("Failed to delete %s/Secret/%s. Code: %s, message: %s", kubernetesConfig.getNamespace(),
            secretName, exception.getCode(), getErrorMessage(exception));
        log.error(message);
        throw new InvalidRequestException(message, exception, USER);
      }
    });
  }

  @Override
  @Deprecated
  public Secret createOrReplaceSecretFabric8(KubernetesConfig kubernetesConfig, Secret secret) {
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .secrets()
        .inNamespace(kubernetesConfig.getNamespace())
        .createOrReplace(secret);
  }

  @Override
  public V1Secret createOrReplaceSecret(KubernetesConfig kubernetesConfig, V1Secret definition) {
    String name = definition.getMetadata().getName();
    V1Secret secret = getSecret(kubernetesConfig, name);
    return createOrReplaceSecret(kubernetesConfig, definition, secret != null);
  }

  private V1Secret createOrReplaceSecret(KubernetesConfig kubernetesConfig, V1Secret secret, boolean secretExists) {
    return secretExists ? replaceSecret(kubernetesConfig, secret) : createSecret(kubernetesConfig, secret);
  }

  @Override
  public V1ConfigMap createOrReplaceConfigMap(KubernetesConfig kubernetesConfig, V1ConfigMap definition) {
    String name = definition.getMetadata().getName();
    V1ConfigMap configMap = getConfigMap(kubernetesConfig, name);
    return createOrReplaceConfigMap(kubernetesConfig, definition, configMap != null);
  }

  private V1ConfigMap createOrReplaceConfigMap(
      KubernetesConfig kubernetesConfig, V1ConfigMap configmap, boolean configMapExists) {
    return configMapExists ? replaceConfigMap(kubernetesConfig, configmap)
                           : createConfigMap(kubernetesConfig, configmap);
  }

  @VisibleForTesting
  V1Secret createSecret(KubernetesConfig kubernetesConfig, V1Secret secret) {
    log.info("Creating secret [{}]", secret.getMetadata().getName());
    final Supplier<V1Secret> v1SecretSupplier = Retry.decorateSupplier(retry, () -> {
      ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
      try {
        return new CoreV1Api(apiClient).createNamespacedSecret(
            kubernetesConfig.getNamespace(), secret, null, null, null, null);
      } catch (ApiException exception) {
        String secretDef = secret.getMetadata() != null && isNotEmpty(secret.getMetadata().getName())
            ? format("%s/Secret/%s", kubernetesConfig.getNamespace(), secret.getMetadata().getName())
            : "Secret";
        String message = format(
            "Failed to create %s. Code: %s, message: %s", secretDef, exception.getCode(), getErrorMessage(exception));
        log.error(message);
        throw new InvalidRequestException(message, exception, USER);
      }
    });
    return v1SecretSupplier.get();
  }

  @VisibleForTesting
  V1Secret replaceSecret(KubernetesConfig kubernetesConfig, V1Secret secret) {
    String name = secret.getMetadata().getName();
    log.info("Replacing secret [{}]", name);
    ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
    try {
      return new CoreV1Api(apiClient).replaceNamespacedSecret(
          name, kubernetesConfig.getNamespace(), secret, null, null, null, null);
    } catch (ApiException exception) {
      String secretDef = secret.getMetadata() != null && isNotEmpty(secret.getMetadata().getName())
          ? format("%s/Secret/%s", kubernetesConfig.getNamespace(), secret.getMetadata().getName())
          : "Secret";
      String message = format(
          "Failed to replace %s. Code: %s, message: %s", secretDef, exception.getCode(), getErrorMessage(exception));
      log.error(message);
      throw new InvalidRequestException(message, exception, USER);
    }
  }

  @Override
  public List<Pod> getPods(KubernetesConfig kubernetesConfig, Map<String, String> labels) {
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .pods()
        .inNamespace(kubernetesConfig.getNamespace())
        .withLabels(labels)
        .list()
        .getItems();
  }

  private List<Pod> prunePodsInFinalState(List<Pod> pods) {
    return pods.stream()
        .filter(pod
            -> !StringUtils.equals(pod.getStatus().getPhase(), "Failed")
                && !StringUtils.equals(pod.getStatus().getPhase(), "Succeeded"))
        .collect(toList());
  }

  @Override
  public void waitForPodsToStop(KubernetesConfig kubernetesConfig, Map<String, String> labels,
      int serviceSteadyStateTimeout, List<Pod> originalPods, long startTime, LogCallback logCallback) {
    KubernetesClient kubernetesClient = kubernetesHelperService.getKubernetesClient(kubernetesConfig);
    List<String> originalPodNames = originalPods.stream().map(pod -> pod.getMetadata().getName()).collect(toList());
    String namespace = kubernetesConfig.getNamespace();
    String waitingMsg = "Waiting for pods to stop...";
    log.info(waitingMsg);
    try {
      Callable<Boolean> callable = () -> {
        Set<String> seenEvents = new HashSet<>();

        while (true) {
          logCallback.saveExecutionLog(waitingMsg);
          List<Pod> pods = kubernetesClient.pods().inNamespace(namespace).withLabels(labels).list().getItems();

          showPodEvents(kubernetesClient, namespace, pods, originalPodNames, seenEvents, startTime, logCallback);

          pods = prunePodsInFinalState(pods);
          if (pods.size() <= 0) {
            return TRUE;
          }
          sleep(ofSeconds(5));
        }
      };
      HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMinutes(serviceSteadyStateTimeout), callable);
    } catch (UncheckedTimeoutException e) {
      String msg = "Timed out waiting for pods to stop";
      log.error(msg, e);
      logCallback.saveExecutionLog(msg, LogLevel.ERROR);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new WingsException(ErrorCode.GENERAL_ERROR, e).addParam("message", "Error while waiting for pods to stop");
    }
  }

  @SuppressWarnings({"squid:S00107", "squid:S3776"})
  private List<Pod> waitForPodsToBeRunning(KubernetesConfig kubernetesConfig, String controllerName, int previousCount,
      int desiredCount, int serviceSteadyStateTimeout, List<Pod> originalPods, boolean isNotVersioned, long startTime,
      String namespace, LogCallback executionLogCallback) {
    HasMetadata controller = getController(kubernetesConfig, controllerName, namespace);
    if (controller == null) {
      throw new InvalidArgumentsException(Pair.of(controllerName, "is null"));
    }
    PodTemplateSpec podTemplateSpec = getPodTemplateSpec(controller);
    if (podTemplateSpec == null) {
      throw new InvalidArgumentsException(Pair.of(controllerName + " pod spec", "is null"));
    }
    Set<String> images = getControllerImages(podTemplateSpec);
    Map<String, String> labels = podTemplateSpec.getMetadata().getLabels();
    List<String> originalPodNames = originalPods.stream().map(pod -> pod.getMetadata().getName()).collect(toList());
    KubernetesClient kubernetesClient = kubernetesHelperService.getKubernetesClient(kubernetesConfig);
    log.info("Waiting for pods to be ready...");
    AtomicBoolean countReached = new AtomicBoolean(false);
    AtomicBoolean haveImagesCountReached = new AtomicBoolean(false);
    AtomicBoolean runningCountReached = new AtomicBoolean(false);
    AtomicBoolean steadyStateCountReached = new AtomicBoolean(false);

    try {
      int waitMinutes = serviceSteadyStateTimeout > 0 ? serviceSteadyStateTimeout : DEFAULT_STEADY_STATE_TIMEOUT;
      Callable<List<Pod>> callable = () -> {
        Set<String> seenEvents = new HashSet<>();

        while (true) {
          try {
            int absoluteDesiredCount = desiredCount;
            HasMetadata currentController = getController(kubernetesConfig, controllerName, namespace);
            if (currentController != null) {
              int controllerDesiredCount = getControllerPodCount(currentController);
              absoluteDesiredCount = (desiredCount == -1) ? controllerDesiredCount : desiredCount;
              if (controllerDesiredCount != absoluteDesiredCount) {
                String msg = format("Replica count is set to %d instead of %d. [Could be due to HPA.]",
                    controllerDesiredCount, absoluteDesiredCount);
                log.warn(msg);
                executionLogCallback.saveExecutionLog(msg, LogLevel.ERROR);
              }
            } else {
              String msg = "Couldn't find controller " + controllerName;
              log.error(msg);
              executionLogCallback.saveExecutionLog(msg, LogLevel.ERROR);
            }

            showControllerEvents(
                kubernetesClient, namespace, controllerName, seenEvents, startTime, executionLogCallback);

            List<Pod> pods = kubernetesClient.pods().inNamespace(namespace).withLabels(labels).list().getItems();

            // Show pod events
            showPodEvents(
                kubernetesClient, namespace, pods, originalPodNames, seenEvents, startTime, executionLogCallback);

            pods = prunePodsInFinalState(pods);

            // Check current state
            if (pods.size() != absoluteDesiredCount) {
              executionLogCallback.saveExecutionLog(
                  format("Waiting for desired number of pods [%d/%d]", pods.size(), absoluteDesiredCount));
              sleep(ofSeconds(5));
              continue;
            }
            if (!countReached.getAndSet(true)) {
              executionLogCallback.saveExecutionLog(
                  format("Desired number of pods reached [%d/%d]", pods.size(), absoluteDesiredCount));
            }

            if (absoluteDesiredCount > 0) {
              int haveImages = (int) pods.stream().filter(pod -> podHasImages(pod, images)).count();
              if (haveImages != absoluteDesiredCount) {
                executionLogCallback.saveExecutionLog(format("Waiting for pods to be updated with image %s [%d/%d]",
                                                          images, haveImages, absoluteDesiredCount),
                    LogLevel.INFO);
                sleep(ofSeconds(5));
                continue;
              }
              if (!haveImagesCountReached.getAndSet(true)) {
                executionLogCallback.saveExecutionLog(
                    format("Pods are updated with image %s [%d/%d]", images, haveImages, absoluteDesiredCount));
              }
            }

            if (isNotVersioned || absoluteDesiredCount > previousCount) {
              int running = (int) pods.stream().filter(this::isRunning).count();
              if (running != absoluteDesiredCount) {
                executionLogCallback.saveExecutionLog(
                    format("Waiting for pods to be running [%d/%d]", running, absoluteDesiredCount));
                sleep(ofSeconds(10));
                continue;
              }
              if (!runningCountReached.getAndSet(true)) {
                executionLogCallback.saveExecutionLog(
                    format("Pods are running [%d/%d]", running, absoluteDesiredCount));
              }

              int steadyState = (int) pods.stream().filter(this::inSteadyState).count();
              if (steadyState != absoluteDesiredCount) {
                executionLogCallback.saveExecutionLog(
                    format("Waiting for pods to reach steady state [%d/%d]", steadyState, absoluteDesiredCount));
                sleep(ofSeconds(15));
                continue;
              }
              if (!steadyStateCountReached.getAndSet(true)) {
                executionLogCallback.saveExecutionLog(
                    format("Pods have reached steady state [%d/%d]", steadyState, absoluteDesiredCount));
              }
            }
            return pods;
          } catch (Exception e) {
            log.error("Exception in pod state wait loop.", e);
            executionLogCallback.saveExecutionLog("Error while waiting for pods to be ready", LogLevel.ERROR);
            Misc.logAllMessages(e, executionLogCallback);
            executionLogCallback.saveExecutionLog("Continuing to wait...", LogLevel.ERROR);
            sleep(ofSeconds(15));
          }
        }
      };
      return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMinutes(waitMinutes), callable);
    } catch (UncheckedTimeoutException e) {
      String msg = "Timed out waiting for pods to be ready";
      log.error(msg, e);
      executionLogCallback.saveExecutionLog(msg, LogLevel.ERROR);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new WingsException(ErrorCode.GENERAL_ERROR, e)
          .addParam("message", "Error while waiting for pods to be ready");
    }

    return kubernetesClient.pods().inNamespace(namespace).withLabels(labels).list().getItems();
  }

  private void showPodEvents(KubernetesClient kubernetesClient, String namespace, List<Pod> currentPods,
      List<String> originalPodNames, Set<String> seenEvents, long startTime, LogCallback executionLogCallback) {
    try {
      Set<String> podNames = new LinkedHashSet<>(originalPodNames);
      podNames.addAll(currentPods.stream().map(pod -> pod.getMetadata().getName()).collect(toList()));

      List<Event> newEvents = kubernetesClient.v1()
                                  .events()
                                  .inNamespace(namespace)
                                  .list()
                                  .getItems()
                                  .stream()
                                  .filter(evt -> !seenEvents.contains(evt.getMetadata().getName()))
                                  .filter(evt -> podNames.contains(evt.getInvolvedObject().getName()))
                                  .filter(evt -> DateTime.parse(evt.getLastTimestamp()).getMillis() > startTime)
                                  .collect(toList());

      if (isNotEmpty(newEvents)) {
        executionLogCallback.saveExecutionLog("\n****  Kubernetes Pod Events  ****");
        podNames.forEach(podName -> {
          List<Event> podEvents =
              newEvents.stream().filter(evt -> evt.getInvolvedObject().getName().equals(podName)).collect(toList());
          if (isNotEmpty(podEvents)) {
            executionLogCallback.saveExecutionLog("  Pod: " + podName);
            podEvents.forEach(evt -> executionLogCallback.saveExecutionLog("   - " + evt.getMessage()));
          }
        });
        executionLogCallback.saveExecutionLog("");
        seenEvents.addAll(newEvents.stream().map(evt -> evt.getMetadata().getName()).collect(toList()));
      }
    } catch (Exception e) {
      Misc.logAllMessages(e, executionLogCallback);
      log.error("Failed to process kubernetes pod events", e);
    }
  }

  private void showControllerEvents(KubernetesClient kubernetesClient, String namespace, String controllerName,
      Set<String> seenEvents, long startTime, LogCallback executionLogCallback) {
    try {
      List<Event> newEvents = kubernetesClient.v1()
                                  .events()
                                  .inNamespace(namespace)
                                  .list()
                                  .getItems()
                                  .stream()
                                  .filter(evt -> !seenEvents.contains(evt.getMetadata().getName()))
                                  .filter(evt -> controllerName.equals(evt.getInvolvedObject().getName()))
                                  .filter(evt -> DateTime.parse(evt.getLastTimestamp()).getMillis() > startTime)
                                  .collect(toList());

      if (isNotEmpty(newEvents)) {
        executionLogCallback.saveExecutionLog("\n****  Kubernetes Controller Events  ****");
        executionLogCallback.saveExecutionLog("  Controller: " + controllerName);
        newEvents.forEach(evt -> executionLogCallback.saveExecutionLog("   - " + evt.getMessage()));
        executionLogCallback.saveExecutionLog("");
        seenEvents.addAll(newEvents.stream().map(evt -> evt.getMetadata().getName()).collect(toList()));
      }
    } catch (Exception e) {
      Misc.logAllMessages(e, executionLogCallback);
      log.error("Failed to process kubernetes controller events", e);
    }
  }

  @Override
  public List<Pod> getRunningPods(KubernetesConfig kubernetesConfig, String controllerName) {
    HasMetadata controller = getController(kubernetesConfig, controllerName);
    PodTemplateSpec podTemplateSpec = getPodTemplateSpec(controller);
    if (podTemplateSpec == null) {
      return emptyList();
    }
    Map<String, String> labels = podTemplateSpec.getMetadata().getLabels();
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .pods()
        .inNamespace(kubernetesConfig.getNamespace())
        .withLabels(labels)
        .list()
        .getItems();
  }

  private Set<String> getControllerImages(PodTemplateSpec template) {
    return template.getSpec().getContainers().stream().map(Container::getImage).collect(toSet());
  }

  public void checkStatus(KubernetesConfig kubernetesConfig, String rcName, String serviceName) {
    KubernetesClient client = kubernetesHelperService.getKubernetesClient(kubernetesConfig);
    String masterUrl = client.getMasterUrl().toString();
    ReplicationController rc =
        client.replicationControllers().inNamespace(kubernetesConfig.getNamespace()).withName(rcName).get();
    if (rc != null) {
      String rcLink = masterUrl + rc.getMetadata().getSelfLink().substring(1);
      log.info("Controller {}: {}", rcName, rcLink);
    } else {
      log.info("Controller {} does not exist", rcName);
    }
    Service service = client.services().inNamespace(kubernetesConfig.getNamespace()).withName(serviceName).get();
    if (service != null) {
      String serviceLink = masterUrl + service.getMetadata().getSelfLink().substring(1);
      log.info("Service: {}, link: {}", serviceName, serviceLink);
    } else {
      log.info("Service {} does not exist", serviceName);
    }
  }

  @Override
  public String fetchReleaseHistoryFromConfigMap(KubernetesConfig kubernetesConfig, String releaseName)
      throws IOException {
    V1ConfigMap configMap = getConfigMap(kubernetesConfig, releaseName);
    if (configMap != null && configMap.getData() != null && configMap.getData().containsKey(ReleaseHistoryKeyName)) {
      return fetchReleaseHistoryValue(configMap);
    }

    return EMPTY;
  }

  @Override
  public String fetchReleaseHistoryFromSecrets(KubernetesConfig kubernetesConfig, String releaseName)
      throws IOException {
    V1Secret secret = getSecret(kubernetesConfig, releaseName);
    if (secret != null && secret.getData() != null && secret.getData().containsKey(ReleaseHistoryKeyName)) {
      return fetchReleaseHistoryValue(secret);
    }

    return EMPTY;
  }

  @Override
  public String fetchReleaseHistoryValue(V1ConfigMap configMap) throws IOException {
    Map<String, String> configMapData = configMap.getData();
    String releaseHistory = configMapData.get(ReleaseHistoryKeyName);

    if (configMapData.containsKey(CompressedReleaseHistoryFlag)
        && Boolean.parseBoolean(configMapData.get(CompressedReleaseHistoryFlag))) {
      return deCompressString(decodeBase64(releaseHistory));
    }
    return releaseHistory;
  }

  @Override
  public String fetchReleaseHistoryValue(V1Secret secret) throws IOException {
    Map<String, byte[]> secretData = secret.getData();
    byte[] releaseHistory = secretData.get(ReleaseHistoryKeyName);

    if (secretData.containsKey(CompressedReleaseHistoryFlag) && secretData.get(CompressedReleaseHistoryFlag)[0] == 1) {
      return deCompressString(releaseHistory);
    }
    return new String(releaseHistory, Charsets.UTF_8);
  }

  @Override
  public V1ObjectMeta saveReleaseHistory(KubernetesConfig kubernetesConfig, String releaseName, String releaseHistory,
      boolean storeInSecrets) throws IOException {
    if (storeInSecrets) {
      return saveReleaseHistoryInSecrets(kubernetesConfig, releaseName, releaseHistory).getMetadata();
    } else {
      return saveReleaseHistoryInConfigMap(kubernetesConfig, releaseName, releaseHistory).getMetadata();
    }
  }

  private V1ConfigMap saveReleaseHistoryInConfigMap(
      KubernetesConfig kubernetesConfig, String releaseName, String releaseHistory) throws IOException {
    V1ConfigMap configMap = getConfigMap(kubernetesConfig, releaseName);
    String compressedB64EncodedReleaseHistory = encodeBase64(compressString(releaseHistory, Deflater.BEST_COMPRESSION));
    boolean configMapExists = false;

    if (configMap == null) {
      configMap = new V1ConfigMapBuilder()
                      .withMetadata(new V1ObjectMetaBuilder()
                                        .withName(releaseName)
                                        .withNamespace(kubernetesConfig.getNamespace())
                                        .build())
                      .withData(ImmutableMap.of(ReleaseHistoryKeyName, compressedB64EncodedReleaseHistory,
                          CompressedReleaseHistoryFlag, "true"))
                      .build();
    } else {
      configMap.putDataItem(ReleaseHistoryKeyName, compressedB64EncodedReleaseHistory);
      configMap.putDataItem(CompressedReleaseHistoryFlag, "true");
      configMapExists = true;
    }

    return createOrReplaceConfigMap(kubernetesConfig, configMap, configMapExists);
  }

  private V1Secret saveReleaseHistoryInSecrets(
      KubernetesConfig kubernetesConfig, String releaseName, String releaseHistory) throws IOException {
    V1Secret secret = getSecret(kubernetesConfig, releaseName);
    byte[] compressedReleaseHistory = compressString(releaseHistory, Deflater.BEST_COMPRESSION);
    boolean secretExists = false;

    if (secret == null) {
      secret = new V1SecretBuilder()
                   .withMetadata(new V1ObjectMetaBuilder()
                                     .withNamespace(kubernetesConfig.getNamespace())
                                     .withName(releaseName)
                                     .build())
                   .withData(ImmutableMap.of(ReleaseHistoryKeyName, compressedReleaseHistory,
                       CompressedReleaseHistoryFlag, new byte[] {(byte) 1}))
                   .build();
    } else {
      secret.putDataItem(ReleaseHistoryKeyName, compressedReleaseHistory);
      secret.putDataItem(CompressedReleaseHistoryFlag, new byte[] {(byte) 1});
      secretExists = true;
    }

    return createOrReplaceSecret(kubernetesConfig, secret, secretExists);
  }

  @Override
  public List<V1Pod> getRunningPodsWithLabels(
      KubernetesConfig kubernetesConfig, String namespace, Map<String, String> labels) {
    final Supplier<List<V1Pod>> podSupplier = Retry.decorateSupplier(retry, () -> {
      try {
        ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
        String labelSelector = labels.entrySet()
                                   .stream()
                                   .map(entry -> format(K8S_SELECTOR_FORMAT, entry.getKey(), entry.getValue()))
                                   .collect(Collectors.joining(K8S_SELECTOR_DELIMITER));
        V1PodList podList = new CoreV1Api(apiClient).listNamespacedPod(
            namespace, null, null, null, null, labelSelector, null, null, null, null, false);
        return podList.getItems()
            .stream()
            .filter(pod
                -> pod.getMetadata() != null && pod.getMetadata().getDeletionTimestamp() == null
                    && pod.getStatus() != null && StringUtils.equals(pod.getStatus().getPhase(), RUNNING))
            .collect(toList());
      } catch (ApiException exception) {
        String message = format(
            "Unable to get running pods. Code: %s, message: %s", exception.getCode(), getErrorMessage(exception));
        throw new InvalidRequestException(message, exception, USER);
      }
    });
    return podSupplier.get();
  }

  @Override
  public V1Deployment getDeployment(KubernetesConfig kubernetesConfig, String namespace, String name) {
    if (kubernetesConfig == null || isBlank(name)) {
      return null;
    }
    final Supplier<V1Deployment> decorateSupplier = Retry.decorateSupplier(retry, () -> {
      try {
        ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);

        return new AppsV1Api(apiClient).readNamespacedDeployment(name, namespace, null);
      } catch (ApiException exception) {
        if (isResourceNotFoundException(exception.getCode())) {
          return null;
        }
        String message = format("Unable to get %s/deployment/%s. Code: %s, message: %s", namespace, name,
            exception.getCode(), getErrorMessage(exception));
        throw new InvalidRequestException(message, exception, USER);
      }
    });
    return decorateSupplier.get();
  }

  @Override
  public VersionInfo getVersion(KubernetesConfig kubernetesConfig) {
    final Supplier<VersionInfo> versionInfoSupplier = Retry.decorateSupplier(retry, () -> {
      try {
        ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
        return new VersionApi(apiClient).getCode();
      } catch (ApiException exception) {
        String message = format(
            "Unable to retrieve k8s version. Code: %s, message: %s", exception.getCode(), exception.getMessage());
        log.error(message);
        throw new InvalidRequestException(message, exception, USER);
      }
    });
    return versionInfoSupplier.get();
  }

  @Override
  public String getVersionAsString(KubernetesConfig kubernetesConfig) {
    VersionInfo versionInfo = getVersion(kubernetesConfig);
    return format("%s.%s", versionInfo.getMajor(), versionInfo.getMinor());
  }

  @Override
  public String getConfigFileContent(KubernetesConfig config) {
    encodeCharsIfNeeded(config);

    if (isBlank(config.getMasterUrl())) {
      return "";
    }

    if (KubernetesClusterAuthType.OIDC == config.getAuthType()) {
      OidcTokenRequestData oidcTokenRequestData = oidcTokenRetriever.createOidcTokenRequestData(config);
      return generateKubeConfigStringForOpenID(config, oidcTokenRequestData);
    }

    if (KubernetesClusterAuthType.GCP_OAUTH == config.getAuthType()) {
      return generateKubeConfigStringForGcp(config);
    }

    if (KubernetesClusterAuthType.AZURE_OAUTH == config.getAuthType()) {
      return generateKubeConfigStringForAzure(config);
    }

    String insecureSkipTlsVerify = isEmpty(config.getCaCert()) ? "insecure-skip-tls-verify: true" : "";
    String certificateAuthorityData =
        isNotEmpty(config.getCaCert()) ? "certificate-authority-data: " + new String(config.getCaCert()) : "";
    String clientCertData =
        isNotEmpty(config.getClientCert()) ? "client-certificate-data: " + new String(config.getClientCert()) : "";
    String clientKeyData =
        isNotEmpty(config.getClientKey()) ? "client-key-data: " + new String(config.getClientKey()) : "";
    String password = isNotEmpty(config.getPassword()) ? "password: " + new String(config.getPassword()) : "";
    String username = isNotEmpty(config.getUsername()) ? "username: " + new String(config.getUsername()) : "";
    String namespace = isNotEmpty(config.getNamespace()) ? "namespace: " + config.getNamespace() : "";
    String serviceAccountTokenData = config.getServiceAccountTokenSupplier() != null
        ? "token: " + config.getServiceAccountTokenSupplier().get()
        : "";

    return KUBE_CONFIG_TEMPLATE.replace("${MASTER_URL}", config.getMasterUrl())
        .replace("${INSECURE_SKIP_TLS_VERIFY}", insecureSkipTlsVerify)
        .replace("${CERTIFICATE_AUTHORITY_DATA}", certificateAuthorityData)
        .replace("${NAMESPACE}", namespace)
        .replace("${USER_NAME}", username)
        .replace("${CLIENT_CERT_DATA}", clientCertData)
        .replace("${CLIENT_KEY_DATA}", clientKeyData)
        .replace("${PASSWORD}", password)
        .replace("${SERVICE_ACCOUNT_TOKEN_DATA}", serviceAccountTokenData);
  }

  @Override
  public List<V1Secret> getSecretsWithLabelsAndFields(KubernetesConfig kubernetesConfig, String labels, String fields) {
    final Supplier<List<V1Secret>> secretSupplier = Retry.decorateSupplier(retry, () -> {
      ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
      String namespace = kubernetesConfig.getNamespace();
      try {
        V1SecretList secrets = new CoreV1Api(apiClient).listNamespacedSecret(
            namespace, null, null, null, fields, labels, null, null, null, null, null);
        return secrets.getItems();
      } catch (ApiException e) {
        throw new InvalidRequestException(
            String.format("Unable to get secrets from namespace: %s %nCode: %s, message: %s, labels: %s, fields: %s",
                namespace, e.getCode(), getErrorMessage(e), labels, fields));
      }
    });
    return secretSupplier.get();
  }

  @Override
  public V1Status deleteSecrets(KubernetesConfig kubernetesConfig, String labels, String fields) {
    final Supplier<V1Status> secretSupplier = Retry.decorateSupplier(retry, () -> {
      ApiClient apiClient = kubernetesHelperService.getApiClient(kubernetesConfig);
      String namespace = kubernetesConfig.getNamespace();
      try {
        return new CoreV1Api(apiClient).deleteCollectionNamespacedSecret(
            namespace, null, null, null, fields, null, labels, null, null, null, null, null, null, null);
      } catch (ApiException e) {
        throw new InvalidRequestException(
            String.format("Unable to delete secrets from namespace %s %nCode: %s, message: %s, labels: %s, fields: %s",
                namespace, e.getCode(), getErrorMessage(e), labels, fields));
      }
    });
    return secretSupplier.get();
  }

  private String generateKubeConfigStringForGcp(KubernetesConfig config) {
    String insecureSkipTlsVerify = isEmpty(config.getCaCert()) ? "insecure-skip-tls-verify: true" : "";
    String certificateAuthorityData =
        isNotEmpty(config.getCaCert()) ? "certificate-authority-data: " + new String(config.getCaCert()) : "";
    String namespace = isNotEmpty(config.getNamespace()) ? "namespace: " + config.getNamespace() : "";

    return GCP_KUBE_CONFIG_TEMPLATE.replace("${MASTER_URL}", config.getMasterUrl())
        .replace("${INSECURE_SKIP_TLS_VERIFY}", insecureSkipTlsVerify)
        .replace("${CERTIFICATE_AUTHORITY_DATA}", certificateAuthorityData)
        .replace("${NAMESPACE}", namespace);
  }

  private String generateKubeConfigStringForAzure(KubernetesConfig config) {
    String insecureSkipTlsVerify = isEmpty(config.getCaCert()) ? "insecure-skip-tls-verify: true" : "";
    String certificateAuthorityData =
        isNotEmpty(config.getCaCert()) ? "certificate-authority-data: " + new String(config.getCaCert()) : "";
    String namespace = isNotEmpty(config.getNamespace()) ? "namespace: " + config.getNamespace() : "";

    return AZURE_KUBE_CONFIG_TEMPLATE.replace("${MASTER_URL}", config.getMasterUrl())
        .replace("${INSECURE_SKIP_TLS_VERIFY}", insecureSkipTlsVerify)
        .replace("${CERTIFICATE_AUTHORITY_DATA}", certificateAuthorityData)
        .replace("${NAMESPACE}", namespace)
        .replace("${CLUSTER_NAME}", config.getAzureConfig().getClusterName())
        .replace("${CLUSTER_USER}", config.getAzureConfig().getClusterUser())
        .replace("${CURRENT_CONTEXT}", config.getAzureConfig().getCurrentContext())
        .replace("${APISERVER_ID}", config.getAzureConfig().getApiServerId())
        .replace("${CLIENT_ID}", config.getAzureConfig().getClientId())
        .replace("${CONFIG_MODE}", config.getAzureConfig().getConfigMode())
        .replace("${ENVIRONMENT}", config.getAzureConfig().getEnvironment())
        .replace("${TENANT_ID}", config.getAzureConfig().getTenantId())
        .replace("${TOKEN}", config.getAzureConfig().getAadIdToken());
  }

  private void encodeCharsIfNeeded(KubernetesConfig config) {
    config.setCaCert(getEncodedChars(config.getCaCert()));
    config.setClientCert(getEncodedChars(config.getClientCert()));
    config.setClientKey(getEncodedChars(config.getClientKey()));
  }

  private char[] getEncodedChars(char[] chars) {
    if (isEmpty(chars) || !(new String(chars).startsWith("-----BEGIN "))) {
      return chars;
    }
    return encodeBase64(chars).toCharArray();
  }

  @VisibleForTesting
  String generateKubeConfigStringForOpenID(KubernetesConfig config, OidcTokenRequestData oidcTokenRequestData) {
    OpenIdOAuth2AccessToken openIdOAuth2AccessToken =
        oidcTokenRetriever.retrieveOpenIdAccessToken(oidcTokenRequestData);

    String clientIdData =
        isNotEmpty(oidcTokenRequestData.getClientId()) ? CLIENT_ID_KEY + oidcTokenRequestData.getClientId() : EMPTY;
    String clientSecretData = isNotEmpty(oidcTokenRequestData.getClientSecret())
        ? CLIENT_SECRET_KEY + oidcTokenRequestData.getClientSecret()
        : EMPTY;
    String idToken = isNotEmpty(openIdOAuth2AccessToken.getOpenIdToken())
        ? ID_TOKEN_KEY + openIdOAuth2AccessToken.getOpenIdToken()
        : EMPTY;
    String providerUrl = isNotEmpty(oidcTokenRequestData.getProviderUrl())
        ? ISSUER_URL_KEY + oidcTokenRequestData.getProviderUrl()
        : EMPTY;
    String refreshToken = isNotEmpty(openIdOAuth2AccessToken.getRefreshToken())
        ? REFRESH_TOKEN + openIdOAuth2AccessToken.getRefreshToken()
        : EMPTY;
    String authConfigName = NAME + OIDC_AUTH_NAME_VAL;
    String namespace = isNotEmpty(config.getNamespace()) ? NAMESPACE_KEY + config.getNamespace() : EMPTY;

    return KUBE_CONFIG_OIDC_TEMPLATE.replace(MASTER_URL, config.getMasterUrl())
        .replace(NAMESPACE, namespace)
        .replace(OIDC_CLIENT_ID, clientIdData)
        .replace(OIDC_CLIENT_SECRET, clientSecretData)
        .replace(OIDC_ID_TOKEN, idToken)
        .replace(OIDC_ISSUER_URL, providerUrl)
        .replace(OIDC_RERESH_TOKEN, refreshToken)
        .replace(OIDC_AUTH_NAME, authConfigName);
  }

  public <T extends KubernetesObject> boolean watchRetriesWrapper(WorkloadDetails workloadDetails,
      ThrowingSupplier<Call> callSupplier, Predicate<Watch.Response<T>> consumer) throws Exception {
    boolean success;
    try {
      success = watchWithRetries(
          workloadDetails.getWorkloadType(), callSupplier.get(), workloadDetails.getApiClient(), consumer);
    } catch (ApiException e) {
      ApiException ex = ExceptionMessageSanitizer.sanitizeException(e);
      String errorMessage = String.format("Failed to watch rollout status for workload [%s]. ",
                                workloadDetails.getK8sWorkload().kindNameRef())
          + ExceptionUtils.getMessage(ex);
      log.error(errorMessage, ex);
      workloadDetails.getLogCallback().saveExecutionLog(errorMessage, LogLevel.ERROR);
      if (workloadDetails.isErrorFramework()) {
        throw e;
      }
      return false;
    } catch (RuntimeException e) {
      if (e.getCause() != null) {
        if (e.getCause() instanceof InterruptedIOException) {
          log.warn("Kubernetes watch was aborted.", e);
          Thread.currentThread().interrupt();
          return false;
        }
      }
      log.error("Runtime exception during Kubernetes watch.", e);
      throw e;
    }
    return success;
  }

  public <T extends KubernetesObject> boolean watchWithRetries(
      Type type, Call call, ApiClient apiClient, Predicate<Watch.Response<T>> consumer) {
    final Supplier<Boolean> v1Supplier = Retry.decorateSupplier(retry, () -> {
      while (!Thread.currentThread().isInterrupted()) {
        try (Watch<T> watch = Watch.createWatch(apiClient, call.clone(), type)) {
          for (Watch.Response<T> event : watch) {
            if (consumer.test(event)) {
              return true;
            }
          }
        } catch (IOException e) {
          IOException ex = ExceptionMessageSanitizer.sanitizeException(e);
          String errorMessage = "Failed to close Kubernetes watch." + ExceptionUtils.getMessage(ex);
          log.error(errorMessage, ex);
          return false;
        } catch (ApiException e) {
          throw new RuntimeException(e);
        }
      }
      return false;
    });
    return v1Supplier.get();
  }

  private Retry buildRetryAndRegisterListeners() {
    final Retry exponentialRetry = RetryHelper.getExponentialRetry(this.getClass().getSimpleName(),
        new Class[] {ConnectException.class, TimeoutException.class, ConnectionShutdownException.class,
            StreamResetException.class, SocketException.class, EOFException.class});
    RetryHelper.registerEventListeners(exponentialRetry);
    return exponentialRetry;
  }
}