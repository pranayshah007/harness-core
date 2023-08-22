/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.api;

import io.harness.spec.server.ssca.v1.EnforcementSummaryApi;
import io.harness.spec.server.ssca.v1.model.EnforcementSummaryDTO;
import io.harness.ssca.services.EnforcementSummaryService;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.core.Response;

public class EnforcementSummaryApiImpl implements EnforcementSummaryApi {
  @Inject EnforcementSummaryService enforcementSummaryService;

  @Override
  public Response saveEnforcementSummary(
      String orgIdentifier, String projectIdentifier, @Valid EnforcementSummaryDTO body, String accountId) {
    enforcementSummaryService.create(body);
    return Response.ok().build();
  }
}
