/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;
import static io.harness.beans.WebhookEvent.Type.PUSH;
import static io.harness.constants.Constants.BITBUCKET_CLOUD_HEADER_KEY;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.ngtriggers.Constants.CHANGED_FILES;
import static io.harness.ngtriggers.Constants.ISSUE_COMMENT_EVENT_TYPE;
import static io.harness.ngtriggers.Constants.MERGE_REQUEST_EVENT_TYPE;
import static io.harness.ngtriggers.Constants.MR_COMMENT_EVENT_TYPE;
import static io.harness.ngtriggers.Constants.PR_COMMENT_EVENT_TYPE;
import static io.harness.ngtriggers.Constants.PULL_REQUEST_EVENT_TYPE;
import static io.harness.ngtriggers.Constants.PUSH_EVENT_TYPE;
import static io.harness.ngtriggers.Constants.RELEASE_EVENT_TYPE;
import static io.harness.ngtriggers.beans.source.webhook.WebhookAction.BT_PULL_REQUEST_UPDATED;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.HeaderConfig;
import io.harness.exception.TriggerException;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerSpecV2;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.action.BitbucketPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitAction;
import io.harness.ngtriggers.beans.source.webhook.v2.harness.HarnessSpec;
import io.harness.ngtriggers.conditionchecker.ConditionEvaluator;
import io.harness.ngtriggers.expressions.TriggerExpressionEvaluator;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@Slf4j
public class WebhookTriggerFilterUtils {
  public boolean checkIfEventTypeMatchesHarnessScm(WebhookTriggerSpecV2 webhookTriggerSpec) {
    if (webhookTriggerSpec.fetchGitAware() == null) {
      throw new TriggerException(
          "Invalid Filter used. Event Filter is not compatible with class: " + webhookTriggerSpec.getClass(), USER_SRE);
    }
    String gitEvent = webhookTriggerSpec.fetchGitAware().fetchEvent().getValue();
    return gitEvent.equals(PUSH_EVENT_TYPE);
  }

  public boolean evaluateEventAndActionFilters(
      WebhookPayloadData webhookPayloadData, WebhookTriggerSpecV2 webhookTriggerConfigSpec) {
    if (webhookTriggerConfigSpec instanceof HarnessSpec) {
      return checkIfEventTypeMatchesHarnessScm(webhookTriggerConfigSpec)
          && checkIfActionMatches(webhookPayloadData, webhookTriggerConfigSpec);
    }
    return checkIfEventTypeMatches(webhookPayloadData.getWebhookEvent().getType(), webhookTriggerConfigSpec)
        && checkIfActionMatches(webhookPayloadData, webhookTriggerConfigSpec);
  }

  public boolean checkIfEventTypeMatches(
      io.harness.beans.WebhookEvent.Type eventTypeFromPayload, WebhookTriggerSpecV2 webhookTriggerSpec) {
    if (webhookTriggerSpec.fetchGitAware() == null) {
      throw new TriggerException(
          "Invalid Filter used. Event Filter is not compatible with class: " + webhookTriggerSpec.getClass(), USER_SRE);
    }

    String gitEvent = webhookTriggerSpec.fetchGitAware().fetchEvent().getValue();

    if (eventTypeFromPayload.equals(io.harness.beans.WebhookEvent.Type.PR)) {
      return gitEvent.equals(PULL_REQUEST_EVENT_TYPE) || gitEvent.equals(MERGE_REQUEST_EVENT_TYPE);
    }

    if (eventTypeFromPayload.equals(PUSH)) {
      return gitEvent.equals(PUSH_EVENT_TYPE);
    }

    if (eventTypeFromPayload.equals(io.harness.beans.WebhookEvent.Type.ISSUE_COMMENT)) {
      return gitEvent.equals(ISSUE_COMMENT_EVENT_TYPE) || gitEvent.equals(MR_COMMENT_EVENT_TYPE)
          || gitEvent.equals(PR_COMMENT_EVENT_TYPE);
    }

    if (io.harness.beans.WebhookEvent.Type.RELEASE.equals(eventTypeFromPayload)) {
      return RELEASE_EVENT_TYPE.equals(gitEvent);
    }
    return false;
  }

