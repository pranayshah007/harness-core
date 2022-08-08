/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.businessMapping.service.impl;

import static io.harness.rule.OwnerRule.SAHILDEEP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.views.businessMapping.dao.BusinessMappingDao;
import io.harness.ccm.views.businessMapping.entities.BusinessMapping;
import io.harness.ccm.views.businessMapping.helper.BusinessMappingHelper;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.helper.AwsAccountFieldHelper;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BusinessMappingServiceImplTest extends CategoryTest {
  @Mock private BusinessMappingDao businessMappingDao;
  @Mock private AwsAccountFieldHelper awsAccountFieldHelper;
  @InjectMocks private BusinessMappingServiceImpl businessMappingService;
  private BusinessMapping businessMapping;

  @Before
  public void setUp() {
    businessMapping = BusinessMappingHelper.getBusinessMapping(BusinessMappingHelper.TEST_ID);
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
    when(businessMappingDao.findByAccountId(BusinessMappingHelper.TEST_ACCOUNT_ID)).thenReturn(businessMappings);
    final List<BusinessMapping> response = businessMappingService.list(BusinessMappingHelper.TEST_ACCOUNT_ID);
    verify(businessMappingDao).findByAccountId(BusinessMappingHelper.TEST_ACCOUNT_ID);
    verify(awsAccountFieldHelper, times(4)).mergeAwsAccountNameInAccountRules(anyList(), anyString());
    assertThat(response).isEqualTo(businessMappings);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testGetByUUID() {
    when(businessMappingDao.get(BusinessMappingHelper.TEST_ID)).thenReturn(businessMapping);
    final BusinessMapping response = businessMappingService.get(BusinessMappingHelper.TEST_ID);
    verify(businessMappingDao).get(BusinessMappingHelper.TEST_ID);
    assertThat(response).isEqualTo(businessMapping);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testGetByUUIDAndAccountId() {
    when(businessMappingDao.get(BusinessMappingHelper.TEST_ID, BusinessMappingHelper.TEST_ACCOUNT_ID))
        .thenReturn(businessMapping);
    final BusinessMapping response =
        businessMappingService.get(BusinessMappingHelper.TEST_ID, BusinessMappingHelper.TEST_ACCOUNT_ID);
    verify(businessMappingDao).get(BusinessMappingHelper.TEST_ID, BusinessMappingHelper.TEST_ACCOUNT_ID);
    verify(awsAccountFieldHelper, times(4)).mergeAwsAccountNameInAccountRules(anyList(), anyString());
    assertThat(response).isEqualTo(businessMapping);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testUpdate() {
    when(businessMappingDao.update(businessMapping)).thenReturn(businessMapping);
    final BusinessMapping response = businessMappingService.update(businessMapping);
    verify(businessMappingDao).update(businessMapping);
    verify(awsAccountFieldHelper, times(4)).removeAwsAccountNameFromAccountRules(anyList());
    assertThat(response).isEqualTo(businessMapping);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testDelete() {
    when(businessMappingDao.delete(BusinessMappingHelper.TEST_ID, BusinessMappingHelper.TEST_ACCOUNT_ID))
        .thenReturn(true);
    final boolean response =
        businessMappingService.delete(BusinessMappingHelper.TEST_ID, BusinessMappingHelper.TEST_ACCOUNT_ID);
    verify(businessMappingDao).delete(BusinessMappingHelper.TEST_ID, BusinessMappingHelper.TEST_ACCOUNT_ID);
    assertThat(response).isTrue();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testGetBusinessMappingViewFields() {
    final List<BusinessMapping> businessMappings = Collections.singletonList(businessMapping);
    when(businessMappingDao.findByAccountId(BusinessMappingHelper.TEST_ACCOUNT_ID)).thenReturn(businessMappings);
    final List<ViewField> response =
        businessMappingService.getBusinessMappingViewFields(BusinessMappingHelper.TEST_ACCOUNT_ID);
    verify(businessMappingDao).findByAccountId(BusinessMappingHelper.TEST_ACCOUNT_ID);
    assertThat(response.size()).isEqualTo(1);
    final ViewField viewField = response.get(0);
    assertThat(viewField.getFieldId()).isEqualTo(BusinessMappingHelper.TEST_ID);
    assertThat(viewField.getFieldName()).isEqualTo(BusinessMappingHelper.TEST_NAME);
    assertThat(viewField.getIdentifier()).isEqualTo(ViewFieldIdentifier.BUSINESS_MAPPING);
    assertThat(viewField.getIdentifierName()).isEqualTo(ViewFieldIdentifier.BUSINESS_MAPPING.getDisplayName());
  }
}
