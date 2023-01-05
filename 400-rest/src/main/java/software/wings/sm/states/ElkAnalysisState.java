/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.common.TemplateExpressionProcessor.checkFieldTemplatized;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.exception.ExceptionUtils;

import software.wings.service.impl.analysis.DataCollectionInfoV2;
import software.wings.service.impl.elk.ElkDataCollectionInfoV2;
import software.wings.service.impl.elk.ElkLogFetchRequest;
import software.wings.service.impl.elk.ElkQueryType;
import software.wings.service.impl.elk.ElkQueryTypeProvider;
import software.wings.service.intfc.elk.ElkAnalysisService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.StateType;
import software.wings.stencils.DefaultValue;
import software.wings.stencils.EnumData;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import dev.morphia.annotations.Transient;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

/**
 * Created by raghu on 8/4/17.
 */
@Slf4j
@FieldNameConstants(innerTypeName = "ElkAnalysisStateKeys")
public class ElkAnalysisState extends AbstractLogAnalysisState {
  @SchemaIgnore @Transient public static final String DEFAULT_TIME_FIELD = "@timestamp";

  @SchemaIgnore @Transient protected static final String DEFAULT_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSX";

  @Transient @Inject protected ElkAnalysisService elkAnalysisService;

  @Attributes(required = true, title = "Elastic Search Server") protected String analysisServerConfigId;

  @Attributes(title = "Elastic search indices to search", required = true)
  @DefaultValue("_all")
  protected String indices;

  @Attributes(required = true, title = "Timestamp Field")
  @DefaultValue(DEFAULT_TIME_FIELD)
  protected String timestampField;

  @Attributes(required = true, title = "Message Field") @DefaultValue("message") protected String messageField;

  @Attributes(required = true, title = "Timestamp format")
  @DefaultValue("yyyy-MM-dd'T'HH:mm:ss.SSSX")
  private String timestampFormat;

  @Attributes(required = true, title = "Query Type") @DefaultValue("TERM") private String queryType;

  @Override
  public void setQuery(String query) {
    this.query = query.trim();
  }

  @Override
  @Attributes(
      required = true, title = "Search Keywords", description = "Wildcarded queries with '*' can affect cluster health")
  @DefaultValue("error")
  public String
  getQuery() {
    return query;
  }

  public ElkAnalysisState(String name) {
    super(name, StateType.ELK.getType());
  }

  public ElkAnalysisState(String name, String type) {
    super(name, type);
  }

  public String getIndices() {
    return indices;
  }

  public void setIndices(String indices) {
    this.indices = indices;
  }

  @DefaultValue("beat.hostname")
  @Attributes(required = true, title = "Hostname or Container Id Field")
  public String getHostnameField() {
    return hostnameField;
  }

  public void setHostnameField(String hostnameField) {
    this.hostnameField = hostnameField;
  }

  public String getTimestampField() {
    if (timestampField == null) {
      return DEFAULT_TIME_FIELD;
    }
    return timestampField;
  }

  public void setTimestampField(String timestampField) {
    this.timestampField = timestampField;
  }

  public String getMessageField() {
    return messageField;
  }

  public void setMessageField(String messageField) {
    this.messageField = messageField;
  }

  @EnumData(enumDataProvider = ElkQueryTypeProvider.class)
  public ElkQueryType getQueryType() {
    if (isBlank(queryType)) {
      return ElkQueryType.TERM;
    }
    return ElkQueryType.valueOf(queryType);
  }

  @Override
  @Attributes(required = true, title = "Include nodes from previous phases")
  public boolean getIncludePreviousPhaseNodes() {
    return includePreviousPhaseNodes;
  }

  public void setQueryType(String queryType) {
    this.queryType = queryType;
  }

  @Attributes(title = "Execute with previous steps")
  public boolean getExecuteWithPreviousSteps() {
    return super.isExecuteWithPreviousSteps();
  }

  public String getTimestampFormat() {
    if (timestampFormat == null) {
      return DEFAULT_TIME_FORMAT;
    }
    return timestampFormat;
  }

  public void setTimestampFormat(String format) {
    this.timestampFormat = format;
  }

  @Override
  @Attributes(required = false, title = "Expression for Host/Container name")
  public String getHostnameTemplate() {
    return hostnameTemplate;
  }

  @Override
  public void setHostnameTemplate(String hostnameTemplate) {
    this.hostnameTemplate = hostnameTemplate;
  }

