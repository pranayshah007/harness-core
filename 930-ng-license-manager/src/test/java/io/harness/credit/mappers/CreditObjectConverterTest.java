/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.credit.mappers;

import static io.harness.credit.CreditTestConstant.DEFAULT_CI_CREDIT_DTO;
import static io.harness.credit.CreditTestConstant.DEFAULT_CREDIT;
import static io.harness.credit.CreditTestConstant.DEFAULT_MODULE_TYPE;
import static io.harness.rule.OwnerRule.XIN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.category.element.UnitTests;
import io.harness.credit.beans.credits.CICreditDTO;
import io.harness.credit.beans.credits.CreditDTO;
import io.harness.credit.entities.CICredit;
import io.harness.credit.entities.Credit;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class CreditObjectConverterTest extends CategoryTest {
  @InjectMocks CreditObjectConverter creditObjectConverter;
  @Mock CreditObjectMapper CICreditObjectMapper;
  @Mock Map<ModuleType, CreditObjectMapper> mapperMap;
  private CreditDTO defaultCreditDTO;
  private Credit defaultCredit;

  @Before
  public void setUp() {
    initMocks(this);
    defaultCreditDTO = DEFAULT_CI_CREDIT_DTO;
    defaultCredit = DEFAULT_CREDIT;
    when(CICreditObjectMapper.toEntity(any())).thenReturn(CICredit.builder().build());
    when(CICreditObjectMapper.toDTO(any())).thenReturn(CICreditDTO.builder().build());
    when(mapperMap.get(DEFAULT_MODULE_TYPE)).thenReturn(CICreditObjectMapper);
  }

  @Test
  @Owner(developers = XIN)
  @Category(UnitTests.class)
  public void testToDTO() {
    CreditDTO creditDTO = creditObjectConverter.toDTO(defaultCredit);
    assertThat(creditDTO.equals(DEFAULT_CI_CREDIT_DTO));
  }

  @Test
  @Owner(developers = XIN)
  @Category(UnitTests.class)
  public void testToEntity() {
    Credit credit = creditObjectConverter.toEntity(defaultCreditDTO);
    assertThat(credit.equals(DEFAULT_CREDIT));
  }
}
