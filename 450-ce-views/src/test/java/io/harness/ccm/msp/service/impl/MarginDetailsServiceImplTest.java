/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.msp.service.impl;

import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_ACCOUNT_FIELD;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_ACCOUNT_FIELD_ID;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.AWS;
import static io.harness.ccm.views.entities.ViewIdOperator.IN;
import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.constants.ViewFieldConstants;
import io.harness.ccm.msp.dao.MarginDetailsDao;
import io.harness.ccm.msp.entities.MarginDetails;
import io.harness.ccm.msp.service.intf.MarginDetailsBqService;
import io.harness.ccm.msp.service.intf.MspValidationService;
import io.harness.ccm.views.businessmapping.entities.CostTarget;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MarginDetailsServiceImplTest extends CategoryTest {
  @Mock private MarginDetailsDao marginDetailsDao;
  @Mock private MarginDetailsBqService marginDetailsBqService;
  @Mock private MspValidationService mspValidationService;
  @InjectMocks @Inject private MarginDetailsServiceImpl marginDetailsService;

  private static final String MANAGED_ACCOUNT_ID = "account_id";
  private static final String MANAGED_ACCOUNT_NAME = "account_name";
  private static final String MSP_ACCOUNT_ID = "msp_account_id";
  private static final String UUID = "uuid";
  private static final String AWS_ACCOUNT_NAME = "aws_account_name";
  private static final String UPDATED_AWS_ACCOUNT_NAME = "updated_aws_account_name";

  private static final Double MARGIN_PERCENTAGE = 10.0;

  @Before
  public void setUp() throws Exception {
    when(marginDetailsDao.save(getDummyMarginDetails())).thenReturn(UUID);
    when(marginDetailsDao.getMarginDetailsForAccount(MSP_ACCOUNT_ID, MANAGED_ACCOUNT_ID))
        .thenReturn(getDummyMarginDetails());
    when(marginDetailsDao.list(MSP_ACCOUNT_ID)).thenReturn(Collections.singletonList(getDummyMarginDetails()));
    when(marginDetailsDao.update(getUpdatedDummyMarginDetails())).thenReturn(getUpdatedDummyMarginDetails());
    when(marginDetailsDao.unsetMarginRules(UUID)).thenReturn(getDummyMarginDetailsPostUnset());
    when(marginDetailsDao.get(UUID)).thenReturn(getDummyMarginDetails());
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testSave() {
    String uuid = marginDetailsService.save(getDummyMarginDetails());
    assertThat(uuid).isEqualTo(UUID);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetPerManagedAccountId() {
    MarginDetails marginDetails = marginDetailsService.get(MSP_ACCOUNT_ID, MANAGED_ACCOUNT_ID);
    assertThat(marginDetails.getUuid()).isEqualTo(UUID);
    assertThat(marginDetails.getAccountId()).isEqualTo(MANAGED_ACCOUNT_ID);
    assertThat(marginDetails.getMspAccountId()).isEqualTo(MSP_ACCOUNT_ID);
    assertThat(marginDetails.getAccountName()).isEqualTo(MANAGED_ACCOUNT_NAME);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testList() {
    List<MarginDetails> marginDetailsList = marginDetailsService.list(MSP_ACCOUNT_ID);
    assertThat(marginDetailsList.size()).isEqualTo(1);
    MarginDetails marginDetails = marginDetailsList.get(0);
    assertThat(marginDetails.getUuid()).isEqualTo(UUID);
    assertThat(marginDetails.getAccountId()).isEqualTo(MANAGED_ACCOUNT_ID);
    assertThat(marginDetails.getMspAccountId()).isEqualTo(MSP_ACCOUNT_ID);
    assertThat(marginDetails.getAccountName()).isEqualTo(MANAGED_ACCOUNT_NAME);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testUpdate() {
    MarginDetails marginDetails = marginDetailsService.update(getUpdatedDummyMarginDetails());
    assertThat(marginDetails.getUuid()).isEqualTo(UUID);
    assertThat(marginDetails.getAccountId()).isEqualTo(MANAGED_ACCOUNT_ID);
    assertThat(marginDetails.getMspAccountId()).isEqualTo(MSP_ACCOUNT_ID);
    assertThat(marginDetails.getAccountName()).isEqualTo(MANAGED_ACCOUNT_NAME);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testDelete() {
    MarginDetails marginDetailsPostUnset = marginDetailsService.unsetMargins(UUID);
    assertThat(marginDetailsPostUnset).isEqualTo(getDummyMarginDetailsPostUnset());
  }

  private MarginDetails getDummyMarginDetails() {
    return MarginDetails.builder()
        .uuid(UUID)
        .accountId(MANAGED_ACCOUNT_ID)
        .accountName(MANAGED_ACCOUNT_NAME)
        .mspAccountId(MSP_ACCOUNT_ID)
        .marginRules(Collections.singletonList(
            CostTarget.builder()
                .marginPercentage(MARGIN_PERCENTAGE)
                .rules(Collections.singletonList(
                    ViewRule.builder()
                        .viewConditions(Collections.singletonList(
                            ViewIdCondition.builder()
                                .values(Collections.singletonList(AWS_ACCOUNT_NAME))
                                .viewOperator(IN)
                                .viewField(ViewField.builder()
                                               .identifierName(ViewFieldConstants.AWS_ACCOUNT_FIELD)
                                               .fieldId(ViewFieldConstants.AWS_ACCOUNT_FIELD_ID)
                                               .fieldName(ViewFieldConstants.AWS_ACCOUNT_FIELD)
                                               .identifier(AWS)
                                               .build())
                                .build()))
                        .build()))
                .build()))
        .build();
  }

  private MarginDetails getUpdatedDummyMarginDetails() {
    return MarginDetails.builder()
        .uuid(UUID)
        .accountId(MANAGED_ACCOUNT_ID)
        .accountName(MANAGED_ACCOUNT_NAME)
        .mspAccountId(MSP_ACCOUNT_ID)
        .marginRules(
            Collections.singletonList(CostTarget.builder()
                                          .marginPercentage(MARGIN_PERCENTAGE)
                                          .rules(Collections.singletonList(
                                              ViewRule.builder()
                                                  .viewConditions(Collections.singletonList(
                                                      ViewIdCondition.builder()
                                                          .values(Collections.singletonList(UPDATED_AWS_ACCOUNT_NAME))
                                                          .viewOperator(IN)
                                                          .viewField(ViewField.builder()
                                                                         .identifierName("AWS")
                                                                         .fieldId(AWS_ACCOUNT_FIELD_ID)
                                                                         .fieldName(AWS_ACCOUNT_FIELD)
                                                                         .identifier(AWS)
                                                                         .build())
                                                          .build()))
                                                  .build()))
                                          .build()))
        .build();
  }

  private MarginDetails getDummyMarginDetailsPostUnset() {
    return MarginDetails.builder()
        .uuid(UUID)
        .accountId(MANAGED_ACCOUNT_ID)
        .accountName(MANAGED_ACCOUNT_NAME)
        .mspAccountId(MSP_ACCOUNT_ID)
        .build();
  }
}
