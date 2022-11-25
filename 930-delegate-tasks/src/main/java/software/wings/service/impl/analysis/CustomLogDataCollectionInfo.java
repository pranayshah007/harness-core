/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator.HttpCapabilityDetailsLevel.QUERY;

import static software.wings.delegatetasks.cv.CVConstants.DELAY_MINUTES;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.delegatetasks.DelegateStateType;
import software.wings.delegatetasks.cv.beans.CustomLogResponseMapper;
import software.wings.delegatetasks.utils.UrlUtility;

import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
public class CustomLogDataCollectionInfo extends LogDataCollectionInfo {
  private String baseUrl;
  private String validationUrl;
  private String dataUrl;
  private Map<String, Map<String, CustomLogResponseMapper>> logResponseDefinition;
  private Map<String, String> headers;
  private Map<String, String> options;
  private String jsonBody;
  private int collectionFrequency;
  private String hostnameSeparator;

  @Default private boolean shouldDoHostBasedFiltering = true;
  private boolean fixedHostName;
  // initial delay in LogDataCollectionInfo is not used because we want this value to be default to DELAY_MINUTE instead
  // of 0 Also this is done to reduce risk refactoring and touching more code. Not adding it to builder because this is
  // used by only data dog log (For per minute task)
  @Deprecated private int delayMinutes = DELAY_MINUTES;

  @Builder
  public CustomLogDataCollectionInfo(String baseUrl, String validationUrl, String dataUrl,
      Map<String, Map<String, CustomLogResponseMapper>> responseDefinition, Map<String, String> headers,
      Map<String, String> options, String jsonBody, int collectionFrequency, String accountId, String applicationId,
      String stateExecutionId, String cvConfidId, String workflowId, String workflowExecutionId, String serviceId,
      String query, long startTime, long endTime, int startMinute, int collectionTime, String hostnameField,
      Set<String> hosts, DelegateStateType stateType, List<EncryptedDataDetail> encryptedDataDetails,
      int initialDelayMinutes, String hostnameSeparator, boolean shouldDoHostBasedFiltering, boolean fixedHostName) {
    super(accountId, applicationId, stateExecutionId, cvConfidId, workflowId, workflowExecutionId, serviceId, query,
        startTime, endTime, startMinute, collectionTime, hostnameField, hosts, stateType, encryptedDataDetails,
        initialDelayMinutes);
    this.baseUrl = baseUrl;
    this.validationUrl = validationUrl;
    this.dataUrl = dataUrl;
    this.logResponseDefinition = responseDefinition;
    this.headers = headers;
    this.options = options;
    this.jsonBody = jsonBody;
    this.collectionFrequency = collectionFrequency;
    this.hostnameSeparator = hostnameSeparator;
    this.shouldDoHostBasedFiltering = shouldDoHostBasedFiltering;
    this.fixedHostName = fixedHostName;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();
    executionCapabilities.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
        UrlUtility.appendPathToBaseUrl(getBaseUrl(), getValidationUrl()), QUERY, maskingEvaluator));
    executionCapabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
        encryptedDataDetails, maskingEvaluator));
    return executionCapabilities;
  }

  public CustomLogDataCollectionInfo clone() {
    CustomLogDataCollectionInfo clone = CustomLogDataCollectionInfo.builder().build();
    super.copy(clone);
    clone.setBaseUrl(baseUrl);
    clone.setValidationUrl(validationUrl);
    clone.setDataUrl(dataUrl);
    clone.setLogResponseDefinition(
        logResponseDefinition == null ? null : Maps.newHashMap(logResponseDefinition)); // note: NOT A DEEPCOPY
    clone.setHeaders(headers == null ? null : Maps.newHashMap(headers));
    clone.setOptions(options == null ? null : Maps.newHashMap(options));
    clone.setJsonBody(jsonBody);
    clone.setCollectionFrequency(collectionFrequency);
    clone.setHostnameSeparator(hostnameSeparator);
    clone.setShouldDoHostBasedFiltering(shouldDoHostBasedFiltering);
    clone.setFixedHostName(fixedHostName);

    return clone;
  }
}
