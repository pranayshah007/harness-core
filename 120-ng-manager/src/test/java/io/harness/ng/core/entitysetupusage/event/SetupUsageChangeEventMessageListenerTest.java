/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entitysetupusage.event;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE;
import static io.harness.rule.OwnerRule.ABHINAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.DeleteSetupUsageDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.ng.core.entitysetupusage.helper.SetupUsageGitInfoPopulator;
import io.harness.ng.core.entitysetupusage.mapper.EntitySetupUsageEventDTOMapper;
import io.harness.ng.core.entitysetupusage.mapper.SetupUsageDetailProtoToRestMapper;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(DX)
public class SetupUsageChangeEventMessageListenerTest extends CategoryTest {
  private final String accountId = "accountId";
  private final String orgId = "orgId";
  private final String projectId = "projectId";
  private EntitySetupUsageService entitySetupUsageService;
  private EntitySetupUsageEventDTOMapper entitySetupUsageEventDTOMapper;
  private SetupUsageChangeEventMessageListener setupUsageChangeEventMessageListener;
  private IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper;
  private SetupUsageGitInfoPopulator setupUsageGitInfoPopulator;

  @Before
  public void setUp() {
    entitySetupUsageService = mock(EntitySetupUsageService.class);
    setupUsageGitInfoPopulator = mock(SetupUsageGitInfoPopulator.class);
    entitySetupUsageEventDTOMapper = new EntitySetupUsageEventDTOMapper(new EntityDetailProtoToRestMapper(),
        new SetupUsageDetailProtoToRestMapper(), new SetupUsageGitInfoPopulator(null, null));
    setupUsageChangeEventMessageListener =
        new SetupUsageChangeEventMessageListener(entitySetupUsageService, entitySetupUsageEventDTOMapper);
    identifierRefProtoDTOHelper = new IdentifierRefProtoDTOHelper();
    when(entitySetupUsageService.delete(any(), any(), any(), any(), any())).thenReturn(Boolean.TRUE);
    when(entitySetupUsageService.flushSave(any(), any(), anyBoolean(), any())).thenReturn(true);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testCreateEventSingleSave() {
    final String secretName = "secretName";
    final String secretManagerName = "secretManagerName";
    final String secretId = "secretId";
    final String secretManagerId = "secretManagerId";

    EntityDetailProtoDTO secretDetails =
        getEntityDetailProto(secretName, secretId, projectId, orgId, accountId, EntityTypeProtoEnum.SECRETS);
    EntityDetailProtoDTO secretManagerDetails = getEntityDetailProto(
        secretManagerName, secretManagerId, projectId, orgId, accountId, EntityTypeProtoEnum.CONNECTORS);

    EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                         .setAccountIdentifier(accountId)
                                                         .setReferredByEntity(secretDetails)
                                                         .addReferredEntities(secretManagerDetails)
                                                         .build();

    Message message = getCreateMessage(entityReferenceDTO, EntityTypeProtoEnum.CONNECTORS);
    final boolean b = setupUsageChangeEventMessageListener.handleMessage(message);
    assertThat(b).isTrue();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testCreateEventMultipleSave() {
    final String secretName = "secretName";
    final String secretManagerName = "secretManagerName";
    final String secretManagerName1 = "secretManagerName1";
    final String secretId = "secretId";
    final String secretManagerId = "secretManagerId";
    final String secretManagerId1 = "secretManagerId1";
    // Hypothetical situation
    EntityDetailProtoDTO secretDetails =
        getEntityDetailProto(secretName, secretId, projectId, orgId, accountId, EntityTypeProtoEnum.SECRETS);

    EntityDetailProtoDTO secretManagerDetails = getEntityDetailProto(
        secretManagerName, secretManagerId, projectId, orgId, accountId, EntityTypeProtoEnum.CONNECTORS);
    EntityDetailProtoDTO secretManagerDetails1 = getEntityDetailProto(
        secretManagerName1, secretManagerId1, projectId, orgId, accountId, EntityTypeProtoEnum.CONNECTORS);
    EntitySetupUsageCreateV2DTO entityReferenceDTO =
        EntitySetupUsageCreateV2DTO.newBuilder()
            .setAccountIdentifier(accountId)
            .setReferredByEntity(secretDetails)
            .addAllReferredEntities(Arrays.asList(secretManagerDetails, secretManagerDetails1))
            .build();

    Message message = getCreateMessage(entityReferenceDTO, EntityTypeProtoEnum.CONNECTORS);
    final boolean b = setupUsageChangeEventMessageListener.handleMessage(message);
    assertThat(b).isTrue();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testCreateEventWithWrongMetadata() {
    final String secretName = "secretName";
    final String secretManagerName = "secretManagerName";
    final String secretManagerName1 = "secretManagerName1";
    final String secretId = "secretId";
    final String secretManagerId = "secretManagerId";
    final String secretManagerId1 = "secretManagerId1";
    // Hypothetical situation
    EntityDetailProtoDTO secretDetails =
        getEntityDetailProto(secretName, secretId, projectId, orgId, accountId, EntityTypeProtoEnum.SECRETS);

    EntityDetailProtoDTO secretManagerDetails = getEntityDetailProto(
        secretManagerName, secretManagerId, projectId, orgId, accountId, EntityTypeProtoEnum.CONNECTORS);
    EntityDetailProtoDTO secretManagerDetails1 = getEntityDetailProto(
        secretManagerName1, secretManagerId1, projectId, orgId, accountId, EntityTypeProtoEnum.CONNECTORS);
    EntitySetupUsageCreateV2DTO entityReferenceDTO =
        EntitySetupUsageCreateV2DTO.newBuilder()
            .setAccountIdentifier(accountId)
            .setReferredByEntity(secretDetails)
            .addAllReferredEntities(Arrays.asList(secretManagerDetails, secretManagerDetails1))
            .build();

    Message message = getCreateMessage(entityReferenceDTO, EntityTypeProtoEnum.PIPELINES);
    final boolean b = setupUsageChangeEventMessageListener.handleMessage(message);
    // The event should be skipped and marked as successfully processed
    assertThat(b).isTrue();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testDeleteEvent() {
    final String secretId = "secretId";
    final String secretManagerId = "secretManagerId";
    testCreateEventSingleSave();
    DeleteSetupUsageDTO entityReferenceDTO =
        DeleteSetupUsageDTO.newBuilder()
            .setAccountIdentifier(accountId)
            .setReferredByEntityFQN(accountId + "/" + orgId + "/" + projectId + "/" + secretId)
            .setReferredByEntityType(EntityTypeProtoEnum.SECRETS)
            .setReferredEntityFQN(accountId + "/" + orgId + "/" + projectId + "/" + secretManagerId)
            .setReferredEntityType(EntityTypeProtoEnum.CONNECTORS)
            .build();

    Message message = getDeleteMessage(entityReferenceDTO, EntityTypeProtoEnum.SECRETS);
    final boolean b = setupUsageChangeEventMessageListener.handleMessage(message);
    assertThat(b).isTrue();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testDeleteEventWithWrongMetadata() {
    final String secretId = "secretId";
    final String secretManagerId = "secretManagerId";
    testCreateEventSingleSave();
    DeleteSetupUsageDTO entityReferenceDTO =
        DeleteSetupUsageDTO.newBuilder()
            .setAccountIdentifier(accountId)
            .setReferredByEntityFQN(accountId + "/" + orgId + "/" + projectId + "/" + secretId)
            .setReferredByEntityType(EntityTypeProtoEnum.SECRETS)
            .setReferredEntityFQN(accountId + "/" + orgId + "/" + projectId + "/" + secretManagerId)
            .setReferredEntityType(EntityTypeProtoEnum.CONNECTORS)
            .build();

    Message message = getDeleteMessage(entityReferenceDTO, EntityTypeProtoEnum.CONNECTORS);
    assertThatThrownBy(() -> setupUsageChangeEventMessageListener.handleMessage(message));
  }

  private EntityDetailProtoDTO getEntityDetailProto(String name, String id, String projectId, String orgId,
      String accountId, EntityTypeProtoEnum entityTypeProtoEnum) {
    IdentifierRefProtoDTO ref =
        identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(accountId, orgId, projectId, id);

    return EntityDetailProtoDTO.newBuilder().setIdentifierRef(ref).setType(entityTypeProtoEnum).setName(name).build();
  }

  private Message getCreateMessage(
      EntitySetupUsageCreateV2DTO entityReferenceDTO, EntityTypeProtoEnum referredEntityType) {
    Map<String, String> metadata = new HashMap<>();
    metadata.put(REFERRED_ENTITY_TYPE, referredEntityType.name());
    metadata.put(EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION);
    return Message.newBuilder()
        .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                        .putAllMetadata(metadata)
                        .setData(entityReferenceDTO.toByteString())
                        .build())
        .setId("testId")
        .build();
  }

  private Message getDeleteMessage(DeleteSetupUsageDTO deleteSetupUsageDTO, EntityTypeProtoEnum referredEntityType) {
    Map<String, String> metadata = new HashMap<>();
    metadata.put(REFERRED_ENTITY_TYPE, referredEntityType.name());
    metadata.put(EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.DELETE_ACTION);
    return Message.newBuilder()
        .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                        .putAllMetadata(metadata)
                        .setData(deleteSetupUsageDTO.toByteString())
                        .build())
        .setId("testId")
        .build();
  }
}
