/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.TaskData;

import software.wings.beans.ScalyrConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.delegatetasks.cv.beans.CustomLogResponseMapper;
import software.wings.service.impl.analysis.CustomLogDataCollectionInfo;
import software.wings.service.intfc.scalyr.ScalyrService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.StateType;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

@Data
@Slf4j
@FieldNameConstants(innerTypeName = "ScalyrStateKeys")
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class ScalyrState extends AbstractLogAnalysisState {
  @Inject @SchemaIgnore private ScalyrService scalyrService;
  private String analysisServerConfigId;
  private String messageField;
  private String timestampField;

  public ScalyrState(String name) {
    super(name, StateType.SCALYR.name());
  }

  @Override
  public Logger getLogger() {
    return log;
  }

  public String getHostnameField() {
    return hostnameField;
  }

  public void setHostnameField(String hostnameField) {
    this.hostnameField = hostnameField;
  }

  @Override
  protected String triggerAnalysisDataCollection(
      ExecutionContext context, VerificationStateAnalysisExecutionData executionData, Set<String> hosts) {
    String envId = getEnvId(context);
    String serverConfigId =
        getResolvedConnectorId(context, ScalyrStateKeys.analysisServerConfigId, analysisServerConfigId);
    SettingAttribute settingAttribute = getSettingAttribute(serverConfigId);

    final ScalyrConfig scalyrConfig = (ScalyrConfig) settingAttribute.getValue();
    final long dataCollectionStartTimeStamp = dataCollectionStartTimestampMillis();
    String accountId = appService.get(context.getAppId()).getAccountId();

    Map<String, Map<String, CustomLogResponseMapper>> logDefinitions = scalyrService.createLogCollectionMapping(
        getResolvedFieldValue(context, AbstractAnalysisStateKeys.hostnameField, hostnameField),
        getResolvedFieldValue(context, ScalyrStateKeys.messageField, messageField),
        getResolvedFieldValue(context, ScalyrStateKeys.timestampField, timestampField));

    Map<String, String> headers = new HashMap<>();
    headers.put("Accept", "application/json");

    CustomLogDataCollectionInfo dataCollectionInfo =
        CustomLogDataCollectionInfo.builder()
            .baseUrl(scalyrConfig.getUrl())
            .validationUrl(ScalyrConfig.VALIDATION_URL)
            .dataUrl(ScalyrConfig.QUERY_URL)
            .headers(headers)
            .options(new HashMap<>())
            .body(scalyrConfig.fetchLogBodyMap(false))
            .encryptedDataDetails(secretManager.getEncryptionDetails(scalyrConfig, context.getAppId(), accountId))
            .query(getRenderedQuery())
            .hosts(hosts)
            .stateType(DelegateStateType.SCALYR)
            .applicationId(context.getAppId())
            .stateExecutionId(context.getStateExecutionInstanceId())
            .workflowId(context.getWorkflowId())
            .workflowExecutionId(context.getWorkflowExecutionId())
            .serviceId(getPhaseServiceId(context))
            .hostnameField(hostnameField)
            .responseDefinition(logDefinitions)
            .shouldDoHostBasedFiltering(shouldInspectHostsForLogAnalysis())
            .startTime(dataCollectionStartTimeStamp)
            .startMinute(0)
            .collectionFrequency(1)
            .collectionTime(Integer.parseInt(getTimeDuration(context)))
            .accountId(accountId)
            .fixedHostName(true)
            .build();

    String waitId = generateUuid();
    String infrastructureMappingId = context.fetchInfraMappingId();
    return delegateService.queueTask(
        DelegateTask.builder()
            .accountId(accountId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, context.getAppId())
            .waitId(waitId)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.CUSTOM_LOG_COLLECTION_TASK.name())
                      .parameters(new Object[] {dataCollectionInfo})
                      .timeout(TimeUnit.MINUTES.toMillis(Integer.parseInt(getTimeDuration(context)) + 120))
                      .build())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, envId)
            .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, infrastructureMappingId)
            .build());
  }
}
