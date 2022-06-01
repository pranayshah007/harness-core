/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s.client;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.KubernetesHelperService;
import io.harness.k8s.model.KubernetesConfig;

import software.wings.delegatetasks.k8s.exception.K8sClusterException;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.client.Adapters;
import io.fabric8.kubernetes.client.Client;
import io.fabric8.kubernetes.client.ExtensionAdapter;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.net.MalformedURLException;
import java.net.URL;

@Singleton
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class HarnessKubernetesClientFactory implements KubernetesClientFactory {
  @Inject private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Inject private KubernetesHelperService kubernetesHelperService;

  @Override
  public KubernetesClient newKubernetesClient(K8sClusterConfig k8sClusterConfig) {
    KubernetesConfig kubernetesConfig = containerDeploymentDelegateHelper.getKubernetesConfig(k8sClusterConfig, false);
    kubernetesConfig.setMasterUrl(modifyMasterUrl(kubernetesConfig.getMasterUrl()));
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig);
  }

  @Override
  public <T extends Client> T newAdaptedClient(K8sClusterConfig k8sClusterConfig, Class<T> clazz) {
    final ExtensionAdapter<T> adapter = Adapters.get(clazz);
    final KubernetesClient client = newKubernetesClient(k8sClusterConfig);
    if (adapter != null && adapter.isAdaptable(client)) {
      return adapter.adapt(client);
    }
    throw new K8sClusterException("Cluster doesn't support metrics-server");
  }

  String modifyMasterUrl(String masterURL) {
    URL url;
    try {
      url = new URL(masterURL);
      if (url.getPort() == -1) {
        int port = getDefaultPort(url.getProtocol());
        url = new URL(url.getProtocol(), url.getHost(), port, url.getFile());
      }
    } catch (MalformedURLException e) {
      throw new InvalidRequestException("Master url is invalid", e);
    }
    return url.toString();
  }

  private int getDefaultPort(String scheme) {
    return "https".equals(scheme) ? 443 : 80;
  }
}
