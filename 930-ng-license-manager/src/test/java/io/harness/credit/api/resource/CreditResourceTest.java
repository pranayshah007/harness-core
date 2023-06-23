/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.credit.api.resource;

import static io.harness.credit.CreditTestConstant.ACCOUNT_IDENTIFIER;
import static io.harness.credit.CreditTestConstant.DEFAULT_CI_CREDIT_DTO;
import static io.harness.credit.CreditTestConstant.REQUEST_CREDIT_DTO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.credit.beans.credits.CreditDTO;
import io.harness.credit.resource.CreditResource;
import io.harness.credit.services.CreditService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.common.collect.Lists;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.GTM)
public class CreditResourceTest extends CategoryTest {
  @Mock CreditService creditService;
  @InjectMocks CreditResource creditResource;
  private CreditDTO defaultCreditDTO;
  private CreditDTO requestCreditDTO;

  @Before
  public void setUp() {
    initMocks(this);
    defaultCreditDTO = DEFAULT_CI_CREDIT_DTO;
    requestCreditDTO = REQUEST_CREDIT_DTO;
  }

  @Test
  @Owner(developers = OwnerRule.XIN)
  @Category(UnitTests.class)
  public void testGetCredits() {
    doReturn(Lists.newArrayList(defaultCreditDTO)).when(creditService).getCredits(ACCOUNT_IDENTIFIER);
    ResponseDTO<List<CreditDTO>> creditsResponseDTO = creditResource.getCredits(ACCOUNT_IDENTIFIER);
    Mockito.verify(creditService, times(1)).getCredits(ACCOUNT_IDENTIFIER);
    assertThat(creditsResponseDTO.getData()).isNotNull();
  }
}
