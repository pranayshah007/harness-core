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
import static io.harness.rule.OwnerRule.NISHANT;

import static javax.ws.rs.core.UriBuilder.fromPath;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Set;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ApiUtilsTest {
  public static final String PATH = "/some/path";

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testAddLinksHeaderForOnlyOnePage() {
    int page = 0;
    int limit = 10;
    for (int currentResultCount = 0; currentResultCount < limit; currentResultCount++) {
      ResponseBuilder responseBuilder = Response.ok();
      ApiUtils.addLinksHeader(responseBuilder, PATH, currentResultCount, page, limit);
      Response response = responseBuilder.build();
      assertThat(response.getLinks()).isNotEmpty();
      assertThat(response.getLinks()).hasSize(1);
      assertLink(response.getLinks(), page, limit, SELF_REL);
    }
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testAddLinksHeaderShouldHaveNextPage() {
    int currentResultCount = 10;
    int page = 0;
    int limit = 10;
    ResponseBuilder responseBuilder = Response.ok();
    ApiUtils.addLinksHeader(responseBuilder, PATH, currentResultCount, page, limit);
    Response response = responseBuilder.build();
    assertThat(response.getLinks()).isNotEmpty();
    assertThat(response.getLinks()).hasSize(2);
    Set<Link> links = response.getLinks();
    assertLink(links, page, limit, SELF_REL);
    assertLink(links, page + 1, limit, NEXT_REL);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testAddLinksHeaderShouldHaveAllPage() {
    int currentResultCount = 10;
    int limit = 10;
    for (int page = 1; page < 10; page++) {
      ResponseBuilder responseBuilder = Response.ok();
      ApiUtils.addLinksHeader(responseBuilder, PATH, currentResultCount, page, limit);
      Response response = responseBuilder.build();
      assertThat(response.getLinks()).isNotEmpty();
      assertThat(response.getLinks()).hasSize(3);
      Set<Link> links = response.getLinks();

      assertLink(links, page - 1, limit, PREVIOUS_REL);
      assertLink(links, page, limit, SELF_REL);
      assertLink(links, page + 1, limit, NEXT_REL);
    }
  }

  private void assertLink(Set<Link> link, int expectedPage, int expectedLimit, String expectedRel) {
    Link expectedLink =
        Link.fromUri(fromPath(PATH).queryParam(PAGE, expectedPage).queryParam(PAGE_SIZE, expectedLimit).build())
            .rel(expectedRel)
            .build();
    assertThat(link).contains(expectedLink);
  }
}
