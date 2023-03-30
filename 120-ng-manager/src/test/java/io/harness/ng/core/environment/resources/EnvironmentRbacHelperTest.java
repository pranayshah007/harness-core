/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.resources;

import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO.AccessControlDTOBuilder;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.pms.rbac.NGResourceType;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class EnvironmentRbacHelperTest extends CategoryTest {
  String ACC_ID = "accId";
  String ORG_ID = "orgId";
  String PRO_ID = "proId";
  @InjectMocks private EnvironmentRbacHelper environmentRbacHelper;
  @Mock private AccessControlClient accessControlClient;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }
  private List<Environment> getEntities() {
    List<io.harness.ng.core.environment.beans.Environment> list = new ArrayList<>();
    list.add(Environment.builder()
                 .accountId(ACC_ID)
                 .type(EnvironmentType.PreProduction)
                 .orgIdentifier(ORG_ID)
                 .projectIdentifier(PRO_ID)
                 .identifier("newEnv1")
                 .name("newEnv1")
                 .color("newCol")
                 .createdAt(1L)
                 .lastModifiedAt(2L)
                 .yaml("yaml")
                 .build());
    list.add(Environment.builder()
                 .accountId(ACC_ID)
                 .type(EnvironmentType.Production)
                 .orgIdentifier(ORG_ID)
                 .projectIdentifier(PRO_ID)
                 .identifier("newEnv2")
                 .name("newEnv2")
                 .color("newCol")
                 .createdAt(1L)
                 .lastModifiedAt(2L)
                 .yaml("yaml")
                 .build());
    list.add(Environment.builder()
                 .type(EnvironmentType.PreProduction)
                 .accountId(ACC_ID)
                 .orgIdentifier(ORG_ID)
                 .projectIdentifier(PRO_ID)
                 .identifier("newEnv3")
                 .name("newEnv3")
                 .color("newCol")
                 .createdAt(1L)
                 .lastModifiedAt(2L)
                 .yaml("yaml")
                 .build());
    return list;
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testEnvironmentsHavingOnlyProdAccess() {
    List<AccessControlDTO> accessControlDTOS = new ArrayList<>();

    AccessControlDTOBuilder accessControlDTOBuilder = AccessControlDTO.builder()
                                                          .resourceType(NGResourceType.ENVIRONMENT)
                                                          .permission(ENVIRONMENT_VIEW_PERMISSION)
                                                          .resourceScope(ResourceScope.builder()
                                                                             .accountIdentifier(ACC_ID)
                                                                             .orgIdentifier(ORG_ID)
                                                                             .projectIdentifier(PRO_ID)
                                                                             .build());
    AccessControlDTOBuilder accessControlDTOBuilderForType = AccessControlDTO.builder()
                                                                 .resourceType(NGResourceType.ENVIRONMENT)
                                                                 .permission(ENVIRONMENT_VIEW_PERMISSION)
                                                                 .resourceScope(ResourceScope.builder()
                                                                                    .accountIdentifier(ACC_ID)
                                                                                    .orgIdentifier(ORG_ID)
                                                                                    .projectIdentifier(PRO_ID)
                                                                                    .build());

    Map<String, String> preProdAttributes = new HashMap<>();
    preProdAttributes.put("type", "PreProduction");
    Map<String, String> prodAttributes = new HashMap<>();
    prodAttributes.put("type", "Production");

    accessControlDTOS.add(accessControlDTOBuilderForType.permitted(true).resourceAttributes(prodAttributes).build());
    accessControlDTOS.add(
        accessControlDTOBuilderForType.permitted(false).resourceAttributes(preProdAttributes).build());
    accessControlDTOS.add(accessControlDTOBuilder.permitted(false).resourceIdentifier("newEnv1").build());
    accessControlDTOS.add(accessControlDTOBuilder.permitted(false).resourceIdentifier("newEnv2").build());

    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .principal(Principal.builder().principalIdentifier("id").principalType(USER).build())
            .accessControlList(accessControlDTOS)
            .build();

    doReturn(accessCheckResponseDTO).when(accessControlClient).checkForAccessOrThrow(anyList());
    List<Environment> list = environmentRbacHelper.getPermittedEnvironmentsList(getEntities());

    Assertions.assertThat(list.size()).isEqualTo(1);
    Assertions.assertThat(list.get(0).getIdentifier()).isEqualTo("newEnv2");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testEnvironmentsHavingOnlyPreProdAccess() {
    List<AccessControlDTO> accessControlDTOS = new ArrayList<>();

    AccessControlDTOBuilder accessControlDTOBuilder = AccessControlDTO.builder()
                                                          .resourceType(NGResourceType.ENVIRONMENT)
                                                          .permission(ENVIRONMENT_VIEW_PERMISSION)
                                                          .resourceScope(ResourceScope.builder()
                                                                             .accountIdentifier(ACC_ID)
                                                                             .orgIdentifier(ORG_ID)
                                                                             .projectIdentifier(PRO_ID)
                                                                             .build());
    AccessControlDTOBuilder accessControlDTOBuilderForType = AccessControlDTO.builder()
                                                                 .resourceType(NGResourceType.ENVIRONMENT)
                                                                 .permission(ENVIRONMENT_VIEW_PERMISSION)
                                                                 .resourceScope(ResourceScope.builder()
                                                                                    .accountIdentifier(ACC_ID)
                                                                                    .orgIdentifier(ORG_ID)
                                                                                    .projectIdentifier(PRO_ID)
                                                                                    .build());
    Map<String, String> preProdAttributes = new HashMap<>();
    preProdAttributes.put("type", "PreProduction");
    Map<String, String> prodAttributes = new HashMap<>();
    prodAttributes.put("type", "Production");

    accessControlDTOS.add(accessControlDTOBuilderForType.permitted(false).resourceAttributes(prodAttributes).build());
    accessControlDTOS.add(accessControlDTOBuilderForType.permitted(true).resourceAttributes(preProdAttributes).build());
    accessControlDTOS.add(accessControlDTOBuilder.permitted(false).resourceIdentifier("newEnv1").build());
    accessControlDTOS.add(accessControlDTOBuilder.permitted(false).resourceIdentifier("newEnv3").build());
    accessControlDTOS.add(accessControlDTOBuilder.permitted(false).resourceIdentifier("newEnv2").build());
    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .principal(Principal.builder().principalIdentifier("id").principalType(USER).build())
            .accessControlList(accessControlDTOS)
            .build();

    doReturn(accessCheckResponseDTO).when(accessControlClient).checkForAccessOrThrow(anyList());
    List<Environment> list = environmentRbacHelper.getPermittedEnvironmentsList(getEntities());

    Assertions.assertThat(list.size()).isEqualTo(2);
  }
}
