/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@OwnedBy(PL)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel(value = "Page")
public class PageResponse<T> {
  long totalPages;
  long totalItems;
  long pageItemCount;
  long pageSize;
  List<T> content;
  long pageIndex;
  boolean empty;

  public <U> PageResponse<U> map(Function<? super T, ? extends U> converter) {
    List<U> convertedContent = this.content.stream().map(converter).collect(Collectors.toList());
    return new PageResponseBuilder<U>()
        .totalPages(this.totalPages)
        .totalItems(this.totalItems)
        .pageItemCount(this.pageItemCount)
        .pageSize(this.pageSize)
        .content(convertedContent)
        .pageIndex(this.pageIndex)
        .empty(this.empty)
        .build();
  }

  public PageResponseBuilder<T> but() {
    return new PageResponseBuilder<T>()
        .totalPages(this.getTotalPages())
        .totalItems(this.getTotalItems())
        .pageItemCount(this.getPageItemCount())
        .pageSize(this.getPageSize())
        .content(this.content != null ? List.copyOf(this.getContent()) : null)
        .pageIndex(this.getPageIndex())
        .empty(this.isEmpty());
  }

  /**
   * Creates empty {@link PageResponse} with pageSize and pageIndex from the {@link PageRequest}. This is a method and
   * not a static field in {@link PageRequest} because it should return type parameter in the returned object which
   * could not be achieved if it was done as static field.
   * @param pageRequest
   * @param <T>
   * @return
   */
  public static <T> PageResponse<T> getEmptyPageResponse(PageRequest pageRequest) {
    return new PageResponseBuilder<T>()
        .totalPages(0)
        .totalItems(0)
        .pageItemCount(0)
        .pageSize(Objects.nonNull(pageRequest) ? pageRequest.getPageSize() : 0)
        .content(Collections.emptyList())
        .pageIndex(Objects.nonNull(pageRequest) ? pageRequest.getPageIndex() : 0)
        .empty(true)
        .build();
  }
}
