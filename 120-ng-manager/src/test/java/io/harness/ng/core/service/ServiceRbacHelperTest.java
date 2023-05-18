/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service;

import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_VIEW_PERMISSION;
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
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.resources.ServiceRbacHelper;
import io.harness.pms.rbac.NGResourceType;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ServiceRbacHelperTest extends CategoryTest {
  String ACC_ID = "accId";
  String ORG_ID = "orgId";
  String PRO_ID = "proId";
  @InjectMocks private ServiceRbacHelper serviceRbacHelper;
  @Mock private AccessControlClient accessControlClient;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }
  private List<ServiceEntity> getEntities() {
    List<ServiceEntity> list = new ArrayList<>();
    list.add(ServiceEntity.builder()
                 .accountId(ACC_ID)
                 .orgIdentifier(ORG_ID)
                 .projectIdentifier(PRO_ID)
                 .identifier("newService1")
                 .name("newService1")
                 .createdAt(1L)
                 .lastModifiedAt(2L)
                 .yaml("yaml")
                 .build());
    list.add(ServiceEntity.builder()
                 .accountId(ACC_ID)
                 .orgIdentifier(ORG_ID)
                 .projectIdentifier(PRO_ID)
                 .identifier("newService2")
                 .name("newService2")
                 .createdAt(1L)
                 .lastModifiedAt(2L)
                 .yaml("yaml")
                 .build());
    return list;
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetApi() {
    List<AccessControlDTO> accessControlDTOS = new ArrayList<>();

    AccessControlDTOBuilder accessControlDTOBuilder = AccessControlDTO.builder()
                                                          .resourceType(NGResourceType.SERVICE)
                                                          .permission(SERVICE_VIEW_PERMISSION)
                                                          .resourceScope(ResourceScope.builder()
                                                                             .accountIdentifier(ACC_ID)
                                                                             .orgIdentifier(ORG_ID)
                                                                             .projectIdentifier(PRO_ID)
                                                                             .build());

    accessControlDTOS.add(accessControlDTOBuilder.permitted(true).resourceIdentifier("newService1").build());
    accessControlDTOS.add(accessControlDTOBuilder.permitted(false).resourceIdentifier("newService2").build());

    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .principal(Principal.builder().principalIdentifier("id").principalType(USER).build())
            .accessControlList(accessControlDTOS)
            .build();

    doReturn(accessCheckResponseDTO).when(accessControlClient).checkForAccessOrThrow(anyList());
    List<ServiceEntity> list = serviceRbacHelper.getPermittedServiceList(getEntities());

    Assertions.assertThat(list.size()).isEqualTo(1);
    Assertions.assertThat(list.get(0).getIdentifier()).isEqualTo("newService1");
  }
}
