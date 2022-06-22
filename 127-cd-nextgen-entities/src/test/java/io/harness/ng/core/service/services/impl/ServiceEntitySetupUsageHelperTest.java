package io.harness.ng.core.service.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGEntitiesTestBase;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.setupusage.SetupUsageHelper;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.walktree.visitor.SimpleVisitorFactory;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import org.mockito.MockitoAnnotations;

public class ServiceEntitySetupUsageHelperTest extends CDNGEntitiesTestBase {
  @Inject private SimpleVisitorFactory visitorFactory;
  @Inject private IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper;
  @Inject private SetupUsageHelper setupUsageHelper;
  @Mock private Producer producer;
  @InjectMocks private ServiceEntitySetupUsageHelper entitySetupUsageHelper;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    Reflect.on(entitySetupUsageHelper).set("simpleVisitorFactory", visitorFactory);
    Reflect.on(entitySetupUsageHelper).set("setupUsageHelper", setupUsageHelper);
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

    verify(producer, times(EntityTypeProtoEnum.values().length)).send(msgCaptor.capture());

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
                       .setName(entity.getName())
                       .build());

    assertThat(metadataMap.get("accountId")).isEqualTo("accountId");
    assertThat(metadataMap.get("referredEntityType")).isNotNull();
    assertThat(metadataMap.get("action")).isEqualTo("flushCreate");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testUpdateSetupUsages_1() throws InvalidProtocolBufferException {
    ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);

    String serviceYaml = readFile("service/serviceWith3ConnectorReferences.yaml");
    ServiceEntity entity = ServiceEntity.builder()
                               .identifier("id")
                               .name("my-service")
                               .accountId("accountId")
                               .orgIdentifier("orgId")
                               .projectIdentifier("projId")
                               .yaml(serviceYaml)
                               .build();
    entitySetupUsageHelper.updateSetupUsages(entity);

    verify(producer, times(1)).send(msgCaptor.capture());

    Message value = msgCaptor.getValue();
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