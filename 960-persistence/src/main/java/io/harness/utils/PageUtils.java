/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SortOrder;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@UtilityClass
@OwnedBy(PL)
public class PageUtils {
  public final String COMMA_SEPARATOR = ",";

  public static Pageable getPageRequest(int page, int size, List<String> sort) {
    if (isEmpty(sort)) {
      return org.springframework.data.domain.PageRequest.of(page, size);
    }

    List<Sort.Order> orders = new ArrayList<>();
    for (String propOrder : sort) {
      String[] propOrderSplit = propOrder.split(COMMA_SEPARATOR);
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

  public static Pageable getPageRequest(PageRequest pageRequestDTO) {
    List<String> sortOrders = new ArrayList<>();
    if (pageRequestDTO.getSortOrders() != null) {
      for (SortOrder sortOrder : pageRequestDTO.getSortOrders()) {
        sortOrders.add(sortOrder.getFieldName() + COMMA_SEPARATOR + sortOrder.getOrderType());
      }
    }
    return getPageRequest(pageRequestDTO.getPageIndex(), pageRequestDTO.getPageSize(), sortOrders);
  }

  public static <T> PageResponse<T> getNGPageResponse(Page<T> page) {
    return PageResponse.<T>builder()
        .totalPages(page.getTotalPages())
        .totalItems(page.getTotalElements())
        .pageItemCount(page.getContent().size())
        .content(page.getContent())
        .pageSize(page.getSize())
        .pageIndex(page.getNumber())
        .empty(page.isEmpty())
        .build();
  }

  public static <T, C> PageResponse<C> getNGPageResponse(Page<T> page, List<C> content) {
    return PageResponse.<C>builder()
        .totalPages(page.getTotalPages())
        .totalItems(page.getTotalElements())
        .pageItemCount((content != null) ? content.size() : 0)
        .content(content)
        .pageSize(page.getSize())
        .pageIndex(page.getNumber())
        .empty(page.isEmpty())
        .build();
  }

  public static <T, C> PageResponse<C> getNGPageResponse(PageResponse<T> page, List<C> content) {
    return PageResponse.<C>builder()
        .totalPages(page.getTotalPages())
        .totalItems(page.getTotalItems())
        .pageItemCount((content != null) ? content.size() : 0)
        .content(content)
        .pageSize(page.getPageSize())
        .pageIndex(page.getPageIndex())
        .empty(page.isEmpty())
        .build();
  }

  public static <T> PageResponse<T> getNGPageResponse(io.harness.beans.PageResponse<T> page) {
    return PageResponse.<T>builder()
        .totalPages((page.getPageSize() == 0) ? 0 : (page.getTotal() + page.getPageSize() - 1) / page.getPageSize())
        .totalItems(page.getTotal())
        .pageItemCount((page.getResponse() != null) ? page.getResponse().size() : 0)
        .content(page.getResponse())
        .pageSize(page.getPageSize())
        .pageIndex(page.getStart())
        .empty(page.isEmpty())
        .build();
  }

  public static <T> PageResponse<T> offsetAndLimit(List<T> input, int offset, int pageSize) {
    if (input.size() < offset * pageSize) {
      throw new InvalidRequestException(String.format(
          "for a list of size %s the offset %s and pagesize %s is invalid", input.size(), offset, pageSize));
    }

    int startIndex = offset * pageSize;
    int endIndex = startIndex + pageSize < input.size() ? startIndex + pageSize : input.size();
    int totalPages = (input.size() / pageSize) + (input.size() % pageSize == 0 ? 0 : 1);
    List<T> returnList = input.subList(startIndex, endIndex);
    return PageResponse.<T>builder()
        .pageSize(pageSize)
        .pageIndex(offset)
        .totalPages(totalPages)
        .totalItems(input.size())
        .pageItemCount(returnList.size())
        .content(returnList)
        .build();
  }

  public static <T> PageResponse<T> offsetAndLimit(List<T> input, long count, int offset, int pageSize) {
    if (count < (long) offset * pageSize) {
      throw new InvalidRequestException(
          String.format("for a list of size %s the offset %s and pagesize %s is invalid", count, offset, pageSize));
    }

    long totalPages = (count / pageSize) + (count % pageSize == 0 ? 0 : 1);
    return PageResponse.<T>builder()
        .pageSize(pageSize)
        .pageIndex(offset)
        .totalPages(totalPages)
        .totalItems(count)
        .pageItemCount(input.size())
        .content(input)
        .build();
  }

  public static <T> Page<T> getPage(List<T> input, int offset, int pageSize) {
    if (offset < 0) {
      throw new IllegalArgumentException("Page index must not be less than zero!");
    }
    if (pageSize < 1) {
      throw new IllegalArgumentException("Page size must not be less than one!");
    }
    if (input == null) {
      return Page.empty();
    }
    if (input.size() < offset * pageSize) {
      return new PageImpl(Collections.emptyList(), PageUtils.getPageRequest(offset, pageSize, Collections.EMPTY_LIST),
          (long) input.size());
    }

    int startIndex = offset * pageSize;
    int endIndex = Math.min(startIndex + pageSize, input.size());
    List<T> returnList = input.subList(startIndex, endIndex);
    return new PageImpl(
        returnList, PageUtils.getPageRequest(offset, pageSize, Collections.EMPTY_LIST), (long) input.size());
  }

  public Pageable getPageRequest(int page, int size, List<String> sort, Sort sortBy) {
    try {
      if (EmptyPredicate.isEmpty(sort)) {
        return org.springframework.data.domain.PageRequest.of(page, size, sortBy);
      } else {
        return PageUtils.getPageRequest(page, size, sort);
      }
    } catch (Exception e) {
      throw new InvalidRequestException(e.getMessage(), e);
    }
  }

  public enum SortFields {
    IDENTIFIER("identifier"),
    NAME("name"),
    CREATED("created"),
    UPDATED("updated"),
    UNSUPPORTED(null);

    private String field;

    SortFields(String field) {
      this.field = field;
    }

    public String value() {
      return field;
    }

    @Override
    public String toString() {
      return String.valueOf(field);
    }

    public static SortFields fromValue(String value) {
      for (SortFields sortField : SortFields.values()) {
        if (String.valueOf(sortField.field).equals(value)) {
          return sortField;
        }
      }
      return null;
    }
  }
}
