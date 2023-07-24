/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans.cvnglog;

import static io.harness.cvng.beans.cvnglog.ApiCallLogDTO.FieldType.TEXT;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANSUMAN;
import static io.harness.rule.OwnerRule.KANHAIYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.cvnglog.ApiCallLogDTO.ApiCallLogDTOField;
import io.harness.rule.Owner;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
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
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testAddFieldToRequest_WithTextBody() {
    ApiCallLogDTO apiCallLogDTO = ApiCallLogDTO.builder().accountId(accountId).build();
    ApiCallLogDTOField field = ApiCallLogDTOField.builder().name(name).value(value).build();
    apiCallLogDTO.addFieldToRequest(field);
    RequestBody formBody = new FormBody.Builder().add("message", "YourMessage").build();
    Request request = new Request.Builder().url("https://www.example.com/").post(formBody).build();
    apiCallLogDTO.addCallDetailsBodyFieldToRequest(request);
    assertThat(apiCallLogDTO.getRequests()).hasSize(2);
    assertThat(apiCallLogDTO.getRequests().get(0).getName()).isEqualTo(name);
    assertThat(apiCallLogDTO.getRequests().get(0).getValue()).isEqualTo(value);
    assertThat(apiCallLogDTO.getRequests().get(1).getName()).isEqualTo("Request Body");
    assertThat(apiCallLogDTO.getRequests().get(1).getValue()).isEqualTo("message=YourMessage");
    assertThat(apiCallLogDTO.getRequests().get(1).getType()).isEqualTo(TEXT);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testAddFieldToRequest_WithJSONBody() {
    ApiCallLogDTO apiCallLogDTO = ApiCallLogDTO.builder().accountId(accountId).build();
    ApiCallLogDTOField field = ApiCallLogDTOField.builder().name(name).value(value).build();
    apiCallLogDTO.addFieldToRequest(field);
    MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    RequestBody body = RequestBody.create(JSON, "{\"jsonExample\":\"value\"}");
    Request request = new Request.Builder().url("https://www.example.com/").post(body).build();
    apiCallLogDTO.addCallDetailsBodyFieldToRequest(request);
    assertThat(apiCallLogDTO.getRequests()).hasSize(2);
    assertThat(apiCallLogDTO.getRequests().get(0).getName()).isEqualTo(name);
    assertThat(apiCallLogDTO.getRequests().get(0).getValue()).isEqualTo(value);
    assertThat(apiCallLogDTO.getRequests().get(1).getName()).isEqualTo("Request Body");
    assertThat(apiCallLogDTO.getRequests().get(1).getValue()).isEqualTo("{\"jsonExample\":\"value\"}");
    assertThat(apiCallLogDTO.getRequests().get(1).getType()).isEqualTo(ApiCallLogDTO.FieldType.JSON);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testAddFieldToRequest_WithJSONBodyWithEscapeCharacters() {
    ApiCallLogDTO apiCallLogDTO = ApiCallLogDTO.builder().accountId(accountId).build();
    ApiCallLogDTOField field = ApiCallLogDTOField.builder().name(name).value(value).build();
    apiCallLogDTO.addFieldToRequest(field);
    MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    RequestBody body = RequestBody.create(JSON, "{\"jsonExample\":\"\"valu\\e\"\"}");
    Request request = new Request.Builder().url("https://www.example.com/").post(body).build();
    apiCallLogDTO.addCallDetailsBodyFieldToRequest(request);
    assertThat(apiCallLogDTO.getRequests()).hasSize(2);
    assertThat(apiCallLogDTO.getRequests().get(0).getName()).isEqualTo(name);
    assertThat(apiCallLogDTO.getRequests().get(0).getValue()).isEqualTo(value);
    assertThat(apiCallLogDTO.getRequests().get(1).getName()).isEqualTo("Request Body");
    assertThat(apiCallLogDTO.getRequests().get(1).getValue()).isEqualTo("{\"jsonExample\":\"\"valu\\e\"\"}");
    assertThat(apiCallLogDTO.getRequests().get(1).getType()).isEqualTo(ApiCallLogDTO.FieldType.JSON);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testAddFieldToRequest_WithURLFormEncoded() {
    ApiCallLogDTO apiCallLogDTO = ApiCallLogDTO.builder().accountId(accountId).build();
    ApiCallLogDTOField field = ApiCallLogDTOField.builder().name(name).value(value).build();
    apiCallLogDTO.addFieldToRequest(field);
    MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");
    RequestBody body = RequestBody.create(mediaType,
        "search=search%20index%3D_internal%20%22%20error%20%22%20NOT%20debug%20source%3D*splunkd.log*&earliest_time=1688700000&latest_time=1688700300");
    Request request = new Request.Builder().url("https://www.example.com/").post(body).build();
    apiCallLogDTO.addCallDetailsBodyFieldToRequest(request);
    assertThat(apiCallLogDTO.getRequests()).hasSize(2);
    assertThat(apiCallLogDTO.getRequests().get(0).getName()).isEqualTo(name);
    assertThat(apiCallLogDTO.getRequests().get(0).getValue()).isEqualTo(value);
    assertThat(apiCallLogDTO.getRequests().get(1).getName()).isEqualTo("Request Body");
    assertThat(apiCallLogDTO.getRequests().get(1).getValue())
        .isEqualTo(
            "search=search index=_internal \" error \" NOT debug source=*splunkd.log*&earliest_time=1688700000&latest_time=1688700300");
    assertThat(apiCallLogDTO.getRequests().get(1).getType()).isEqualTo(ApiCallLogDTO.FieldType.TEXT);
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
