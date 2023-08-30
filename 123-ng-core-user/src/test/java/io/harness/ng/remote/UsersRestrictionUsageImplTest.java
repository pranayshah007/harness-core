/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.enforcement.beans.metadata.StaticLimitRestrictionMetadataDTO;
import io.harness.ng.core.remote.UsersRestrictionUsageImpl;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(PL)
public class UsersRestrictionUsageImplTest extends CategoryTest {
  @Mock NgUserService ngUserService;
  @Inject @InjectMocks UsersRestrictionUsageImpl usersRestrictionUsage;
  private String accountIdentifier;

  @Before
  public void setup() throws NoSuchFieldException {
    initMocks(this);
    accountIdentifier = randomAlphabetic(10);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testCurrentCount() {
    final ArgumentCaptor<Scope> userMembershipCriteriaArgumentCaptor = ArgumentCaptor.forClass(Scope.class);
    when(ngUserService.getNgUsersCount(any())).thenReturn(1L);
    StaticLimitRestrictionMetadataDTO metadataDTO = new StaticLimitRestrictionMetadataDTO();
    usersRestrictionUsage.getCurrentValue(accountIdentifier, metadataDTO);
    verify(ngUserService, times(1)).getNgUsersCount(userMembershipCriteriaArgumentCaptor.capture());
    Scope scope = userMembershipCriteriaArgumentCaptor.getValue();
    assertNotNull(scope);
    assertEquals(accountIdentifier, scope.getAccountIdentifier());
  }
}
