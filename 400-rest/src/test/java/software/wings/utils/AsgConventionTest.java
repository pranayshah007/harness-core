/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import static io.harness.rule.OwnerRule.ADWAIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import software.wings.utils.nonkryos.AsgConvention;

public class AsgConventionTest extends CategoryTest {
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetRevisionFromTag() {
    int revision = AsgConvention.getRevisionFromTag("aaa__123");
    assertThat(revision).isEqualTo(123);

    revision = AsgConvention.getRevisionFromTag("aaa__bbb__ccc__123");
    assertThat(revision).isEqualTo(123);

    revision = AsgConvention.getRevisionFromTag("aaabbbccc");
    assertThat(revision).isEqualTo(0);

    revision = AsgConvention.getRevisionFromTag("aaa__bbb__ccc");
    assertThat(revision).isEqualTo(0);

    revision = AsgConvention.getRevisionFromTag(null);
    assertThat(revision).isEqualTo(0);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetAsgNamePrefix() {
    String asgNamePrefix = AsgConvention.getAsgNamePrefix("appName", "serviceName", "envName");
    assertThat(asgNamePrefix).isEqualTo("appName__serviceName__envName");

    asgNamePrefix = AsgConvention.getAsgNamePrefix("app&Name", "service+Name", "env*Name");
    assertThat(asgNamePrefix).isEqualTo("app__Name__service__Name__env__Name");

    asgNamePrefix = AsgConvention.getAsgNamePrefix("app/Name", "service.Name", "env'Name");
    assertThat(asgNamePrefix).isEqualTo("app__Name__service__Name__env__Name");

    asgNamePrefix = AsgConvention.getAsgNamePrefix("app$Name", "service Name", "env\"Name");
    assertThat(asgNamePrefix).isEqualTo("app__Name__service__Name__env__Name");

    asgNamePrefix = AsgConvention.getAsgNamePrefix("app$Name", "service|Name", "env\\Name");
    assertThat(asgNamePrefix).isEqualTo("app__Name__service__Name__env__Name");

    asgNamePrefix = AsgConvention.getAsgNamePrefix("appName", null, null);
    assertThat(asgNamePrefix).isEqualTo("appName__null__null");
  }
}
