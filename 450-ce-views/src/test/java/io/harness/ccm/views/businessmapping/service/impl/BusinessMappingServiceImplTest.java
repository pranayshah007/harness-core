/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.businessmapping.service.impl;

import static io.harness.rule.OwnerRule.SAHILDEEP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.CCMSortOrder;
import io.harness.ccm.views.businessmapping.dao.BusinessMappingDao;
import io.harness.ccm.views.businessmapping.entities.BusinessMapping;
import io.harness.ccm.views.businessmapping.entities.BusinessMappingListDTO;
import io.harness.ccm.views.businessmapping.entities.CostCategorySortType;
import io.harness.ccm.views.businessmapping.helper.BusinessMappingTestHelper;
import io.harness.ccm.views.businessmapping.service.intf.BusinessMappingValidationService;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.helper.AwsAccountFieldHelper;
import io.harness.ccm.views.helper.BusinessMappingDataSourceHelper;
import io.harness.rule.Owner;

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
public class BusinessMappingServiceImplTest extends CategoryTest {
  @Mock private BusinessMappingDao businessMappingDao;
  @Mock private AwsAccountFieldHelper awsAccountFieldHelper;
  @Mock private BusinessMappingDataSourceHelper businessMappingDataSourceHelper;
  @Mock private BusinessMappingValidationService businessMappingValidationService;
  @InjectMocks private BusinessMappingServiceImpl businessMappingService;
  private BusinessMapping businessMapping;

  @Before
  public void setUp() {
    businessMapping = BusinessMappingTestHelper.getBusinessMapping(BusinessMappingTestHelper.TEST_ID);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testSave() {
    when(businessMappingDao.save(any(BusinessMapping.class))).thenReturn(businessMapping);
    final BusinessMapping response = businessMappingService.save(businessMapping);
    verify(businessMappingDao).save(businessMapping);
    verify(awsAccountFieldHelper, times(4)).removeAwsAccountNameFromAccountRules(anyList());
    assertThat(response).isEqualTo(businessMapping);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testList() {
    final List<BusinessMapping> businessMappings = Collections.singletonList(businessMapping);
    when(businessMappingDao.findByAccountIdAndRegexNameWithLimitAndOffsetAndOrder(
             BusinessMappingTestHelper.TEST_ACCOUNT_ID, BusinessMappingTestHelper.TEST_SEARCH_KEY,
             CostCategorySortType.LAST_EDIT, CCMSortOrder.DESCENDING, BusinessMappingTestHelper.TEST_LIMIT,
             BusinessMappingTestHelper.TEST_OFFSET))
        .thenReturn(businessMappings);
    when(businessMappingDao.getCountByAccountIdAndRegexName(
             BusinessMappingTestHelper.TEST_ACCOUNT_ID, BusinessMappingTestHelper.TEST_SEARCH_KEY))
        .thenReturn(1L);
    final BusinessMappingListDTO response = businessMappingService.list(BusinessMappingTestHelper.TEST_ACCOUNT_ID,
        BusinessMappingTestHelper.TEST_SEARCH_KEY, CostCategorySortType.LAST_EDIT, CCMSortOrder.DESCENDING,
        BusinessMappingTestHelper.TEST_LIMIT, BusinessMappingTestHelper.TEST_OFFSET);
    verify(businessMappingDao)
        .findByAccountIdAndRegexNameWithLimitAndOffsetAndOrder(BusinessMappingTestHelper.TEST_ACCOUNT_ID,
            BusinessMappingTestHelper.TEST_SEARCH_KEY, CostCategorySortType.LAST_EDIT, CCMSortOrder.DESCENDING,
            BusinessMappingTestHelper.TEST_LIMIT, BusinessMappingTestHelper.TEST_OFFSET);
    verify(businessMappingDao)
        .getCountByAccountIdAndRegexName(
            BusinessMappingTestHelper.TEST_ACCOUNT_ID, BusinessMappingTestHelper.TEST_SEARCH_KEY);
    verify(awsAccountFieldHelper, times(4)).mergeAwsAccountNameInAccountRules(anyList(), anyString());
    assertThat(response.getBusinessMappings()).isEqualTo(businessMappings);
    assertThat(response.getTotalCount()).isEqualTo(1L);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testGetByUUID() {
    when(businessMappingDao.get(BusinessMappingTestHelper.TEST_ID)).thenReturn(businessMapping);
    final BusinessMapping response = businessMappingService.get(BusinessMappingTestHelper.TEST_ID);
    verify(businessMappingDao).get(BusinessMappingTestHelper.TEST_ID);
    assertThat(response).isEqualTo(businessMapping);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testGetByUUIDAndAccountId() {
    when(businessMappingDao.get(BusinessMappingTestHelper.TEST_ID, BusinessMappingTestHelper.TEST_ACCOUNT_ID))
        .thenReturn(businessMapping);
    final BusinessMapping response =
        businessMappingService.get(BusinessMappingTestHelper.TEST_ID, BusinessMappingTestHelper.TEST_ACCOUNT_ID);
    verify(businessMappingDao).get(BusinessMappingTestHelper.TEST_ID, BusinessMappingTestHelper.TEST_ACCOUNT_ID);
    verify(awsAccountFieldHelper, times(4)).mergeAwsAccountNameInAccountRules(anyList(), anyString());
    assertThat(response).isEqualTo(businessMapping);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testUpdate() {
    when(businessMappingDao.update(businessMapping)).thenReturn(businessMapping);
    final BusinessMapping response = businessMappingService.update(businessMapping, businessMapping);
    verify(businessMappingDao).update(businessMapping);
    verify(awsAccountFieldHelper, times(4)).removeAwsAccountNameFromAccountRules(anyList());
    assertThat(response).isEqualTo(businessMapping);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testDelete() {
    when(businessMappingDao.delete(BusinessMappingTestHelper.TEST_ID, BusinessMappingTestHelper.TEST_ACCOUNT_ID))
        .thenReturn(true);
    final boolean response =
        businessMappingService.delete(BusinessMappingTestHelper.TEST_ID, BusinessMappingTestHelper.TEST_ACCOUNT_ID);
    verify(businessMappingDao).delete(BusinessMappingTestHelper.TEST_ID, BusinessMappingTestHelper.TEST_ACCOUNT_ID);
    assertThat(response).isTrue();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testGetBusinessMappingViewFields() {
    final List<BusinessMapping> businessMappings = Collections.singletonList(businessMapping);
    when(businessMappingDao.findByAccountId(BusinessMappingTestHelper.TEST_ACCOUNT_ID)).thenReturn(businessMappings);
    final List<ViewField> response =
        businessMappingService.getBusinessMappingViewFields(BusinessMappingTestHelper.TEST_ACCOUNT_ID);
    verify(businessMappingDao).findByAccountId(BusinessMappingTestHelper.TEST_ACCOUNT_ID);
    assertThat(response.size()).isEqualTo(1);
    final ViewField viewField = response.get(0);
    assertThat(viewField.getFieldId()).isEqualTo(BusinessMappingTestHelper.TEST_ID);
    assertThat(viewField.getFieldName()).isEqualTo(BusinessMappingTestHelper.TEST_NAME);
    assertThat(viewField.getIdentifier()).isEqualTo(ViewFieldIdentifier.BUSINESS_MAPPING);
    assertThat(viewField.getIdentifierName()).isEqualTo(ViewFieldIdentifier.BUSINESS_MAPPING.getDisplayName());
  }
}
