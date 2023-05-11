/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roles;

import static io.harness.accesscontrol.roles.api.RoleDTO.ScopeLevel.ACCOUNT;
import static io.harness.accesscontrol.roles.api.RoleDTO.ScopeLevel.ORGANIZATION;
import static io.harness.accesscontrol.roles.api.RoleDTO.ScopeLevel.PROJECT;
import static io.harness.rule.OwnerRule.MANKRIT;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;

import io.harness.accesscontrol.roles.api.RoleDTO;
import io.harness.accesscontrol.roles.api.RoleResponseDTO;
import io.harness.accesscontrol.roles.api.RolesApiUtils;
import io.harness.beans.SortOrder;
import io.harness.category.element.UnitTests;
import io.harness.ng.beans.PageRequest;
import io.harness.rule.Owner;
import io.harness.spec.server.accesscontrol.v1.model.CreateRoleRequest;
import io.harness.spec.server.accesscontrol.v1.model.RolesResponse;
import io.harness.spec.server.accesscontrol.v1.model.RolesResponse.AllowedScopeLevelsEnum;
import io.harness.utils.ApiUtils;

import java.util.Collections;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class RolesApiUtilsTest {
  private RolesApiUtils rolesApiUtils;
  private Validator validator;
  String identifier = randomAlphabetic(10);
  String name = randomAlphabetic(10);

  @Before
  public void setup() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
    rolesApiUtils = new RolesApiUtils(validator);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetRoleAccDTO() {
    CreateRoleRequest request = new CreateRoleRequest();
    request.setIdentifier(identifier);
    request.setName(name);
    request.setPermissions(Collections.singletonList("core_role_view"));

    RoleDTO roleDTO = rolesApiUtils.getRoleAccDTO(request);
    assertEquals(identifier, roleDTO.getIdentifier());
    assertEquals(name, roleDTO.getName());
    assertTrue(roleDTO.getPermissions().contains("core_role_view"));
    assertTrue(roleDTO.getAllowedScopeLevels().contains(ACCOUNT));
    assertFalse(roleDTO.getAllowedScopeLevels().contains(ORGANIZATION));
    assertFalse(roleDTO.getAllowedScopeLevels().contains(PROJECT));
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetRoleOrgDTO() {
    CreateRoleRequest request = new CreateRoleRequest();
    request.setIdentifier(identifier);
    request.setName(name);
    request.setPermissions(Collections.singletonList("core_role_view"));

    RoleDTO roleDTO = rolesApiUtils.getRoleOrgDTO(request);
    assertEquals(identifier, roleDTO.getIdentifier());
    assertEquals(name, roleDTO.getName());
    assertTrue(roleDTO.getPermissions().contains("core_role_view"));
    assertFalse(roleDTO.getAllowedScopeLevels().contains(ACCOUNT));
    assertTrue(roleDTO.getAllowedScopeLevels().contains(ORGANIZATION));
    assertFalse(roleDTO.getAllowedScopeLevels().contains(PROJECT));
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetRoleProjectDTO() {
    CreateRoleRequest request = new CreateRoleRequest();
    request.setIdentifier(identifier);
    request.setName(name);
    request.setPermissions(Collections.singletonList("core_role_view"));

    RoleDTO roleDTO = rolesApiUtils.getRoleProjectDTO(request);
    assertEquals(identifier, roleDTO.getIdentifier());
    assertEquals(name, roleDTO.getName());
    assertTrue(roleDTO.getPermissions().contains("core_role_view"));
    assertFalse(roleDTO.getAllowedScopeLevels().contains(ACCOUNT));
    assertFalse(roleDTO.getAllowedScopeLevels().contains(ORGANIZATION));
    assertTrue(roleDTO.getAllowedScopeLevels().contains(PROJECT));
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetRolesResponse() {
    RoleResponseDTO responseDTO = RoleResponseDTO.builder()
                                      .role(RoleDTO.builder()
                                                .identifier(identifier)
                                                .name(name)
                                                .permissions(Collections.singleton("core_role_view"))
                                                .allowedScopeLevels(Collections.singleton(ACCOUNT))
                                                .build())
                                      .createdAt(123L)
                                      .build();

    RolesResponse rolesResponse = RolesApiUtils.getRolesResponse(responseDTO);
    assertEquals(identifier, rolesResponse.getIdentifier());
    assertEquals(name, rolesResponse.getName());
    assertTrue(rolesResponse.getPermissions().get(0).equals("core_role_view"));
    assertEquals(AllowedScopeLevelsEnum.ACCOUNT, rolesResponse.getAllowedScopeLevels().get(0));
    assertEquals(123L, (long) rolesResponse.getCreated());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetPageRequest() {
    int page = 0;
    int limit = 1;
    String sort = "name";
    String order = "desc";

    PageRequest pageRequest = ApiUtils.getPageRequest(page, limit, sort, order);
    assertEquals(pageRequest.getPageIndex(), page);
    assertEquals(pageRequest.getPageSize(), limit);
    assertEquals(pageRequest.getSortOrders().get(0).getFieldName(), sort);
    assertEquals(pageRequest.getSortOrders().get(0).getOrderType(), SortOrder.OrderType.DESC);
  }
}
