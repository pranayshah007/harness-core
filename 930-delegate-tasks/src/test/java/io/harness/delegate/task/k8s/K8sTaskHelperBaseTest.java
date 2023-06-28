/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesAuthType.SERVICE_ACCOUNT;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesAuthType.USER_PASSWORD;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesConnectorTestHelper.inClusterDelegateK8sConfig;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesConnectorTestHelper.manualK8sConfig;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType.MANUAL_CREDENTIALS;
import static io.harness.delegate.k8s.K8sTestConstants.DAEMON_SET_YAML;
import static io.harness.delegate.k8s.K8sTestConstants.DEPLOYMENT_YAML;
import static io.harness.delegate.k8s.K8sTestConstants.STATEFUL_SET_YAML;
import static io.harness.delegate.k8s.K8sTestHelper.CONFIG_MAP;
import static io.harness.delegate.k8s.K8sTestHelper.DEPLOYMENT;
import static io.harness.delegate.k8s.K8sTestHelper.DEPLOYMENT_CONFIG;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.helm.HelmConstants.HELM_RELEASE_LABEL;
import static io.harness.helm.HelmSubCommandType.TEMPLATE;
import static io.harness.k8s.K8sConstants.RELEASE_NAME_CONFLICTS_WITH_SECRETS_OR_CONFIG_MAPS;
import static io.harness.k8s.K8sConstants.SKIP_FILE_FOR_DEPLOY_PLACEHOLDER_TEXT;
import static io.harness.k8s.KubernetesConvention.ReleaseHistoryKeyName;
import static io.harness.k8s.manifest.ManifestHelper.processYaml;
import static io.harness.k8s.manifest.ManifestHelper.values_filename;
import static io.harness.k8s.model.K8sExpressions.canaryDestinationExpression;
import static io.harness.k8s.model.K8sExpressions.stableDestinationExpression;
import static io.harness.k8s.model.Kind.ConfigMap;
import static io.harness.k8s.model.Kind.Deployment;
import static io.harness.k8s.model.Kind.DeploymentConfig;
import static io.harness.k8s.model.Kind.Job;
import static io.harness.k8s.model.Kind.Namespace;
import static io.harness.k8s.model.Kind.Secret;
import static io.harness.k8s.model.Kind.Service;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;
import static io.harness.rule.OwnerRule.ABHINAV2;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.BOGDAN;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.PRATYUSH;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.TARUN_UBA;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.UTSAV;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.YOGESH;
import static io.harness.state.StateConstants.DEFAULT_STEADY_STATE_TIMEOUT;

import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static io.fabric8.utils.Files.getFileName;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileData;
import io.harness.beans.version.Version;
import io.harness.category.element.UnitTests;
import io.harness.concurent.HTimeLimiterMocker;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.container.ContainerInfo;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.delegate.beans.storeconfig.CustomRemoteStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.LocalFileStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.clienttools.ClientTool;
import io.harness.delegate.clienttools.InstallUtils;
import io.harness.delegate.k8s.K8sTestHelper;
import io.harness.delegate.k8s.openshift.OpenShiftDelegateService;
import io.harness.delegate.service.ExecutionConfigOverrideFromFileOnDelegate;
import io.harness.delegate.task.git.ScmFetchFilesHelperNG;
import io.harness.delegate.task.helm.HelmCommandFlag;
import io.harness.delegate.task.helm.HelmTaskHelperBase;
import io.harness.delegate.task.k8s.client.K8sApiClient;
import io.harness.delegate.task.k8s.client.K8sCliClient;
import io.harness.delegate.task.k8s.k8sbase.K8sReleaseHandlerFactory;
import io.harness.delegate.task.k8s.k8sbase.KustomizeTaskHelper;
import io.harness.delegate.task.localstore.ManifestFiles;
import io.harness.encryption.SecretRefData;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.KubernetesCliTaskRuntimeException;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.KubernetesYamlException;
import io.harness.exception.UrlNotProvidedException;
import io.harness.exception.UrlNotReachableException;
import io.harness.exception.WingsException;
import io.harness.filesystem.FileIo;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
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
import io.harness.k8s.kubectl.OcClient;
import io.harness.k8s.kubectl.RolloutHistoryCommand;
import io.harness.k8s.kubectl.ScaleCommand;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.HarnessAnnotations;
import io.harness.k8s.model.HarnessLabelValues;
import io.harness.k8s.model.HarnessLabels;
import io.harness.k8s.model.HelmVersion;
import io.harness.k8s.model.IstioDestinationWeight;
import io.harness.k8s.model.K8sContainer;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.K8sRequestHandlerContext;
import io.harness.k8s.model.Kind;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.model.response.CEK8sDelegatePrerequisite;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.K8sLegacyRelease;
import io.harness.k8s.releasehistory.ReleaseHistory;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.manifest.CustomManifestSource;
import io.harness.ng.core.dto.ErrorDetail;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.SshSessionConfig;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import io.fabric8.istio.api.networking.v1alpha3.Destination;
import io.fabric8.istio.api.networking.v1alpha3.DestinationBuilder;
import io.fabric8.istio.api.networking.v1alpha3.HTTPRoute;
import io.fabric8.istio.api.networking.v1alpha3.HTTPRouteBuilder;
import io.fabric8.istio.api.networking.v1alpha3.HTTPRouteDestination;
import io.fabric8.istio.api.networking.v1alpha3.HTTPRouteDestinationBuilder;
import io.fabric8.istio.api.networking.v1alpha3.PortSelectorBuilder;
import io.fabric8.istio.api.networking.v1alpha3.Subset;
import io.fabric8.istio.api.networking.v1alpha3.TCPRoute;
import io.fabric8.istio.api.networking.v1alpha3.TLSRoute;
import io.fabric8.istio.api.networking.v1alpha3.VirtualService;
import io.fabric8.istio.api.networking.v1alpha3.VirtualServiceBuilder;
import io.fabric8.istio.api.networking.v1alpha3.VirtualServiceSpec;
import io.fabric8.istio.api.networking.v1alpha3.VirtualServiceSpecBuilder;
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodStatusBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ParameterNamespaceListVisitFromServerGetDeleteRecreateWaitApplicable;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1ContainerStatusBuilder;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentSpec;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1LoadBalancerIngress;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodBuilder;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1PodStatusBuilder;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceBuilder;
import io.kubernetes.client.openapi.models.V1ServicePortBuilder;
import io.kubernetes.client.openapi.models.V1TokenReviewStatus;
import io.kubernetes.client.openapi.models.V1TokenReviewStatusBuilder;
import io.kubernetes.client.util.Yaml;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import junitparams.JUnitParamsRunner;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import org.zeroturnaround.exec.ProcessOutput;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.exec.stream.LogOutputStream;

@OwnedBy(CDP)
@RunWith(JUnitParamsRunner.class)
@Slf4j
public class K8sTaskHelperBaseTest extends CategoryTest {
  private static final KubernetesConfig KUBERNETES_CONFIG = KubernetesConfig.builder().build();
  private static final String DEFAULT = "default";
  private static final HelmCommandFlag TEST_HELM_COMMAND =
      HelmCommandFlag.builder().valueMap(ImmutableMap.of(TEMPLATE, "--debug")).build();
  private static final HelmCommandFlag HELM_DEPENDENCY_UPDATE =
      HelmCommandFlag.builder().valueMap(ImmutableMap.of(TEMPLATE, "--dependency-update")).build();
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private KubernetesContainerService mockKubernetesContainerService;
  @Mock private TimeLimiter mockTimeLimiter;
  @Mock private LogCallback executionLogCallback;
  @Mock private NGGitService ngGitService;
  @Mock private GitDecryptionHelper gitDecryptionHelper;
  @Mock private KustomizeTaskHelper kustomizeTaskHelper;
  @Mock private HelmTaskHelperBase helmTaskHelperBase;
  @Mock private OpenShiftDelegateService openShiftDelegateService;
  @Mock private K8sYamlToDelegateDTOMapper mockK8sYamlToDelegateDTOMapper;
  @Mock private SecretDecryptionService mockSecretDecryptionService;
  @Mock private ExecutionConfigOverrideFromFileOnDelegate delegateLocalConfigService;
  @Mock private KubernetesHelperService kubernetesHelperService;
  @Mock private ScmFetchFilesHelperNG scmFetchFilesHelper;
  @Mock private NGErrorHelper ngErrorHelper;
  @Mock private K8sApiClient k8sApiClient;
  @Mock private K8sCliClient k8sCliClient;
  @Mock private K8sReleaseHandlerFactory releaseHandlerFactory;

  @Inject @InjectMocks private K8sTaskHelperBase k8sTaskHelperBase;
  @Spy @InjectMocks private K8sTaskHelperBase spyK8sTaskHelperBase;

  private final String flagValue = "--flag-test-1";
  private final HelmCommandFlag commandFlag =
      HelmCommandFlag.builder().valueMap(ImmutableMap.of(TEMPLATE, flagValue)).build();

  long LONG_TIMEOUT_INTERVAL = 60 * 1000L;
  private static final String validDeploymentWorkload =
      "namespace/Deployment/name,namespace/ConfigMap/name,namespace/ConfigMap/name2,namespace/Secret/name";
  private static final String validStatefulSetWorkload =
      "namespace/StatefulSet/name,namespace/ConfigMap/name,namespace/ConfigMap/name2,namespace/Secret/name";
  private static final String validDeploymentConfigWorkload =
      "namespace/DeploymentConfig/name,namespace/ConfigMap/name,namespace/ConfigMap/name2,namespace/Secret/name";
  private static final String invalidDeploymentWorkload =
      "namespace/Deployment/name,namespace/Deployment/name2,namespace/ConfigMap/name,namespace/ConfigMap/name2,namespace/Secret/name";
  private static final String invalidWorkload =
      "namespace/ConfigMap/name,namespace/ConfigMap/name2,namespace/Secret/name";

