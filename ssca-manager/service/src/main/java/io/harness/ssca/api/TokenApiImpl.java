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
import io.harness.security.ServiceTokenGenerator;
import io.harness.spec.server.ssca.v1.TokenApi;
import io.harness.spec.server.ssca.v1.model.TokenIssueResponseBody;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.SSCA)
@Slf4j
@SSCAServiceAuth
public class TokenApiImpl implements TokenApi {
  @Inject private ServiceTokenGenerator tokenGenerator;
  @Inject @Named("jwtAuthSecret") String jwtAuthSecret;
  @Override
  public Response tokenIssueToken(String harnessAccount) {
    String serviceTokenWithDuration = tokenGenerator.getServiceTokenWithDuration(jwtAuthSecret, Duration.ofHours(24));

    TokenIssueResponseBody responseBody = new TokenIssueResponseBody().token(serviceTokenWithDuration);
    return Response.ok().entity(responseBody).build();
  }
}
