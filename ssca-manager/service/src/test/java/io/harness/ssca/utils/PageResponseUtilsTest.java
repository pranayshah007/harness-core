/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.utils;

import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.SSCAManagerTestBase;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Arrays;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PageResponseUtilsTest extends SSCAManagerTestBase {
  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetPageable() {
    Pageable pageable = PageResponseUtils.getPageable(0, 5, "name", "ASC");
    assertThat(pageable.getPageNumber()).isEqualTo(0);
    assertThat(pageable.getPageSize()).isEqualTo(5);
    assertThat(pageable.getSort()).isEqualTo(Sort.by("name").ascending());
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetPagedResponse() {
    Page<String> page =
        new PageImpl<>(Arrays.asList("item1", "item2", "item3", "item4", "item5"), Pageable.ofSize(2).withPage(1), 5);
    Response pageResponse = PageResponseUtils.getPagedResponse(page);
    assertThat(pageResponse.getHeaders().get("X-Total-Elements").get(0)).isEqualTo(5L);
    assertThat(pageResponse.getHeaders().get("X-Page-Number").get(0)).isEqualTo(1L);
    assertThat(pageResponse.getHeaders().get("X-Page-Size").get(0)).isEqualTo(2L);
  }
}
