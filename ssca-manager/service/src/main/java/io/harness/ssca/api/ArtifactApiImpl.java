/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.api;

import io.harness.spec.server.ssca.v1.ArtifactApi;
import io.harness.spec.server.ssca.v1.model.ArtifactSbomResponseBody;
import io.harness.ssca.services.ArtifactService;

import com.google.inject.Inject;
import javax.ws.rs.core.Response;

public class ArtifactApiImpl implements ArtifactApi {
  @Inject ArtifactService artifactService;

  @Override
  public Response sbom(String artifactId, String stepExecutionId, String harnessAccount) {
    String sbom = artifactService.sbom(artifactId, stepExecutionId);
    ArtifactSbomResponseBody response = new ArtifactSbomResponseBody().sbom(sbom);
    return Response.ok().entity(response).build();
  }
}
