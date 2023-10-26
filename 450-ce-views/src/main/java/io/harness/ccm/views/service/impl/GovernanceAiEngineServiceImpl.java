/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.views.helper.GovernancePoliciesPromptConstants.AWS_SAMPLE_POLICIES_FOR_RESOURCE;
import static io.harness.ccm.views.helper.GovernancePoliciesPromptConstants.AZURE_SAMPLE_POLICIES_FOR_RESOURCE;
import static io.harness.ccm.views.helper.GovernancePoliciesPromptConstants.DEFAULT_POLICY;
import static io.harness.ccm.views.helper.GovernancePoliciesPromptConstants.DEFAULT_POLICY_CHAT_MODEL;
import static io.harness.ccm.views.helper.GovernancePoliciesPromptConstants.GPT3;
import static io.harness.ccm.views.helper.GovernancePoliciesPromptConstants.POLICIES_PATH;
import static io.harness.ccm.views.helper.GovernancePoliciesPromptConstants.PROMPT_PATH;
import static io.harness.ccm.views.helper.GovernancePoliciesPromptConstants.TEXT_BISON;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.beans.config.GenAIServiceConfig;
import io.harness.ccm.views.dto.GovernancePromptDto;
import io.harness.ccm.views.helper.RuleCloudProviderType;
import io.harness.ccm.views.service.GovernanceAiEngineService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.CharMatcher;
import com.google.common.io.Resources;
import com.google.inject.Singleton;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

@Slf4j
@Singleton
@OwnedBy(CE)
public class GovernanceAiEngineServiceImpl implements GovernanceAiEngineService {
  @Override
  public JSONObject getDataObject(GenAIServiceConfig genAIServiceConfig, String prompt,
      RuleCloudProviderType ruleCloudProviderType, String resourceType) {
    if (genAIServiceConfig.getModel().equalsIgnoreCase(TEXT_BISON)) {
      return modifyDataForTextBison(
          genAIServiceConfig, prompt, getCompleteModelPrompt(prompt, ruleCloudProviderType, resourceType));
    } else if (genAIServiceConfig.getModel().equalsIgnoreCase(GPT3)) {
      return getChatModelPrompt(genAIServiceConfig, prompt, ruleCloudProviderType, resourceType);
    }
    return null;
  }

  @Override
  public JSONObject getExplainDataObject(GenAIServiceConfig genAIServiceConfig, String prompt) {
    if (genAIServiceConfig.getModel().equalsIgnoreCase(TEXT_BISON)) {
      return modifyDataForTextBison(genAIServiceConfig, prompt, getExplainCompleteModelPrompt(prompt));
    } else if (genAIServiceConfig.getModel().equalsIgnoreCase(GPT3)) {
      return getExplainChatModelPrompt(genAIServiceConfig, prompt);
    }
    return null;
  }

  @Override
  public JSONObject getChatModelPrompt(GenAIServiceConfig genAIServiceConfig, String prompt,
      RuleCloudProviderType ruleCloudProviderType, String resourceType) {
    JSONObject data = new JSONObject();
    String finalPrompt = "Generate only cloud custodian YAML. If you do not know the answer do not respond at all. "
        + "Repeat or modify the previous answers from chat if similar request comes in."
        + samplePolicyExecutionOutput(ruleCloudProviderType, resourceType)
        + getFiltersActionsAndCustomPrompt(ruleCloudProviderType, resourceType);
    data.put("message", prompt);
    data.put("context", finalPrompt);
    data.put("examples", samplePolicyPromptExamples(ruleCloudProviderType, resourceType));

    return modifyDataForChatGPT(genAIServiceConfig, prompt, data);
  }

  @Override
  public JSONObject getExplainChatModelPrompt(GenAIServiceConfig genAIServiceConfig, String prompt) {
    JSONObject data = new JSONObject();
    String PROMPT_PRIMING = "Explain this cloud custodian yaml policy."
        + "\n'''";
    String finalPrompt = PROMPT_PRIMING.concat(CharMatcher.is('\"').trimFrom(prompt)) + "'''";

    data.put("message", finalPrompt);
    data.put("context", "");

    JSONArray examples = new JSONArray();
    JSONObject sampleExample = new JSONObject();
    sampleExample.put("input", "");
    sampleExample.put("output", "");
    examples.put(sampleExample);

    data.put("examples", examples);

    return modifyDataForChatGPT(genAIServiceConfig, prompt, data);
  }

