/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service;

import io.harness.ccm.commons.beans.config.AiEngineConfig;
import io.harness.ccm.views.dto.GovernanceAiEngineRequestDTO;
import io.harness.ccm.views.dto.GovernanceAiEngineResponseDTO;
import io.harness.ccm.views.dto.GovernancePromptRule;
import io.harness.ccm.views.helper.RuleCloudProviderType;

import java.util.List;
import java.util.Set;

public interface GovernanceAiEngineService {
  GovernanceAiEngineResponseDTO getAiEngineResponse(
      String accountId, AiEngineConfig aiEngineConfig, GovernanceAiEngineRequestDTO governanceAiEngineRequestDTO);
  Set<String> getGovernancePromptResources(RuleCloudProviderType ruleCloudProviderType);
  List<GovernancePromptRule> getGovernancePromptRules(RuleCloudProviderType ruleCloudProviderType, String resourceType);
}
