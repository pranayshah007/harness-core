/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGEntitiesTestBase;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.setupusage.SetupUsageHelper;
import io.harness.ng.core.template.TemplateReferenceRequestDTO;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.walktree.visitor.SimpleVisitorFactory;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jooq.tools.reflect.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

public class ServiceEntitySetupUsageHelperTest extends CDNGEntitiesTestBase {
  @Inject private SimpleVisitorFactory visitorFactory;
  @Inject private IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper;
  @Inject private SetupUsageHelper setupUsageHelper;
  @Inject private TemplateResourceClient templateResourceClient;
  @Mock private Producer producer;
  @InjectMocks private ServiceEntitySetupUsageHelper entitySetupUsageHelper;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    Reflect.on(entitySetupUsageHelper).set("simpleVisitorFactory", visitorFactory);
    Reflect.on(entitySetupUsageHelper).set("setupUsageHelper", setupUsageHelper);
    Reflect.on(entitySetupUsageHelper).set("templateResourceClient", templateResourceClient);
    Reflect.on(setupUsageHelper).set("producer", producer);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testUpdateSetupUsages_0() throws InvalidProtocolBufferException {
    ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);

    ServiceEntity entity = ServiceEntity.builder()
                               .identifier("id")
                               .name("my-service")
                               .accountId("accountId")
                               .orgIdentifier("orgId")
                               .projectIdentifier("projId")
                               .build();
    entitySetupUsageHelper.updateSetupUsages(entity);

    verify(producer, times(1)).send(msgCaptor.capture());

    Message value = msgCaptor.getValue();
    EntitySetupUsageCreateV2DTO createV2DTO = EntitySetupUsageCreateV2DTO.parseFrom(value.getData());
    Map<String, String> metadataMap = value.getMetadataMap();

