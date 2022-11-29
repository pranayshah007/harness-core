/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance.sync;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;
import static io.harness.validation.Validator.notNullCheck;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.Application;
import software.wings.beans.AzureKubernetesInfrastructureMapping;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.EcsInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.infrastructure.instance.ContainerDeploymentInfo;
import software.wings.beans.infrastructure.instance.info.ContainerInfo;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.service.impl.ContainerMetadata;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.impl.instance.sync.request.ContainerSyncRequest;
import software.wings.service.impl.instance.sync.response.ContainerSyncResponse;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ContainerService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by brett on 9/6/17
 */
@Slf4j
@OwnedBy(CDP)
public class ContainerSyncImpl implements ContainerSync {
  @Inject private SettingsService settingsService;
  @Inject private AppService appService;
  @Inject private InfrastructureMappingService infraMappingService;
  @Inject private SecretManager secretManager;
  @Inject private DelegateProxyFactory delegateProxyFactory;
  @Inject private EnvironmentService environmentService;

  @Override
  public ContainerSyncResponse getInstances(ContainerSyncRequest syncRequest) {
    List<ContainerInfo> result = Lists.newArrayList();
    for (ContainerDeploymentInfo containerDeploymentInfo :
        syncRequest.getFilter().getContainerDeploymentInfoCollection()) {
      try {
        InfrastructureMapping infrastructureMapping =
            infraMappingService.get(containerDeploymentInfo.getAppId(), containerDeploymentInfo.getInfraMappingId());
        if (!(infrastructureMapping instanceof ContainerInfrastructureMapping)) {
          String msg =
              "Unsupported infrastructure mapping type for containers :" + infrastructureMapping.getInfraMappingType();
          log.error(msg);
          throw new WingsException(msg);
        }

        SettingAttribute settingAttribute;
        String clusterName = null;
        String region = null;
        String subscriptionId = null;
        String resourceGroup = null;
        String masterUrl = null;
        ContainerInfrastructureMapping containerInfraMapping = (ContainerInfrastructureMapping) infrastructureMapping;
        settingAttribute = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
        clusterName = containerInfraMapping.getClusterName();
        if (containerInfraMapping instanceof AzureKubernetesInfrastructureMapping) {
          subscriptionId = ((AzureKubernetesInfrastructureMapping) containerInfraMapping).getSubscriptionId();
          resourceGroup = ((AzureKubernetesInfrastructureMapping) containerInfraMapping).getResourceGroup();
          masterUrl = ((AzureKubernetesInfrastructureMapping) containerInfraMapping).getMasterUrl();
        } else if (containerInfraMapping instanceof EcsInfrastructureMapping) {
          region = ((EcsInfrastructureMapping) containerInfraMapping).getRegion();
        }
        notNullCheck("SettingAttribute", settingAttribute);

        List<EncryptedDataDetail> encryptionDetails =
            secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(),
                infrastructureMapping.getAppId(), containerDeploymentInfo.getWorkflowExecutionId());

        Application app = appService.get(infrastructureMapping.getAppId());
        Environment environment = environmentService.get(app.getUuid(), infrastructureMapping.getEnvId());

        SyncTaskContext syncTaskContext =
            SyncTaskContext.builder()
                .accountId(app.getAccountId())
                .appId(app.getUuid())
                .envId(infrastructureMapping.getEnvId())
                .envType(environment != null ? environment.getEnvironmentType() : null)
                .serviceId(infrastructureMapping.getServiceId())
                .infrastructureMappingId(infrastructureMapping.getUuid())
                .infraStructureDefinitionId(infrastructureMapping.getInfrastructureDefinitionId())
                .timeout(DEFAULT_SYNC_CALL_TIMEOUT * 2)
                .build();
        ContainerServiceParams containerServiceParams =
            ContainerServiceParams.builder()
                .settingAttribute(settingAttribute.toDTO())
                .containerServiceName(containerDeploymentInfo.getContainerSvcName())
                .encryptionDetails(encryptionDetails)
                .clusterName(clusterName)
                .namespace(containerDeploymentInfo.getNamespace())
                .region(region)
                .subscriptionId(subscriptionId)
                .resourceGroup(resourceGroup)
                .masterUrl(masterUrl)
                .build();

        result.addAll(delegateProxyFactory.get(ContainerService.class, syncTaskContext)
                          .getContainerInfos(containerServiceParams, false));
      } catch (WingsException e) {
        // PL-1118: If cluster not found, return empty instance list so that all instances associated with this cluster
        // will be deleted.
        if (e.getCode() == ErrorCode.CLUSTER_NOT_FOUND) {
          log.info(e.getMessage());
        } else {
          throw e;
        }
      } catch (Exception ex) {
        log.warn("Error while getting instances for container {}", containerDeploymentInfo.getContainerSvcName(), ex);
        throw new WingsException(ErrorCode.GENERAL_ERROR, ex)
            .addParam("message",
                "Error while getting instances for container " + containerDeploymentInfo.getContainerSvcName());
      }
    }
    return ContainerSyncResponse.builder().containerInfoList(result).build();
  }

  @Override
  public ContainerSyncResponse getInstances(
      ContainerInfrastructureMapping containerInfraMapping, List<ContainerMetadata> containerMetadataList) {
    List<ContainerInfo> result = Lists.newArrayList();

    log.info("getInstances() call for app {}", containerInfraMapping.getAppId());

    for (ContainerMetadata containerMetadata : containerMetadataList) {
      try {
        log.info("getInstances() call for app {} and containerSvcName {}", containerInfraMapping.getAppId(),
            containerMetadata);

        ContainerServiceParams containerServiceParams =
            getContainerServiceParams(containerInfraMapping, containerMetadata.getContainerServiceName(),
                containerMetadata.getNamespace(), containerMetadata.getReleaseName());

        Application app = appService.get(containerInfraMapping.getAppId());
        Environment environment = environmentService.get(app.getUuid(), containerInfraMapping.getEnvId());

        SyncTaskContext syncTaskContext =
            SyncTaskContext.builder()
                .accountId(app.getAccountId())
                .appId(app.getUuid())
                .envId(containerInfraMapping.getEnvId())
                .envType(environment != null ? environment.getEnvironmentType() : null)
                .serviceId(containerInfraMapping.getServiceId())
                .infrastructureMappingId(containerInfraMapping.getUuid())
                .infraStructureDefinitionId(containerInfraMapping.getInfrastructureDefinitionId())
                .timeout(DEFAULT_SYNC_CALL_TIMEOUT * 2)
                .build();

        result.addAll(delegateProxyFactory.get(ContainerService.class, syncTaskContext)
                          .getContainerInfos(containerServiceParams, false));
      } catch (WingsException e) {
        // PL-1118: If cluster not found, return empty instance list so that all instances associated with this cluster
        // will be deleted.
        if (e.getCode() == ErrorCode.CLUSTER_NOT_FOUND) {
          log.info(e.getMessage());
        } else {
          throw e;
        }
      } catch (Exception ex) {
        throw new WingsException(ErrorCode.GENERAL_ERROR, ex)
            .addParam("message",
                "Error while getting instances for container for appId " + containerInfraMapping.getAppId()
                    + " and containerSvcName " + containerMetadata);
      }
    }
    return ContainerSyncResponse.builder().containerInfoList(result).build();
  }

  @Override
  public Set<String> getControllerNames(
      ContainerInfrastructureMapping containerInfraMapping, Map<String, String> labels, String namespace) {
    ContainerServiceParams containerServiceParams =
        getContainerServiceParams(containerInfraMapping, null, namespace, null);

    Application app = appService.get(containerInfraMapping.getAppId());
    Environment environment = environmentService.get(app.getUuid(), containerInfraMapping.getEnvId());

    SyncTaskContext syncTaskContext =
        SyncTaskContext.builder()
            .accountId(app.getAccountId())
            .appId(app.getUuid())
            .envId(containerInfraMapping.getEnvId())
            .envType(environment != null ? environment.getEnvironmentType() : null)
            .serviceId(containerInfraMapping.getServiceId())
            .infrastructureMappingId(containerInfraMapping.getUuid())
            .infraStructureDefinitionId(containerInfraMapping.getInfrastructureDefinitionId())
            .timeout(DEFAULT_SYNC_CALL_TIMEOUT * 2)
            .build();

    return delegateProxyFactory.get(ContainerService.class, syncTaskContext)
        .getControllerNames(containerServiceParams, labels);
  }

  private ContainerServiceParams getContainerServiceParams(ContainerInfrastructureMapping containerInfraMapping,
      String containerSvcName, String namespace, String releaseName) {
    SettingAttribute settingAttribute;
    String clusterName = null;
    String region = null;
    String resourceGroup = null;
    String subscriptionId = null;
    String masterUrl = null;
    settingAttribute = settingsService.get(containerInfraMapping.getComputeProviderSettingId());
    clusterName = containerInfraMapping.getClusterName();
    if (containerInfraMapping instanceof AzureKubernetesInfrastructureMapping) {
      subscriptionId = ((AzureKubernetesInfrastructureMapping) containerInfraMapping).getSubscriptionId();
      resourceGroup = ((AzureKubernetesInfrastructureMapping) containerInfraMapping).getResourceGroup();
      masterUrl = ((AzureKubernetesInfrastructureMapping) containerInfraMapping).getMasterUrl();
    } else if (containerInfraMapping instanceof EcsInfrastructureMapping) {
      region = ((EcsInfrastructureMapping) containerInfraMapping).getRegion();
    }
    notNullCheck("SettingAttribute", settingAttribute);

    List<EncryptedDataDetail> encryptionDetails = secretManager.getEncryptionDetails(
        (EncryptableSetting) settingAttribute.getValue(), containerInfraMapping.getAppId(), null);

    return ContainerServiceParams.builder()
        .settingAttribute(settingAttribute.toDTO())
        .containerServiceName(containerSvcName)
        .encryptionDetails(encryptionDetails)
        .clusterName(clusterName)
        .namespace(namespace)
        .region(region)
        .subscriptionId(subscriptionId)
        .resourceGroup(resourceGroup)
        .masterUrl(masterUrl)
        .releaseName(releaseName)
        .build();
  }
}
