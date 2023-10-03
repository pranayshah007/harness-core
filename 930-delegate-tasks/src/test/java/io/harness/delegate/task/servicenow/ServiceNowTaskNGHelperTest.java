/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.beans.connector.servicenow.ServiceNowConstants.INVALID_SERVICE_NOW_CREDENTIALS;
import static io.harness.delegate.beans.connector.servicenow.ServiceNowConstants.NOT_FOUND;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.RAFAEL;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthType;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthenticationDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowUserNamePasswordDTO;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.encryption.SecretRefData;
import io.harness.eraro.ErrorCode;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ServiceNowException;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.serializer.JsonUtils;
import io.harness.servicenow.ChangeTaskUpdateMultiple;
import io.harness.servicenow.ServiceNowActionNG;
import io.harness.servicenow.ServiceNowFieldNG;
import io.harness.servicenow.ServiceNowImportSetResponseNG;
import io.harness.servicenow.ServiceNowStagingTable;
import io.harness.servicenow.ServiceNowTemplate;
import io.harness.servicenow.ServiceNowTicketTypeDTO;
import io.harness.servicenow.ServiceNowUpdateMultipleTaskNode;

import software.wings.helpers.ext.servicenow.ServiceNowRestClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;

@OwnedBy(CDC)
@RunWith(MockitoJUnitRunner.class)
public class ServiceNowTaskNGHelperTest extends CategoryTest {
  public static final String TICKET_NUMBER = "INC00001";
  public static final String TICKET_SYSID = "aacc24dcdb5f85509e7c2a59139619c4";
  public static final String TICKET_LINK =
      "incident.do?sys_id=aacc24dcdb5f85509e7c2a59139619c4&sysparm_stack=incident_list.do?sysparm_query=active=true";
  private static final String TEMPLATE_NAME = "test_incident_template";
  private static final String CHANGE_REQUEST_TEMPLATE_NAME = "standard_change_template";

  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock private ILogStreamingTaskClient logStreamingTaskClient;
  @InjectMocks private ServiceNowTaskNgHelper serviceNowTaskNgHelper;

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldValidateCredentialsWithRetry() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);

    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.validateConnection(anyString())).thenReturn(mockCall);
    Response<JsonNode> jsonNodeResponse = Response.success(null);
    when(mockCall.clone()).thenReturn(mockCall);
    doThrow(new SocketTimeoutException()).doReturn(jsonNodeResponse).when(mockCall).execute();
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
      ServiceNowTaskNGResponse response =
          serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                           .action(ServiceNowActionNG.VALIDATE_CREDENTIALS)
                                                           .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                           .build(),
              null);
      assertThat(response.getDelegateMetaInfo()).isNull();
      verify(secretDecryptionService).decrypt(any(), any());
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void shouldValidateCredentialsWithRetryWhenCachedTokenGives401() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);

    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.validateConnection(anyString())).thenReturn(mockCall);
    Response<JsonNode> jsonNodeUnAuthResponse = Response.error(401, ResponseBody.create("", MediaType.parse("JSON")));
    Response<JsonNode> jsonNodeResponse = Response.success(null);
    when(mockCall.clone()).thenReturn(mockCall);
    doReturn(jsonNodeUnAuthResponse)
        .doThrow(new SocketTimeoutException())
        .doReturn(jsonNodeResponse)
        .when(mockCall)
        .execute();
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
      ServiceNowTaskNGResponse response =
          serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                           .action(ServiceNowActionNG.VALIDATE_CREDENTIALS)
                                                           .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                           .build(),
              null);
      assertThat(response.getDelegateMetaInfo()).isNull();
      verify(secretDecryptionService).decrypt(any(), any());
    }
    verify(serviceNowRestClient, times(2)).validateConnection(anyString());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldValidateCredentialsFailure() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);

    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.validateConnection(anyString())).thenReturn(mockCall);
    Response<JsonNode> jsonNodeResponse = Response.error(401, ResponseBody.create(MediaType.parse("JSON"), ""));
    when(mockCall.execute()).thenReturn(jsonNodeResponse);
    when(mockCall.clone()).thenReturn(mockCall);
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient))) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
      assertThatThrownBy(
          ()
              -> serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                                  .action(ServiceNowActionNG.VALIDATE_CREDENTIALS)
                                                                  .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                                  .build(),
                  null))
          .isInstanceOf(HintException.class)
          .hasMessage(
              "Check if the ServiceNow credentials are correct and you have necessary permissions to access the incident table");
      verify(secretDecryptionService).decrypt(any(), any());
    }
  }

  private ServiceNowConnectorDTO getServiceNowConnector() {
    return ServiceNowConnectorDTO.builder()
        .serviceNowUrl("https://harness.service-now.com/")
        .auth(ServiceNowAuthenticationDTO.builder()
                  .authType(ServiceNowAuthType.USER_PASSWORD)
                  .credentials(
                      ServiceNowUserNamePasswordDTO.builder()
                          .username("username")
                          .passwordRef(
                              SecretRefData.builder().decryptedValue(new char[] {'3', '4', 'f', '5', '1'}).build())
                          .build())
                  .build())
        .build();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldGetIssueCreateMetdataWithRetry() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);

    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getAdditionalFields(anyString(), anyString())).thenReturn(mockCall);
    List<Map<String, String>> responseMap =
        Arrays.asList(ImmutableMap.of("label", "field1", "name", "value1", "internalType", "boolean"),
            ImmutableMap.of("label", "field2", "name", "value2", "internalType", "string"));
    JsonNode successResponse = JsonUtils.asTree(Collections.singletonMap("result", responseMap));
    Response<JsonNode> jsonNodeResponse = Response.success(successResponse);
    when(mockCall.clone()).thenReturn(mockCall);
    doThrow(new SocketTimeoutException()).doReturn(jsonNodeResponse).when(mockCall).execute();
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
      ServiceNowTaskNGResponse response =
          serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                           .action(ServiceNowActionNG.GET_TICKET_CREATE_METADATA)
                                                           .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                           .ticketType("incident")
                                                           .build(),
              logStreamingTaskClient);
      assertThat(response.getDelegateMetaInfo()).isNull();
      assertThat(response.getServiceNowFieldNGList()).hasSize(2);
      assertThat(
          response.getServiceNowFieldNGList().stream().map(ServiceNowFieldNG::getName).collect(Collectors.toList()))
          .containsExactlyInAnyOrder("field1", "field2");
      assertThat(
          response.getServiceNowFieldNGList().stream().map(ServiceNowFieldNG::getKey).collect(Collectors.toList()))
          .containsExactlyInAnyOrder("value1", "value2");
      assertThat(response.getServiceNowFieldNGList()
                     .stream()
                     .map(ServiceNowFieldNG::getInternalType)
                     .collect(Collectors.toList()))
          .containsExactlyInAnyOrder("string", "boolean");
      verify(secretDecryptionService).decrypt(any(), any());
    }
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldGetTicketWithRetry() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);

    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getIssueV2(anyString(), anyString(), anyString(), anyString(), eq(null)))
        .thenReturn(mockCall);
    Map<String, Map<String, String>> responseMap =
        ImmutableMap.of("field1", ImmutableMap.of("value", "BEvalue1", "display_value", "UIvalue1"), "field2",
            ImmutableMap.of("value", "BEvalue2", "display_value", "UIvalue2"));
    JsonNode successResponse =
        JsonUtils.asTree(Collections.singletonMap("result", Collections.singletonList(responseMap)));
    Response<JsonNode> jsonNodeResponse = Response.success(successResponse);
    when(mockCall.clone()).thenReturn(mockCall);
    doThrow(new SocketTimeoutException()).doReturn(jsonNodeResponse).when(mockCall).execute();
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
      ServiceNowTaskNGResponse response =
          serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                           .action(ServiceNowActionNG.GET_TICKET)
                                                           .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                           .ticketType("incident")
                                                           .ticketNumber(TICKET_NUMBER)
                                                           .build(),
              logStreamingTaskClient);

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

      verify(serviceNowRestClient)
          .getIssueV2(anyString(), anyString(), eq("number=" + TICKET_NUMBER), eq("all"), eq(null));
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void shouldGetTicketWithRetryWhenCachedTokenGives401() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);

    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getIssueV2(anyString(), anyString(), anyString(), anyString(), eq(null)))
        .thenReturn(mockCall);
    Map<String, Map<String, String>> responseMap =
        ImmutableMap.of("field1", ImmutableMap.of("value", "BEvalue1", "display_value", "UIvalue1"), "field2",
            ImmutableMap.of("value", "BEvalue2", "display_value", "UIvalue2"));
    JsonNode successResponse =
        JsonUtils.asTree(Collections.singletonMap("result", Collections.singletonList(responseMap)));
    Response<JsonNode> jsonNodeUnAuthResponse = Response.error(401, ResponseBody.create("", MediaType.parse("JSON")));
    Response<JsonNode> jsonNodeResponse = Response.success(successResponse);
    when(mockCall.clone()).thenReturn(mockCall);
    doReturn(jsonNodeUnAuthResponse)
        .doThrow(new SocketTimeoutException())
        .doReturn(jsonNodeResponse)
        .when(mockCall)
        .execute();
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
      ServiceNowTaskNGResponse response =
          serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                           .action(ServiceNowActionNG.GET_TICKET)
                                                           .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                           .ticketType("incident")
                                                           .ticketNumber(TICKET_NUMBER)
                                                           .build(),
              logStreamingTaskClient);

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

      verify(serviceNowRestClient, times(2))
          .getIssueV2(anyString(), anyString(), eq("number=" + TICKET_NUMBER), eq("all"), eq(null));
    }
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testApplyServiceNowTemplateToCreateTicket() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);

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
    when(mockFetchIssueCall.clone()).thenReturn(mockFetchIssueCall);
    doThrow(new SocketTimeoutException()).doReturn(jsonNodeFetchIssueResponse).when(mockFetchIssueCall).execute();
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
      ServiceNowTaskNGResponse response =
          serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                           .action(ServiceNowActionNG.CREATE_TICKET)
                                                           .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                           .templateName(TEMPLATE_NAME)
                                                           .useServiceNowTemplate(true)
                                                           .ticketType("incident")
                                                           .build(),
              logStreamingTaskClient);

      assertThat(response.getDelegateMetaInfo()).isNull();

      // ServiceNow Outcome
      assertThat(response.getTicket().getNumber()).isEqualTo(TICKET_NUMBER);
      assertThat(response.getTicket().getUrl())
          .isEqualTo(
              "https://harness.service-now.com/nav_to.do?uri=/incident.do?sys_id=aacc24dcdb5f85509e7c2a59139619c4");
      assertThat(response.getTicket().getFields()).hasSize(2);
      verify(secretDecryptionService).decrypt(any(), any());
      verify(serviceNowRestClient).createUsingTemplate(anyString(), eq("incident"), eq(TEMPLATE_NAME));
    }
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetMetadataWithChoicesWithRetry() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);
    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getMetadata(anyString(), anyString())).thenReturn(mockCall);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL jsonFile = classLoader.getResource("servicenow/serviceNowMetadataResponse.json");
    ObjectMapper mapper = new ObjectMapper();
    JsonNode responseNode = mapper.readTree(jsonFile);
    Response<JsonNode> jsonNodeResponse = Response.success(responseNode);
    when(mockCall.clone()).thenReturn(mockCall);
    doThrow(new SocketTimeoutException()).doReturn(jsonNodeResponse).when(mockCall).execute();
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
      ServiceNowTaskNGResponse response =
          serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                           .action(ServiceNowActionNG.GET_METADATA)
                                                           .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                           .ticketType("incident")
                                                           .build(),
              logStreamingTaskClient);

      assertThat(response.getDelegateMetaInfo()).isNull();
      assertThat(response.getServiceNowFieldNGList()).hasSize(8);
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
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetMetadataV2WithChoicesWithRetry() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);

    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getMetadata(anyString(), anyString())).thenReturn(mockCall);

    ClassLoader classLoader = this.getClass().getClassLoader();

    when(mockCall.clone()).thenReturn(mockCall);
    doThrow(new SocketTimeoutException())
        .doReturn(getJsonNodeResponseFromJsonFile("servicenow/serviceNowMetadataResponse.json", classLoader))
        .when(mockCall)
        .execute();
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
      ServiceNowTaskNGResponse response =
          serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                           .action(ServiceNowActionNG.GET_METADATA_V2)
                                                           .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                           .ticketType("incident")
                                                           .build(),
              logStreamingTaskClient);

      assertThat(response.getDelegateMetaInfo()).isNull();
      assertThat(response.getServiceNowFieldNGList()).isNull();
      assertThat(response.getTicket()).isNull();
      assertThat(response.getServiceNowFieldJsonNGListAsString()).isNotBlank();

      String serviceNowFieldJsonNGList = response.getServiceNowFieldJsonNGListAsString();
      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode jsonNodeList = objectMapper.readTree(serviceNowFieldJsonNGList);
      List<JsonNode> jsonNodeParsed = new ArrayList<>();
      for (JsonNode jsonNode : jsonNodeList) {
        jsonNodeParsed.add(jsonNode);
      }
      assertThat(jsonNodeParsed.size()).isEqualTo(8);
      when(mockCall.execute())
          .thenReturn(getJsonNodeResponseFromJsonFile("servicenow/serviceNowMetadataBadResponse1.json", classLoader));
      assertThatThrownBy(
          ()
              -> serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                                  .action(ServiceNowActionNG.GET_METADATA_V2)
                                                                  .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                                  .ticketType("incident")
                                                                  .build(),
                  logStreamingTaskClient))
          .isInstanceOf(ServiceNowException.class);
      when(mockCall.execute())
          .thenReturn(getJsonNodeResponseFromJsonFile("servicenow/serviceNowMetadataBadResponse2.json", classLoader));
      assertThatThrownBy(
          ()
              -> serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                                  .action(ServiceNowActionNG.GET_METADATA_V2)
                                                                  .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                                  .ticketType("incident")
                                                                  .build(),
                  logStreamingTaskClient))
          .isInstanceOf(ServiceNowException.class);
      verify(secretDecryptionService, times(3)).decrypt(any(), any());

      verify(serviceNowRestClient, times(3)).getMetadata(anyString(), eq("incident"));
    }
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testApplyServiceNowTemplateToUpdateTicketWithRetry() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);

    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.updateUsingTemplate(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(mockCall);
    ImmutableMap<String, String> responseMap = ImmutableMap.of("record_sys_id", "aacc24dcdb5f85509e7c2a59139619c4",
        "record_link",
        "incident.do?sys_id=aacc24dcdb5f85509e7c2a59139619c4&sysparm_stack=incident_list.do?sysparm_query=active=true",
        "record_number", TICKET_NUMBER);

    JsonNode successResponse = JsonUtils.asTree(Collections.singletonMap("result", responseMap));
    Response<JsonNode> jsonNodeResponse = Response.success(successResponse);
    when(mockCall.clone()).thenReturn(mockCall);
    doThrow(new SocketTimeoutException()).doReturn(jsonNodeResponse).when(mockCall).execute();

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
    when(mockFetchIssueCall.clone()).thenReturn(mockFetchIssueCall);
    doThrow(new SocketTimeoutException()).doReturn(jsonNodeFetchIssueResponse).when(mockFetchIssueCall).execute();

    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
      ServiceNowTaskNGResponse response =
          serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                           .action(ServiceNowActionNG.UPDATE_TICKET)
                                                           .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                           .templateName(TEMPLATE_NAME)
                                                           .useServiceNowTemplate(true)
                                                           .ticketType("incident")
                                                           .ticketNumber(TICKET_NUMBER)
                                                           .build(),
              logStreamingTaskClient);

      assertThat(response.getDelegateMetaInfo()).isNull();

      // ServiceNow Outcome
      assertThat(response.getTicket().getNumber()).isEqualTo(TICKET_NUMBER);
      assertThat(response.getTicket().getUrl())
          .isEqualTo(
              "https://harness.service-now.com/nav_to.do?uri=/incident.do?sys_id=aacc24dcdb5f85509e7c2a59139619c4");
      assertThat(response.getTicket().getFields()).hasSize(2);
      verify(secretDecryptionService).decrypt(any(), any());
      verify(serviceNowRestClient)
          .updateUsingTemplate(anyString(), eq("incident"), eq(TEMPLATE_NAME), eq(TICKET_NUMBER));
    }
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetTemplateListWithRetry() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);

    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getTemplateList(anyString(), anyString(), anyInt(), eq(100), anyString(), anyString()))
        .thenReturn(mockCall);

    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL jsonFile = classLoader.getResource("servicenow/serviceNowTemplateResponse.json");
    ObjectMapper mapper = new ObjectMapper();
    JsonNode responseNode = mapper.readTree(jsonFile);

    Response<JsonNode> jsonNodeResponse = Response.success(responseNode);
    when(mockCall.clone()).thenReturn(mockCall);
    doThrow(new SocketTimeoutException()).doReturn(jsonNodeResponse).when(mockCall).execute();
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
      ServiceNowTaskNGResponse response =
          serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                           .action(ServiceNowActionNG.GET_TEMPLATE)
                                                           .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                           .ticketType("incident")
                                                           .templateListLimit(50)
                                                           .templateListOffset(100)
                                                           .templateName(TEMPLATE_NAME)
                                                           .searchTerm("filter")
                                                           .build(),
              logStreamingTaskClient);

      assertThat(response.getDelegateMetaInfo()).isNull();
      assertThat(response.getServiceNowTemplateList()).hasSize(1);

      // template fields
      ServiceNowTemplate serviceNowTemplate = response.getServiceNowTemplateList().get(0);
      assertThat(serviceNowTemplate.getName()).isEqualTo(TEMPLATE_NAME);
      assertThat(serviceNowTemplate.getFields()).hasSize(5);
      assertThat(serviceNowTemplate.getFields().get("Impact").getDisplayValue()).isEqualTo("1 - High");

      verify(secretDecryptionService).decrypt(any(), any());

      verify(serviceNowRestClient)
          .getTemplateList(anyString(), eq("incident"), anyInt(), eq(100), anyString(), eq("filter"));
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetStandardChangeRequestTemplateListWithRetry() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);

    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getTemplateList(anyString(), anyString(), anyInt(), anyInt(), anyString(), eq(null)))
        .thenReturn(mockCall);

    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL jsonFile = classLoader.getResource("servicenow/serviceNowChangeRequestTemplateResponse.json");
    ObjectMapper mapper = new ObjectMapper();
    JsonNode responseNode = mapper.readTree(jsonFile);

    Response<JsonNode> jsonNodeResponse = Response.success(responseNode);
    when(mockCall.clone()).thenReturn(mockCall);
    doThrow(new SocketTimeoutException()).doReturn(jsonNodeResponse).when(mockCall).execute();
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
      ServiceNowTaskNGResponse response =
          serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                           .action(ServiceNowActionNG.GET_TEMPLATE)
                                                           .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                           .ticketType("change_request")
                                                           .templateListLimit(1)
                                                           .templateListOffset(0)
                                                           .templateName(CHANGE_REQUEST_TEMPLATE_NAME)
                                                           .build(),
              logStreamingTaskClient);

      assertThat(response.getDelegateMetaInfo()).isNull();
      assertThat(response.getServiceNowTemplateList()).hasSize(1);

      // template fields
      ServiceNowTemplate serviceNowTemplate = response.getServiceNowTemplateList().get(0);
      assertThat(serviceNowTemplate.getName()).isEqualTo(CHANGE_REQUEST_TEMPLATE_NAME);
      assertThat(serviceNowTemplate.getFields()).hasSize(12);
      assertThat(serviceNowTemplate.getFields().get("Impact").getDisplayValue()).isEqualTo("3 - Low");
      assertThat(serviceNowTemplate.getFields().get("Type").getDisplayValue()).isEqualTo("Standard");
      assertThat(serviceNowTemplate.getFields().get("Actual end date").getDisplayValue())
          .isEqualTo("2015-07-06 05:12:40");

      verify(secretDecryptionService).decrypt(any(), any());

      verify(serviceNowRestClient)
          .getTemplateList(anyString(), eq("change_request"), anyInt(), anyInt(), anyString(), eq(null));
    }
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetStandardChangeRequestTemplateList() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);

    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getStandardTemplate(anyString(), anyString(), anyString(), anyInt(), anyInt()))
        .thenReturn(mockCall);

    List<ImmutableMap<String, JsonNode>> responsemap = new ArrayList<>();
    responsemap.add(ImmutableMap.of("template", JsonUtils.asTree(Collections.singletonMap("value", "12678")), "sys_id",
        new TextNode("12345677"), "sys_name", new TextNode("test s 1")));
    responsemap.add(ImmutableMap.of("template", JsonUtils.asTree(Collections.singletonMap("value", "1279")), "sys_id",
        new TextNode("12345678"), "sys_name", new TextNode("test s 2")));

    JsonNode successResponse = JsonUtils.asTree(Collections.singletonMap("result", responsemap));
    Response<JsonNode> jsonNodeResponse = Response.success(successResponse);
    when(mockCall.clone()).thenReturn(mockCall);
    doReturn(jsonNodeResponse).when(mockCall).execute();
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
      ServiceNowTaskNGResponse response =
          serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                           .action(ServiceNowActionNG.GET_STANDARD_TEMPLATE)
                                                           .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                           .ticketType("change_request")
                                                           .templateListLimit(1)
                                                           .templateListOffset(0)
                                                           .build(),
              logStreamingTaskClient);
      ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);

      verify(serviceNowRestClient)
          .getStandardTemplate(anyString(), queryCaptor.capture(), anyString(), anyInt(), anyInt());
      String query = queryCaptor.getValue();
      assertThat(query).isEqualTo("active=true^ORDERBYsys_created_on");
      assertThat(response.getServiceNowTemplateList()).hasSize(2);

      // template fields
      ServiceNowTemplate serviceNowTemplate = response.getServiceNowTemplateList().get(0);
      assertThat(serviceNowTemplate.getName()).isEqualTo("test s 1");
      assertThat(serviceNowTemplate.getSys_id()).isEqualTo("12345677");

      serviceNowTemplate = response.getServiceNowTemplateList().get(1);
      assertThat(serviceNowTemplate.getName()).isEqualTo("test s 2");
      assertThat(serviceNowTemplate.getSys_id()).isEqualTo("12345678");

      verify(secretDecryptionService).decrypt(any(), any());
    }
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetStandardChangeRequestTemplateListHandleException() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);

    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getStandardTemplate(anyString(), anyString(), anyString(), anyInt(), anyInt()))
        .thenReturn(mockCall);
    when(mockCall.clone()).thenReturn(mockCall);
    doThrow(new InvalidRequestException("no data found", ErrorCode.SERVICENOW_ERROR, WingsException.USER))
        .when(mockCall)
        .execute();
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();

      assertThatThrownBy(
          ()
              -> serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                                  .action(ServiceNowActionNG.GET_STANDARD_TEMPLATE)
                                                                  .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                                  .ticketType("change_request")
                                                                  .templateListLimit(1)
                                                                  .templateListOffset(0)
                                                                  .build(),
                  logStreamingTaskClient))
          .isInstanceOf(ServiceNowException.class)
          .hasMessage(
              "Error occurred while fetching serviceNow Standard templates: InvalidRequestException: no data found");
    }
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetStandardChangeRequestTemplateWithTemplateName() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);

    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getStandardTemplate(anyString(), anyString(), anyString(), anyInt(), anyInt()))
        .thenReturn(mockCall);
    Call mockCall2 = Mockito.mock(Call.class);
    when(serviceNowRestClient.getStandardTemplate(anyString(), anyString())).thenReturn(mockCall2);
    List<ImmutableMap<String, JsonNode>> responsemap = new ArrayList<>();
    responsemap.add(ImmutableMap.of("template", JsonUtils.asTree(Collections.singletonMap("value", "12678")), "sys_id",
        new TextNode("12345677"), "sys_name", new TextNode(CHANGE_REQUEST_TEMPLATE_NAME)));

    JsonNode successResponse = JsonUtils.asTree(Collections.singletonMap("result", responsemap));
    Response<JsonNode> jsonNodeResponse = Response.success(successResponse);
    when(mockCall.clone()).thenReturn(mockCall);
    when(mockCall2.clone()).thenReturn(mockCall2);
    doReturn(jsonNodeResponse).when(mockCall).execute();
    doReturn(
        Response.success(JsonUtils.asTree(Collections.singletonMap("result",
            ImmutableMap.of("template",
                "short_description=Include a title for your change no greater than 100 characters^description=Describe what you plan to do^implementation_plan=List the steps")))))
        .when(mockCall2)
        .execute();
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
      ServiceNowTaskNGResponse response =
          serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                           .action(ServiceNowActionNG.GET_STANDARD_TEMPLATE)
                                                           .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                           .ticketType("change_request")
                                                           .templateName(CHANGE_REQUEST_TEMPLATE_NAME)
                                                           .templateListLimit(1)
                                                           .templateListOffset(0)
                                                           .build(),
              logStreamingTaskClient);
      ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);

      verify(serviceNowRestClient)
          .getStandardTemplate(anyString(), queryCaptor.capture(), anyString(), anyInt(), anyInt());
      String query = queryCaptor.getValue();
      assertThat(query).isEqualTo("active=true^sys_name=standard_change_template^ORDERBYsys_created_on");
      // template fields
      assertThat(response.getServiceNowFieldJsonNGListAsString())
          .isEqualTo(
              "short_description=Include a title for your change no greater than 100 characters^description=Describe what you plan to do^implementation_plan=List the steps");

      verify(serviceNowRestClient).getStandardTemplate(anyString(), queryCaptor.capture());
      query = queryCaptor.getValue();
      assertThat(query).isEqualTo("12678");
      verify(secretDecryptionService).decrypt(any(), any());
    }
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetStandardChangeRequestTemplateWithTemplateNameAndIncorrectResponse() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);

    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getStandardTemplate(anyString(), anyString(), anyString(), anyInt(), anyInt()))
        .thenReturn(mockCall);
    Call mockCall2 = Mockito.mock(Call.class);
    when(serviceNowRestClient.getStandardTemplate(anyString(), anyString())).thenReturn(mockCall2);
    List<ImmutableMap<String, JsonNode>> responsemap = new ArrayList<>();
    responsemap.add(ImmutableMap.of("template", JsonUtils.asTree(Collections.singletonMap("value", "12678")), "sys_id",
        new TextNode("12345677"), "sys_name", new TextNode("incorrect")));

    JsonNode successResponse = JsonUtils.asTree(Collections.singletonMap("result", responsemap));
    Response<JsonNode> jsonNodeResponse = Response.success(successResponse);
    when(mockCall.clone()).thenReturn(mockCall);
    when(mockCall2.clone()).thenReturn(mockCall2);
    doReturn(jsonNodeResponse).when(mockCall).execute();
    doReturn(
        Response.success(JsonUtils.asTree(Collections.singletonMap("result",
            ImmutableMap.of("template",
                "short_description=Include a title for your change no greater than 100 characters^description=Describe what you plan to do^implementation_plan=List the steps")))))
        .when(mockCall2)
        .execute();
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();

      assertThatThrownBy(
          ()
              -> serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                                  .action(ServiceNowActionNG.GET_STANDARD_TEMPLATE)
                                                                  .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                                  .ticketType("change_request")
                                                                  .templateName(CHANGE_REQUEST_TEMPLATE_NAME)
                                                                  .templateListLimit(1)
                                                                  .templateListOffset(0)
                                                                  .build(),
                  logStreamingTaskClient))
          .isInstanceOf(ServiceNowException.class)
          .hasMessage(
              "Standard Template fetched incorrect is not matched with template name standard_change_template provided");
    }
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetStandardChangeRequestTemplateWithTemplateException() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);

    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getStandardTemplate(anyString(), anyString(), anyString(), anyInt(), anyInt()))
        .thenReturn(mockCall);
    Call mockCall2 = Mockito.mock(Call.class);
    when(serviceNowRestClient.getStandardTemplate(anyString(), anyString())).thenReturn(mockCall2);
    List<ImmutableMap<String, JsonNode>> responsemap = new ArrayList<>();
    responsemap.add(ImmutableMap.of("template", JsonUtils.asTree(Collections.singletonMap("value", "12678")), "sys_id",
        new TextNode("12345677"), "sys_name", new TextNode(CHANGE_REQUEST_TEMPLATE_NAME)));

    JsonNode successResponse = JsonUtils.asTree(Collections.singletonMap("result", responsemap));
    Response<JsonNode> jsonNodeResponse = Response.success(successResponse);
    when(mockCall.clone()).thenReturn(mockCall);
    when(mockCall2.clone()).thenReturn(mockCall2);
    doReturn(jsonNodeResponse).when(mockCall).execute();
    doThrow(new InvalidRequestException("no data found", ErrorCode.SERVICENOW_ERROR, WingsException.USER))
        .when(mockCall2)
        .execute();
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
      assertThatThrownBy(
          ()
              -> serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                                  .action(ServiceNowActionNG.GET_STANDARD_TEMPLATE)
                                                                  .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                                  .ticketType("change_request")
                                                                  .templateListLimit(1)
                                                                  .templateName(CHANGE_REQUEST_TEMPLATE_NAME)
                                                                  .templateListOffset(0)
                                                                  .build(),
                  logStreamingTaskClient))
          .isInstanceOf(ServiceNowException.class)
          .hasMessage(
              "Error occurred while fetching serviceNow standard template fields: InvalidRequestException: no data found");
    }
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetReadOnlyFieldsForStandardTemplate() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);
    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getReadOnlyFieldsForStandardTemplate(anyString())).thenReturn(mockCall);
    Map<String, String> responseMap =
        ImmutableMap.of("readonly_fields", "description,backout_plan,test_plan,implementation_plan,short_description");
    JsonNode successResponse =
        JsonUtils.asTree(Collections.singletonMap("result", Collections.singletonList(responseMap)));
    Response<JsonNode> jsonNodeResponse = Response.success(successResponse);
    when(mockCall.clone()).thenReturn(mockCall);
    doReturn(jsonNodeResponse).when(mockCall).execute();
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
      ServiceNowTaskNGResponse response = serviceNowTaskNgHelper.getServiceNowResponse(
          ServiceNowTaskNGParameters.builder()
              .action(ServiceNowActionNG.GET_STANDARD_TEMPLATES_READONLY_FIELDS)
              .serviceNowConnectorDTO(serviceNowConnectorDTO)
              .build(),
          logStreamingTaskClient);

      assertThat(response.getDelegateMetaInfo()).isNull();
      assertThat(response.getServiceNowStandardTemplateReadOnlyFields())
          .isEqualTo("description,backout_plan,test_plan,implementation_plan,short_description");

      verify(secretDecryptionService).decrypt(any(), any());

      verify(serviceNowRestClient).getReadOnlyFieldsForStandardTemplate(anyString());
    }
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetReadOnlyFieldsForStandardTemplatForNullResponse() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);
    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getReadOnlyFieldsForStandardTemplate(anyString())).thenReturn(mockCall);
    when(mockCall.clone()).thenReturn(mockCall);
    JsonNode successResponse = JsonUtils.asTree(Collections.singletonMap("result1", null));
    Response<JsonNode> jsonNodeResponse = Response.success(successResponse);
    doReturn(jsonNodeResponse).when(mockCall).execute();
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();

      ServiceNowTaskNGParameters serviceNowTaskNGParameters =
          ServiceNowTaskNGParameters.builder()
              .action(ServiceNowActionNG.GET_STANDARD_TEMPLATES_READONLY_FIELDS)
              .serviceNowConnectorDTO(serviceNowConnectorDTO)
              .build();
      assertThatThrownBy(
          () -> serviceNowTaskNgHelper.getServiceNowResponse(serviceNowTaskNGParameters, logStreamingTaskClient))
          .isInstanceOf(ServiceNowException.class)
          .hasMessage(
              "Failed to fetch read only fields for standard templates  response: Response{protocol=http/1.1, code=200, message=OK, url=http://localhost/}");

      verify(secretDecryptionService).decrypt(any(), any());

      verify(serviceNowRestClient).getReadOnlyFieldsForStandardTemplate(anyString());
    }
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetReadOnlyFieldsForStandardTemplateForNullString() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);
    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getReadOnlyFieldsForStandardTemplate(anyString())).thenReturn(mockCall);
    Map<String, String> responseMap = ImmutableMap.of("dummy", "hello");
    JsonNode successResponse = JsonUtils.asTree(Collections.singletonMap("result", Collections.singleton(responseMap)));
    Response<JsonNode> jsonNodeResponse = Response.success(successResponse);
    when(mockCall.clone()).thenReturn(mockCall);
    doReturn(jsonNodeResponse).when(mockCall).execute();
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
      ServiceNowTaskNGResponse response = serviceNowTaskNgHelper.getServiceNowResponse(
          ServiceNowTaskNGParameters.builder()
              .action(ServiceNowActionNG.GET_STANDARD_TEMPLATES_READONLY_FIELDS)
              .serviceNowConnectorDTO(serviceNowConnectorDTO)
              .build(),
          logStreamingTaskClient);

      assertThat(response.getDelegateMetaInfo()).isNull();
      assertThat(response.getServiceNowStandardTemplateReadOnlyFields()).isEqualTo(null);
      verify(secretDecryptionService).decrypt(any(), any());
      verify(serviceNowRestClient).getReadOnlyFieldsForStandardTemplate(anyString());
    }
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void responseTest() throws IOException {
    Response<JsonNode> jsonNodeResponse = Response.success(200, null);

    serviceNowTaskNgHelper.handleResponse(jsonNodeResponse, "Success Message");
    ResponseBody body = mock(ResponseBody.class);
    Response<JsonNode> jsonNodeResponse1 = Response.error(401, body);
    assertThatThrownBy(() -> serviceNowTaskNgHelper.handleResponse(jsonNodeResponse1, ""))
        .isInstanceOf(ServiceNowException.class)
        .hasMessage(INVALID_SERVICE_NOW_CREDENTIALS);

    Response<JsonNode> jsonNodeResponse2 = Response.error(404, body);
    assertThatThrownBy(() -> serviceNowTaskNgHelper.handleResponse(jsonNodeResponse2, ""))
        .isInstanceOf(ServiceNowException.class)
        .hasMessage(NOT_FOUND);

    when(body.string()).thenReturn("dummy error message");
    Response<JsonNode> jsonNodeResponse3 = Response.error(400, body);
    assertThatThrownBy(
        () -> serviceNowTaskNgHelper.handleResponse(jsonNodeResponse3, "Error occurred while x operation"))
        .isInstanceOf(ServiceNowException.class)
        .hasMessage("Error occurred while x operation : dummy error message");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testApplyServiceNowWithoutTemplateToCreateTicket() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);

    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.createTicket(anyString(), anyString(), anyString(), isNull(), anyMap()))
        .thenReturn(mockCall);

    ImmutableMap<String, JsonNode> responsemap =
        ImmutableMap.of("number", JsonUtils.asTree(Collections.singletonMap("display_value", TICKET_NUMBER)));

    JsonNode successResponse = JsonUtils.asTree(Collections.singletonMap("result", responsemap));
    Response<JsonNode> jsonNodeResponse = Response.success(successResponse);
    when(mockCall.execute()).thenReturn(jsonNodeResponse);

    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      Map<String, String> fieldmap = ImmutableMap.of("value", "BEvalue2", "display_value", "UIvalue2");
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
      ServiceNowTaskNGResponse response =
          serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                           .action(ServiceNowActionNG.CREATE_TICKET)
                                                           .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                           .useServiceNowTemplate(false)
                                                           .ticketType("incident")
                                                           .fields(fieldmap)
                                                           .build(),
              logStreamingTaskClient);

      assertThat(response.getDelegateMetaInfo()).isNull();

      // ServiceNow Outcome
      assertThat(response.getTicket().getNumber()).isEqualTo(TICKET_NUMBER);
      assertThat(response.getTicket().getUrl())
          .isEqualTo("https://harness.service-now.com/nav_to.do?uri=/incident.do?sysparm_query=number=INC00001");
      assertThat(response.getTicket().getFields()).hasSize(1);
      verify(secretDecryptionService).decrypt(any(), any());
    }
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testUpdateServiceNowWithoutTemplateToUpdateTicketWithRetry() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);

    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.updateTicket(anyString(), anyString(), anyString(), anyString(), isNull(), anyMap()))
        .thenReturn(mockCall);

    ImmutableMap<String, JsonNode> responsemap =
        ImmutableMap.of("number", JsonUtils.asTree(Collections.singletonMap("display_value", TICKET_NUMBER)));

    JsonNode successResponse = JsonUtils.asTree(Collections.singletonMap("result", responsemap));
    Response<JsonNode> jsonNodeResponse = Response.success(successResponse);
    when(mockCall.clone()).thenReturn(mockCall);
    doThrow(new SocketTimeoutException()).doReturn(jsonNodeResponse).when(mockCall).execute();

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
    when(mockFetchIssueCall.clone()).thenReturn(mockFetchIssueCall);
    doThrow(new SocketTimeoutException()).doReturn(jsonNodeFetchIssueResponse).when(mockFetchIssueCall).execute();
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
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
                                                           .build(),
              logStreamingTaskClient);

      assertThat(response.getDelegateMetaInfo()).isNull();

      // ServiceNow Outcome
      assertThat(response.getTicket().getNumber()).isEqualTo(TICKET_NUMBER);
      assertThat(response.getTicket().getUrl())
          .isEqualTo("https://harness.service-now.com/nav_to.do?uri=/incident.do?sysparm_query=number=INC00001");
      assertThat(response.getTicket().getFields()).hasSize(1);
      verify(secretDecryptionService).decrypt(any(), any());
    }
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testApplyServiceNowTemplateToUpdateTicketWithoutSysIdInResponseWithRetry() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);

    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.updateUsingTemplate(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(mockCall);
    // Response map does not have record_sys_id, so we will parse record_link for this
    ImmutableMap<String, String> responseMap = ImmutableMap.of("record_link",
        "incident.do?sys_id=aacc24dcdb5f85509e7c2a59139619c4&sysparm_stack=incident_list.do?sysparm_query=active=true",
        "record_number", TICKET_NUMBER);

    JsonNode successResponse = JsonUtils.asTree(Collections.singletonMap("result", responseMap));
    Response<JsonNode> jsonNodeResponse = Response.success(successResponse);
    when(mockCall.clone()).thenReturn(mockCall);
    doThrow(new SocketTimeoutException()).doReturn(jsonNodeResponse).when(mockCall).execute();

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
    when(mockFetchIssueCall.clone()).thenReturn(mockFetchIssueCall);
    doThrow(new SocketTimeoutException()).doReturn(jsonNodeFetchIssueResponse).when(mockFetchIssueCall).execute();

    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
      ServiceNowTaskNGResponse response =
          serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                           .action(ServiceNowActionNG.UPDATE_TICKET)
                                                           .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                           .templateName(TEMPLATE_NAME)
                                                           .useServiceNowTemplate(true)
                                                           .ticketType("incident")
                                                           .ticketNumber(TICKET_NUMBER)
                                                           .build(),
              logStreamingTaskClient);

      assertThat(response.getDelegateMetaInfo()).isNull();

      // ServiceNow Outcome
      assertThat(response.getTicket().getNumber()).isEqualTo(TICKET_NUMBER);
      assertThat(response.getTicket().getUrl())
          .isEqualTo(
              "https://harness.service-now.com/nav_to.do?uri=/incident.do?sys_id=aacc24dcdb5f85509e7c2a59139619c4");
      assertThat(response.getTicket().getFields()).hasSize(2);
      verify(secretDecryptionService).decrypt(any(), any());
      verify(serviceNowRestClient)
          .updateUsingTemplate(anyString(), eq("incident"), eq(TEMPLATE_NAME), eq(TICKET_NUMBER));
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testCreateImportSetNormalAndEmptyImportData() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);

    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.createImportSet(anyString(), anyString(), anyString(), any())).thenReturn(mockCall);

    ClassLoader classLoader = this.getClass().getClassLoader();
    when(mockCall.execute())
        .thenReturn(getJsonNodeResponseFromJsonFile("servicenow/serviceNowImportSetResponse.json", classLoader));
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class, (mock, context) -> {
      when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient);
    }); MockedConstruction<NGDelegateLogCallback> logCallback = mockConstruction(NGDelegateLogCallback.class)) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
      String stagingTable = "u_testing0001";
      String importData = "{\n"
          + "    \"u_test_field\" : \"my_test_import_data\"\n"
          + "}";
      ServiceNowTaskNGResponse response =
          serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                           .action(ServiceNowActionNG.IMPORT_SET)
                                                           .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                           .stagingTableName(stagingTable)
                                                           .importData(importData)
                                                           .build(),
              logStreamingTaskClient);
      ServiceNowImportSetResponseNG importSetResponse = response.getServiceNowImportSetResponseNG();
      assertThat(response.getDelegateMetaInfo()).isNull();
      assertThat(importSetResponse.getServiceNowImportSetTransformMapResultList()).hasSize(3);
      assertThat(importSetResponse.getStagingTable()).isEqualTo(stagingTable);
      assertThat(importSetResponse.getImportSet()).isEqualTo("ISET0010075");

      assertThat(importSetResponse.getServiceNowImportSetTransformMapResultList().get(0).getTransformMap())
          .isEqualTo("Testing 2 transform maps");
      assertThat(importSetResponse.getServiceNowImportSetTransformMapResultList().get(0).getTargetTable())
          .isEqualTo("incident");
      assertThat(importSetResponse.getServiceNowImportSetTransformMapResultList().get(0).getDisplayValue())
          .isEqualTo("INC0083151");
      assertThat(importSetResponse.getServiceNowImportSetTransformMapResultList().get(0).getDisplayName())
          .isEqualTo("number");
      assertThat(importSetResponse.getServiceNowImportSetTransformMapResultList().get(0).getStatus())
          .isEqualTo("inserted");
      assertThat(importSetResponse.getServiceNowImportSetTransformMapResultList().get(0).getTargetRecordURL())
          .isEqualTo(
              "https://harness.service-now.com/nav_to.do?uri=/incident.do?sys_id=a639e9ccdb4651909e7c2a5913961911");

      assertThat(importSetResponse.getServiceNowImportSetTransformMapResultList().get(1).getTransformMap())
          .isEqualTo("Testing Full Flow");
      assertThat(importSetResponse.getServiceNowImportSetTransformMapResultList().get(1).getTargetTable())
          .isEqualTo("problem");
      assertThat(importSetResponse.getServiceNowImportSetTransformMapResultList().get(1).getDisplayValue())
          .isEqualTo("PRB0066379");
      assertThat(importSetResponse.getServiceNowImportSetTransformMapResultList().get(1).getDisplayName())
          .isEqualTo("number");
      assertThat(importSetResponse.getServiceNowImportSetTransformMapResultList().get(1).getStatus())
          .isEqualTo("inserted");
      assertThat(importSetResponse.getServiceNowImportSetTransformMapResultList().get(1).getTargetRecordURL())
          .isEqualTo(
              "https://harness.service-now.com/nav_to.do?uri=/problem.do?sys_id=123929ccdb4651909e7c2a5913961985");

      assertThat(importSetResponse.getServiceNowImportSetTransformMapResultList().get(2).getTransformMap())
          .isEqualTo("testing permissions");
      assertThat(importSetResponse.getServiceNowImportSetTransformMapResultList().get(2).getTargetTable())
          .isEqualTo("sqanda_vote");
      assertThat(importSetResponse.getServiceNowImportSetTransformMapResultList().get(2).getErrorMessage())
          .isEqualTo("No transform entry or scripts are defined; Target record not found");
      assertThat(importSetResponse.getServiceNowImportSetTransformMapResultList().get(2).getStatus())
          .isEqualTo("error");

      assertThat(response.getTicket()).isNull();
      assertThat(response.getServiceNowFieldNGList()).isNull();
      assertThat(response.getServiceNowTemplateList()).isNull();
      assertThat(response.getServiceNowStagingTableList()).isNull();

      verify(serviceNowRestClient)
          .createImportSet(anyString(), eq(stagingTable), eq("all"),
              eq(JsonUtils.asObject(importData, new TypeReference<Map<String, String>>() {})));

      serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                       .action(ServiceNowActionNG.IMPORT_SET)
                                                       .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                       .stagingTableName(stagingTable)
                                                       .importData("    ")
                                                       .build(),
          logStreamingTaskClient);
      verify(secretDecryptionService, times(2)).decrypt(any(), any());
      verify(serviceNowRestClient).createImportSet(anyString(), eq(stagingTable), eq("all"), eq(new HashMap<>()));
      verify(logCallback.constructed().get(0), times(3)).saveExecutionLog(any());
      verify(logCallback.constructed().get(1), times(3)).saveExecutionLog(any());
      verify(logCallback.constructed().get(0), times(1)).saveExecutionLog(any(), any());
      verify(logCallback.constructed().get(1), times(1)).saveExecutionLog(any(), any());
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testCreateImportSetWithMalformedResponse() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);

    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.createImportSet(anyString(), anyString(), anyString(), any())).thenReturn(mockCall);
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class, (mock, context) -> {
      when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient);
    }); MockedConstruction<NGDelegateLogCallback> logCallback = mockConstruction(NGDelegateLogCallback.class)) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
      String stagingTable = "u_testing0001";
      String importData = "{\n"
          + "    \"u_test_field\" : \"my_test_import_data\"\n"
          + "}";
      ClassLoader classLoader = this.getClass().getClassLoader();

      // case 1 when import data number missing from response
      when(mockCall.execute())
          .thenReturn(getJsonNodeResponseFromJsonFile("servicenow/serviceNowImportSetBadResponse1.json", classLoader));

      try {
        serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                         .action(ServiceNowActionNG.IMPORT_SET)
                                                         .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                         .stagingTableName(stagingTable)
                                                         .importData(importData)
                                                         .build(),
            logStreamingTaskClient);
        fail("Expected failure as import set is missing from response");
      } catch (ServiceNowException ex) {
        assertThat(ex.getParams().get("message"))
            .isEqualTo(String.format(
                "Error occurred while creating/executing serviceNow import set: InvalidArgumentsException: Field not found: %s",
                "import_set"));
      }

      // case 2 when transform map is empty array in response
      when(mockCall.execute())
          .thenReturn(getJsonNodeResponseFromJsonFile("servicenow/serviceNowImportSetBadResponse2.json", classLoader));

      try {
        serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                         .action(ServiceNowActionNG.IMPORT_SET)
                                                         .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                         .stagingTableName(stagingTable)
                                                         .importData(importData)
                                                         .build(),
            logStreamingTaskClient);
        fail("Expected failure as import set is missing from response");
      } catch (ServiceNowException ex) {
        assertThat(ex.getParams().get("message"))
            .isEqualTo("Transformation details are missing or invalid in the response received from ServiceNow");
      }

      // case 3 when staging table missing from response
      when(mockCall.execute())
          .thenReturn(getJsonNodeResponseFromJsonFile("servicenow/serviceNowImportSetBadResponse3.json", classLoader));

      try {
        serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                         .action(ServiceNowActionNG.IMPORT_SET)
                                                         .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                         .stagingTableName(stagingTable)
                                                         .importData(importData)
                                                         .build(),
            logStreamingTaskClient);
        fail("Expected failure as import set is missing from response");
      } catch (ServiceNowException ex) {
        assertThat(ex.getParams().get("message"))
            .isEqualTo(String.format(
                "Error occurred while creating/executing serviceNow import set: InvalidArgumentsException: Field not found: %s",
                "staging_table"));
      }
      logCallback.constructed().forEach(contructed -> {
        verify(contructed, times(3)).saveExecutionLog(any());
        verify(contructed, times(1)).saveExecutionLog(any(), any());
      });
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetStagingTableListWithRetry() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);
    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getStagingTableList(anyString())).thenReturn(mockCall);

    ClassLoader classLoader = this.getClass().getClassLoader();
    when(mockCall.clone()).thenReturn(mockCall);
    doThrow(new SocketTimeoutException())
        .doReturn(getJsonNodeResponseFromJsonFile("servicenow/serviceNowStagingTableListResponse.json", classLoader))
        .when(mockCall)
        .execute();
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
      ServiceNowTaskNGResponse response =
          serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                           .action(ServiceNowActionNG.GET_IMPORT_SET_STAGING_TABLES)
                                                           .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                           .build(),
              logStreamingTaskClient);
      List<ServiceNowStagingTable> stagingTableList = response.getServiceNowStagingTableList();
      assertThat(response.getDelegateMetaInfo()).isNull();
      assertThat(stagingTableList).hasSize(4);

      assertThat(stagingTableList.get(0).getName()).isEqualTo("u_venkat_demo_table");
      assertThat(stagingTableList.get(0).getLabel()).isEqualTo("venkat_demo_table");

      assertThat(stagingTableList.get(1).getName()).isEqualTo("imp_computer");
      assertThat(stagingTableList.get(1).getLabel()).isEqualTo("Computer");

      assertThat(stagingTableList.get(2).getName()).isEqualTo("u_testing0001");
      assertThat(stagingTableList.get(2).getLabel()).isEqualTo("Testing0001");

      assertThat(stagingTableList.get(3).getName()).isEqualTo("u_name_without_label");
      assertThat(stagingTableList.get(3).getLabel()).isEqualTo("u_name_without_label");

      assertThat(response.getTicket()).isNull();
      assertThat(response.getServiceNowFieldNGList()).isNull();
      assertThat(response.getServiceNowTemplateList()).isNull();
      assertThat(response.getServiceNowImportSetResponseNG()).isNull();

      verify(serviceNowRestClient).getStagingTableList(anyString());
      when(mockCall.execute())
          .thenReturn(
              getJsonNodeResponseFromJsonFile("servicenow/serviceNowStagingTableListBadResponse.json", classLoader));
      try {
        serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                         .action(ServiceNowActionNG.GET_IMPORT_SET_STAGING_TABLES)
                                                         .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                         .build(),
            logStreamingTaskClient);
        fail("Expected failure as invalid response");
      } catch (ServiceNowException ex) {
        assertThat(ex.getParams().get("message"))
            .isEqualTo(String.format("Failed to fetch staging tables received response: {%s}",
                getJsonNodeResponseFromJsonFile("servicenow/serviceNowStagingTableListBadResponse.json", classLoader)
                    .body()));
      }
      verify(secretDecryptionService, times(2)).decrypt(any(), any());
      verify(serviceNowRestClient, times(2)).getStagingTableList(anyString());
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetTicketTypesWithRetry() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);

    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getTicketTypes(anyString())).thenReturn(mockCall);

    ClassLoader classLoader = this.getClass().getClassLoader();
    when(mockCall.clone()).thenReturn(mockCall);
    doThrow(new SocketTimeoutException())
        .doReturn(getJsonNodeResponseFromJsonFile("servicenow/serviceNowTicketTypeResponse.json", classLoader))
        .when(mockCall)
        .execute();
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
      ServiceNowTaskNGResponse response =
          serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                           .action(ServiceNowActionNG.GET_TICKET_TYPES)
                                                           .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                           .build(),
              logStreamingTaskClient);
      List<ServiceNowTicketTypeDTO> ticketTypes = response.getServiceNowTicketTypeList();
      assertThat(response.getDelegateMetaInfo()).isNull();
      assertThat(ticketTypes).hasSize(43);

      assertThat(response.getTicket()).isNull();
      assertThat(response.getServiceNowFieldNGList()).isNull();
      assertThat(response.getServiceNowTemplateList()).isNull();
      assertThat(response.getServiceNowImportSetResponseNG()).isNull();
      assertThat(response.getServiceNowStagingTableList()).isNull();

      verify(serviceNowRestClient).getTicketTypes(anyString());

      verify(secretDecryptionService, times(1)).decrypt(any(), any());
      verify(serviceNowRestClient, times(1)).getTicketTypes(anyString());
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetTicketTypesMissingACLWithRetry() throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);

    Call mockCall = Mockito.mock(Call.class);
    when(serviceNowRestClient.getTicketTypes(anyString())).thenReturn(mockCall);

    ClassLoader classLoader = this.getClass().getClassLoader();
    when(mockCall.clone()).thenReturn(mockCall);
    doThrow(new SocketTimeoutException())
        .doReturn(
            getJsonNodeResponseFromJsonFile("servicenow/serviceNowTicketTypeMissingACLResponse.json", classLoader))
        .when(mockCall)
        .execute();
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();
      ServiceNowTaskNGResponse response =
          serviceNowTaskNgHelper.getServiceNowResponse(ServiceNowTaskNGParameters.builder()
                                                           .action(ServiceNowActionNG.GET_TICKET_TYPES)
                                                           .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                           .build(),
              logStreamingTaskClient);
      List<ServiceNowTicketTypeDTO> ticketTypes = response.getServiceNowTicketTypeList();
      assertThat(response.getDelegateMetaInfo()).isNull();
      assertThat(ticketTypes).hasSize(4);

      assertThat(response.getTicket()).isNull();
      assertThat(response.getServiceNowFieldNGList()).isNull();
      assertThat(response.getServiceNowTemplateList()).isNull();
      assertThat(response.getServiceNowImportSetResponseNG()).isNull();
      assertThat(response.getServiceNowStagingTableList()).isNull();

      verify(serviceNowRestClient).getTicketTypes(anyString());

      verify(secretDecryptionService, times(1)).decrypt(any(), any());
      verify(serviceNowRestClient, times(1)).getTicketTypes(anyString());
    }
  }

  private Response<JsonNode> getJsonNodeResponseFromJsonFile(String filePath, ClassLoader classLoader)
      throws Exception {
    URL jsonFile = classLoader.getResource(filePath);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode responseNode = mapper.readTree(jsonFile);
    return Response.success(responseNode);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void testUpdateServiceNowWithoutTemplateToUpdateTicketWithMultipleTasksAndWithTemplateWithRetry()
      throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);
    serviceNowTaskNgHelper.executorService = MoreExecutors.newDirectExecutorService();

    Call mockFetchChangeRequestFromCR = Mockito.mock(Call.class);
    when(serviceNowRestClient.fetchChangeTasksFromCR(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(mockFetchChangeRequestFromCR);

    List<Map<String, String>> getChangeRequestTaskResponseMap =
        List.of(ImmutableMap.of("number", "CTASK0011062", "sys_id", "9193f27f1bf4b91044cbdb58b04bcbce",
                    "change_task_type", "planning"),
            ImmutableMap.of(
                "number", "CTASK0011061", "sys_id", "a683f6fb1bf4b91044cbdb58b04bcbca", "change_task_type", "planning"),
            ImmutableMap.of("number", "CTASK0011060", "sys_id", "1044cbdb58b04bcbcaa683f6fb1bf4b9", "change_task_type",
                "planning"));
    JsonNode fetchChangeRequestResponse =
        JsonUtils.asTree(Collections.singletonMap("result", getChangeRequestTaskResponseMap));
    Response<JsonNode> jsonNodeFetchChangeRequestResponse = Response.success(fetchChangeRequestResponse);
    when(mockFetchChangeRequestFromCR.clone()).thenReturn(mockFetchChangeRequestFromCR);
    doThrow(new SocketTimeoutException())
        .doReturn(jsonNodeFetchChangeRequestResponse)
        .when(mockFetchChangeRequestFromCR)
        .execute();

    Call mockCall = Mockito.mock(Call.class);
    Call mockFetchIssueCall = Mockito.mock(Call.class);
    List<String> listTickets = List.of(TICKET_NUMBER + 1, TICKET_NUMBER + 2, TICKET_NUMBER + 3);
    for (String item : List.of("9193f27f1bf4b91044cbdb58b04bcbce", "a683f6fb1bf4b91044cbdb58b04bcbca",
             "1044cbdb58b04bcbcaa683f6fb1bf4b9")) {
      int i = 0;
      when(serviceNowRestClient.updateUsingTemplate(anyString(), anyString(), anyString(), anyString()))
          .thenReturn(mockCall);

      ImmutableMap<String, String> responseMap = ImmutableMap.of("record_sys_id", item, "record_link",
          "incident.do?sys_id=aacc24dcdb5f85509e7c2a59139619c4&sysparm_stack=incident_list.do?sysparm_query=active=true",
          "record_number", listTickets.get(i));

      JsonNode successResponse = JsonUtils.asTree(Collections.singletonMap("result", responseMap));
      Response<JsonNode> jsonNodeResponse = Response.success(successResponse);
      when(mockCall.clone()).thenReturn(mockCall);
      doThrow(new SocketTimeoutException()).doReturn(jsonNodeResponse).when(mockCall).execute();

      when(serviceNowRestClient.getIssue(anyString(), anyString(), anyString(), anyString()))
          .thenReturn(mockFetchIssueCall);
      Map<String, Map<String, String>> getIssueResponseMap =
          ImmutableMap.of("parent", ImmutableMap.of("value", "", "display_value", ""), "description",
              ImmutableMap.of("value", "BEvalue2", "display_value", "UIvalue2"), "number",
              ImmutableMap.of("value", listTickets.get(i), "display_value", listTickets.get(i)));

      JsonNode fetchIssueResponse =
          JsonUtils.asTree(Collections.singletonMap("result", Collections.singletonList(getIssueResponseMap)));
      Response<JsonNode> jsonNodeFetchIssueResponse = Response.success(fetchIssueResponse);
      when(mockFetchIssueCall.clone()).thenReturn(mockFetchIssueCall);
      doThrow(new SocketTimeoutException()).doReturn(jsonNodeFetchIssueResponse).when(mockFetchIssueCall).execute();
      i++;
    }

    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      Map<String, String> fieldmap = ImmutableMap.of("value", "BEvalue2", "display_value", "UIvalue2");

      ServiceNowUpdateMultipleTaskNode serviceNowUpdateMultiple = ServiceNowUpdateMultipleTaskNode.builder()
                                                                      .type("change_task")
                                                                      .spec(ChangeTaskUpdateMultiple.builder()
                                                                                .changeTaskType("planning")
                                                                                .changeRequestNumber(TICKET_NUMBER)
                                                                                .build())
                                                                      .build();

      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();

      ServiceNowTaskNGParameters serviceNowTaskNGParameters = ServiceNowTaskNGParameters.builder()
                                                                  .action(ServiceNowActionNG.UPDATE_TICKET)
                                                                  .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                                  .useServiceNowTemplate(true)
                                                                  .templateName("aoeu")
                                                                  .ticketType("change_task")
                                                                  .updateMultiple(serviceNowUpdateMultiple)
                                                                  .fields(fieldmap)
                                                                  .build();

      ServiceNowTaskNGResponse response =
          serviceNowTaskNgHelper.getServiceNowResponse(serviceNowTaskNGParameters, logStreamingTaskClient);

      assertThat(response.getDelegateMetaInfo()).isNull();

      // ServiceNow Outcome
      assertThat(response.getTicket()).isNull();
      assertThat(response.getTickets()).hasSize(3);
      assertThat(response.getTickets().get(0).getFields()).hasSize(2);
      assertThat(response.getTickets().get(1).getFields()).hasSize(2);
      assertThat(response.getTickets().get(2).getFields()).hasSize(2);
      verify(secretDecryptionService).decrypt(any(), any());
      verify(serviceNowRestClient, times(3)).updateUsingTemplate(anyString(), anyString(), anyString(), anyString());
    }
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void testUpdateServiceNowWithoutTemplateToUpdateTicketWithMultipleTasksAndWithoutTemplateWithRetry()
      throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);
    serviceNowTaskNgHelper.executorService = MoreExecutors.newDirectExecutorService();

    Call mockFetchChangeRequestFromCR = Mockito.mock(Call.class);
    when(serviceNowRestClient.fetchChangeTasksFromCR(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(mockFetchChangeRequestFromCR);

    List<Map<String, String>> getChangeRequestTaskResponseMap =
        List.of(ImmutableMap.of("number", "CTASK0011062", "sys_id", "9193f27f1bf4b91044cbdb58b04bcbce",
                    "change_task_type", "planning"),
            ImmutableMap.of(
                "number", "CTASK0011061", "sys_id", "a683f6fb1bf4b91044cbdb58b04bcbca", "change_task_type", "planning"),
            ImmutableMap.of("number", "CTASK0011060", "sys_id", "1044cbdb58b04bcbcaa683f6fb1bf4b9", "change_task_type",
                "planning"));
    JsonNode fetchChangeRequestResponse =
        JsonUtils.asTree(Collections.singletonMap("result", getChangeRequestTaskResponseMap));
    Response<JsonNode> jsonNodeFetchChangeRequestResponse = Response.success(fetchChangeRequestResponse);
    when(mockFetchChangeRequestFromCR.clone()).thenReturn(mockFetchChangeRequestFromCR);
    doThrow(new SocketTimeoutException())
        .doReturn(jsonNodeFetchChangeRequestResponse)
        .when(mockFetchChangeRequestFromCR)
        .execute();

    Call mockCall = Mockito.mock(Call.class);
    for (String item : List.of("9193f27f1bf4b91044cbdb58b04bcbce", "a683f6fb1bf4b91044cbdb58b04bcbca",
             "1044cbdb58b04bcbcaa683f6fb1bf4b9")) {
      when(serviceNowRestClient.updateTicket(anyString(), anyString(), anyString(), eq("all"), isNull(), anyMap()))
          .thenReturn(mockCall);

      ImmutableMap<String, JsonNode> responseMap =
          ImmutableMap.of("number", JsonUtils.asTree(Collections.singletonMap("display_value", item)));

      JsonNode successResponse = JsonUtils.asTree(Collections.singletonMap("result", responseMap));
      Response<JsonNode> jsonNodeResponse = Response.success(successResponse);
      when(mockCall.clone()).thenReturn(mockCall);
      doThrow(new SocketTimeoutException()).doReturn(jsonNodeResponse).when(mockCall).execute();
    }

    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      Map<String, String> fieldmap = ImmutableMap.of("value", "BEvalue2", "display_value", "UIvalue2");

      ServiceNowUpdateMultipleTaskNode serviceNowUpdateMultiple = ServiceNowUpdateMultipleTaskNode.builder()
                                                                      .type("change_task")
                                                                      .spec(ChangeTaskUpdateMultiple.builder()
                                                                                .changeTaskType("planning")
                                                                                .changeRequestNumber(TICKET_NUMBER)
                                                                                .build())
                                                                      .build();

      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();

      ServiceNowTaskNGParameters serviceNowTaskNGParameters = ServiceNowTaskNGParameters.builder()
                                                                  .action(ServiceNowActionNG.UPDATE_TICKET)
                                                                  .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                                  .useServiceNowTemplate(false)
                                                                  .updateMultiple(serviceNowUpdateMultiple)
                                                                  .ticketType("change_task")
                                                                  .fields(fieldmap)
                                                                  .build();

      ServiceNowTaskNGResponse response =
          serviceNowTaskNgHelper.getServiceNowResponse(serviceNowTaskNGParameters, logStreamingTaskClient);

      assertThat(response.getDelegateMetaInfo()).isNull();

      // ServiceNow Outcome
      assertThat(response.getTicket()).isNull();
      assertThat(response.getTickets()).hasSize(3);
      assertThat(response.getTickets().get(0).getFields()).hasSize(1);
      assertThat(response.getTickets().get(1).getFields()).hasSize(1);
      assertThat(response.getTickets().get(2).getFields()).hasSize(1);
      verify(secretDecryptionService).decrypt(any(), any());
      verify(serviceNowRestClient, times(3))
          .updateTicket(anyString(), anyString(), anyString(), anyString(), any(), any());
      verify(serviceNowRestClient, times(1))
          .updateTicket(anyString(), anyString(), eq("9193f27f1bf4b91044cbdb58b04bcbce"), anyString(), any(), any());
      verify(serviceNowRestClient, times(1))
          .updateTicket(anyString(), anyString(), eq("a683f6fb1bf4b91044cbdb58b04bcbca"), anyString(), any(), any());
      verify(serviceNowRestClient, times(1))
          .updateTicket(anyString(), anyString(), eq("1044cbdb58b04bcbcaa683f6fb1bf4b9"), anyString(), any(), any());
    }
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void testUpdateServiceNowWithoutTemplateToUpdateTicketWithMultipleWithALLTasksAndWithTemplateWithRetry()
      throws Exception {
    ServiceNowRestClient serviceNowRestClient = Mockito.mock(ServiceNowRestClient.class);
    serviceNowTaskNgHelper.executorService = MoreExecutors.newDirectExecutorService();

    Call mockFetchChangeRequestFromCR = Mockito.mock(Call.class);
    when(serviceNowRestClient.fetchChangeTasksFromCR(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(mockFetchChangeRequestFromCR);

    List<Map<String, String>> getChangeRequestTaskResponseMap =
        List.of(ImmutableMap.of("number", "CTASK0011062", "sys_id", "9193f27f1bf4b91044cbdb58b04bcbce",
                    "change_task_type", "planning"),
            ImmutableMap.of(
                "number", "CTASK0011061", "sys_id", "a683f6fb1bf4b91044cbdb58b04bcbca", "change_task_type", "planning"),
            ImmutableMap.of("number", "CTASK0011060", "sys_id", "1044cbdb58b04bcbcaa683f6fb1bf4b9", "change_task_type",
                "planning"));
    JsonNode fetchChangeRequestResponse =
        JsonUtils.asTree(Collections.singletonMap("result", getChangeRequestTaskResponseMap));
    Response<JsonNode> jsonNodeFetchChangeRequestResponse = Response.success(fetchChangeRequestResponse);
    when(mockFetchChangeRequestFromCR.clone()).thenReturn(mockFetchChangeRequestFromCR);
    doThrow(new SocketTimeoutException())
        .doReturn(jsonNodeFetchChangeRequestResponse)
        .when(mockFetchChangeRequestFromCR)
        .execute();

    Call mockCall = Mockito.mock(Call.class);
    Call mockFetchIssueCall = Mockito.mock(Call.class);
    List<String> listTickets = List.of(TICKET_NUMBER + 1, TICKET_NUMBER + 2, TICKET_NUMBER + 3);
    for (String item : List.of("9193f27f1bf4b91044cbdb58b04bcbce", "a683f6fb1bf4b91044cbdb58b04bcbca",
             "1044cbdb58b04bcbcaa683f6fb1bf4b9")) {
      int i = 0;
      when(serviceNowRestClient.updateUsingTemplate(anyString(), anyString(), anyString(), anyString()))
          .thenReturn(mockCall);

      ImmutableMap<String, String> responseMap = ImmutableMap.of("record_sys_id", item, "record_link",
          "incident.do?sys_id=aacc24dcdb5f85509e7c2a59139619c4&sysparm_stack=incident_list.do?sysparm_query=active=true",
          "record_number", listTickets.get(i));

      JsonNode successResponse = JsonUtils.asTree(Collections.singletonMap("result", responseMap));
      Response<JsonNode> jsonNodeResponse = Response.success(successResponse);
      when(mockCall.clone()).thenReturn(mockCall);
      doThrow(new SocketTimeoutException()).doReturn(jsonNodeResponse).when(mockCall).execute();

      when(serviceNowRestClient.getIssue(anyString(), anyString(), anyString(), anyString()))
          .thenReturn(mockFetchIssueCall);
      Map<String, Map<String, String>> getIssueResponseMap =
          ImmutableMap.of("parent", ImmutableMap.of("value", "", "display_value", ""), "description",
              ImmutableMap.of("value", "BEvalue2", "display_value", "UIvalue2"), "number",
              ImmutableMap.of("value", listTickets.get(i), "display_value", listTickets.get(i)));

      JsonNode fetchIssueResponse =
          JsonUtils.asTree(Collections.singletonMap("result", Collections.singletonList(getIssueResponseMap)));
      Response<JsonNode> jsonNodeFetchIssueResponse = Response.success(fetchIssueResponse);
      when(mockFetchIssueCall.clone()).thenReturn(mockFetchIssueCall);
      doThrow(new SocketTimeoutException()).doReturn(jsonNodeFetchIssueResponse).when(mockFetchIssueCall).execute();
      i++;
    }

    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> { when(mock.create(ServiceNowRestClient.class)).thenReturn(serviceNowRestClient); })) {
      Map<String, String> fieldmap = ImmutableMap.of("value", "BEvalue2", "display_value", "UIvalue2");

      ServiceNowUpdateMultipleTaskNode serviceNowUpdateMultiple =
          ServiceNowUpdateMultipleTaskNode.builder()
              .type("change_task")
              .spec(ChangeTaskUpdateMultiple.builder().changeRequestNumber(TICKET_NUMBER).build())
              .build();

      ServiceNowConnectorDTO serviceNowConnectorDTO = getServiceNowConnector();

      ServiceNowTaskNGParameters serviceNowTaskNGParameters = ServiceNowTaskNGParameters.builder()
                                                                  .action(ServiceNowActionNG.UPDATE_TICKET)
                                                                  .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                                  .useServiceNowTemplate(true)
                                                                  .templateName("aoeu")
                                                                  .ticketType("change_task")
                                                                  .updateMultiple(serviceNowUpdateMultiple)
                                                                  .fields(fieldmap)
                                                                  .build();

      ServiceNowTaskNGResponse response =
          serviceNowTaskNgHelper.getServiceNowResponse(serviceNowTaskNGParameters, logStreamingTaskClient);

      assertThat(response.getDelegateMetaInfo()).isNull();

      // ServiceNow Outcome
      assertThat(response.getTicket()).isNull();
      assertThat(response.getTickets()).hasSize(3);
      assertThat(response.getTickets().get(0).getFields()).hasSize(2);
      assertThat(response.getTickets().get(1).getFields()).hasSize(2);
      assertThat(response.getTickets().get(2).getFields()).hasSize(2);
      verify(secretDecryptionService).decrypt(any(), any());
      verify(serviceNowRestClient, times(3)).updateUsingTemplate(anyString(), anyString(), anyString(), anyString());
    }
  }
}
