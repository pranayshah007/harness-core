/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.MEENAKSHI;
import static io.harness.utils.PageUtils.getPageRequest;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.OrganizationRequest;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.rule.Owner;
import io.harness.utils.PageUtils;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Pageable;

@OwnedBy(PL)
public class UserGroupResourceTest extends CategoryTest {
  @Mock private UserGroupService userGroupService;
  @Mock private AccessControlClient accessControlClient;

  @InjectMocks UserGroupResource userGroupResource;

  String accountIdentifier = randomAlphabetic(10);
  String identifier = randomAlphabetic(10);
  String name = randomAlphabetic(10);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testListFilter_withAccessOnResourceType() {
    when(accessControlClient.hasAccess(any(), any(), any(), any())).thenReturn(true);
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();

    userGroupResource.list(accountIdentifier, null, pageRequest);

    verify(userGroupService, times(1)).list(null, getPageRequest(pageRequest));
  }
}
