/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.credit.services.impl;

import static io.harness.credit.CreditTestConstant.ACCOUNT_IDENTIFIER;
import static io.harness.credit.CreditTestConstant.DEFAULT_CI_CREDIT_DTO;
import static io.harness.credit.CreditTestConstant.DEFAULT_CREDIT;
import static io.harness.rule.OwnerRule.KAPIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.credit.beans.credits.CreditDTO;
import io.harness.credit.entities.Credit;
import io.harness.credit.mappers.CreditObjectConverter;
import io.harness.credit.utils.CreditStatus;
import io.harness.repositories.CreditRepository;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class CreditServiceImplTest extends CategoryTest {
  @Mock private CreditRepository creditRepository;
  @Mock private CreditObjectConverter creditObjectConverter;
  @InjectMocks private CreditServiceImpl creditService;

  private static final List<Credit> CREDITS = Collections.singletonList(DEFAULT_CREDIT);

  @Before
  public void setUp() {
    initMocks(this);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetCredits() {
    when(creditRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(CREDITS);
    when(creditObjectConverter.toDTO(any())).thenReturn(DEFAULT_CI_CREDIT_DTO);

    List<CreditDTO> creditDTOS = creditService.getCredits(ACCOUNT_IDENTIFIER);
    assertThat(creditDTOS).isNotNull();
    assertThat(creditDTOS.size()).isEqualTo(1);
    assertThat(creditDTOS.get(0).getAccountIdentifier()).isEqualTo(ACCOUNT_IDENTIFIER);
    assertThat(creditDTOS.get(0).getCreditStatus()).isEqualTo(CreditStatus.EXPIRED);
  }
}
