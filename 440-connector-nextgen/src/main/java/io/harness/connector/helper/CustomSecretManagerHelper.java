/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.helper;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.services.NGConnectorSecretManagerService;
import io.harness.data.algorithm.HashGenerator;
import io.harness.delegate.beans.connector.customsecretmanager.CustomSecretManagerConnectorDTO;
import io.harness.engine.expressions.ShellScriptBaseDTO;
import io.harness.engine.expressions.ShellScriptYamlDTO;
import io.harness.engine.expressions.ShellScriptYamlExpressionEvaluator;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SecretManagementException;
import io.harness.ng.core.template.TemplateApplyRequestDTO;
import io.harness.pms.yaml.YamlUtils;
import io.harness.pms.yaml.validation.InputSetValidatorFactory;
import io.harness.remote.client.NGRestUtils;
import io.harness.secretmanagerclient.dto.CustomSecretManagerConfigDTO;
import io.harness.security.encryption.EncryptedDataParams;
import io.harness.template.remote.TemplateResourceClient;

import software.wings.beans.NameValuePairWithDefault;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.serializer.HObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
public class CustomSecretManagerHelper {
  private static final String EXPRESSION_FUNCTOR_TOKEN = "expressionFunctorToken";
  private static final String ENVIRONMENT_VARIABLES = "environmentVariables";
  private static final String INPUT_VARIABLES = "inputVariables";
  private static final String SCRIPT = "Script";
  @Inject private TemplateResourceClient templateResourceClient;
  @Inject private NGConnectorSecretManagerService ngConnectorSecretManagerService;
  @Inject private InputSetValidatorFactory inputSetValidatorFactory;
  private final ObjectMapper objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

  public Set<EncryptedDataParams> prepareEncryptedDataParamsSet(
      CustomSecretManagerConfigDTO customNGSecretManagerConfigDTO, String yaml) {
    String mergedYaml =
        NGRestUtils
            .getResponse(
                templateResourceClient.applyTemplatesOnGivenYaml(customNGSecretManagerConfigDTO.getAccountIdentifier(),
                    customNGSecretManagerConfigDTO.getOrgIdentifier(),
                    customNGSecretManagerConfigDTO.getProjectIdentifier(), null, null, null, BOOLEAN_FALSE_VALUE,
                    TemplateApplyRequestDTO.builder().originalEntityYaml(yaml).build(), false))
            .getMergedPipelineYaml();

    log.info("Yaml received from template service is \n" + mergedYaml);
    int functorToken = HashGenerator.generateIntegerHash();
    ShellScriptYamlExpressionEvaluator shellScriptYamlExpressionEvaluator =
        new ShellScriptYamlExpressionEvaluator(mergedYaml, functorToken, inputSetValidatorFactory);
    ShellScriptBaseDTO shellScriptBaseDTO;
    try {
      shellScriptBaseDTO = YamlUtils.read(mergedYaml, ShellScriptYamlDTO.class).getShellScriptBaseDTO();
    } catch (IOException e) {
      throw new InvalidRequestException("Can not convert input to shell script base dto " + e.getMessage());
    }
    shellScriptBaseDTO = (ShellScriptBaseDTO) shellScriptYamlExpressionEvaluator.resolve(shellScriptBaseDTO, false);
    // get the script out of resolved yaml
    String script = shellScriptBaseDTO.getShellScriptSpec().getSource().getSpec().getScript().getValue();
    Set<EncryptedDataParams> encryptedDataParamsSet = new HashSet<>();
    encryptedDataParamsSet.add(EncryptedDataParams.builder().name(SCRIPT).value(script).build());
    encryptedDataParamsSet.add(
        EncryptedDataParams.builder().name(EXPRESSION_FUNCTOR_TOKEN).value(String.valueOf(functorToken)).build());
    return encryptedDataParamsSet;
  }

