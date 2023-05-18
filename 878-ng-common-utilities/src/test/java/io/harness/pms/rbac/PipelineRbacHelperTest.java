/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.rbac;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.exception.AccessDeniedException;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.PrincipalType;
import io.harness.rule.Owner;

import io.fabric8.utils.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineRbacHelperTest extends CategoryTest {
  private static String ACCOUNT_ID = "accountId";
  private static String ORG_ID = "orgId";
  private static String PROJECT_ID = "projectId";

  @Mock EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  @Mock AccessControlClient accessControlClient;

  @InjectMocks PipelineRbacHelper pipelineRbacHelper;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @After
  public void verifyInteractions() {
    verifyNoMoreInteractions(accessControlClient);
    verifyNoMoreInteractions(entityDetailProtoToRestMapper);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testCheckRuntimePermissionsWithEmptyPrincipal() {
    List<EntityDetail> entityDetails = getEntityDetailsWithoutMetadata();
    when(entityDetailProtoToRestMapper.createEntityDetailsDTO(Mockito.anyList())).thenReturn(entityDetails);
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(
                ExecutionMetadata.newBuilder().setPrincipalInfo(ExecutionPrincipalInfo.newBuilder().build()).build())
            .build();
    pipelineRbacHelper.checkRuntimePermissions(ambiance, new HashSet<>());

    verify(entityDetailProtoToRestMapper).createEntityDetailsDTO(Mockito.anyList());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testCheckRuntimePermissions() {
    List<EntityDetail> entityDetails = getEntityDetailsWithoutMetadata();
    when(entityDetailProtoToRestMapper.createEntityDetailsDTO(Mockito.anyList())).thenReturn(entityDetails);
    Ambiance ambiance = Ambiance.newBuilder()
                            .setMetadata(ExecutionMetadata.newBuilder()
                                             .setPrincipalInfo(ExecutionPrincipalInfo.newBuilder()
                                                                   .setPrincipal("princ")
                                                                   .setPrincipalType(PrincipalType.USER)
                                                                   .setShouldValidateRbac(true)
                                                                   .build())
                                             .build())
                            .build();
    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .accessControlList(Collections.singletonList(AccessControlDTO.builder().permitted(true).build()))
            .build();
    when(accessControlClient.checkForAccess(
             Mockito.eq(Principal.of(io.harness.accesscontrol.principals.PrincipalType.USER, "princ")), anyList()))
        .thenReturn(accessCheckResponseDTO);

    pipelineRbacHelper.checkRuntimePermissions(ambiance, new HashSet<>());

    verify(entityDetailProtoToRestMapper).createEntityDetailsDTO(Mockito.anyList());
    verify(accessControlClient)
        .checkForAccess(
            Mockito.eq(Principal.of(io.harness.accesscontrol.principals.PrincipalType.USER, "princ")), anyList());
  }
  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testCheckRuntimePermissionsWithNonPermittedResource() {
    List<EntityDetail> entityDetails = getEntityDetailsWithoutMetadata();
    when(entityDetailProtoToRestMapper.createEntityDetailsDTO(Mockito.anyList())).thenReturn(entityDetails);
    Ambiance ambiance = Ambiance.newBuilder()
                            .setMetadata(ExecutionMetadata.newBuilder()
                                             .setPrincipalInfo(ExecutionPrincipalInfo.newBuilder()
                                                                   .setPrincipal("princ")
                                                                   .setPrincipalType(PrincipalType.USER)
                                                                   .setShouldValidateRbac(true)
                                                                   .build())
                                             .build())
                            .build();
    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .accessControlList(Collections.singletonList(AccessControlDTO.builder()
                                                             .permitted(false)
                                                             .permission("core_connector_access")
                                                             .resourceType("Connectors")
                                                             .build()))
            .build();
    when(accessControlClient.checkForAccess(
             Mockito.eq(Principal.of(io.harness.accesscontrol.principals.PrincipalType.USER, "princ")), anyList()))
        .thenReturn(accessCheckResponseDTO);

    assertThatThrownBy(() -> pipelineRbacHelper.checkRuntimePermissions(ambiance, new HashSet<>()))
        .isInstanceOf(AccessDeniedException.class);

    verify(entityDetailProtoToRestMapper).createEntityDetailsDTO(Mockito.anyList());
    verify(accessControlClient)
        .checkForAccess(
            Mockito.eq(Principal.of(io.harness.accesscontrol.principals.PrincipalType.USER, "princ")), anyList());
  }

  // NOTE: Order matters for this test
  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void throwAccessDeniedErrorWithIdentifier() {
    List<AccessControlDTO> accessControlDTOS = new ArrayList<>();
    accessControlDTOS.add(AccessControlDTO.builder()
                              .permitted(true)
                              .permission("core_connector_access")
                              .resourceType("Connectors")
                              .build());
    accessControlDTOS.add(AccessControlDTO.builder()
                              .permitted(false)
                              .permission("core_connector_access")
                              .resourceType("Connectors")
                              .resourceIdentifier("ri")
                              .build());
    accessControlDTOS.add(AccessControlDTO.builder()
                              .permitted(false)
                              .permission("core_connector_access")
                              .resourceType("Connectors")
                              .resourceIdentifier("ri")
                              .build());

    accessControlDTOS.add(
        AccessControlDTO.builder().permitted(false).permission("core_service_access").resourceType("Service").build());

    assertThatThrownBy(() -> pipelineRbacHelper.throwAccessDeniedError(accessControlDTOS))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> pipelineRbacHelper.throwAccessDeniedError(accessControlDTOS))
        .hasMessage("For Connectors, these permissions are not there: [core_connector_access].\n"
            + "For Connectors with identifier ri, these permissions are not there: [core_connector_access, core_connector_access].\n"
            + "For Service, these permissions are not there: [core_service_access].\n");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void throwAccessDeniedError() {
    List<AccessControlDTO> accessControlDTOS = new ArrayList<>();
    accessControlDTOS.add(AccessControlDTO.builder()
                              .permitted(false)
                              .permission("core_connector_access")
                              .resourceType("Connectors")
                              .build());
    accessControlDTOS.add(AccessControlDTO.builder()
                              .permitted(false)
                              .permission("core_connector_access")
                              .resourceType("Connectors")
                              .build());
    accessControlDTOS.add(AccessControlDTO.builder()
                              .permitted(true)
                              .permission("core_connector_access")
                              .resourceType("Connectors")
                              .build());
    accessControlDTOS.add(
        AccessControlDTO.builder().permitted(false).permission("core_service_access").resourceType("Service").build());

    assertThatThrownBy(() -> pipelineRbacHelper.throwAccessDeniedError(accessControlDTOS))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> pipelineRbacHelper.throwAccessDeniedError(accessControlDTOS))
        .hasMessage(
            "For Connectors, these permissions are not there: [core_connector_access, core_connector_access, core_connector_access].\n"
            + "For Service, these permissions are not there: [core_service_access].\n");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testConvertToPermissionCheckDTO() {
    EntityDetail entityDetailWithMetaData = getEntityDetailWithMetadata();
    EntityDetail entityDetail1WithoutMetaData = getEntityDetailWithoutMetadata();

    PermissionCheckDTO permissionCheckDTO = pipelineRbacHelper.convertToPermissionCheckDTO(entityDetailWithMetaData);

    assertThat(permissionCheckDTO.getPermission()).isEqualTo("core_connector_edit");

    PermissionCheckDTO permissionCheckDTO1 =
        pipelineRbacHelper.convertToPermissionCheckDTO(entityDetail1WithoutMetaData);

    assertThat(permissionCheckDTO1.getPermission()).isEqualTo("core_connector_access");
  }

  private List<EntityDetail> getEntityDetailsWithoutMetadata() {
    EntityDetail entityDetailProjectLevel = EntityDetail.builder()
                                                .entityRef(IdentifierRef.builder()
                                                               .accountIdentifier(ACCOUNT_ID)
                                                               .orgIdentifier(ORG_ID)
                                                               .projectIdentifier(PROJECT_ID)
                                                               .identifier("id1")
                                                               .build())
                                                .type(EntityType.CONNECTORS)
                                                .build();
    EntityDetail entityDetailOrgLevel =
        EntityDetail.builder()
            .entityRef(
                IdentifierRef.builder().accountIdentifier(ACCOUNT_ID).orgIdentifier(ORG_ID).identifier("id2").build())
            .type(EntityType.CONNECTORS)
            .build();
    EntityDetail entityDetailAccountLevel =
        EntityDetail.builder()
            .entityRef(IdentifierRef.builder().accountIdentifier(ACCOUNT_ID).identifier("id3").build())
            .type(EntityType.CONNECTORS)
            .build();

    return Lists.newArrayList(entityDetailAccountLevel, entityDetailOrgLevel, entityDetailProjectLevel);
  }

  private EntityDetail getEntityDetailWithMetadata() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("new", "true");
    EntityDetail entityDetailProjectLevel = EntityDetail.builder()
                                                .entityRef(IdentifierRef.builder()
                                                               .accountIdentifier(ACCOUNT_ID)
                                                               .orgIdentifier(ORG_ID)
                                                               .projectIdentifier(PROJECT_ID)
                                                               .identifier("id1")
                                                               .metadata(metadata)
                                                               .build())
                                                .type(EntityType.CONNECTORS)
                                                .build();
    return entityDetailProjectLevel;
  }

  private EntityDetail getEntityDetailWithoutMetadata() {
    EntityDetail entityDetailProjectLevel = EntityDetail.builder()
                                                .entityRef(IdentifierRef.builder()
                                                               .accountIdentifier(ACCOUNT_ID)
                                                               .orgIdentifier(ORG_ID)
                                                               .projectIdentifier(PROJECT_ID)
                                                               .identifier("id1")
                                                               .build())
                                                .type(EntityType.CONNECTORS)
                                                .build();
    return entityDetailProjectLevel;
  }
}
