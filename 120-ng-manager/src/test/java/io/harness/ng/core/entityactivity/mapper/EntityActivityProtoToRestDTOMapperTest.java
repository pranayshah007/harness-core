/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entityactivity.mapper;

import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.CONNECTORS;
import static io.harness.ng.core.activityhistory.NGActivityStatus.SUCCESS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;
import io.harness.eventsframework.schemas.entityactivity.EntityActivityCreateDTO;
import io.harness.ng.core.activityhistory.NGActivityType;
import io.harness.ng.core.activityhistory.dto.NGActivityDTO;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.protobuf.StringValue;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class EntityActivityProtoToRestDTOMapperTest extends CategoryTest {
  @InjectMocks EntityActivityProtoToRestDTOMapper entityActivityProtoToRestDTOMapper;
  @Mock EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  private static final String accountIdentifier = "accountIdentifier";
  private static final String orgIdentifier = "orgIdentifier";
  private static final String projectIdentifier = "projectIdentifier";
  private static final String identifier = "identifier";
  private static final String name = "name";
  private static final long time = System.currentTimeMillis();
  private static final String errorMessage = "errorMessage";

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    when(entityDetailProtoToRestMapper.createEntityDetailDTO(any())).thenCallRealMethod();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void toRestDTO() {
    NGActivityDTO activityDTO = entityActivityProtoToRestDTOMapper.toRestDTO(createConnectivityCheckActivityDTO());
    assertThat(activityDTO).isNotNull();
    assertThat(activityDTO.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(activityDTO.getType()).isEqualTo(NGActivityType.CONNECTIVITY_CHECK);
    assertThat(activityDTO.getActivityTime()).isEqualTo(time);
    assertThat(activityDTO.getDescription()).isEqualTo("CONNECTIVITY_CHECK_DESCRIPTION");
    assertThat(activityDTO.getReferredEntity().getEntityRef().getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(activityDTO.getReferredEntity().getEntityRef().getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(activityDTO.getReferredEntity().getEntityRef().getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(activityDTO.getReferredEntity().getEntityRef().getIdentifier()).isEqualTo(identifier);
    assertThat(activityDTO.getReferredEntity().getType()).isEqualTo(EntityType.CONNECTORS);
    assertThat(activityDTO.getActivityStatus()).isEqualTo(SUCCESS);
  }

  private EntityActivityCreateDTO createConnectivityCheckActivityDTO() {
    IdentifierRefProtoDTO identifierRefProtoDTO = IdentifierRefProtoDTO.newBuilder()
                                                      .setScope(ScopeProtoEnum.PROJECT)
                                                      .setAccountIdentifier(StringValue.of(accountIdentifier))
                                                      .setOrgIdentifier(StringValue.of(orgIdentifier))
                                                      .setProjectIdentifier(StringValue.of(projectIdentifier))
                                                      .setIdentifier(StringValue.of(identifier))
                                                      .build();
    EntityDetailProtoDTO referredEntity = EntityDetailProtoDTO.newBuilder()
                                              .setType(CONNECTORS)
                                              .setIdentifierRef(identifierRefProtoDTO)
                                              .setName(name)
                                              .build();
    return EntityActivityCreateDTO.newBuilder()
        .setType(NGActivityType.CONNECTIVITY_CHECK.toString())
        .setStatus("SUCCESS")
        .setActivityTime(time)
        .setAccountIdentifier(accountIdentifier)
        .setDescription("CONNECTIVITY_CHECK_DESCRIPTION")
        .setErrorMessage(errorMessage)
        .setReferredEntity(referredEntity)
        .build();
  }
}
