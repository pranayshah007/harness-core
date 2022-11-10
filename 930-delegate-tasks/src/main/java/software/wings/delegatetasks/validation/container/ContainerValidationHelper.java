/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.validation.container;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.AzureConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SettingAttribute;
import software.wings.cloudprovider.gke.GkeClusterService;
import software.wings.helpers.ext.azure.AzureDelegateHelperService;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.settings.SettingValue;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Transient;

@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ContainerValidationHelper {
  private static final String ALWAYS_TRUE_CRITERIA = "ALWAYS_TRUE_CRITERIA";

  @Inject @Transient private transient GkeClusterService gkeClusterService;
  @Inject @Transient private transient AzureDelegateHelperService azureDelegateHelperService;
  @Inject @Transient private transient EncryptionService encryptionService;

  public String getK8sMasterUrl(ContainerServiceParams containerServiceParams) {
    SettingValue value = containerServiceParams.getSettingAttribute().getValue();
    if (value instanceof EncryptableSetting && !value.isDecrypted()
        && isNotEmpty(containerServiceParams.getEncryptionDetails())) {
      try {
        encryptionService.decrypt((EncryptableSetting) value, containerServiceParams.getEncryptionDetails(), false);
      } catch (Exception e) {
        log.info("failed to decrypt " + value, e);
        return null;
      }
    }
    return getKubernetesMasterUrl(containerServiceParams);
  }

  private String getKubernetesMasterUrl(ContainerServiceParams containerServiceParams) {
    SettingAttribute settingAttribute = containerServiceParams.getSettingAttribute();
    SettingValue value = settingAttribute.getValue();
    KubernetesConfig kubernetesConfig;
    String clusterName = containerServiceParams.getClusterName();
    String namespace = containerServiceParams.getNamespace();
    String subscriptionId = containerServiceParams.getSubscriptionId();
    String resourceGroup = containerServiceParams.getResourceGroup();
    List<EncryptedDataDetail> edd = containerServiceParams.getEncryptionDetails();
    if (value instanceof GcpConfig) {
      kubernetesConfig = gkeClusterService.getCluster(settingAttribute, edd, clusterName, namespace, false);
    } else if (value instanceof AzureConfig) {
      AzureConfig azureConfig = (AzureConfig) value;
      kubernetesConfig = azureDelegateHelperService.getKubernetesClusterConfig(
          azureConfig, edd, subscriptionId, resourceGroup, clusterName, namespace, false);
    } else if (value instanceof KubernetesClusterConfig) {
      return ((KubernetesClusterConfig) value).getMasterUrl();
    } else {
      throw new WingsException(ErrorCode.INVALID_ARGUMENT)
          .addParam("args", "Unknown kubernetes cloud provider setting value: " + value.getType());
    }
    return kubernetesConfig.getMasterUrl();
  }
}
