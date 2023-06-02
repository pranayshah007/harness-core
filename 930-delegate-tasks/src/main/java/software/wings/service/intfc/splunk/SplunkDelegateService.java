/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.splunk;

import io.harness.cvng.beans.SplunkSavedSearch;
import io.harness.cvng.beans.SplunkValidationResponse;
import io.harness.delegate.beans.connector.splunkconnector.SplunkConnectorDTO;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.SplunkConfig;
import software.wings.beans.TaskType;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.DelegateTaskType;
import software.wings.service.impl.analysis.LogElement;

import com.splunk.Service;
import java.util.List;
import javax.validation.constraints.NotNull;

/**
 * Splunk Delegate Service.
 *
 * Created by rsingh on 4/17/17.
 */
public interface SplunkDelegateService {
  /**
   * Method to validate Splunk config.
   * @param splunkConfig
   * @param encryptedDataDetails
   * @return
   */
  @DelegateTaskType(TaskType.SPLUNK_CONFIGURATION_VALIDATE_TASK)
  boolean validateConfig(@NotNull SplunkConfig splunkConfig, List<EncryptedDataDetail> encryptedDataDetails);

  /**
   * Method to initialize the Splunk Service.
   * @param splunkConfig
   * @param encryptedDataDetails
   * @return
   */
  Service initSplunkService(SplunkConfig splunkConfig, List<EncryptedDataDetail> encryptedDataDetails);

  /**
   * Method that fetches log records basd on given config and query.
   * @param splunkConfig
   * @param encryptedDataDetails
   * @param basicQuery
   * @param hostNameField
   * @param host
   * @param startTime
   * @param endTime
   * @param apiCallLog
   * @return
   */
  @DelegateTaskType(TaskType.SPLUNK_GET_HOST_RECORDS)
  List<LogElement> getLogResults(SplunkConfig splunkConfig, List<EncryptedDataDetail> encryptedDataDetails,
      String basicQuery, String hostNameField, String host, long startTime, long endTime,
      ThirdPartyApiCallLog apiCallLog, int logCollectionMinute, boolean isAdvancedQuery);
  @DelegateTaskType(TaskType.SPLUNK_NG_GET_SAVED_SEARCHES)
  List<SplunkSavedSearch> getSavedSearches(
      SplunkConnectorDTO splunkConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails, String requestGuid);
  @DelegateTaskType(TaskType.SPLUNK_NG_VALIDATION_RESPONSE_TASK)
  SplunkValidationResponse getValidationResponse(SplunkConnectorDTO splunkConnectorDTO,
      List<EncryptedDataDetail> encryptedDataDetails, String query, String requestGuid);
}
