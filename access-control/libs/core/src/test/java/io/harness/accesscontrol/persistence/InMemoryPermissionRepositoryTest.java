/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.persistence;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;

import static java.util.List.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.permissions.persistence.PermissionDBO;
import io.harness.accesscontrol.permissions.persistence.repositories.InMemoryPermissionRepository;
import io.harness.accesscontrol.resources.resourcetypes.persistence.ResourceTypeDBO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(PL)
public class InMemoryPermissionRepositoryTest {
  public static final String CORE_USERGROUP_MANAGE_PERMISSION = "core_usergroup_manage";
  public static final String USERGROUP_RESOURCE_NAME = "usergroup";
  public static final String USERGROUP_RESOURCE_IDENTIFIER = "USERGROUP";

  public static final String CORE_RESOURCEGROUP_MANAGE_PERMISSION = "core_resourcegroup_manage";
  public static final String RESOURCEGROUP_RESOURCE_NAME = "resourcegroup";
  public static final String RESOURCEGROUP_RESOURCE_IDENTIFIER = "RESOURCEGROUP";

  public static final String CORE_ENVIRONMENT_GROUP_VIEW_PERMISSION = "core_environmentgroup_view";
  public static final String ENVIRONMENT_GROUP_RESOURCE_NAME = "environmentgroup";
  public static final String ENVIRONMENT_GROUP_RESOURCE_IDENTIFIER = "ENVIRONMENT_GROUP";

  public static final String CORE_GOVERNANCEPOLICYSETS_VIEW_PERMISSION = "core_governancePolicySets_view";
  public static final String GOVERNANCEPOLICYSETS_RESOURCE_NAME = "governancePolicySets";
  public static final String GOVERNANCEPOLICYSETS_RESOURCE_IDENTIFIER = "GOVERNANCEPOLICYSETS";

  private MongoTemplate mongoTemplate;
  private InMemoryPermissionRepository inMemoryPermissionRepository;

  @Before
  public void setup() {
    mongoTemplate = mock(MongoTemplate.class);

    when(mongoTemplate.findAll(PermissionDBO.class))
        .thenReturn(of(PermissionDBO.builder().identifier(CORE_USERGROUP_MANAGE_PERMISSION).build(),
            PermissionDBO.builder().identifier(CORE_RESOURCEGROUP_MANAGE_PERMISSION).build(),
            PermissionDBO.builder().identifier(CORE_ENVIRONMENT_GROUP_VIEW_PERMISSION).build(),
            PermissionDBO.builder().identifier(CORE_GOVERNANCEPOLICYSETS_VIEW_PERMISSION).build()));
    when(mongoTemplate.findAll(ResourceTypeDBO.class))
        .thenReturn(of(ResourceTypeDBO.builder()
                           .identifier(USERGROUP_RESOURCE_IDENTIFIER)
                           .permissionKey(USERGROUP_RESOURCE_NAME)
                           .build(),
            ResourceTypeDBO.builder()
                .identifier(RESOURCEGROUP_RESOURCE_IDENTIFIER)
                .permissionKey(RESOURCEGROUP_RESOURCE_NAME)
                .build(),
            ResourceTypeDBO.builder()
                .identifier(ENVIRONMENT_GROUP_RESOURCE_IDENTIFIER)
                .permissionKey(ENVIRONMENT_GROUP_RESOURCE_NAME)
                .build(),
            ResourceTypeDBO.builder()
                .identifier(GOVERNANCEPOLICYSETS_RESOURCE_IDENTIFIER)
                .permissionKey(GOVERNANCEPOLICYSETS_RESOURCE_NAME)
                .build()));
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testPermissionToResourceTypeMapping() {
    inMemoryPermissionRepository = new InMemoryPermissionRepository(mongoTemplate);

    assertThat(inMemoryPermissionRepository.getResourceTypeBy(CORE_USERGROUP_MANAGE_PERMISSION))
        .isEqualToIgnoringCase(USERGROUP_RESOURCE_IDENTIFIER);
    assertThat(inMemoryPermissionRepository.getResourceTypeBy(CORE_RESOURCEGROUP_MANAGE_PERMISSION))
        .isEqualToIgnoringCase(RESOURCEGROUP_RESOURCE_IDENTIFIER);
    assertThat(inMemoryPermissionRepository.getResourceTypeBy(CORE_ENVIRONMENT_GROUP_VIEW_PERMISSION))
        .isEqualToIgnoringCase(ENVIRONMENT_GROUP_RESOURCE_IDENTIFIER);
    assertThat(inMemoryPermissionRepository.getResourceTypeBy(CORE_GOVERNANCEPOLICYSETS_VIEW_PERMISSION))
        .isEqualToIgnoringCase(GOVERNANCEPOLICYSETS_RESOURCE_IDENTIFIER);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testEmptyPermissionToResourceTypeMapping() {
    when(mongoTemplate.findAll(PermissionDBO.class)).thenReturn(of());

    inMemoryPermissionRepository = new InMemoryPermissionRepository(mongoTemplate);

    assertThat(inMemoryPermissionRepository.getResourceTypeBy(CORE_USERGROUP_MANAGE_PERMISSION)).isNull();
    assertThat(inMemoryPermissionRepository.getResourceTypeBy(CORE_RESOURCEGROUP_MANAGE_PERMISSION)).isNull();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testMultipleInvocationShouldMakeOnlyOneDBCall() {
    inMemoryPermissionRepository = new InMemoryPermissionRepository(mongoTemplate);

    assertThat(inMemoryPermissionRepository.getResourceTypeBy(CORE_USERGROUP_MANAGE_PERMISSION))
        .isEqualToIgnoringCase(USERGROUP_RESOURCE_IDENTIFIER);
    assertThat(inMemoryPermissionRepository.getResourceTypeBy(CORE_RESOURCEGROUP_MANAGE_PERMISSION))
        .isEqualToIgnoringCase(RESOURCEGROUP_RESOURCE_IDENTIFIER);
    assertThat(inMemoryPermissionRepository.getResourceTypeBy(CORE_ENVIRONMENT_GROUP_VIEW_PERMISSION))
        .isEqualToIgnoringCase(ENVIRONMENT_GROUP_RESOURCE_IDENTIFIER);
    assertThat(inMemoryPermissionRepository.getResourceTypeBy(CORE_GOVERNANCEPOLICYSETS_VIEW_PERMISSION))
        .isEqualToIgnoringCase(GOVERNANCEPOLICYSETS_RESOURCE_IDENTIFIER);

    verify(mongoTemplate, times(1)).findAll(PermissionDBO.class);
    verify(mongoTemplate, times(1)).findAll(ResourceTypeDBO.class);
  }
}
