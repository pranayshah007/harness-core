/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.KeyValuePair;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.serializer.JsonUtils;
import io.harness.serializer.XmlUtils;

import software.wings.sm.ContextElement;
import software.wings.sm.StateExecutionData;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.parsers.ParserConfigurationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.mongodb.morphia.annotations.Transient;
import org.w3c.dom.Document;

/**
 * The Class HttpStateExecutionData.
 *
 * @author Rishi
 */
@OwnedBy(CDC)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("httpStateExecutionData")
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class HttpStateExecutionData extends StateExecutionData implements DelegateTaskNotifyResponseData {
  private String httpUrl;
  private String httpMethod;
  private int httpResponseCode;
  private String httpResponseBody;
  private String assertionStatement;
  private String assertionStatus;
  private String header;
  private List<KeyValuePair> headers;
  private boolean useProxy;
  private String warningMessage;

  @Transient private transient Document document;

  @Builder
  public HttpStateExecutionData(String stateName, String stateType, Long startTs, Long endTs, ExecutionStatus status,
      String errorMsg, Integer waitInterval, ContextElement element, Map<String, Object> stateParams,
      Map<String, Object> templateVariables, String httpUrl, String httpMethod, int httpResponseCode,
      String httpResponseBody, String assertionStatement, String assertionStatus, Document document, String header,
      List<KeyValuePair> headers, boolean useProxy, String warningMessage) {
    super(
        stateName, stateType, startTs, endTs, status, errorMsg, waitInterval, element, stateParams, templateVariables);
    this.httpUrl = httpUrl;
    this.httpMethod = httpMethod;
    this.httpResponseCode = httpResponseCode;
    this.httpResponseBody = httpResponseBody;
    this.assertionStatement = assertionStatement;
    this.assertionStatus = assertionStatus;
    this.document = document;
    this.header = header;
    this.headers = headers;
    this.useProxy = useProxy;
    this.warningMessage = warningMessage;
  }

  /**
   * Xml format.
   *
   * @return true, if successful
   */
  public boolean xmlFormat() {
    try {
      document();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Xpath.
   *
   * @param path the path
   * @return the string
   */
  public Object xpath(String path) {
    try {
      return XmlUtils.xpath(document(), path);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * json path.
   *
   * @param path the path
   * @return the string
   */
  public Object jsonpath(String path) {
    try {
      return JsonUtils.jsonPath(httpResponseBody, path);
    } catch (Exception e) {
      return null;
    }
  }

  private Document document() throws ParserConfigurationException, IOException, org.xml.sax.SAXException {
    if (document == null) {
      document = XmlUtils.parse(httpResponseBody);
    }
    return document;
  }

  @Override
  public Map<String, ExecutionDataValue> getExecutionSummary() {
    Map<String, ExecutionDataValue> executionDetails = super.getExecutionSummary();
    return setHttpExecutionDetails(
        executionDetails, StringUtils.abbreviate(httpResponseBody, StateExecutionData.SUMMARY_PAYLOAD_LIMIT));
  }

  @Override
  public Map<String, ExecutionDataValue> getExecutionDetails() {
    Map<String, ExecutionDataValue> executionDetails = super.getExecutionDetails();
    return setHttpExecutionDetails(executionDetails, httpResponseBody);
  }

  private Map<String, ExecutionDataValue> setHttpExecutionDetails(
      Map<String, ExecutionDataValue> executionDetails, String httpResponseBody) {
    putNotNull(executionDetails, "httpUrl", ExecutionDataValue.builder().displayName("Url").value(httpUrl).build());
    putNotNull(
        executionDetails, "httpMethod", ExecutionDataValue.builder().displayName("Method").value(httpMethod).build());
    if (isNotEmpty(headers)) {
      String headerStr = headers.stream()
                             .map(headerPair -> headerPair.getKey() + ":" + headerPair.getValue())
                             .collect(Collectors.joining(","));
      putNotNull(
          executionDetails, "headers", ExecutionDataValue.builder().displayName("Header(s)").value(headerStr).build());
    }
    putNotNull(executionDetails, "httpResponseCode",
        ExecutionDataValue.builder().displayName("Response Code").value(httpResponseCode).build());
    putNotNull(executionDetails, "httpResponseBody",
        ExecutionDataValue.builder().displayName("Response Body").value(httpResponseBody).build());
    putNotNull(executionDetails, "assertionStatement",
        ExecutionDataValue.builder().displayName("Assertion").value(assertionStatement).build());
    putNotNull(executionDetails, "assertionStatus",
        ExecutionDataValue.builder().displayName("Assertion Result").value(assertionStatus).build());
    putNotNull(executionDetails, "warningMessage",
        ExecutionDataValue.builder().displayName("Warning").value(warningMessage).build());
    return executionDetails;
  }
}
