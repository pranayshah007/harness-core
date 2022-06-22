/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.HintException;
import io.harness.exception.ServiceNowException;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.serializer.JsonUtils;
import io.harness.servicenow.ServiceNowActionNG;
import io.harness.servicenow.ServiceNowFieldNG;
import io.harness.servicenow.ServiceNowTemplate;

import software.wings.helpers.ext.servicenow.ServiceNowRestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;

@OwnedBy(CDC)
@RunWith(PowerMockRunner.class)
@PrepareForTest({Retrofit.class, ServiceNowTaskNgHelper.class})
@PowerMockIgnore({"javax.net.ssl.*"})
public class ServiceNowTaskNGHelperTest extends CategoryTest {
  public static final String TICKET_NUMBER = "INC00001";
  public static final String TICKET_SYSID = "aacc24dcdb5f85509e7c2a59139619c4";
  public static final String TICKET_LINK =
      "incident.do?sys_id=aacc24dcdb5f85509e7c2a59139619c4&sysparm_stack=incident_list.do?sysparm_query=active=true";
  private static final String TEMPLATE_NAME = "test_incident_template";

  @Mock private SecretDecryptionService secretDecryptionService;
  @InjectMocks private ServiceNowTaskNgHelper serviceNowTaskNgHelper;

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldValidateCredentials() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);
    Retrofit retrofit = Mockito.mock(Retrofit.class);
    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.validateConnection(anyString())).thenReturn(mockCall);
    Response<JsonNode> jsonNodeResponse = Response.success(null);
    when(mockCall.execute()).thenReturn(jsonNodeResponse);
    PowerMockito.whenNew(Retrofit.class).withAnyArguments().thenReturn(retrofit);
    PowerMockito.when(retrofit.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient);

    ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
    ServiceNowTaskNGResponse response =
        serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                         .action(ServiceNowActionNG.VALIDATE_CREDENTIALS)
                                                         .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                         .build());
    assertThat(response.getDelegateMetaInfo()).isNull();
    verify(secretDecryptionService).decrypt(any(), any());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldValidateCredentialsFailure() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);
    Retrofit retrofit = Mockito.mock(Retrofit.class);
    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.validateConnection(anyString())).thenReturn(mockCall);
    Response<JsonNode> jsonNodeResponse = Response.error(401, ResponseBody.create(MediaType.parse("JSON"), ""));
    when(mockCall.execute()).thenReturn(jsonNodeResponse);
    PowerMockito.whenNew(Retrofit.class).withAnyArguments().thenReturn(retrofit);
    PowerMockito.when(retrofit.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient);

    ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
    assertThatThrownBy(
        ()
            -> serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                                .action(ServiceNowActionNG.VALIDATE_CREDENTIALS)
                                                                .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                                .build()))
        .isInstanceOf(HintException.class)
        .hasMessage(
            "Check if the ServiceNow credentials are correct and you have necessary permissions to access the incident table");
    verify(secretDecryptionService).decrypt(any(), any());
  }

  private ServiceNowConnectorDTO getServiceNowConnector() {
    return ServiceNowConnectorDTO.builder()
        .serviceNowUrl("https://harness.service-now.com/")
        .username("username")
        .passwordRef(SecretRefData.builder().decryptedValue(new char[] {'3', '4', 'f', '5', '1'}).build())
        .build();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldGetIssueCreateMetdata() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);
    Retrofit retrofit = Mockito.mock(Retrofit.class);
    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getAdditionalFields(anyString(), anyString())).thenReturn(mockCall);
    List<Map<String, String>> responseMap = Arrays.asList(
        ImmutableMap.of("label", "field1", "name", "value1"), ImmutableMap.of("label", "field2", "name", "value2"));
    JsonNode successResponse = JsonUtils.asTree(Collections.singletonMap("result", responseMap));
    Response<JsonNode> jsonNodeResponse = Response.success(successResponse);
    when(mockCall.execute()).thenReturn(jsonNodeResponse);
    PowerMockito.whenNew(Retrofit.class).withAnyArguments().thenReturn(retrofit);
    PowerMockito.when(retrofit.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient);

    ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
    ServiceNowTaskNGResponse response =
        serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                         .action(ServiceNowActionNG.GET_TICKET_CREATE_METADATA)
                                                         .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                         .ticketType("incident")
                                                         .build());
    assertThat(response.getDelegateMetaInfo()).isNull();
    assertThat(response.getServiceNowFieldNGList()).hasSize(2);
    assertThat(
        response.getServiceNowFieldNGList().stream().map(ServiceNowFieldNG::getName).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("field1", "field2");
    assertThat(response.getServiceNowFieldNGList().stream().map(ServiceNowFieldNG::getKey).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("value1", "value2");
    verify(secretDecryptionService).decrypt(any(), any());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldGetTicket() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);
    Retrofit retrofit = Mockito.mock(Retrofit.class);
    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getIssue(anyString(), anyString(), anyString(), anyString())).thenReturn(mockCall);
    Map<String, Map<String, String>> responseMap =
        ImmutableMap.of("field1", ImmutableMap.of("value", "BEvalue1", "display_value", "UIvalue1"), "field2",
            ImmutableMap.of("value", "BEvalue2", "display_value", "UIvalue2"));
    JsonNode successResponse =
        JsonUtils.asTree(Collections.singletonMap("result", Collections.singletonList(responseMap)));
    Response<JsonNode> jsonNodeResponse = Response.success(successResponse);
    when(mockCall.execute()).thenReturn(jsonNodeResponse);
    PowerMockito.whenNew(Retrofit.class).withAnyArguments().thenReturn(retrofit);
    PowerMockito.when(retrofit.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient);

    ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
    ServiceNowTaskNGResponse response =
        serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                         .action(ServiceNowActionNG.GET_TICKET)
                                                         .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                         .ticketType("incident")
                                                         .ticketNumber(TICKET_NUMBER)
                                                         .build());

    assertThat(response.getDelegateMetaInfo()).isNull();
    assertThat(response.getTicket().getNumber()).isEqualTo(TICKET_NUMBER);
    assertThat(response.getTicket().getUrl())
        .isEqualTo("https://harness.service-now.com/nav_to.do?uri=/incident.do?sysparm_query=number=INC00001");
    assertThat(response.getTicket().getFields()).hasSize(2);
    assertThat(response.getTicket().getFields().get("field1").getValue()).isEqualTo("BEvalue1");
    assertThat(response.getTicket().getFields().get("field1").getDisplayValue()).isEqualTo("UIvalue1");
    assertThat(response.getTicket().getFields().get("field2").getValue()).isEqualTo("BEvalue2");
    assertThat(response.getTicket().getFields().get("field2").getDisplayValue()).isEqualTo("UIvalue2");
    verify(secretDecryptionService).decrypt(any(), any());

    verify(serviceNowRestClient).getIssue(anyString(), anyString(), eq("number=" + TICKET_NUMBER), eq("all"));
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testApplyServiceNowTemplateToCreateTicket() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);
    Retrofit retrofit = Mockito.mock(Retrofit.class);
    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.createUsingTemplate(anyString(), anyString(), anyString())).thenReturn(mockCall);
    ImmutableMap<String, String> responseMap = ImmutableMap.of("record_sys_id", "aacc24dcdb5f85509e7c2a59139619c4",
        "record_link",
        "incident.do?sys_id=aacc24dcdb5f85509e7c2a59139619c4&sysparm_stack=incident_list.do?sysparm_query=active=true",
        "record_number", TICKET_NUMBER);

    JsonNode successResponse = JsonUtils.asTree(Collections.singletonMap("result", responseMap));
    Response<JsonNode> jsonNodeResponse = Response.success(successResponse);
    when(mockCall.execute()).thenReturn(jsonNodeResponse);

    Call mockFetchIssueCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getIssue(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(mockFetchIssueCall);
    Map<String, Map<String, String>> getIssueResponseMap =
        ImmutableMap.of("parent", ImmutableMap.of("value", "", "display_value", ""), "description",
            ImmutableMap.of("value", "BEvalue2", "display_value", "UIvalue2"), "number",
            ImmutableMap.of("value", "INC00001", "display_value", "INC00001"));

    JsonNode fetchIssueResponse =
        JsonUtils.asTree(Collections.singletonMap("result", Collections.singletonList(getIssueResponseMap)));
    Response<JsonNode> jsonNodeFetchIssueResponse = Response.success(fetchIssueResponse);
    when(mockFetchIssueCall.execute()).thenReturn(jsonNodeFetchIssueResponse);
    PowerMockito.whenNew(Retrofit.class).withAnyArguments().thenReturn(retrofit);
    PowerMockito.when(retrofit.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient);

    ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
    ServiceNowTaskNGResponse response =
        serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                         .action(ServiceNowActionNG.CREATE_TICKET)
                                                         .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                         .templateName(TEMPLATE_NAME)
                                                         .useServiceNowTemplate(true)
                                                         .ticketType("incident")
                                                         .build());

    assertThat(response.getDelegateMetaInfo()).isNull();

    // ServiceNow Outcome
    assertThat(response.getTicket().getNumber()).isEqualTo(TICKET_NUMBER);
    assertThat(response.getTicket().getUrl())
        .isEqualTo("https://harness.service-now.com/nav_to.do?uri=/incident.do?sysparm_query=number=INC00001");
    assertThat(response.getTicket().getFields()).hasSize(2);
    verify(secretDecryptionService).decrypt(any(), any());
    verify(serviceNowRestClient).createUsingTemplate(anyString(), eq("incident"), eq(TEMPLATE_NAME));
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetMetadataWithChoices() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);
    Retrofit retrofit = Mockito.mock(Retrofit.class);
    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getMetadata(anyString(), anyString())).thenReturn(mockCall);

    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL jsonFile = classLoader.getResource("servicenow/serviceNowMetadataResponse.json");
    ObjectMapper mapper = new ObjectMapper();
    JsonNode responseNode = mapper.readTree(jsonFile);

    Response<JsonNode> jsonNodeResponse = Response.success(responseNode);
    when(mockCall.execute()).thenReturn(jsonNodeResponse);
    PowerMockito.whenNew(Retrofit.class).withAnyArguments().thenReturn(retrofit);
    PowerMockito.when(retrofit.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient);

    ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
    ServiceNowTaskNGResponse response =
        serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                         .action(ServiceNowActionNG.GET_METADATA)
                                                         .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                         .ticketType("incident")
                                                         .build());

    assertThat(response.getDelegateMetaInfo()).isNull();
    assertThat(response.getServiceNowFieldNGList()).hasSize(2);
    assertThat(response.getServiceNowFieldNGList().get(0).getKey()).isEqualTo("parent");
    assertThat(response.getServiceNowFieldNGList().get(0).getName()).isEqualTo("Parent");

    // choice based fields
    assertThat(response.getServiceNowFieldNGList().get(1).getKey()).isEqualTo("priority");
    assertThat(response.getServiceNowFieldNGList().get(1).getName()).isEqualTo("Priority");
    assertThat(response.getServiceNowFieldNGList().get(1).getAllowedValues()).hasSize(6);
    assertThat(response.getServiceNowFieldNGList().get(1).getSchema().isArray()).isTrue();

    assertThat(response.getTicket()).isNull();
    verify(secretDecryptionService).decrypt(any(), any());

    verify(serviceNowRestClient).getMetadata(anyString(), eq("incident"));
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testApplyServiceNowTemplateToUpdateTicket() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);
    Retrofit retrofit = Mockito.mock(Retrofit.class);
    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.updateUsingTemplate(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(mockCall);
    ImmutableMap<String, String> responseMap = ImmutableMap.of("record_sys_id", "aacc24dcdb5f85509e7c2a59139619c4",
        "record_link",
        "incident.do?sys_id=aacc24dcdb5f85509e7c2a59139619c4&sysparm_stack=incident_list.do?sysparm_query=active=true",
        "record_number", TICKET_NUMBER);

    JsonNode successResponse = JsonUtils.asTree(Collections.singletonMap("result", responseMap));
    Response<JsonNode> jsonNodeResponse = Response.success(successResponse);
    when(mockCall.execute()).thenReturn(jsonNodeResponse);

    Call mockFetchIssueCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getIssue(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(mockFetchIssueCall);
    Map<String, Map<String, String>> getIssueResponseMap =
        ImmutableMap.of("parent", ImmutableMap.of("value", "", "display_value", ""), "description",
            ImmutableMap.of("value", "BEvalue2", "display_value", "UIvalue2"), "number",
            ImmutableMap.of("value", "INC00001", "display_value", "INC00001"));

    JsonNode fetchIssueResponse =
        JsonUtils.asTree(Collections.singletonMap("result", Collections.singletonList(getIssueResponseMap)));
    Response<JsonNode> jsonNodeFetchIssueResponse = Response.success(fetchIssueResponse);
    when(mockFetchIssueCall.execute()).thenReturn(jsonNodeFetchIssueResponse);

    PowerMockito.whenNew(Retrofit.class).withAnyArguments().thenReturn(retrofit);
    PowerMockito.when(retrofit.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient);

    ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
    ServiceNowTaskNGResponse response =
        serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                         .action(ServiceNowActionNG.UPDATE_TICKET)
                                                         .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                         .templateName(TEMPLATE_NAME)
                                                         .useServiceNowTemplate(true)
                                                         .ticketType("incident")
                                                         .ticketNumber(TICKET_NUMBER)
                                                         .build());

    assertThat(response.getDelegateMetaInfo()).isNull();

    // ServiceNow Outcome
    assertThat(response.getTicket().getNumber()).isEqualTo(TICKET_NUMBER);
    assertThat(response.getTicket().getUrl())
        .isEqualTo("https://harness.service-now.com/nav_to.do?uri=/incident.do?sysparm_query=number=INC00001");
    assertThat(response.getTicket().getFields()).hasSize(2);
    verify(secretDecryptionService).decrypt(any(), any());
    verify(serviceNowRestClient).updateUsingTemplate(anyString(), eq("incident"), eq(TEMPLATE_NAME), eq(TICKET_NUMBER));
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetTemplateList() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);
    Retrofit retrofit = Mockito.mock(Retrofit.class);
    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getTemplateList(anyString(), anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(mockCall);

    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL jsonFile = classLoader.getResource("servicenow/serviceNowTemplateResponse.json");
    ObjectMapper mapper = new ObjectMapper();
    JsonNode responseNode = mapper.readTree(jsonFile);

    Response<JsonNode> jsonNodeResponse = Response.success(responseNode);
    when(mockCall.execute()).thenReturn(jsonNodeResponse);
    PowerMockito.whenNew(Retrofit.class).withAnyArguments().thenReturn(retrofit);
    PowerMockito.when(retrofit.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient);

    ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
    ServiceNowTaskNGResponse response =
        serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                         .action(ServiceNowActionNG.GET_TEMPLATE)
                                                         .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                         .ticketType("incident")
                                                         .templateListLimit(1)
                                                         .templateListOffset(0)
                                                         .templateName(TEMPLATE_NAME)
                                                         .build());

    assertThat(response.getDelegateMetaInfo()).isNull();
    assertThat(response.getServiceNowTemplateList()).hasSize(1);

    // template fields
    ServiceNowTemplate serviceNowTemplate = response.getServiceNowTemplateList().get(0);
    assertThat(serviceNowTemplate.getName()).isEqualTo(TEMPLATE_NAME);
    assertThat(serviceNowTemplate.getFields()).hasSize(5);
    assertThat(serviceNowTemplate.getFields().get("Impact").getDisplayValue()).isEqualTo("1 - High");

    verify(secretDecryptionService).decrypt(any(), any());

    verify(serviceNowRestClient).getTemplateList(anyString(), eq("incident"), anyInt(), anyInt(), anyString());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void responseTest() throws IOException {
    Response<JsonNode> jsonNodeResponse = Response.success(200, null);

    serviceNowTaskNgHelper.handleResponse(jsonNodeResponse, "Success Message");
    ResponseBody body = mock(ResponseBody.class);
    Response<JsonNode> jsonNodeResponse1 = Response.error(401, body);
    assertThatThrownBy(() -> serviceNowTaskNgHelper.handleResponse(jsonNodeResponse1, any()))
        .isInstanceOf(ServiceNowException.class);

    Response<JsonNode> jsonNodeResponse2 = Response.error(404, body);
    assertThatThrownBy(() -> serviceNowTaskNgHelper.handleResponse(jsonNodeResponse2, any()))
        .isInstanceOf(ServiceNowException.class);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testApplyServiceNowWithoutTemplateToCreateTicket() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);
    Retrofit retrofit = Mockito.mock(Retrofit.class);
    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.createTicket(anyString(), anyString(), anyString(), isNull(), anyMap()))
        .thenReturn(mockCall);

    ImmutableMap<String, JsonNode> responsemap =
        ImmutableMap.of("number", JsonUtils.asTree(Collections.singletonMap("display_value", TICKET_NUMBER)));

    JsonNode successResponse = JsonUtils.asTree(Collections.singletonMap("result", responsemap));
    Response<JsonNode> jsonNodeResponse = Response.success(successResponse);
    when(mockCall.execute()).thenReturn(jsonNodeResponse);

    Call mockFetchIssueCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getIssue(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(mockFetchIssueCall);
    Map<String, Map<String, String>> getIssueResponseMap =
        ImmutableMap.of("parent", ImmutableMap.of("value", "", "display_value", ""), "description",
            ImmutableMap.of("value", "BEvalue2", "display_value", "UIvalue2"), "number",
            ImmutableMap.of("value", "INC00001", "display_value", "INC00001"));

    JsonNode fetchIssueResponse =
        JsonUtils.asTree(Collections.singletonMap("result", Collections.singletonList(getIssueResponseMap)));
    Response<JsonNode> jsonNodeFetchIssueResponse = Response.success(fetchIssueResponse);
    when(mockFetchIssueCall.execute()).thenReturn(jsonNodeFetchIssueResponse);
    PowerMockito.whenNew(Retrofit.class).withAnyArguments().thenReturn(retrofit);
    PowerMockito.when(retrofit.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient);
    Map<String, String> fieldmap = ImmutableMap.of("value", "BEvalue2", "display_value", "UIvalue2");
    ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
    ServiceNowTaskNGResponse response =
        serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                         .action(ServiceNowActionNG.CREATE_TICKET)
                                                         .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                         .templateName(TEMPLATE_NAME)
                                                         .useServiceNowTemplate(false)
                                                         .ticketType("incident")
                                                         .fields(fieldmap)
                                                         .build());

    assertThat(response.getDelegateMetaInfo()).isNull();

    // ServiceNow Outcome
    assertThat(response.getTicket().getNumber()).isEqualTo(TICKET_NUMBER);
    assertThat(response.getTicket().getUrl())
        .isEqualTo("https://harness.service-now.com/nav_to.do?uri=/incident.do?sysparm_query=number=INC00001");
    assertThat(response.getTicket().getFields()).hasSize(1);
    verify(secretDecryptionService).decrypt(any(), any());
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testUpdateServiceNowWithoutTemplateToCreateTicket() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);
    Retrofit retrofit = Mockito.mock(Retrofit.class);
    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.updateTicket(anyString(), anyString(), anyString(), anyString(), isNull(), anyMap()))
        .thenReturn(mockCall);

    ImmutableMap<String, JsonNode> responsemap =
        ImmutableMap.of("number", JsonUtils.asTree(Collections.singletonMap("display_value", TICKET_NUMBER)));

    JsonNode successResponse = JsonUtils.asTree(Collections.singletonMap("result", responsemap));
    Response<JsonNode> jsonNodeResponse = Response.success(successResponse);
    when(mockCall.execute()).thenReturn(jsonNodeResponse);

    Call mockFetchIssueCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getIssue(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(mockFetchIssueCall);
    Map<String, Map<String, String>> getIssueResponseMap =
        ImmutableMap.of("parent", ImmutableMap.of("value", "", "display_value", ""), "sys_id",
            ImmutableMap.of("value", "BEvalue2", "display_value", "UIvalue2"), "number",
            ImmutableMap.of("value", "INC00001", "display_value", "INC00001"));

    JsonNode fetchIssueResponse =
        JsonUtils.asTree(Collections.singletonMap("result", Collections.singletonList(getIssueResponseMap)));
    Response<JsonNode> jsonNodeFetchIssueResponse = Response.success(fetchIssueResponse);
    when(mockFetchIssueCall.execute()).thenReturn(jsonNodeFetchIssueResponse);
    PowerMockito.whenNew(Retrofit.class).withAnyArguments().thenReturn(retrofit);
    PowerMockito.when(retrofit.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient);
    Map<String, String> fieldmap = ImmutableMap.of("value", "BEvalue2", "display_value", "UIvalue2");
    ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
    ServiceNowTaskNGResponse response =
        serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                         .action(ServiceNowActionNG.UPDATE_TICKET)
                                                         .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                         .templateName(TEMPLATE_NAME)
                                                         .useServiceNowTemplate(false)
                                                         .ticketType("incident")
                                                         .fields(fieldmap)
                                                         .ticketNumber(TICKET_NUMBER)
                                                         .build());

    assertThat(response.getDelegateMetaInfo()).isNull();

    // ServiceNow Outcome
    assertThat(response.getTicket().getNumber()).isEqualTo(TICKET_NUMBER);
    assertThat(response.getTicket().getUrl())
        .isEqualTo("https://harness.service-now.com/nav_to.do?uri=/incident.do?sysparm_query=number=INC00001");
    assertThat(response.getTicket().getFields()).hasSize(1);
    verify(secretDecryptionService).decrypt(any(), any());
  }
}
