/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.gcp.helpers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.gcp.helpers.GcpHelperService.ALL_LOCATIONS;
import static io.harness.delegate.task.gcp.helpers.GcpHelperService.LOCATION_DELIMITER;
import static io.harness.eraro.ErrorCode.CLUSTER_NOT_FOUND;
import static io.harness.k8s.K8sConstants.API_VERSION;
import static io.harness.k8s.K8sConstants.GCP_AUTH_PLUGIN_BINARY;
import static io.harness.k8s.K8sConstants.GCP_AUTH_PLUGIN_INSTALL_HINT;
import static io.harness.k8s.model.kubeconfig.KubeConfigAuthPluginHelper.isExecAuthPluginBinaryAvailable;
import static io.harness.threading.Morpheus.sleep;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.version.Version;
import io.harness.concurrent.HTimeLimiter;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ExplanationException;
import io.harness.exception.GcpServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.gcp.helpers.GcpCredentialsHelperService;
import io.harness.k8s.model.GcpAccessTokenSupplier;
import io.harness.k8s.model.KubernetesClusterAuthType;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesConfig.KubernetesConfigBuilder;
import io.harness.k8s.model.kubeconfig.Exec;
import io.harness.k8s.model.kubeconfig.InteractiveMode;
import io.harness.logging.LogCallback;
import io.harness.serializer.JsonUtils;

import software.wings.beans.TaskType;

