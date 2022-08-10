/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.model.response.CEK8sDelegatePrerequisite;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.ApisApi;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.AuthorizationV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ResourceAttributes;
import io.kubernetes.client.openapi.models.V1ResourceAttributesBuilder;
import io.kubernetes.client.openapi.models.V1SelfSubjectAccessReview;
import io.kubernetes.client.openapi.models.V1SelfSubjectAccessReviewBuilder;
import io.kubernetes.client.openapi.models.V1SelfSubjectAccessReviewSpec;
import io.kubernetes.client.openapi.models.V1SelfSubjectAccessReviewSpecBuilder;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.openapi.models.V1SubjectAccessReviewStatus;
import io.kubernetes.client.openapi.models.V1SubjectAccessReviewStatusBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CE)
public class K8sResourceValidatorImpl implements K8sResourceValidator {
  public static final Gson GSON = new Gson();
  @Inject @Named("asyncExecutor") ExecutorService executorService;

  @Override
  public boolean validateMetricsServer(ApiClient apiClient) throws ApiException {
    ApisApi apisApi = new ApisApi(apiClient);
    return apisApi.getAPIVersions().getGroups().stream().anyMatch(x -> "metrics.k8s.io".equals(x.getName()));
  }

  @Override
  public V1SubjectAccessReviewStatus validate(AuthorizationV1Api apiClient, String group, String verb, String resource)
      throws ApiException {
    return this.validate(
        apiClient, new V1ResourceAttributesBuilder().withGroup(group).withVerb(verb).withResource(resource).build());
  }

  @Override
  public V1SubjectAccessReviewStatus validate(AuthorizationV1Api apiClient, V1ResourceAttributes v1ResourceAttributes)
      throws ApiException {
    return apiClient
        .createSelfSubjectAccessReview(new V1SelfSubjectAccessReviewBuilder()
                                           .withNewSpec()
                                           .withResourceAttributes(v1ResourceAttributes)
                                           .endSpec()
                                           .build(),
            null, null, null, null)
        .getStatus();
  }

  @Override
  public List<V1SubjectAccessReviewStatus> validate(
      AuthorizationV1Api apiClient, List<V1ResourceAttributes> v1ResourceAttributesList, int timeoutsec) {
    List<Callable<V1SubjectAccessReviewStatus>> tasks = new ArrayList<>();
    v1ResourceAttributesList.forEach(
        v1ResourceAttributes -> tasks.add(() -> this.validate(apiClient, v1ResourceAttributes)));

    try {
      return executorService.invokeAll(tasks, timeoutsec, TimeUnit.SECONDS)
          .stream()
          .map(x -> {
            try {
              return x.get();
            } catch (Exception ex) {
              String message = "BACKEND_ERROR";
              if (ex.getMessage() != null && ex.getMessage().contains("ApiException")) {
                message = "ApiException";
              }
              return new V1SubjectAccessReviewStatusBuilder()
                  .withAllowed(false)
                  .withDenied(true)
                  .withEvaluationError(message)
                  .build();
            }
          })
          .collect(Collectors.toList());
    } catch (Exception e) {
      log.error("Failed to validate all permissions ", e);
      return new ArrayList<>();
    }
  }

  public static String buildResponse(
      List<V1ResourceAttributes> cePermissions, List<V1SubjectAccessReviewStatus> statuses) {
    StringBuilder st = new StringBuilder();

    IntStream.range(0, statuses.size()).forEach(i -> {
      if (null == statuses.get(i)) {
        st.append(String.format(FAILED_RESPOSE_FORMAT, cePermissions.get(i).getVerb(),
            cePermissions.get(i).getResource(), cePermissions.get(i).getGroup()));
      } else if (!statuses.get(i).getAllowed()) {
        st.append(String.format(DENIED_RESPOSE_FORMAT, cePermissions.get(i).getVerb(),
            cePermissions.get(i).getResource(), cePermissions.get(i).getGroup()));
      }
    });

    return st.toString();
  }

  public static List<V1ResourceAttributes> v1ResourceAttributesListBuilder(
      String[] apiGroups, String[] resources, String[] verbs) {
    List<V1ResourceAttributes> list = new ArrayList<>();
    for (String group : apiGroups) {
      for (String resource : resources) {
        for (String verb : verbs) {
          list.add(new V1ResourceAttributes().group(group).resource(resource).verb(verb));
        }
      }
    }
    return list;
  }