  @Before
  public void setup() throws Exception {
    HTimeLimiterMocker.mockCallInterruptible(mockTimeLimiter)
        .thenAnswer(invocation -> invocation.getArgument(0, Callable.class).call());
    doReturn(-1).when(helmTaskHelperBase).skipDefaultHelmValuesYaml(anyString(), any(), anyBoolean(), any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetTargetInstancesForCanary() {
    doNothing().when(executionLogCallback).saveExecutionLog(anyString());
    assertThat(k8sTaskHelperBase.getTargetInstancesForCanary(50, 4, executionLogCallback)).isEqualTo(2);
    assertThat(k8sTaskHelperBase.getTargetInstancesForCanary(5, 2, executionLogCallback)).isEqualTo(1);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetResourcesInTableFormat() {
    String expectedResourcesInTableFormat = "\n"
        + "\u001B[1;97m\u001B[40mKind                Name                                    Versioned #==#\n"
        + "\u001B[0;37m\u001B[40mDeployment          deployment                              false     #==#\n"
        + "\u001B[0;37m\u001B[40mStatefulSet         statefulSet                             false     #==#\n"
        + "\u001B[0;37m\u001B[40mDaemonSet           daemonSet                               false     #==#\n";
    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    kubernetesResources.addAll(processYaml(DEPLOYMENT_YAML));
    kubernetesResources.addAll(processYaml(STATEFUL_SET_YAML));
    kubernetesResources.addAll(processYaml(DAEMON_SET_YAML));

    String resourcesInTableFormat = k8sTaskHelperBase.getResourcesInTableFormat(kubernetesResources);

    assertThat(resourcesInTableFormat).isEqualTo(expectedResourcesInTableFormat);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFetchAllResourcesForRelease() throws Exception {
    String releaseName = "releaseName";
    doNothing().when(executionLogCallback).saveExecutionLog(anyString());

    V1ConfigMap configMap = new V1ConfigMap();
    configMap.setKind(ConfigMap.name());

    Map<String, String> data = new HashMap<>();
    configMap.setData(data);
    doReturn(configMap).when(mockKubernetesContainerService).getConfigMap(any(), anyString());

    // Empty release history
    List<KubernetesResourceId> kubernetesResourceIds = k8sTaskHelperBase.fetchAllResourcesForRelease(
        releaseName, KubernetesConfig.builder().build(), executionLogCallback);
    assertThat(kubernetesResourceIds).isEmpty();

    data.put(ReleaseHistoryKeyName, null);
    kubernetesResourceIds = k8sTaskHelperBase.fetchAllResourcesForRelease(
        releaseName, KubernetesConfig.builder().build(), executionLogCallback);
    assertThat(kubernetesResourceIds).isEmpty();

    data.put(ReleaseHistoryKeyName, "");
    kubernetesResourceIds = k8sTaskHelperBase.fetchAllResourcesForRelease(
        releaseName, KubernetesConfig.builder().build(), executionLogCallback);
    assertThat(kubernetesResourceIds).isEmpty();

    List<KubernetesResourceId> kubernetesResourceIdList = getKubernetesResourceIdList("1");
    ReleaseHistory releaseHistory = ReleaseHistory.createNew();
    releaseHistory.setReleases(asList(
        K8sLegacyRelease.builder().status(IK8sRelease.Status.Succeeded).resources(kubernetesResourceIdList).build()));

    String releaseHistoryString = releaseHistory.getAsYaml();
    doReturn(releaseHistoryString)
        .when(mockKubernetesContainerService)
        .fetchReleaseHistoryValue(any(V1ConfigMap.class));
    data.put(ReleaseHistoryKeyName, releaseHistoryString);
    kubernetesResourceIds = k8sTaskHelperBase.fetchAllResourcesForRelease(
        releaseName, KubernetesConfig.builder().namespace("default").build(), executionLogCallback);

    assertThat(kubernetesResourceIds.size()).isEqualTo(5);
    Set<String> resourceIdentifiers = kubernetesResourceIds.stream()
                                          .map(resourceId
                                              -> new StringBuilder(resourceId.getNamespace())
                                                     .append('/')
                                                     .append(resourceId.getKind())
                                                     .append('/')
                                                     .append(resourceId.getName())
                                                     .toString())
                                          .collect(Collectors.toSet());

    assertThat(resourceIdentifiers.containsAll(asList("default/Namespace/n1", "default/Deployment/d1",
                   "default/ConfigMap/c1", "default/ConfigMap/releaseName", "default/Service/s1")))
        .isTrue();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchAllResourcesForReleaseWhenMissingConfigMap() throws Exception {
    KubernetesConfig config = KubernetesConfig.builder().build();

    doReturn(null).when(mockKubernetesContainerService).getConfigMap(config, "releaseName");
    List<KubernetesResourceId> kubernetesResourceIds =
        k8sTaskHelperBase.fetchAllResourcesForRelease("releaseName", config, executionLogCallback);
    assertThat(kubernetesResourceIds).isEmpty();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testFetchAllResourcesForReleaseWhenMissingSecretAndConfigMap() throws Exception {
    KubernetesConfig config = KubernetesConfig.builder().build();

    doReturn(null).when(mockKubernetesContainerService).getConfigMap(config, "releaseName");
    doReturn(null).when(mockKubernetesContainerService).getSecret(config, "releaseName");
    List<KubernetesResourceId> kubernetesResourceIds =
        k8sTaskHelperBase.fetchAllResourcesForRelease("releaseName", config, executionLogCallback);
    assertThat(kubernetesResourceIds).isEmpty();
  }

  // Fetch release history from secret first
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void fetchAllResourcesSecretConfigMapPreference() throws IOException {
    String releaseName = "releaseName";

    final V1Secret secret = new V1Secret();
    secret.setKind("secret");
    secret.setData(new HashMap<>());

    final V1ConfigMap configMap = new V1ConfigMap();
    configMap.setKind("configMap");
    configMap.setData(new HashMap<>());

    doReturn(secret).when(mockKubernetesContainerService).getSecret(any(), eq(releaseName));
    doReturn(configMap).when(mockKubernetesContainerService).getConfigMap(any(), eq(releaseName));

    ReleaseHistory releaseHistorySecret = ReleaseHistory.createNew();
    releaseHistorySecret.setReleases(asList(K8sLegacyRelease.builder()
                                                .status(IK8sRelease.Status.Succeeded)
                                                .resources(getKubernetesResourceIdList("-from-secret"))
                                                .build()));

    String releaseHistoryString = releaseHistorySecret.getAsYaml();
    doReturn(releaseHistoryString).when(mockKubernetesContainerService).fetchReleaseHistoryValue(any(V1Secret.class));
    secret.getData().put(ReleaseHistoryKeyName, releaseHistoryString.getBytes());

    ReleaseHistory releaseHistoryConfigMap = ReleaseHistory.createNew();
    releaseHistoryConfigMap.setReleases(asList(K8sLegacyRelease.builder()
                                                   .status(IK8sRelease.Status.Succeeded)
                                                   .resources(getKubernetesResourceIdList("-from-cm"))
                                                   .build()));

    configMap.getData().put(ReleaseHistoryKeyName, releaseHistoryConfigMap.getAsYaml());

    final List<KubernetesResourceId> kubernetesResourceIds = k8sTaskHelperBase.fetchAllResourcesForRelease(
        releaseName, KubernetesConfig.builder().namespace("default").build(), executionLogCallback);

    assertThat(kubernetesResourceIds.size()).isEqualTo(6);
    Set<String> resourceIdentifiers = kubernetesResourceIds.stream()
                                          .map(resourceId
                                              -> new StringBuilder(resourceId.getNamespace())
                                                     .append('/')
                                                     .append(resourceId.getKind())
                                                     .append('/')
                                                     .append(resourceId.getName())
                                                     .toString())
                                          .collect(Collectors.toSet());

    assertThat(resourceIdentifiers)
        .containsExactlyInAnyOrder("default/Namespace/n-from-secret", "default/Deployment/d-from-secret",
            "default/ConfigMap/c-from-secret", "default/secret/releaseName", "default/Service/s-from-secret",
            "default/configMap/releaseName");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testFetchAllResourcesForReleaseWithSecret() throws Exception {
    String releaseName = "releaseName";

    doNothing().when(executionLogCallback).saveExecutionLog(anyString());

    V1Secret secret = new V1Secret();
    secret.setKind("secret");

    secret.setData(new HashMap<>());
    doReturn(secret).when(mockKubernetesContainerService).getSecret(any(), eq(releaseName));
    doReturn(null).when(mockKubernetesContainerService).getConfigMap(any(), eq(releaseName));

    // Empty release history
    List<KubernetesResourceId> kubernetesResourceIds = k8sTaskHelperBase.fetchAllResourcesForRelease(
        releaseName, KubernetesConfig.builder().build(), executionLogCallback);
    assertThat(kubernetesResourceIds).isEmpty();

    secret.getData().put(ReleaseHistoryKeyName, null);
    kubernetesResourceIds = k8sTaskHelperBase.fetchAllResourcesForRelease(
        releaseName, KubernetesConfig.builder().build(), executionLogCallback);
    assertThat(kubernetesResourceIds).isEmpty();

    secret.getData().put(ReleaseHistoryKeyName, "".getBytes());
    kubernetesResourceIds = k8sTaskHelperBase.fetchAllResourcesForRelease(
        releaseName, KubernetesConfig.builder().build(), executionLogCallback);
    assertThat(kubernetesResourceIds).isEmpty();

    List<KubernetesResourceId> kubernetesResourceIdList = getKubernetesResourceIdList("1");
    ReleaseHistory releaseHistory = ReleaseHistory.createNew();
    releaseHistory.setReleases(asList(
        K8sLegacyRelease.builder().status(IK8sRelease.Status.Succeeded).resources(kubernetesResourceIdList).build()));

    String releaseHistoryString = releaseHistory.getAsYaml();
    doReturn(releaseHistoryString).when(mockKubernetesContainerService).fetchReleaseHistoryValue(any(V1Secret.class));
    secret.getData().put(ReleaseHistoryKeyName, releaseHistoryString.getBytes());
    kubernetesResourceIds = k8sTaskHelperBase.fetchAllResourcesForRelease(
        releaseName, KubernetesConfig.builder().namespace("default").build(), executionLogCallback);

    assertThat(kubernetesResourceIds.size()).isEqualTo(5);
    Set<String> resourceIdentifiers = kubernetesResourceIds.stream()
                                          .map(resourceId
                                              -> new StringBuilder(resourceId.getNamespace())
                                                     .append('/')
                                                     .append(resourceId.getKind())
                                                     .append('/')
                                                     .append(resourceId.getName())
                                                     .toString())
                                          .collect(Collectors.toSet());

    assertThat(resourceIdentifiers)
        .containsExactlyInAnyOrder("default/Namespace/n1", "default/Deployment/d1", "default/ConfigMap/c1",
            "default/secret/releaseName", "default/Service/s1");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetLatestRevision() throws Exception {
    URL url = this.getClass().getResource("/k8s/deployment-config.yaml");
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    KubernetesResource resource = processYaml(fileContents).get(0);

    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder().build();
    Kubectl client = Kubectl.client("kubectl", "config-path");

    String output = "deploymentconfigs \"test-dc\"\n"
        + "REVISION\tSTATUS\t\tCAUSE\n"
        + "35\t\tComplete\tconfig change\n"
        + "36\t\tComplete\tconfig change";

    ProcessResult processResult = new ProcessResult(0, new ProcessOutput(output.getBytes()));
    doReturn(processResult)
        .when(spyK8sTaskHelperBase)
        .executeCommandUsingUtils(nullable(K8sDelegateTaskParams.class), any(), any(), anyString(), any());

    MockedStatic<InstallUtils> mock = mockStatic(InstallUtils.class);
    PowerMockito.when(InstallUtils.getLatestVersionPath(ClientTool.OC)).thenReturn("oc");
    String latestRevision =
        spyK8sTaskHelperBase.getLatestRevision(client, resource.getResourceId(), k8sDelegateTaskParams);
    assertThat(latestRevision).isEqualTo("36");

    processResult = new ProcessResult(1, new ProcessOutput("".getBytes()));
    doReturn(processResult)
        .when(spyK8sTaskHelperBase)
        .executeCommandUsingUtils(nullable(K8sDelegateTaskParams.class), any(), any(), anyString(), any());

    latestRevision = spyK8sTaskHelperBase.getLatestRevision(client, resource.getResourceId(), k8sDelegateTaskParams);
    mock.close();
    assertThat(latestRevision).isEqualTo("");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDryRunForOpenshiftResources() throws Exception {
    ProcessResponse response =
        ProcessResponse.builder().processResult(new ProcessResult(0, new ProcessOutput("abc".getBytes()))).build();
    doReturn(response).when(spyK8sTaskHelperBase).runK8sExecutable(any(), any(), any());

    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory(workingDirectory)
                                                      .ocPath("oc")
                                                      .kubectlPath("kubectl")
                                                      .kubeconfigPath("config-path")
                                                      .build();
    Kubectl client = Kubectl.client("kubectl", "config-path");

    spyK8sTaskHelperBase.dryRunManifests(client, emptyList(), k8sDelegateTaskParams, executionLogCallback, false);

    ArgumentCaptor<ApplyCommand> captor = ArgumentCaptor.forClass(ApplyCommand.class);
    verify(spyK8sTaskHelperBase, times(1)).runK8sExecutable(any(), any(), captor.capture());
    assertThat(captor.getValue().command())
        .isEqualTo("kubectl --kubeconfig=config-path apply --filename=manifests-dry-run.yaml --dry-run");
    reset(spyK8sTaskHelperBase);

    doReturn(response).when(spyK8sTaskHelperBase).runK8sExecutable(any(), any(), any());
    MockedStatic<InstallUtils> mock = mockStatic(InstallUtils.class);
    PowerMockito.when(InstallUtils.getLatestVersionPath(ClientTool.OC)).thenReturn("oc");
    Kubectl ocClient = OcClient.client("oc", "config-path");
    ocClient.setVersion(Version.parse("4.2"));
    MockedStatic<KubectlFactory> mockFactory = mockStatic(KubectlFactory.class);
    PowerMockito.when(KubectlFactory.getOpenShiftClient("oc", "config-path", ".")).thenReturn(ocClient);
    spyK8sTaskHelperBase.dryRunManifests(ocClient,
        asList(KubernetesResource.builder()
                   .spec("")
                   .resourceId(KubernetesResourceId.builder().kind("Route").build())
                   .build()),
        k8sDelegateTaskParams, executionLogCallback, false);
    mock.close();
    mockFactory.close();
    verify(spyK8sTaskHelperBase, times(1)).runK8sExecutable(any(), any(), captor.capture());
    assertThat(captor.getValue().command())
        .isEqualTo("oc --kubeconfig=config-path apply --filename=manifests-dry-run.yaml --dry-run");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDryRunManifestIsErrorFrameworkEnabled() throws Exception {
    ProcessResponse response =
        ProcessResponse.builder()
            .processResult(new ProcessResult(1, new ProcessOutput("Something went wrong".getBytes())))
            .build();
    doReturn(response).when(spyK8sTaskHelperBase).runK8sExecutable(any(), any(), any());
    ProcessResult result = new ProcessResult(0, null);
    doReturn(result).when(spyK8sTaskHelperBase).runK8sExecutableSilent(any(), any());
    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory(workingDirectory)
                                                      .ocPath("oc")
                                                      .kubectlPath("kubectl")
                                                      .kubeconfigPath("config-path")
                                                      .build();
    Kubectl client = KubectlFactory.getKubectlClient("kubectl", "config-path", ".");

    assertThatThrownBy(()
                           -> spyK8sTaskHelperBase.dryRunManifests(
                               client, emptyList(), k8sDelegateTaskParams, executionLogCallback, true))
        .matches(throwable -> {
          KubernetesCliTaskRuntimeException taskException = (KubernetesCliTaskRuntimeException) throwable;
          assertThat(taskException.getProcessResponse().getProcessResult().outputUTF8())
              .contains("Something went wrong");
          assertThat(taskException.getProcessResponse().getProcessResult().getExitValue()).isEqualTo(1);
          return true;
        });
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testDryRunManifestIsErrorFrameworkEnabledWithResources() throws Exception {
    ProcessResponse response =
        ProcessResponse.builder()
            .processResult(new ProcessResult(1, new ProcessOutput("Something went wrong".getBytes())))
            .build();
    Kubectl client = KubectlFactory.getKubectlClient("kubectl", "config-path", ".");
    doReturn(response).when(spyK8sTaskHelperBase).runK8sExecutable(any(), any(), any());
    ProcessResult result = new ProcessResult(0, null);
    doReturn(result).when(spyK8sTaskHelperBase).runK8sExecutableSilent(any(), any());
    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory(workingDirectory)
                                                      .ocPath("oc")
                                                      .kubectlPath("kubectl")
                                                      .kubeconfigPath("config-path")
                                                      .build();

    assertThatThrownBy(
        ()
            -> spyK8sTaskHelperBase.dryRunManifests(client,
                Collections.singletonList(
                    KubernetesResource.builder()
                        .spec("Sample resouece")
                        .resourceId(new KubernetesResourceId().builder().kind("Deployment").name("test-svc").build())
                        .build()),
                k8sDelegateTaskParams, executionLogCallback, true))
        .matches(throwable -> {
          KubernetesCliTaskRuntimeException taskException = (KubernetesCliTaskRuntimeException) throwable;
          assertThat(taskException.getProcessResponse().getProcessResult().outputUTF8())
              .contains("Something went wrong");
          assertThat(taskException.getResourcesNotApplied().contains("deployment/test-svc"));
          assertThat(taskException.getProcessResponse().getProcessResult().getExitValue()).isEqualTo(1);
          return true;
        });
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testDryRunManifestIsErrorFrameworkEnabledWithEmptyOutput() throws Exception {
    ProcessResponse response = ProcessResponse.builder().processResult(new ProcessResult(1, null)).build();
    Kubectl client = KubectlFactory.getKubectlClient("kubectl", "config-path", ".");
    doReturn(response).when(spyK8sTaskHelperBase).runK8sExecutable(any(), any(), any());
    ProcessResult result = new ProcessResult(0, null);
    doReturn(result).when(spyK8sTaskHelperBase).runK8sExecutableSilent(any(), any());
    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory(workingDirectory)
                                                      .ocPath("oc")
                                                      .kubectlPath("kubectl")
                                                      .kubeconfigPath("config-path")
                                                      .build();

    assertThatThrownBy(
        ()
            -> spyK8sTaskHelperBase.dryRunManifests(client,
                Collections.singletonList(
                    KubernetesResource.builder()
                        .spec("Sample resouece")
                        .resourceId(new KubernetesResourceId().builder().kind("Deployment").name("test-svc").build())
                        .build()),
                k8sDelegateTaskParams, executionLogCallback, true))
        .matches(throwable -> {
          KubernetesCliTaskRuntimeException taskException = (KubernetesCliTaskRuntimeException) throwable;
          assertThat(taskException.getResourcesNotApplied().contains("deployment/test-svc"));
          assertThat(taskException.getProcessResponse().getProcessResult().getExitValue()).isEqualTo(1);
          assertThat(taskException.getKubectlVersion()).contains("");
          return true;
        });
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testDryRunForOpenshiftResourcesKubernetesVersion() {
    final String workingDirectory = ".";
    Kubectl client = KubectlFactory.getOpenShiftClient("kubectl", "config-path", workingDirectory);

    String result2 = client.getVersion().toString();
    assertThat(result2).isEqualTo("4.2.16");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testApplyForOpenshiftResources() throws Exception {
    ProcessResponse response =
        ProcessResponse.builder().processResult(new ProcessResult(0, new ProcessOutput("abc".getBytes()))).build();
    doReturn(response).when(spyK8sTaskHelperBase).runK8sExecutable(any(), any(), any(AbstractExecutable.class));

    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory(workingDirectory)
                                                      .kubectlPath("kubectl")
                                                      .ocPath("oc")
                                                      .kubeconfigPath("config-path")
                                                      .build();
    Kubectl client = KubectlFactory.getKubectlClient("kubectl", "config-path", workingDirectory);
    client.setVersion(Version.parse("1.21"));
    spyK8sTaskHelperBase.applyManifests(client, emptyList(), k8sDelegateTaskParams, executionLogCallback, true, null);

    ArgumentCaptor<ApplyCommand> captor = ArgumentCaptor.forClass(ApplyCommand.class);
    verify(spyK8sTaskHelperBase, times(1)).runK8sExecutable(any(), any(), captor.capture());
    assertThat(captor.getValue().command())
        .isEqualTo("kubectl --kubeconfig=config-path apply --filename=manifests.yaml --record");
    reset(spyK8sTaskHelperBase);

    doReturn(response).when(spyK8sTaskHelperBase).runK8sExecutable(any(), any(), any(AbstractExecutable.class));
    MockedStatic<InstallUtils> mock = mockStatic(InstallUtils.class);
    PowerMockito.when(InstallUtils.getLatestVersionPath(ClientTool.OC)).thenReturn("oc");
    Kubectl ocClient = OcClient.client("oc", "config-path");
    ocClient.setVersion(Version.parse("4.7"));
    MockedStatic<KubectlFactory> mockFactory = mockStatic(KubectlFactory.class);
    PowerMockito.when(KubectlFactory.getOpenShiftClient("oc", "config-path", ".")).thenReturn(ocClient);
    spyK8sTaskHelperBase.applyManifests(ocClient,
        asList(KubernetesResource.builder()
                   .spec("")
                   .resourceId(KubernetesResourceId.builder().kind("Route").build())
                   .build()),
        k8sDelegateTaskParams, executionLogCallback, true, null);
    mock.close();
    mockFactory.close();
    verify(spyK8sTaskHelperBase, times(1)).runK8sExecutable(any(), any(), captor.capture());
    assertThat(captor.getValue().command())
        .isEqualTo("oc --kubeconfig=config-path apply --filename=manifests.yaml --record");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testApplyIsErrorFrameworkEnabled() throws Exception {
    ProcessResponse response =
        ProcessResponse.builder()
            .processResult(new ProcessResult(1, new ProcessOutput("Something went wrong".getBytes())))
            .build();
    doReturn(response).when(spyK8sTaskHelperBase).runK8sExecutable(any(), any(), any(AbstractExecutable.class));

    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory(workingDirectory)
                                                      .kubectlPath("kubectl")
                                                      .ocPath("oc")
                                                      .kubeconfigPath("config-path")
                                                      .build();
    Kubectl client = Kubectl.client("kubectl", "config-path");

    assertThatThrownBy(()
                           -> spyK8sTaskHelperBase.applyManifests(client,
                               singletonList(KubernetesResource.builder()
                                                 .spec("")
                                                 .resourceId(KubernetesResourceId.builder().kind("Deployment").build())
                                                 .build()),
                               k8sDelegateTaskParams, executionLogCallback, true, true, null))
        .matches(throwable -> {
          KubernetesCliTaskRuntimeException taskException = (KubernetesCliTaskRuntimeException) throwable;
          assertThat(taskException.getProcessResponse().getProcessResult().outputUTF8())
              .contains("Something went wrong");
          assertThat(taskException.getProcessResponse().getProcessResult().getExitValue()).isEqualTo(1);
          return true;
        }, "expected exception message");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testDeleteForOpenshiftResources() throws Exception {
    ProcessResponse response =
        ProcessResponse.builder().processResult(new ProcessResult(0, new ProcessOutput("abc".getBytes()))).build();
    doReturn(response).when(spyK8sTaskHelperBase).runK8sExecutable(any(), any(), any(AbstractExecutable.class));

    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory(workingDirectory)
                                                      .kubectlPath("kubectl")
                                                      .ocPath("oc")
                                                      .kubeconfigPath("config-path")
                                                      .build();
    Kubectl client = Kubectl.client("kubectl", "config-path");

    spyK8sTaskHelperBase.deleteManifests(client, emptyList(), k8sDelegateTaskParams, executionLogCallback);

    ArgumentCaptor<DeleteCommand> captor = ArgumentCaptor.forClass(DeleteCommand.class);
    verify(spyK8sTaskHelperBase, times(1)).runK8sExecutable(any(), any(), captor.capture());
    assertThat(captor.getValue().command())
        .isEqualTo("kubectl --kubeconfig=config-path delete --filename=manifests.yaml");
    reset(spyK8sTaskHelperBase);

    doReturn(response).when(spyK8sTaskHelperBase).runK8sExecutable(any(), any(), any(AbstractExecutable.class));
    MockedStatic<InstallUtils> mock = mockStatic(InstallUtils.class);
    PowerMockito.when(InstallUtils.getLatestVersionPath(ClientTool.OC)).thenReturn("oc");
    spyK8sTaskHelperBase.deleteManifests(client,
        asList(KubernetesResource.builder()
                   .spec("")
                   .resourceId(KubernetesResourceId.builder().kind("Route").build())
                   .build()),
        k8sDelegateTaskParams, executionLogCallback);
    mock.close();
    verify(spyK8sTaskHelperBase, times(1)).runK8sExecutable(any(), any(), captor.capture());
    assertThat(captor.getValue().command()).isEqualTo("oc --kubeconfig=config-path delete --filename=manifests.yaml");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void doStatusCheckForJob() throws Exception {
    String RANDOM = "RANDOM";
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder().workingDirectory(RANDOM).build();
    GetJobCommand jobStatusCommand = spy(new GetJobCommand(null, null, null));
    doReturn(null).when(jobStatusCommand).execute(RANDOM, null, null, false, Collections.emptyMap());

    shouldFailWhenCompletedJobCommandFailed(RANDOM, k8sDelegateTaskParams, jobStatusCommand, false);
    shouldFailWhenCompletedTimeCommandFailed(RANDOM, k8sDelegateTaskParams, jobStatusCommand, false);
    shouldReturnTrueWhenCompletedTimeReached(RANDOM, k8sDelegateTaskParams, jobStatusCommand, false);
    shouldFailWhenFailedJobCommandFailed(RANDOM, k8sDelegateTaskParams, jobStatusCommand, false);
    shouldFailWhenJobStatusIsFailed(RANDOM, k8sDelegateTaskParams, jobStatusCommand, false);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void doStatusCheckForJobIsErrorFrameworkEnabled() throws Exception {
    String RANDOM = "RANDOM";
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder().workingDirectory(RANDOM).build();
    GetJobCommand jobStatusCommand = spy(new GetJobCommand(null, null, null));
    doReturn(null).when(jobStatusCommand).execute(RANDOM, null, null, false, Collections.emptyMap());

    shouldFailWhenCompletedJobCommandFailed(RANDOM, k8sDelegateTaskParams, jobStatusCommand, true);
    shouldFailWhenCompletedTimeCommandFailed(RANDOM, k8sDelegateTaskParams, jobStatusCommand, true);
    shouldReturnTrueWhenCompletedTimeReached(RANDOM, k8sDelegateTaskParams, jobStatusCommand, true);
    shouldFailWhenFailedJobCommandFailed(RANDOM, k8sDelegateTaskParams, jobStatusCommand, true);
    shouldFailWhenJobStatusIsFailed(RANDOM, k8sDelegateTaskParams, jobStatusCommand, true);
  }

  private void shouldFailWhenFailedJobCommandFailed(String RANDOM, K8sDelegateTaskParams k8sDelegateTaskParams,
      GetJobCommand jobStatusCommand, boolean isErrorFrameworkEnabled) throws Exception {
    GetJobCommand jobCompletionStatus = spy(new GetJobCommand(null, null, null));
    ProcessResult jobStatusResult = new ProcessResult(0, new ProcessOutput("".getBytes()));
    GetJobCommand jobFailedCommand = spy(new GetJobCommand(null, null, null));
    ProcessResult jobFailedResult = new ProcessResult(1, new ProcessOutput("Something went wrong".getBytes()));

    doReturn(jobStatusResult).when(jobCompletionStatus).execute(RANDOM, null, null, false, Collections.emptyMap());
    doReturn(jobFailedResult).when(jobFailedCommand).execute(RANDOM, null, null, false, Collections.emptyMap());
    doReturn("kubectl --kubeconfig=file get").when(jobFailedCommand).command();

    if (isErrorFrameworkEnabled) {
      assertThatThrownBy(()
                             -> k8sTaskHelperBase.getJobStatus(k8sDelegateTaskParams, null, null, jobCompletionStatus,
                                 jobFailedCommand, jobStatusCommand, null, true))
          .matches(throwable -> {
            HintException hint = ExceptionUtils.cause(HintException.class, throwable);
            ExplanationException explanation = ExceptionUtils.cause(ExplanationException.class, throwable);
            KubernetesTaskException taskException = ExceptionUtils.cause(KubernetesTaskException.class, throwable);
            assertThat(hint).hasMessageContaining(KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_CLI_FAILED);
            assertThat(explanation).hasMessageContaining("Something went wrong");
            assertThat(taskException).hasMessageContaining(KubernetesExceptionMessages.WAIT_FOR_STEADY_STATE_FAILED);
            return true;
          });
    } else {
      assertThat(k8sTaskHelperBase.getJobStatus(
                     k8sDelegateTaskParams, null, null, jobCompletionStatus, jobFailedCommand, jobStatusCommand, null))
          .isFalse();
    }
  }

  private void shouldFailWhenCompletedTimeCommandFailed(String RANDOM, K8sDelegateTaskParams k8sDelegateTaskParams,
      GetJobCommand jobStatusCommand, boolean isErrorFrameworkEnabled) throws Exception {
    GetJobCommand jobCompletionStatus = spy(new GetJobCommand(null, null, null));
    ProcessResult jobStatusResult = new ProcessResult(0, new ProcessOutput("True".getBytes()));
    GetJobCommand jobCompletionCommand = spy(new GetJobCommand(null, null, null));
    ProcessResult jobCompletionTimeResult = new ProcessResult(1, new ProcessOutput("Something went wrong".getBytes()));

    doReturn(jobStatusResult).when(jobCompletionStatus).execute(RANDOM, null, null, false, Collections.emptyMap());
    doReturn(jobCompletionTimeResult)
        .when(jobCompletionCommand)
        .execute(RANDOM, null, null, false, Collections.emptyMap());
    doReturn("kubectl --kubeconfig=file get").when(jobCompletionCommand).command();

    if (isErrorFrameworkEnabled) {
      assertThatThrownBy(()
                             -> k8sTaskHelperBase.getJobStatus(k8sDelegateTaskParams, null, null, jobCompletionStatus,
                                 null, jobStatusCommand, jobCompletionCommand, true))
          .matches(throwable -> {
            HintException hint = ExceptionUtils.cause(HintException.class, throwable);
            ExplanationException explanation = ExceptionUtils.cause(ExplanationException.class, throwable);
            KubernetesTaskException taskException = ExceptionUtils.cause(KubernetesTaskException.class, throwable);
            assertThat(hint).hasMessageContaining(KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_CLI_FAILED);
            assertThat(explanation).hasMessageContaining("Something went wrong");
            assertThat(taskException).hasMessageContaining(KubernetesExceptionMessages.WAIT_FOR_STEADY_STATE_FAILED);
            return true;
          });
    } else {
      assertThat(k8sTaskHelperBase.getJobStatus(k8sDelegateTaskParams, null, null, jobCompletionStatus, null,
                     jobStatusCommand, jobCompletionCommand))
          .isFalse();
    }
  }

  private void shouldFailWhenJobStatusIsFailed(String RANDOM, K8sDelegateTaskParams k8sDelegateTaskParams,
      GetJobCommand jobStatusCommand, boolean isErrorFrameworkEnabled) throws Exception {
    GetJobCommand jobCompletionStatus = spy(new GetJobCommand(null, null, null));
    ProcessResult jobStatusResult = new ProcessResult(0, new ProcessOutput("".getBytes()));
    GetJobCommand jobFailedCommand = spy(new GetJobCommand(null, null, null));
    ProcessResult jobFailedResult = new ProcessResult(0, new ProcessOutput("True".getBytes()));

    doReturn(jobStatusResult).when(jobCompletionStatus).execute(RANDOM, null, null, false, Collections.emptyMap());
    doReturn(jobFailedResult).when(jobFailedCommand).execute(RANDOM, null, null, false, Collections.emptyMap());

    if (isErrorFrameworkEnabled) {
      assertThatThrownBy(()
                             -> k8sTaskHelperBase.getJobStatus(k8sDelegateTaskParams, null, null, jobCompletionStatus,
                                 jobFailedCommand, jobStatusCommand, null, true))
          .matches(throwable -> {
            HintException hint = ExceptionUtils.cause(HintException.class, throwable);
            ExplanationException explanation = ExceptionUtils.cause(ExplanationException.class, throwable);
            KubernetesTaskException taskException = ExceptionUtils.cause(KubernetesTaskException.class, throwable);
            assertThat(hint).hasMessageContaining(KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_JOB_FAILED);
            assertThat(explanation)
                .hasMessageContaining(KubernetesExceptionExplanation.WAIT_FOR_STEADY_STATE_JOB_FAILED);
            assertThat(taskException).hasMessageContaining(KubernetesExceptionMessages.WAIT_FOR_STEADY_STATE_FAILED);
            return true;
          });
    } else {
      assertThat(k8sTaskHelperBase.getJobStatus(
                     k8sDelegateTaskParams, null, null, jobCompletionStatus, jobFailedCommand, jobStatusCommand, null))
          .isFalse();
    }
  }

  private void shouldReturnTrueWhenCompletedTimeReached(String RANDOM, K8sDelegateTaskParams k8sDelegateTaskParams,
      GetJobCommand jobStatusCommand, boolean isErrorFrameworkEnabled) throws Exception {
    GetJobCommand jobCompletionStatus = spy(new GetJobCommand(null, null, null));
    ProcessResult jobStatusResult = new ProcessResult(0, new ProcessOutput("True".getBytes()));
    GetJobCommand jobCompletionCommand = spy(new GetJobCommand(null, null, null));
    ProcessResult jobCompletionTimeResult = new ProcessResult(0, new ProcessOutput("time".getBytes()));

    doReturn(jobStatusResult).when(jobCompletionStatus).execute(RANDOM, null, null, false, Collections.emptyMap());
    doReturn(jobCompletionTimeResult)
        .when(jobCompletionCommand)
        .execute(RANDOM, null, null, false, Collections.emptyMap());

    assertThat(k8sTaskHelperBase.getJobStatus(k8sDelegateTaskParams, null, null, jobCompletionStatus, null,
                   jobStatusCommand, jobCompletionCommand, isErrorFrameworkEnabled))
        .isTrue();
  }

  private void shouldFailWhenCompletedJobCommandFailed(String RANDOM, K8sDelegateTaskParams k8sDelegateTaskParams,
      GetJobCommand jobStatusCommand, boolean isErrorFrameworkEnabled) throws Exception {
    GetJobCommand jobCompletionStatus = spy(new GetJobCommand(null, null, null));
    ProcessResult jobStatusResult = new ProcessResult(1, new ProcessOutput("Something went wrong".getBytes()));

    doReturn(jobStatusResult).when(jobCompletionStatus).execute(RANDOM, null, null, false, Collections.emptyMap());
    doReturn("kubectl --kubeconfig=file get").when(jobCompletionStatus).command();

    if (isErrorFrameworkEnabled) {
      assertThatThrownBy(()
                             -> k8sTaskHelperBase.getJobStatus(k8sDelegateTaskParams, null, null, jobCompletionStatus,
                                 null, jobStatusCommand, null, true))
          .matches(throwable -> {
            HintException hint = ExceptionUtils.cause(HintException.class, throwable);
            ExplanationException explanation = ExceptionUtils.cause(ExplanationException.class, throwable);
            KubernetesTaskException taskException = ExceptionUtils.cause(KubernetesTaskException.class, throwable);
            assertThat(hint).hasMessageContaining(KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_CLI_FAILED);
            assertThat(explanation).hasMessageContaining("Something went wrong");
            assertThat(taskException).hasMessageContaining(KubernetesExceptionMessages.WAIT_FOR_STEADY_STATE_FAILED);
            return true;
          });
    } else {
      assertThat(k8sTaskHelperBase.getJobStatus(
                     k8sDelegateTaskParams, null, null, jobCompletionStatus, null, jobStatusCommand, null))
          .isFalse();
    }
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void scaleFailure() throws Exception {
    Kubectl kubectl = Kubectl.client("kubectl", "config-path");
    ProcessResponse response =
        ProcessResponse.builder().processResult(new ProcessResult(1, new ProcessOutput("failure".getBytes()))).build();
    doReturn(response).when(spyK8sTaskHelperBase).runK8sExecutable(any(), any(), any());
    final boolean success = spyK8sTaskHelperBase.scale(kubectl, K8sDelegateTaskParams.builder().build(),
        KubernetesResourceId.builder().name("nginx").kind("Deployment").namespace("default").build(), 5,
        executionLogCallback, false);
    assertThat(success).isFalse();
    ArgumentCaptor<ScaleCommand> captor = ArgumentCaptor.forClass(ScaleCommand.class);
    verify(spyK8sTaskHelperBase, times(1)).runK8sExecutable(any(), any(), captor.capture());
    assertThat(captor.getValue().command())
        .isEqualTo("kubectl --kubeconfig=config-path scale Deployment/nginx --namespace=default --replicas=5");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void scaleSuccess() throws Exception {
    Kubectl kubectl = Kubectl.client("kubectl", "config-path");
    ProcessResponse response = ProcessResponse.builder().processResult(new ProcessResult(0, null)).build();
    doReturn(response).when(spyK8sTaskHelperBase).runK8sExecutable(any(), any(), any());
    final boolean success =
        spyK8sTaskHelperBase.scale(kubectl, K8sDelegateTaskParams.builder().workingDirectory(".").build(),
            KubernetesResourceId.builder().name("nginx").kind("Deployment").namespace("default").build(), 5,
            executionLogCallback, true);

    assertThat(success).isTrue();
    ArgumentCaptor<ScaleCommand> captor = ArgumentCaptor.forClass(ScaleCommand.class);
    verify(spyK8sTaskHelperBase, times(1)).runK8sExecutable(any(), any(), captor.capture());
    assertThat(captor.getValue().command())
        .isEqualTo("kubectl --kubeconfig=config-path scale Deployment/nginx --namespace=default --replicas=5");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testValidateExistingResourceIdsFailure() throws Exception {
    Kubectl kubectl = Kubectl.client("kubectl", "config-path");
    ProcessResponse response =
        ProcessResponse.builder().processResult(new ProcessResult(1, new ProcessOutput("failure".getBytes()))).build();
    doReturn(response).when(spyK8sTaskHelperBase).runK8sExecutable(any(), any(), any(), eq(WARN));
    final boolean success = spyK8sTaskHelperBase.checkIfResourceContainsHarnessDirectApplyAnnotation(kubectl,
        K8sDelegateTaskParams.builder().build(),
        KubernetesResourceId.builder().name("nginx").kind("Deployment").namespace("default").build(),
        executionLogCallback);
    assertThat(success).isFalse();
    ArgumentCaptor<GetCommand> captor = ArgumentCaptor.forClass(GetCommand.class);
    verify(spyK8sTaskHelperBase, times(1)).runK8sExecutable(any(), any(), captor.capture(), eq(WARN));
    assertThat(captor.getValue().command())
        .isEqualTo(
            "kubectl --kubeconfig=config-path get Deployment/nginx --namespace=default --output=jsonpath='{.metadata.annotations.harness\\.io/direct-apply}'");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testValidateScaleDownResourcesSuccess() throws Exception {
    Kubectl kubectl = Kubectl.client("kubectl", "config-path");
    ProcessResponse response =
        ProcessResponse.builder()
            .processResult(new ProcessResult(0, new ProcessOutput("false".getBytes(StandardCharsets.UTF_8))))
            .build();
    doReturn(response).when(spyK8sTaskHelperBase).runK8sExecutable(any(), any(), any(), eq(WARN));
    boolean success = spyK8sTaskHelperBase.checkIfResourceContainsHarnessDirectApplyAnnotation(kubectl,
        K8sDelegateTaskParams.builder().build(),
        KubernetesResourceId.builder().name("nginx").kind("Deployment").namespace("default").build(),
        executionLogCallback);
    assertThat(success).isTrue();
    ArgumentCaptor<GetCommand> captor = ArgumentCaptor.forClass(GetCommand.class);
    verify(spyK8sTaskHelperBase, times(1)).runK8sExecutable(any(), any(), captor.capture(), eq(WARN));
    assertThat(captor.getValue().command())
        .isEqualTo(
            "kubectl --kubeconfig=config-path get Deployment/nginx --namespace=default --output=jsonpath='{.metadata.annotations.harness\\.io/direct-apply}'");

    response = ProcessResponse.builder()
                   .processResult(new ProcessResult(0, new ProcessOutput("true".getBytes(StandardCharsets.UTF_8))))
                   .build();
    doReturn(response).when(spyK8sTaskHelperBase).runK8sExecutable(any(), any(), any(), eq(WARN));
    success = spyK8sTaskHelperBase.checkIfResourceContainsHarnessDirectApplyAnnotation(kubectl,
        K8sDelegateTaskParams.builder().build(),
        KubernetesResourceId.builder().name("nginx").kind("Deployment").namespace("default").build(),
        executionLogCallback);
    assertThat(success).isFalse();
    captor = ArgumentCaptor.forClass(GetCommand.class);
    verify(spyK8sTaskHelperBase, times(2)).runK8sExecutable(any(), any(), captor.capture(), eq(WARN));
    assertThat(captor.getValue().command())
        .isEqualTo(
            "kubectl --kubeconfig=config-path get Deployment/nginx --namespace=default --output=jsonpath='{.metadata.annotations.harness\\.io/direct-apply}'");

    response = ProcessResponse.builder()
                   .processResult(new ProcessResult(0, new ProcessOutput("".getBytes(StandardCharsets.UTF_8))))
                   .build();
    doReturn(response).when(spyK8sTaskHelperBase).runK8sExecutable(any(), any(), any(), eq(WARN));
    success = spyK8sTaskHelperBase.checkIfResourceContainsHarnessDirectApplyAnnotation(kubectl,
        K8sDelegateTaskParams.builder().build(),
        KubernetesResourceId.builder().name("nginx").kind("Deployment").namespace("default").build(),
        executionLogCallback);
    assertThat(success).isTrue();
    captor = ArgumentCaptor.forClass(GetCommand.class);
    verify(spyK8sTaskHelperBase, times(3)).runK8sExecutable(any(), any(), captor.capture(), eq(WARN));
    assertThat(captor.getValue().command())
        .isEqualTo(
            "kubectl --kubeconfig=config-path get Deployment/nginx --namespace=default --output=jsonpath='{.metadata.annotations.harness\\.io/direct-apply}'");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testValidateExistingResourceIdsSuccess() throws Exception {
    Kubectl kubectl = Kubectl.client("kubectl", "config-path");
    ProcessResponse response =
        ProcessResponse.builder().processResult(new ProcessResult(0, new ProcessOutput("".getBytes()))).build();
    doReturn(response).when(spyK8sTaskHelperBase).runK8sExecutable(any(), any(), any(), eq(WARN));
    final boolean success = spyK8sTaskHelperBase.checkIfResourceContainsHarnessDirectApplyAnnotation(kubectl,
        K8sDelegateTaskParams.builder().build(),
        KubernetesResourceId.builder().name("nginx").kind("Deployment").namespace("default").build(),
        executionLogCallback);
    assertThat(success).isTrue();
    ArgumentCaptor<GetCommand> captor = ArgumentCaptor.forClass(GetCommand.class);
    verify(spyK8sTaskHelperBase, times(1)).runK8sExecutable(any(), any(), captor.capture(), eq(WARN));
    assertThat(captor.getValue().command())
        .isEqualTo(
            "kubectl --kubeconfig=config-path get Deployment/nginx --namespace=default --output=jsonpath='{.metadata.annotations.harness\\.io/direct-apply}'");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void cleanUp() throws Exception {
    cleanUpIfOnly1FailedRelease();
    cleanUpIfMultipleFailedReleases();
    cleanUpAllOlderReleases();
  }

  private void cleanUpAllOlderReleases() throws Exception {
    final ReleaseHistory releaseHistory = ReleaseHistory.createNew();
    releaseHistory.getReleases().add(K8sTestHelper.buildRelease(IK8sRelease.Status.Succeeded, 3));
    releaseHistory.getReleases().add(K8sTestHelper.buildRelease(IK8sRelease.Status.Succeeded, 2));
    releaseHistory.getReleases().add(K8sTestHelper.buildRelease(IK8sRelease.Status.Succeeded, 1));
    releaseHistory.getReleases().add(K8sTestHelper.buildRelease(IK8sRelease.Status.Succeeded, 0));
    ProcessResponse response = ProcessResponse.builder().processResult(K8sTestHelper.buildProcessResult(0)).build();
    doReturn(response).when(spyK8sTaskHelperBase).runK8sExecutable(any(), any(), any());
    spyK8sTaskHelperBase.cleanup(Kubectl.client("kubectl", "kubeconfig"), K8sDelegateTaskParams.builder().build(),
        releaseHistory, executionLogCallback);
    ArgumentCaptor<DeleteCommand> captor = ArgumentCaptor.forClass(DeleteCommand.class);
    verify(spyK8sTaskHelperBase, times(3)).runK8sExecutable(any(), any(), captor.capture());
    final List<DeleteCommand> deleteCommands = captor.getAllValues();
    assertThat(releaseHistory.getReleases()).hasSize(1);
    assertThat(deleteCommands.get(0).command()).isEqualTo("kubectl --kubeconfig=kubeconfig delete ConfigMap/configMap");
    reset(spyK8sTaskHelperBase);
  }

  private void cleanUpIfMultipleFailedReleases() throws Exception {
    final ReleaseHistory releaseHistory = ReleaseHistory.createNew();
    releaseHistory.getReleases().add(K8sTestHelper.buildRelease(IK8sRelease.Status.Failed, 3));
    releaseHistory.getReleases().add(K8sTestHelper.buildRelease(IK8sRelease.Status.Failed, 2));
    releaseHistory.getReleases().add(K8sTestHelper.buildRelease(IK8sRelease.Status.Succeeded, 1));
    releaseHistory.getReleases().add(K8sTestHelper.buildRelease(IK8sRelease.Status.Failed, 0));
    ProcessResponse response = ProcessResponse.builder().processResult(K8sTestHelper.buildProcessResult(0)).build();
    doReturn(response).when(spyK8sTaskHelperBase).runK8sExecutable(any(), any(), any());
    spyK8sTaskHelperBase.cleanup(Kubectl.client("kubectl", "kubeconfig"), K8sDelegateTaskParams.builder().build(),
        releaseHistory, executionLogCallback);
    ArgumentCaptor<DeleteCommand> captor = ArgumentCaptor.forClass(DeleteCommand.class);
    verify(spyK8sTaskHelperBase, times(3)).runK8sExecutable(any(), any(), captor.capture());
    final List<DeleteCommand> deleteCommands = captor.getAllValues();
    assertThat(releaseHistory.getReleases()).hasSize(1);
    assertThat(deleteCommands.get(0).command()).isEqualTo("kubectl --kubeconfig=kubeconfig delete ConfigMap/configMap");
    reset(spyK8sTaskHelperBase);
  }

  private void cleanUpIfOnly1FailedRelease() throws Exception {
    final ReleaseHistory releaseHistory = ReleaseHistory.createNew();
    releaseHistory.getReleases().add(K8sLegacyRelease.builder()
                                         .number(0)
                                         .resources(asList(K8sTestHelper.deployment().getResourceId()))
                                         .status(IK8sRelease.Status.Failed)
                                         .build());
    k8sTaskHelperBase.cleanup(
        mock(Kubectl.class), K8sDelegateTaskParams.builder().build(), releaseHistory, executionLogCallback);
    assertThat(releaseHistory.getReleases()).isEmpty();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getCurrentReplicas() throws Exception {
    doReturn(K8sTestHelper.buildProcessResult(0, "3"))
        .doReturn(K8sTestHelper.buildProcessResult(1))
        .doReturn(K8sTestHelper.buildProcessResult(0, ""))
        .when(spyK8sTaskHelperBase)
        .runK8sExecutableSilent(any(), any());

    assertThat(
        spyK8sTaskHelperBase.getCurrentReplicas(Kubectl.client("kubectl", "kubeconfig"),
            K8sTestHelper.deployment().getResourceId(), K8sDelegateTaskParams.builder().build(), executionLogCallback))
        .isEqualTo(3);

    assertThat(
        spyK8sTaskHelperBase.getCurrentReplicas(Kubectl.client("kubectl", "kubeconfig"),
            K8sTestHelper.deployment().getResourceId(), K8sDelegateTaskParams.builder().build(), executionLogCallback))
        .isNull();

    assertThat(
        spyK8sTaskHelperBase.getCurrentReplicas(Kubectl.client("kubectl", "kubeconfig"),
            K8sTestHelper.deployment().getResourceId(), K8sDelegateTaskParams.builder().build(), executionLogCallback))
        .isNull();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getLatestRevisionForDeploymentConfig() throws Exception {
    doReturn(K8sTestHelper.buildProcessResult(0,
                 "deploymentconfig.apps.openshift.io/anshul-dc\n"
                     + "REVISION\tSTATUS\t\tCAUSE\n"
                     + "137\t\tComplete\tconfig change\n"
                     + "138\t\tComplete\tconfig change\n"
                     + "139\t\tComplete\tconfig change\n"
                     + "140\t\tComplete\tconfig change\n"))
        .when(spyK8sTaskHelperBase)
        .executeCommandUsingUtils(any(K8sDelegateTaskParams.class), any(), any(), any(), any());
    MockedStatic<InstallUtils> mock = mockStatic(InstallUtils.class);
    PowerMockito.when(InstallUtils.getLatestVersionPath(ClientTool.OC)).thenReturn("oc");
    String latestRevision;
    latestRevision = spyK8sTaskHelperBase.getLatestRevision(Kubectl.client("kubectl", "kubeconfig"),
        K8sTestHelper.deploymentConfig().getResourceId(),
        K8sDelegateTaskParams.builder()
            .ocPath("oc")
            .kubeconfigPath("kubeconfig")
            .workingDirectory("./working-dir")
            .build());
    mock.close();

    verify(spyK8sTaskHelperBase, times(1))
        .executeCommandUsingUtils(eq(K8sDelegateTaskParams.builder()
                                          .ocPath("oc")
                                          .kubeconfigPath("kubeconfig")
                                          .workingDirectory("./working-dir")
                                          .build()),
            any(), any(), eq("oc --kubeconfig=kubeconfig rollout history DeploymentConfig/test-dc --namespace=default"),
            any());
    assertThat(latestRevision).isEqualTo("140");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getLatestRevisionForDeployment() throws Exception {
    doReturn(
        K8sTestHelper.buildProcessResult(0,
            "deployments \"nginx-deployment\"\n"
                + "REVISION    CHANGE-CAUSE\n"
                + "1           kubectl apply --filename=https://k8s.io/examples/controllers/nginx-deployment.yaml --record=true\n"
                + "2           kubectl set image deployment.v1.apps/nginx-deployment nginx=nginx:1.16.1 --record=true\n"
                + "3           kubectl set image deployment.v1.apps/nginx-deployment nginx=nginx:1.161 --record=true"))
        .when(spyK8sTaskHelperBase)
        .runK8sExecutableSilent(any(), any());
    String latestRevision;
    latestRevision = spyK8sTaskHelperBase.getLatestRevision(Kubectl.client("kubectl", "kubeconfig"),
        K8sTestHelper.deployment().getResourceId(),
        K8sDelegateTaskParams.builder()
            .kubectlPath("kubectl")
            .kubeconfigPath("kubeconfig")
            .workingDirectory("./working-dir")
            .build());

    ArgumentCaptor<RolloutHistoryCommand> captor = ArgumentCaptor.forClass(RolloutHistoryCommand.class);
    verify(spyK8sTaskHelperBase, times(1))
        .runK8sExecutableSilent(eq(K8sDelegateTaskParams.builder()
                                        .kubectlPath("kubectl")
                                        .kubeconfigPath("kubeconfig")
                                        .workingDirectory("./working-dir")
                                        .build()),
            captor.capture());
    RolloutHistoryCommand rolloutHistoryCommand = captor.getValue();
    assertThat(rolloutHistoryCommand.command())
        .isEqualTo("kubectl --kubeconfig=kubeconfig rollout history Deployment/nginx-deployment");
    assertThat(latestRevision).isEqualTo("3");
  }

  @Test
  @Owner(developers = {YOGESH, ACASIAN})
  @Category(UnitTests.class)
  public void readManifests() throws IOException {
    final List<KubernetesResource> resources =
        k8sTaskHelperBase.readManifests(prepareSomeCorrectManifestFiles(), executionLogCallback);
    assertThat(resources).hasSize(3);
    assertThat(resources.stream()
                   .map(KubernetesResource::getResourceId)
                   .map(KubernetesResourceId::getKind)
                   .collect(Collectors.toList()))
        .containsExactly("ConfigMap", "Deployment", "DeploymentConfig");
    assertThatExceptionOfType(KubernetesYamlException.class)
        .isThrownBy(() -> k8sTaskHelperBase.readManifests(prepareSomeInCorrectManifestFiles(), executionLogCallback));

    assertThatThrownBy(
        () -> k8sTaskHelperBase.readManifests(prepareSomeInCorrectManifestFiles(), executionLogCallback, true))
        .matches(throwable -> {
          HintException hint = ExceptionUtils.cause(HintException.class, throwable);
          ExplanationException explanation = ExceptionUtils.cause(ExplanationException.class, throwable);
          KubernetesTaskException taskException = ExceptionUtils.cause(KubernetesTaskException.class, throwable);
          assertThat(hint).hasMessageContaining(KubernetesExceptionHints.READ_MANIFEST_FAILED);
          assertThat(explanation).hasMessageContaining(throwable.getCause().getMessage());
          assertThat(taskException)
              .hasMessageContaining(format(KubernetesExceptionMessages.READ_MANIFEST_FAILED, "manifest.yaml"));
          return true;
        });
  }

  private List<FileData> prepareSomeCorrectManifestFiles() throws IOException {
    return asList(
        FileData.builder().fileContent(K8sTestHelper.readResourceFileContent(DEPLOYMENT)).fileName(DEPLOYMENT).build(),
        FileData.builder()
            .fileName(DEPLOYMENT_CONFIG)
            .fileContent(K8sTestHelper.readResourceFileContent(DEPLOYMENT_CONFIG))
            .build(),
        FileData.builder().fileName(CONFIG_MAP).fileContent(K8sTestHelper.readResourceFileContent(CONFIG_MAP)).build());
  }

  private List<FileData> prepareSomeInCorrectManifestFiles() {
    return asList(FileData.builder().fileContent("some-random-content").fileName("manifest.yaml").build(),
        FileData.builder().fileContent("not-a-manifest-file").fileName("a.txt").build());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void setNameSpaceToKubernetesResources() throws IOException {
    k8sTaskHelperBase.setNamespaceToKubernetesResourcesIfRequired(null, "default");
    k8sTaskHelperBase.setNamespaceToKubernetesResourcesIfRequired(emptyList(), "default");
    KubernetesResource deployment = K8sTestHelper.deployment();
    deployment.getResourceId().setNamespace(null);
    KubernetesResource configMap = K8sTestHelper.configMap();
    configMap.getResourceId().setNamespace("default");
    k8sTaskHelperBase.setNamespaceToKubernetesResourcesIfRequired(asList(deployment, configMap), "harness");
    assertThat(deployment.getResourceId().getNamespace()).isEqualTo("harness");
    assertThat(configMap.getResourceId().getNamespace()).isEqualTo("default");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testDoStatusCheck() throws Exception {
    KubernetesResourceId resourceId = KubernetesResourceId.builder().namespace("namespace").name("resource").build();
    Kubectl client = Kubectl.client("kubectl", "config-path");
    K8sDelegateTaskParams k8sDelegateTaskParams =
        K8sDelegateTaskParams.builder().ocPath(".").workingDirectory(".").build();

    MockedStatic<InstallUtils> mock = mockStatic(InstallUtils.class);
    PowerMockito.when(InstallUtils.getLatestVersionPath(ClientTool.OC)).thenReturn("oc");
    final boolean result =
        spyK8sTaskHelperBase.doStatusCheck(client, resourceId, k8sDelegateTaskParams, executionLogCallback);
    mock.close();

    assertThat(result).isEqualTo(false);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testDoStatusCheckKindDeployment() throws Exception {
    KubernetesResourceId resourceId =
        KubernetesResourceId.builder().namespace("namespace").kind(DeploymentConfig.name()).name("name").build();
    Kubectl client = Kubectl.client("kubectl", "config-path");
    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory(workingDirectory)
                                                      .ocPath("oc")
                                                      .kubectlPath("kubectl")
                                                      .kubeconfigPath("config-path")
                                                      .build();

    ProcessResult processResult = new ProcessResult(0, new ProcessOutput("".getBytes()));
    doReturn(processResult)
        .when(spyK8sTaskHelperBase)
        .executeCommandUsingUtils(any(String.class), any(), any(), any(), any());

    final String expectedCommand =
        "oc --kubeconfig=config-path rollout status DeploymentConfig/name --namespace=namespace --watch=true";
    MockedStatic<InstallUtils> mock = mockStatic(InstallUtils.class);
    PowerMockito.when(InstallUtils.getLatestVersionPath(ClientTool.OC)).thenReturn("oc");
    final boolean result =
        spyK8sTaskHelperBase.doStatusCheck(client, resourceId, k8sDelegateTaskParams, executionLogCallback);
    mock.close();

    verify(spyK8sTaskHelperBase).executeCommandUsingUtils(eq("."), any(), any(), eq(expectedCommand), any());

    assertThat(result).isEqualTo(true);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testDoStatusCheckForAllResourcesNonJobResource() throws Exception {
    KubernetesResourceId resourceId =
        KubernetesResourceId.builder().namespace("namespace").kind(DeploymentConfig.name()).name("name").build();
    KubernetesResourceId resourceId2 =
        KubernetesResourceId.builder().kind(ConfigMap.name()).name("resource").namespace("namespace").build();

    Kubectl client = Kubectl.client("kubectl", "config-path");
    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory(workingDirectory)
                                                      .ocPath("oc")
                                                      .kubectlPath("kubectl")
                                                      .kubeconfigPath("config-path")
                                                      .build();

    ProcessResult processResult = new ProcessResult(0, new ProcessOutput("".getBytes()));
    doReturn(processResult)
        .when(spyK8sTaskHelperBase)
        .executeCommandUsingUtils(any(K8sDelegateTaskParams.class), any(), any(), any(), any());

    List<KubernetesResourceId> resourceIds = new ArrayList<>();
    resourceIds.add(resourceId);
    resourceIds.add(resourceId2);
    MockedStatic<InstallUtils> mock = mockStatic(InstallUtils.class);
    PowerMockito.when(InstallUtils.getLatestVersionPath(ClientTool.OC)).thenReturn("oc");
    final boolean result = spyK8sTaskHelperBase.doStatusCheckForAllResources(
        client, resourceIds, k8sDelegateTaskParams, "name", executionLogCallback, false);
    mock.close();
    verify(spyK8sTaskHelperBase)
        .executeCommandUsingUtils(eq(k8sDelegateTaskParams), any(), any(),
            eq("oc --kubeconfig=config-path rollout status DeploymentConfig/name --namespace=namespace --watch=true"),
            any());

    assertThat(result).isEqualTo(false);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testDoStatusCheckForAllResourcesDeploymentConfigIsErrorFrameworkEnabled() throws Exception {
    KubernetesResourceId deploymentConfig =
        KubernetesResourceId.builder().namespace("namespace").kind(DeploymentConfig.name()).name("name").build();

    Kubectl client = Kubectl.client("kubectl", "config-path");
    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory(workingDirectory)
                                                      .ocPath("oc")
                                                      .kubectlPath("kubectl")
                                                      .kubeconfigPath("config-path")
                                                      .build();

    ProcessResult processResult = new ProcessResult(1, new ProcessOutput("Something went wrong".getBytes()));
    doReturn(processResult)
        .when(spyK8sTaskHelperBase)
        .executeCommandUsingUtils(any(K8sDelegateTaskParams.class), any(), any(), any(), any());

    MockedStatic<InstallUtils> mock = mockStatic(InstallUtils.class);
    PowerMockito.when(InstallUtils.getLatestVersionPath(ClientTool.OC)).thenReturn("oc");
    assertThatThrownBy(()
                           -> spyK8sTaskHelperBase.doStatusCheckForAllResources(client, singletonList(deploymentConfig),
                               k8sDelegateTaskParams, "name", executionLogCallback, false, true))
        .matches(throwable -> {
          KubernetesCliTaskRuntimeException taskException = (KubernetesCliTaskRuntimeException) throwable;
          assertThat(taskException.getProcessResponse().getProcessResult().outputUTF8())
              .contains("Something went wrong");
          assertThat(taskException.getProcessResponse().getProcessResult().getExitValue()).isEqualTo(1);
          return true;
        });
    mock.close();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testDoStatusCheckForAllResourcesJobIsErrorFrameworkEnabled() throws Exception {
    KubernetesResourceId deploymentConfig =
        KubernetesResourceId.builder().namespace("namespace").kind(Job.name()).name("name").build();

    Kubectl client = Kubectl.client("kubectl", "config-path");
    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory(workingDirectory)
                                                      .ocPath("oc")
                                                      .kubectlPath("kubectl")
                                                      .kubeconfigPath("config-path")
                                                      .build();

    ProcessResult processResult = new ProcessResult(1, new ProcessOutput("Something went wrong".getBytes()));
    doReturn(processResult)
        .when(spyK8sTaskHelperBase)
        .executeCommandUsingUtils(any(K8sDelegateTaskParams.class), any(), any(), any(), any());

    assertThatThrownBy(()
                           -> spyK8sTaskHelperBase.doStatusCheckForAllResources(client, singletonList(deploymentConfig),
                               k8sDelegateTaskParams, "name", executionLogCallback, false, true))
        .matches(throwable -> {
          HintException hint = ExceptionUtils.cause(HintException.class, throwable);
          KubernetesTaskException taskException = ExceptionUtils.cause(KubernetesTaskException.class, throwable);
          assertThat(hint).hasMessageContaining(KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_CLI_FAILED);
          assertThat(taskException).hasMessageContaining(KubernetesExceptionMessages.WAIT_FOR_STEADY_STATE_FAILED);
          return true;
        });
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testDoStatusCheckForAllResourcesMultipleResources() throws Exception {
    KubernetesResourceId resourceId =
        KubernetesResourceId.builder().namespace("namespace").kind(DeploymentConfig.name()).name("name").build();
    KubernetesResourceId resourceId1 =
        KubernetesResourceId.builder().kind(Kind.Job.name()).name("resource").namespace("namespace").build();
    Kubectl client = Kubectl.client("kubectl", "config-path");
    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory(workingDirectory)
                                                      .ocPath("oc")
                                                      .kubectlPath("kubectl")
                                                      .kubeconfigPath("config-path")
                                                      .build();

    ProcessResult processResult = new ProcessResult(0, new ProcessOutput("".getBytes()));
    doReturn(processResult)
        .when(spyK8sTaskHelperBase)
        .executeCommandUsingUtils(any(K8sDelegateTaskParams.class), any(), any(), any(), any());

    List<KubernetesResourceId> resourceIds = new ArrayList<>();
    resourceIds.add(resourceId);
    resourceIds.add(resourceId1);
    MockedStatic<InstallUtils> mock = mockStatic(InstallUtils.class);
    PowerMockito.when(InstallUtils.getLatestVersionPath(ClientTool.OC)).thenReturn("oc");
    final boolean result = spyK8sTaskHelperBase.doStatusCheckForAllResources(
        client, resourceIds, k8sDelegateTaskParams, "name", executionLogCallback, false);
    mock.close();

    verify(spyK8sTaskHelperBase)
        .executeCommandUsingUtils(eq(k8sDelegateTaskParams), any(), any(),
            eq("oc --kubeconfig=config-path rollout status DeploymentConfig/name --namespace=namespace --watch=true"),
            any());

    assertThat(result).isEqualTo(false);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testReadManifestAndOverrideLocalSecrets() throws Exception {
    when(delegateLocalConfigService.replacePlaceholdersWithLocalConfig(anyString()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, String.class));
    final String workingDirectory = ".";

    ProcessResult processResult = new ProcessResult(0, new ProcessOutput("".getBytes()));
    doReturn(processResult)
        .when(spyK8sTaskHelperBase)
        .executeCommandUsingUtils(any(K8sDelegateTaskParams.class), any(), any(), any(), any());

    final List<FileData> manifestFiles = prepareSomeCorrectManifestFiles();

    final List<KubernetesResource> resources =
        k8sTaskHelperBase.readManifestAndOverrideLocalSecrets(manifestFiles, executionLogCallback, true);

    assertThat(resources.stream()
                   .map(KubernetesResource::getResourceId)
                   .map(KubernetesResourceId::getKind)
                   .collect(Collectors.toList()))
        .isEqualTo(asList(ConfigMap.name(), Deployment.name(), DeploymentConfig.name()));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getResourcesInStringFormat() throws IOException {
    final String resourcesInStringFormat = K8sTaskHelperBase.getResourcesInStringFormat(
        asList(K8sTestHelper.deployment().getResourceId(), K8sTestHelper.configMap().getResourceId()));
    assertThat(resourcesInStringFormat)
        .isEqualTo("\n"
            + "- Deployment/nginx-deployment\n"
            + "- ConfigMap/configMap");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void describe() throws Exception {
    ProcessResponse response = ProcessResponse.builder().processResult(K8sTestHelper.buildProcessResult(0)).build();
    doReturn(response).when(spyK8sTaskHelperBase).runK8sExecutable(any(), any(), any());
    spyK8sTaskHelperBase.describe(Kubectl.client("kubectl", "kubeconfig"),
        K8sDelegateTaskParams.builder().workingDirectory("./working-dir").build(), executionLogCallback);
    ArgumentCaptor<DescribeCommand> captor = ArgumentCaptor.forClass(DescribeCommand.class);
    verify(spyK8sTaskHelperBase, times(1))
        .runK8sExecutable(
            eq(K8sDelegateTaskParams.builder().workingDirectory("./working-dir").build()), any(), captor.capture());
    assertThat(captor.getValue().command())
        .isEqualTo("kubectl --kubeconfig=kubeconfig describe --filename=manifests.yaml");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testReadManifestAndOverrideLocalSecretsOverrideLocalSecrets() throws Exception {
    ProcessResult processResult = new ProcessResult(0, new ProcessOutput("".getBytes()));
    doReturn(processResult)
        .when(spyK8sTaskHelperBase)
        .executeCommandUsingUtils(any(K8sDelegateTaskParams.class), any(), any(), any(), any());

    final List<FileData> manifestFiles = prepareSomeCorrectManifestFiles();
    final List<KubernetesResource> resources =
        spyK8sTaskHelperBase.readManifestAndOverrideLocalSecrets(manifestFiles, executionLogCallback, false);

    assertThat(resources.size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetManifestFileNamesInLogFormat() throws Exception {
    final String result = spyK8sTaskHelperBase.getManifestFileNamesInLogFormat(".");

    assertThat(result).isNotBlank();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testRunK8sExecutable() throws Exception {
    KubernetesResourceId resourceId =
        KubernetesResourceId.builder().namespace("namespace").kind(DeploymentConfig.name()).name("name").build();
    KubernetesResourceId resourceId1 =
        KubernetesResourceId.builder().kind(Kind.Job.name()).name("resource").namespace("namespace").build();
    Kubectl client = Kubectl.client("kubectl", "config-path");
    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory(workingDirectory)
                                                      .ocPath("oc")
                                                      .kubectlPath("kubectl")
                                                      .kubeconfigPath("config-path")
                                                      .build();

    List<KubernetesResourceId> resourceIds = new ArrayList<>();
    resourceIds.add(resourceId);
    resourceIds.add(resourceId1);
    ProcessResult result =
        spyK8sTaskHelperBase.runK8sExecutable(k8sDelegateTaskParams, executionLogCallback, new ApplyCommand(client))
            .getProcessResult();

    assertThat(result.getExitValue()).isEqualTo(1);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testRunK8sExecutableSilent() throws Exception {
    Kubectl client = Kubectl.client("kubectl", "config-path");
    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory(workingDirectory)
                                                      .ocPath("oc")
                                                      .kubectlPath("kubectl")
                                                      .kubeconfigPath("config-path")
                                                      .build();

    ProcessResult result = spyK8sTaskHelperBase.runK8sExecutableSilent(k8sDelegateTaskParams, new ApplyCommand(client));
    assertThat(result.getExitValue()).isEqualTo(1);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetExecutionLogOutputStream() throws Exception {
    LogOutputStream logOutputStream = K8sTaskHelperBase.getExecutionLogOutputStream(executionLogCallback, INFO);

    assertThat(logOutputStream).isInstanceOf(LogOutputStream.class);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testDoStatusCheckForAllResourcesEmptyResourceIds() throws Exception {
    Kubectl client = Kubectl.client("kubectl", "config-path");
    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory(workingDirectory)
                                                      .ocPath("oc")
                                                      .kubectlPath("kubectl")
                                                      .kubeconfigPath("config-path")
                                                      .build();

    List<KubernetesResourceId> resourceIds = new ArrayList<>();
    final boolean result = spyK8sTaskHelperBase.doStatusCheckForAllResources(
        client, resourceIds, k8sDelegateTaskParams, "name", executionLogCallback, false);

    assertThat(result).isEqualTo(true);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetOcCommandPrefix() {
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory(".")
                                                      .kubectlPath("kubectl")
                                                      .kubeconfigPath("config-path")
                                                      .build();

    MockedStatic<InstallUtils> mock = mockStatic(InstallUtils.class);
    PowerMockito.when(InstallUtils.getLatestVersionPath(ClientTool.OC)).thenReturn("oc");
    final String result = spyK8sTaskHelperBase.getOcCommandPrefix(k8sDelegateTaskParams);
    mock.close();

    assertThat(result).isEqualTo("oc --kubeconfig=config-path");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testOcRolloutCommand() throws Exception {
    KubernetesResourceId resourceId =
        KubernetesResourceId.builder().name("app1").kind("Deployment").namespace("default").build();
    String actualOcRolloutCommand =
        spyK8sTaskHelperBase.getRolloutStatusCommandForDeploymentConfig("oc", "/.kube/config", resourceId);

    String expectedOcRolloutCommand =
        "oc --kubeconfig=/.kube/config rollout status Deployment/app1 --namespace=default --watch=true";
    assertThat(actualOcRolloutCommand).isEqualTo(expectedOcRolloutCommand);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testSteadyStateConditionIsSet() {
    List<KubernetesResource> managedResources = ManifestHelper.processYaml("apiVersion: apps/v1\n"
        + "kind: Foo\n"
        + "metadata:\n"
        + "  name: deployment\n"
        + "  annotations:\n"
        + "    harness.io/managed-workload: true\n"
        + "    harness.io/steadyStateCondition: 1==1\n"
        + "spec:\n"
        + "  replicas: 1");

    spyK8sTaskHelperBase.checkSteadyStateCondition(managedResources);
    assert true;
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testSteadyStateConditionIsUnset() {
    List<KubernetesResource> managedResources = ManifestHelper.processYaml("apiVersion: apps/v1\n"
        + "kind: Foo\n"
        + "metadata:\n"
        + "  name: deployment\n"
        + "  annotations:\n"
        + "    harness.io/managed-workload: true\n"
        + "spec:\n"
        + "  replicas: 1");

    try {
      spyK8sTaskHelperBase.checkSteadyStateCondition(managedResources);
    } catch (InvalidArgumentsException e) {
      assertThat(e).hasMessage("INVALID_ARGUMENT");
    }
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldGetReleaseHistoryFromSecretFirstK8sClient() throws IOException {
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();
    when(mockKubernetesContainerService.fetchReleaseHistoryFromSecrets(any(), any())).thenReturn("secret");
    String releaseHistory = spyK8sTaskHelperBase.getReleaseHistoryData(kubernetesConfig, "release");
    ArgumentCaptor<String> releaseArgumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockKubernetesContainerService).fetchReleaseHistoryFromSecrets(any(), releaseArgumentCaptor.capture());
    verify(mockKubernetesContainerService, times(0)).fetchReleaseHistoryFromConfigMap(any(), any());

    assertThat(releaseArgumentCaptor.getValue()).isEqualTo("release");
    assertThat(releaseHistory).isEqualTo("secret");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldGetReleaseHistoryConfigMapIfNotFoundInSecretK8sClient() throws IOException {
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();
    when(mockKubernetesContainerService.fetchReleaseHistoryFromSecrets(any(), any())).thenReturn(null);
    when(mockKubernetesContainerService.fetchReleaseHistoryFromConfigMap(any(), any())).thenReturn("configmap");
    String releaseHistory = spyK8sTaskHelperBase.getReleaseHistoryData(kubernetesConfig, "release");
    ArgumentCaptor<String> releaseArgumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockKubernetesContainerService, times(1)).fetchReleaseHistoryFromSecrets(any(), anyString());
    verify(mockKubernetesContainerService, times(1))
        .fetchReleaseHistoryFromConfigMap(any(), releaseArgumentCaptor.capture());

    assertThat(releaseArgumentCaptor.getValue()).isEqualTo("release");
    assertThat(releaseHistory).isEqualTo("configmap");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testShouldGetReleaseHistoryConfigMapIfInvalidRequestExceptionThrown() throws IOException {
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();
    when(mockKubernetesContainerService.fetchReleaseHistoryFromSecrets(any(), any()))
        .thenThrow(new InvalidRequestException(""));
    when(mockKubernetesContainerService.fetchReleaseHistoryFromConfigMap(any(), any())).thenReturn("configmap");
    String releaseHistory = spyK8sTaskHelperBase.getReleaseHistoryData(kubernetesConfig, "release");
    ArgumentCaptor<String> releaseArgumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockKubernetesContainerService, times(1)).fetchReleaseHistoryFromSecrets(any(), anyString());
    verify(mockKubernetesContainerService, times(1))
        .fetchReleaseHistoryFromConfigMap(any(), releaseArgumentCaptor.capture());

    assertThat(releaseArgumentCaptor.getValue()).isEqualTo("release");
    assertThat(releaseHistory).isEqualTo("configmap");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateVirtualServiceWithDestinationWeights() throws IOException {
    VirtualService service = virtualServiceWith(ImmutableMap.of("localhost", 2304));
    List<IstioDestinationWeight> destinationWeights =
        asList(IstioDestinationWeight.builder().destination(canaryDestinationExpression).weight("10").build(),
            IstioDestinationWeight.builder().destination(stableDestinationExpression).weight("40").build(),
            IstioDestinationWeight.builder().destination("host: test\nsubset: default").weight("50").build());

    k8sTaskHelperBase.updateVirtualServiceWithDestinationWeights(destinationWeights, service, executionLogCallback);
    List<HTTPRouteDestination> routes = service.getSpec().getHttp().get(0).getRoute();
    assertThat(routes.stream().map(HTTPRouteDestination::getWeight)).containsExactly(10, 40, 50);
    assertThat(routes.stream().map(HTTPRouteDestination::getDestination).map(Destination::getSubset))
        .containsExactly(HarnessLabelValues.trackCanary, HarnessLabelValues.trackStable, "default");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateVirtualServiceWithDestinationWeightsMultipleRoutes() {
    VirtualService service = virtualServiceWith(ImmutableMap.of("localhost", 2304, "0.0.0.0", 8030));
    List<IstioDestinationWeight> destinationWeights = emptyList();
    assertThatThrownBy(()
                           -> k8sTaskHelperBase.updateVirtualServiceWithDestinationWeights(
                               destinationWeights, service, executionLogCallback))
        .hasMessageContaining("Only one route is allowed in VirtualService");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateVirtualServiceWithDestinationWeightsNoRoutes() {
    VirtualService service = virtualServiceWith(ImmutableMap.of());
    List<IstioDestinationWeight> destinationWeights = emptyList();
    assertThatThrownBy(()
                           -> k8sTaskHelperBase.updateVirtualServiceWithDestinationWeights(
                               destinationWeights, service, executionLogCallback))
        .hasMessageContaining("Http route is not present in VirtualService. Only Http routes are allowed");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateVirtualServiceWithDestinationWeightsNonHttpRoutes() {
    VirtualServiceSpec spec = new VirtualServiceSpecBuilder().withHttp(new HTTPRoute()).withTcp(new TCPRoute()).build();
    VirtualService service = new VirtualServiceBuilder().withSpec(spec).build();
    List<IstioDestinationWeight> destinationWeights = emptyList();
    assertThatThrownBy(()
                           -> k8sTaskHelperBase.updateVirtualServiceWithDestinationWeights(
                               destinationWeights, service, executionLogCallback))
        .hasMessageContaining("Only Http routes are allowed in VirtualService for Traffic split");

    spec.setTcp(emptyList());
    spec.setTls(asList(new TLSRoute()));
    assertThatThrownBy(()
                           -> k8sTaskHelperBase.updateVirtualServiceWithDestinationWeights(
                               destinationWeights, service, executionLogCallback))
        .hasMessageContaining("Only Http routes are allowed in VirtualService for Traffic split");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateVirtualServiceManifestFilesWithRoutesForCanary() throws IOException {
    VirtualService service1 = virtualServiceWith(ImmutableMap.of("localhost", 1234));
    List<KubernetesResource> resources =
        asList(KubernetesResource.builder()
                   .resourceId(KubernetesResourceId.builder().name("service1").kind(Kind.VirtualService.name()).build())
                   .value(ImmutableMap.of(
                       "metadata", ImmutableMap.of("annotations", ImmutableMap.of(HarnessAnnotations.managed, "true"))))
                   .spec("mock")
                   .build(),
            KubernetesResource.builder()
                .resourceId(KubernetesResourceId.builder().name("service2").kind(Kind.VirtualService.name()).build())
                .value(ImmutableMap.of())
                .build(),
            KubernetesResource.builder()
                .resourceId(KubernetesResourceId.builder().name("deployment").kind(Deployment.name()).build())
                .build());

    KubernetesClient mockClient = mock(KubernetesClient.class);
    doReturn(mockClient).when(kubernetesHelperService).getKubernetesClient(any());
    ParameterNamespaceListVisitFromServerGetDeleteRecreateWaitApplicable resource =
        mock(ParameterNamespaceListVisitFromServerGetDeleteRecreateWaitApplicable.class);
    doReturn(resource).when(mockClient).load(any());
    doReturn(asList(service1)).when(resource).items();
    VirtualService result = k8sTaskHelperBase.updateVirtualServiceManifestFilesWithRoutesForCanary(
        resources, KubernetesConfig.builder().build(), executionLogCallback);
    List<HTTPRouteDestination> routes = result.getSpec().getHttp().get(0).getRoute();
    assertThat(routes.stream().map(HTTPRouteDestination::getWeight)).containsExactly(100, 0);
    assertThat(routes.stream().map(HTTPRouteDestination::getDestination).map(Destination::getSubset))
        .containsExactly(HarnessLabelValues.trackStable, HarnessLabelValues.trackCanary);
  }

  private VirtualService virtualServiceWith(Map<String, Integer> destinations) {
    List<HTTPRoute> routes =
        destinations.entrySet()
            .stream()
            .map(entry
                -> new HTTPRouteBuilder()
                       .withRoute(new HTTPRouteDestinationBuilder()
                                      .withDestination(
                                          new DestinationBuilder()
                                              .withHost(entry.getKey())
                                              .withPort(new PortSelectorBuilder().withNumber(entry.getValue()).build())
                                              .build())
                                      .build())
                       .build())
            .collect(Collectors.toList());

    return new VirtualServiceBuilder().withSpec(new VirtualServiceSpecBuilder().withHttp(routes).build()).build();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDelete() throws Exception {
    Kubectl kubectl = Kubectl.client("kubectl", "test");
    K8sDelegateTaskParams params = K8sDelegateTaskParams.builder().build();
    List<KubernetesResourceId> kubernetesResourceIds =
        asList(KubernetesResourceId.builder().kind("deployment").name("test1").build(),
            KubernetesResourceId.builder().kind("configmap").name("test2").build());
    ProcessResponse response = ProcessResponse.builder().processResult(new ProcessResult(0, null)).build();
    doReturn(response)
        .when(spyK8sTaskHelperBase)
        .runK8sExecutable(eq(params), eq(executionLogCallback), any(AbstractExecutable.class));

    spyK8sTaskHelperBase.delete(kubectl, params, kubernetesResourceIds, executionLogCallback, true);
    ArgumentCaptor<AbstractExecutable> captor = ArgumentCaptor.forClass(AbstractExecutable.class);
    verify(spyK8sTaskHelperBase, times(2)).runK8sExecutable(eq(params), eq(executionLogCallback), captor.capture());
    assertThat(captor.getAllValues().get(0)).isInstanceOf(DeleteCommand.class);
    assertThat(captor.getAllValues().get(0).command()).isEqualTo("kubectl --kubeconfig=test delete deployment/test1");
    assertThat(captor.getAllValues().get(1)).isInstanceOf(DeleteCommand.class);
    assertThat(captor.getAllValues().get(1).command()).isEqualTo("kubectl --kubeconfig=test delete configmap/test2");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteDelete() throws Exception {
    Kubectl kubectl = Kubectl.client("kubectl", "test");
    K8sDelegateTaskParams params = K8sDelegateTaskParams.builder().build();
    List<KubernetesResourceId> kubernetesResourceIds =
        asList(KubernetesResourceId.builder().kind("deployment").name("test1").build(),
            KubernetesResourceId.builder().kind("configmap").name("test2").build());
    ProcessResponse response = ProcessResponse.builder().processResult(new ProcessResult(0, null)).build();
    doReturn(response)
        .when(spyK8sTaskHelperBase)
        .runK8sExecutable(eq(params), eq(executionLogCallback), any(AbstractExecutable.class));

    spyK8sTaskHelperBase.delete(kubectl, params, kubernetesResourceIds, executionLogCallback, true);
    ArgumentCaptor<AbstractExecutable> captor = ArgumentCaptor.forClass(AbstractExecutable.class);
    verify(spyK8sTaskHelperBase, times(2)).runK8sExecutable(eq(params), eq(executionLogCallback), captor.capture());
    assertThat(captor.getAllValues().get(0)).isInstanceOf(DeleteCommand.class);
    assertThat(captor.getAllValues().get(0).command()).isEqualTo("kubectl --kubeconfig=test delete deployment/test1");
    assertThat(captor.getAllValues().get(1)).isInstanceOf(DeleteCommand.class);
    assertThat(captor.getAllValues().get(1).command()).isEqualTo("kubectl --kubeconfig=test delete configmap/test2");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRenderManifestFilesForGoTemplate() throws Exception {
    Path temp = Files.createTempDirectory("testRenderManifestFilesForGoTemplate");
    try {
      String renderedTemplate = "rendered";
      List<String> values = singletonList("field: value");
      K8sDelegateTaskParams params =
          K8sDelegateTaskParams.builder().goTemplateClientPath("go-template").workingDirectory(temp.toString()).build();

      doReturn(new ProcessResult(0, new ProcessOutput(renderedTemplate.getBytes())))
          .when(spyK8sTaskHelperBase)
          .executeShellCommand(anyString(), eq("go-template -t template.yaml  -f values-0.yaml"), any(), anyLong());

      List<FileData> result = spyK8sTaskHelperBase.renderManifestFilesForGoTemplate(params,
          asList(FileData.builder().fileContent("values").fileName(values_filename).build(),
              FileData.builder().fileContent("template").fileName("template.yaml").build()),
          values, executionLogCallback, 10000);
      assertThat(result.stream().map(FileData::getFileContent)).containsExactly("rendered");
    } finally {
      deleteDirectoryAndItsContentIfExists(temp.toString());
    }
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldLogIfValuesMissing() throws Exception {
    Path temp = Files.createTempDirectory("testRenderManifestFilesForGoTemplate");
    try {
      String renderedTemplate = "field: <no value>";
      List<String> values = singletonList("field: value");
      K8sDelegateTaskParams params =
          K8sDelegateTaskParams.builder().goTemplateClientPath("go-template").workingDirectory(temp.toString()).build();

      doReturn(new ProcessResult(0, new ProcessOutput(renderedTemplate.getBytes())))
          .when(spyK8sTaskHelperBase)
          .executeShellCommand(anyString(), eq("go-template -t template.yaml  -f values-0.yaml"), any(), anyLong());

      spyK8sTaskHelperBase.renderManifestFilesForGoTemplate(params,
          asList(FileData.builder().fileContent("values").fileName(values_filename).build(),
              FileData.builder().fileContent("template").fileName("template.yaml").build()),
          values, executionLogCallback, 10000);

      String expectedLogLine = "Rendered template is missing values (replaced with <no value>)!";
      verify(executionLogCallback, times(1)).saveExecutionLog(eq(color(expectedLogLine, Yellow, Bold)), eq(WARN));
    } finally {
      deleteDirectoryAndItsContentIfExists(temp.toString());
    }
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testSavingPatchesToDirectory() throws Exception {
    Path temp = Files.createTempDirectory("testRenderManifestFilesForGoTemplate");
    try {
      FileIo.writeUtf8StringToFile(temp.toString() + '/' + "kustomization.yaml",
          "patchesStrategicMerge:\n"
              + "- env.yaml\n");
      List<String> kustomizePatchesList = Arrays.asList("field: value", "field: value");
      spyK8sTaskHelperBase.savingPatchesToDirectory(temp.toString(), kustomizePatchesList, executionLogCallback);
      ArgumentCaptor<JSONArray> captor = ArgumentCaptor.forClass(JSONArray.class);
      verify(spyK8sTaskHelperBase, times(1)).updateKustomizationYaml(any(), captor.capture(), any());
      assertThat(captor.getValue()).isNotNull();
      assertThat(captor.getValue().get(0).toString()).endsWith("patches-0.yaml");
      assertThat(captor.getValue().get(1).toString()).endsWith("patches-1.yaml");
      // no patches case
      spyK8sTaskHelperBase.savingPatchesToDirectory(temp.toString(), emptyList(), executionLogCallback);
      verify(executionLogCallback, times(1))
          .saveExecutionLog("\nNo Patches files found. Skipping kustomization.yaml updation\n");

    } finally {
      deleteDirectoryAndItsContentIfExists(temp.toString());
    }
    temp = Files.createTempDirectory("testRenderManifestFilesForGoTemplate");
    try {
      FileIo.writeUtf8StringToFile(temp.toString() + '/' + "kustomization.yaml",
          "resources:\n"
              + "- ../../application\n");
      List<String> kustomizePatchesList = Arrays.asList("field: value", "field: value");
      spyK8sTaskHelperBase.savingPatchesToDirectory(temp.toString(), kustomizePatchesList, executionLogCallback);
      ArgumentCaptor<JSONArray> captor = ArgumentCaptor.forClass(JSONArray.class);
      verify(spyK8sTaskHelperBase, times(2)).updateKustomizationYaml(any(), captor.capture(), any());
      assertThat(captor.getValue()).isNotNull();
      assertThat(captor.getValue().get(0).toString()).endsWith("patches-0.yaml");
      assertThat(captor.getValue().get(1).toString()).endsWith("patches-1.yaml");
    } finally {
      deleteDirectoryAndItsContentIfExists(temp.toString());
    }
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDoStatusCheckForAllCustomResources() throws Exception {
    final String steadyStateCondition = "true";
    final Map<String, Object> resource = ImmutableMap.of("metadata",
        ImmutableMap.of("annotations", ImmutableMap.of(HarnessAnnotations.steadyStateCondition, steadyStateCondition)));
    final List<KubernetesResource> resources =
        asList(KubernetesResource.builder()
                   .resourceId(KubernetesResourceId.builder().name("test1").kind("foo").namespace("bar").build())
                   .value(resource)
                   .build(),
            KubernetesResource.builder()
                .resourceId(KubernetesResourceId.builder().name("test2").kind("bar").namespace("bar").build())
                .value(resource)
                .build(),
            KubernetesResource.builder()
                .resourceId(KubernetesResourceId.builder().name("test3").kind("boo").namespace("default").build())
                .value(resource)
                .build());

    boolean result = executeDoStatusCheckForCustomResources(
        resources, new ProcessResult(0, new ProcessOutput("value: value".getBytes())), false);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDoStatusCheckForAllCustomResourcesFailCli() throws Exception {
    final List<KubernetesResource> resources = singletonList(
        KubernetesResource.builder()
            .resourceId(KubernetesResourceId.builder().name("test2").kind("bar").namespace("default").build())
            .value(ImmutableMap.of("metadata",
                ImmutableMap.of("annotations", ImmutableMap.of(HarnessAnnotations.steadyStateCondition, "true"))))
            .build());
    boolean result = executeDoStatusCheckForCustomResources(
        resources, new ProcessResult(1, new ProcessOutput("value: value".getBytes())), false);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDoStatusCheckForAllCustomResourcesFailSteadyCheck() throws Exception {
    final List<KubernetesResource> resources = singletonList(
        KubernetesResource.builder()
            .resourceId(KubernetesResourceId.builder().name("test2").kind("bar").namespace("default").build())
            .value(ImmutableMap.of("metadata",
                ImmutableMap.of("annotations", ImmutableMap.of(HarnessAnnotations.steadyStateCondition, "false"))))
            .build());
    boolean result = executeDoStatusCheckForCustomResources(
        resources, new ProcessResult(0, new ProcessOutput("value: value".getBytes())), false);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDoStatusCheckForAllCustomResourcesIsErrorFrameworkEnabled() throws Exception {
    final String steadyStateCondition = "true";
    final Map<String, Object> resource = ImmutableMap.of("metadata",
        ImmutableMap.of("annotations", ImmutableMap.of(HarnessAnnotations.steadyStateCondition, steadyStateCondition)));
    final List<KubernetesResource> resources =
        asList(KubernetesResource.builder()
                   .resourceId(KubernetesResourceId.builder().name("test1").kind("foo").namespace("bar").build())
                   .value(resource)
                   .build(),
            KubernetesResource.builder()
                .resourceId(KubernetesResourceId.builder().name("test2").kind("bar").namespace("bar").build())
                .value(resource)
                .build(),
            KubernetesResource.builder()
                .resourceId(KubernetesResourceId.builder().name("test3").kind("boo").namespace("default").build())
                .value(resource)
                .build());

    boolean result = executeDoStatusCheckForCustomResources(
        resources, new ProcessResult(0, new ProcessOutput("value: value".getBytes())), true);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDoStatusCheckForAllCustomResourcesFailSteadyCheckIsErrorFrameworkEnabled() {
    final List<KubernetesResource> resources = singletonList(
        KubernetesResource.builder()
            .resourceId(KubernetesResourceId.builder().name("test2").kind("bar").namespace("default").build())
            .value(ImmutableMap.of("metadata",
                ImmutableMap.of("annotations", ImmutableMap.of(HarnessAnnotations.steadyStateCondition, "false"))))
            .build());
    assertThatThrownBy(()
                           -> executeDoStatusCheckForCustomResources(
                               resources, new ProcessResult(0, new ProcessOutput("value: value".getBytes())), true))
        .matches(throwable -> {
          HintException hint = ExceptionUtils.cause(HintException.class, throwable);
          ExplanationException explanationException = ExceptionUtils.cause(ExplanationException.class, throwable);
          KubernetesTaskException taskException = ExceptionUtils.cause(KubernetesTaskException.class, throwable);
          assertThat(hint).hasMessageContaining(
              format(KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_CRD_FAILED_CHECK_CONDITION, false));
          assertThat(hint.getCause())
              .hasMessageContaining(KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_CRD_FAILED_CHECK_CONTROLLER);
          assertThat(explanationException)
              .hasMessageContaining(format(KubernetesExceptionExplanation.WAIT_FOR_STEADY_STATE_CRD_FAILED, "false"));
          assertThat(taskException).hasMessageContaining(KubernetesExceptionMessages.WAIT_FOR_STEADY_STATE_FAILED);
          return true;
        });
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDoStatusCheckForAllCustomResourcesFailCliIsErrorFrameworkEnabled() {
    final List<KubernetesResource> resources = singletonList(
        KubernetesResource.builder()
            .resourceId(KubernetesResourceId.builder().name("test2").kind("bar").namespace("default").build())
            .value(ImmutableMap.of("metadata",
                ImmutableMap.of("annotations", ImmutableMap.of(HarnessAnnotations.steadyStateCondition, "true"))))
            .build());
    assertThatThrownBy(()
                           -> executeDoStatusCheckForCustomResources(resources,
                               new ProcessResult(1, new ProcessOutput("Something went wrong".getBytes())), true))
        .matches(throwable -> {
          HintException hint = ExceptionUtils.cause(HintException.class, throwable);
          ExplanationException explanationException = ExceptionUtils.cause(ExplanationException.class, throwable);
          KubernetesTaskException taskException = ExceptionUtils.cause(KubernetesTaskException.class, throwable);
          assertThat(hint).hasMessageContaining(KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_CLI_FAILED);
          assertThat(explanationException).hasMessageContaining("Something went wrong");
          assertThat(taskException).hasMessageContaining(KubernetesExceptionMessages.WAIT_FOR_STEADY_STATE_FAILED);
          return true;
        });
  }

  private boolean executeDoStatusCheckForCustomResources(List<KubernetesResource> resources,
      ProcessResult executeResult, boolean isErrorFrameworkEnabled) throws Exception {
    Kubectl client = mock(Kubectl.class);
    GetCommand getResources = spy(new GetCommand(client));
    GetCommand getEvent = spy(new GetCommand(client));
    StartedProcess startedProcess = mock(StartedProcess.class);
    Process process = mock(Process.class);

    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder().workingDirectory("pwd").build();
    doReturn(getResources).when(client).get();
    doReturn("kubectl --kubeconfig=test").when(client).command();
    doReturn(executeResult).when(getResources).execute("pwd", null, null, false, Collections.emptyMap());
    doReturn(getResources).when(getResources).resources("foo/test1");
    doReturn(getResources).when(getResources).resources("bar/test2");
    doReturn(getResources).when(getResources).resources("boo/test3");
    doReturn(getEvent).when(getResources).resources("events");
    doReturn(startedProcess)
        .when(spyK8sTaskHelperBase)
        .getEventWatchProcess(eq("pwd"), eq(getEvent), any(LogOutputStream.class), any(LogOutputStream.class));
    doReturn(process).when(startedProcess).getProcess();
    doReturn(process).when(process).destroyForcibly();

    doAnswer(invocation -> { throw new TimeoutException(); })
        .when(spyK8sTaskHelperBase)
        .doStatusCheckForCustomResources(any(Kubectl.class), any(KubernetesResourceId.class), eq("false"),
            any(K8sDelegateTaskParams.class), any(LogCallback.class), eq(isErrorFrameworkEnabled));

    return spyK8sTaskHelperBase.doStatusCheckForAllCustomResources(
        client, resources, k8sDelegateTaskParams, executionLogCallback, false, 10000, isErrorFrameworkEnabled);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldGetEmptyPodListWhenReleaseLabelIsMissing() throws Exception {
    KubernetesConfig config = KubernetesConfig.builder().build();

    List<K8sPod> podsWithReleaseNameNull =
        k8sTaskHelperBase.getPodDetails(config, "default", null, LONG_TIMEOUT_INTERVAL);
    assertThat(podsWithReleaseNameNull).isEmpty();

    List<K8sPod> podsWithReleaseNameEmpty =
        k8sTaskHelperBase.getPodDetails(config, "default", "", LONG_TIMEOUT_INTERVAL);
    assertThat(podsWithReleaseNameEmpty).isEmpty();
  }

  private Pod k8sApiMockPodWith(String uid, Map<String, String> labels, List<String> containerIds) {
    return new PodBuilder()
        .withMetadata(new ObjectMetaBuilder()
                          .withUid(uid)
                          .withName(uid + "-name")
                          .withNamespace("default")
                          .withLabels(labels)
                          .build())
        .withStatus(new PodStatusBuilder()
                        .withContainerStatuses(containerIds.stream()
                                                   .map(id
                                                       -> new ContainerStatusBuilder()
                                                              .withContainerID(id)
                                                              .withName(id + "-name")
                                                              .withImage("example:0.0.1")
                                                              .build())
                                                   .collect(Collectors.toList()))
                        .build())
        .build();
  }

  private void assertThatK8sPodHas(K8sPod pod, String uid, Map<String, String> labels, List<String> containerIds) {
    assertThat(pod.getUid()).isEqualTo(uid);
    assertThat(pod.getName()).isEqualTo(uid + "-name");
    assertThat(pod.getLabels()).isEqualTo(labels);
    assertThat(pod.getContainerList()).hasSize(containerIds.size());
    IntStream.range(0, containerIds.size()).forEach(idx -> {
      K8sContainer container = pod.getContainerList().get(idx);
      String expectedContainerId = containerIds.get(idx);
      assertThat(container.getContainerId()).isEqualTo(expectedContainerId);
      assertThat(container.getName()).isEqualTo(expectedContainerId + "-name");
      assertThat(container.getImage()).isEqualTo("example:0.0.1");
    });
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGenerateSubsetsForDestinationRule() {
    List<String> subsetNames = new ArrayList<>();
    subsetNames.add(HarnessLabelValues.trackCanary);
    subsetNames.add(HarnessLabelValues.trackStable);
    subsetNames.add(HarnessLabelValues.colorBlue);
    subsetNames.add(HarnessLabelValues.colorGreen);

    final List<Subset> result = k8sTaskHelperBase.generateSubsetsForDestinationRule(subsetNames);

    assertThat(result.size()).isEqualTo(4);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetTimeoutMillisFromMinutes() throws Exception {
    int randomPositiveInt = new Random().nextInt(1000) + 1;
    assertThat(K8sTaskHelperBase.getTimeoutMillisFromMinutes(-randomPositiveInt))
        .isEqualTo(DEFAULT_STEADY_STATE_TIMEOUT * 60 * 1000L);
    assertThat(K8sTaskHelperBase.getTimeoutMillisFromMinutes(null))
        .isEqualTo(DEFAULT_STEADY_STATE_TIMEOUT * 60 * 1000L);
    assertThat(K8sTaskHelperBase.getTimeoutMillisFromMinutes(0)).isEqualTo(DEFAULT_STEADY_STATE_TIMEOUT * 60 * 1000L);
    assertThat(K8sTaskHelperBase.getTimeoutMillisFromMinutes(1)).isEqualTo(60 * 1000L);
    assertThat(K8sTaskHelperBase.getTimeoutMillisFromMinutes(randomPositiveInt))
        .isEqualTo(randomPositiveInt * 60 * 1000L);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetEmptyLogOutputStream() throws Exception {
    assertThat(K8sTaskHelperBase.getEmptyLogOutputStream()).isInstanceOf(LogOutputStream.class);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testArrangeResourceIdsInDeletionOrder() {
    List<KubernetesResourceId> kubernetesResourceIdList = getKubernetesResourceIdList();
    kubernetesResourceIdList = k8sTaskHelperBase.arrangeResourceIdsInDeletionOrder(kubernetesResourceIdList);

    assertThat(kubernetesResourceIdList.size()).isEqualTo(4);
    assertThat(kubernetesResourceIdList.get(0).getKind()).isEqualTo(Deployment.name());
    assertThat(kubernetesResourceIdList.get(1).getKind()).isEqualTo(Service.name());
    assertThat(kubernetesResourceIdList.get(2).getKind()).isEqualTo(ConfigMap.name());
    assertThat(kubernetesResourceIdList.get(3).getKind()).isEqualTo(Namespace.name());
  }

  private List<KubernetesResourceId> getKubernetesResourceIdList() {
    List<KubernetesResourceId> kubernetesResourceIds = new ArrayList<>();
    kubernetesResourceIds.add(
        KubernetesResourceId.builder().kind(Namespace.name()).name("n1").namespace("default").build());
    kubernetesResourceIds.add(
        KubernetesResourceId.builder().kind(Deployment.name()).name("d1").namespace("default").build());
    kubernetesResourceIds.add(
        KubernetesResourceId.builder().kind(ConfigMap.name()).name("c1").namespace("default").build());
    kubernetesResourceIds.add(
        KubernetesResourceId.builder().kind(Service.name()).name("s1").namespace("default").build());
    return kubernetesResourceIds;
  }

  private List<KubernetesResourceId> getKubernetesResourceIdList(String suffix) {
    List<KubernetesResourceId> kubernetesResourceIds = new ArrayList<>();
    kubernetesResourceIds.add(
        KubernetesResourceId.builder().kind(Namespace.name()).name("n" + suffix).namespace("default").build());
    kubernetesResourceIds.add(
        KubernetesResourceId.builder().kind(Deployment.name()).name("d" + suffix).namespace("default").build());
    kubernetesResourceIds.add(
        KubernetesResourceId.builder().kind(ConfigMap.name()).name("c" + suffix).namespace("default").build());
    kubernetesResourceIds.add(
        KubernetesResourceId.builder().kind(Service.name()).name("s" + suffix).namespace("default").build());
    return kubernetesResourceIds;
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testCreateLogInfoOutputStream() throws Exception {
    List<KubernetesResourceId> resourceIds =
        ImmutableList.of(KubernetesResourceId.builder().name("app1").namespace("default").build());

    final String eventInfoFormat = "%-7s: %-4s   %s";
    LogCallback executionLogCallback = spy(getLogCallback());
    LogOutputStream logOutputStream =
        k8sTaskHelperBase.createFilteredInfoLogOutputStream(resourceIds, executionLogCallback, eventInfoFormat);
    byte[] message = "Starting app1 in default namespace\r\n".getBytes();
    logOutputStream.write(message);

    verify(executionLogCallback, times(1)).saveExecutionLog("Event  : app1   Starting app1 in default namespace", INFO);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testCreateLogErrorOutputStream() throws Exception {
    LogCallback executionLogCallback = spy(getLogCallback());
    LogOutputStream logOutputStream = k8sTaskHelperBase.createErrorLogOutputStream(executionLogCallback);
    byte[] message = "Failed to start app1 in default namespace\r\n".getBytes();
    logOutputStream.write(message);

    verify(executionLogCallback, times(1))
        .saveExecutionLog("Event  : Failed to start app1 in default namespace", ERROR);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testCreateStatusInfoLogOutputStream() throws Exception {
    LogCallback executionLogCallback = spy(getLogCallback());
    LogOutputStream logOutputStream =
        k8sTaskHelperBase.createStatusInfoLogOutputStream(executionLogCallback, "app1", "%n%-7s: %-4s   %s");
    byte[] message = "Deployed\r\n".getBytes();
    logOutputStream.write(message);

    verify(executionLogCallback, times(1)).saveExecutionLog("\nStatus : app1   Deployed", INFO);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testCreateStatusErrorLogOutputStream() throws Exception {
    LogCallback executionLogCallback = spy(getLogCallback());
    LogOutputStream logOutputStream =
        k8sTaskHelperBase.createStatusErrorLogOutputStream(executionLogCallback, "app1", "%n%-7s: %-4s   %s");
    byte[] message = "Failed\r\n".getBytes();
    logOutputStream.write(message);

    verify(executionLogCallback, times(1)).saveExecutionLog("\nStatus : app1   Failed", ERROR);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testDeleteFunctionWhenProcessResultSucceeded() throws Exception {
    List<KubernetesResourceId> resourceIds =
        ImmutableList.of(KubernetesResourceId.builder().name("app1").namespace("default").build());
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory(".")
                                                      .kubectlPath("kubectl")
                                                      .kubeconfigPath("config-path")
                                                      .build();
    Kubectl client = Kubectl.client("kubectl", "config-path");
    ProcessResult processResult = new ProcessResult(0, new ProcessOutput("Successful".getBytes()));
    ProcessResponse response = ProcessResponse.builder().processResult(processResult).build();
    doReturn(response)
        .when(spyK8sTaskHelperBase)
        .runK8sExecutable(eq(k8sDelegateTaskParams), eq(executionLogCallback), any(AbstractExecutable.class));
    spyK8sTaskHelperBase.delete(client, k8sDelegateTaskParams, resourceIds, executionLogCallback, true);
    verify(executionLogCallback, times(1)).saveExecutionLog("Done", INFO, CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testDeleteFunctionWhenProcessResultFailedCauseResourceNotFound() throws Exception {
    List<KubernetesResourceId> resourceIds =
        ImmutableList.of(KubernetesResourceId.builder().name("app1").namespace("default").build());
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory(".")
                                                      .kubectlPath("kubectl")
                                                      .kubeconfigPath("config-path")
                                                      .build();
    Kubectl client = Kubectl.client("kubectl", "config-path");
    ProcessResult processResult = new ProcessResult(1, new ProcessOutput("Resource not found".getBytes()));
    ProcessResponse response = ProcessResponse.builder().processResult(processResult).build();
    doReturn(response)
        .when(spyK8sTaskHelperBase)
        .runK8sExecutable(eq(k8sDelegateTaskParams), eq(executionLogCallback), any(AbstractExecutable.class));
    spyK8sTaskHelperBase.delete(client, k8sDelegateTaskParams, resourceIds, executionLogCallback, true);
    verify(executionLogCallback, times(1)).saveExecutionLog("Done", INFO, CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testDeleteFunctionWhenProcessResultFailedCauseOfOtherReasons() throws Exception {
    List<KubernetesResourceId> resourceIds =
        ImmutableList.of(KubernetesResourceId.builder().name("app1").namespace("default").build());
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory(".")
                                                      .kubectlPath("kubectl")
                                                      .kubeconfigPath("config-path")
                                                      .build();
    Kubectl client = Kubectl.client("kubectl", "config-path");
    ProcessResult processResult = new ProcessResult(1, new ProcessOutput("Something went wrong".getBytes()));
    ProcessResponse response = ProcessResponse.builder().processResult(processResult).build();
    doReturn(response)
        .when(spyK8sTaskHelperBase)
        .runK8sExecutable(eq(k8sDelegateTaskParams), eq(executionLogCallback), any(AbstractExecutable.class));
    spyK8sTaskHelperBase.delete(client, k8sDelegateTaskParams, resourceIds, executionLogCallback, true);
    verify(executionLogCallback, times(1)).saveExecutionLog("Failed", ERROR, CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testDeleteFunctionWhenProcessResultSucceededButCannotDenoteSuccess() throws Exception {
    List<KubernetesResourceId> resourceIds =
        ImmutableList.of(KubernetesResourceId.builder().name("app1").namespace("default").build());
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory(".")
                                                      .kubectlPath("kubectl")
                                                      .kubeconfigPath("config-path")
                                                      .build();
    Kubectl client = Kubectl.client("kubectl", "config-path");
    ProcessResult processResult = new ProcessResult(0, new ProcessOutput("Successful".getBytes()));
    ProcessResponse response = ProcessResponse.builder().processResult(processResult).build();
    doReturn(response)
        .when(spyK8sTaskHelperBase)
        .runK8sExecutable(eq(k8sDelegateTaskParams), eq(executionLogCallback), any(AbstractExecutable.class));
    spyK8sTaskHelperBase.delete(client, k8sDelegateTaskParams, resourceIds, executionLogCallback, false);
    verify(executionLogCallback, times(0)).saveExecutionLog("Done", INFO, CommandExecutionStatus.SUCCESS);
  }

  private LogCallback getLogCallback() {
    return new LogCallback() {
      @Override
      public void saveExecutionLog(String line) {}

      @Override
      public void saveExecutionLog(String line, LogLevel logLevel) {}

      @Override
      public void saveExecutionLog(String line, LogLevel logLevel, CommandExecutionStatus commandExecutionStatus) {}
    };
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetLoadBalancerEndpoint() {
    List<Integer> servicePorts = asList(38493, 80, 443);
    List<KubernetesResource> resources = asList(getServiceResource("ClusterIP"), getServiceResource("LoadBalancer"));

    doReturn(getK8sService("LoadBalancer", servicePorts, "hostname", null))
        .when(mockKubernetesContainerService)
        .getService(KUBERNETES_CONFIG, "LoadBalancer", DEFAULT);

    String endpoint = k8sTaskHelperBase.getLoadBalancerEndpoint(KUBERNETES_CONFIG, resources);
    assertThat(endpoint).isEqualTo("https://hostname/");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetLoadBalancerEndpointHttpPort() {
    List<Integer> servicePorts = asList(38493, 80);
    List<KubernetesResource> resources = singletonList(getServiceResource("LoadBalancer"));

    doReturn(getK8sService("LoadBalancer", servicePorts, "hostname", null))
        .when(mockKubernetesContainerService)
        .getService(KUBERNETES_CONFIG, "LoadBalancer", DEFAULT);

    String endpoint = k8sTaskHelperBase.getLoadBalancerEndpoint(KUBERNETES_CONFIG, resources);
    assertThat(endpoint).isEqualTo("http://hostname/");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetLoadBalancerEndpointHttpPortWithIP() {
    List<Integer> servicePorts = asList(38493, 80);
    List<KubernetesResource> resources = singletonList(getServiceResource("LoadBalancer"));

    doReturn(getK8sService("LoadBalancer", servicePorts, null, "10.33.33.33"))
        .when(mockKubernetesContainerService)
        .getService(KUBERNETES_CONFIG, "LoadBalancer", DEFAULT);

    String endpoint = k8sTaskHelperBase.getLoadBalancerEndpoint(KUBERNETES_CONFIG, resources);
    assertThat(endpoint).isEqualTo("http://10.33.33.33/");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetLoadBalancerEndpointRandomPort() {
    List<Integer> servicePorts = singletonList(38493);
    List<KubernetesResource> resources = singletonList(getServiceResource("LoadBalancer"));

    doReturn(getK8sService("LoadBalancer", servicePorts, "hostname", null))
        .when(mockKubernetesContainerService)
        .getService(KUBERNETES_CONFIG, "LoadBalancer", DEFAULT);

    String endpoint = k8sTaskHelperBase.getLoadBalancerEndpoint(KUBERNETES_CONFIG, resources);
    assertThat(endpoint).isEqualTo("hostname:38493");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetLoadBalancerEndpointNoPortsExposed() {
    List<KubernetesResource> resources = singletonList(getServiceResource("LoadBalancer"));

    doReturn(getK8sService("LoadBalancer", emptyList(), null, "33.33.33.33"))
        .when(mockKubernetesContainerService)
        .getService(KUBERNETES_CONFIG, "LoadBalancer", DEFAULT);

    String endpoint = k8sTaskHelperBase.getLoadBalancerEndpoint(KUBERNETES_CONFIG, resources);
    assertThat(endpoint).isEqualTo("33.33.33.33");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetLoadBalancerEndpointServiceNotReady() throws Exception {
    List<KubernetesResource> resources = singletonList(getServiceResource("LoadBalancer"));

    HTimeLimiterMocker.mockCallInterruptible(mockTimeLimiter).thenThrow(new UncheckedTimeoutException());

    String endpoint = k8sTaskHelperBase.getLoadBalancerEndpoint(KUBERNETES_CONFIG, resources);
    assertThat(endpoint).isNull();
  }

  private V1Service getK8sService(String type, List<Integer> ports, String hostname, String ip) {
    V1ServiceBuilder serviceBuilder = new V1ServiceBuilder()
                                          .withNewSpec()
                                          .withPorts(ports.stream()
                                                         .map(port -> new V1ServicePortBuilder().withPort(port).build())
                                                         .collect(Collectors.toList()))
                                          .withType(type)
                                          .endSpec();

    if (hostname != null || ip != null) {
      serviceBuilder.withNewStatus()
          .withNewLoadBalancer()
          .withIngress(new V1LoadBalancerIngress().hostname(hostname).ip(ip))
          .endLoadBalancer()
          .endStatus();
    }

    return serviceBuilder.build();
  }

  private KubernetesResource getServiceResource(String type) {
    return KubernetesResource.builder()
        .resourceId(KubernetesResourceId.builder().name(type).kind(Service.name()).namespace(DEFAULT).build())
        .spec(Yaml.dump(getK8sService(type, emptyList(), null, null)))
        .build();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetPodDetailsWithLabels() throws Exception {
    KubernetesConfig config = KubernetesConfig.builder().build();
    Map<String, String> labels = ImmutableMap.of("release-name", "releaseName");
    List<V1Pod> existingPods =
        asList(v1Pod(v1Metadata("pod-1", labels), v1PodStatus("pod-1-ip", v1ContainerStatus("web", "nginx"))),
            v1Pod(v1Metadata("pod-2", labels),
                v1PodStatus("pod-2-ip", v1ContainerStatus("app", "todo"), v1ContainerStatus("web", "nginx"))),
            v1Pod(v1Metadata("pod-3", labels), v1PodStatus("pod-3-ip")), v1Pod(v1Metadata("pod-4", labels), null),
            v1Pod(null, null));

    doReturn(existingPods).when(mockKubernetesContainerService).getRunningPodsWithLabels(config, "default", labels);

    List<K8sPod> pods =
        k8sTaskHelperBase.getPodDetailsWithLabels(config, "default", "releaseName", labels, LONG_TIMEOUT_INTERVAL);

    assertThat(pods).hasSize(2);
    K8sPod pod = pods.get(0);
    K8sContainer container = pods.get(0).getContainerList().get(0);
    assertThat(pod.getName()).isEqualTo("pod-1");
    assertThat(pod.getUid()).isEqualTo("pod-1");
    assertThat(pod.getLabels()).isEqualTo(labels);
    assertThat(pod.getContainerList()).hasSize(1);
    assertThat(container.getName()).isEqualTo("web");
    assertThat(container.getImage()).isEqualTo("nginx");

    pod = pods.get(1);
    assertThat(pod.getName()).isEqualTo("pod-2");
    assertThat(pod.getUid()).isEqualTo("pod-2");
    assertThat(pod.getLabels()).isEqualTo(labels);
    assertThat(pod.getContainerList()).hasSize(2);
    container = pods.get(1).getContainerList().get(0);
    assertThat(container.getName()).isEqualTo("app");
    assertThat(container.getImage()).isEqualTo("todo");
    container = pods.get(1).getContainerList().get(1);
    assertThat(container.getName()).isEqualTo("web");
    assertThat(container.getImage()).isEqualTo("nginx");
  }

  private V1ObjectMeta v1Metadata(String name, Map<String, String> labels) {
    return new V1ObjectMetaBuilder().withUid(name).withName(name).withLabels(labels).build();
  }

  private V1ContainerStatus v1ContainerStatus(String name, String image) {
    return new V1ContainerStatusBuilder().withContainerID(name).withName(name).withImage(image).build();
  }

  private V1PodStatus v1PodStatus(String podIP, V1ContainerStatus... containerStatuses) {
    return new V1PodStatusBuilder().withPodIP(podIP).withContainerStatuses(containerStatuses).build();
  }

  private V1Pod v1Pod(V1ObjectMeta metadata, V1PodStatus status) {
    return new V1PodBuilder().withMetadata(metadata).withStatus(status).build();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetPodDetailsWithTrack() throws Exception {
    KubernetesConfig config = KubernetesConfig.builder().build();
    K8sTaskHelperBase spyK8sTaskHelperBase = spy(K8sTaskHelperBase.class);
    Map<String, String> expectedLabels =
        ImmutableMap.of(HarnessLabels.releaseName, "release", HarnessLabels.track, "canary");
    doReturn(emptyList())
        .when(spyK8sTaskHelperBase)
        .getPodDetailsWithLabels(any(KubernetesConfig.class), anyString(), anyString(), anyMap(), anyLong());
    spyK8sTaskHelperBase.getPodDetailsWithTrack(config, "default", "release", "canary", DEFAULT_STEADY_STATE_TIMEOUT);

    verify(spyK8sTaskHelperBase, times(1))
        .getPodDetailsWithLabels(config, "default", "release", expectedLabels, DEFAULT_STEADY_STATE_TIMEOUT);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetPodDetailsWithColor() throws Exception {
    KubernetesConfig config = KubernetesConfig.builder().build();
    K8sTaskHelperBase spyK8sTaskHelperBase = spy(K8sTaskHelperBase.class);
    Map<String, String> expectedLabels =
        ImmutableMap.of(HarnessLabels.releaseName, "release", HarnessLabels.color, "blue");
    doReturn(emptyList())
        .when(spyK8sTaskHelperBase)
        .getPodDetailsWithLabels(any(KubernetesConfig.class), anyString(), anyString(), anyMap(), anyLong());
    spyK8sTaskHelperBase.getPodDetailsWithColor(config, "default", "release", "blue", DEFAULT_STEADY_STATE_TIMEOUT);

    verify(spyK8sTaskHelperBase, times(1))
        .getPodDetailsWithLabels(config, "default", "release", expectedLabels, DEFAULT_STEADY_STATE_TIMEOUT);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetContainerInfos() throws Exception {
    List<K8sPod> existingPods = singletonList(K8sPod.builder().name("name").podIP("pod-ip").build());
    KubernetesConfig config = KubernetesConfig.builder().build();
    K8sTaskHelperBase spyK8sTaskHelperBase = spy(K8sTaskHelperBase.class);
    Map<String, String> expectedLabels = ImmutableMap.of(HELM_RELEASE_LABEL, "release");
    doReturn(existingPods)
        .when(spyK8sTaskHelperBase)
        .getPodDetailsWithLabels(any(KubernetesConfig.class), anyString(), anyString(), anyMap(), anyLong());
    List<ContainerInfo> result =
        spyK8sTaskHelperBase.getContainerInfos(config, "release", "default", DEFAULT_STEADY_STATE_TIMEOUT);

    verify(spyK8sTaskHelperBase, times(1))
        .getPodDetailsWithLabels(config, "default", "release", expectedLabels, DEFAULT_STEADY_STATE_TIMEOUT);
    assertThat(result).hasSize(1);
    ContainerInfo containerInfo = result.get(0);
    assertThat(containerInfo.getPodName()).isEqualTo("name");
    assertThat(containerInfo.getIp()).isEqualTo("pod-ip");
    assertThat(containerInfo.getReleaseName()).isEqualTo("release");
    assertThat(containerInfo.isNewContainer()).isTrue();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldGetReleaseHistoryFromConfigMapUsingK8sClient() throws IOException {
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();
    when(mockKubernetesContainerService.fetchReleaseHistoryFromConfigMap(any(), any())).thenReturn("secret");
    String releaseHistory = k8sTaskHelperBase.getReleaseHistoryDataFromConfigMap(kubernetesConfig, "release");
    ArgumentCaptor<String> releaseArgumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockKubernetesContainerService).fetchReleaseHistoryFromConfigMap(any(), releaseArgumentCaptor.capture());

    assertThat(releaseArgumentCaptor.getValue()).isEqualTo("release");
    assertThat(releaseHistory).isEqualTo("secret");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldSaveReleaseHistoryInConfigMapUsingK8sClient() throws IOException {
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();
    when(mockKubernetesContainerService.saveReleaseHistory(any(), any(), anyString(), anyBoolean())).thenReturn(null);
    k8sTaskHelperBase.saveReleaseHistoryInConfigMap(kubernetesConfig, "release", "secret");
    ArgumentCaptor<String> releaseNameCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> releaseHistoryCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockKubernetesContainerService)
        .saveReleaseHistory(any(), releaseNameCaptor.capture(), releaseHistoryCaptor.capture(), anyBoolean());

    assertThat(releaseNameCaptor.getValue()).isEqualTo("release");
    assertThat(releaseHistoryCaptor.getValue()).isEqualTo("secret");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldSaveReleaseHistoryUsingK8sClient() throws IOException {
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();
    doReturn(null).when(mockKubernetesContainerService).saveReleaseHistory(any(), any(), anyString(), anyBoolean());
    k8sTaskHelperBase.saveReleaseHistory(kubernetesConfig, "release", "secret", true);
    ArgumentCaptor<String> releaseNameCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> releaseHistoryCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockKubernetesContainerService)
        .saveReleaseHistory(any(), releaseNameCaptor.capture(), releaseHistoryCaptor.capture(), anyBoolean());

    assertThat(releaseNameCaptor.getValue()).isEqualTo("release");
    assertThat(releaseHistoryCaptor.getValue()).isEqualTo("secret");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldGetReleaseHistoryFromSecretUsingK8sClient() throws IOException {
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();
    when(mockKubernetesContainerService.fetchReleaseHistoryFromSecrets(any(), any())).thenReturn("secret");
    String releaseHistory = k8sTaskHelperBase.getReleaseHistoryFromSecret(kubernetesConfig, "release");
    ArgumentCaptor<String> releaseArgumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockKubernetesContainerService).fetchReleaseHistoryFromSecrets(any(), releaseArgumentCaptor.capture());

    assertThat(releaseArgumentCaptor.getValue()).isEqualTo("release");
    assertThat(releaseHistory).isEqualTo("secret");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testApplyWithCustomChangeCauseAnnotation() throws Exception {
    String deploymentSpec = "apiVersion: apps/v1\n"
        + "kind: Deployment\n"
        + "metadata:\n"
        + "  name: deployment\n"
        + "  annotations:\n"
        + "    ${annotation}\n"
        + "spec:\n"
        + "  template:\n"
        + "    metadata:\n"
        + "      name: deployment\n";
    KubernetesResource resourceWithCustomChangeCause = ManifestHelper.getKubernetesResourceFromSpec(
        deploymentSpec.replace("${annotation}", "kubernetes.io/change-cause: custom value"));
    KubernetesResource resourceWithoutCustomChangeCause =
        ManifestHelper.getKubernetesResourceFromSpec(deploymentSpec.replace("${annotation}", ""));

    testApplyAndCheckRecord(resourceWithCustomChangeCause, false);
    testApplyAndCheckRecord(resourceWithoutCustomChangeCause, true);
  }

  private void testApplyAndCheckRecord(KubernetesResource resource, boolean expectedRecord) throws Exception {
    K8sTaskHelperBase spyK8sTaskHelper = spy(k8sTaskHelperBase);
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory(".")
                                                      .kubectlPath("kubectl")
                                                      .kubeconfigPath("config-path")
                                                      .build();
    Kubectl client = Kubectl.client("kubectl", "config-path");

    spyK8sTaskHelper.applyManifests(
        client, singletonList(resource), k8sDelegateTaskParams, executionLogCallback, true, null);
    ArgumentCaptor<ApplyCommand> captor = ArgumentCaptor.forClass(ApplyCommand.class);
    verify(spyK8sTaskHelper, times(1)).runK8sExecutable(any(), any(), captor.capture());

    String expectedExecutedCommand = "kubectl --kubeconfig=config-path apply --filename=manifests.yaml";
    if (expectedRecord) {
      expectedExecutedCommand += " --record";
    }

    assertThat(captor.getValue().command()).isEqualTo(expectedExecutedCommand);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testTagNewPods() {
    assertThat(k8sTaskHelperBase.tagNewPods(emptyList(), emptyList())).isEmpty();

    List<K8sPod> pods = k8sTaskHelperBase.tagNewPods(
        asList(podWithName("pod-1"), podWithName("pod-2")), asList(podWithName("old-pod-1"), podWithName("old-pod-2")));
    assertThat(pods).hasSize(2);
    assertThat(pods.stream().filter(K8sPod::isNewPod).count()).isEqualTo(2);
    assertThat(pods.stream().map(K8sPod::getName).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("pod-1", "pod-2");

    pods =
        k8sTaskHelperBase.tagNewPods(asList(podWithName("pod-1"), podWithName("pod-2")), asList(podWithName("pod-1")));
    assertThat(pods).hasSize(2);
    assertThat(pods.stream().filter(K8sPod::isNewPod).count()).isEqualTo(1);
    assertThat(pods.stream().map(K8sPod::getName).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("pod-1", "pod-2");

    pods = k8sTaskHelperBase.tagNewPods(asList(podWithName("pod-1"), podWithName("pod-2")), emptyList());
    assertThat(pods).hasSize(2);
    assertThat(pods.stream().filter(K8sPod::isNewPod).count()).isEqualTo(2);
    assertThat(pods.stream().map(K8sPod::getName).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("pod-1", "pod-2");
  }

  private K8sPod podWithName(String name) {
    return K8sPod.builder().name(name).build();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetHelmV2CommandForRender() {
    String command = k8sTaskHelperBase.getHelmCommandForRender(
        "helm", "chart_location", "test-release", "default", " -f values-0.yaml", HelmVersion.V2, null, "");
    assertThat(command).doesNotContain("$").doesNotContain("{").doesNotContain("}");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetHelmV2CommandForRenderWithCommand() {
    String command = k8sTaskHelperBase.getHelmCommandForRender("helm", "chart_location", "test-release", "default",
        " -f values-0.yaml", HelmVersion.V2, commandFlag, "config");
    assertThat(command).doesNotContain("$").doesNotContain("{").doesNotContain("}");
    assertThat(command).contains(flagValue);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetHelmV2CommandForRenderOneChartFile() {
    String command = k8sTaskHelperBase.getHelmCommandForRender("helm", "chart_location", "test-release", "default",
        " -f values-0.yaml", "template/service.yaml", HelmVersion.V2, null, "");
    assertThat(command).doesNotContain("$").doesNotContain("{").doesNotContain("}");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetHelmV2CommandForRenderOneChartFileWithCommandFlags() {
    String command = k8sTaskHelperBase.getHelmCommandForRender("helm", "chart_location", "test-release", "default",
        " -f values-0.yaml", "template/service.yaml", HelmVersion.V2, commandFlag, "config");
    assertThat(command).doesNotContain("$").doesNotContain("{").doesNotContain("}");
    assertThat(command).contains(flagValue);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetHelmV3CommandForRender() {
    String command = k8sTaskHelperBase.getHelmCommandForRender(
        "helm", "chart_location", "test-release", "default", " -f values-0.yaml", HelmVersion.V3, null, "");
    assertThat(command).doesNotContain("$").doesNotContain("{").doesNotContain("}");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetHelmV3CommandForRenderWithCommand() {
    String command = k8sTaskHelperBase.getHelmCommandForRender("helm", "chart_location", "test-release", "default",
        " -f values-0.yaml", HelmVersion.V3, commandFlag, "config");
    assertThat(command).doesNotContain("$").doesNotContain("{").doesNotContain("}");
    assertThat(command).contains(flagValue);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetHelmV3CommandForRenderOneChartFile() {
    String command = k8sTaskHelperBase.getHelmCommandForRender("helm", "chart_location", "test-release", "default",
        " -f values-0.yaml", "template/service.yaml", HelmVersion.V3, null, null);
    assertThat(command).doesNotContain("$").doesNotContain("{").doesNotContain("}");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetHelmV3CommandForRenderOneChartFileWithCommandFlag() {
    String command = k8sTaskHelperBase.getHelmCommandForRender("helm", "chart_location", "test-release", "default",
        " -f values-0.yaml", "template/service.yaml", HelmVersion.V3, commandFlag, "config");
    assertThat(command).doesNotContain("$").doesNotContain("{").doesNotContain("}");
    assertThat(command).contains(flagValue);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRenderTemplateForHelmChartFiles() throws Exception {
    K8sTaskHelperBase spyHelperBase = Mockito.spy(k8sTaskHelperBase);
    List<String> chartFiles = Arrays.asList("file.yaml");

    ProcessResult processResult = new ProcessResult(0, new ProcessOutput("".getBytes()));
    doReturn(processResult).when(spyHelperBase).executeShellCommand(any(), any(), any(), anyLong());

    final List<FileData> manifestFiles = spyHelperBase.renderTemplateForHelmChartFiles("helm", "manifest", chartFiles,
        new ArrayList<>(), "release", "namespace", executionLogCallback, HelmVersion.V3, 9000, commandFlag, "config");

    assertThat(manifestFiles.size()).isEqualTo(1);
    verify(spyHelperBase, times(1))
        .getHelmCommandForRender(
            "helm", "manifest", "release", "namespace", "", "file.yaml", HelmVersion.V3, commandFlag, "config");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testRenderTemplateHelmChartRepo() throws Exception {
    K8sTaskHelperBase spyHelperBase = Mockito.spy(k8sTaskHelperBase);

    ProcessResult processResult = new ProcessResult(0, new ProcessOutput("".getBytes()));
    doReturn(processResult).when(spyHelperBase).executeShellCommand(any(), any(), any(), anyLong());
    doReturn("").when(spyHelperBase).writeValuesToFile(any(), any());

    final List<FileData> manifestFiles = spyHelperBase.renderTemplateForHelm("helm", "./chart", new ArrayList<>(),
        "release", "namespace", executionLogCallback, HelmVersion.V3, 9000, commandFlag, "config");

    verify(spyHelperBase, times(1)).executeShellCommand(eq("./chart"), anyString(), any(), anyLong());
    assertThat(manifestFiles.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchManifestFilesAndWriteToDirectory() throws Exception {
    K8sTaskHelperBase spyHelperBase = spy(k8sTaskHelperBase);
    GitConfigDTO gitConfigDTO = GitConfigDTO.builder().build();
    List<EncryptedDataDetail> encryptionDataDetails = new ArrayList<>();
    SshSessionConfig sshSessionConfig = mock(SshSessionConfig.class);
    SSHKeySpecDTO sshKeySpecDTO = SSHKeySpecDTO.builder().build();
    GitStoreDelegateConfig storeDelegateConfig = GitStoreDelegateConfig.builder()
                                                     .branch("master")
                                                     .fetchType(FetchType.BRANCH)
                                                     .connectorName("conenctor")
                                                     .connectorId("connectorId")
                                                     .gitConfigDTO(gitConfigDTO)
                                                     .path("manifest")
                                                     .encryptedDataDetails(encryptionDataDetails)
                                                     .sshKeySpecDTO(sshKeySpecDTO)
                                                     .build();

    K8sManifestDelegateConfig manifestDelegateConfig =
        K8sManifestDelegateConfig.builder().storeDelegateConfig(storeDelegateConfig).build();

    doReturn(sshSessionConfig).when(gitDecryptionHelper).getSSHSessionConfig(sshKeySpecDTO, encryptionDataDetails);
    doReturn("files").when(spyHelperBase).getManifestFileNamesInLogFormat("manifest");

    boolean result = spyHelperBase.fetchManifestFilesAndWriteToDirectory(
        manifestDelegateConfig, "manifest", executionLogCallback, 9000L, "accountId");
    assertThat(result).isTrue();

    verify(gitDecryptionHelper, times(1)).decryptGitConfig(gitConfigDTO, encryptionDataDetails);
    verify(ngGitService, times(1))
        .downloadFiles(storeDelegateConfig, "manifest", "accountId", sshSessionConfig, gitConfigDTO);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testFetchManifestFilesAndWriteToDirectoryLocalStore() throws Exception {
    K8sTaskHelperBase spyHelperBase = spy(k8sTaskHelperBase);
    LocalFileStoreDelegateConfig localFileStoreDelegateConfig =
        LocalFileStoreDelegateConfig.builder()
            .filePaths(asList("path/to/k8s/template/deploy.yaml"))
            .manifestIdentifier("identifier")
            .manifestType("K8sManifest")
            .manifestFiles(getManifestFiles())
            .build();

    K8sManifestDelegateConfig manifestDelegateConfig =
        K8sManifestDelegateConfig.builder().storeDelegateConfig(localFileStoreDelegateConfig).build();

    assertThatCode(()
                       -> spyHelperBase.fetchManifestFilesAndWriteToDirectory(
                           manifestDelegateConfig, "manifest", executionLogCallback, 9000L, "accountId"))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testFetchManifestFilesAndWriteToDirectoryLocalStoreWithEmptyManifestFiles() throws Exception {
    K8sTaskHelperBase spyHelperBase = spy(k8sTaskHelperBase);
    LocalFileStoreDelegateConfig localFileStoreDelegateConfig =
        LocalFileStoreDelegateConfig.builder()
            .filePaths(asList("path/to/k8s/template/deploy.yaml"))
            .manifestIdentifier("identifier")
            .manifestType("K8sManifest")
            .manifestFiles(getEmptyManifestFiles())
            .build();

    K8sManifestDelegateConfig manifestDelegateConfig =
        K8sManifestDelegateConfig.builder().storeDelegateConfig(localFileStoreDelegateConfig).build();

    assertThat(spyHelperBase.fetchManifestFilesAndWriteToDirectory(
                   manifestDelegateConfig, "manifest", executionLogCallback, 9000L, "accountId"))
        .isEqualTo(true);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testFetchManifestFilesAndWriteToDirectoryOptimizedFileFetch() throws Exception {
    K8sTaskHelperBase spyHelperBase = spy(k8sTaskHelperBase);
    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .authentication(GithubAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(GithubHttpCredentialsDTO.builder()
                                                 .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                 .httpCredentialsSpec(GithubUsernamePasswordDTO.builder()
                                                                          .username("usermane")
                                                                          .passwordRef(SecretRefData.builder().build())
                                                                          .build())
                                                 .build())
                                .build())
            .apiAccess(GithubApiAccessDTO.builder().spec(GithubTokenSpecDTO.builder().build()).build())
            .build();
    List<EncryptedDataDetail> encryptionDataDetails = new ArrayList<>();
    List<EncryptedDataDetail> apiAuthEncryptedDataDetails = new ArrayList<>();
    SshSessionConfig sshSessionConfig = mock(SshSessionConfig.class);
    SSHKeySpecDTO sshKeySpecDTO = SSHKeySpecDTO.builder().build();
    GitStoreDelegateConfig storeDelegateConfig = GitStoreDelegateConfig.builder()
                                                     .branch("master")
                                                     .fetchType(FetchType.BRANCH)
                                                     .connectorName("conenctor")
                                                     .connectorId("connectorId")
                                                     .gitConfigDTO(githubConnectorDTO)
                                                     .path("manifest")
                                                     .encryptedDataDetails(encryptionDataDetails)
                                                     .apiAuthEncryptedDataDetails(apiAuthEncryptedDataDetails)
                                                     .sshKeySpecDTO(sshKeySpecDTO)
                                                     .optimizedFilesFetch(true)
                                                     .build();

    K8sManifestDelegateConfig manifestDelegateConfig =
        K8sManifestDelegateConfig.builder().storeDelegateConfig(storeDelegateConfig).build();

    doReturn("files").when(spyHelperBase).getManifestFileNamesInLogFormat("manifest");

    boolean result = spyHelperBase.fetchManifestFilesAndWriteToDirectory(
        manifestDelegateConfig, "manifest", executionLogCallback, 9000L, "accountId");
    assertThat(result).isTrue();

    verify(mockSecretDecryptionService, times(1)).decrypt(any(), eq(apiAuthEncryptedDataDetails));
    verify(scmFetchFilesHelper, times(1)).downloadFilesUsingScm("manifest", storeDelegateConfig, executionLogCallback);

    verify(gitDecryptionHelper, times(0)).decryptGitConfig(any(), eq(encryptionDataDetails));
    verify(ngGitService, times(0))
        .downloadFiles(eq(storeDelegateConfig), eq("manifest"), eq("accountId"), eq(sshSessionConfig), any());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchManifestFilesAndWriteToDirectoryFailed() {
    GitConfigDTO gitConfigDTO = GitConfigDTO.builder().build();
    List<EncryptedDataDetail> encryptionDataDetails = new ArrayList<>();
    GitStoreDelegateConfig storeDelegateConfig =
        GitStoreDelegateConfig.builder().gitConfigDTO(gitConfigDTO).encryptedDataDetails(encryptionDataDetails).build();

    K8sManifestDelegateConfig manifestDelegateConfig =
        K8sManifestDelegateConfig.builder().storeDelegateConfig(storeDelegateConfig).build();

    doAnswer(invocation -> { throw new RuntimeException("unable to decrypt"); })
        .when(gitDecryptionHelper)
        .decryptGitConfig(gitConfigDTO, encryptionDataDetails);

    assertThatThrownBy(()
                           -> k8sTaskHelperBase.fetchManifestFilesAndWriteToDirectory(
                               manifestDelegateConfig, "manifest", executionLogCallback, 9000L, "accountId"))
        .isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchManifestFilesAndWriteToDirectoryHttpHelm() throws Exception {
    K8sTaskHelperBase spyTaskHelperBase = spy(k8sTaskHelperBase);
    HttpHelmStoreDelegateConfig httpStoreDelegateConfig = HttpHelmStoreDelegateConfig.builder()
                                                              .repoName("repoName")
                                                              .repoDisplayName("Repo Name")
                                                              .httpHelmConnector(HttpHelmConnectorDTO.builder().build())
                                                              .build();

    HelmChartManifestDelegateConfig manifestDelegateConfig = HelmChartManifestDelegateConfig.builder()
                                                                 .chartName("chartName")
                                                                 .chartVersion("1.0.0")
                                                                 .storeDelegateConfig(httpStoreDelegateConfig)
                                                                 .helmVersion(HelmVersion.V3)
                                                                 .build();

    doReturn("list of files").when(spyTaskHelperBase).getManifestFileNamesInLogFormat("manifest");

    boolean result = spyTaskHelperBase.fetchManifestFilesAndWriteToDirectory(
        manifestDelegateConfig, "manifest", executionLogCallback, 9000L, "accountId");

    assertThat(result).isTrue();
    verify(helmTaskHelperBase, times(1)).initHelm("manifest", HelmVersion.V3, 9000L);
    verify(helmTaskHelperBase, times(1))
        .printHelmChartInfoWithVersionInExecutionLogs("manifest", manifestDelegateConfig, executionLogCallback);
    verify(helmTaskHelperBase, times(1)).downloadChartFilesFromHttpRepo(manifestDelegateConfig, "manifest", 9000L);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testFetchManifestFilesAndWriteToDirectoryHttpHelmEnvVar() throws Exception {
    K8sTaskHelperBase spyTaskHelperBase = spy(k8sTaskHelperBase);
    HttpHelmStoreDelegateConfig httpStoreDelegateConfig = HttpHelmStoreDelegateConfig.builder()
                                                              .repoName("repoName")
                                                              .repoDisplayName("Repo Name")
                                                              .httpHelmConnector(HttpHelmConnectorDTO.builder().build())
                                                              .build();

    HelmChartManifestDelegateConfig manifestDelegateConfig = HelmChartManifestDelegateConfig.builder()
                                                                 .chartName("chartName")
                                                                 .chartVersion("1.0.0")
                                                                 .storeDelegateConfig(httpStoreDelegateConfig)
                                                                 .helmVersion(HelmVersion.V3)
                                                                 .build();

    doReturn("/helm-working-dir/").when(helmTaskHelperBase).getHelmLocalRepositoryPath();
    doReturn(true).when(helmTaskHelperBase).isHelmLocalRepoSet();
    doReturn("/helm-working-dir/repoName")
        .when(helmTaskHelperBase)
        .getHelmLocalRepositoryCompletePath(any(), any(), any());
    doNothing()
        .when(helmTaskHelperBase)
        .populateChartToLocalHelmRepo(eq(manifestDelegateConfig), anyLong(), any(), any());
    doNothing().when(helmTaskHelperBase).createAndWaitForDir(any());
    doNothing().when(spyTaskHelperBase).copyHelmChartFolderToWorkingDir(any(), any());
    doReturn("list of files").when(spyTaskHelperBase).getManifestFileNamesInLogFormat("manifest");

    boolean result = spyTaskHelperBase.fetchManifestFilesAndWriteToDirectory(
        manifestDelegateConfig, "manifest", executionLogCallback, 9000L, "accountId");

    assertThat(result).isTrue();
    verify(spyTaskHelperBase, times(1)).getManifestFileNamesInLogFormat(anyString());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchManifestFilesAndWriteToDirectoryS3Helm() throws Exception {
    S3HelmStoreDelegateConfig s3HelmStoreDelegateConfig = S3HelmStoreDelegateConfig.builder().build();
    testFetchManifestFilesAndWriteToDirectoryUsingChartMuseum(s3HelmStoreDelegateConfig);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchManifestFilesAndWriteToDirectoryGCSHelm() throws Exception {
    GcsHelmStoreDelegateConfig gcsHelmStoreDelegateConfig = GcsHelmStoreDelegateConfig.builder().build();
    testFetchManifestFilesAndWriteToDirectoryUsingChartMuseum(gcsHelmStoreDelegateConfig);
  }

  private void testFetchManifestFilesAndWriteToDirectoryUsingChartMuseum(StoreDelegateConfig storeDelegateConfig)
      throws Exception {
    K8sTaskHelperBase spyTaskHelperBase = spy(k8sTaskHelperBase);

    HelmChartManifestDelegateConfig manifestDelegateConfig = HelmChartManifestDelegateConfig.builder()
                                                                 .chartName("chartName")
                                                                 .chartVersion("1.0.0")
                                                                 .storeDelegateConfig(storeDelegateConfig)
                                                                 .helmVersion(HelmVersion.V2)
                                                                 .build();

    doReturn("list of files").when(spyTaskHelperBase).getManifestFileNamesInLogFormat("manifest");

    boolean result = spyTaskHelperBase.fetchManifestFilesAndWriteToDirectory(
        manifestDelegateConfig, "manifest", executionLogCallback, 9000L, "accountId");

    assertThat(result).isTrue();
    verify(helmTaskHelperBase, times(1)).initHelm("manifest", HelmVersion.V2, 9000L);
    verify(helmTaskHelperBase, times(1))
        .printHelmChartInfoWithVersionInExecutionLogs("manifest", manifestDelegateConfig, executionLogCallback);
    verify(helmTaskHelperBase, times(1)).downloadChartFilesUsingChartMuseum(manifestDelegateConfig, "manifest", 9000L);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRenderTemplateHelmChart() throws Exception {
    testRenderTemplateWithHelmChart(HelmChartManifestDelegateConfig.builder()
                                        .helmVersion(HelmVersion.V3)
                                        .storeDelegateConfig(GitStoreDelegateConfig.builder().build())
                                        .helmCommandFlag(TEST_HELM_COMMAND)
                                        .subChartPath("")
                                        .build(),
        "manifest", "manifest");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRenderTemplateHelmChartHttpRepo() throws Exception {
    testRenderTemplateWithHelmChart(HelmChartManifestDelegateConfig.builder()
                                        .helmVersion(HelmVersion.V3)
                                        .storeDelegateConfig(HttpHelmStoreDelegateConfig.builder().build())
                                        .helmCommandFlag(TEST_HELM_COMMAND)
                                        .chartName("chart-name")
                                        .subChartPath("")
                                        .build(),
        "manifest", "manifest/chart-name");

    testRenderTemplateWithHelmSubChart(HelmChartManifestDelegateConfig.builder()
                                           .helmVersion(HelmVersion.V3)
                                           .storeDelegateConfig(HttpHelmStoreDelegateConfig.builder().build())
                                           .helmCommandFlag(HELM_DEPENDENCY_UPDATE)
                                           .chartName("chart-name")
                                           .subChartPath("charts/first-child")
                                           .build(),
        "manifest", "manifest/chart-name/charts/first-child");
  }

  private void testRenderTemplateWithHelmChart(ManifestDelegateConfig manifestDelegateConfig, String manifestDirectory,
      String expectedManifestDirectory) throws Exception {
    K8sTaskHelperBase spyHelper = spy(k8sTaskHelperBase);
    String helmPath = "/usr/bin/helm";
    List<String> valuesList = new ArrayList<>();
    List<FileData> renderedFiles = new ArrayList<>();

    doReturn(renderedFiles)
        .when(spyHelper)
        .renderTemplateForHelm(helmPath, expectedManifestDirectory, valuesList, "release", "namespace",
            executionLogCallback, HelmVersion.V3, 600000, TEST_HELM_COMMAND, "dir/config");

    List<FileData> result = spyHelper.renderTemplate(
        K8sDelegateTaskParams.builder().helmPath(helmPath).kubeconfigPath("config").workingDirectory("dir").build(),
        manifestDelegateConfig, manifestDirectory, valuesList, "release", "namespace", executionLogCallback, 10);

    assertThat(result).isEqualTo(renderedFiles);
    verify(spyHelper, times(1))
        .renderTemplateForHelm(helmPath, expectedManifestDirectory, valuesList, "release", "namespace",
            executionLogCallback, HelmVersion.V3, 600000, TEST_HELM_COMMAND, "dir/config");
  }

  private void testRenderTemplateWithHelmSubChart(ManifestDelegateConfig manifestDelegateConfig,
      String manifestDirectory, String expectedManifestDirectory) throws Exception {
    K8sTaskHelperBase spyHelper = spy(k8sTaskHelperBase);
    String helmPath = "/usr/bin/helm";
    List<String> valuesList = new ArrayList<>();
    List<FileData> renderedFiles = new ArrayList<>();

    doReturn(renderedFiles)
        .when(spyHelper)
        .renderTemplateForHelm(helmPath, expectedManifestDirectory, valuesList, "release", "namespace",
            executionLogCallback, HelmVersion.V3, 600000, HELM_DEPENDENCY_UPDATE, "dir/config");

    List<FileData> result = spyHelper.renderTemplate(
        K8sDelegateTaskParams.builder().helmPath(helmPath).workingDirectory("dir").kubeconfigPath("config").build(),
        manifestDelegateConfig, manifestDirectory, valuesList, "release", "namespace", executionLogCallback, 10);

    assertThat(result).isEqualTo(renderedFiles);
    verify(spyHelper, times(1))
        .renderTemplateForHelm(helmPath, expectedManifestDirectory, valuesList, "release", "namespace",
            executionLogCallback, HelmVersion.V3, 600000, HELM_DEPENDENCY_UPDATE, "dir/config");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRenderTemplateForGivenFiles() throws Exception {
    K8sTaskHelperBase spyHelper = spy(k8sTaskHelperBase);
    String helmPath = "/usr/bin/helm";
    List<String> valuesList = new ArrayList<>();
    List<String> filesToRender = Arrays.asList("file1", "file2");
    HelmCommandFlag helmCommandFlag = HelmCommandFlag.builder().valueMap(ImmutableMap.of(TEMPLATE, "--debug")).build();
    ManifestDelegateConfig manifestDelegateConfig = HelmChartManifestDelegateConfig.builder()
                                                        .helmVersion(HelmVersion.V3)
                                                        .storeDelegateConfig(GitStoreDelegateConfig.builder().build())
                                                        .helmCommandFlag(helmCommandFlag)
                                                        .build();
    List<FileData> renderedFiles = new ArrayList<>();

    doReturn(renderedFiles)
        .when(spyHelper)
        .renderTemplateForHelmChartFiles(helmPath, "manifest", filesToRender, valuesList, "release", "namespace",
            executionLogCallback, HelmVersion.V3, 600000, helmCommandFlag, "dir/config");

    List<FileData> result = spyHelper.renderTemplateForGivenFiles(
        K8sDelegateTaskParams.builder().helmPath(helmPath).kubeconfigPath("config").workingDirectory("dir").build(),
        manifestDelegateConfig, "manifest", filesToRender, valuesList, "release", "namespace", executionLogCallback, 10,
        false);

    assertThat(result).isEqualTo(renderedFiles);
    verify(spyHelper, times(1))
        .renderTemplateForHelmChartFiles(helmPath, "manifest", filesToRender, valuesList, "release", "namespace",
            executionLogCallback, HelmVersion.V3, 600000, helmCommandFlag, "dir/config");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testRenderTemplateKustomize() throws Exception {
    String kustomizePath = "/usr/bin/kustomize";
    String kustomizePluginPath = "/usr/bin/kustomize/plugin";
    List<String> valuesList = new ArrayList<>();
    ManifestDelegateConfig manifestDelegateConfig =
        KustomizeManifestDelegateConfig.builder()
            .pluginPath(kustomizePluginPath)
            .storeDelegateConfig(GitStoreDelegateConfig.builder().paths(Arrays.asList("kustomize-dir")).build())
            .kustomizeDirPath("kustomize-dir-path")
            .build();
    K8sDelegateTaskParams delegateTaskParams =
        K8sDelegateTaskParams.builder().kustomizeBinaryPath(kustomizePath).build();
    List<FileData> renderedFiles = new ArrayList<>();
    doReturn(renderedFiles)
        .when(kustomizeTaskHelper)
        .build("manifest", kustomizePath, kustomizePluginPath, "kustomize-dir-path", executionLogCallback, null);

    List<FileData> result = k8sTaskHelperBase.renderTemplate(delegateTaskParams, manifestDelegateConfig, "manifest",
        valuesList, "release", "namespace", executionLogCallback, 10);

    assertThat(result).isEqualTo(renderedFiles);
    verify(kustomizeTaskHelper, times(1))
        .build("manifest", kustomizePath, kustomizePluginPath, "kustomize-dir-path", executionLogCallback, null);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testRenderTemplateForGivenFilesKustomize() throws Exception {
    String kustomizePath = "/usr/bin/kustomize";
    String kustomizePluginPath = "/usr/bin/kustomize/plugin";
    List<String> valuesList = new ArrayList<>();
    List<String> fileList = ImmutableList.of("deploy.yaml");
    ManifestDelegateConfig manifestDelegateConfig = KustomizeManifestDelegateConfig.builder()
                                                        .pluginPath(kustomizePluginPath)
                                                        .storeDelegateConfig(GitStoreDelegateConfig.builder().build())
                                                        .build();
    K8sDelegateTaskParams delegateTaskParams =
        K8sDelegateTaskParams.builder().kustomizeBinaryPath(kustomizePath).build();
    List<FileData> renderedFiles = ImmutableList.of(FileData.builder().fileName("deploy.yaml").build());
    doReturn(renderedFiles)
        .when(kustomizeTaskHelper)
        .buildForApply(
            kustomizePath, kustomizePluginPath, "manifest", fileList, true, emptyList(), executionLogCallback, null);

    List<FileData> result = k8sTaskHelperBase.renderTemplateForGivenFiles(delegateTaskParams, manifestDelegateConfig,
        "manifest", fileList, valuesList, "release", "namespace", executionLogCallback, 10, false);

    assertThat(result).isEqualTo(renderedFiles);
    verify(kustomizeTaskHelper, times(1))
        .buildForApply(
            kustomizePath, kustomizePluginPath, "manifest", fileList, true, emptyList(), executionLogCallback, null);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testRenderTemplateOpenshift() throws Exception {
    String ocPath = "/usr/bin/openshift";
    String ocTemplatePath = "/usr/bin/openshift/template";
    List<String> valuesList = new ArrayList<>();
    ManifestDelegateConfig manifestDelegateConfig =
        OpenshiftManifestDelegateConfig.builder()
            .storeDelegateConfig(GitStoreDelegateConfig.builder().paths(Arrays.asList(ocTemplatePath)).build())
            .build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().ocPath(ocPath).build();
    List<FileData> renderedFiles = new ArrayList<>();
    doReturn(renderedFiles)
        .when(openShiftDelegateService)
        .processTemplatization("manifest", ocPath, ocTemplatePath, executionLogCallback, valuesList);

    List<FileData> result = k8sTaskHelperBase.renderTemplate(delegateTaskParams, manifestDelegateConfig, "manifest",
        valuesList, "release", "namespace", executionLogCallback, 10);

    assertThat(result).isEqualTo(renderedFiles);
    verify(openShiftDelegateService, times(1))
        .processTemplatization("manifest", ocPath, ocTemplatePath, executionLogCallback, valuesList);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testRenderTemplateOpenshiftHarnessStore() throws Exception {
    String ocPath = "/usr/bin/openshift";
    String ocTemplatePath = "/usr/bin/openshift/template/openshiftTemplate.yaml";
    List<String> valuesList = new ArrayList<>();
    List<ManifestFiles> manifestFileList = asList(ManifestFiles.builder().filePath(ocTemplatePath).build());
    ManifestDelegateConfig manifestDelegateConfig =
        OpenshiftManifestDelegateConfig.builder()
            .storeDelegateConfig(LocalFileStoreDelegateConfig.builder().manifestFiles(manifestFileList).build())
            .build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().ocPath(ocPath).build();
    List<FileData> renderedFiles = new ArrayList<>();
    doReturn(renderedFiles)
        .when(openShiftDelegateService)
        .processTemplatization("manifest", ocPath, ocTemplatePath.substring(1), executionLogCallback, valuesList);

    List<FileData> result = k8sTaskHelperBase.renderTemplate(delegateTaskParams, manifestDelegateConfig, "manifest",
        valuesList, "release", "namespace", executionLogCallback, 10);

    assertThat(result).isEqualTo(renderedFiles);
    verify(openShiftDelegateService, times(1))
        .processTemplatization("manifest", ocPath, ocTemplatePath.substring(1), executionLogCallback, valuesList);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testRenderTemplateOpenshiftCustomRemoteStore() throws Exception {
    String ocPath = "/usr/bin/openshift";
    String ocTemplatePath = "/usr/bin/openshift/template.yaml";
    List<String> valuesList = new ArrayList<>();
    ManifestDelegateConfig manifestDelegateConfig =
        OpenshiftManifestDelegateConfig.builder()
            .storeDelegateConfig(
                CustomRemoteStoreDelegateConfig.builder()
                    .customManifestSource(CustomManifestSource.builder().filePaths(asList(ocTemplatePath)).build())
                    .build())
            .build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().ocPath(ocPath).build();
    List<FileData> renderedFiles = new ArrayList<>();
    String templateFileName = getFileName(ocTemplatePath);
    doReturn(renderedFiles)
        .when(openShiftDelegateService)
        .processTemplatization("manifest", ocPath, templateFileName, executionLogCallback, valuesList);

    List<FileData> result = k8sTaskHelperBase.renderTemplate(delegateTaskParams, manifestDelegateConfig, "manifest",
        valuesList, "release", "namespace", executionLogCallback, 10);

    assertThat(result).isEqualTo(renderedFiles);
    verify(openShiftDelegateService, times(1))
        .processTemplatization("manifest", ocPath, templateFileName, executionLogCallback, valuesList);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetResourcesToBePrunedInOrder() throws Exception {
    List<KubernetesResource> currentResource = getKubernetesResourcesFromFiles(singletonList("/k8s/deployment.yaml"));

    List<String> previousResourcesYamls = asList(
        "/k8s/podWithSkipPruneAnnotation.yaml", "/k8s/deployment.yaml", "/k8s/configMap.yaml", "/k8s/service.yaml");
    List<KubernetesResource> previousResource = getKubernetesResourcesFromFiles(previousResourcesYamls);

    List<KubernetesResourceId> resourcesToBePrunedInOrder =
        k8sTaskHelperBase.getResourcesToBePrunedInOrder(previousResource, currentResource);

    assertThat(resourcesToBePrunedInOrder).hasSize(2);
    assertThat(resourcesToBePrunedInOrder.stream().map(KubernetesResourceId::getKind).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("Service", "ConfigMap");
    assertThat(resourcesToBePrunedInOrder.stream().map(KubernetesResourceId::getKind).collect(Collectors.toList()))
        .doesNotContain("Deployment", "Pod");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteDeleteHandlingPartialExecution() throws Exception {
    K8sTaskHelperBase spyK8sHelperBase = spy(k8sTaskHelperBase);
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder().build();
    List<KubernetesResourceId> resourceIds = new ArrayList<>();
    resourceIds.add(KubernetesResourceId.builder().name("resource1").build());
    resourceIds.add(KubernetesResourceId.builder().name("resource2").build());
    LogCallback logCallback = mock(LogCallback.class);
    Kubectl client = mock(Kubectl.class);
    ProcessOutput output = new ProcessOutput("output".getBytes());
    ProcessResult processResultFail = new ProcessResult(1, output);
    ProcessResult processResultSuccess = new ProcessResult(0, output);
    ProcessResponse failedResponse = ProcessResponse.builder().processResult(processResultFail).build();
    ProcessResponse successfulResponse = ProcessResponse.builder().processResult(processResultSuccess).build();

    doReturn(failedResponse).doReturn(successfulResponse).when(spyK8sHelperBase).runK8sExecutable(any(), any(), any());
    doReturn(new DeleteCommand(client)).when(client).delete();

    List<KubernetesResourceId> deletedResources = spyK8sHelperBase.executeDeleteHandlingPartialExecution(
        client, k8sDelegateTaskParams, resourceIds, logCallback, false);

    assertThat(deletedResources).hasSize(1);
    assertThat(deletedResources.get(0).getName()).isEqualTo("resource2");
  }

  private List<KubernetesResource> getKubernetesResourcesFromFiles(List<String> fileNames) {
    List<KubernetesResource> resources = new ArrayList<>();
    fileNames.forEach(filename -> {
      URL url = this.getClass().getResource(filename);
      String fileContents = null;
      try {
        fileContents = Resources.toString(url, Charsets.UTF_8);
      } catch (IOException e) {
        e.printStackTrace();
      }
      resources.add(processYaml(fileContents).get(0));
    });
    return resources;
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetErrorMessageIfProcessFailed() {
    ProcessResult mockProcessResult = mock(ProcessResult.class);
    ProcessOutput mockProcessOutput = mock(ProcessOutput.class);
    doReturn(mockProcessOutput).doReturn(mockProcessOutput).when(mockProcessResult).getOutput();
    doReturn("").doReturn("foo").when(mockProcessOutput).getUTF8();
    assertThat(k8sTaskHelperBase.getErrorMessageIfProcessFailed("base", mockProcessResult)).isEqualTo("base");
    assertThat(k8sTaskHelperBase.getErrorMessageIfProcessFailed("base", mockProcessResult)).isEqualTo("base Error foo");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testValidateK8sConnectionBadCredentials() {
    KubernetesClusterConfigDTO clusterConfigDTO =
        KubernetesClusterConfigDTO.builder()
            .credential(KubernetesCredentialDTO.builder()
                            .kubernetesCredentialType(MANUAL_CREDENTIALS)
                            .config(KubernetesClusterDetailsDTO.builder()
                                        .auth(KubernetesAuthDTO.builder().authType(USER_PASSWORD).build())
                                        .build())
                            .build())
            .build();

    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();

    doReturn(kubernetesConfig)
        .when(mockK8sYamlToDelegateDTOMapper)
        .createKubernetesConfigFromClusterConfig(clusterConfigDTO);

    InvalidRequestException exception = new InvalidRequestException("Unable to retrieve k8s version. Code: 401");

    doAnswer(invocation -> { throw exception; })
        .when(mockKubernetesContainerService)
        .validateCredentials(kubernetesConfig);
    assertThatThrownBy(() -> k8sTaskHelperBase.validate(clusterConfigDTO, emptyList())).isSameAs(exception);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testValidateK8sConnectionCorrectCredentials() {
    KubernetesClusterConfigDTO clusterConfigDTO =
        KubernetesClusterConfigDTO.builder()
            .credential(KubernetesCredentialDTO.builder()
                            .kubernetesCredentialType(MANUAL_CREDENTIALS)
                            .config(KubernetesClusterDetailsDTO.builder()
                                        .auth(KubernetesAuthDTO.builder().authType(SERVICE_ACCOUNT).build())
                                        .build())
                            .build())
            .build();

    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();

    doReturn(kubernetesConfig)
        .when(mockK8sYamlToDelegateDTOMapper)
        .createKubernetesConfigFromClusterConfig(clusterConfigDTO);

    doNothing().when(mockKubernetesContainerService).validateCredentials(kubernetesConfig);

    ConnectorValidationResult connectorValidationResult = k8sTaskHelperBase.validate(clusterConfigDTO, emptyList());

    assertThat(connectorValidationResult.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testFetchTokenReviewStatus_InClusterDelegate() throws Exception {
    final String username = "system:serviceaccount:harness-delegate:default";

    doReturn(KubernetesConfig.builder().build())
        .when(mockK8sYamlToDelegateDTOMapper)
        .createKubernetesConfigFromClusterConfig(eq(inClusterDelegateK8sConfig()));

    when(mockKubernetesContainerService.fetchTokenReviewStatus(any()))
        .thenReturn(new V1TokenReviewStatusBuilder().withNewUser().withUsername(username).endUser().build());

    V1TokenReviewStatus v1TokenReviewStatus =
        k8sTaskHelperBase.fetchTokenReviewStatus(inClusterDelegateK8sConfig(), null);

    assertThat(v1TokenReviewStatus).isNotNull();
    assertThat(v1TokenReviewStatus.getUser()).isNotNull();
    assertThat(v1TokenReviewStatus.getUser().getUsername()).isEqualTo(username);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testFetchTokenReviewStatus_ManualCredential() throws Exception {
    final String username = "system:serviceaccount:harness-delegate:default";

    doReturn(KubernetesConfig.builder().build())
        .when(mockK8sYamlToDelegateDTOMapper)
        .createKubernetesConfigFromClusterConfig(eq(manualK8sConfig()));

    when(mockKubernetesContainerService.fetchTokenReviewStatus(any()))
        .thenReturn(new V1TokenReviewStatusBuilder().withNewUser().withUsername(username).endUser().build());

    V1TokenReviewStatus v1TokenReviewStatus = k8sTaskHelperBase.fetchTokenReviewStatus(
        manualK8sConfig(), ImmutableList.of(EncryptedDataDetail.builder().fieldName("accessKey").build()));

    assertThat(v1TokenReviewStatus).isNotNull();
    assertThat(v1TokenReviewStatus.getUser()).isNotNull();
    assertThat(v1TokenReviewStatus.getUser().getUsername()).isEqualTo(username);
  }

  private Object[] badUrlExceptions() {
    return new Object[] {
        new UrlNotProvidedException("URL not provided"), new UrlNotReachableException("URL not reachable")};
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testCEKubernetesClusterWithException() {
    ConnectorConfigDTO connector =
        KubernetesClusterConfigDTO.builder()
            .credential(KubernetesCredentialDTO.builder()
                            .kubernetesCredentialType(KubernetesCredentialType.INHERIT_FROM_DELEGATE)
                            .build())
            .build();
    doReturn(KUBERNETES_CONFIG)
        .when(mockK8sYamlToDelegateDTOMapper)
        .createKubernetesConfigFromClusterConfig(any(KubernetesClusterConfigDTO.class));
    doReturn(ErrorDetail.builder().message(DEFAULT).build())
        .when(ngErrorHelper)
        .createErrorDetail(nullable(String.class));
    doReturn(DEFAULT).when(ngErrorHelper).getErrorSummary(nullable(String.class));
    doAnswer(invocation -> { throw new ApiException(); })
        .when(mockKubernetesContainerService)
        .validateMetricsServer(any(KubernetesConfig.class));

    ConnectorValidationResult result =
        k8sTaskHelperBase.validateCEKubernetesCluster(connector, DEFAULT, emptyList(), emptyList());

    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.FAILURE);
    assertThat(result.getErrorSummary()).isEqualTo(DEFAULT);
    assertThat(result.getErrors().get(0).getMessage()).isEqualTo(DEFAULT);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testCEKubernetesClusterWithErrors() throws Exception {
    ConnectorConfigDTO connector =
        KubernetesClusterConfigDTO.builder()
            .credential(KubernetesCredentialDTO.builder()
                            .kubernetesCredentialType(KubernetesCredentialType.INHERIT_FROM_DELEGATE)
                            .build())
            .build();
    CEK8sDelegatePrerequisite.MetricsServerCheck metricsServerCheck =
        CEK8sDelegatePrerequisite.MetricsServerCheck.builder().isInstalled(false).build();
    CEK8sDelegatePrerequisite.Rule rule = CEK8sDelegatePrerequisite.Rule.builder().build();

    doReturn(KUBERNETES_CONFIG)
        .when(mockK8sYamlToDelegateDTOMapper)
        .createKubernetesConfigFromClusterConfig(any(KubernetesClusterConfigDTO.class));
    doReturn(metricsServerCheck)
        .when(mockKubernetesContainerService)
        .validateMetricsServer(any(KubernetesConfig.class));
    doReturn(singletonList(rule))
        .when(mockKubernetesContainerService)
        .validateCEResourcePermissions(any(KubernetesConfig.class));
    doReturn(emptyList())
        .when(mockKubernetesContainerService)
        .validateLightwingResourceExists(any(KubernetesConfig.class));
    doReturn(emptyList())
        .when(mockKubernetesContainerService)
        .validateLightwingResourcePermissions(any(KubernetesConfig.class));

    ConnectorValidationResult result = k8sTaskHelperBase.validateCEKubernetesCluster(
        connector, DEFAULT, emptyList(), singletonList(CEFeatures.OPTIMIZATION));

    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.FAILURE);
    assertThat(result.getErrors().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testCEKubernetesCluster() {
    ConnectorConfigDTO connector =
        KubernetesClusterConfigDTO.builder()
            .credential(KubernetesCredentialDTO.builder()
                            .kubernetesCredentialType(KubernetesCredentialType.INHERIT_FROM_DELEGATE)
                            .build())
            .build();
    CEK8sDelegatePrerequisite.MetricsServerCheck metricsServerCheck =
        CEK8sDelegatePrerequisite.MetricsServerCheck.builder().isInstalled(true).build();

    doReturn(KUBERNETES_CONFIG)
        .when(mockK8sYamlToDelegateDTOMapper)
        .createKubernetesConfigFromClusterConfig(any(KubernetesClusterConfigDTO.class));
    doReturn(metricsServerCheck)
        .when(mockKubernetesContainerService)
        .validateMetricsServer(any(KubernetesConfig.class));
    doReturn(emptyList())
        .when(mockKubernetesContainerService)
        .validateCEResourcePermissions(any(KubernetesConfig.class));

    ConnectorValidationResult result =
        k8sTaskHelperBase.validateCEKubernetesCluster(connector, DEFAULT, emptyList(), emptyList());

    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetDeploymentContainingTrackStableSelector() {
    List<V1Deployment> deployments = getV1DeploymentTestData();
    when(mockKubernetesContainerService.getDeployment(any(KubernetesConfig.class), anyString(), anyString()))
        .thenReturn(deployments.get(0))
        .thenReturn(deployments.get(1))
        .thenReturn(deployments.get(2))
        .thenReturn(deployments.get(3));
    k8sTaskHelperBase.getDeploymentContainingTrackStableSelector(KubernetesConfig.builder().build(),
        generateInputWorkloadTestResource(), Maps.immutableEntry("harness.io/track", "stable"));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDeleteSkippedManifestFiles() throws Exception {
    String manifestDir = Files.createTempDirectory("testDeleteSkippedManifestFiles").toString();
    try {
      prepareTestRandomByteManifestFile(
          Paths.get(manifestDir, "test1.yaml").toString(), SKIP_FILE_FOR_DEPLOY_PLACEHOLDER_TEXT);
      prepareTestRandomByteManifestFile(Paths.get(manifestDir, "test2").toString(), null);
      prepareTestRandomByteManifestFile(Paths.get(manifestDir, "test3").toString(), null);
      prepareTestRandomByteManifestFile(
          Paths.get(manifestDir, "test5.yaml").toString(), SKIP_FILE_FOR_DEPLOY_PLACEHOLDER_TEXT);
      prepareTestRandomByteManifestFile(
          Paths.get(manifestDir, "sub/test1.yaml").toString(), SKIP_FILE_FOR_DEPLOY_PLACEHOLDER_TEXT);
      prepareTestRandomByteManifestFile(
          Paths.get(manifestDir, "sub/path/test2.yaml").toString(), SKIP_FILE_FOR_DEPLOY_PLACEHOLDER_TEXT);
      prepareTestRandomByteManifestFile(Paths.get(manifestDir, "sub/path/test3").toString(), null);
      Collection<File> filesBefore =
          FileUtils.listFiles(new File(manifestDir), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
      assertThat(filesBefore.stream().map(File::getPath).map(path -> path.substring(manifestDir.length())))
          .containsExactlyInAnyOrder("/test1.yaml", "/test2", "/test3", "/test5.yaml", "/sub/test1.yaml",
              "/sub/path/test2.yaml", "/sub/path/test3");

      k8sTaskHelperBase.deleteSkippedManifestFiles(manifestDir, executionLogCallback);
      Collection<File> filesAfter =
          FileUtils.listFiles(new File(manifestDir), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
      assertThat(filesAfter.stream().map(File::getPath).map(path -> path.substring(manifestDir.length())))
          .containsExactlyInAnyOrder("/test2", "/test3", "/sub/path/test3");
    } finally {
      FileUtils.deleteQuietly(new File(manifestDir));
    }

    assertThat(new File(manifestDir).exists())
        .withFailMessage("Temporary directory is not deleted after test, please check")
        .isFalse();
  }

  @NotNull
  private List<V1Deployment> getV1DeploymentTestData() {
    V1Deployment v1Deployment1 = new V1Deployment()
                                     .metadata(new V1ObjectMeta().name("d1"))
                                     .kind("Deployment")
                                     .apiVersion("apps/v1")
                                     .spec(new V1DeploymentSpec().selector(new V1LabelSelector().matchLabels(
                                         Collections.singletonMap("harness.io/track", "stable"))));
    V1Deployment v1Deployment2 = new V1Deployment()
                                     .metadata(new V1ObjectMeta().name("d2"))
                                     .kind("Deployment")
                                     .apiVersion("apps/v1")
                                     .spec(new V1DeploymentSpec().selector(
                                         new V1LabelSelector().matchLabels(Collections.singletonMap("app", "nginx"))));

    V1Deployment v1Deployment3 =
        new V1Deployment().metadata(new V1ObjectMeta().name("d3")).kind("Deployment").apiVersion("apps/v1");

    V1Deployment v1Deployment4 = new V1Deployment()
                                     .metadata(new V1ObjectMeta().name("d4"))
                                     .kind("Deployment")
                                     .apiVersion("apps/v1")
                                     .spec(new V1DeploymentSpec().selector(new V1LabelSelector()));

    V1Deployment v1Deployment5 = new V1Deployment()
                                     .metadata(new V1ObjectMeta().name("d5"))
                                     .kind("Deployment")
                                     .apiVersion("apps/v1")
                                     .spec(new V1DeploymentSpec());
    return Arrays.asList(v1Deployment1, v1Deployment2, v1Deployment3, v1Deployment4, v1Deployment5);
  }

  private List<KubernetesResource> generateInputWorkloadTestResource() {
    List<KubernetesResource> resources = new ArrayList<>();
    resources.add(KubernetesResource.builder()
                      .resourceId(KubernetesResourceId.builder().name("d1").kind("Deployment").namespace("ns1").build())
                      .build());
    resources.add(KubernetesResource.builder()
                      .resourceId(KubernetesResourceId.builder().name("d2").kind("Deployment").namespace("ns2").build())
                      .build());
    resources.add(KubernetesResource.builder()
                      .resourceId(KubernetesResourceId.builder().name("d3").kind("Deployment").namespace("ns3").build())
                      .build());

    resources.add(KubernetesResource.builder()
                      .resourceId(KubernetesResourceId.builder().name("d4").kind("Deployment").namespace("ns4").build())
                      .build());
    return resources;
  }

  private List<KubernetesResourceId> generateServerWorkloadTestResource() {
    List<KubernetesResourceId> resources = new ArrayList<>();
    resources.add(KubernetesResourceId.builder().name("d1").kind("Deployment").namespace("ns1").build());
    resources.add(KubernetesResourceId.builder().name("d2").kind("Deployment").namespace("ns-diff").build());
    resources.add(KubernetesResourceId.builder().name("d3").kind("Service").namespace("ns3").build());
    resources.add(KubernetesResourceId.builder().name("d4").kind("Deployment").namespace("ns4").build());
    resources.add(KubernetesResourceId.builder().name("d5").kind("Namespace").namespace("ns4").build());
    return resources;
  }

  private void prepareTestRandomByteManifestFile(String filePath, String header) throws IOException {
    Random random = new Random();
    byte[] randomBytes = new byte[2048];
    random.nextBytes(randomBytes);

    StringBuilder fileContent = new StringBuilder();
    if (header != null) {
      fileContent.append(header);
      fileContent.append("\n");
    }
    fileContent.append(new String(randomBytes));

    FileUtils.writeStringToFile(new File(filePath), fileContent.toString(), StandardCharsets.UTF_8);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testAddingRevisionNumberWithException() {
    KubernetesResource resource = mock(KubernetesResource.class);
    K8sRequestHandlerContext context = new K8sRequestHandlerContext();
    context.setResources(Collections.singletonList(resource));
    when(resource.transformName(any(UnaryOperator.class), any())).thenThrow(new KubernetesYamlException(DEFAULT));
    when(resource.getResourceId()).thenReturn(KubernetesResourceId.builder().kind(Secret.name()).build());
    when(resource.getMetadataAnnotationValue(anyString())).thenReturn(DEFAULT);
    assertThatThrownBy(() -> k8sTaskHelperBase.addRevisionNumber(context, 1))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .hasMessageContaining("KUBERNETES_YAML_ERROR");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testKubernetesClientInstance() {
    assertThat(k8sTaskHelperBase.getKubernetesClient(true)).isInstanceOf(K8sApiClient.class);
    assertThat(k8sTaskHelperBase.getKubernetesClient(false)).isInstanceOf(K8sCliClient.class);
  }

  private List<ManifestFiles> getManifestFiles() {
    return asList(ManifestFiles.builder()
                      .fileName("chart.yaml")
                      .filePath("path/to/helm/chart/chart.yaml")
                      .fileContent("Test content")
                      .build());
  }

  private List<ManifestFiles> getEmptyManifestFiles() {
    return asList(ManifestFiles.builder()
                      .fileName("chart.yaml")
                      .filePath("path/to/helm/chart/chart.yaml")
                      .fileContent(null)
                      .build());
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGetResourceIdsToDelete() throws IOException {
    List<KubernetesResourceId> resourceIds = generateServerWorkloadTestResource();
    K8sReleaseHandler releaseHandler = mock(K8sReleaseHandler.class);
    doReturn(releaseHandler).when(releaseHandlerFactory).getK8sReleaseHandler(anyBoolean());
    doReturn(resourceIds).when(releaseHandler).getResourceIdsToDelete(any(), any(), any());
    doAnswer(returnsFirstArg()).when(spyK8sTaskHelperBase).arrangeResourceIdsInDeletionOrder(anyList());

    List<KubernetesResourceId> resourcesToDeleteIncludingNamespace = spyK8sTaskHelperBase.getResourceIdsForDeletion(
        false, "somerelease", KubernetesConfig.builder().build(), executionLogCallback, true);
    List<KubernetesResourceId> resourcesToDeleteExcludingNamespace = spyK8sTaskHelperBase.getResourceIdsForDeletion(
        false, "somerelease", KubernetesConfig.builder().build(), executionLogCallback, false);

    assertThat(resourcesToDeleteIncludingNamespace.size()).isEqualTo(5);
    assertThat(resourcesToDeleteIncludingNamespace.containsAll(resourceIds)).isTrue();

    assertThat(resourcesToDeleteExcludingNamespace.size()).isEqualTo(4);
    assertThat(resourceIds.containsAll(resourcesToDeleteExcludingNamespace)).isTrue();

    List<KubernetesResourceId> diff = resourceIds.stream()
                                          .filter(resource -> !resourcesToDeleteExcludingNamespace.contains(resource))
                                          .collect(Collectors.toList());
    assertThat(diff.size()).isEqualTo(1);
    assertThat(diff.get(0).getKind()).isEqualTo(Namespace.name());
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testCreateKubernetesResourceIdFromNamespaceKindNameWrapper() {
    KubernetesResourceId kubernetesResourceId =
        spyK8sTaskHelperBase.findScalableKubernetesResourceIdFromWorkload(validDeploymentWorkload);
    assertThat(kubernetesResourceId).isNotNull();
    assertThat(kubernetesResourceId.namespaceKindNameRef()).isEqualTo("namespace/Deployment/name");

    kubernetesResourceId = spyK8sTaskHelperBase.findScalableKubernetesResourceIdFromWorkload(validStatefulSetWorkload);
    assertThat(kubernetesResourceId).isNotNull();
    assertThat(kubernetesResourceId.namespaceKindNameRef()).isEqualTo("namespace/StatefulSet/name");

    kubernetesResourceId =
        spyK8sTaskHelperBase.findScalableKubernetesResourceIdFromWorkload(validDeploymentConfigWorkload);
    assertThat(kubernetesResourceId).isNotNull();
    assertThat(kubernetesResourceId.namespaceKindNameRef()).isEqualTo("namespace/DeploymentConfig/name");

    kubernetesResourceId =
        spyK8sTaskHelperBase.findScalableKubernetesResourceIdFromWorkload("namespace/Deployment/name");
    assertThat(kubernetesResourceId).isNotNull();
    assertThat(kubernetesResourceId.namespaceKindNameRef()).isEqualTo("namespace/Deployment/name");

    assertThatThrownBy(
        () -> spyK8sTaskHelperBase.findScalableKubernetesResourceIdFromWorkload(invalidDeploymentWorkload))
        .isInstanceOf(WingsException.class)
        .hasMessage("Invalid Kubernetes resource name " + invalidDeploymentWorkload
            + ". More than one workloads found. Others should be marked with annotation"
            + HarnessAnnotations.directApply + ": true");

    assertThatThrownBy(() -> spyK8sTaskHelperBase.findScalableKubernetesResourceIdFromWorkload(invalidWorkload))
        .hasMessage("Invalid Kubernetes resource name " + invalidWorkload + ". No workload found");

    assertThatThrownBy(() -> spyK8sTaskHelperBase.findScalableKubernetesResourceIdFromWorkload("dummy"))
        .hasMessage("Invalid Kubernetes resource name dummy. Should be in format Kind/Name");
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testFunctionSecretOrConfigMapConflicts() throws Exception {
    final List<KubernetesResource> resources =
        k8sTaskHelperBase.readManifests(prepareSomeCorrectManifestFiles(), executionLogCallback);
    spyK8sTaskHelperBase.warnIfReleaseNameConflictsWithSecretOrConfigMap(resources, "configMap", executionLogCallback);
    verify(executionLogCallback, times(1))
        .saveExecutionLog(RELEASE_NAME_CONFLICTS_WITH_SECRETS_OR_CONFIG_MAPS, WARN, CommandExecutionStatus.RUNNING);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testFunctionSecretOrConfigMapConflictsNegativeCase() throws Exception {
    final List<KubernetesResource> resources =
        k8sTaskHelperBase.readManifests(prepareSomeCorrectManifestFiles(), executionLogCallback);
    spyK8sTaskHelperBase.warnIfReleaseNameConflictsWithSecretOrConfigMap(resources, "configMap1", executionLogCallback);
    verify(executionLogCallback, times(0))
        .saveExecutionLog(RELEASE_NAME_CONFLICTS_WITH_SECRETS_OR_CONFIG_MAPS, WARN, CommandExecutionStatus.RUNNING);
  }
}
