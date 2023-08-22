/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.api;

import io.harness.spec.server.ssca.v1.NormalizeSbomApi;
import io.harness.ssca.services.NormalizeSbomService;

import com.google.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;

public class NormalizedSbomApiImpl implements NormalizeSbomApi {
  @Inject NormalizeSbomService normalizeSbomService;

  @Override
  public Response listNormalizedSbomComponent(String orgIdentifier, String projectIdentifier,
      @NotNull String orchestrationId, Integer page, Integer limit, String accountId) {
    return normalizeSbomService.listNormalizedSbomComponent(
        orgIdentifier, projectIdentifier, page, limit, orchestrationId, accountId);
  }
}
