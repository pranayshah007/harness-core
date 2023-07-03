/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.api;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.ssca.v1.SbomProcessorApi;
import io.harness.spec.server.ssca.v1.model.EnforceSbomRequestBody;
import io.harness.spec.server.ssca.v1.model.SbomProcessRequestBody;
import io.harness.spec.server.ssca.v1.model.SbomProcessResponseBody;
import io.harness.ssca.services.SbomProcessorService;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.SSCA)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class SbomProcessorApiImpl implements SbomProcessorApi {
  @Inject SbomProcessorService sbomProcessorService;

  @Override
  public Response enforceSbom(String org, String project, @Valid EnforceSbomRequestBody body, String harnessAccount) {
    return null;
  }

  @SneakyThrows
  @Override
  public Response processSbom(
      String orgIdentifier, String projectIdentifier, SbomProcessRequestBody sbomProcessRequestBody, String accountId) {
    SbomProcessResponseBody response = new SbomProcessResponseBody();
    response.setArtifactId(
        sbomProcessorService.processSBOM(accountId, orgIdentifier, projectIdentifier, sbomProcessRequestBody));
    return Response.ok().entity(response).build();
  }
}
