package io.harness.helpers.docker;

import io.harness.delegate.beans.ci.docker.CIDockerCleanupStepRequest;
import io.harness.delegate.beans.ci.docker.CIDockerCleanupTaskParams;

public class CICleanupStepConverter {
  public CIDockerCleanupStepRequest convert(CIDockerCleanupTaskParams params) {
    return CIDockerCleanupStepRequest.builder()
        .id(params.getStageRuntimeId())
        .correlationID(params.getStageRuntimeId()) // TODO (Vistaar): Should this be the task ID instead?
        .build();
  }
}
