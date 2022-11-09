/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.TEJAS;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.fail;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.remote.client.CGRestUtils;
import io.harness.rule.Owner;

import java.util.Optional;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(PL)
public class AccountOrgProjectHelperImplTest extends CategoryTest {
  @Mock OrganizationService organizationService;
  @Mock ProjectService projectService;
  @Mock AccountClient accountclient;

  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;

  private AccountOrgProjectHelperImpl accountOrgProjectHelper;

  @Before
  public void setup() throws NoSuchFieldException {
    initMocks(this);
    accountIdentifier = randomAlphabetic(10);
    orgIdentifier = randomAlphabetic(10);
    projectIdentifier = randomAlphabetic(10);
    accountOrgProjectHelper = new AccountOrgProjectHelperImpl(organizationService, projectService, accountclient);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void test_getProjectName_notFound() {
    when(projectService.get(accountIdentifier, orgIdentifier, projectIdentifier)).thenReturn(Optional.empty());
    try {
      accountOrgProjectHelper.getProjectName(accountIdentifier, orgIdentifier, projectIdentifier);
      fail();
    } catch (NotFoundException ex) {
      assertEquals(ex.getMessage(), String.format("Project with identifier [%s] doesn't exist", projectIdentifier));
    } catch (Exception ex) {
      fail();
    }
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void test_getOrgtName_notFound() {
    when(organizationService.get(accountIdentifier, orgIdentifier)).thenReturn(Optional.empty());
    try {
      accountOrgProjectHelper.getOrgName(accountIdentifier, orgIdentifier);
      fail();
    } catch (NotFoundException ex) {
      assertEquals(ex.getMessage(), String.format("Organization with identifier [%s] doesn't exist", orgIdentifier));
    } catch (Exception ex) {
      fail();
    }
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void test_getAccountName_notFound() {
    mockStatic(CGRestUtils.class);
    when(CGRestUtils.getResponse(any())).thenReturn(null);
    try {
      accountOrgProjectHelper.getAccountName(accountIdentifier);
      fail();
    } catch (NotFoundException ex) {
      assertEquals(ex.getMessage(), String.format("Account with identifier [%s] doesn't exist", accountIdentifier));
    } catch (Exception ex) {
      fail();
    }
  }
}
