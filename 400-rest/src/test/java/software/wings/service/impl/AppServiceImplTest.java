/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SortOrder;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.service.intfc.ResourceLookupService;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AppServiceImplTest {
  @InjectMocks private AppServiceImpl service;
  @Mock private ResourceLookupService resourceLookupService;

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldListApplicationSortingByName() {
    PageRequest<Application> req = new PageRequest<>();

    when(resourceLookupService.listWithTagFilters(req, "tagFilter", EntityType.APPLICATION, false))
        .thenReturn(new PageResponse<>());

    service.list(req, false, false, "tagFilter");

    assertThat(req.getOrders()).hasSize(1);
    assertThat(req.getOrders().get(0).getFieldName()).isEqualTo("name");
    assertThat(req.getOrders().get(0).getOrderType()).isEqualTo(SortOrder.OrderType.ASC);
  }
}