  @Override
  public String getCompleteModelPrompt(
      String prompt, RuleCloudProviderType ruleCloudProviderType, String resourceType) {
    String finalPrompt =
        "You are a cloud custodian YAML generator, who generates Cloud custodian YAML outputs based on user inputs. "
        + "Do not respond to questions not related to cloud custodian. Below are few sample policies:"
        + samplePolicyPrompt(ruleCloudProviderType, resourceType)
        + samplePolicyExecutionOutput(ruleCloudProviderType, resourceType)
        + getFiltersActionsAndCustomPrompt(ruleCloudProviderType, resourceType) + "\n\ninput: "
        + "";
    return finalPrompt.concat(CharMatcher.is('\"').trimFrom(prompt)) + "\n output: \n";
  }

  @Override
  public String getExplainCompleteModelPrompt(String prompt) {
    String PROMPT_PRIMING = "Explain this cloud custodian yaml policy."
        + "\n ";
    return PROMPT_PRIMING.concat(CharMatcher.is('\"').trimFrom(prompt));
  }

  private JSONArray samplePolicyPromptExamples(RuleCloudProviderType cloudProvider, String resourceType) {
    Map<String, String> samplePoliciesPath = getPoliciesPath(cloudProvider, resourceType);
    JSONArray examples = new JSONArray();
    for (String samplePolicyPath : samplePoliciesPath.keySet()) {
      String policyInput = fetchSamplePolicyInput(samplePolicyPath);
      String policyOutput = fetchSamplePolicyOutput(samplePoliciesPath.get(samplePolicyPath));
      if (policyInput.length() > 0 && policyOutput.length() > 0) {
        JSONObject sampleExample = new JSONObject();
        sampleExample.put("input", policyInput);
        sampleExample.put("output", "```" + policyOutput + "```");
        examples.put(sampleExample);
      }
    }
    if (examples.length() == 0) {
      examples.put(DEFAULT_POLICY_CHAT_MODEL);
    }
    return examples;
  }

  private String samplePolicyPrompt(RuleCloudProviderType cloudProvider, String resourceType) {
    Map<String, String> samplePoliciesPath = getPoliciesPath(cloudProvider, resourceType);
    String samplePolicyPrompt = "";
    for (String samplePolicyPath : samplePoliciesPath.keySet()) {
      String policyInput = fetchSamplePolicyInput(samplePolicyPath);
      String policyOutput = fetchSamplePolicyOutput(samplePoliciesPath.get(samplePolicyPath));
      if (policyInput.length() > 0 && policyOutput.length() > 0) {
        samplePolicyPrompt += "\ninput: " + policyInput + "\n"
            + "output: " + policyOutput;
      }
    }
    if (samplePolicyPrompt.length() == 0) {
      return DEFAULT_POLICY;
    }
    return samplePolicyPrompt;
  }

  private String samplePolicyExecutionOutput(RuleCloudProviderType cloudProvider, String resourceType) {
    try {
      String basePath = PROMPT_PATH + cloudProvider.name() + "/" + resourceType + ".json";
      return "\nBelow is output of the policy execution which can be used in filters:\n"
          + IOUtils.toString(getClass().getClassLoader().getResourceAsStream(basePath), StandardCharsets.UTF_8.name());
    } catch (Exception e) {
      log.info("Failed while getting policy execution sample output: {}", e);
    }
    return "";
  }

  private String getFiltersActionsAndCustomPrompt(RuleCloudProviderType cloudProvider, String resourceType) {
    String filtersActionsAndCustomPrompt = "";
    if (cloudProvider == null || resourceType == null) {
      return filtersActionsAndCustomPrompt;
    }
    GovernancePromptDto governancePromptDto = getGovernancePromptDto(cloudProvider.name(), resourceType);
    if (governancePromptDto.getFilters() != null && governancePromptDto.getFilters().length() > 0) {
      filtersActionsAndCustomPrompt += "\nWe can use these Filters: " + governancePromptDto.getFilters();
    }
    if (governancePromptDto.getActions() != null && governancePromptDto.getActions().length() > 0) {
      filtersActionsAndCustomPrompt +=
          "\nEither use no Action or use any of these Actions: " + governancePromptDto.getActions();
    }
    if (governancePromptDto.getCustomPrompt() != null && governancePromptDto.getCustomPrompt().length() > 0) {
      filtersActionsAndCustomPrompt += "\n" + governancePromptDto.getCustomPrompt();
    }
    return filtersActionsAndCustomPrompt;
  }