  @Override
  protected String triggerAnalysisDataCollection(
      ExecutionContext context, VerificationStateAnalysisExecutionData executionData, Set<String> hosts) {
    throw new UnsupportedOperationException(
        "This should not get called. ELK is now using new data collection framework");
  }

  @Override
  public String getAnalysisServerConfigId() {
    return analysisServerConfigId;
  }

  @Override
  public void setAnalysisServerConfigId(String analysisServerConfigId) {
    this.analysisServerConfigId = analysisServerConfigId;
  }
  // don't remove this. It's used by yaml update.
  public void setInitialAnalysisDelay(String initialAnalysisDelay) {
    this.initialAnalysisDelay = initialAnalysisDelay;
  }
  /**
   * Validates Query on Manager side. No ELK call is made here.
   *
   * @return
   */
  @Override
  public Map<String, String> validateFields() {
    Map<String, String> invalidFields = new HashMap<>();
    try {
      ElkLogFetchRequest.builder()
          .query(query)
          .indices(indices)
          .hostnameField(hostnameField)
          .messageField(messageField)
          .hosts(Sets.newHashSet("ip-172-31-8-144", "ip-172-31-12-79", "ip-172-31-13-153"))
          .startTime(1518724315175L - TimeUnit.MINUTES.toMillis(1))
          .endTime(1518724315175L)
          .queryType(ElkQueryType.valueOf(queryType))
          .build()
          .toElasticSearchJsonObject();
    } catch (Exception ex) {
      invalidFields.put("query", ExceptionUtils.getMessage(ex));
    }
    return invalidFields;
  }

  @Override
  @SchemaIgnore
  public Logger getLogger() {
    return log;
  }

  @Override
  public Map<String, String> parentTemplateFields(String fieldName) {
    Map<String, String> parentTemplateFields = new LinkedHashMap<>();
    if (fieldName.equals(ElkAnalysisStateKeys.indices)) {
      if (!checkFieldTemplatized(ElkAnalysisStateKeys.analysisServerConfigId, getTemplateExpressions())) {
        parentTemplateFields.put(ElkAnalysisStateKeys.analysisServerConfigId, analysisServerConfigId);
      }
    }
    return parentTemplateFields;
  }

  @Override
  public DataCollectionInfoV2 createDataCollectionInfo(ExecutionContext context, Set<String> hosts) {
    return ElkDataCollectionInfoV2.builder()
        .connectorId(
            getResolvedConnectorId(context, ElkAnalysisStateKeys.analysisServerConfigId, analysisServerConfigId))
        .workflowExecutionId(context.getWorkflowExecutionId())
        .stateExecutionId(context.getStateExecutionInstanceId())
        .workflowId(context.getWorkflowId())
        .accountId(appService.getAccountIdByAppId(context.getAppId()))
        .envId(getEnvId(context))
        .applicationId(context.getAppId())
        .query(getRenderedQuery())
        .hostnameField(getResolvedFieldValue(context, AbstractAnalysisStateKeys.hostnameField, getHostnameField()))
        .hosts(hosts)
        .indices(getResolvedFieldValue(context, ElkAnalysisStateKeys.indices, indices))
        .messageField(getResolvedFieldValue(context, ElkAnalysisStateKeys.messageField, messageField))
        .timestampField(getResolvedFieldValue(context, ElkAnalysisStateKeys.timestampField, getTimestampField()))
        .timestampFieldFormat(
            getResolvedFieldValue(context, ElkAnalysisStateKeys.timestampFormat, getTimestampFormat()))
        .queryType(getQueryType())
        .build();
  }

  @Override
  protected int getDelaySeconds(String initialDelay) {
    if (isEmpty(initialDelay)) {
      initialDelay = "2m";
    }
    char lastChar = initialDelay.charAt(initialDelay.length() - 1);
    int initialDelayInSec;
    switch (lastChar) {
      case 's':
        initialDelayInSec = Integer.parseInt(initialDelay.substring(0, initialDelay.length() - 1));
        break;
      case 'm':
        initialDelayInSec = (int) TimeUnit.MINUTES.toSeconds(1)
            * Integer.parseInt(initialDelay.substring(0, initialDelay.length() - 1));
        break;
      default:
        throw new IllegalArgumentException("Specify delay(initialAnalysisDelay) in seconds (1s) or minutes (1m)");
    }
    Preconditions.checkState(initialDelayInSec >= 60 && initialDelayInSec <= 10 * 60,
        "initialAnalysisDelay can only be between 1 to 10 minutes.");
    return initialDelayInSec;
  }
  @Override
  protected boolean isCVTaskEnqueuingEnabled(String accountId) {
    return true;
  }
}
