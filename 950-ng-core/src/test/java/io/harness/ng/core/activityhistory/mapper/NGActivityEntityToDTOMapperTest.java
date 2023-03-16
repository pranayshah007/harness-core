/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.activityhistory.mapper;

import static io.harness.EntityType.CONNECTORS;
import static io.harness.EntityType.PIPELINES;
import static io.harness.ng.core.activityhistory.NGActivityStatus.SUCCESS;
import static io.harness.ng.core.activityhistory.NGActivityType.CONNECTIVITY_CHECK;
import static io.harness.ng.core.activityhistory.NGActivityType.ENTITY_USAGE;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.beans.EntityReference;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.activityhistory.NGActivityType;
import io.harness.ng.core.activityhistory.dto.EntityUsageActivityDetailDTO;
import io.harness.ng.core.activityhistory.dto.NGActivityDTO;
import io.harness.ng.core.activityhistory.entity.ConnectivityCheckDetail;
import io.harness.ng.core.activityhistory.entity.EntityUsageActivityDetail;
import io.harness.ng.core.activityhistory.entity.NGActivity;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.utils.FullyQualifiedIdentifierHelper;
import io.harness.utils.IdentifierRefHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class NGActivityEntityToDTOMapperTest extends CategoryTest {
  String referredEntityFQN;
  String referredEntityOrgIdentifier = "referredEntityOrgIdentifier";
  String referredEntityProjIdentifier = "referredEntityProjIdentifier";
  String referredEntityIdentifier = "referredEntityIdentifier";
  String errorMessage = "errorMessage";
  String activityDescription = "activityDescription";
  String accountIdentifier = "accountIdentifier";
  String referredByEntityFQN;
  String referredByEntityIdentifier = "referredByEntityIdentifier";
  String referredByEntityOrgIdentifier = "referredByEntityOrgIdentifier";
  String referredByEntityProjectIdentifier = "referredByEntityProjectIdentifier";
  long activityTime = System.currentTimeMillis();

  @InjectMocks NGActivityEntityToDTOMapper activityEntityToDTOMapper;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    referredEntityFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, referredEntityOrgIdentifier, referredEntityProjIdentifier, referredEntityIdentifier);
    referredByEntityFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(accountIdentifier,
        referredByEntityOrgIdentifier, referredByEntityProjectIdentifier, referredByEntityIdentifier);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void writeDTOForConenctivityCheckActivity() {
    NGActivity activityEntity = ConnectivityCheckDetail.builder().build();
    setCommonFieldsOfActivityHistory(activityEntity, CONNECTIVITY_CHECK);
    NGActivityDTO activityHistoryDTO = activityEntityToDTOMapper.writeDTO(activityEntity);
    verifyTheActivityHistoryDetails(activityHistoryDTO, CONNECTIVITY_CHECK);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void writeDTOForEntityUsedActivity() {
    EntityReference referredByEntityReference = IdentifierRefHelper.getIdentifierRef(referredByEntityIdentifier,
        accountIdentifier, referredByEntityOrgIdentifier, referredByEntityProjectIdentifier);
    EntityDetail referredByEntity = EntityDetail.builder().entityRef(referredByEntityReference).build();
    NGActivity activityEntity = EntityUsageActivityDetail.builder()
                                    .referredByEntity(referredByEntity)
                                    .referredByEntityFQN(referredByEntityFQN)
                                    .referredByEntityType(PIPELINES.toString())
                                    .build();
    setCommonFieldsOfActivityHistory(activityEntity, ENTITY_USAGE);
    NGActivityDTO activityHistoryDTO = activityEntityToDTOMapper.writeDTO(activityEntity);
    verifyTheActivityHistoryDetails(activityHistoryDTO, ENTITY_USAGE);
    EntityUsageActivityDetailDTO activityDetailDTO = (EntityUsageActivityDetailDTO) activityHistoryDTO.getDetail();
    assertThat(activityDetailDTO.getReferredByEntity().getEntityRef().getFullyQualifiedName())
        .isEqualTo(referredByEntityFQN);
    assertThat(activityDetailDTO.getReferredByEntity()).isEqualTo(referredByEntity);
  }

  private void setCommonFieldsOfActivityHistory(NGActivity activityEntity, NGActivityType ngActivityType) {
    activityEntity.setReferredEntityType(CONNECTORS.toString());
    activityEntity.setReferredEntityFQN(referredEntityFQN);
    activityEntity.setType(ngActivityType.toString());
    activityEntity.setDescription(activityDescription);
    activityEntity.setActivityTime(activityTime);
    activityEntity.setAccountIdentifier(accountIdentifier);
    activityEntity.setActivityStatus(SUCCESS.toString());
    activityEntity.setReferredEntity(getReferredEntity());
  }

  private EntityDetail getReferredEntity() {
    EntityReference referredEntityReference = IdentifierRefHelper.getIdentifierRef(
        referredEntityIdentifier, accountIdentifier, referredEntityOrgIdentifier, referredEntityProjIdentifier);
    return EntityDetail.builder()
        .type(CONNECTORS)
        .name("referredEntityName")
        .entityRef(referredEntityReference)
        .build();
  }

  private void verifyTheActivityHistoryDetails(NGActivityDTO activityHistoryDTO, NGActivityType ngActivityType) {
    assertThat(activityHistoryDTO).isNotNull();
    assertThat(activityHistoryDTO.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(activityHistoryDTO.getReferredEntity()).isEqualTo(getReferredEntity());
    assertThat(activityHistoryDTO.getType()).isEqualTo(ngActivityType);
    assertThat(activityHistoryDTO.getActivityTime()).isEqualTo(activityTime);
    assertThat(activityHistoryDTO.getDescription()).isEqualTo(activityDescription);
    assertThat(activityHistoryDTO.getReferredEntity().getEntityRef().getFullyQualifiedName())
        .isEqualTo(referredEntityFQN);
    assertThat(activityHistoryDTO.getReferredEntity().getType()).isEqualTo(CONNECTORS);
    assertThat(activityHistoryDTO.getActivityStatus()).isEqualTo(SUCCESS);
  }
}
