/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorsTestBase;
import io.harness.connector.expression.HostFilterFunctor;
import io.harness.delegate.beans.connector.pdcconnector.HostDTO;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.common.collect.Maps;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
@Slf4j
public class HostFilterFunctorTest extends ConnectorsTestBase {
  private HostFilterFunctor hostFilterFunctor;

  @Before
  public void setup() {
    hostFilterFunctor = new HostFilterFunctor();
  }

  @Test
  @Owner(developers = OwnerRule.BOJAN)
  @Category({UnitTests.class})
  public void testExpressionForHostAttributesCommaSeparatedFilterTrue() {
    HostDTO host = createHostDto("host1");
    String expression = "region:west,test:test\ntest1:test1";
    boolean filter = hostFilterFunctor.filterByHostAttributes(expression, host);
    assertThat(filter).isEqualTo(true);
  }

  @Test
  @Owner(developers = OwnerRule.BOJAN)
  @Category({UnitTests.class})
  public void testExpressionForSingleAttributeFilterTrue() {
    HostDTO host = createHostDto("host1");
    String expression = "region:west";
    boolean filter = hostFilterFunctor.filterByHostAttributes(expression, host);
    assertThat(filter).isEqualTo(true);
  }

  @Test
  @Owner(developers = OwnerRule.BOJAN)
  @Category({UnitTests.class})
  public void testExpressionForHostNamesCommaSeparatedFilterTrue() {
    HostDTO host = createHostDto("host1");
    String expression = "host1,host2\nhost3";
    boolean filter = hostFilterFunctor.filterByHostName(expression, host);
    assertThat(filter).isEqualTo(true);
  }

  @Test
  @Owner(developers = OwnerRule.BOJAN)
  @Category({UnitTests.class})
  public void testExpressionForHostNamesCommaSeparatedFilterFalse() {
    HostDTO host = createHostDto("host4");
    String expression = "host1,host2\nhost3";
    boolean filter = hostFilterFunctor.filterByHostName(expression, host);
    assertThat(filter).isEqualTo(false);
  }

  @NotNull
  private HostDTO createHostDto(String name) {
    Map<String, String> attr1 = Maps.newHashMap();
    attr1.put("region", "west");
    attr1.put("hostType", "DB");
    return new HostDTO(name, attr1);
  }
}
