/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.AZURE_AUTH_CERT_DIR_PATH;
import static io.harness.azure.model.AzureConstants.REPOSITORY_DIR_PATH;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType.INHERIT_FROM_DELEGATE;
import static io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType.MANUAL_CREDENTIALS;

import static com.google.common.base.Charsets.UTF_8;
import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.container.ContainerInfo;
import io.harness.delegate.beans.azure.AzureConfigContext;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.delegate.task.gcp.helpers.GkeClusterHelper;
import io.harness.exception.AzureAuthenticationException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filesystem.LazyAutoCloseableWorkingDirectory;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.delegatetasks.azure.AzureAsyncTaskHelper;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class ContainerDeploymentDelegateBaseHelper {
  @Inject private KubernetesContainerService kubernetesContainerService;
  @Inject private K8sYamlToDelegateDTOMapper k8sYamlToDelegateDTOMapper;
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private GkeClusterHelper gkeClusterHelper;
  @Inject private EncryptionService encryptionService;
  @Inject private AzureAsyncTaskHelper azureAsyncTaskHelper;

  public static final LoadingCache<String, Object> lockObjects =
      CacheBuilder.newBuilder().expireAfterAccess(30, TimeUnit.MINUTES).build(CacheLoader.from(Object::new));
  private static final String KUBE_CONFIG_DIR = "./repository/helm/.kube/";

  @NotNull
  public List<Pod> getExistingPodsByLabels(KubernetesConfig kubernetesConfig, Map<String, String> labels) {
    return emptyIfNull(kubernetesContainerService.getPods(kubernetesConfig, labels));
  }

  private List<ContainerInfo> fetchContainersUsingControllersWhenReady(KubernetesConfig kubernetesConfig,
      LogCallback executionLogCallback, List<? extends HasMetadata> controllers, List<Pod> existingPods) {
    if (isNotEmpty(controllers)) {
      return controllers.stream()
          .filter(controller
              -> !(controller.getKind().equals("ReplicaSet") && controller.getMetadata().getOwnerReferences() != null))
          .flatMap(controller -> {
            boolean isNotVersioned =
                controller.getKind().equals("DaemonSet") || controller.getKind().equals("StatefulSet");
            return kubernetesContainerService
                .getContainerInfosWhenReady(kubernetesConfig, controller.getMetadata().getName(), 0, -1,
                    (int) TimeUnit.MINUTES.toMinutes(30), existingPods, isNotVersioned, executionLogCallback, true, 0,
                    kubernetesConfig.getNamespace())
                .stream();
          })
          .collect(Collectors.toList());
    }
    return emptyList();
  }

  public List<ContainerInfo> getContainerInfosWhenReadyByLabels(
      KubernetesConfig kubernetesConfig, LogCallback logCallback, Map<String, String> labels, List<Pod> existingPods) {
    List<? extends HasMetadata> controllers = kubernetesContainerService.getControllers(kubernetesConfig, labels);

    logCallback.saveExecutionLog(format("Deployed Controllers [%s]:", controllers.size()));
    controllers.forEach(controller
        -> logCallback.saveExecutionLog(String.format("Kind:%s, Name:%s (desired: %s)", controller.getKind(),
            controller.getMetadata().getName(), kubernetesContainerService.getControllerPodCount(controller))));

    return fetchContainersUsingControllersWhenReady(kubernetesConfig, logCallback, controllers, existingPods);
  }

  public int getControllerCountByLabels(KubernetesConfig kubernetesConfig, Map<String, String> labels) {
    List<? extends HasMetadata> controllers = kubernetesContainerService.getControllers(kubernetesConfig, labels);

    return controllers.size();
  }

  public KubernetesConfig createKubernetesConfig(K8sInfraDelegateConfig clusterConfigDTO, LogCallback logCallback) {
    if (clusterConfigDTO instanceof DirectK8sInfraDelegateConfig) {
      return k8sYamlToDelegateDTOMapper.createKubernetesConfigFromClusterConfig(
          ((DirectK8sInfraDelegateConfig) clusterConfigDTO).getKubernetesClusterConfigDTO(),
          clusterConfigDTO.getNamespace());
    } else if (clusterConfigDTO instanceof GcpK8sInfraDelegateConfig) {
      GcpK8sInfraDelegateConfig gcpK8sInfraDelegateConfig = (GcpK8sInfraDelegateConfig) clusterConfigDTO;
      GcpConnectorCredentialDTO gcpCredentials = gcpK8sInfraDelegateConfig.getGcpConnectorDTO().getCredential();
      return gkeClusterHelper.getCluster(getGcpServiceAccountKeyFileContent(gcpCredentials),
          gcpCredentials.getGcpCredentialType() == INHERIT_FROM_DELEGATE, gcpK8sInfraDelegateConfig.getCluster(),
          gcpK8sInfraDelegateConfig.getNamespace());
    } else if (clusterConfigDTO instanceof AzureK8sInfraDelegateConfig) {
      try (LazyAutoCloseableWorkingDirectory workingDirectory =
               new LazyAutoCloseableWorkingDirectory(REPOSITORY_DIR_PATH, AZURE_AUTH_CERT_DIR_PATH)) {
        AzureK8sInfraDelegateConfig azureK8sInfraDelegateConfig = (AzureK8sInfraDelegateConfig) clusterConfigDTO;
        AzureConfigContext azureConfigContext =
            AzureConfigContext.builder()
                .azureConnector(azureK8sInfraDelegateConfig.getAzureConnectorDTO())
                .encryptedDataDetails(azureK8sInfraDelegateConfig.getEncryptionDataDetails())
                .subscriptionId(azureK8sInfraDelegateConfig.getSubscription())
                .resourceGroup(azureK8sInfraDelegateConfig.getResourceGroup())
                .cluster(azureK8sInfraDelegateConfig.getCluster())
                .namespace(azureK8sInfraDelegateConfig.getNamespace())
                .useClusterAdminCredentials(azureK8sInfraDelegateConfig.isUseClusterAdminCredentials())
                .certificateWorkingDirectory(workingDirectory)
                .build();
        return azureAsyncTaskHelper.getClusterConfig(azureConfigContext, logCallback);
      } catch (IOException ioe) {
        throw NestedExceptionUtils.hintWithExplanationException("Failed to authenticate with Azure",
            "Please check you Azure connector configuration or delegate filesystem permissions.",
            new AzureAuthenticationException(ioe.getMessage()));
      }
    } else {
      throw new InvalidRequestException("Unhandled K8sInfraDelegateConfig " + clusterConfigDTO.getClass());
    }
  }

  private char[] getGcpServiceAccountKeyFileContent(GcpConnectorCredentialDTO gcpCredentials) {
    if (gcpCredentials.getGcpCredentialType() == MANUAL_CREDENTIALS) {
      GcpManualDetailsDTO gcpCredentialSpecDTO = (GcpManualDetailsDTO) gcpCredentials.getConfig();
      return gcpCredentialSpecDTO.getSecretKeyRef().getDecryptedValue();
    }
    return null;
  }

  public String getKubeconfigFileContent(K8sInfraDelegateConfig k8sInfraDelegateConfig) {
    decryptK8sInfraDelegateConfig(k8sInfraDelegateConfig);
    return kubernetesContainerService.getConfigFileContent(createKubernetesConfig(k8sInfraDelegateConfig, null));
  }

  public void persistKubernetesConfig(KubernetesConfig kubernetesConfig, String directory) throws IOException {
    kubernetesContainerService.persistKubernetesConfig(kubernetesConfig, directory);
  }

  public KubernetesConfig decryptAndGetKubernetesConfig(K8sInfraDelegateConfig k8sInfraDelegateConfig) {
    decryptK8sInfraDelegateConfig(k8sInfraDelegateConfig);
    return createKubernetesConfig(k8sInfraDelegateConfig, null);
  }

  public void decryptK8sInfraDelegateConfig(K8sInfraDelegateConfig k8sInfraDelegateConfig) {
    if (k8sInfraDelegateConfig instanceof DirectK8sInfraDelegateConfig) {
      DirectK8sInfraDelegateConfig directK8sInfraDelegateConfig = (DirectK8sInfraDelegateConfig) k8sInfraDelegateConfig;
      decryptK8sClusterConfig(directK8sInfraDelegateConfig.getKubernetesClusterConfigDTO(),
          directK8sInfraDelegateConfig.getEncryptionDataDetails());
    } else if (k8sInfraDelegateConfig instanceof GcpK8sInfraDelegateConfig) {
      GcpK8sInfraDelegateConfig k8sGcpInfraDelegateConfig = (GcpK8sInfraDelegateConfig) k8sInfraDelegateConfig;
      decryptGcpClusterConfig(
          k8sGcpInfraDelegateConfig.getGcpConnectorDTO(), k8sGcpInfraDelegateConfig.getEncryptionDataDetails());
    }
  }

  public void decryptK8sClusterConfig(
      KubernetesClusterConfigDTO clusterConfigDTO, List<EncryptedDataDetail> encryptedDataDetails) {
    if (clusterConfigDTO.getCredential().getKubernetesCredentialType() == KubernetesCredentialType.MANUAL_CREDENTIALS) {
      KubernetesClusterDetailsDTO clusterDetailsDTO =
          (KubernetesClusterDetailsDTO) clusterConfigDTO.getCredential().getConfig();
      KubernetesAuthCredentialDTO authCredentialDTO = clusterDetailsDTO.getAuth().getCredentials();
      secretDecryptionService.decrypt(authCredentialDTO, encryptedDataDetails);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(authCredentialDTO, encryptedDataDetails);
    }
  }

  public void decryptGcpClusterConfig(GcpConnectorDTO gcpConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails) {
    if (gcpConnectorDTO.getCredential().getGcpCredentialType() == MANUAL_CREDENTIALS) {
      GcpManualDetailsDTO gcpCredentialSpecDTO = (GcpManualDetailsDTO) gcpConnectorDTO.getCredential().getConfig();
      secretDecryptionService.decrypt(gcpCredentialSpecDTO, encryptedDataDetails);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(gcpCredentialSpecDTO, encryptedDataDetails);
    }
  }

  public String createKubeConfig(KubernetesConfig kubernetesConfig) {
    try {
      String configFileContent = kubernetesContainerService.getConfigFileContent(kubernetesConfig);
      String md5Hash = DigestUtils.md5Hex(configFileContent);

      synchronized (lockObjects.get(md5Hash)) {
        String configFilePath = KUBE_CONFIG_DIR + md5Hash;
        File file = new File(configFilePath);
        if (!file.exists()) {
          log.info("File doesn't exist. Creating file at path {}", configFilePath);
          FileUtils.forceMkdir(file.getParentFile());
          FileUtils.writeStringToFile(file, configFileContent, UTF_8);
          log.info("Created file with size {}", file.length());
        }
        return file.getAbsolutePath();
      }
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }
}
