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
import io.harness.ng.core.activityhistory.dto.ActivityDetail;
import io.harness.ng.core.activityhistory.dto.ConnectivityCheckActivityDetailDTO;
import io.harness.ng.core.activityhistory.dto.EntityUsageActivityDetailDTO;
import io.harness.ng.core.activityhistory.dto.NGActivityDTO;
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

public class NGActivityDTOToEntityMapperTest extends CategoryTest {
  String referredEntityOrgIdentifier = "referredEntityOrgIdentifier";
  String referredEntityProjIdentifier = "referredEntityProjIdentifier";
  String referredEntityIdentifier = "referredEntityIdentifier";
  String activityDescription = "activityDescription";
  String accountIdentifier = "accountIdentifier";
  String referredByEntityIdentifier = "referredByEntityIdentifier";
  String referredByEntityOrgIdentifier = "referredByEntityOrgIdentifier";
  String referredByEntityProjectIdentifier = "referredByEntityProjectIdentifier";
  long activityTime = System.currentTimeMillis();

  @InjectMocks NGActivityDTOToEntityMapper activityDTOToEntityMapper;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void toActivityHistoryEntityForConnectivityCheckActivity() {
    ConnectivityCheckActivityDetailDTO connectivityCheckActivity = ConnectivityCheckActivityDetailDTO.builder().build();
    NGActivityDTO activityHistoryDTO = createActivityHistoryDTO(connectivityCheckActivity, CONNECTIVITY_CHECK);
    NGActivity activityEntity = activityDTOToEntityMapper.toActivityHistoryEntity(activityHistoryDTO);
    verifyActivityHistoryEntityValues(activityEntity, CONNECTIVITY_CHECK);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void toActivityHistoryEntityForEntityUsageActivity() {
    String referredByEntityFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(accountIdentifier,
        referredByEntityOrgIdentifier, referredByEntityProjectIdentifier, referredByEntityIdentifier);
    EntityReference referredByEntityRef = IdentifierRefHelper.getIdentifierRef(referredByEntityIdentifier,
        accountIdentifier, referredByEntityOrgIdentifier, referredByEntityProjectIdentifier);
    EntityDetail referredByEntity = EntityDetail.builder().entityRef(referredByEntityRef).type(PIPELINES).build();
    EntityUsageActivityDetailDTO entityUsageActivityDetailDTO =
        EntityUsageActivityDetailDTO.builder().referredByEntity(referredByEntity).build();
    NGActivityDTO activityHistoryDTO = createActivityHistoryDTO(entityUsageActivityDetailDTO, ENTITY_USAGE);
    NGActivity activityEntity = activityDTOToEntityMapper.toActivityHistoryEntity(activityHistoryDTO);
    EntityUsageActivityDetail entityUsageActivityDetailEntity = (EntityUsageActivityDetail) activityEntity;
    verifyActivityHistoryEntityValues(activityEntity, ENTITY_USAGE);
    assertThat(entityUsageActivityDetailEntity.getReferredByEntityFQN())
        .isEqualTo(FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(accountIdentifier,
            referredByEntityOrgIdentifier, referredByEntityProjectIdentifier, referredByEntityIdentifier));
    assertThat(entityUsageActivityDetailEntity.getReferredByEntityType()).isEqualTo(PIPELINES.toString());
    assertThat(entityUsageActivityDetailEntity.getReferredByEntity()).isEqualTo(referredByEntity);
  }

  private NGActivityDTO createActivityHistoryDTO(ActivityDetail activityDetail, NGActivityType ngActivityType) {
    EntityReference referredEntityRef = IdentifierRefHelper.getIdentifierRef(
        referredEntityIdentifier, accountIdentifier, referredEntityOrgIdentifier, referredEntityProjIdentifier);
    EntityDetail referredEntity = EntityDetail.builder().entityRef(referredEntityRef).type(CONNECTORS).build();
    return NGActivityDTO.builder()
        .accountIdentifier(accountIdentifier)
        .activityStatus(SUCCESS)
        .activityTime(activityTime)
        .description(activityDescription)
        .detail(activityDetail)
        .referredEntity(referredEntity)
        .type(ngActivityType)
        .build();
  }

  private void verifyActivityHistoryEntityValues(NGActivity activityEntity, NGActivityType ngActivityType) {
    assertThat(activityEntity).isNotNull();
    assertThat(activityEntity.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(activityEntity.getReferredEntity()).isEqualTo(activityEntity.getReferredEntity());
    assertThat(activityEntity.getActivityTime()).isEqualTo(activityTime);
    assertThat(activityEntity.getDescription()).isEqualTo(activityDescription);
    assertThat(activityEntity.getReferredEntityType()).isEqualTo(CONNECTORS.toString());
    assertThat(activityEntity.getType()).isEqualTo(ngActivityType.toString());
    assertThat(activityEntity.getActivityStatus()).isEqualTo(SUCCESS.toString());
    assertThat(activityEntity.getReferredEntityFQN())
        .isEqualTo(FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
            accountIdentifier, referredEntityOrgIdentifier, referredEntityProjIdentifier, referredEntityIdentifier));
  }
}
