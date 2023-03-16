/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.rule.OwnerRule.MANKRIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ApiUtilsTest {
  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testAddHeaders() {
    int page = 0;
    int limit = 10;
    for (int currentResultCount = 0; currentResultCount < limit; currentResultCount++) {
      ResponseBuilder responseBuilder = Response.ok();
      ApiUtils.addLinksHeader(responseBuilder, currentResultCount, page, limit);
      Response response = responseBuilder.build();
      assertThat(response.getHeaders()).isNotEmpty();
      assertThat(response.getHeaders()).hasSize(3);
      assertThat(response.getHeaderString(ApiUtils.X_TOTAL_ELEMENTS)).isEqualTo(Long.toString(currentResultCount));
      assertThat(response.getHeaderString(ApiUtils.X_PAGE_SIZE)).isEqualTo(Long.toString(limit));
      assertThat(response.getHeaderString(ApiUtils.X_PAGE_NUMBER)).isEqualTo(Long.toString(page));
    }
  }
}
