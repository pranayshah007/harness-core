/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.rule.OwnerRule.HANTANG;
import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static java.lang.String.format;
import static javax.ws.rs.client.Entity.entity;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.budget.BudgetService;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.utils.ResourceTestRule;

import java.util.List;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class BudgetResourceTest extends CategoryTest {
  private String accountId = "ACCOUNT_ID";
  private String budgetId = "BUDGET_ID";
  private String cloneBudgetName = "CLONE";
  private Budget budget;

  private static BudgetService budgetService = mock(BudgetService.class);
  @ClassRule
  public static ResourceTestRule RESOURCES =
      ResourceTestRule.builder().instance(new BudgetResource(budgetService)).build();

  @Before
  public void setUp() {
    budget = Budget.builder().accountId(accountId).build();
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testSave() {
    RESOURCES.client()
        .target(format("/budgets/?accountId=%s", accountId))
        .request()
        .post(entity(budget, MediaType.APPLICATION_JSON), new GenericType<RestResponse<Budget>>() {});
    verify(budgetService).create(eq(budget));
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testClone() {
    RESOURCES.client()
        .target(format("/budgets/%s/?accountId=%s&cloneName=%s", budgetId, accountId, cloneBudgetName))
        .request()
        .post(entity(budget, MediaType.APPLICATION_JSON), new GenericType<RestResponse<Budget>>() {});
    verify(budgetService).clone(eq(budgetId), eq(cloneBudgetName), eq(accountId));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testGet() {
    RESOURCES.client()
        .target(format("/budgets/%s/?accountId=%s", budgetId, accountId))
        .request()
        .get(new GenericType<RestResponse<Budget>>() {});
    verify(budgetService).get(eq(budgetId), eq(accountId));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testList() {
    RESOURCES.client()
        .target(format("/budgets?accountId=%s&count=%d&startIndex=%d", accountId, 0, 0))
        .request()
        .get(new GenericType<RestResponse<List<Budget>>>() {});
    verify(budgetService).list(eq(accountId), eq(0), eq(0));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testUpdate() {
    RESOURCES.client()
        .target(format("/budgets/%s/?accountId=%s", budgetId, accountId))
        .request()
        .put(entity(budget, MediaType.APPLICATION_JSON), new GenericType<RestResponse<Budget>>() {});
    verify(budgetService).update(eq(budgetId), isA(Budget.class));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testDelete() {
    RESOURCES.client().target(format("/budgets/%s/?accountId=%s", budgetId, accountId)).request().delete();
    verify(budgetService).delete(eq(budgetId), eq(accountId));
  }
}
