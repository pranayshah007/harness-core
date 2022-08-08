/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.api.GcpClusterExecutionData.GcpClusterExecutionDataBuilder.aGcpClusterExecutionData;
import static software.wings.sm.StateType.GCP_CLUSTER_SETUP;

import io.harness.beans.ExecutionStatus;
import io.harness.context.ContextElementType;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.KubernetesConvention;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.api.ClusterElement;
import software.wings.api.DeploymentType;
import software.wings.api.PhaseElement;
import software.wings.beans.Application;
import software.wings.beans.GcpKubernetesInfrastructureMapping;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.cloudprovider.gke.GkeClusterService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;

import com.github.reinert.jjschema.Attributes;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Transient;

/**
 * Created by brett on 4/14/17
 */
@Slf4j
public class GcpClusterSetup extends State {
  @Attributes(title = "Zone") private String zone;

  @Attributes(title = "Node Count") private int nodeCount;

  @Attributes(title = "Machine Type") private String machineType;

  @Inject @Transient private transient GkeClusterService gkeClusterService;
  @Inject @Transient private transient SettingsService settingsService;
  @Inject @Transient private transient ServiceResourceService serviceResourceService;
  @Inject @Transient private transient InfrastructureMappingService infrastructureMappingService;
  @Inject @Transient private transient WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @Inject @Transient private transient SecretManager secretManager;

  /**
   * Instantiates a new state.
   */
  public GcpClusterSetup(String name) {
    super(name, GCP_CLUSTER_SETUP.name());
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    String serviceId = phaseElement.getServiceElement().getUuid();

    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    Application app = workflowStandardParamsExtensionService.getApp(workflowStandardParams);
    String env = workflowStandardParamsExtensionService.getEnv(workflowStandardParams).getName();

    InfrastructureMapping infrastructureMapping =
        infrastructureMappingService.get(app.getUuid(), context.fetchInfraMappingId());
    if (!(infrastructureMapping instanceof GcpKubernetesInfrastructureMapping)) {
      throw new InvalidRequestException("Invalid infrastructure type");
    }
    GcpKubernetesInfrastructureMapping gcpInfraMapping = (GcpKubernetesInfrastructureMapping) infrastructureMapping;
    SettingAttribute computeProviderSetting = settingsService.get(gcpInfraMapping.getComputeProviderSettingId());
    List<EncryptedDataDetail> encryptionDetails = secretManager.getEncryptionDetails(
        (EncryptableSetting) computeProviderSetting.getValue(), context.getAppId(), context.getWorkflowExecutionId());
    String serviceName = serviceResourceService.getWithDetails(app.getUuid(), serviceId).getName();
    if (isEmpty(zone)) {
      zone = "us-west1-a";
    }
    if (nodeCount <= 0) {
      nodeCount = 2;
    }
    if (isEmpty(machineType)) {
      machineType = "n1-standard-2";
    }

    String clusterName = "harness-"
        + KubernetesConvention.getKubernetesServiceName(
            KubernetesConvention.getControllerNamePrefix(app.getName(), serviceName, env));
    String zoneCluster = zone + "/" + clusterName;

    gkeClusterService.createCluster(computeProviderSetting, encryptionDetails, zoneCluster,
        context.renderExpression(gcpInfraMapping.getNamespace()),
        ImmutableMap.<String, String>builder()
            .put("nodeCount", Integer.toString(nodeCount))
            .put("machineType", machineType)
            .put("masterUser", "admin")
            .put("masterPwd", "admin")
            .build());

    ClusterElement clusterElement = ClusterElement.builder()
                                        .uuid(serviceId)
                                        .name(zoneCluster)
                                        .deploymentType(DeploymentType.KUBERNETES)
                                        .infraMappingId(context.fetchInfraMappingId())
                                        .build();

    return ExecutionResponse.builder()
        .executionStatus(ExecutionStatus.SUCCESS)
        .contextElement(clusterElement)
        .notifyElement(clusterElement)
        .stateExecutionData(aGcpClusterExecutionData()
                                .withClusterName(clusterName)
                                .withZone(zone)
                                .withNodeCount(nodeCount)
                                .withMachineType(machineType)
                                .build())
        .build();
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  public String getZone() {
    return zone;
  }

  public void setZone(String zone) {
    this.zone = zone;
  }

  public int getNodeCount() {
    return nodeCount;
  }

  public void setNodeCount(int nodeCount) {
    this.nodeCount = nodeCount;
  }

  public String getMachineType() {
    return machineType;
  }

  public void setMachineType(String machineType) {
    this.machineType = machineType;
  }
}
