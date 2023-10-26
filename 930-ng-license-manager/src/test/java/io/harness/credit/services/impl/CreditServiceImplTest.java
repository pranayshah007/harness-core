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
import static io.harness.credit.CreditTestConstant.DEFAULT_ID;
import static io.harness.credit.CreditTestConstant.MISMATCH_ACCOUNT_IDENTIFIER;
import static io.harness.credit.CreditTestConstant.TO_BE_UPDATE_CI_CREDIT_DTO;
import static io.harness.credit.CreditTestConstant.TO_BE_UPDATE_CI_CREDIT_DTO_WRONG_UUID;
import static io.harness.credit.CreditTestConstant.UPDATE_CREDIT;
import static io.harness.credit.CreditTestConstant.UPDATE_QUANTITY;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.XIN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.CreditType;
import io.harness.category.element.UnitTests;
import io.harness.credit.beans.credits.CreditDTO;
import io.harness.credit.entities.Credit;
import io.harness.credit.mappers.CreditObjectConverter;
import io.harness.credit.utils.CreditStatus;
import io.harness.exception.InvalidRequestException;
import io.harness.repositories.CreditRepository;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
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

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetLatestFreeAndActiveCredits() {
    when(creditRepository.findByAccountIdentifierAndCreditTypeAndCreditStatus(
             ACCOUNT_IDENTIFIER, CreditType.FREE, CreditStatus.ACTIVE))
        .thenReturn(CREDITS);
    when(creditObjectConverter.toDTO(any())).thenReturn(DEFAULT_CI_CREDIT_DTO);

    List<CreditDTO> creditDTOS = creditService.getCredits(ACCOUNT_IDENTIFIER, CreditType.FREE, CreditStatus.ACTIVE);
    assertThat(creditDTOS).isNotNull();
    assertThat(creditDTOS.size()).isEqualTo(1);
    assertThat(creditDTOS.get(0).getAccountIdentifier()).isEqualTo(ACCOUNT_IDENTIFIER);
    assertThat(creditDTOS.get(0).getCreditStatus()).isEqualTo(CreditStatus.EXPIRED);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testPurchaseCredit() {
    assertThatThrownBy(() -> creditService.purchaseCredit("random_account", DEFAULT_CI_CREDIT_DTO))
        .hasMessage("AccountIdentifier: [random_account] did not match with the Credit information")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = XIN)
  @Category(UnitTests.class)
  public void testUpdateCredit() {
    when(creditObjectConverter.toEntity(any())).thenReturn(UPDATE_CREDIT);
    when(creditRepository.findById(DEFAULT_ID)).thenReturn(Optional.of(DEFAULT_CREDIT));
    when(creditRepository.save(UPDATE_CREDIT)).thenReturn(UPDATE_CREDIT);
    when(creditObjectConverter.toDTO(UPDATE_CREDIT)).thenReturn(TO_BE_UPDATE_CI_CREDIT_DTO);
    CreditDTO updatedCreditDTO = creditService.updateCredit(ACCOUNT_IDENTIFIER, TO_BE_UPDATE_CI_CREDIT_DTO);
    assertThat(updatedCreditDTO).isNotNull();
    assertThat(updatedCreditDTO.getQuantity()).isEqualTo(UPDATE_QUANTITY);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = XIN)
  @Category(UnitTests.class)
  public void testUpdateCreditWithMisMatchIdentifier() {
    CreditDTO updatedCreditDTO = creditService.updateCredit(MISMATCH_ACCOUNT_IDENTIFIER, TO_BE_UPDATE_CI_CREDIT_DTO);
    assertThat(updatedCreditDTO).isNull();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = XIN)
  @Category(UnitTests.class)
  public void testUpdateCreditWithoutUUID() {
    CreditDTO updatedCreditDTO = creditService.updateCredit(ACCOUNT_IDENTIFIER, TO_BE_UPDATE_CI_CREDIT_DTO_WRONG_UUID);
    assertThat(updatedCreditDTO).isNull();
  }

  @Test(expected = NotFoundException.class)
  @Owner(developers = XIN)
  @Category(UnitTests.class)
  public void testUpdateCreditWithoutNotFoundException() {
    when(creditObjectConverter.toEntity(any())).thenReturn(UPDATE_CREDIT);
    when(creditRepository.findById(DEFAULT_ID)).thenReturn(Optional.empty());
    when(creditRepository.save(UPDATE_CREDIT)).thenReturn(UPDATE_CREDIT);
    when(creditObjectConverter.toDTO(UPDATE_CREDIT)).thenReturn(TO_BE_UPDATE_CI_CREDIT_DTO);
    CreditDTO updatedCreditDTO = creditService.updateCredit(ACCOUNT_IDENTIFIER, TO_BE_UPDATE_CI_CREDIT_DTO);
    assertThat(updatedCreditDTO).isNull();
  }
}
