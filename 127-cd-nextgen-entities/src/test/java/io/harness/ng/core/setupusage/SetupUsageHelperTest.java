/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.setupusage;

import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGEntitiesTestBase;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.InfraDefinitionReferenceProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntityDetailWithSetupUsageDetailProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.StringValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class SetupUsageHelperTest extends CDNGEntitiesTestBase {
  @Mock private Producer producer;
  @Mock private IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper;
  @InjectMocks private SetupUsageHelper setupUsageHelper;

  @Inject private KryoSerializer kryoSerializer;

  @Before
  public void setUp() {
    initMocks(this);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testDeleteSetupUsages() {
    final EntityDetailProtoDTO entityDetail =
        EntityDetailProtoDTO.newBuilder()
            .setInfraDefRef(
                InfraDefinitionReferenceProtoDTO.newBuilder().setIdentifier(StringValue.of("infraId")).build())
            .setType(EntityTypeProtoEnum.INFRASTRUCTURE)
            .setName("infraName")
            .build();
    setupUsageHelper.deleteInfraSetupUsages(entityDetail, "accountId");
    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(producer, times(1)).send(captor.capture());
    assertThat(captor.getValue()).isNotNull();
    final Message message = captor.getValue();
    assertThat(message.getMetadataMap().values())
        .containsExactlyInAnyOrder("accountId", EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testPublishEntitySetupUsage() throws InvalidProtocolBufferException {
    final EntityDetailProtoDTO referredEntityDetail =
        EntityDetailProtoDTO.newBuilder()
            .setInfraDefRef(
                InfraDefinitionReferenceProtoDTO.newBuilder().setIdentifier(StringValue.of("connectorId")).build())
            .setType(EntityTypeProtoEnum.CONNECTORS)
            .setName("connectorName")
            .build();

    final EntityDetailProtoDTO referredByEntityDetail =
        EntityDetailProtoDTO.newBuilder()
            .setInfraDefRef(InfraDefinitionReferenceProtoDTO.newBuilder()
                                .setIdentifier(StringValue.of("infraId"))
                                .setEnvIdentifier(StringValue.of("envId"))
                                .setEnvName(StringValue.of("envName"))
                                .build())
            .setType(EntityTypeProtoEnum.INFRASTRUCTURE)
            .setName("infraName")
            .build();

    setupUsageHelper.publishInfraEntitySetupUsage(
        referredByEntityDetail, Collections.singleton(referredEntityDetail), "accountId");
    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(producer, times(1)).send(captor.capture());
    assertThat(captor.getValue()).isNotNull();
    final Message message = captor.getValue();
    EntitySetupUsageCreateV2DTO entitySetupUsageCreateV2DTO = EntitySetupUsageCreateV2DTO.parseFrom(message.getData());
    assertThat(entitySetupUsageCreateV2DTO.getAccountIdentifier()).isEqualTo("accountId");
    assertThat(entitySetupUsageCreateV2DTO.getReferredByEntity().getInfraDefRef()).isNotNull();

    assertThat(entitySetupUsageCreateV2DTO.getReferredByEntity().getInfraDefRef()).isNotNull();
    assertThat(entitySetupUsageCreateV2DTO.getReferredByEntity().getInfraDefRef().getIdentifier())
        .isEqualTo(StringValue.of("infraId"));
    Set<Descriptors.FieldDescriptor> fieldDescriptorsSet = entitySetupUsageCreateV2DTO.getAllFields().keySet();
    assertThat(fieldDescriptorsSet).isNotEmpty();
    ArrayList<Descriptors.FieldDescriptor> fieldDescriptorsList = new ArrayList<>(fieldDescriptorsSet);
    Set<String> fieldDescriptorsFullName =
        fieldDescriptorsList.stream().map(Descriptors.FieldDescriptor::getFullName).collect(Collectors.toSet());
    assertThat(fieldDescriptorsFullName)
        .containsExactlyInAnyOrder(
            "io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO.accountIdentifier",
            "io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO.referredByEntity",
            "io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO.deleteOldReferredByRecords",
            "io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO.referredEntityWithSetupUsageDetail");

    Collection<Object> entitySetupUsageValueFields = entitySetupUsageCreateV2DTO.getAllFields().values();

    int typesOfFields = 0;
    for (Object value : entitySetupUsageValueFields) {
      if (value instanceof String) {
        typesOfFields++;
        assertThat((String) value).isEqualTo("accountId");
      } else if (value instanceof EntityDetailProtoDTO) {
        typesOfFields++;
        EntityDetailProtoDTO referredByEntity = (EntityDetailProtoDTO) value;
        assertThat(referredByEntity.getName()).isEqualTo("infraName");
        assertThat(referredByEntity.getInfraDefRef().getIdentifier()).isEqualTo(StringValue.of("infraId"));
      } else if (value instanceof Boolean) {
        typesOfFields++;
        assertThat((Boolean) value).isTrue();
      } else if (value instanceof List) {
        typesOfFields++;
        List<EntityDetailWithSetupUsageDetailProtoDTO> entityDetailWithSetupUsageList =
            (List<EntityDetailWithSetupUsageDetailProtoDTO>) value;
        assertThat(entityDetailWithSetupUsageList).hasSize(1);
        EntityDetailWithSetupUsageDetailProtoDTO entityDetailWithSetupUsageDetailProtoDTO =
            entityDetailWithSetupUsageList.get(0);
        assertThat(entityDetailWithSetupUsageDetailProtoDTO.getType()).isEqualTo("ENTITY_REFERRED_BY_INFRA");
        assertThat(entityDetailWithSetupUsageDetailProtoDTO.getEntityInInfraDetail()).isNotNull();
        assertThat(entityDetailWithSetupUsageDetailProtoDTO.getEntityInInfraDetail().getEnvironmentIdentifier())
            .isEqualTo("envId");
        assertThat(entityDetailWithSetupUsageDetailProtoDTO.getEntityInInfraDetail().getEnvironmentName())
            .isEqualTo("envName");
      }
    }
    assertThat(typesOfFields).isEqualTo(4);

    assertThat(message.getMetadataMap().keySet())
        .containsExactlyInAnyOrder("accountId", EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE,
            EventsFrameworkMetadataConstants.ACTION);
    assertThat(message.getMetadataMap().values())
        .containsExactlyInAnyOrder("accountId", "CONNECTORS", EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testPublishServiceEntitySetupUsage() throws InvalidProtocolBufferException {
    final SetupUsageOwnerEntity setupUsageOwnerEntity = SetupUsageOwnerEntity.builder()
                                                            .name("someService")
                                                            .accountId("accountId")
                                                            .identifier("svcId")
                                                            .orgIdentifier("orgId")
                                                            .projectIdentifier("projId")
                                                            .type(EntityTypeProtoEnum.SERVICE)
                                                            .build();

    EntityDetailProtoDTO entityDetailProtoDTO1 =
        EntityDetailProtoDTO.newBuilder().setType(EntityTypeProtoEnum.CONNECTORS).setName("someConnector").build();

    Set<EntityDetailProtoDTO> referredEntities = new HashSet<>();
    referredEntities.add(entityDetailProtoDTO1);

    setupUsageHelper.publishServiceEntitySetupUsage(setupUsageOwnerEntity, referredEntities);

    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(producer, times(4)).send(captor.capture());
    List<Message> messages = captor.getAllValues();

    EntitySetupUsageCreateV2DTO entitySetupUsageCreateV2DTO =
        EntitySetupUsageCreateV2DTO.parseFrom(messages.get(0).getData());
    assertThat(entitySetupUsageCreateV2DTO.getAccountIdentifier()).isEqualTo("accountId");
    assertThat(entitySetupUsageCreateV2DTO.getReferredByEntity().getIdentifierRef()).isNotNull();
    assertThat(messages.get(0).getMetadataMap().keySet())
        .containsExactlyInAnyOrder("accountId", EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE,
            EventsFrameworkMetadataConstants.ACTION);
    assertThat(messages.get(0).getMetadataMap().values())
        .containsExactlyInAnyOrder("accountId", "CONNECTORS", EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION);

    assertThat(
        messages.subList(1, messages.size())
            .stream()
            .map(msg -> String.valueOf(msg.getMetadataMap().get(EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE)))
            .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("TEMPLATE", "FILES", "SECRETS");
  }
}
