/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.views.helper.GovernancePoliciesPromptConstants.AWS_DEFAULT_POLICIES;
import static io.harness.ccm.views.helper.GovernancePoliciesPromptConstants.AWS_SAMPLE_POLICIES_FOR_RESOURCE;
import static io.harness.ccm.views.helper.GovernancePoliciesPromptConstants.AZURE_DEFAULT_POLICIES;
import static io.harness.ccm.views.helper.GovernancePoliciesPromptConstants.AZURE_SAMPLE_POLICIES_FOR_RESOURCE;
import static io.harness.ccm.views.helper.GovernancePoliciesPromptConstants.CLAIMS;
import static io.harness.ccm.views.helper.GovernancePoliciesPromptConstants.DEFAULT_POLICY;
import static io.harness.ccm.views.helper.GovernancePoliciesPromptConstants.DEFAULT_POLICY_CHAT_MODEL;
import static io.harness.ccm.views.helper.GovernancePoliciesPromptConstants.GPT3;
import static io.harness.ccm.views.helper.GovernancePoliciesPromptConstants.POLICIES_PATH;
import static io.harness.ccm.views.helper.GovernancePoliciesPromptConstants.PROMPT_PATH;
import static io.harness.ccm.views.helper.GovernancePoliciesPromptConstants.TEXT_BISON;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.beans.config.AiEngineConfig;
import io.harness.ccm.commons.beans.config.GenAIServiceConfig;
import io.harness.ccm.views.dto.GovernanceAiEngineRequestDTO;
import io.harness.ccm.views.dto.GovernanceAiEngineResponseDTO;
import io.harness.ccm.views.dto.GovernancePromptDto;
import io.harness.ccm.views.dto.GovernancePromptRule;
import io.harness.ccm.views.entities.Rule;
import io.harness.ccm.views.helper.RuleCloudProviderType;
import io.harness.ccm.views.service.GovernanceAiEngineService;
import io.harness.ccm.views.service.GovernanceRuleService;
import io.harness.security.JWTTokenServiceUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.CharMatcher;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
  @Inject private GovernanceRuleService governanceRuleService;
  @Override
  public GovernanceAiEngineResponseDTO getAiEngineResponse(
      String accountId, AiEngineConfig aiEngineConfig, GovernanceAiEngineRequestDTO governanceAiEngineRequestDTO) {
    GenAIServiceConfig chatModelGenAIConfig = aiEngineConfig.getChatModelGenAIConfig();
    GenAIServiceConfig completeModelGenAIConfig = aiEngineConfig.getCompleteModelGenAIConfig();
    String jwtToken = JWTTokenServiceUtils.generateJWTToken(CLAIMS, TimeUnit.MILLISECONDS.convert(10, TimeUnit.MINUTES),
        aiEngineConfig.getChatModelGenAIConfig().getServiceSecret());

    // If request is to explain policy we use chat model and return answe
    if (governanceAiEngineRequestDTO.getIsExplain()) {
      return GovernanceAiEngineResponseDTO.builder()
          .text(callInternalGenAIService(chatModelGenAIConfig, jwtToken,
              getExplainPolicyObject(chatModelGenAIConfig, governanceAiEngineRequestDTO.getPrompt())))
          .build();
    }

    ExecutorService executorService = Executors.newFixedThreadPool(2);
    List<Future<GovernanceAiEngineResponseDTO>> governancePolicyGenerated = new ArrayList<>();
    governancePolicyGenerated.add(executorService.submit(
        () -> generateGovernancePolicy(accountId, chatModelGenAIConfig, governanceAiEngineRequestDTO, jwtToken)));
    governancePolicyGenerated.add(executorService.submit(
        () -> generateGovernancePolicy(accountId, completeModelGenAIConfig, governanceAiEngineRequestDTO, jwtToken)));

    executorService.shutdown();
    try {
      if (!executorService.awaitTermination(aiEngineConfig.getModelExecutionTermination(), TimeUnit.SECONDS)) {
        executorService.shutdownNow();
      }
    } catch (InterruptedException e) {
      executorService.shutdownNow();
    }

    GovernanceAiEngineResponseDTO invalidPolicy = null;
    for (Future<GovernanceAiEngineResponseDTO> governancePolicy : governancePolicyGenerated) {
      try {
        if (invalidPolicy == null) {
          invalidPolicy = governancePolicy.get();
        }
        if (governancePolicy.get().getIsValid()) {
          return governancePolicy.get();
        }
      } catch (InterruptedException | ExecutionException e) {
        log.error("Error while checking generated policies {}: ", e.getMessage());
      }
    }
    return invalidPolicy;
  }

  @Override
  public Set<String> getGovernancePromptResources(RuleCloudProviderType ruleCloudProviderType) {
    if (ruleCloudProviderType == RuleCloudProviderType.AZURE) {
      return AZURE_SAMPLE_POLICIES_FOR_RESOURCE.keySet();
    } else {
      return AWS_SAMPLE_POLICIES_FOR_RESOURCE.keySet();
    }
  }

  @Override
  public List<GovernancePromptRule> getGovernancePromptRules(
      RuleCloudProviderType ruleCloudProviderType, String resourceType) {
    Map<String, String> samplePoliciesPath = getPoliciesPath(
        ruleCloudProviderType == null ? RuleCloudProviderType.AWS : ruleCloudProviderType, resourceType);
    List<GovernancePromptRule> governancePromptRules = new ArrayList<>();
    for (String samplePolicyPath : samplePoliciesPath.keySet()) {
      String policyInput = fetchSamplePolicyInput(samplePolicyPath);
      String policyOutput = fetchSamplePolicyOutput(samplePoliciesPath.get(samplePolicyPath));
      if (policyInput.length() > 0 && policyOutput.length() > 0) {
        governancePromptRules.add(
            GovernancePromptRule.builder().description(policyInput).ruleYaml(policyOutput).build());
      }
    }
    return governancePromptRules;
  }

  private GovernanceAiEngineResponseDTO generateGovernancePolicy(String accountId,
      GenAIServiceConfig genAIServiceConfig, GovernanceAiEngineRequestDTO governanceAiEngineRequestDTO,
      String jwtToken) {
    JSONObject governanceRuleTypeData =
        getGeneratePolicyObject(genAIServiceConfig, governanceAiEngineRequestDTO.getPrompt(),
            governanceAiEngineRequestDTO.getRuleCloudProviderType(), governanceAiEngineRequestDTO.getResourceType());
    String result = callInternalGenAIService(genAIServiceConfig, jwtToken, governanceRuleTypeData);
    Rule validateRule = Rule.builder()
                            .rulesYaml(CharMatcher.is('\"').trimFrom(result))
                            .name(UUID.randomUUID().toString().replace("-", ""))
                            .accountId(accountId)
                            .build();
    try {
      governanceRuleService.custodianValidate(validateRule);
    } catch (Exception ex) {
      log.error("{} Model gave an invalid policy, {}: ", genAIServiceConfig.getModel(), ex);
      return GovernanceAiEngineResponseDTO.builder().text(result).isValid(false).error(ex.getMessage()).build();
    }
    return GovernanceAiEngineResponseDTO.builder().text(result).isValid(true).error("").build();
  }

  private String callInternalGenAIService(GenAIServiceConfig genAIServiceConfig, String jwtToken, JSONObject data) {
    String result = "";
    try {
      log.info("Using internal genai service");
      String API_ENDPOINT = genAIServiceConfig.getApiEndpoint();
      HttpURLConnection con = (HttpURLConnection) new URL(API_ENDPOINT).openConnection();
      con.setRequestMethod("POST");
      con.setRequestProperty("Content-Type", "application/json");
      con.setRequestProperty("Authorization", "Bearer " + jwtToken);
      con.setDoOutput(true);

      log.info("request payload:  {}", data.toString());

      con.getOutputStream().write(data.toString().getBytes());
      String output =
          new BufferedReader(new InputStreamReader(con.getInputStream())).lines().reduce((a, b) -> a + b).get();
      log.info("output for model {}:  {}", genAIServiceConfig.getModel(), output);
      JSONObject responseObject = new JSONObject(output);
      result = responseObject.getString("text");

      if (genAIServiceConfig.getModel().equalsIgnoreCase(GPT3)) {
        String pattern = "```([\\s\\S]*?)```";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(result);
        if (m.find()) {
          result = m.group(1);
          if (result.charAt(0) == '\n') {
            result = result.substring(1);
          }
        }
      }
    } catch (IOException e) {
      log.info("IOException caught, {}", e);
    }
    return result;
  }

  private JSONObject getGeneratePolicyObject(GenAIServiceConfig genAIServiceConfig, String prompt,
      RuleCloudProviderType ruleCloudProviderType, String resourceType) {
    if (genAIServiceConfig.getModel().equalsIgnoreCase(TEXT_BISON)) {
      return modifyDataForTextBison(
          genAIServiceConfig, getCompleteModelPrompt(prompt, ruleCloudProviderType, resourceType));
    } else if (genAIServiceConfig.getModel().equalsIgnoreCase(GPT3)) {
      return getChatModelPrompt(genAIServiceConfig, prompt, ruleCloudProviderType, resourceType);
    }
    return null;
  }

  private JSONObject getExplainPolicyObject(GenAIServiceConfig genAIServiceConfig, String prompt) {
    if (genAIServiceConfig.getModel().equalsIgnoreCase(TEXT_BISON)) {
      return modifyDataForTextBison(genAIServiceConfig, getExplainCompleteModelPrompt(prompt));
    } else if (genAIServiceConfig.getModel().equalsIgnoreCase(GPT3)) {
      return getExplainChatModelPrompt(genAIServiceConfig, prompt);
    }
    return null;
  }

  private JSONObject getChatModelPrompt(GenAIServiceConfig genAIServiceConfig, String prompt,
      RuleCloudProviderType ruleCloudProviderType, String resourceType) {
    String finalPrompt = "Generate only cloud custodian YAML. If you do not know the answer do not respond at all. "
        + "Repeat or modify the previous answers from chat if similar request comes in."
        + samplePolicyExecutionOutput(ruleCloudProviderType, resourceType)
        + getFiltersActionsAndCustomPrompt(ruleCloudProviderType, resourceType);

    return modifyDataForChatGPT(
        genAIServiceConfig, prompt, finalPrompt, samplePolicyPromptExamples(ruleCloudProviderType, resourceType));
  }

  private JSONObject getExplainChatModelPrompt(GenAIServiceConfig genAIServiceConfig, String prompt) {
    return modifyDataForChatGPT(genAIServiceConfig, getExplainPrompt(prompt), "", getEmptyExamplesArray());
  }

  private String getCompleteModelPrompt(
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

  private String getExplainCompleteModelPrompt(String prompt) {
    return getExplainPrompt(prompt);
  }

  private String getExplainPrompt(String prompt) {
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

  private Map<String, String> getPoliciesPath(RuleCloudProviderType cloudProvider, String resourceType) {
    Map<String, String> finalPoliciesPath = new HashMap<>();
    String basePath = POLICIES_PATH + cloudProvider + "/";
    if (resourceDoNotExist(cloudProvider, resourceType)) {
      Map<String, List<String>> defaultPolicies;
      if (cloudProvider == RuleCloudProviderType.AWS) {
        defaultPolicies = AWS_DEFAULT_POLICIES;
      } else {
        defaultPolicies = AZURE_DEFAULT_POLICIES;
      }
      for (String defaultResourceType : defaultPolicies.keySet()) {
        String updatedBasePath = basePath;
        if (cloudProvider == RuleCloudProviderType.AZURE) {
          updatedBasePath += defaultResourceType.split("\\.")[1];
        } else {
          updatedBasePath += defaultResourceType;
        }
        for (String ruleName : defaultPolicies.get(defaultResourceType)) {
          finalPoliciesPath.put(
              updatedBasePath + "/metadata/" + ruleName + "-metadata.yml", updatedBasePath + "/" + ruleName + ".yml");
        }
      }
    } else {
      List<String> policiesPath;
      if (cloudProvider == RuleCloudProviderType.AZURE) {
        basePath += resourceType.split("\\.")[1];
        policiesPath = AZURE_SAMPLE_POLICIES_FOR_RESOURCE.get(resourceType);
      } else {
        basePath += resourceType;
        policiesPath = AWS_SAMPLE_POLICIES_FOR_RESOURCE.get(resourceType);
      }
      if (policiesPath != null) {
        for (String policyPath : policiesPath) {
          finalPoliciesPath.put(
              basePath + "/metadata/" + policyPath + "-metadata.yml", basePath + "/" + policyPath + ".yml");
        }
      }
    }
    return finalPoliciesPath;
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

  private JSONObject modifyDataForTextBison(GenAIServiceConfig genAIServiceConfig, String finalPrompt) {
    JSONObject data = new JSONObject();
    data.put("prompt", finalPrompt);
    return commonDataObjectForModels(genAIServiceConfig, data);
  }

  private JSONObject modifyDataForChatGPT(
      GenAIServiceConfig genAIServiceConfig, String message, String context, JSONArray examples) {
    JSONObject data = new JSONObject();
    data.put("message", message);
    data.put("context", context);
    data.put("examples", examples);
    return commonDataObjectForModels(genAIServiceConfig, data);
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

  private boolean resourceDoNotExist(RuleCloudProviderType cloudProvider, String resourceType) {
    if (resourceType == null) {
      return true;
    }
    if (cloudProvider == RuleCloudProviderType.AWS && !AWS_SAMPLE_POLICIES_FOR_RESOURCE.containsKey(resourceType)) {
      return true;
    }
    if (cloudProvider == RuleCloudProviderType.AZURE && !AZURE_SAMPLE_POLICIES_FOR_RESOURCE.containsKey(resourceType)) {
      return true;
    }
    return false;
  }

  private JSONArray getEmptyExamplesArray() {
    JSONArray examples = new JSONArray();
    JSONObject sampleExample = new JSONObject();
    sampleExample.put("input", "");
    sampleExample.put("output", "");
    examples.put(sampleExample);
    return examples;
  }
}