  public Set<EncryptedDataParams> prepareEncryptedDataParamsSet(
      CustomSecretManagerConfigDTO customNGSecretManagerConfigDTO) {
    // Get connector DTO
    ConnectorDTO connectorDTO = ngConnectorSecretManagerService.getConnectorDTO(
        customNGSecretManagerConfigDTO.getAccountIdentifier(), customNGSecretManagerConfigDTO.getOrgIdentifier(),
        customNGSecretManagerConfigDTO.getProjectIdentifier(), customNGSecretManagerConfigDTO.getIdentifier());
    List<String> inputValueKeys = new LinkedList<>();
    inputValueKeys.add(ENVIRONMENT_VARIABLES);
    inputValueKeys.add(INPUT_VARIABLES);
    removeDefaultFromConfigDTO(customNGSecretManagerConfigDTO, inputValueKeys);
    // Set the template input in connector dto from the inputs received from secret.
    ((CustomSecretManagerConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig())
        .getTemplate()
        .setTemplateInputs(customNGSecretManagerConfigDTO.getTemplate().getTemplateInputs());
    String yaml = YamlUtils.writeYamlString(connectorDTO);
    return prepareEncryptedDataParamsSet(customNGSecretManagerConfigDTO, yaml);
  }

  private void removeDefaultFromConfigDTO(
      CustomSecretManagerConfigDTO customSecretManagerConfigDTO, List<String> keysToRemove) {
    Map<String, List<NameValuePairWithDefault>> inputMap =
        customSecretManagerConfigDTO.getTemplate().getTemplateInputs();
    if (inputMap == null) {
      log.info(String.format("No runtime input variables for %s Custom SecretManager Connector",
          customSecretManagerConfigDTO.getIdentifier()));
      return;
    }
    for (String key : keysToRemove) {
      if (inputMap.containsKey(key)) {
        // iterate over and set useAsDefault as null
        inputMap.get(key).forEach(variable -> variable.setUseAsDefault(null));
      }
    }
  }

  // This loops over keysToMerge in connectorValues
  // Finds if any of the list have entry which has "useAsDefault"
  // if there, insert it in inputValues
  @VisibleForTesting
  protected void mergeDefaultValuesInInputValues(Map<String, List<NameValuePairWithDefault>> inputValues,
      Map<String, List<NameValuePairWithDefault>> connectorValues) {
    List<String> keysToMerge = new LinkedList<>();
    keysToMerge.add(ENVIRONMENT_VARIABLES);
    keysToMerge.add(INPUT_VARIABLES);
    for (String keyToMerge : keysToMerge) {
      if (connectorValues.containsKey(keyToMerge)) {
        // These are like all environmentVariables or inputVariables...
        List<NameValuePairWithDefault> values = connectorValues.get(keyToMerge);
        // iterate over each variable to find values marked as default
        for (NameValuePairWithDefault variable : values) {
          // if variable has useAsDefault and if its value is true.
          if (Boolean.TRUE.equals(variable.getUseAsDefault())) {
            // set useAsDefault to null
            variable.setUseAsDefault(null);
            // insert it in inputValues
            inputValues.putIfAbsent(keyToMerge, new LinkedList<>());
            inputValues.get(keyToMerge).add(variable);
          }
        }
      }
    }
  }

  public Map<String, List<NameValuePairWithDefault>> mergeStringInInputValues(
      String inputString, Map<String, List<NameValuePairWithDefault>> connectorValues) {
    Map<String, List<NameValuePairWithDefault>> inputValues;
    if (StringUtils.isEmpty(inputString)) {
      inputValues = new HashMap<>();
    } else {
      TypeReference<HashMap<String, List<NameValuePairWithDefault>>> inputDataRef =
          new TypeReference<HashMap<String, List<NameValuePairWithDefault>>>() {};
      try {
        inputValues = objectMapper.readValue(inputString, inputDataRef);
      } catch (JsonProcessingException exception) {
        log.error("Exception when converting user input to template input.", exception);
        throw new SecretManagementException(SECRET_MANAGEMENT_ERROR,
            String.format("Input values passed can not be converted to TemplateInput map. Parsing error : %s",
                exception.getMessage()),
            USER);
      }
    }
    mergeDefaultValuesInInputValues(inputValues, connectorValues);
    return inputValues;
  }
}