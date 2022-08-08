/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.cloudprovider.gke;

import static io.harness.eraro.ErrorCode.INVALID_ARGUMENT;

import io.harness.delegate.task.gcp.helpers.GkeClusterHelper;
import io.harness.exception.WingsException;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.GcpConfig;
import software.wings.beans.SettingAttribute;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by bzane on 2/21/17
 */
@Singleton
@Slf4j
public class GkeClusterServiceImpl implements GkeClusterService {
  @Inject private EncryptionService encryptionService;
  @Inject private GkeClusterHelper gkeClusterHelper;

  @Override
  public KubernetesConfig createCluster(SettingAttribute computeProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, String locationClusterName, String namespace,
      Map<String, String> params) {
    GcpConfig gcpConfig = validateAndGetCredentials(computeProviderSetting);
    // Decrypt gcpConfig
    encryptionService.decrypt(gcpConfig, encryptedDataDetails, false);
    return gkeClusterHelper.createCluster(gcpConfig.getServiceAccountKeyFileContent(),
        gcpConfig.isUseDelegateSelectors(), locationClusterName, namespace, params);
  }

  private GcpConfig validateAndGetCredentials(SettingAttribute computeProviderSetting) {
    if (computeProviderSetting == null || !(computeProviderSetting.getValue() instanceof GcpConfig)) {
      throw new WingsException(INVALID_ARGUMENT).addParam("args", "InvalidConfiguration");
    }
    return (GcpConfig) computeProviderSetting.getValue();
  }

  @Override
  public KubernetesConfig getCluster(SettingAttribute computeProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, String locationClusterName, String namespace,
      boolean isInstanceSync) {
    GcpConfig gcpConfig = validateAndGetCredentials(computeProviderSetting);
    return getCluster(gcpConfig, encryptedDataDetails, locationClusterName, namespace, isInstanceSync);
  }

  @Override
  public KubernetesConfig getCluster(GcpConfig gcpConfig, List<EncryptedDataDetail> encryptedDataDetails,
      String locationClusterName, String namespace, boolean isInstanceSync) {
    // Decrypt gcpConfig
    encryptionService.decrypt(gcpConfig, encryptedDataDetails, isInstanceSync);
    return gkeClusterHelper.getCluster(gcpConfig.getServiceAccountKeyFileContent(), gcpConfig.isUseDelegateSelectors(),
        locationClusterName, namespace);
  }

  @Override
  public List<String> listClusters(
      SettingAttribute computeProviderSetting, List<EncryptedDataDetail> encryptedDataDetails) {
    GcpConfig gcpConfig = validateAndGetCredentials(computeProviderSetting);
    // Decrypt gcpConfig
    encryptionService.decrypt(gcpConfig, encryptedDataDetails, false);
    return gkeClusterHelper.listClusters(
        gcpConfig.getServiceAccountKeyFileContent(), gcpConfig.isUseDelegateSelectors());
  }
}
