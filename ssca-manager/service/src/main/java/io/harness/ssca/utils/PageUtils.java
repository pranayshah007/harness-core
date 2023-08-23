/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.utils;

import io.harness.utils.ApiUtils;

import javax.ws.rs.core.Response;
import org.springframework.data.domain.Page;

public class PageUtils {
  public static Response pageResponse(Page<?> page, long totalElements, long pageNumber, long pageSize) {
    Response.ResponseBuilder responseBuilder = Response.ok();
    Response.ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, totalElements, pageNumber, pageSize);
    return responseBuilderWithLinks.entity(page.getContent()).build();
  }
}