  private static List<V1ResourceAttributes> getAllCEPermissionsRequired() {
    List<V1ResourceAttributes> cePermissions = new ArrayList<>();

    cePermissions.addAll(ImmutableList.copyOf(v1ResourceAttributesListBuilder(new String[] {""},
        new String[] {
            "pods", "nodes", "nodes/proxy", "events", "namespaces", "persistentvolumes", "persistentvolumeclaims"},
        new String[] {"get", "list", "watch"})));

    cePermissions.addAll(ImmutableList.copyOf(v1ResourceAttributesListBuilder(new String[] {"apps", "extensions"},
        new String[] {"statefulsets", "deployments", "daemonsets", "replicasets"},
        new String[] {"get", "list", "watch"})));

    cePermissions.addAll(ImmutableList.copyOf(v1ResourceAttributesListBuilder(
        new String[] {"batch"}, new String[] {"jobs", "cronjobs"}, new String[] {"get", "list", "watch"})));

    cePermissions.addAll(ImmutableList.copyOf(v1ResourceAttributesListBuilder(
        new String[] {"metrics.k8s.io"}, new String[] {"pods", "nodes"}, new String[] {"get", "list"})));

    cePermissions.addAll(ImmutableList.copyOf(v1ResourceAttributesListBuilder(
        new String[] {"storage.k8s.io"}, new String[] {"storageclasses"}, new String[] {"get", "list", "watch"})));

    return cePermissions;
  }

  public String validateCEPermissions(ApiClient apiClient) {
    AuthorizationV1Api authorizationV1Api = new AuthorizationV1Api(apiClient);
    final List<V1ResourceAttributes> cePermissions = getAllCEPermissionsRequired();

    List<V1SubjectAccessReviewStatus> response = this.validate(authorizationV1Api, cePermissions, 10);

    return buildResponse(cePermissions, response);
  }

  @Override
  public List<CEK8sDelegatePrerequisite.Rule> validateCEPermissions2(ApiClient apiClient) {
    AuthorizationV1Api authorizationV1Api = new AuthorizationV1Api(apiClient);
    final List<V1ResourceAttributes> cePermissions = getAllCEPermissionsRequired();

    List<CEK8sDelegatePrerequisite.Rule> ruleList = new ArrayList<>();

    List<V1SubjectAccessReviewStatus> statuses = this.validate(authorizationV1Api, cePermissions, 10);

    for (int i = 0; i < statuses.size(); i++) {
      V1ResourceAttributes resourceAttributes = cePermissions.get(i);
      String message = null;
      if (null == statuses.get(i)) {
        message = "BACKEND_ERROR";
      } else if (!statuses.get(i).getAllowed()) {
        if (statuses.get(i).getReason() != null) {
          message = statuses.get(i).getReason();
        } else if (statuses.get(i).getEvaluationError() != null) {
          message = statuses.get(i).getEvaluationError();
        } else {
          message = String.format("%s not allowed on %s in apiGroup %s by the configured service account",
              resourceAttributes.getVerb(), resourceAttributes.getResource(), resourceAttributes.getGroup());
        }
      }
      if (message != null) {
        ruleList.add(CEK8sDelegatePrerequisite.Rule.builder()
                         .apiGroups(resourceAttributes.getGroup())
                         .resources(resourceAttributes.getResource())
                         .verbs(resourceAttributes.getVerb())
                         .message(message)
                         .build());
      }
    }

    return ruleList;
  }

  private Optional<V1Status> getReasonOnApiException(final Callable callable) throws Exception {
    try {
      callable.call();
    } catch (ApiException ex) {
      log.error("ApiException: {}", ex.getResponseBody(), ex);
      V1Status status = GSON.fromJson(ex.getResponseBody(), V1Status.class);
      return Optional.ofNullable(status);
    }
    return Optional.empty();
  }

