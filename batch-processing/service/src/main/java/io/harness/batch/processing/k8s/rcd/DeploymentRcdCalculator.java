/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.k8s.rcd;

import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentSpec;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.util.Yaml;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DeploymentRcdCalculator implements ResourceClaimDiffCalculator {
  @Override
  public String getKind() {
    return "Deployment";
  }

  @Override
  public ResourceClaimDiff computeResourceClaimDiff(String oldYaml, String newYaml) {
    Optional<V1DeploymentSpec> oldDepSpecMaybe =
        Optional.ofNullable(Yaml.loadAs(oldYaml, V1Deployment.class)).map(V1Deployment::getSpec);
    Optional<V1DeploymentSpec> newDepSpecMaybe =
        Optional.ofNullable(Yaml.loadAs(newYaml, V1Deployment.class)).map(V1Deployment::getSpec);
    V1PodSpec oldPodSpec =
        oldDepSpecMaybe.map(V1DeploymentSpec::getTemplate).map(V1PodTemplateSpec::getSpec).orElse(null);
    V1PodSpec newPodSpec =
        newDepSpecMaybe.map(V1DeploymentSpec::getTemplate).map(V1PodTemplateSpec::getSpec).orElse(null);
    int oldReplicas = oldDepSpecMaybe.map(V1DeploymentSpec::getReplicas).orElse(0);
    int newReplicas = newDepSpecMaybe.map(V1DeploymentSpec::getReplicas).orElse(0);
    return ResourceClaimUtils.resourceClaimDiffForPodWithScale(oldPodSpec, oldReplicas, newPodSpec, newReplicas);
  }
}
