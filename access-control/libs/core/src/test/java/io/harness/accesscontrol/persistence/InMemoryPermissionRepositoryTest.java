/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.persistence;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.permissions.persistence.PermissionDBO;
import io.harness.accesscontrol.permissions.persistence.repositories.InMemoryPermissionRepository;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(PL)
public class InMemoryPermissionRepositoryTest {
  public static final String CORE_USERGROUP_MANAGE_PERMISSION = "core_usergroup_manage";
  public static final String CORE_RESOURCEGROUP_MANAGE_PERMISSION = "core_resourcegroup_manage";
  public static final String USERGROUP = "USERGROUP";
  public static final String RESOURCEGROUP = "RESOURCEGROUP";
  private MongoTemplate mongoTemplate;
  private InMemoryPermissionRepository inMemoryPermissionRepository;

  @Before
  public void setup() {
    mongoTemplate = mock(MongoTemplate.class);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testPermissionToResourceTypeMapping() {
    when(mongoTemplate.findAll(any()))
        .thenReturn(List.of(PermissionDBO.builder().identifier(CORE_USERGROUP_MANAGE_PERMISSION).build(),
            PermissionDBO.builder().identifier(CORE_RESOURCEGROUP_MANAGE_PERMISSION).build()));
    inMemoryPermissionRepository = new InMemoryPermissionRepository(mongoTemplate);

    assertThat(inMemoryPermissionRepository.getResourceTypeBy(CORE_USERGROUP_MANAGE_PERMISSION))
        .isEqualToIgnoringCase(USERGROUP);
    assertThat(inMemoryPermissionRepository.getResourceTypeBy(CORE_RESOURCEGROUP_MANAGE_PERMISSION))
        .isEqualToIgnoringCase(RESOURCEGROUP);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testEmptyPermissionToResourceTypeMapping() {
    inMemoryPermissionRepository = new InMemoryPermissionRepository(mongoTemplate);

    assertThat(inMemoryPermissionRepository.getResourceTypeBy(CORE_USERGROUP_MANAGE_PERMISSION)).isNull();
    assertThat(inMemoryPermissionRepository.getResourceTypeBy(CORE_RESOURCEGROUP_MANAGE_PERMISSION)).isNull();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testMultipleInvocationShouldMakeOnlyOneDBCallIfRefreshIsFalse() {
    when(mongoTemplate.findAll(any()))
        .thenReturn(List.of(PermissionDBO.builder().identifier(CORE_USERGROUP_MANAGE_PERMISSION).build(),
            PermissionDBO.builder().identifier(CORE_RESOURCEGROUP_MANAGE_PERMISSION).build()));
    inMemoryPermissionRepository = new InMemoryPermissionRepository(mongoTemplate);

    assertThat(inMemoryPermissionRepository.getResourceTypeBy(CORE_USERGROUP_MANAGE_PERMISSION))
        .isEqualToIgnoringCase(USERGROUP);
    assertThat(inMemoryPermissionRepository.getResourceTypeBy(CORE_RESOURCEGROUP_MANAGE_PERMISSION))
        .isEqualToIgnoringCase(RESOURCEGROUP);

    verify(mongoTemplate, times(1)).findAll(any());
  }
}