  private GovernancePromptDto getGovernancePromptDto(String cloudProvider, String resourceType) {
    try {
      String basePath = PROMPT_PATH + cloudProvider + "/" + resourceType + ".yml";
      ObjectMapper om = new ObjectMapper(new YAMLFactory());
      URL url = getClass().getClassLoader().getResource(basePath);
      byte[] bytes = Resources.toByteArray(url);
      return om.readValue(bytes, new TypeReference<GovernancePromptDto>() {});
    } catch (Exception e) {
      log.info("Failed while getting prompt for the governance resource: {}", e);
    }
    return GovernancePromptDto.builder().build();
  }

  private String fetchSamplePolicyInput(String resourcePath) {
    try {
      // URL url = new URL(resourcePath);
      // InputStream in = url.openStream();
      InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath);
      if (in != null) {
        Yaml yaml = new Yaml();
        Map<String, Object> ruleYamlMap = yaml.load(in);
        return ruleYamlMap.get("description").toString();
      }
    } catch (Exception e) {
      log.info("Failed while getting policy description from the metadata {}", e);
    }
    return "";
  }

  private String fetchSamplePolicyOutput(String resourcePath) {
    try {
      // URL url = new URL(resourcePath);
      // InputStream in = url.openStream();
      InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath);
      if (in != null) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        Yaml yaml = new Yaml(options);
        Map<String, Object> ruleYamlMap = yaml.load(in);
        return yaml.dump(ruleYamlMap);
      }
    } catch (Exception e) {
      log.info("Failed while getting governance policy {}", e);
    }
    return "";
  }

  private Map<String, String> getPoliciesPath(RuleCloudProviderType cloudProvider, String resourceType) {
    String basePath = POLICIES_PATH + cloudProvider + "/" + resourceType;
    List<String> policiesPath = new ArrayList<>();
    Map<String, String> finalPoliciesPath = new HashMap<>();
    if (cloudProvider == RuleCloudProviderType.AWS) {
      policiesPath = AWS_SAMPLE_POLICIES_FOR_RESOURCE.get(resourceType);
    } else if (cloudProvider == RuleCloudProviderType.AZURE) {
      policiesPath = AZURE_SAMPLE_POLICIES_FOR_RESOURCE.get(resourceType);
    }
    if (policiesPath != null) {
      for (String policyPath : policiesPath) {
        finalPoliciesPath.put(
            basePath + "/metadata/" + policyPath + "-metadata.yml", basePath + "/" + policyPath + ".yml");
      }
    }
    return finalPoliciesPath;
  }

  private JSONObject modifyDataForTextBison(
      GenAIServiceConfig genAIServiceConfig, String inputPrompt, String finalPrompt) {
    log.info("input prompt is: {}", CharMatcher.is('\"').trimFrom(inputPrompt));
    log.info("finalPrompt prompt is: {}", finalPrompt);

    JSONObject data = new JSONObject();
    data.put("prompt", finalPrompt);
    return commonDataObjectForModels(genAIServiceConfig, data);
  }

  private JSONObject modifyDataForChatGPT(
      GenAIServiceConfig genAIServiceConfig, String inputPrompt, JSONObject inputData) {
    log.info("input prompt is: {}", CharMatcher.is('\"').trimFrom(inputPrompt));
    log.info("message is: {}", inputData.get("message"));
    log.info("context is: {}", inputData.get("context"));
    log.info("examples are: {}", inputData.get("examples"));

    return commonDataObjectForModels(genAIServiceConfig, inputData);
  }

  private JSONObject commonDataObjectForModels(GenAIServiceConfig genAIServiceConfig, JSONObject inputData) {
    JSONObject parameters = new JSONObject();
    parameters.put("temperature", genAIServiceConfig.getTemperature());
    parameters.put("max_output_tokens", genAIServiceConfig.getMaxDecodeSteps());
    parameters.put("top_p", genAIServiceConfig.getTopP());
    parameters.put("top_k", genAIServiceConfig.getTopK());

    JSONObject finalData = inputData;
    finalData.put("provider", genAIServiceConfig.getProvider());
    finalData.put("model_name", genAIServiceConfig.getModel());
    finalData.put("model_parameters", parameters);
    return finalData;
  }
}
