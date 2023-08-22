/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.api;

import io.harness.spec.server.ssca.v1.EnforcementResultApi;
import io.harness.spec.server.ssca.v1.model.EnforcementResultDTO;
import io.harness.ssca.services.EnforcementResultService;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.core.Response;

public class EnforcementResultApiImpl implements EnforcementResultApi {
  @Inject EnforcementResultService enforcementResultService;

  @Override
  public Response saveEnforcementResult(
      String orgIdentifier, String projectIdentifier, @Valid EnforcementResultDTO body, String accountId) {
    enforcementResultService.create(body);
    return Response.ok().build();
  }
}