  @Override
  @NotNull
  public List<V1Status> validateLightwingResourceExists(final ApiClient apiClient) throws Exception {
    final CoreV1Api coreV1Api = new CoreV1Api(apiClient);
    final AppsV1Api appsV1Api = new AppsV1Api(apiClient);

    final List<V1Status> statusList = new ArrayList<>();
    Optional<V1Status> status;

    status = getReasonOnApiException(() -> coreV1Api.readNamespace("harness-autostopping", null));
    status.ifPresent(statusList::add);

    status = getReasonOnApiException(() -> coreV1Api.readNamespace("harness-autostopping", null));
    status.ifPresent(statusList::add);

    status =
        getReasonOnApiException(() -> coreV1Api.readNamespacedSecret("harness-api-key", "harness-autostopping", null));
    status.ifPresent(statusList::add);

    status = getReasonOnApiException(
        () -> coreV1Api.readNamespacedService("autostopping-router", "harness-autostopping", null));
    status.ifPresent(statusList::add);

    status = getReasonOnApiException(
        () -> appsV1Api.readNamespacedDeployment("autostopping-router", "harness-autostopping", null));
    status.ifPresent(statusList::add);

    status = getReasonOnApiException(
        () -> coreV1Api.readNamespacedService("autostopping-controller", "harness-autostopping", null));
    status.ifPresent(statusList::add);

    status = getReasonOnApiException(
        () -> appsV1Api.readNamespacedDeployment("autostopping-controller", "harness-autostopping", null));
    status.ifPresent(statusList::add);

    return statusList;
  }

  private V1SelfSubjectAccessReview lightwingSubjectAccessReviewCommons(
      final ApiClient apiClient, final V1ResourceAttributes attributes) throws ApiException {
    final AuthorizationV1Api authorizationV1Api = new AuthorizationV1Api(apiClient);

    final V1SelfSubjectAccessReviewSpec spec =
        new V1SelfSubjectAccessReviewSpecBuilder().withResourceAttributes(attributes).build();
    final V1SelfSubjectAccessReview accessReview = new V1SelfSubjectAccessReview().spec(spec);

    return authorizationV1Api.createSelfSubjectAccessReview(accessReview, null, null, null, null);
  }

  private List<V1SelfSubjectAccessReview> subjectAccessReviewForAllRequiredVerbs(
      final ApiClient apiClient, V1ResourceAttributes attributes) throws ApiException {
    final List<V1SelfSubjectAccessReview> statusList = new ArrayList<>();

    for (String verb : new String[] {"create", "update", "delete", "list"}) {
      attributes = attributes.verb(verb);

      V1SelfSubjectAccessReview status = lightwingSubjectAccessReviewCommons(apiClient, attributes);
      log.info("V1SubjectAccessReviewStatus: {}", status);
      statusList.add(status);
    }

    return statusList;
  }

  private List<V1SelfSubjectAccessReview> ingressReview(final ApiClient apiClient) throws ApiException {
    V1ResourceAttributes attributes = new V1ResourceAttributes().resource("ingress");
    return subjectAccessReviewForAllRequiredVerbs(apiClient, attributes);
  }

  private List<V1SelfSubjectAccessReview> autostoppingrulesReview(final ApiClient apiClient) throws ApiException {
    V1ResourceAttributes attributes = new V1ResourceAttributes()
                                          .group("lightwing.lightwing.io")
                                          .resource("autostoppingrules")
                                          .namespace("harness-autostopping");
    return subjectAccessReviewForAllRequiredVerbs(apiClient, attributes);
  }

  public List<V1SelfSubjectAccessReview> updateDeploymentReview(final ApiClient apiClient) throws ApiException {
    final V1ResourceAttributes attributes1 =
        new V1ResourceAttributes().resource("deployments").verb("update").group("apps");
    final V1ResourceAttributes attributes2 =
        new V1ResourceAttributes().resource("deployments").verb("update").group("extensions");

    return ImmutableList.of(lightwingSubjectAccessReviewCommons(apiClient, attributes1),
        lightwingSubjectAccessReviewCommons(apiClient, attributes2));
  }

  @Override
  @NotNull
  public List<V1SelfSubjectAccessReview> validateLightwingResourcePermissions(final ApiClient apiClient)
      throws ApiException {
    final List<V1SelfSubjectAccessReview> accessReviewStatusList = new ArrayList<>();

    accessReviewStatusList.addAll(ingressReview(apiClient));
    accessReviewStatusList.addAll(autostoppingrulesReview(apiClient));
    accessReviewStatusList.addAll(updateDeploymentReview(apiClient));

    return accessReviewStatusList;
  }
}
