/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service;

import io.harness.ccm.commons.beans.config.GenAIServiceConfig;
import io.harness.ccm.views.helper.RuleCloudProviderType;

import org.json.JSONObject;

public interface GovernanceAiEngineService {
  JSONObject getDataObject(GenAIServiceConfig genAIServiceConfig, String prompt,
      RuleCloudProviderType ruleCloudProviderType, String resourceType);
  JSONObject getExplainDataObject(GenAIServiceConfig genAIServiceConfig, String prompt);
  JSONObject getChatModelPrompt(GenAIServiceConfig genAIServiceConfig, String prompt,
      RuleCloudProviderType ruleCloudProviderType, String resourceType);
  JSONObject getExplainChatModelPrompt(GenAIServiceConfig genAIServiceConfig, String prompt);
  String getCompleteModelPrompt(String prompt, RuleCloudProviderType ruleCloudProviderType, String resourceType);
  String getExplainCompleteModelPrompt(String prompt);
}
