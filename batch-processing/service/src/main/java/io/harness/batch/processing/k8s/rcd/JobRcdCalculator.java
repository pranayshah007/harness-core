/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.k8s.rcd;

import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.util.Yaml;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class JobRcdCalculator implements ResourceClaimDiffCalculator {
  @Override
  public String getKind() {
    return "Job";
  }

  @Override
  public ResourceClaimDiff computeResourceClaimDiff(String oldYaml, String newYaml) {
    V1PodSpec oldPodSpec = Optional.ofNullable(Yaml.loadAs(oldYaml, V1Job.class))
                               .map(V1Job::getSpec)
                               .map(V1JobSpec::getTemplate)
                               .map(V1PodTemplateSpec::getSpec)
                               .orElse(null);
    V1PodSpec newPodSpec = Optional.ofNullable(Yaml.loadAs(newYaml, V1Job.class))
                               .map(V1Job::getSpec)
                               .map(V1JobSpec::getTemplate)
                               .map(V1PodTemplateSpec::getSpec)
                               .orElse(null);
    return ResourceClaimUtils.resourceClaimDiffForPod(oldPodSpec, newPodSpec);
  }
}
