/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.rule.OwnerRule.HITESH;
import static io.harness.rule.OwnerRule.ROHIT;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.views.dao.ViewCustomFieldDao;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ViewCustomField;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.service.CEViewService;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import com.google.cloud.bigquery.BigQuery;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ViewCustomFieldServiceImplTest extends CategoryTest {
  @InjectMocks @Inject private ViewCustomFieldServiceImpl viewCustomFieldService;
  @Mock private ViewCustomFieldDao viewCustomFieldDao;
  @Mock private CEViewService ceViewService;
  @Mock BigQuery bigQuery;

  private static final String ACCOUNT_ID = "account_id";
  private static final String VIEW_ID = "view_id";
  private static final String CUSTOM_FIELD_NAME = "custom_field";
  private static final String VIEW_FIELD = "projectId";
  private static final String UUID = "uuid";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhileSavingCustomField() {
    doReturn(viewCustomField()).when(viewCustomFieldDao).findByName(ACCOUNT_ID, VIEW_ID, CUSTOM_FIELD_NAME);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> viewCustomFieldService.save(viewCustomField(), bigQuery, "tableName"));
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testSavingCustomField() {
    doReturn(null).when(viewCustomFieldDao).findByName(ACCOUNT_ID, VIEW_ID, CUSTOM_FIELD_NAME);
    viewCustomFieldService.save(viewCustomField(), bigQuery, "tableName");
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testGetField() {
    doReturn(viewCustomField()).when(viewCustomFieldDao).getById(UUID);
    ViewCustomField viewCustomField = viewCustomFieldService.get(UUID);
    assertThat(viewCustomField).isEqualTo(viewCustomField());
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testDeleteCustomField() {
    doReturn(true).when(viewCustomFieldDao).delete(any(), any());
    doReturn(CEView.builder().build()).when(ceViewService).update(any());
    doReturn(ViewCustomField.builder().viewId(VIEW_ID).build()).when(viewCustomFieldDao).getById(any());
    ViewRule viewRule = ViewRule.builder()
                            .viewConditions(Arrays.asList(
                                ViewIdCondition.builder().viewField(ViewField.builder().fieldId("ID").build()).build()))
                            .build();

    boolean delete =
        viewCustomFieldService.delete(UUID, ACCOUNT_ID, CEView.builder().viewRules(Arrays.asList(viewRule)).build());
    assertThat(delete).isTrue();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testDeleteCustomFieldByViewId() {
    doReturn(true).when(viewCustomFieldDao).deleteByViewId(any(), any());
    boolean delete = viewCustomFieldService.deleteByViewId(UUID, ACCOUNT_ID);
    assertThat(delete).isTrue();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testGetCustomField() {
    doReturn(Collections.singletonList(viewCustomField())).when(viewCustomFieldDao).findByAccountId(ACCOUNT_ID);
    List<ViewField> customFields = viewCustomFieldService.getCustomFields(ACCOUNT_ID);
    assertThat(customFields.size()).isEqualTo(1);
    assertThat(customFields.get(0).getFieldId()).isEqualTo(UUID);
    assertThat(customFields.get(0).getFieldName()).isEqualTo(CUSTOM_FIELD_NAME);
    assertThat(customFields.get(0).getIdentifier()).isEqualTo(ViewFieldIdentifier.CUSTOM);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testGetCustomFieldPerView() {
    doReturn(Collections.singletonList(viewCustomField())).when(viewCustomFieldDao).findByViewId(UUID, ACCOUNT_ID);
    List<ViewField> customFields = viewCustomFieldService.getCustomFieldsPerView(UUID, ACCOUNT_ID);
    assertThat(customFields.size()).isEqualTo(1);
  }

  private ViewCustomField viewCustomField() {
    return ViewCustomField.builder()
        .uuid(UUID)
        .accountId(ACCOUNT_ID)
        .viewId(VIEW_ID)
        .name(CUSTOM_FIELD_NAME)
        .viewFields(singletonList(
            ViewField.builder().fieldId(VIEW_FIELD).fieldName(VIEW_FIELD).identifier(ViewFieldIdentifier.GCP).build()))
        .build();
  }
}
