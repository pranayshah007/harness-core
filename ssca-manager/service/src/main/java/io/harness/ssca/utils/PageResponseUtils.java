/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.utils;

import io.harness.beans.SortOrder;
import io.harness.ng.beans.PageRequest;
import io.harness.utils.ApiUtils;
import io.harness.utils.PageUtils;

import java.util.Arrays;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public class PageResponseUtils {
  public static Pageable getPageable(Integer page, Integer limit, String sort, String order) {
    SortOrder sortOrder = new SortOrder();
    sortOrder.setFieldName(sort);
    sortOrder.setOrderType(SortOrder.OrderType.valueOf(order));
    return PageUtils.getPageRequest(new PageRequest(page, limit, Arrays.asList(sortOrder)));
  }

  public static <T> Response getPagedResponse(Page<T> entities) {
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, entities.getTotalElements(), entities.getNumber(), entities.getSize());
    return responseBuilderWithLinks.entity(entities.getContent()).build();
  }
}
