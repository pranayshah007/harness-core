/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.cik8handler;

/**
 * Delegate task handler to delete CI build setup pod on a K8 cluster.
 */

import static io.harness.connector.SecretSpecBuilder.getSecretName;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static java.lang.String.format;

import io.harness.delegate.beans.ci.CICleanupTaskParams;
import io.harness.delegate.beans.ci.k8s.CIK8CleanupTaskParams;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.task.citasks.CICleanupTaskHandler;
import io.harness.delegate.task.citasks.cik8handler.helper.SecretVolumesHelper;
import io.harness.delegate.task.citasks.cik8handler.k8java.CIK8JavaClientHandler;
import io.harness.exception.PodNotFoundException;
import io.harness.k8s.apiclient.ApiClientFactory;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.AutoLogContext;
import io.harness.logging.CommandExecutionStatus;

import com.google.inject.Inject;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Status;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CIK8CleanupTaskHandler implements CICleanupTaskHandler {
  @Inject private CIK8JavaClientHandler cik8JavaClientHandler;
  @Inject private K8sConnectorHelper k8sConnectorHelper;
  @Inject private ApiClientFactory apiClientFactory;
  @Inject private SecretVolumesHelper secretVolumesHelper;

  @NotNull private CICleanupTaskHandler.Type type = CICleanupTaskHandler.Type.GCP_K8;

  @Override
  public CICleanupTaskHandler.Type getType() {
    return type;
  }

  @Override
  public K8sTaskExecutionResponse executeTaskInternal(CICleanupTaskParams ciCleanupTaskParams, String taskId) {
    CIK8CleanupTaskParams taskParams = (CIK8CleanupTaskParams) ciCleanupTaskParams;
    String namespace = taskParams.getNamespace();

    if (taskParams.getPodNameList().size() != 1) {
      return K8sTaskExecutionResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .errorMessage("Only single pod can be cleaned in k8 clean up task")
          .build();
    }

    String podName = taskParams.getPodNameList().get(0);
    try (AutoLogContext ignore1 = new K8LogContext(podName, null, OVERRIDE_ERROR)) {
      try {
        KubernetesConfig kubernetesConfig = k8sConnectorHelper.getKubernetesConfig(taskParams.getK8sConnector());
        ApiClient apiClient = apiClientFactory.getClient(kubernetesConfig);
        CoreV1Api coreV1Api = new CoreV1Api(apiClient);

        boolean podsDeleted;
        try {
          podsDeleted = deletePods(coreV1Api, namespace, taskParams.getPodNameList());
        } catch (Exception ex) {
          podsDeleted = false;
          log.warn("Failed to delete pod {}", taskParams.getPodNameList(), ex);
        }

        if (!podsDeleted) {
          log.warn("Failed to delete pod {}", taskParams.getPodNameList());
        }

        boolean serviceDeleted = deleteServices(coreV1Api, namespace, taskParams.getServiceNameList());
        if (!serviceDeleted) {
          log.warn("Failed to delete service {}", taskParams.getPodNameList());
        }
        boolean secretsDeleted =
            deleteSecrets(coreV1Api, namespace, taskParams.getPodNameList(), taskParams.getCleanupContainerNames());

        if (!secretsDeleted) {
          log.warn("Failed to delete secrets {}", taskParams.getPodNameList());
        }
        if (podsDeleted && serviceDeleted && secretsDeleted) {
          return K8sTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
        } else {
          return K8sTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build();
        }

      } catch (Exception ex) {
        log.error("Exception in processing CI K8 delete setup task: {}", taskParams, ex);
        return K8sTaskExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .errorMessage(ex.getMessage())
            .build();
      }
    }
  }

  private boolean deletePods(CoreV1Api coreV1Api, String namespace, List<String> podNameList) {
    boolean isSuccess = true;
    if (isEmpty(podNameList)) {
      log.warn("No pods to delete");
      return isSuccess;
    }
    for (String podName : podNameList) {
      try {
        V1Status v1Status = cik8JavaClientHandler.deletePodWithRetries(coreV1Api, podName, namespace);
        if (v1Status.getStatus().equals("Failure")) {
          log.warn("Failed to delete pod {}", podName);
          isSuccess = false;
        }
      } catch (ApiException ex) {
        isSuccess = false;
        log.info("CreateOrReplace Pod: Pod delete failed with err: %s", ex);
      } catch (PodNotFoundException ex) {
        isSuccess = false;
        log.warn("Failed to delete pod as pod doesn’t exist", podName);
      }
    }

    return isSuccess;
  }

  private boolean deleteServices(CoreV1Api coreV1Api, String namespace, List<String> serviceNameList) {
    boolean isSuccess = true;
    if (serviceNameList == null) {
      return isSuccess;
    }
    for (String serviceName : serviceNameList) {
      Boolean isDeleted = cik8JavaClientHandler.deleteService(coreV1Api, namespace, serviceName);
      if (isDeleted.equals(Boolean.FALSE)) {
        log.warn("Failed to delete service {}", serviceName);
        isSuccess = false;
      }
    }
    return isSuccess;
  }

  private boolean deleteSecrets(
      CoreV1Api coreV1Api, String namespace, List<String> podNameList, List<String> containerNames) {
    boolean isSuccess = true;
    if (isEmpty(podNameList)) {
      log.warn("Empty pod list, Failed to delete secrets");
      return false;
    }
    for (String podName : podNameList) {
      String secretName = getSecretName(podName);
      Boolean isDeleted = false;
      try {
        isDeleted = cik8JavaClientHandler.deleteSecret(coreV1Api, namespace, secretName);
        if (isDeleted.equals(Boolean.FALSE)) {
          log.warn("Failed to delete secret {}", secretName);
          isSuccess = false;
        }
      } catch (Exception ex) {
        log.warn("Failed to delete secret {}", secretName, ex.getMessage());
        isSuccess = false;
      }

      // Delete all secrets associated with secret volumes
      for (String secretKey : secretVolumesHelper.getAllSecretKeys(podName)) {
        try {
          isDeleted = cik8JavaClientHandler.deleteSecret(coreV1Api, namespace, secretKey);
          if (isDeleted.equals(Boolean.FALSE)) {
            log.warn("Failed to delete secret {}", secretKey);
            isSuccess = false;
          }
        } catch (Exception ex) {
          log.warn("Failed to delete secret {}", secretName, ex.getMessage());
          isSuccess = false;
        }
      }

      if (isNotEmpty(containerNames)) {
        for (String containerName : containerNames) {
          String containerSecretName = format("%s-image-%s", podName, containerName);
          Boolean isDeletedContainerImageSecret = false;
          try {
            isDeletedContainerImageSecret =
                cik8JavaClientHandler.deleteSecret(coreV1Api, namespace, containerSecretName);
          } catch (Exception ex) {
            log.warn("Failed to delete secret {}", secretName, ex.getMessage());
            isSuccess = false;
          }
          if (isDeletedContainerImageSecret.equals(Boolean.FALSE)) {
            log.warn("Failed to delete secret {}", containerSecretName);
            isSuccess = false;
          }
        }
      }
    }
    return isSuccess;
  }
}
