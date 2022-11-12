/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.rule.OwnerRule.BHAVYA;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.VIKAS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.beans.PageResponse;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PageUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testGetPageRequest_When_NoSortDirectionProvided() {
    List<String> sortList = new ArrayList<>();
    sortList.add("name");
    Pageable pageable = PageUtils.getPageRequest(1, sortList.size(), sortList);
    assertThat(pageable).isNotNull();
    assertThat(pageable.getPageSize()).isEqualTo(sortList.size());
    assertThat(pageable.getSort()).isNotNull();
    assertThat(pageable.getSort().getOrderFor("name")).isNotNull();
    assertThat(pageable.getSort().getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.ASC);
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testGetPageRequest_When_SortDirectionProvided() {
    List<String> sortList = new ArrayList<>();
    sortList.add("name,desc");
    Pageable pageable = PageUtils.getPageRequest(1, sortList.size(), sortList);
    assertThat(pageable).isNotNull();
    assertThat(pageable.getPageSize()).isEqualTo(sortList.size());
    assertThat(pageable.getSort()).isNotNull();
    assertThat(pageable.getSort().getOrderFor("name")).isNotNull();
    assertThat(pageable.getSort().getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.DESC);
  }

  @Test(expected = IllegalArgumentException.class)
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testGetPageRequest_When_InvalidSortDirectionProvided() {
    List<String> sortList = new ArrayList<>();
    sortList.add("name,des");
    PageUtils.getPageRequest(1, sortList.size(), sortList);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testOffsetAndLimit_whenNotEnoughElements() {
    assertThatThrownBy(() -> PageUtils.offsetAndLimit(Lists.newArrayList("1", "2"), 1, 4))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("for a list of size 2 the offset 1 and pagesize 4 is invalid");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testOffsetAndLimit() {
    int numOfElements = 35;
    List<String> input = new ArrayList<>();
    for (int i = 0; i < numOfElements; i++) {
      input.add("e-" + i);
    }

    // assert all are returned if page size is big enough
    PageResponse<String> output = PageUtils.offsetAndLimit(input, 0, numOfElements);
    assertThat(output.getTotalPages()).isEqualTo(1);
    assertThat(output.getPageIndex()).isEqualTo(0);
    assertThat(output.getTotalItems()).isEqualTo(numOfElements);
    assertThat(output.getContent()).isEqualTo(input);

    output = PageUtils.offsetAndLimit(input, 0, 4);
    assertThat(output.getTotalPages()).isEqualTo(9);
    assertThat(output.getPageIndex()).isEqualTo(0);
    assertThat(output.getTotalItems()).isEqualTo(numOfElements);
    assertThat(output.getPageItemCount()).isEqualTo(4);
    assertThat(output.getContent()).isEqualTo(Lists.newArrayList("e-0", "e-1", "e-2", "e-3"));

    output = PageUtils.offsetAndLimit(input, 3, 7);
    assertThat(output.getTotalPages()).isEqualTo(5);
    assertThat(output.getPageIndex()).isEqualTo(3);
    assertThat(output.getTotalItems()).isEqualTo(numOfElements);
    assertThat(output.getPageItemCount()).isEqualTo(7);
    assertThat(output.getContent())
        .isEqualTo(Lists.newArrayList("e-21", "e-22", "e-23", "e-24", "e-25", "e-26", "e-27"));

    // last page
    output = PageUtils.offsetAndLimit(input, 8, 4);
    assertThat(output.getTotalPages()).isEqualTo(9);
    assertThat(output.getPageIndex()).isEqualTo(8);
    assertThat(output.getTotalItems()).isEqualTo(numOfElements);
    assertThat(output.getPageItemCount()).isEqualTo(3);
    assertThat(output.getContent()).isEqualTo(Lists.newArrayList("e-32", "e-33", "e-34"));
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetPage() {
    int numOfElements = 35;
    List<String> input = new ArrayList<>();
    for (int i = 0; i < numOfElements; i++) {
      input.add("e-" + i);
    }

    Page<String> output = PageUtils.getPage(input, 0, numOfElements);
    assertThat(output.getTotalElements()).isEqualTo(numOfElements);
    assertThat(output.getContent()).isEqualTo(input);
    assertThat(output.getTotalPages()).isEqualTo(1);
    assertThat(output.getNumberOfElements()).isEqualTo(numOfElements);

    output = PageUtils.getPage(input, 0, 4);
    assertThat(output.getNumberOfElements()).isEqualTo(4);
    assertThat(output.getContent()).isEqualTo(input.subList(0, 4));
    assertThat(output.getTotalPages()).isEqualTo(9);
    assertThat(output.getTotalElements()).isEqualTo(numOfElements);

    output = PageUtils.getPage(input, 3, 7);
    assertThat(output.getNumberOfElements()).isEqualTo(7);
    assertThat(output.getContent()).isEqualTo(input.subList(21, 28));
    assertThat(output.getTotalPages()).isEqualTo(5);
    assertThat(output.getTotalElements()).isEqualTo(numOfElements);

    output = PageUtils.getPage(input, 8, 4);
    assertThat(output.getNumberOfElements()).isEqualTo(3);
    assertThat(output.getContent()).isEqualTo(input.subList(32, 35));
    assertThat(output.getTotalPages()).isEqualTo(9);
    assertThat(output.getTotalElements()).isEqualTo(numOfElements);

    output = PageUtils.getPage(input, 9, 4);
    assertThat(output.getNumberOfElements()).isEqualTo(0);
    assertThat(output.getContent()).isEqualTo(Collections.emptyList());
    assertThat(output.getTotalPages()).isEqualTo(9);
    assertThat(output.getTotalElements()).isEqualTo(numOfElements);

    output = PageUtils.getPage(Collections.emptyList(), 0, 4);
    assertThat(output.getNumberOfElements()).isEqualTo(0);
    assertThat(output.getContent()).isEqualTo(Collections.emptyList());
    assertThat(output.getTotalPages()).isEqualTo(0);
    assertThat(output.getTotalElements()).isEqualTo(0);
  }
}