  public boolean checkIfActionMatches(WebhookPayloadData webhookPayloadData, WebhookTriggerSpecV2 webhookTriggerSpec) {
    if (webhookTriggerSpec.fetchGitAware() == null) {
      throw new TriggerException(
          "Invalid Filter used. Event Filter is not compatible with class: " + webhookTriggerSpec.getClass(), USER_SRE);
    }

    List<GitAction> actions = webhookTriggerSpec.fetchGitAware().fetchActions();
    // No filter means any actions is valid for trigger invocation
    if (isEmpty(actions)) {
      return true;
    }

    Set<String> parsedActionValueSet = actions.stream().map(GitAction::getParsedValue).collect(toSet());
    if (actions.contains(BT_PULL_REQUEST_UPDATED)) {
      specialHandlingForBBSPullReqUpdate(webhookPayloadData, parsedActionValueSet);
    }
    String eventActionReceived = webhookPayloadData.getWebhookEvent().getBaseAttributes().getAction();

    if (parsedActionValueSet.contains(eventActionReceived)) {
      return true;
    }

    // perform case insensitive check
    String matchedAction = parsedActionValueSet.stream()
                               .filter(parsedValue -> parsedValue.equalsIgnoreCase(eventActionReceived))
                               .findAny()
                               .orElse(null);
    return StringUtils.isNotBlank(matchedAction);
  }

  // SCM returns "sync" for pr:open for BitbucketCloud and "open" for BitbucketServer.
  // So, For BT_PULL_REQUEST_UPDATED, we have associated "sync" as parsedValue,
  // So, here are adding "open" in case, it was bitbucker server payload
  private static void specialHandlingForBBSPullReqUpdate(
      WebhookPayloadData webhookPayloadData, Set<String> parsedActionValueSet) {
    Set<String> headerKeys =
        webhookPayloadData.getOriginalEvent().getHeaders().stream().map(HeaderConfig::getKey).collect(toSet());

    if (!headerKeys.contains(BITBUCKET_CLOUD_HEADER_KEY)
        && !headerKeys.contains(BITBUCKET_CLOUD_HEADER_KEY.toLowerCase())
        && !headerKeys.stream().anyMatch(BITBUCKET_CLOUD_HEADER_KEY::equalsIgnoreCase)) {
      parsedActionValueSet.add(BitbucketPRAction.CREATE.getParsedValue());
    }
  }

  public boolean checkIfPayloadConditionsMatch(
      WebhookPayloadData webhookPayloadData, WebhookTriggerSpecV2 webhookTriggerSpec) {
    if (isEmpty(webhookTriggerSpec.fetchPayloadAware().fetchPayloadConditions())) {
      return true;
    }

    // Remove changed files condition from payload conditions. It will be evaluated separately.
    List<TriggerEventDataCondition> payloadConditions = webhookTriggerSpec.fetchPayloadAware().fetchPayloadConditions();
    payloadConditions = payloadConditions.stream()
                            .filter(payloadCondition -> !CHANGED_FILES.equalsIgnoreCase(payloadCondition.getKey()))
                            .collect(toList());
    if (isEmpty(payloadConditions)) {
      return true;
    }

    String input;
    String standard;
    String operator;
    TriggerExpressionEvaluator triggerExpressionEvaluator = null;
    boolean allConditionsMatched = true;
    for (TriggerEventDataCondition triggerEventDataCondition : payloadConditions) {
      standard = triggerEventDataCondition.getValue();
      operator =
          triggerEventDataCondition.getOperator() != null ? triggerEventDataCondition.getOperator().getValue() : EMPTY;

      if (triggerEventDataCondition.getKey().equals("sourceBranch")) {
        input = webhookPayloadData.getWebhookEvent().getBaseAttributes().getSource();
        if (isBlank(input)) {
          // Skipping for push event type, because it doesn't have a source branch
          continue;
        }
      } else if (triggerEventDataCondition.getKey().equals("targetBranch")) {
        input = webhookPayloadData.getWebhookEvent().getBaseAttributes().getTarget();
      } else {
        if (triggerExpressionEvaluator == null) {
          triggerExpressionEvaluator = generatorPMSExpressionEvaluator(webhookPayloadData);
        }
        input = readFromPayload(triggerEventDataCondition.getKey(), triggerExpressionEvaluator);
      }

      allConditionsMatched = allConditionsMatched && ConditionEvaluator.evaluate(input, standard, operator);
      if (!allConditionsMatched) {
        break;
      }
    }

    return allConditionsMatched;
  }

