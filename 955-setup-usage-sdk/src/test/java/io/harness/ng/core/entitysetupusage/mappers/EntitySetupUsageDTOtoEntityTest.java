/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entitysetupusage.mappers;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.beans.EntityReference;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.entity.EntitySetupUsage;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.utils.IdentifierRefHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class EntitySetupUsageDTOtoEntityTest extends CategoryTest {
  @InjectMocks EntitySetupUsageDTOtoEntity entitySetupUsageDTOtoEntity;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void toEntityReference() {
    String accountIdentifier = "accountIdentifier";
    String referredByEntityIdentifier = "account.referredByEntityIdentifier";
    EntityType referredByEntityType = EntityType.PIPELINES;
    String referredByEntityName = "Pipeline 1";
    EntityType referredEntityType = EntityType.CONNECTORS;
    String referredEntityIdentifier = "account.referredEntityIdentifier";
    String referredEntityName = "Connector 1";
    EntityReference referredEntityRef =
        IdentifierRefHelper.getIdentifierRef(referredEntityIdentifier, accountIdentifier, null, null);
    EntityDetail referredEntity =
        EntityDetail.builder().entityRef(referredEntityRef).type(referredEntityType).name(referredEntityName).build();
    EntityReference referredByEntityRef =
        IdentifierRefHelper.getIdentifierRef(referredByEntityIdentifier, accountIdentifier, null, null);
    EntityDetail referredByEntity = EntityDetail.builder()
                                        .entityRef(referredByEntityRef)
                                        .type(referredByEntityType)
                                        .name(referredByEntityName)
                                        .build();
    EntitySetupUsageDTO entitySetupUsageDTO = EntitySetupUsageDTO.builder()
                                                  .accountIdentifier(accountIdentifier)
                                                  .referredEntity(referredEntity)
                                                  .referredByEntity(referredByEntity)
                                                  .build();
    EntitySetupUsage entitySetupUsage = entitySetupUsageDTOtoEntity.toEntityReference(entitySetupUsageDTO);
    assertThat(entitySetupUsage).isNotNull();
    assertThat(entitySetupUsage.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(entitySetupUsage.getReferredByEntityFQN()).isEqualTo(referredByEntityRef.getFullyQualifiedName());
    assertThat(entitySetupUsage.getReferredEntityFQN()).isEqualTo(referredEntityRef.getFullyQualifiedName());
    assertThat(entitySetupUsage.getReferredByEntityType()).isEqualTo(referredByEntityType.toString());
    assertThat(entitySetupUsage.getReferredEntityType()).isEqualTo(referredEntityType.toString());
  }
}
