/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.PHOENIKX;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.resources.UserGroupResource;
import software.wings.service.intfc.UserGroupService;

import com.google.inject.Inject;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class UserGroupResourceTest extends WingsBaseTest {
  private UserGroupService userGroupService = mock(UserGroupService.class);
  private static final String DEMO_ACCOUNT_ID = randomAlphabetic(10);
  @Inject @InjectMocks private UserGroupResource userGroupResource;

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void shouldSearchUsers() {
    when(userGroupService.list(anyString(), any(), anyBoolean(), any(), any())).thenReturn(aPageResponse().build());
    userGroupResource.list(aPageRequest().build(), UUIDGenerator.generateUuid(), "xyz", false, null);
    verify(userGroupService).list(anyString(), any(), anyBoolean(), any(), any());
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void test_getCountOfUserGroups() {
    Long countOfUserGroups = ThreadLocalRandom.current().nextLong(1, 10000);
    when(userGroupService.getCountOfUserGroups(DEMO_ACCOUNT_ID)).thenReturn(countOfUserGroups);
    RestResponse<Long> response = userGroupResource.getCountOfUserGroups(DEMO_ACCOUNT_ID);

    verify(userGroupService, times(1)).getCountOfUserGroups(DEMO_ACCOUNT_ID);
    assertThat(countOfUserGroups).isEqualTo(response.getResource());
    verifyNoMoreInteractions(userGroupService);
  }
}
