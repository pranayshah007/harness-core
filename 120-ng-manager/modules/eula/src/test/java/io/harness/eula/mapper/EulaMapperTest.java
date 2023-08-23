/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.eula.mapper;

import static io.harness.rule.OwnerRule.BHAVYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.eula.AgreementType;
import io.harness.eula.dto.EulaDTO;
import io.harness.eula.entity.Eula;
import io.harness.rule.Owner;

import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class EulaMapperTest extends CategoryTest {
  private EulaMapper eulaMapper;
  private static final String ACCOUNT_ID = "accountId";

  @Before
  public void setUp() throws IllegalAccessException {
    MockitoAnnotations.openMocks(this);
    eulaMapper = new EulaMapper();
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void toEntity() {
    EulaDTO eulaDTO =
        io.harness.eula.dto.EulaDTO.builder().agreement(AgreementType.AIDA).accountIdentifier(ACCOUNT_ID).build();
    Eula eula = io.harness.eula.entity.Eula.builder()
                    .signedAgreements(Set.of(AgreementType.AIDA))
                    .accountIdentifier(ACCOUNT_ID)
                    .build();
    assertThat(eulaMapper.toEntity(eulaDTO)).isEqualTo(eula);
  }
}