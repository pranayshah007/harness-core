/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.usage.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class PageableUtils {
  public Pageable getPageRequest(int page, int size, List<String> sort, Sort sortBy) {
    try {
      if (isEmpty(sort)) {
        return PageRequest.of(page, size, sortBy);
      } else {
        return getPageRequest(page, size, sort);
      }
    } catch (Exception e) {
      throw new InvalidRequestException(e.getMessage(), e);
    }
  }

  public static Pageable getPageRequest(int page, int size, List<String> sort) {
    if (isEmpty(sort)) {
      return PageRequest.of(page, size);
    }

    List<Sort.Order> orders = new ArrayList<>();
    for (String propOrder : sort) {
      String[] propOrderSplit = propOrder.split(",");
      String property = propOrderSplit[0];
      if (propOrderSplit.length == 1) {
        orders.add(new Sort.Order(Sort.Direction.ASC, property));
      } else {
        Sort.Direction direction = Sort.Direction.fromString(propOrderSplit[1]);
        orders.add(new Sort.Order(direction, property));
      }
    }

    return org.springframework.data.domain.PageRequest.of(page, size, Sort.by(orders));
  }

  public static void validateSort(Sort sort, List<String> allowedSortProperties) {
    for (Sort.Order next : sort) {
      String property = next.getProperty();
      if (!allowedSortProperties.contains(property)) {
        throw new InvalidArgumentsException(format("Invalid sort property: %s", property));
      }
    }
  }
}
