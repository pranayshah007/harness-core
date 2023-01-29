/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.NGCommonEntityConstants.NEXT_REL;
import static io.harness.NGCommonEntityConstants.PAGE;
import static io.harness.NGCommonEntityConstants.PAGE_SIZE;
import static io.harness.NGCommonEntityConstants.PREVIOUS_REL;
import static io.harness.NGCommonEntityConstants.SELF_REL;

import static javax.ws.rs.core.UriBuilder.fromPath;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SortOrder;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.common.beans.NGTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections.CollectionUtils;

@OwnedBy(HarnessTeam.PL)
@UtilityClass
public class ApiUtils {
  public ResponseBuilder addLinksHeader(
      ResponseBuilder responseBuilder, String path, int currentResultCount, int page, int limit) {
    ArrayList<Link> links = new ArrayList<>();

    links.add(
        Link.fromUri(fromPath(path).queryParam(PAGE, page).queryParam(PAGE_SIZE, limit).build()).rel(SELF_REL).build());

    if (page >= 1) {
      links.add(Link.fromUri(fromPath(path).queryParam(PAGE, page - 1).queryParam(PAGE_SIZE, limit).build())
                    .rel(PREVIOUS_REL)
                    .build());
    }
    if (limit == currentResultCount) {
      links.add(Link.fromUri(fromPath(path).queryParam(PAGE, page + 1).queryParam(PAGE_SIZE, limit).build())
                    .rel(NEXT_REL)
                    .build());
    }
    return responseBuilder.links(links.toArray(new Link[0]));
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
