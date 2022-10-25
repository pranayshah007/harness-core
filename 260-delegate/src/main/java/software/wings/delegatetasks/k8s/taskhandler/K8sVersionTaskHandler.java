/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s.taskhandler;

import static io.harness.logging.logcontext.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.k8s.apiclient.ApiClientFactoryImpl;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.logcontext.AutoLogContext;

import software.wings.beans.KubernetesClusterConfig;
import software.wings.delegatetasks.k8s.logging.K8sVersionLogContext;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.helpers.ext.k8s.response.K8sVersionResponse;

import com.google.inject.Inject;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.VersionApi;
import io.kubernetes.client.openapi.models.VersionInfo;
import java.util.Objects;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class K8sVersionTaskHandler extends K8sTaskHandler {
  @Inject private transient ApiClientFactoryImpl apiClientFactory;
  @Inject private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;

  @Override
  public K8sTaskExecutionResponse executeTaskInternal(
      K8sTaskParameters k8sTaskParameters, K8sDelegateTaskParams k8sDelegateTaskParams) throws Exception {
    return executeTaskInternal(k8sTaskParameters);
  }

  public K8sTaskExecutionResponse executeTaskInternal(K8sTaskParameters k8sTaskParameters) throws ApiException {
    KubernetesClusterConfig kubernetesClusterConfig = getKubernetesConfig(k8sTaskParameters);

    VersionInfo versionInfo = getK8sVersionInfo(k8sTaskParameters.getK8sClusterConfig());
    K8sVersionResponse k8sVersionResponse = k8sVersionResponseBuilder(versionInfo);

    boolean isCloudCostEnabled = Objects.nonNull(kubernetesClusterConfig)
        && kubernetesClusterConfig.getCcmConfig() != null
        && kubernetesClusterConfig.getCcmConfig().isCloudCostEnabled();

    try (AutoLogContext ignore =
             new K8sVersionLogContext(k8sTaskParameters.getK8sClusterConfig().getCloudProvider().getType(),
                 k8sVersionResponse.getServerMajorVersion() + ":" + k8sVersionResponse.getServerMinorVersion(),
                 isCloudCostEnabled, OVERRIDE_ERROR);) {
      log.info("[cloudProvider={}, version={}, ccEnabled={}]",
          k8sTaskParameters.getK8sClusterConfig().getCloudProvider().getType(),
          k8sVersionResponse.getServerMajorVersion() + ":" + k8sVersionResponse.getServerMinorVersion(),
          isCloudCostEnabled);
    }

    return K8sTaskExecutionResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .k8sTaskResponse(k8sVersionResponse)
        .build();
  }

  public VersionInfo getK8sVersionInfo(K8sClusterConfig k8sClusterConfig) throws ApiException {
    KubernetesConfig kubernetesConfig = containerDeploymentDelegateHelper.getKubernetesConfig(k8sClusterConfig, false);
    ApiClient client = apiClientFactory.getClient(kubernetesConfig);
    VersionApi apiInstance = new VersionApi(client);
    return apiInstance.getCode();
  }

  public static K8sVersionResponse k8sVersionResponseBuilder(VersionInfo versionInfo) {
    return K8sVersionResponse.builder()
        .serverMajorVersion(versionInfo.getMajor())
        .serverMinorVersion(versionInfo.getMinor())
        .gitVersion(versionInfo.getGitVersion())
        .platform(versionInfo.getPlatform())
        .gitCommit(versionInfo.getGitCommit())
        .build();
  }

  public static KubernetesClusterConfig getKubernetesConfig(K8sTaskParameters k8sTaskParameters) {
    return k8sTaskParameters.getK8sClusterConfig().getCloudProvider() instanceof KubernetesClusterConfig
        ? (KubernetesClusterConfig) k8sTaskParameters.getK8sClusterConfig().getCloudProvider()
        : null;
  }
}