/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.settings.mappers;

import static io.harness.rule.OwnerRule.SARTHAK_KASAT;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.settings.beans.entity.BackstagePermissionsEntity;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.BackstagePermissions;

import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class BackstagePermissionsMapperTest extends CategoryTest {
  static final String TEST_PERMISSIONS_ID = "backstagePermissionsId";
  static final String TEST_ACCOUNT_IDENTIFIER = "accountId";

  static final String TEST_USERGROUP = "IDP-ADMIN";
  static final List<String> TEST_PERMISSIONS =
      List.of("user_read", "user_update", "user_delete", "owner_read", "owner_update", "owner_delete", "all_create");
  AutoCloseable openMocks;
  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testToDTO() {
    BackstagePermissionsEntity backstagePermissionsEntity = BackstagePermissionsEntity.builder()
                                                                .id(TEST_PERMISSIONS_ID)
                                                                .userGroup(TEST_USERGROUP)
                                                                .permissions(TEST_PERMISSIONS)
                                                                .createdAt(System.currentTimeMillis())
                                                                .lastModifiedAt(System.currentTimeMillis())
                                                                .build();
    BackstagePermissions backstagePermissions = BackstagePermissionsMapper.toDTO(backstagePermissionsEntity);
    assertEquals(backstagePermissionsEntity.getId(), backstagePermissions.getIdentifer());
    assertEquals(backstagePermissionsEntity.getUserGroup(), backstagePermissions.getUserGroup());
    assertEquals(backstagePermissionsEntity.getPermissions(), backstagePermissions.getPermissions());
    assertEquals(backstagePermissionsEntity.getCreatedAt(), backstagePermissions.getCreated());
    assertEquals(backstagePermissionsEntity.getLastModifiedAt(), backstagePermissions.getUpdated());
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testFromDTO() {
    BackstagePermissions backstagePermissions = new BackstagePermissions();
    backstagePermissions.setIdentifer(TEST_PERMISSIONS_ID);
    backstagePermissions.setUserGroup(TEST_USERGROUP);
    backstagePermissions.setPermissions(TEST_PERMISSIONS);
    backstagePermissions.created(System.currentTimeMillis());
    backstagePermissions.updated(System.currentTimeMillis());
    BackstagePermissionsEntity backstagePermissionsEntity =
        BackstagePermissionsMapper.fromDTO(backstagePermissions, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(backstagePermissions.getIdentifer(), backstagePermissionsEntity.getId());
    assertEquals(backstagePermissions.getPermissions(), backstagePermissionsEntity.getPermissions());
    assertEquals(backstagePermissions.getUserGroup(), backstagePermissionsEntity.getUserGroup());
    assertEquals(backstagePermissions.getCreated(), backstagePermissionsEntity.getCreatedAt());
    assertEquals(backstagePermissions.getUpdated(), backstagePermissionsEntity.getLastModifiedAt());
  }
  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
