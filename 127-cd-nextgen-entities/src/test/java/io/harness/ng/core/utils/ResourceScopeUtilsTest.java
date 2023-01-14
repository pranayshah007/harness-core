/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.utils;

import static io.harness.rule.OwnerRule.HINGER;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.OrgScope;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.ResourceScope;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ResourceScopeUtilsTest {
  String ACCOUNT_ID = "accountId";
  String ORG_ID = "orgId";
  String PROJECT_ID = "projectId";
  String IDENTIFIER = "id";

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testProjectScope() {
    ServiceEntity scopeAwareEntity = ServiceEntity.builder()
                                         .accountId(ACCOUNT_ID)
                                         .orgIdentifier(ORG_ID)
                                         .projectIdentifier(PROJECT_ID)
                                         .identifier(IDENTIFIER)
                                         .build();

    ResourceScope scope = ResourceScopeUtils.getEntityScope(scopeAwareEntity);
    assertThat(scope).isInstanceOf(ProjectScope.class);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testOrgScope() {
    ServiceEntity scopeAwareEntity =
        ServiceEntity.builder().accountId(ACCOUNT_ID).orgIdentifier(ORG_ID).identifier(IDENTIFIER).build();

    ResourceScope scope = ResourceScopeUtils.getEntityScope(scopeAwareEntity);
    assertThat(scope).isInstanceOf(OrgScope.class);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testAccountScope() {
    ServiceEntity scopeAwareEntity = ServiceEntity.builder().accountId(ACCOUNT_ID).identifier(IDENTIFIER).build();

    ResourceScope scope = ResourceScopeUtils.getEntityScope(scopeAwareEntity);
    assertThat(scope).isInstanceOf(AccountScope.class);
  }
}
