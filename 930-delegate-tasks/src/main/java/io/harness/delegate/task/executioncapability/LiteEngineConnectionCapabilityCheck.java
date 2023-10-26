/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.executioncapability;

import io.harness.capability.CapabilityParameters;
import io.harness.capability.CapabilitySubjectPermission;
import io.harness.capability.CapabilitySubjectPermission.CapabilitySubjectPermissionBuilder;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialSpecDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.LiteEngineConnectionCapability;
import io.harness.delegate.task.k8s.K8sYamlToDelegateDTOMapper;
import io.harness.k8s.apiclient.ApiClientFactory;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.product.ci.engine.proto.LiteEngineGrpc;
import io.harness.product.ci.engine.proto.PingRequest;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.internal.GrpcUtil;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.options.DeleteOptions;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LiteEngineConnectionCapabilityCheck implements CapabilityCheck, ProtoCapabilityCheck {
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private K8sYamlToDelegateDTOMapper k8sYamlToDelegateDTOMapper;
  @Inject private ApiClientFactory apiClientFactory;
  @Override
  public CapabilityResponse performCapabilityCheck(ExecutionCapability delegateCapability) {
    LiteEngineConnectionCapability liteEngineConnectionCapability = (LiteEngineConnectionCapability) delegateCapability;
    boolean valid = true;
    try {
      if (liteEngineConnectionCapability.getPodName() != null && valid) {
        ConnectorDetails k8sConnectorDetails = liteEngineConnectionCapability.getK8sConnectorDetails();
        KubernetesConfig kubernetesConfig =
            getKubernetesConfig((KubernetesClusterConfigDTO) k8sConnectorDetails.getConnectorConfig(),
                k8sConnectorDetails.getEncryptedDataDetails());

        ApiClient apiClient = apiClientFactory.getClient(kubernetesConfig);
        CoreV1Api coreV1Api = new CoreV1Api(apiClient);
        GenericKubernetesApi<V1Pod, V1PodList> podClient =
            new GenericKubernetesApi(V1Pod.class, V1PodList.class, "", "v1", "pods", coreV1Api.getApiClient());
        DeleteOptions deleteOptions = new DeleteOptions();
        deleteOptions.setGracePeriodSeconds(0l);
        deleteOptions.setDryRun(Arrays.asList("All"));
        KubernetesApiResponse kubernetesApiResponse = podClient.delete(
            liteEngineConnectionCapability.getNamespace(), liteEngineConnectionCapability.getPodName(), deleteOptions);
        if (!kubernetesApiResponse.isSuccess()) {
          valid = false;
        }
      }
    } catch (Exception ex) {
      log.error("Failed to validate deletion dry run", ex);
    }
    return CapabilityResponse.builder().delegateCapability(liteEngineConnectionCapability).validated(valid).build();
  }

  @Override
  public CapabilitySubjectPermission performCapabilityCheckWithProto(CapabilityParameters parameters) {
    CapabilitySubjectPermissionBuilder builder = CapabilitySubjectPermission.builder();
    if (parameters.getCapabilityCase() != CapabilityParameters.CapabilityCase.LITE_ENGINE_CONNECTION_PARAMETERS) {
      return builder.permissionResult(CapabilitySubjectPermission.PermissionResult.DENIED).build();
    }

    return builder
        .permissionResult(isConnectibleLiteEngine(parameters.getLiteEngineConnectionParameters().getIp(),
                              parameters.getLiteEngineConnectionParameters().getPort(),
                              parameters.getLiteEngineConnectionParameters().getIsLocal())
                ? CapabilitySubjectPermission.PermissionResult.ALLOWED
                : CapabilitySubjectPermission.PermissionResult.DENIED)
        .build();
  }

  private boolean isConnectibleLiteEngine(String ip, int port, boolean isLocal) {
    String target = String.format("%s:%d", ip, port);

    ManagedChannelBuilder managedChannelBuilder = ManagedChannelBuilder.forTarget(target).usePlaintext();
    if (!isLocal) {
      managedChannelBuilder.proxyDetector(GrpcUtil.NOOP_PROXY_DETECTOR);
    }
    ManagedChannel channel = managedChannelBuilder.build();
    try {
      try {
        LiteEngineGrpc.LiteEngineBlockingStub liteEngineBlockingStub = LiteEngineGrpc.newBlockingStub(channel);
        liteEngineBlockingStub.withDeadlineAfter(120, TimeUnit.SECONDS).ping(PingRequest.newBuilder().build());
        return true;
      } finally {
        // ManagedChannels use resources like threads and TCP connections. To prevent leaking these
        // resources the channel should be shut down when it will no longer be used. If it may be used
        // again leave it running.
        channel.shutdownNow();
      }
    } catch (Exception e) {
      log.error("Failed to connect to lite engine target {} with err: {}", target, e);
    }
    return false;
  }

  private KubernetesConfig getKubernetesConfig(
      KubernetesClusterConfigDTO clusterConfigDTO, List<EncryptedDataDetail> encryptedDataDetails) {
    KubernetesCredentialSpecDTO credentialSpecDTO = clusterConfigDTO.getCredential().getConfig();
    KubernetesCredentialType kubernetesCredentialType = clusterConfigDTO.getCredential().getKubernetesCredentialType();
    if (kubernetesCredentialType == KubernetesCredentialType.MANUAL_CREDENTIALS) {
      KubernetesAuthCredentialDTO kubernetesCredentialAuth =
          ((KubernetesClusterDetailsDTO) credentialSpecDTO).getAuth().getCredentials();
      secretDecryptionService.decrypt(kubernetesCredentialAuth, encryptedDataDetails);
    }
    return k8sYamlToDelegateDTOMapper.createKubernetesConfigFromClusterConfig(clusterConfigDTO);
  }
}
