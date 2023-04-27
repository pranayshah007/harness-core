/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.rule.OwnerRule.UNKNOWN;

import static software.wings.beans.EntityVersion.Builder.anEntityVersion;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.beans.EntityVersion;
import software.wings.beans.EntityVersionCollection;
import software.wings.exception.WingsExceptionMapper;
import software.wings.service.intfc.EntityVersionService;
import software.wings.utils.ResourceTestRule;

import javax.ws.rs.core.GenericType;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Created by peeyushaggarwal on 11/2/16.
 */
public class VersionResourceTest extends CategoryTest {
  private static EntityVersionService ENTITY_VERSION_SERVICE = mock(EntityVersionService.class);

  @ClassRule
  public static final ResourceTestRule RESOURCES = ResourceTestRule.builder()
                                                       .instance(new VersionResource(ENTITY_VERSION_SERVICE))
                                                       .type(WingsExceptionMapper.class)
                                                       .build();

  @Test
  @Owner(developers = UNKNOWN)
  @Category(UnitTests.class)
  public void shouldListVersions() throws Exception {
    PageResponse<EntityVersion> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList(anEntityVersion().build()));
    pageResponse.setTotal(1l);
    when(ENTITY_VERSION_SERVICE.listEntityVersions(any(PageRequest.class))).thenReturn(pageResponse);
    RestResponse<PageResponse<EntityVersion>> restResponse =
        RESOURCES.client()
            .target("/versions?appId=" + APP_ID)
            .request()
            .get(new GenericType<RestResponse<PageResponse<EntityVersion>>>() {});
    PageRequest<EntityVersionCollection> pageRequest = new PageRequest<>();
    pageRequest.setOffset("0");
    verify(ENTITY_VERSION_SERVICE).listEntityVersions(pageRequest);
    assertThat(restResponse.getResource().getResponse().size()).isEqualTo(1);
    assertThat(restResponse.getResource().getResponse().get(0)).isNotNull();
  }
}
