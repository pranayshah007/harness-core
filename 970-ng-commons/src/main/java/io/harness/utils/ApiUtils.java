/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SortOrder;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.common.beans.NGTag;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections.CollectionUtils;

@OwnedBy(HarnessTeam.PL)
@UtilityClass
public class ApiUtils {
  public final String X_TOTAL_ELEMENTS = "X-Total-Elements";
  public final String X_PAGE_NUMBER = "X-Page-Number";
  public final String X_PAGE_SIZE = "X-Page-Size";

  public ResponseBuilder addLinksHeader(
      ResponseBuilder responseBuilder, long totalElements, long pageNumber, long pageSize) {
    responseBuilder.header(X_TOTAL_ELEMENTS, totalElements);
    responseBuilder.header(X_PAGE_NUMBER, pageNumber);
    responseBuilder.header(X_PAGE_SIZE, pageSize);
    return responseBuilder;
  }

  public static Map<String, String> getTags(List<NGTag> tags) {
    if (CollectionUtils.isEmpty(tags)) {
      return Collections.emptyMap();
    }
    return tags.stream().collect(Collectors.toMap(NGTag::getKey, NGTag::getValue));
  }

  public static PageRequest getPageRequest(Integer page, Integer limit, String field, String order) {
    if (field == null && order == null) {
      return PageRequest.builder().pageIndex(page).pageSize(limit).build();
    }
    if (order == null || (!order.equalsIgnoreCase("asc") && !order.equalsIgnoreCase("desc"))) {
      throw new InvalidRequestException("Order of sorting unidentified or null. Accepted values: ASC / DESC");
    }
    List<SortOrder> sortOrders = null;
    if (field != null) {
      switch (field) {
        case "identifier":
          break;
        case "name":
          break;
        case "created":
          field = "createdAt";
          break;
        case "updated":
          field = "lastUpdatedAt";
          break;
        case "modified":
          field = "lastModifiedAt";
          break;
        default:
          throw new InvalidRequestException(
              "Field provided for sorting unidentified. Accepted values: identifier / name / created / updated");
      }
      SortOrder sortOrder = new SortOrder(field + "," + order);
      sortOrders = Collections.singletonList(sortOrder);
    }
    return PageRequest.builder().pageIndex(page).pageSize(limit).sortOrders(sortOrders).build();
  }
}
