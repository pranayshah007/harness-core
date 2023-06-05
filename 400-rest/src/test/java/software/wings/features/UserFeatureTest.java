/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.features;

import static io.harness.rule.OwnerRule.VIKAS_M;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class UserFeatureTest extends WingsBaseTest {
  @Inject private UsersFeature usersFeature;

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testUserLimitCommunity() {
    int limit = usersFeature.getMaxUsageAllowed("COMMUNITY");
    assertThat(limit).isEqualTo(100);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testUserLimitFree() {
    int limit = usersFeature.getMaxUsageAllowed("FREE");
    assertThat(limit).isEqualTo(100);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testUserLimitPaid() {
    int limit = usersFeature.getMaxUsageAllowed("PAID");
    assertThat(limit).isEqualTo(50000);
  }
}