  public boolean checkIfJexlConditionsMatch(
      ParseWebhookResponse parseWebhookResponse, List<HeaderConfig> headers, String payload, String jexlExpression) {
    if (isBlank(jexlExpression)) {
      return true;
    }

    jexlExpression = sanitiseHeaderConditionsForJexl(jexlExpression);

    TriggerExpressionEvaluator triggerExpressionEvaluator =
        generatorPMSExpressionEvaluator(parseWebhookResponse, headers, payload);
    Object result = triggerExpressionEvaluator.evaluateExpression(jexlExpression);
    if (result != null && Boolean.class.isAssignableFrom(result.getClass())) {
      return (Boolean) result;
    }

    StringBuilder errorMsg = new StringBuilder(128);
    if (result == null) {
      errorMsg.append("Expression ")
          .append(jexlExpression)
          .append(" was evaluated to null. Expected type is Boolean")
          .toString();
    } else {
      errorMsg.append("Expression ")
          .append(jexlExpression)
          .append(":  was evaluated to type: ")
          .append(result.getClass())
          .append(". Expected type is Boolean")
          .toString();
    }

    throw new TriggerException(errorMsg.toString(), USER);
  }

  @VisibleForTesting
  String sanitiseHeaderConditionsForJexl(String expresion) {
    if (isBlank(expresion)) {
      return expresion;
    }

    try {
      Pattern p = Pattern.compile("(<\\+trigger.header\\[[\\'|\"])(.*?)([\\'|\"]\\]>)");
      Matcher m = p.matcher(expresion);

      while (m.find()) {
        expresion = expresion.replace(
            m.group(1) + m.group(2) + m.group(3), "<+trigger.header['" + m.group(2).toLowerCase() + "']>");
      }
    } catch (Exception e) {
      log.error(
          "Failed while converting HeaderKey: " + expresion + " to lower case format. Continuing with key as is", e);
    }

    return expresion;
  }

  public boolean checkIfCustomHeaderConditionsMatch(
      List<HeaderConfig> headers, List<TriggerEventDataCondition> headerConditions) {
    if (isEmpty(headerConditions)) {
      return true;
    }
    String input;
    String standard;
    String operator;
    TriggerExpressionEvaluator triggerExpressionEvaluator = generatorPMSExpressionEvaluator(null, headers, "{}");

    for (TriggerEventDataCondition webhookHeaderCondition : headerConditions) {
      String headerConditionKey = webhookHeaderCondition.getKey();
      headerConditionKey = sanitiseHeaderConditionsForJexl(headerConditionKey);

      input = readFromPayload(headerConditionKey, triggerExpressionEvaluator);
      standard = webhookHeaderCondition.getValue();
      operator = webhookHeaderCondition.getOperator().getValue();
      if (!ConditionEvaluator.evaluate(input, standard, operator)) {
        return false;
      }
    }
    return true;
  }

  @VisibleForTesting
  String readFromPayload(String key, TriggerExpressionEvaluator triggerExpressionEvaluator) {
    return triggerExpressionEvaluator.renderExpression(key, true);
  }

  public TriggerExpressionEvaluator generatorPMSExpressionEvaluator(WebhookPayloadData webhookPayloadData) {
    return generatorPMSExpressionEvaluator(webhookPayloadData.getParseWebhookResponse(),
        webhookPayloadData.getOriginalEvent().getHeaders(), webhookPayloadData.getOriginalEvent().getPayload());
  }

  public TriggerExpressionEvaluator generatorPMSExpressionEvaluator(
      ParseWebhookResponse parseWebhookResponse, List<HeaderConfig> headerConfigs, String payload) {
    return new TriggerExpressionEvaluator(parseWebhookResponse, null, headerConfigs, payload);
  }
}
