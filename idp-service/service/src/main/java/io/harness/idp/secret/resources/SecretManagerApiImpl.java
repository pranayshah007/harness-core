/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.secret.resources;

import io.harness.spec.server.idp.v1.SecretManagerApi;
import io.harness.spec.server.idp.v1.model.SecretRequest;

import javax.validation.Valid;
import javax.ws.rs.core.Response;

public class SecretManagerApiImpl implements SecretManagerApi {
  @Override
  public Response createSecret(@Valid SecretRequest body, String harnessAccount) {
    return null;
  }

  @Override
  public Response deleteSecret(String secret, String harnessAccount) {
    return null;
  }

  @Override
  public Response getSecret(String secret, String harnessAccount) {
    return null;
  }

  @Override
  public Response listSecrets(
      @Valid SecretRequest body, String harnessAccount, Integer page, Integer limit, String sort) {
    return null;
  }

  @Override
  public Response updateSecret(String secret, @Valid SecretRequest body, String harnessAccount) {
    return null;
  }
}
