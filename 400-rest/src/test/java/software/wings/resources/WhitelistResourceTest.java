/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.RAMA;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static java.lang.String.format;
import static javax.ws.rs.client.Entity.entity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import software.wings.WingsBaseTest;
import software.wings.beans.security.access.Whitelist;
import software.wings.beans.security.access.WhitelistStatus;
import software.wings.exception.WingsExceptionMapper;
import software.wings.service.intfc.HarnessUserGroupService;
import software.wings.service.intfc.WhitelistService;
import software.wings.utils.ResourceTestRule;

import com.google.common.collect.Lists;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

/**
 * @author rktummala on 04/11/18
 */
@Slf4j
public class WhitelistResourceTest extends WingsBaseTest {
  private static final WhitelistService WHITELIST_SERVICE = mock(WhitelistService.class);
  private static final HarnessUserGroupService HARNESS_USER_GROUP_SERVICE = mock(HarnessUserGroupService.class);

  @Captor private ArgumentCaptor<PageRequest<Whitelist>> pageRequestArgumentCaptor;

  /**
   * The constant RESOURCES.
   */
  @ClassRule
  public static final ResourceTestRule RESOURCES =
      ResourceTestRule.builder()
          .instance(new WhitelistResource(WHITELIST_SERVICE, HARNESS_USER_GROUP_SERVICE))
          .type(WingsExceptionMapper.class)
          .build();

  private static String WHITELIST_ID = "WHITELIST_ID";
  private static final Whitelist WHITELIST = Whitelist.builder()
                                                 .accountId(ACCOUNT_ID)
                                                 .uuid(WHITELIST_ID)
                                                 .filter("127.0.0.1")
                                                 .status(WhitelistStatus.ACTIVE)
                                                 .build();

  /**
   * Should create whitelist config.
   */
  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldCreateWhitelist() {
    Whitelist whitelist2 = Whitelist.builder()
                               .accountId(ACCOUNT_ID)
                               .uuid(generateUuid())
                               .filter("10.01.10.01")
                               .status(WhitelistStatus.ACTIVE)
                               .build();
    when(WHITELIST_SERVICE.save(WHITELIST)).thenReturn(whitelist2);

    RestResponse<Whitelist> restResponse =
        RESOURCES.client()
            .target(format("/whitelist?accountId=%s", ACCOUNT_ID))
            .request()
            .post(entity(WHITELIST, MediaType.APPLICATION_JSON), new GenericType<RestResponse<Whitelist>>() {});

    assertThat(restResponse).isNotNull().hasFieldOrPropertyWithValue("resource", whitelist2);
    verify(WHITELIST_SERVICE).save(WHITELIST);
  }

  /**
   * Should update whitelist config.
   */
  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldUpdateWhitelist() {
    Whitelist whitelist2 = Whitelist.builder()
                               .accountId(ACCOUNT_ID)
                               .uuid(generateUuid())
                               .filter("10.01.10.01")
                               .status(WhitelistStatus.ACTIVE)
                               .build();
    when(WHITELIST_SERVICE.update(WHITELIST)).thenReturn(whitelist2);

    RestResponse<Whitelist> restResponse =
        RESOURCES.client()
            .target(format("/whitelist/%s?accountId=%s", WHITELIST_ID, ACCOUNT_ID))
            .request()
            .put(entity(WHITELIST, MediaType.APPLICATION_JSON), new GenericType<RestResponse<Whitelist>>() {});

    assertThat(restResponse).isNotNull().hasFieldOrPropertyWithValue("resource", whitelist2);
    verify(WHITELIST_SERVICE).update(WHITELIST);
  }

  /**
   * Should update whitelist config.
   */
  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldDeleteWhitelist() {
    when(WHITELIST_SERVICE.delete(ACCOUNT_ID, WHITELIST_ID)).thenReturn(true);

    RestResponse<Boolean> restResponse = RESOURCES.client()
                                             .target(format("/whitelist/%s?accountId=%s", WHITELIST_ID, ACCOUNT_ID))
                                             .request()
                                             .delete(new GenericType<RestResponse<Boolean>>() {});

    assertThat(restResponse).isNotNull().hasFieldOrPropertyWithValue("resource", Boolean.TRUE);
    verify(WHITELIST_SERVICE).delete(ACCOUNT_ID, WHITELIST_ID);
  }

  /**
   * Should list whitelist config.
   */
  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldList() {
    PageResponse<Whitelist> pageResponse = aPageResponse().withResponse(Lists.newArrayList(WHITELIST)).build();
    when(WHITELIST_SERVICE.list(any(String.class), any(PageRequest.class))).thenReturn(pageResponse);

    RestResponse<PageResponse<Whitelist>> restResponse =
        RESOURCES.client()
            .target(format("/whitelist?accountId=%s", ACCOUNT_ID))
            .request()
            .get(new GenericType<RestResponse<PageResponse<Whitelist>>>() {});

    log.info(JsonUtils.asJson(restResponse));
    verify(WHITELIST_SERVICE).list(anyString(), pageRequestArgumentCaptor.capture());
    assertThat(pageRequestArgumentCaptor.getValue()).isNotNull();
    assertThat(restResponse).isNotNull().hasFieldOrPropertyWithValue("resource", pageResponse);
  }

  /**
   * Should read whitelist config.
   */
  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldRead() {
    when(WHITELIST_SERVICE.get(ACCOUNT_ID, WHITELIST_ID)).thenReturn(WHITELIST);

    RestResponse<Whitelist> restResponse = RESOURCES.client()
                                               .target(format("/whitelist/%s?accountId=%s", WHITELIST_ID, ACCOUNT_ID))
                                               .request()
                                               .get(new GenericType<RestResponse<Whitelist>>() {});

    assertThat(restResponse).isNotNull().hasFieldOrPropertyWithValue("resource", WHITELIST);
    verify(WHITELIST_SERVICE).get(ACCOUNT_ID, WHITELIST_ID);
  }

  /**
   * Should read whitelist config.
   */
  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void isIpWhitelisted() {
    when(WHITELIST_SERVICE.isValidIPAddress(ACCOUNT_ID, "127.0.0.1")).thenReturn(true);

    RestResponse<Boolean> restResponse =
        RESOURCES.client()
            .target(format("/whitelist/ip-address-whitelisted?accountId=%s&ipAddress=%s", ACCOUNT_ID, "127.0.0.1"))
            .request()
            .get(new GenericType<RestResponse<Boolean>>() {});

    assertThat(restResponse.getResource()).isEqualTo(Boolean.TRUE);
    verify(WHITELIST_SERVICE).isValidIPAddress(ACCOUNT_ID, "127.0.0.1");
  }
}
