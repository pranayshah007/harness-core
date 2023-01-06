/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans.cvnglog;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KANHAIYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.cvnglog.ApiCallLogDTO.ApiCallLogDTOField;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ApiCallLogDTOTest extends CategoryTest {
  private String accountId;
  private String name;
  private String value;

  @Before
  public void setup() {
    this.accountId = generateUuid();
    this.name = generateUuid();
    this.value = generateUuid();
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testAddFieldToRequest_NullField() {
    ApiCallLogDTO apiCallLogDTO = ApiCallLogDTO.builder().accountId(accountId).build();
    assertThatThrownBy(() -> apiCallLogDTO.addFieldToRequest(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Api call log request field is null.");
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testAddFieldToRequest_EmptyRequest() {
    ApiCallLogDTO apiCallLogDTO = ApiCallLogDTO.builder().accountId(accountId).build();
    ApiCallLogDTOField field = ApiCallLogDTOField.builder().name(name).value(value).build();
    apiCallLogDTO.addFieldToRequest(field);
    assertThat(apiCallLogDTO.getRequests()).hasSize(1);
    assertThat(apiCallLogDTO.getRequests().get(0).getName()).isEqualTo(name);
    assertThat(apiCallLogDTO.getRequests().get(0).getValue()).isEqualTo(value);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testAddFieldToResponse_NullField() {
    ApiCallLogDTO apiCallLogDTO = ApiCallLogDTO.builder().accountId(accountId).build();
    int statusCode = 1;
    ApiCallLogDTO.FieldType fieldType = ApiCallLogDTO.FieldType.JSON;
    assertThatThrownBy(() -> apiCallLogDTO.addFieldToResponse(statusCode, null, fieldType))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Api call log response field is null.");
  }
}
