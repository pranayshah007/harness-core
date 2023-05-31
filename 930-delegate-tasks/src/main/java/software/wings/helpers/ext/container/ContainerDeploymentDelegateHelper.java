/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.container;

import static com.google.common.base.Charsets.UTF_8;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AzureConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.RancherConfig;
import software.wings.beans.dto.SettingAttribute;
import software.wings.cloudprovider.gke.GkeClusterService;
import software.wings.delegatetasks.rancher.RancherTaskHelper;
import software.wings.helpers.ext.azure.AzureDelegateHelperService;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.settings.SettingValue;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;

/**
 * Created by anubhaw on 4/20/18.
 */
@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ContainerDeploymentDelegateHelper {
  @Inject private AzureDelegateHelperService azureDelegateHelperService;
  @Inject private GkeClusterService gkeClusterService;
  @Inject private KubernetesContainerService kubernetesContainerService;
  @Inject private EncryptionService encryptionService;
  @Inject private RancherTaskHelper rancherTaskHelper;

  private static final String KUBE_CONFIG_DIR = "./repository/helm/.kube/";
  private static final int KUBERNETESS_116_VERSION = 116;
  private static final String NON_DIGITS_REGEX = "\\D+";
  private static final int VERSION_LENGTH = 3;

  public static final LoadingCache<String, Object> lockObjects =
      CacheBuilder.newBuilder().expireAfterAccess(30, TimeUnit.MINUTES).build(CacheLoader.from(Object::new));

  public String createAndGetKubeConfigLocation(ContainerServiceParams containerServiceParam) {
    return createKubeConfig(getKubernetesConfig(containerServiceParam));
  }

  public void persistKubernetesConfig(K8sClusterConfig k8sClusterConfig, String workingDir) throws IOException {
    kubernetesContainerService.persistKubernetesConfig(getKubernetesConfig(k8sClusterConfig, false), workingDir);
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

  public String getKubeConfigFileContent(ContainerServiceParams containerServiceParam) {
    try {
      KubernetesConfig kubernetesConfig = getKubernetesConfig(containerServiceParam);
      return kubernetesContainerService.getConfigFileContent(kubernetesConfig);
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  public KubernetesConfig getKubernetesConfig(ContainerServiceParams containerServiceParam) {
    SettingAttribute settingAttribute = containerServiceParam.getSettingAttribute();
    List<EncryptedDataDetail> encryptedDataDetails = containerServiceParam.getEncryptionDetails();
    String clusterName = containerServiceParam.getClusterName();
    String namespace = containerServiceParam.getNamespace();

    KubernetesConfig kubernetesConfig;

    if (settingAttribute.getValue() instanceof RancherConfig) {
      try {
        SettingValue cloudProvider = settingAttribute.getValue();
        kubernetesConfig = rancherTaskHelper.createKubeconfig(
            (RancherConfig) cloudProvider, encryptedDataDetails, containerServiceParam.getClusterName(), namespace);
      } catch (Exception e) {
        throw new InvalidRequestException(
            "Unable to fetch KubeConfig from Rancher for cluster: " + containerServiceParam.getClusterName(), e);
      }
    } else if (settingAttribute.getValue() instanceof KubernetesClusterConfig) {
      KubernetesClusterConfig kubernetesClusterConfig = (KubernetesClusterConfig) settingAttribute.getValue();
      encryptionService.decrypt(kubernetesClusterConfig, encryptedDataDetails, false);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(kubernetesClusterConfig, encryptedDataDetails);

      kubernetesConfig = kubernetesClusterConfig.createKubernetesConfig(namespace);
    } else if (settingAttribute.getValue() instanceof GcpConfig) {
      kubernetesConfig =
          gkeClusterService.getCluster(settingAttribute, encryptedDataDetails, clusterName, namespace, false);
    } else if (settingAttribute.getValue() instanceof AzureConfig) {
      AzureConfig azureConfig = (AzureConfig) settingAttribute.getValue();
      kubernetesConfig = azureDelegateHelperService.getKubernetesClusterConfig(azureConfig, encryptedDataDetails,
          containerServiceParam.getSubscriptionId(), containerServiceParam.getResourceGroup(), clusterName, namespace,
          false);
    } else {
      throw new WingsException(ErrorCode.INVALID_ARGUMENT)
          .addParam(
              "args", "Unknown kubernetes cloud provider setting value: " + settingAttribute.getValue().getType());
    }

    return kubernetesConfig;
  }

  public KubernetesConfig getKubernetesConfig(K8sClusterConfig k8sClusterConfig, boolean isInstanceSync) {
    SettingValue cloudProvider = k8sClusterConfig.getCloudProvider();
    List<EncryptedDataDetail> encryptedDataDetails = k8sClusterConfig.getCloudProviderEncryptionDetails();
    String namespace = k8sClusterConfig.getNamespace();

    KubernetesConfig kubernetesConfig;
    if (cloudProvider instanceof RancherConfig) {
      try {
        kubernetesConfig = rancherTaskHelper.createKubeconfig(
            (RancherConfig) cloudProvider, encryptedDataDetails, k8sClusterConfig.getClusterName(), namespace);
      } catch (Exception e) {
        throw new InvalidRequestException(
            "Unable to fetch KubeConfig from Rancher for cluster: " + k8sClusterConfig.getClusterName(), e);
      }
    } else if (cloudProvider instanceof KubernetesClusterConfig) {
      KubernetesClusterConfig kubernetesClusterConfig = (KubernetesClusterConfig) cloudProvider;
      encryptionService.decrypt(kubernetesClusterConfig, encryptedDataDetails, isInstanceSync);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(kubernetesClusterConfig, encryptedDataDetails);
      kubernetesConfig = kubernetesClusterConfig.createKubernetesConfig(namespace);
    } else if (cloudProvider instanceof GcpConfig) {
      kubernetesConfig = gkeClusterService.getCluster((GcpConfig) cloudProvider, encryptedDataDetails,
          k8sClusterConfig.getGcpKubernetesCluster().getClusterName(), namespace, isInstanceSync);
    } else if (cloudProvider instanceof AzureConfig) {
      AzureConfig azureConfig = (AzureConfig) cloudProvider;
      kubernetesConfig = azureDelegateHelperService.getKubernetesClusterConfig(
          azureConfig, encryptedDataDetails, k8sClusterConfig.getAzureKubernetesCluster(), namespace, isInstanceSync);
    } else {
      throw new WingsException(ErrorCode.INVALID_ARGUMENT)
          .addParam("args", "Unknown kubernetes cloud provider setting value: " + cloudProvider.getType());
    }

    return kubernetesConfig;
  }

  public boolean useK8sSteadyStateCheck(boolean isK8sSteadyStateCheckEnabled, boolean useRefactorSteadyStateCheck,
      ContainerServiceParams containerServiceParams, LogCallback logCallback) {
    if (!isK8sSteadyStateCheckEnabled && !useRefactorSteadyStateCheck) {
      return false;
    }

    KubernetesConfig kubernetesConfig = getKubernetesConfig(containerServiceParams);
    String versionAsString = kubernetesContainerService.getVersionAsString(kubernetesConfig);

    logCallback.saveExecutionLog(format("Kubernetes version [%s]", versionAsString));
    int versionMajorMin = Integer.parseInt(escapeNonDigitsAndTruncate(versionAsString));

    return KUBERNETESS_116_VERSION <= versionMajorMin;
  }

  private String escapeNonDigitsAndTruncate(String value) {
    String digits = value.replaceAll(NON_DIGITS_REGEX, EMPTY);
    return digits.length() > VERSION_LENGTH ? digits.substring(0, VERSION_LENGTH) : digits;
  }
}