    assertThat(createV2DTO.getReferredEntitiesList()).isEmpty();
    assertThat(createV2DTO.getReferredByEntity())
        .isEqualTo(EntityDetailProtoDTO.newBuilder()
                       .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                             .setIdentifier(StringValue.of(entity.getIdentifier()))
                                             .setAccountIdentifier(StringValue.of(entity.getAccountId()))
                                             .setOrgIdentifier(StringValue.of(entity.getOrgIdentifier()))
                                             .setProjectIdentifier(StringValue.of(entity.getProjectIdentifier()))
                                             .setScope(ScopeProtoEnum.PROJECT)
                                             .build())
                       .setType(EntityTypeProtoEnum.SERVICE)
                       .build());

    assertThat(metadataMap.get("accountId")).isEqualTo("accountId");
    assertThat(metadataMap.get("referredEntityType")).isNull();
    assertThat(metadataMap.get("action")).isEqualTo("flushCreate");
  }

  @Test
  @Owner(developers = {OwnerRule.YOGESH, OwnerRule.MLUKIC})
  @Category(UnitTests.class)
  public void testUpdateSetupUsages_1() throws InvalidProtocolBufferException {
    ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);

    String serviceYaml = readFile("service/serviceWith3ConnectorReferences.yaml");
    ServiceEntity entity = ServiceEntity.builder()
                               .identifier("newservice")
                               .name("newservice")
                               .accountId("accountId")
                               .orgIdentifier("orgId")
                               .projectIdentifier("projId")
                               .yaml(serviceYaml)
                               .build();
    entitySetupUsageHelper.updateSetupUsages(entity);

    verify(producer, times(4)).send(msgCaptor.capture());

    List<Message> messages = msgCaptor.getAllValues();

    Message value = messages.get(0);
    EntitySetupUsageCreateV2DTO createV2DTO = EntitySetupUsageCreateV2DTO.parseFrom(value.getData());
    Map<String, String> metadataMap = value.getMetadataMap();

    assertThat(createV2DTO.getReferredEntitiesList()).hasSize(3);
    assertThat(createV2DTO.getReferredEntitiesList()
                   .stream()
                   .map(EntityDetailProtoDTO::getIdentifierRef)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder(
            IdentifierRefProtoDTO.newBuilder()
                .setAccountIdentifier(StringValue.of(entity.getAccountId()))
                .setIdentifier(StringValue.of("harnessImagePrimary"))
                .putAllMetadata(Map.of("fqn", "service.serviceDefinition.spec.artifacts.primary.spec.connectorRef"))
                .build(),
            IdentifierRefProtoDTO.newBuilder()
                .setAccountIdentifier(StringValue.of(entity.getAccountId()))
                .setOrgIdentifier(StringValue.of(entity.getOrgIdentifier()))
                .setProjectIdentifier(StringValue.of(entity.getProjectIdentifier()))
                .setScope(ScopeProtoEnum.PROJECT)
                .setIdentifier(StringValue.of("testconnector"))
                .putAllMetadata(
                    Map.of("fqn", "service.serviceDefinition.spec.manifests.abc.spec.store.spec.connectorRef"))
                .build(),
            IdentifierRefProtoDTO.newBuilder()
                .setAccountIdentifier(StringValue.of(entity.getAccountId()))
                .setOrgIdentifier(StringValue.of(entity.getOrgIdentifier()))
                .setScope(ScopeProtoEnum.ORG)
                .setIdentifier(StringValue.of("harnessImageSidecar"))
                .putAllMetadata(
                    Map.of("fqn", "service.serviceDefinition.spec.artifacts.sidecars.sidecar1.spec.connectorRef"))
                .build());

    assertThat(createV2DTO.getReferredByEntity())
        .isEqualTo(EntityDetailProtoDTO.newBuilder()
                       .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                             .setIdentifier(StringValue.of(entity.getIdentifier()))
                                             .setAccountIdentifier(StringValue.of(entity.getAccountId()))
                                             .setOrgIdentifier(StringValue.of(entity.getOrgIdentifier()))
                                             .setProjectIdentifier(StringValue.of(entity.getProjectIdentifier()))
                                             .setScope(ScopeProtoEnum.PROJECT)
                                             .build())
                       .setType(EntityTypeProtoEnum.SERVICE)
                       .setName(entity.getName())
                       .build());

    assertThat(createV2DTO.getReferredByEntity())
        .isEqualTo(EntityDetailProtoDTO.newBuilder()
                       .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                             .setIdentifier(StringValue.of(entity.getIdentifier()))
                                             .setAccountIdentifier(StringValue.of(entity.getAccountId()))
                                             .setOrgIdentifier(StringValue.of(entity.getOrgIdentifier()))
                                             .setProjectIdentifier(StringValue.of(entity.getProjectIdentifier()))
                                             .setScope(ScopeProtoEnum.PROJECT)
                                             .build())
                       .setType(EntityTypeProtoEnum.SERVICE)
                       .setName(entity.getName())
                       .build());

    assertThat(metadataMap.get("accountId")).isEqualTo("accountId");
    assertThat(metadataMap.get("referredEntityType")).isNotNull();
    assertThat(metadataMap.get("action")).isEqualTo("flushCreate");

    assertThat(
        messages.subList(1, messages.size())
            .stream()
            .map(msg -> String.valueOf(msg.getMetadataMap().get(EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE)))
            .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("TEMPLATE", "FILES", "SECRETS");
  }

  @Test
  @Owner(developers = {OwnerRule.HINGER, OwnerRule.MLUKIC})
  @Category(UnitTests.class)
  public void testUpdateSetupUsages_2() throws InvalidProtocolBufferException, IOException {
    // test template references
    ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);

    String serviceYaml = readFile("service/serviceWith3ConnectorAndTemplateReferences.yaml");
    ServiceEntity entity = ServiceEntity.builder()
                               .identifier("v2serviceTEMP")
                               .name("v2serviceTEMP")
                               .accountId("accountId")
                               .orgIdentifier("orgId")
                               .projectIdentifier("projId")
                               .yaml(serviceYaml)
                               .build();

    // template client call
    Call call = Mockito.mock(Call.class);
    TemplateReferenceRequestDTO templateReferenceRequestDTO =
        TemplateReferenceRequestDTO.builder().yaml(serviceYaml).build();
    IdentifierRefProtoDTO identifierRefProtoDTO =
        identifierRefProtoDTOHelper.createIdentifierRefProtoDTO("accountId", "orgId", "projId", "ast1");

    List<EntityDetailProtoDTO> expected = Collections.singletonList(EntityDetailProtoDTO.newBuilder()
                                                                        .setType(EntityTypeProtoEnum.TEMPLATE)
                                                                        .setIdentifierRef(identifierRefProtoDTO)
                                                                        .build());
    when(templateResourceClient.getTemplateReferenceForGivenYaml(
             "accountId", "orgId", "projId", null, null, null, templateReferenceRequestDTO))
        .thenReturn(call);

    when(call.execute()).thenReturn(Response.success(ResponseDTO.newResponse(expected)));

    entitySetupUsageHelper.updateSetupUsages(entity);

    verify(producer, times(4)).send(msgCaptor.capture());

    List<Message> messages = msgCaptor.getAllValues();

    Message value = messages.get(0);
    EntitySetupUsageCreateV2DTO createV2DTO = EntitySetupUsageCreateV2DTO.parseFrom(value.getData());
    Map<String, String> metadataMap = value.getMetadataMap();

    assertThat(createV2DTO.getReferredEntitiesList()).hasSize(1);
    assertThat(createV2DTO.getReferredEntitiesList()
                   .stream()
                   .map(EntityDetailProtoDTO::getIdentifierRef)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder(IdentifierRefProtoDTO.newBuilder()
                                       .setIdentifier(StringValue.of("ast1"))
                                       .setAccountIdentifier(StringValue.of(entity.getAccountId()))
                                       .setOrgIdentifier(StringValue.of(entity.getOrgIdentifier()))
                                       .setProjectIdentifier(StringValue.of(entity.getProjectIdentifier()))
                                       .setScope(ScopeProtoEnum.PROJECT)
                                       .build());

    assertThat(createV2DTO.getReferredByEntity())
        .isEqualTo(EntityDetailProtoDTO.newBuilder()
                       .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                             .setIdentifier(StringValue.of(entity.getIdentifier()))
                                             .setAccountIdentifier(StringValue.of(entity.getAccountId()))
                                             .setOrgIdentifier(StringValue.of(entity.getOrgIdentifier()))
                                             .setProjectIdentifier(StringValue.of(entity.getProjectIdentifier()))
                                             .setScope(ScopeProtoEnum.PROJECT)
                                             .build())
                       .setType(EntityTypeProtoEnum.SERVICE)
                       .setName(entity.getName())
                       .build());

    assertThat(metadataMap.get("accountId")).isEqualTo("accountId");
    assertThat(metadataMap.get("referredEntityType")).isEqualTo("TEMPLATE");
    assertThat(metadataMap.get("action")).isEqualTo("flushCreate");

    assertThat(
        messages.subList(1, messages.size())
            .stream()
            .map(msg -> String.valueOf(msg.getMetadataMap().get(EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE)))
            .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("CONNECTORS", "FILES", "SECRETS");
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testUpdateSetupUsages_3() throws InvalidProtocolBufferException {
    ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);

    // org level service
    ServiceEntity entity = ServiceEntity.builder()
                               .identifier("id")
                               .name("my-service")
                               .accountId("accountId")
                               .orgIdentifier("orgId")
                               .build();
    entitySetupUsageHelper.updateSetupUsages(entity);

    verify(producer, times(1)).send(msgCaptor.capture());

    Message value = msgCaptor.getValue();
    EntitySetupUsageCreateV2DTO createV2DTO = EntitySetupUsageCreateV2DTO.parseFrom(value.getData());
    Map<String, String> metadataMap = value.getMetadataMap();

    assertThat(createV2DTO.getReferredEntitiesList()).isEmpty();
    assertThat(createV2DTO.getReferredByEntity())
        .isEqualTo(EntityDetailProtoDTO.newBuilder()
                       .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                             .setIdentifier(StringValue.of(entity.getIdentifier()))
                                             .setAccountIdentifier(StringValue.of(entity.getAccountId()))
                                             .setOrgIdentifier(StringValue.of(entity.getOrgIdentifier()))
                                             .setScope(ScopeProtoEnum.ORG)
                                             .build())
                       .setType(EntityTypeProtoEnum.SERVICE)
                       .build());

    assertThat(metadataMap.get("accountId")).isEqualTo("accountId");
    assertThat(metadataMap.get("referredEntityType")).isNull();
    assertThat(metadataMap.get("action")).isEqualTo("flushCreate");
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testUpdateSetupUsages_4() throws InvalidProtocolBufferException {
    ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);

    // org level service
    ServiceEntity entity = ServiceEntity.builder().identifier("id").name("my-service").accountId("accountId").build();
    entitySetupUsageHelper.updateSetupUsages(entity);

    verify(producer, times(1)).send(msgCaptor.capture());

    Message value = msgCaptor.getValue();
    EntitySetupUsageCreateV2DTO createV2DTO = EntitySetupUsageCreateV2DTO.parseFrom(value.getData());
    Map<String, String> metadataMap = value.getMetadataMap();

    assertThat(createV2DTO.getReferredEntitiesList()).isEmpty();
    assertThat(createV2DTO.getReferredByEntity())
        .isEqualTo(EntityDetailProtoDTO.newBuilder()
                       .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                             .setIdentifier(StringValue.of(entity.getIdentifier()))
                                             .setAccountIdentifier(StringValue.of(entity.getAccountId()))
                                             .setScope(ScopeProtoEnum.ACCOUNT)
                                             .build())
                       .setType(EntityTypeProtoEnum.SERVICE)
                       .build());

    assertThat(metadataMap.get("accountId")).isEqualTo("accountId");
    assertThat(metadataMap.get("referredEntityType")).isNull();
    assertThat(metadataMap.get("action")).isEqualTo("flushCreate");
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testUpdateSetupUsages_WithInvalidReferredEntity() {
    String serviceYaml = readFile("service/serviceWithInvalidConnectorReference.yaml");
    // account level service
    ServiceEntity entity = ServiceEntity.builder()
                               .identifier("newservice")
                               .name("newservice")
                               .accountId("accountId")
                               .yaml(serviceYaml)
                               .build();
    assertThatThrownBy(() -> entitySetupUsageHelper.getAllReferredEntities(entity))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("The org level connectors cannot be used at account level. Ref: [org.dp1]");
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }
}
