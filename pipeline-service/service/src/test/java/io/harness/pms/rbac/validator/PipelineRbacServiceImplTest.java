/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.rbac.validator;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.exception.AccessDeniedException;
import io.harness.ng.core.EntityDetail;
import io.harness.pms.pipeline.PipelineSetupUsageHelper;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.preflight.PreFlightCheckMetadata;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class PipelineRbacServiceImplTest extends CategoryTest {
  @Mock private PipelineSetupUsageHelper pipelineSetupUsageHelper;
  @Mock private AccessControlClient accessControlClient;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @InjectMocks private PipelineRbacServiceImpl pipelineRbacService;

  @Before
  public void init() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateStaticallyReferredEntitiesInYaml() {
    String accountIdentifier = "account";
    String orgIdentifier = "default";
    String projectIdentifier = "test";
    String pipelineIdentifier = "pipelineId";
    String pipelineYaml = "pipelineYaml";

    List<EntityDetail> entityDetails = new ArrayList<>();
    entityDetails.add(EntityDetail.builder()
                          .type(EntityType.CONNECTORS)
                          .entityRef(IdentifierRef.builder()
                                         .accountIdentifier(accountIdentifier)
                                         .orgIdentifier(orgIdentifier)
                                         .projectIdentifier(projectIdentifier)
                                         .identifier("DOCKER_NEW_TEST")
                                         .scope(Scope.PROJECT)
                                         .metadata(Collections.singletonMap(PreFlightCheckMetadata.FQN,
                                             "pipeline.stages.deploy.serviceConfig.artifacts.primary.connectorRef"))
                                         .build())
                          .build());
    entityDetails.add(EntityDetail.builder()
                          .type(EntityType.CONNECTORS)
                          .entityRef(IdentifierRef.builder()
                                         .accountIdentifier(accountIdentifier)
                                         .orgIdentifier(orgIdentifier)
                                         .projectIdentifier(projectIdentifier)
                                         .identifier("conn")
                                         .scope(Scope.UNKNOWN)
                                         .metadata(Collections.singletonMap(PreFlightCheckMetadata.FQN,
                                             "pipeline.stages.deploy.infrastructure.connectorRef"))
                                         .build())
                          .build());
    when(pipelineSetupUsageHelper.getReferencesOfPipeline(
             anyString(), anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(entityDetails);

    when(pipelineRbacHelper.convertToPermissionCheckDTO(any(EntityDetail.class))).thenCallRealMethod();

    pipelineRbacService.extractAndValidateStaticallyReferredEntities(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, pipelineYaml);

    List<AccessControlDTO> accessControlList = new ArrayList<>();
    accessControlList.add(AccessControlDTO.builder()
                              .permission("core_connector_access")
                              .resourceScope(ResourceScope.builder()
                                                 .accountIdentifier(accountIdentifier)
                                                 .orgIdentifier(orgIdentifier)
                                                 .projectIdentifier(projectIdentifier)
                                                 .build())
                              .resourceIdentifier("DOCKER_NEW_TEST")
                              .resourceType("CONNECTOR")
                              .permitted(true)
                              .build());
    accessControlList.add(AccessControlDTO.builder()
                              .permission("core_connector_access")
                              .resourceScope(ResourceScope.builder().accountIdentifier(accountIdentifier).build())
                              .resourceIdentifier("conn")
                              .resourceType("CONNECTOR")
                              .permitted(false)
                              .build());

    when(accessControlClient.checkForAccess(anyList()))
        .thenReturn(AccessCheckResponseDTO.builder().accessControlList(accessControlList).build());
    assertThatThrownBy(()
                           -> pipelineRbacService.extractAndValidateStaticallyReferredEntities(
                               accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, pipelineYaml))
        .isInstanceOf(AccessDeniedException.class);

    accessControlList.remove(1);
    pipelineRbacService.extractAndValidateStaticallyReferredEntities(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, pipelineYaml);
  }
}