import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.util.store.DataStore;
import com.google.api.services.container.Container;
import com.google.api.services.container.model.Cluster;
import com.google.api.services.container.model.CreateClusterRequest;
import com.google.api.services.container.model.ListClustersResponse;
import com.google.api.services.container.model.MasterAuth;
import com.google.api.services.container.model.NodeConfig;
import com.google.api.services.container.model.Operation;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class GkeClusterHelper {
  @Inject private GcpHelperService gcpHelperService = new GcpHelperService();
  @Inject private TimeLimiter timeLimiter;
  @Inject private DataStore<StoredCredential> store;
  @Inject private Clock clock;

  public KubernetesConfig createCluster(char[] serviceAccountKeyFileContent, boolean useDelegate,
      String locationClusterName, String namespace, Map<String, String> params) {
    Container gkeContainerService = gcpHelperService.getGkeContainerService(serviceAccountKeyFileContent, useDelegate);
    String projectId = getProjectIdFromCredentials(serviceAccountKeyFileContent, useDelegate);
    String[] locationCluster = locationClusterName.split(LOCATION_DELIMITER);
    String location = locationCluster[0];
    String clusterName = locationCluster[1];
    // See if the cluster already exists
    try {
      Cluster cluster = gkeContainerService.projects()
                            .locations()
                            .clusters()
                            .get("projects/" + projectId + "/locations/" + location + "/clusters/" + clusterName)
                            .execute();
      log.info("Cluster already exists");
      log.debug("Cluster {}, location {}, project {}", clusterName, location, projectId);
      return configFromCluster(cluster, namespace, serviceAccountKeyFileContent, useDelegate);
    } catch (IOException e) {
      logNotFoundOrError(e, projectId, location, clusterName, "getting");
    }

    // Cluster doesn't exist. Create it.
    try {
      CreateClusterRequest content = new CreateClusterRequest().setCluster(
          new Cluster()
              .setName(clusterName)
              .setNodeConfig(new NodeConfig().setMachineType(params.get("machineType")))
              .setInitialNodeCount(Integer.valueOf(params.get("nodeCount")))
              .setMasterAuth(
                  new MasterAuth().setUsername(params.get("masterUser")).setPassword(params.get("masterPwd"))));
      Operation createOperation =
          gkeContainerService.projects()
              .locations()
              .clusters()
              .create("projects/" + projectId + "/locations/" + location + "/clusters/" + clusterName, content)
              .execute();
      String operationStatus =
          waitForOperationToComplete(createOperation, gkeContainerService, projectId, location, "Provisioning");
      if (operationStatus.equals("DONE")) {
        Cluster cluster = gkeContainerService.projects()
                              .locations()
                              .clusters()
                              .get("projects/" + projectId + "/locations/" + location + "/clusters/" + clusterName)
                              .execute();
        log.info("Cluster status: {}", cluster.getStatus());
        log.debug("Master endpoint: {}", cluster.getEndpoint());
        return configFromCluster(cluster, namespace, serviceAccountKeyFileContent, useDelegate);
      }
    } catch (IOException e) {
      logNotFoundOrError(e, projectId, location, clusterName, "creating");
    }
    return null;
  }

  public KubernetesConfig getCluster(
      char[] serviceAccountKeyFileContent, boolean useDelegate, String locationClusterName, String namespace) {
    return getCluster(serviceAccountKeyFileContent, useDelegate, locationClusterName, namespace, null);
  }

  public KubernetesConfig getCluster(char[] serviceAccountKeyFileContent, boolean useDelegate,
      String locationClusterName, String namespace, LogCallback logCallback) {
    Container gkeContainerService = gcpHelperService.getGkeContainerService(serviceAccountKeyFileContent, useDelegate);
    String projectId = getProjectIdFromCredentials(serviceAccountKeyFileContent, useDelegate);
    if (EmptyPredicate.isEmpty(locationClusterName)) {
      throw new InvalidRequestException("Cluster name is empty in Inframapping");
    }
    String[] locationCluster = locationClusterName.split(LOCATION_DELIMITER);
    if (locationCluster.length < 2) {
      throw new InvalidRequestException(String.format("Cluster name is not in proper format. "
              + "Expected format is <Location/ClusterName> i.e us-central1-c/test-cluster. Cluster name: [%s]",
          locationClusterName));
    }
    String location = locationCluster[0];
    String clusterName = locationCluster[1];
    try {
      Cluster cluster = gkeContainerService.projects()
                            .locations()
                            .clusters()
                            .get("projects/" + projectId + "/locations/" + location + "/clusters/" + clusterName)
                            .execute();
      log.debug("Found cluster {} in location {} for project {}", clusterName, location, projectId);
      log.info("Cluster status: {}", cluster.getStatus());
      log.debug("Master endpoint: {}", cluster.getEndpoint());

      return configFromCluster(cluster, namespace, serviceAccountKeyFileContent, useDelegate, logCallback);
    } catch (IOException e) {
      // PL-1118: In case the cluster is being destroyed/torn down. Return null will immediately reclaim the service
      // instances
      if (e instanceof GoogleJsonResponseException
          && ((GoogleJsonResponseException) e).getDetails().getCode() == HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
        String errorMessage =
            format("Cluster %s does not exist in location %s for project %s", clusterName, location, projectId);
        log.warn(errorMessage, e);
        throw new WingsException(CLUSTER_NOT_FOUND, e).addParam("message", errorMessage);
      } else {
        String errorMessage =
            format("Error getting cluster %s in location %s for project %s", clusterName, location, projectId);
        log.error(errorMessage, e);
        throw new InvalidRequestException(errorMessage, e);
      }
    }
  }

  public List<String> listClusters(char[] serviceAccountKeyFileContent, boolean useDelegate) {
    Container gkeContainerService = gcpHelperService.getGkeContainerService(serviceAccountKeyFileContent, useDelegate);
    String projectId = getProjectIdFromCredentials(serviceAccountKeyFileContent, useDelegate);
    try {
      ListClustersResponse response = gkeContainerService.projects()
                                          .locations()
                                          .clusters()
                                          .list("projects/" + projectId + "/locations/" + ALL_LOCATIONS)
                                          .execute();
      List<Cluster> clusters = response.getClusters();
      return clusters != null ? clusters.stream()
                                    .map(cluster -> cluster.getZone() + LOCATION_DELIMITER + cluster.getName())
                                    .collect(toList())
                              : ImmutableList.of();
    } catch (GoogleJsonResponseException e) {
      String errorMessage = e.getMessage();
      if (e.getDetails() != null && isNotEmpty(e.getDetails().getMessage())) {
        errorMessage = e.getDetails().getMessage();
      }
      log.error(errorMessage, e);
      throw new GcpServerException(
          String.format("Error listing clusters for project %s. Error: %s", projectId, errorMessage));
    } catch (IOException e) {
      String errorMessage =
          String.format("Error listing clusters for project %s. Error: %s", projectId, e.getMessage());
      log.error(errorMessage, e);
      throw new GcpServerException(errorMessage);
    }
  }

  private KubernetesConfig configFromCluster(
      Cluster cluster, String namespace, char[] serviceAccountKeyFileContent, boolean useDelegate) {
    return configFromCluster(cluster, namespace, serviceAccountKeyFileContent, useDelegate, null);
  }

  private KubernetesConfig configFromCluster(Cluster cluster, String namespace, char[] serviceAccountKeyFileContent,
      boolean useDelegate, LogCallback logCallback) {
    MasterAuth masterAuth = cluster.getMasterAuth();
    KubernetesConfigBuilder kubernetesConfigBuilder = KubernetesConfig.builder()
                                                          .masterUrl("https://" + cluster.getEndpoint() + "/")
                                                          .namespace(isNotBlank(namespace) ? namespace : "default");
    if (masterAuth.getUsername() != null) {
      kubernetesConfigBuilder.username(masterAuth.getUsername().toCharArray());
    }
    if (masterAuth.getPassword() != null) {
      kubernetesConfigBuilder.password(masterAuth.getPassword().toCharArray());
    }
    if (masterAuth.getClusterCaCertificate() != null) {
      kubernetesConfigBuilder.caCert(masterAuth.getClusterCaCertificate().toCharArray());
    }
    if (masterAuth.getClientCertificate() != null) {
      kubernetesConfigBuilder.clientCert(masterAuth.getClientCertificate().toCharArray());
    }
    if (masterAuth.getClientKey() != null) {
      kubernetesConfigBuilder.clientKey(masterAuth.getClientKey().toCharArray());
    }
    if (Version.parse(cluster.getCurrentMasterVersion()).compareTo(Version.parse("1.19")) >= 0) {
      kubernetesConfigBuilder.authType(KubernetesClusterAuthType.GCP_OAUTH);
      Supplier<String> tokenSupplier;
      if (useDelegate) {
        tokenSupplier = createForDefaultAppCredentials();
      } else {
        tokenSupplier = createForServiceAccount(serviceAccountKeyFileContent);
      }
      kubernetesConfigBuilder.serviceAccountTokenSupplier(tokenSupplier);
    }
    if (isExecAuthPluginBinaryAvailable(GCP_AUTH_PLUGIN_BINARY, logCallback)) {
      kubernetesConfigBuilder.authType(KubernetesClusterAuthType.EXEC_OAUTH);
      kubernetesConfigBuilder.exec(getGkeUserExecConfig());
    }
    return kubernetesConfigBuilder.build();
  }

  private GcpAccessTokenSupplier createForDefaultAppCredentials() {
    Function<String, GoogleCredential> mapper = unused -> {
      try {
        return GcpCredentialsHelperService.getApplicationDefaultCredentials();
      } catch (IOException e) {
        throw new ExplanationException("Cannot instantiate deserialize google credentials.", e);
      }
    };
    return new GcpAccessTokenSupplier(null, mapper, store, clock);
  }

  private GcpAccessTokenSupplier createForServiceAccount(char[] serviceAccountKeyFileContent) {
    Function<String, GoogleCredential> mapper = unused -> {
      try {
        return gcpHelperService.getGoogleCredential(serviceAccountKeyFileContent, false);
      } catch (IOException e) {
        throw new ExplanationException("Cannot instantiate deserialize google credentials.", e);
      }
    };
    String serviceAccountJsonKey = new String(serviceAccountKeyFileContent);
    return new GcpAccessTokenSupplier(serviceAccountJsonKey, mapper, store, clock);
  }

  private String waitForOperationToComplete(Operation operation, Container gkeContainerService, String projectId,
      String location, String operationLogMessage) {
    log.info(operationLogMessage + "...");
    try {
      return HTimeLimiter.callInterruptible21(
          timeLimiter, Duration.ofMinutes(gcpHelperService.getTimeoutMins()), () -> {
            while (true) {
              String status =
                  gkeContainerService.projects()
                      .locations()
                      .operations()
                      .get("projects/" + projectId + "/locations/" + location + "/operations/" + operation.getName())
                      .execute()
                      .getStatus();
              if (!status.equals("RUNNING")) {
                log.info(operationLogMessage + ": " + status);
                return status;
              }
              sleep(ofSeconds(gcpHelperService.getSleepIntervalSecs()));
            }
          });
    } catch (UncheckedTimeoutException e) {
      log.error("Timed out checking operation status");
      return "UNKNOWN";
    } catch (Exception e) {
      log.error("Error checking operation status", e);
      return "UNKNOWN";
    }
  }

  private String getProjectIdFromCredentials(char[] serviceAccountKeyFileContent, boolean useDelegate) {
    if (useDelegate) {
      return gcpHelperService.getClusterProjectId(TaskType.GCP_TASK.name());
    } else {
      return (String) (JsonUtils.asObject(new String(serviceAccountKeyFileContent), HashMap.class)).get("project_id");
    }
  }

  private void logNotFoundOrError(
      IOException e, String projectId, String location, String clusterName, String actionVerb) {
    if (e instanceof GoogleJsonResponseException
        && ((GoogleJsonResponseException) e).getDetails().getCode() == HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
      log.warn(format("Cluster %s does not exist in location %s for project %s", clusterName, location, projectId), e);
    } else {
      log.error(
          format("Error %s cluster %s in location %s for project %s", actionVerb, clusterName, location, projectId), e);
    }
  }

  private Exec getGkeUserExecConfig() {
    return Exec.builder()
        .apiVersion(API_VERSION)
        .command(GCP_AUTH_PLUGIN_BINARY)
        .interactiveMode(InteractiveMode.NEVER)
        .provideClusterInfo(true)
        .installHint(GCP_AUTH_PLUGIN_INSTALL_HINT)
        .build();
  }
}
