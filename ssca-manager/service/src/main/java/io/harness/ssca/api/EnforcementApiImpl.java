/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.api;

import io.harness.annotations.SSCAServiceAuth;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.ssca.v1.EnforcementApi;
import io.harness.spec.server.ssca.v1.model.EnforcementSummaryResponse;
import io.harness.ssca.services.EnforcementStepService;

import com.google.inject.Inject;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.SSCA)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@SSCAServiceAuth
public class EnforcementApiImpl implements EnforcementApi {
  @Inject EnforcementStepService enforcementStepService;

  @Override
  public Response getEnforcementSummary(String org, String project, String enforcementId, String harnessAccount) {
    EnforcementSummaryResponse response =
        enforcementStepService.getEnforcementSummary(harnessAccount, org, project, enforcementId);
    return Response.ok().entity(response).build();
  }
}
