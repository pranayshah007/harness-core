/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.connector.entities.Connector.ConnectorKeys;
import static io.harness.rule.OwnerRule.BHAVYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.agent.sdk.HarnessAlwaysRun;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;

public class ConnectorCustomRepositoryImplTest extends CategoryTest {
  @InjectMocks ConnectorCustomRepositoryImpl connectorCustomRepository;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  @HarnessAlwaysRun
  public void testGetPageableWithSortFieldAsTimeWhenConnectorIsLastUpdated() {
    int page = 0;
    int pageSize = 10;
    List<Order> orders = new ArrayList<>();
    orders.add(new Order(Direction.DESC, ConnectorKeys.lastModifiedAt));
    orders.add(new Order(Direction.DESC, ConnectorKeys.createdAt));
    Pageable pageable = connectorCustomRepository.getPageableWithSortFieldAsTimeWhenConnectorIsLastUpdated(
        PageRequest.of(page, pageSize, Sort.by(orders)));
    assertThat(pageable.getPageNumber()).isEqualTo(page);
    assertThat(pageable.getPageSize()).isEqualTo(pageSize);
    assertThat(pageable.getSort().getOrderFor(ConnectorKeys.lastModifiedAt)).isNull();
    assertThat(pageable.getSort().getOrderFor(ConnectorKeys.timeWhenConnectorIsLastUpdated)).isNotNull();
    assertThat(pageable.getSort().getOrderFor(ConnectorKeys.createdAt)).isNotNull();
  }
}
