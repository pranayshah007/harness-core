/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.helpers;

import static io.harness.rule.OwnerRule.INDER;
import static io.harness.template.helpers.TemplateReferenceTestHelper.ACCOUNT_ID;
import static io.harness.template.helpers.TemplateReferenceTestHelper.ORG_ID;
import static io.harness.template.helpers.TemplateReferenceTestHelper.PROJECT_ID;
import static io.harness.template.helpers.TemplateReferenceTestHelper.generateIdentifierRefWithUnknownScope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.EntityType;
import io.harness.TemplateServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.preflight.PreFlightCheckMetadata;
import io.harness.rule.Owner;
import io.harness.template.TemplateReferenceProtoUtils;
import io.harness.template.entity.TemplateEntity;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
public class TemplateSetupUsageHelperTest extends TemplateServiceTestBase {
  @Mock private EntitySetupUsageClient entitySetupUsageClient;
  @Mock private Producer eventProducer;
  @InjectMocks private TemplateSetupUsageHelper templateSetupUsageHelper;

  private static final TemplateEntity templateEntity = TemplateEntity.builder()
                                                           .accountId("accountId")
                                                           .projectIdentifier("projectId")
                                                           .orgIdentifier("orgId")
                                                           .identifier("templateIdentifier")
                                                           .versionLabel("version1")
                                                           .templateScope(Scope.PROJECT)
                                                           .name("template")
                                                           .build();

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetReferencesOfTemplate() throws IOException {
    Call<ResponseDTO<List<EntitySetupUsageDTO>>> entityUsageCall = mock(Call.class);
    when(entitySetupUsageClient.listAllReferredUsages(eq(0), eq(100), anyString(), anyString(), eq(null), eq(null)))
        .thenReturn(entityUsageCall);
    when(entityUsageCall.clone()).thenReturn(entityUsageCall);
    Map<String, String> metadataMap = new HashMap<>();
    metadataMap.put(PreFlightCheckMetadata.FQN, "templateInputs.spec.connectorRef");
    when(entityUsageCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            Collections.singletonList(EntitySetupUsageDTO.builder()
                                          .referredEntity(EntityDetail.builder()
                                                              .entityRef(generateIdentifierRefWithUnknownScope(
                                                                  ACCOUNT_ID, ORG_ID, PROJECT_ID, "", metadataMap))
                                                              .type(EntityType.CONNECTORS)
                                                              .build())
                                          .build()))));

    List<EntitySetupUsageDTO> templateReferences = templateSetupUsageHelper.getReferencesOfTemplate(
        "accountId", "orgId", "projectId", "templateIdentifier", "version1");
    assertThat(templateReferences).isNotEmpty().hasSize(1);
    verify(entitySetupUsageClient)
        .listAllReferredUsages(
            0, 100, "accountId", "accountId/orgId/projectId/templateIdentifier/version1/", null, null);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testPublishSetupUsageEvent_EmptyReferredEntities() throws IOException {
    templateSetupUsageHelper.publishSetupUsageEvent(templateEntity, new ArrayList<>(), new HashMap<>());
    assertDeleteExistingSetupUsagesIsCalled();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testPublishSetupUsageEvent_NonEmptyReferredEntities() throws IOException {
    List<EntityDetailProtoDTO> referredEntities = new ArrayList<>();
    EntityDetailProtoDTO secretManagerDetails =
        EntityDetailProtoDTO.newBuilder()
            .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                  .putMetadata(PreFlightCheckMetadata.FQN, "pipeline.variables.var1")
                                  .build())
            .setType(EntityTypeProtoEnum.SECRETS)
            .build();
    EntityDetailProtoDTO connectorManagerDetails =
        EntityDetailProtoDTO.newBuilder()
            .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                  .putMetadata(PreFlightCheckMetadata.FQN, "pipelines.stages.s1")
                                  .build())
            .setType(EntityTypeProtoEnum.CONNECTORS)
            .build();
    referredEntities.add(secretManagerDetails);
    referredEntities.add(connectorManagerDetails);

    EntityDetailProtoDTO templateDetails =
        EntityDetailProtoDTO.newBuilder()
            .setTemplateRef(TemplateReferenceProtoUtils.createTemplateReferenceProto(templateEntity.getAccountId(),
                templateEntity.getOrgIdentifier(), templateEntity.getProjectIdentifier(),
                templateEntity.getIdentifier(), templateEntity.getTemplateScope(), templateEntity.getVersionLabel(),
                null))
            .setType(EntityTypeProtoEnum.TEMPLATE)
            .setName(templateEntity.getName())
            .build();
    EntitySetupUsageCreateV2DTO secretEntityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                               .setAccountIdentifier(templateEntity.getAccountId())
                                                               .setReferredByEntity(templateDetails)
                                                               .addReferredEntities(secretManagerDetails)
                                                               .setDeleteOldReferredByRecords(true)
                                                               .build();
    EntitySetupUsageCreateV2DTO connectorEntityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                                  .setAccountIdentifier(templateEntity.getAccountId())
                                                                  .setReferredByEntity(templateDetails)
                                                                  .addReferredEntities(connectorManagerDetails)
                                                                  .setDeleteOldReferredByRecords(true)
                                                                  .build();

    templateSetupUsageHelper.publishSetupUsageEvent(templateEntity, referredEntities, new HashMap<>());

    verify(eventProducer)
        .send(Message.newBuilder()
                  .putAllMetadata(ImmutableMap.of("accountId", templateEntity.getAccountId(),
                      EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, EntityTypeProtoEnum.SECRETS.name(),
                      EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
                  .setData(secretEntityReferenceDTO.toByteString())
                  .build());
    verify(eventProducer)
        .send(Message.newBuilder()
                  .putAllMetadata(ImmutableMap.of("accountId", templateEntity.getAccountId(),
                      EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, EntityTypeProtoEnum.CONNECTORS.name(),
                      EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
                  .setData(connectorEntityReferenceDTO.toByteString())
                  .build());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testDeleteExistingSetupUsages() throws IOException {
    templateSetupUsageHelper.deleteExistingSetupUsages(templateEntity);
    assertDeleteExistingSetupUsagesIsCalled();
  }

  private void assertDeleteExistingSetupUsagesIsCalled() {
    String accountId = templateEntity.getAccountId();
    EntityDetailProtoDTO templateDetails =
        EntityDetailProtoDTO.newBuilder()
            .setTemplateRef(
                TemplateReferenceProtoUtils.createTemplateReferenceProto(accountId, templateEntity.getOrgIdentifier(),
                    templateEntity.getProjectIdentifier(), templateEntity.getIdentifier(),
                    templateEntity.getTemplateScope(), templateEntity.getVersionLabel(), null))
            .setType(EntityTypeProtoEnum.TEMPLATE)
            .build();
    EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                         .setAccountIdentifier(accountId)
                                                         .setReferredByEntity(templateDetails)
                                                         .setDeleteOldReferredByRecords(true)
                                                         .build();

    verify(eventProducer)
        .send(Message.newBuilder()
                  .putAllMetadata(ImmutableMap.of("accountId", accountId, EventsFrameworkMetadataConstants.ACTION,
                      EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
                  .setData(entityReferenceDTO.toByteString())
                  .build());
  }
}
