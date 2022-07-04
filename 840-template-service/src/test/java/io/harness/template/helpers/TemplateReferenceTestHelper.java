/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.helpers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.pms.contracts.service.EntityReferenceRequest;
import io.harness.pms.contracts.service.EntityReferenceResponse;
import io.harness.pms.contracts.service.EntityReferenceServiceGrpc;
import io.harness.preflight.PreFlightCheckMetadata;
import io.harness.template.TemplateReferenceProtoUtils;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.IdentifierRefProtoUtils;

import io.grpc.stub.StreamObserver;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class TemplateReferenceTestHelper extends EntityReferenceServiceGrpc.EntityReferenceServiceImplBase {
  static final String ACCOUNT_ID = "accountId";
  static final String ORG_ID = "orgId";
  static final String PROJECT_ID = "projectId";

  static final String IDENTIFIER_PROJECT_SCOPE = "projectLevelIdentifier";

  public static final Map<String, String> metadata_StepTemplate =
      new HashMap<>(Collections.singletonMap(PreFlightCheckMetadata.FQN, "spec.connector"));

  public static final Map<String, String> metadata_StageTemplate = new HashMap<>(
      Collections.singletonMap(PreFlightCheckMetadata.FQN, "stage.spec.execution.steps.jira.spec.connector"));

  public static final EntitySetupUsageDTO entitySetupUsage = EntitySetupUsageDTO.builder().build();

  public static final IdentifierRef connectorIdentifierRef_StepTemplate = IdentifierRefHelper.getIdentifierRef(
      IDENTIFIER_PROJECT_SCOPE, ACCOUNT_ID, ORG_ID, PROJECT_ID, metadata_StepTemplate);

  public static final IdentifierRef connectorIdentifierRef_StageTemplate = IdentifierRefHelper.getIdentifierRef(
      IDENTIFIER_PROJECT_SCOPE, ACCOUNT_ID, ORG_ID, PROJECT_ID, metadata_StageTemplate);

  public static final EntityDetailProtoDTO connectorEntityDetailProto_StepTemplate =
      EntityDetailProtoDTO.newBuilder()
          .setType(EntityTypeProtoEnum.CONNECTORS)
          .setIdentifierRef(
              IdentifierRefProtoUtils.createIdentifierRefProtoFromIdentifierRef(connectorIdentifierRef_StepTemplate))
          .build();

  public static final EntityDetailProtoDTO connectorEntityDetailProto_StageTemplate =
      EntityDetailProtoDTO.newBuilder()
          .setType(EntityTypeProtoEnum.CONNECTORS)
          .setIdentifierRef(
              IdentifierRefProtoUtils.createIdentifierRefProtoFromIdentifierRef(connectorIdentifierRef_StageTemplate))
          .build();

  public static IdentifierRef generateIdentifierRef(
      String accountId, String orgId, String project, String identifier, Map<String, String> metadata) {
    return IdentifierRefHelper.getIdentifierRef(identifier, accountId, orgId, project, metadata);
  }

  public static IdentifierRef generateIdentifierRefWithUnknownScope(
      String accountId, String orgId, String project, String identifier, Map<String, String> metadata) {
    return IdentifierRefHelper.createIdentifierRefWithUnknownScope(accountId, orgId, project, identifier, metadata);
  }

  public static EntityDetailProtoDTO generateIdentifierRefEntityDetailProto(String accountId, String orgId,
      String project, String identifier, Map<String, String> metadata, EntityTypeProtoEnum entityTypeProtoEnum) {
    return EntityDetailProtoDTO.newBuilder()
        .setType(entityTypeProtoEnum)
        .setIdentifierRef(IdentifierRefProtoUtils.createIdentifierRefProtoFromIdentifierRef(
            generateIdentifierRef(accountId, orgId, project, identifier, metadata)))
        .build();
  }

  public static EntityDetailProtoDTO generateIdentifierRefWithUnknownScopeEntityDetailProto(String accountId,
      String orgId, String project, String identifier, Map<String, String> metadata,
      EntityTypeProtoEnum entityTypeProtoEnum) {
    return EntityDetailProtoDTO.newBuilder()
        .setType(entityTypeProtoEnum)
        .setIdentifierRef(IdentifierRefProtoUtils.createIdentifierRefProtoFromIdentifierRef(
            generateIdentifierRefWithUnknownScope(accountId, orgId, project, identifier, metadata)))
        .build();
  }

  public static EntityDetailProtoDTO generateTemplateRefEntityDetailProto(String accountId, String orgId,
      String project, String identifier, String versionLabel, Map<String, String> metadata) {
    return EntityDetailProtoDTO.newBuilder()
        .setType(EntityTypeProtoEnum.TEMPLATE)
        .setTemplateRef(TemplateReferenceProtoUtils.createTemplateReferenceProtoFromIdentifierRef(
            generateIdentifierRef(accountId, orgId, project, identifier, null), versionLabel, metadata))
        .build();
  }

  @Override
  public void getReferences(EntityReferenceRequest request, StreamObserver<EntityReferenceResponse> responseObserver) {
    Map<String, String> metadata = new HashMap<>();
    metadata.put(PreFlightCheckMetadata.FQN, "stage.spec.execution.steps.jiraApproval.spec.connectorRef");
    EntityReferenceResponse entityReferenceResponse =
        EntityReferenceResponse.newBuilder()
            .addReferredEntities(generateIdentifierRefEntityDetailProto(
                ACCOUNT_ID, ORG_ID, PROJECT_ID, "jiraConnector", metadata, EntityTypeProtoEnum.CONNECTORS))
            .build();
    responseObserver.onNext(entityReferenceResponse);
    responseObserver.onCompleted();
  }
}
