/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.publicaccess;

import io.harness.spec.server.accesscontrol.v1.PublicAccessApi;
import io.harness.spec.server.accesscontrol.v1.model.PublicAccessRequest;

import javax.validation.Valid;
import javax.ws.rs.core.Response;

public class PublicAccessApiImpl implements PublicAccessApi {
  @Override
  public Response enablePublicAccess(@Valid PublicAccessRequest body, String harnessAccount) {
    return null;
  }
}
