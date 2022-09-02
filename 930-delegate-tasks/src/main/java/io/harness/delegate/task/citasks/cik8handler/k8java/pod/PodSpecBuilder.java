/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.cik8handler.k8java.pod;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ci.pod.ContainerParams;
import io.harness.delegate.beans.ci.pod.PodParams;
import io.harness.delegate.task.citasks.cik8handler.params.CIConstants;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;
import io.kubernetes.client.openapi.models.V1PodBuilder;
import io.kubernetes.client.openapi.models.V1PodFluent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CI)
public class PodSpecBuilder extends BasePodSpecBuilder {
  @Override
  protected void decorateSpec(
      PodParams<ContainerParams> podParams, V1PodFluent.SpecNested<V1PodBuilder> podBuilderSpecNested) {
    if (podParams.getType() != PodParams.Type.K8) {
      log.error("Type mismatch: pod parameters is not of type: {}", PodParams.Type.K8);
      throw new InvalidRequestException("Type miss matched");
    }

    podBuilderSpecNested.withRestartPolicy(CIConstants.RESTART_POLICY);
    podBuilderSpecNested.withActiveDeadlineSeconds(podParams.getActiveDeadLineSeconds() == null
            ? CIConstants.POD_MAX_TTL_SECS
            : podParams.getActiveDeadLineSeconds());
  }
}
