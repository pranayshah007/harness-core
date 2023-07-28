/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriInfo;
import okhttp3.Response;

public interface NextGenService {
  Response getRequest(UriInfo uriInfo, HttpHeaders headers, String path, String harnessAccount);
}
