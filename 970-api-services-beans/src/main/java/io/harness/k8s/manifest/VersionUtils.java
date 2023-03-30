/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.manifest;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.k8s.model.Kind.ConfigMap;
import static io.harness.k8s.model.Kind.CronJob;
import static io.harness.k8s.model.Kind.DaemonSet;
import static io.harness.k8s.model.Kind.Deployment;
import static io.harness.k8s.model.Kind.DeploymentConfig;
import static io.harness.k8s.model.Kind.Job;
import static io.harness.k8s.model.Kind.Pod;
import static io.harness.k8s.model.Kind.Secret;
import static io.harness.k8s.model.Kind.StatefulSet;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.k8s.model.HarnessAnnotations;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public class VersionUtils {
  private static String revisionSeparator = "-";
  static final String CONFIGMAP_SECRET_SUFFIX_APPEND_FAILURE_MESSAGE =
      "Unable to append suffix [%s] to configmaps/secrets. "
      + "This could potentially lead to stage and primary resource share the same configmap/secret.";
  static final int CONFIGMAP_SECRET_MAX_NAME_LENGTH = 253;
  private static Set<String> versionedKinds = ImmutableSet.of(ConfigMap.name(), Secret.name());
  private static Set<String> workloadKinds = ImmutableSet.of(Deployment.name(), DaemonSet.name(), StatefulSet.name(),
      Pod.name(), Job.name(), DeploymentConfig.name(), CronJob.name());

  public static boolean shouldVersion(KubernetesResource resource) {
    if (versionedKinds.contains(resource.getResourceId().getKind())) {
      String skipVersioning = resource.getMetadataAnnotationValue(HarnessAnnotations.skipVersioning);
      if (StringUtils.equalsIgnoreCase(skipVersioning, "true")) {
        return false;
      }
      return true;
    }
    return false;
  }

  private static boolean shouldUpdateVersionedReferences(KubernetesResource resource) {
    if (workloadKinds.contains(resource.getResourceId().getKind())) {
      return true;
    }
    return false;
  }

  public static void markVersionedResources(List<KubernetesResource> resources) {
    for (KubernetesResource resource : resources) {
      if (shouldVersion(resource)) {
        resource.getResourceId().setVersioned(true);
      }
    }
  }

  public static void addRevisionNumber(List<KubernetesResource> resources, int revision) {
    Set<KubernetesResourceId> versionedResources = new HashSet<>();
    UnaryOperator<Object> appendRevision = t -> t + revisionSeparator + revision;

    for (KubernetesResource resource : resources) {
      if (shouldVersion(resource)) {
        versionedResources.add(resource.getResourceId().cloneInternal());
        resource.transformName(appendRevision);
        resource.getResourceId().setVersioned(true);
      }
    }

    for (KubernetesResource resource : resources) {
      if (shouldUpdateVersionedReferences(resource)) {
        UnaryOperator<Object> updateConfigMapReference = t -> {
          KubernetesResourceId configMapResourceId = KubernetesResourceId.builder()
                                                         .namespace(resource.getResourceId().getNamespace())
                                                         .kind(ConfigMap.name())
                                                         .name((String) t)
                                                         .build();
          if (versionedResources.contains(configMapResourceId)) {
            return t + revisionSeparator + revision;
          }
          return t;
        };

        UnaryOperator<Object> updateSecretReference = t -> {
          KubernetesResourceId secretResourceId = KubernetesResourceId.builder()
                                                      .namespace(resource.getResourceId().getNamespace())
                                                      .kind(Secret.name())
                                                      .name((String) t)
                                                      .build();
          if (versionedResources.contains(secretResourceId)) {
            return t + revisionSeparator + revision;
          }
          return t;
        };

        resource.transformConfigMapAndSecretRef(updateConfigMapReference, updateSecretReference);
      }
    }
  }

  public static void addSuffixToConfigmapsAndSecrets(
      List<KubernetesResource> resources, String suffixWithoutSeparator, LogCallback logCallback) {
    String suffix = getLongestPossibleSuffix(resources, revisionSeparator + suffixWithoutSeparator, logCallback);

    if (isEmpty(suffix)) {
      return;
    }

    UnaryOperator<Object> appendSuffixOperator = t -> t + suffix;

    for (KubernetesResource resource : resources) {
      if (shouldVersion(resource)) {
        resource.transformName(appendSuffixOperator);
      }
    }

    for (KubernetesResource resource : resources) {
      if (shouldUpdateVersionedReferences(resource)) {
        UnaryOperator<Object> updateReference = t -> t + suffix;

        resource.transformConfigMapAndSecretRef(updateReference, updateReference);
      }
    }
  }

  private static String getLongestPossibleSuffix(
      List<KubernetesResource> resources, String desiredSuffix, LogCallback logCallback) {
    Optional<Integer> longestNameLengthOptional =
        resources.stream()
            .filter(resource -> versionedKinds.contains(resource.getResourceId().getKind()))
            .map(res -> res.getResourceId().getName().length())
            .reduce(Integer::max);

    if (longestNameLengthOptional.isEmpty()) {
      // not expected, but if it happens do nothing
      return EMPTY;
    }

    int longestPossibleSuffixLength = CONFIGMAP_SECRET_MAX_NAME_LENGTH - longestNameLengthOptional.get();

    if (longestPossibleSuffixLength < 0) {
      // will eventually fail during apply anyway, don't append anything
      return EMPTY;
    }

    return computeSuffix(desiredSuffix, longestPossibleSuffixLength, logCallback);
  }

  private static String computeSuffix(String desiredSuffix, int longestPossibleSuffixLength, LogCallback logCallback) {
    if (longestPossibleSuffixLength == 0 || longestPossibleSuffixLength == 1) {
      // since name is already at limit or cannot end with "-"
      logCallback.saveExecutionLog(
          format(CONFIGMAP_SECRET_SUFFIX_APPEND_FAILURE_MESSAGE, desiredSuffix), LogLevel.WARN);
      return EMPTY;
    }

    if (longestPossibleSuffixLength >= desiredSuffix.length()) {
      // apply full suffix
      return desiredSuffix;
    }

    return desiredSuffix.substring(0, longestPossibleSuffixLength);
  }
}
