/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.container;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.api.InstanceElement.Builder.anInstanceElement;
import static software.wings.api.ServiceTemplateElement.Builder.aServiceTemplateElement;
import static software.wings.sm.InstanceStatusSummary.InstanceStatusSummaryBuilder.anInstanceStatusSummary;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.container.ContainerInfo;
import io.harness.container.ContainerInfo.Status;
import io.harness.context.ContextElementType;
import io.harness.data.structure.UUIDGenerator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.api.HostElement;
import software.wings.api.InstanceElement;
import software.wings.api.PhaseElement;
import software.wings.api.RancherClusterElement;
import software.wings.api.ServiceElement;
import software.wings.api.ServiceTemplateElement;
import software.wings.beans.AzureKubernetesCluster;
import software.wings.beans.AzureKubernetesInfrastructureMapping;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.EcsInfrastructureMapping;
import software.wings.beans.GcpKubernetesCluster;
import software.wings.beans.GcpKubernetesInfrastructureMapping;
import software.wings.beans.RancherKubernetesInfrastructureMapping;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.SettingAttribute;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContext;
import software.wings.sm.InstanceStatusSummary;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.mongodb.morphia.Key;

/**
 * Created by anubhaw on 4/6/18.
 */
@Singleton
@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ContainerDeploymentManagerHelper {
  @Inject private SettingsService settingsService;
  @Inject private SecretManager secretManager;
  @Inject private ServiceTemplateService serviceTemplateService;
  @Inject private ContainerMasterUrlHelper containerMasterUrlHelper;
  @Inject private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  public List<InstanceStatusSummary> getInstanceStatusSummaryFromContainerInfoList(
      List<ContainerInfo> containerInfos, ServiceTemplateElement serviceTemplateElement) {
    List<InstanceStatusSummary> instanceStatusSummaries = new ArrayList<>();
    if (isNotEmpty(containerInfos)) {
      for (ContainerInfo containerInfo : containerInfos) {
        InstanceElement instanceElement = buildInstanceElement(serviceTemplateElement, containerInfo);
        ExecutionStatus status =
            containerInfo.getStatus() == Status.SUCCESS ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED;
        instanceStatusSummaries.add(
            anInstanceStatusSummary().withStatus(status).withInstanceElement(instanceElement).build());
      }
    }
    return instanceStatusSummaries;
  }

  public InstanceElement buildInstanceElement(
      ServiceTemplateElement serviceTemplateElement, ContainerInfo containerInfo) {
    HostElement hostElement = HostElement.builder()
                                  .hostName(containerInfo.getHostName())
                                  .ip(containerInfo.getIp())
                                  .ec2Instance(containerInfo.getEc2Instance())
                                  .build();
    return anInstanceElement()
        .uuid(containerInfo.getContainerId() == null ? UUIDGenerator.generateUuid() : containerInfo.getContainerId())
        .dockerId(containerInfo.getContainerId())
        .hostName(containerInfo.getHostName())
        .host(hostElement)
        .serviceTemplateElement(serviceTemplateElement)
        .displayName(containerInfo.getContainerId())
        .podName(containerInfo.getPodName())
        .namespace(containerInfo.getNamespace())
        .workloadName(containerInfo.getWorkloadName())
        .ecsContainerDetails(containerInfo.getEcsContainerDetails())
        .newInstance(containerInfo.isNewContainer())
        .build();
  }

  public List<InstanceStatusSummary> getInstanceStatusSummaries(
      ExecutionContext context, List<ContainerInfo> containerInfos) {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    ServiceElement serviceElement = phaseElement.getServiceElement();
    String serviceId = phaseElement.getServiceElement().getUuid();
    String appId = context.getAppId();
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    String envId = workflowStandardParamsExtensionService.fetchRequiredEnv(workflowStandardParams).getUuid();

    Key<ServiceTemplate> serviceTemplateKey =
        serviceTemplateService.getTemplateRefKeysByService(appId, serviceId, envId).get(0);
    ServiceTemplateElement serviceTemplateElement = aServiceTemplateElement()
                                                        .withUuid(serviceTemplateKey.getId().toString())
                                                        .withServiceElement(serviceElement)
                                                        .build();

    return getInstanceStatusSummaryFromContainerInfoList(containerInfos, serviceTemplateElement);
  }

  public ContainerServiceParams getContainerServiceParams(
      ContainerInfrastructureMapping containerInfraMapping, String containerServiceName, ExecutionContext context) {
    String clusterName = containerInfraMapping.getClusterName();
    SettingAttribute settingAttribute;
    String namespace = null;
    String region = null;
    String resourceGroup = null;
    String subscriptionId = null;
    String masterUrl = null;
    settingAttribute = settingsService.get(containerInfraMapping.getComputeProviderSettingId());

    if (containerInfraMapping instanceof RancherKubernetesInfrastructureMapping) {
      RancherKubernetesInfrastructureMapping rancherInfra =
          (RancherKubernetesInfrastructureMapping) containerInfraMapping;
      namespace = rancherInfra.getNamespace();

      if (Objects.nonNull(context) && Objects.nonNull(context.getContextElement())
          && context.getContextElement() instanceof RancherClusterElement) {
        RancherClusterElement element = context.getContextElement();
        clusterName = element.getClusterName();
      }
    } else if (containerInfraMapping instanceof DirectKubernetesInfrastructureMapping) {
      namespace = containerInfraMapping.getNamespace();
    } else if (containerInfraMapping instanceof GcpKubernetesInfrastructureMapping) {
      namespace = containerInfraMapping.getNamespace();
      masterUrl = ((GcpKubernetesInfrastructureMapping) containerInfraMapping).getMasterUrl();
    } else if (containerInfraMapping instanceof AzureKubernetesInfrastructureMapping) {
      subscriptionId = ((AzureKubernetesInfrastructureMapping) containerInfraMapping).getSubscriptionId();
      resourceGroup = ((AzureKubernetesInfrastructureMapping) containerInfraMapping).getResourceGroup();
      namespace = containerInfraMapping.getNamespace();
      masterUrl = ((AzureKubernetesInfrastructureMapping) containerInfraMapping).getMasterUrl();
    } else if (containerInfraMapping instanceof EcsInfrastructureMapping) {
      region = ((EcsInfrastructureMapping) containerInfraMapping).getRegion();
    }
    notNullCheck("SettingAttribute", settingAttribute);

    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(),
            containerInfraMapping.getAppId(), context != null ? context.getWorkflowExecutionId() : null);
    return ContainerServiceParams.builder()
        .settingAttribute(settingAttribute)
        .containerServiceName(containerServiceName)
        .encryptionDetails(encryptionDetails)
        .clusterName(clusterName)
        .namespace(context != null ? context.renderExpression(namespace) : namespace)
        .region(region)
        .subscriptionId(subscriptionId)
        .resourceGroup(resourceGroup)
        .masterUrl(masterUrl)
        .build();
  }

  public K8sClusterConfig getK8sClusterConfig(
      ContainerInfrastructureMapping containerInfraMapping, ExecutionContext context) {
    SettingAttribute settingAttribute;
    AzureKubernetesCluster azureKubernetesCluster = null;
    GcpKubernetesCluster gcpKubernetesCluster = null;
    String namespace = null;
    String clusterName = null;
    String cloudProviderName = null;

    if (containerInfraMapping instanceof RancherKubernetesInfrastructureMapping) {
      RancherKubernetesInfrastructureMapping rancherInfra =
          (RancherKubernetesInfrastructureMapping) containerInfraMapping;
      settingAttribute = settingsService.get(rancherInfra.getComputeProviderSettingId());
      namespace = rancherInfra.getNamespace();

      if (Objects.nonNull(context) && Objects.nonNull(context.getContextElement())
          && context.getContextElement() instanceof RancherClusterElement) {
        RancherClusterElement element = context.getContextElement();
        clusterName = element.getClusterName();
      }
    } else if (containerInfraMapping instanceof DirectKubernetesInfrastructureMapping) {
      DirectKubernetesInfrastructureMapping directInfraMapping =
          (DirectKubernetesInfrastructureMapping) containerInfraMapping;
      settingAttribute = settingsService.get(directInfraMapping.getComputeProviderSettingId());
      namespace = directInfraMapping.getNamespace();
    } else {
      settingAttribute = settingsService.get(containerInfraMapping.getComputeProviderSettingId());
      if (containerInfraMapping instanceof GcpKubernetesInfrastructureMapping) {
        gcpKubernetesCluster =
            GcpKubernetesCluster.builder().clusterName(containerInfraMapping.getClusterName()).build();
        namespace = containerInfraMapping.getNamespace();
        clusterName = gcpKubernetesCluster.getClusterName();
      } else if (containerInfraMapping instanceof AzureKubernetesInfrastructureMapping) {
        azureKubernetesCluster =
            AzureKubernetesCluster.builder()
                .subscriptionId(((AzureKubernetesInfrastructureMapping) containerInfraMapping).getSubscriptionId())
                .resourceGroup(((AzureKubernetesInfrastructureMapping) containerInfraMapping).getResourceGroup())
                .name(containerInfraMapping.getClusterName())
                .build();
        namespace = containerInfraMapping.getNamespace();
        clusterName = azureKubernetesCluster.getName();
      }
    }
    notNullCheck("SettingAttribute", settingAttribute);
    cloudProviderName = settingAttribute.getName();

    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(),
            containerInfraMapping.getAppId(), context != null ? context.getWorkflowExecutionId() : null);

    return K8sClusterConfig.builder()
        .cloudProvider(settingAttribute.getValue())
        .cloudProviderEncryptionDetails(encryptionDetails)
        .azureKubernetesCluster(azureKubernetesCluster)
        .gcpKubernetesCluster(gcpKubernetesCluster)
        .clusterName(clusterName)
        .namespace(namespace)
        .cloudProviderName(cloudProviderName)
        .masterUrl(containerMasterUrlHelper.fetchMasterUrl(
            getContainerServiceParams(containerInfraMapping, null, context), containerInfraMapping))
        .build();
  }
}