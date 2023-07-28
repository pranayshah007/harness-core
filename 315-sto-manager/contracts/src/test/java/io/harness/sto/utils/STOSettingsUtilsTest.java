/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.sto.utils;

import static io.harness.rule.OwnerRule.SERGEY;
import static io.harness.sto.utils.STOSettingsUtils.getSTOKey;
import static io.harness.sto.utils.STOSettingsUtils.getSTOPluginEnvVariables;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import io.harness.beans.steps.stepinfo.security.AwsEcrStepInfo;
import io.harness.beans.steps.stepinfo.security.BlackDuckStepInfo;
import io.harness.beans.steps.stepinfo.security.BurpStepInfo;
import io.harness.beans.steps.stepinfo.security.CustomIngestStepInfo;
import io.harness.beans.steps.stepinfo.security.FossaStepInfo;
import io.harness.beans.steps.stepinfo.security.MendStepInfo;
import io.harness.beans.steps.stepinfo.security.MetasploitStepInfo;
import io.harness.beans.steps.stepinfo.security.NmapStepInfo;
import io.harness.beans.steps.stepinfo.security.ProwlerStepInfo;
import io.harness.beans.steps.stepinfo.security.shared.STOGenericStepInfo;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlAdvancedSettings;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlArgs;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlAuth;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlBlackduckToolData;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlImage;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlIngestion;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlInstance;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlLog;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlTarget;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;
import io.harness.yaml.sto.variables.STOYamlAuthType;
import io.harness.yaml.sto.variables.STOYamlBurpConfig;
import io.harness.yaml.sto.variables.STOYamlCustomIngestConfig;
import io.harness.yaml.sto.variables.STOYamlGenericConfig;
import io.harness.yaml.sto.variables.STOYamlLogLevel;
import io.harness.yaml.sto.variables.STOYamlLogSerializer;
import io.harness.yaml.sto.variables.STOYamlMetasploitConfig;
import io.harness.yaml.sto.variables.STOYamlNmapConfig;
import io.harness.yaml.sto.variables.STOYamlProwlerConfig;
import io.harness.yaml.sto.variables.STOYamlScanMode;
import io.harness.yaml.sto.variables.STOYamlTargetType;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class STOSettingsUtilsTest {
  private static final String PRODUCT_NAME = "product_name";
  private static final String INGESTION_FILE_NAME = "/tmp/test.json";
  private static final String WORKSPACE = "/tmp";
  private static final String TARGET_NAME = "test";
  private static final String TARGET_VARIANT = "variant";
  private static final String ACCESS_ID = "accessId";
  private static final String ACCESS_TOKEN = "accessToken";
  private static final String ACCESS_REGION = "us-east-1";
  private static final String ACCESS_DOMAIN = "test.io";
  private static final String INSTANCE_PATH = "/path";
  private static final String INSTANCE_PROTOCOL = "https";
  private static final Integer INSTANCE_PORT = 443;
  private static final String CLI_PARAMS = "--test";
  private static final String IMAGE_NAME = "image-test";
  private static final String IMAGE_TAG = "latest";

  private static final String USERNAME = "username";

  private static final String PASSWORD = "password";

  @Test
  @Owner(developers = SERGEY)
  @Category(UnitTests.class)
  public void getBlackDuckEnvVariablesTest() throws IOException {
    BlackDuckStepInfo step =
        BlackDuckStepInfo.builder()
            .target(createTarget(STOYamlTargetType.REPOSITORY, TARGET_NAME, TARGET_VARIANT, WORKSPACE))
            .ingestion(createIngestionSettings(INGESTION_FILE_NAME))
            .advanced(createAdvancedSettings(STOYamlLogSerializer.BASIC, STOYamlLogLevel.DEBUG, CLI_PARAMS, ""))
            .auth(createAuthSettings())
            .image(createImageSettings())
            .tool(createBDHToolData())
            .config(STOYamlGenericConfig.DEFAULT)
            .build();

    assertEnvVariables(step, getExpectedValue("bdh_repository.json"));
  }

  @Test
  @Owner(developers = SERGEY)
  @Category(UnitTests.class)
  public void getMendEnvVariablesTest() throws IOException {
    MendStepInfo step =
        MendStepInfo.builder()
            .mode(ParameterField.createExpressionField(true, "orchestration", null, false))
            .target(createTarget(STOYamlTargetType.CONTAINER, TARGET_NAME, TARGET_VARIANT, WORKSPACE))
            .ingestion(createIngestionSettings(INGESTION_FILE_NAME))
            .advanced(createAdvancedSettings(STOYamlLogSerializer.BASIC, STOYamlLogLevel.DEBUG, CLI_PARAMS, ""))
            .auth(createAuthSettings())
            .image(createImageSettings())
            .config(STOYamlGenericConfig.DEFAULT)
            .build();

    assertEnvVariables(step, getExpectedValue("mend_repository.json"));
  }

  @Test
  @Owner(developers = SERGEY)
  @Category(UnitTests.class)
  public void getFossaEnvVariablesTest() throws IOException {
    FossaStepInfo step =
        FossaStepInfo.builder()
            .mode(ParameterField.createExpressionField(true, "orchestration", null, false))
            .target(createTarget(STOYamlTargetType.REPOSITORY, TARGET_NAME, TARGET_VARIANT, WORKSPACE))
            .ingestion(createIngestionSettings(INGESTION_FILE_NAME))
            .advanced(createAdvancedSettings(STOYamlLogSerializer.BASIC, STOYamlLogLevel.DEBUG, CLI_PARAMS, ""))
            .auth(createAuthSettings())
            .config(STOYamlGenericConfig.DEFAULT)
            .build();

    assertEnvVariables(step, getExpectedValue("fossa_repository.json"));
  }

  @Test
  @Owner(developers = SERGEY)
  @Category(UnitTests.class)
  public void getAwsEcrEnvVariablesTest() throws IOException {
    AwsEcrStepInfo step =
        AwsEcrStepInfo.builder()
            .mode(ParameterField.createExpressionField(true, "orchestration", null, false))
            .target(createTarget(STOYamlTargetType.CONTAINER, TARGET_NAME, TARGET_VARIANT, WORKSPACE))
            .ingestion(createIngestionSettings(INGESTION_FILE_NAME))
            .advanced(createAdvancedSettings(STOYamlLogSerializer.BASIC, STOYamlLogLevel.DEBUG, CLI_PARAMS, ""))
            .auth(createAuthSettings())
            .image(createImageSettings())
            .config(STOYamlGenericConfig.DEFAULT)
            .build();

    assertEnvVariables(step, getExpectedValue("awsecr.json"));
  }

  @Test
  @Owner(developers = SERGEY)
  @Category(UnitTests.class)
  public void getProwlerEnvVariablesTest() throws IOException {
    ParameterField<STOYamlScanMode> scanMode = ParameterField.createExpressionField(false, null, null, false);
    scanMode.updateWithValue("orchestration");

    ProwlerStepInfo step =
        ProwlerStepInfo.builder()
            .mode(scanMode)
            .target(createTarget(STOYamlTargetType.CONFIGURATION, TARGET_NAME, TARGET_VARIANT, WORKSPACE))
            .ingestion(createIngestionSettings(INGESTION_FILE_NAME))
            .advanced(createAdvancedSettings(STOYamlLogSerializer.BASIC, STOYamlLogLevel.DEBUG, CLI_PARAMS, ""))
            .auth(createAuthSettings())
            .config(STOYamlProwlerConfig.HIPAA)
            .build();

    assertEnvVariables(step, getExpectedValue("prowler.json"));
  }

  @Test
  @Owner(developers = SERGEY)
  @Category(UnitTests.class)
  public void getNmapEnvVariablesTest() throws IOException {
    NmapStepInfo step =
        NmapStepInfo.builder()
            .mode(ParameterField.createExpressionField(true, "orchestration", null, false))
            .target(createTarget(STOYamlTargetType.INSTANCE, TARGET_NAME, TARGET_VARIANT, WORKSPACE))
            .ingestion(createIngestionSettings(INGESTION_FILE_NAME))
            .advanced(createAdvancedSettings(STOYamlLogSerializer.BASIC, STOYamlLogLevel.DEBUG, CLI_PARAMS, ""))
            .instance(createInstanceSettings())
            .config(STOYamlNmapConfig.EXPLOIT)
            .build();

    assertEnvVariables(step, getExpectedValue("nmap.json"));
  }
  @Test
  @Owner(developers = SERGEY)
  @Category(UnitTests.class)
  public void getCustomIngestEnvVariablesTest() throws IOException {
    CustomIngestStepInfo step =
        CustomIngestStepInfo.builder()
            .mode(ParameterField.createExpressionField(true, "orchestration", null, false))
            .target(createTarget(STOYamlTargetType.INSTANCE, TARGET_NAME, TARGET_VARIANT, WORKSPACE))
            .ingestion(createIngestionSettings(INGESTION_FILE_NAME))
            .advanced(createAdvancedSettings(STOYamlLogSerializer.BASIC, STOYamlLogLevel.DEBUG, CLI_PARAMS, ""))
            .config(STOYamlCustomIngestConfig.SARIF)
            .build();

    assertEnvVariables(step, getExpectedValue("customingest.json"));
  }

  @Test
  @Owner(developers = SERGEY)
  @Category(UnitTests.class)
  public void getMetasploitEnvVariablesTest() throws IOException {
    MetasploitStepInfo step =
        MetasploitStepInfo.builder()
            .mode(ParameterField.createExpressionField(true, "orchestration", null, false))
            .target(createTarget(STOYamlTargetType.INSTANCE, TARGET_NAME, TARGET_VARIANT, WORKSPACE))
            .ingestion(createIngestionSettings(INGESTION_FILE_NAME))
            .advanced(createAdvancedSettings(STOYamlLogSerializer.BASIC, STOYamlLogLevel.DEBUG, CLI_PARAMS, ""))
            .instance(createInstanceSettings())
            .config(STOYamlMetasploitConfig.WEAK_SSH)
            .build();

    assertEnvVariables(step, getExpectedValue("metasploit.json"));
  }

  @Test
  @Owner(developers = SERGEY)
  @Category(UnitTests.class)
  public void getBurpEnvVariablesTest() throws IOException {
    BurpStepInfo step =
        BurpStepInfo.builder()
            .mode(ParameterField.createExpressionField(true, "orchestration", null, false))
            .target(createTarget(STOYamlTargetType.INSTANCE, TARGET_NAME, TARGET_VARIANT, WORKSPACE))
            .ingestion(createIngestionSettings(INGESTION_FILE_NAME))
            .advanced(createAdvancedSettings(STOYamlLogSerializer.BASIC, STOYamlLogLevel.DEBUG, CLI_PARAMS, ""))
            .instance(createInstanceSettings())
            .config(STOYamlBurpConfig.DEFAULT)
            .build();
    assertEnvVariables(step, getExpectedValue("burp.json"));
  }

  @Test
  @Owner(developers = SERGEY)
  @Category(UnitTests.class)
  public void getSTOKeyTest() {
    assertEquals(getSTOKey(PRODUCT_NAME), "SECURITY_PRODUCT_NAME");
  }

  private static STOYamlBlackduckToolData createBDHToolData() {
    return STOYamlBlackduckToolData.builder()
        .projectName(ParameterField.createValueField("project"))
        .projectVersion(ParameterField.createValueField("version"))
        .build();
  }

  private static STOYamlInstance createInstanceSettings() {
    return STOYamlInstance.builder()
        .username(ParameterField.createValueField(USERNAME))
        .password(ParameterField.createValueField(PASSWORD))
        .path(ParameterField.createValueField(INSTANCE_PATH))
        .port(ParameterField.createValueField(INSTANCE_PORT))
        .protocol(ParameterField.createValueField(INSTANCE_PROTOCOL))
        .domain(ParameterField.createValueField(ACCESS_DOMAIN))
        .build();
  }

  private static STOYamlAuth createAuthSettings() {
    ParameterField<STOYamlAuthType> authType = ParameterField.createExpressionField(false, null, null, false);
    authType.updateWithValue("apiKey");
    return STOYamlAuth.builder()
        .accessId(ParameterField.createValueField(ACCESS_ID))
        .accessToken(ParameterField.createValueField(ACCESS_TOKEN))
        .region(ParameterField.createValueField(ACCESS_REGION))
        .domain(ParameterField.createValueField(ACCESS_DOMAIN))
        .ssl(ParameterField.createValueField(Boolean.FALSE))
        .type(authType)
        .build();
  }

  private static STOYamlImage createImageSettings() {
    return STOYamlImage.builder()
        .accessId(ParameterField.createValueField(ACCESS_ID))
        .accessToken(ParameterField.createValueField(ACCESS_TOKEN))
        .region(ParameterField.createValueField(ACCESS_REGION))
        .domain(ParameterField.createValueField(ACCESS_DOMAIN))
        .name(ParameterField.createValueField(IMAGE_NAME))
        .tag(ParameterField.createValueField(IMAGE_TAG))
        .build();
  }

  private static STOYamlIngestion createIngestionSettings(String file) {
    return STOYamlIngestion.builder().file(ParameterField.createValueField(file)).build();
  }

  private static STOYamlAdvancedSettings createAdvancedSettings(
      STOYamlLogSerializer serializer, STOYamlLogLevel level, String cliParams, String passthroughParams) {
    return STOYamlAdvancedSettings.builder()
        .log(createLogSettings(serializer, level))
        .args(createArgsSettings(cliParams, passthroughParams))
        .build();
  }

  private static STOYamlLog createLogSettings(STOYamlLogSerializer serializer, STOYamlLogLevel level) {
    ParameterField<STOYamlLogLevel> logLevel = ParameterField.createExpressionField(false, null, null, false);
    logLevel.updateWithValue(level.toString());
    return STOYamlLog.builder().serializer(serializer).level(logLevel).build();
  }

  private static STOYamlArgs createArgsSettings(String cliParams, String passthroughParams) {
    return STOYamlArgs.builder()
        .cli(ParameterField.createValueField(cliParams))
        .passthrough(ParameterField.createValueField(passthroughParams))
        .build();
  }

  private static STOYamlTarget createTarget(STOYamlTargetType type, String name, String variant, String workspace) {
    return STOYamlTarget.builder()
        .type(type)
        .variant(ParameterField.createValueField(variant))
        .name(ParameterField.createValueField(name))
        .workspace(ParameterField.createValueField(workspace))
        .build();
  }

  private static Map<String, String> getExpectedValue(String fileName) throws IOException {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    final URL testFile = classLoader.getResource(fileName);
    String json = Resources.toString(testFile, Charsets.UTF_8);
    JsonNode jsonNode = JsonUtils.asObject(json, JsonNode.class);
    ObjectMapper mapper = new ObjectMapper();
    return mapper.convertValue(jsonNode, new TypeReference<Map<String, String>>() {});
  }

  private static void assertEnvVariables(STOGenericStepInfo step, Map<String, String> expected) {
    Map<String, String> actual = getSTOPluginEnvVariables(step, "1");

    MapDifference<String, String> difference = Maps.difference(actual, expected);
    if (!difference.entriesOnlyOnLeft().isEmpty()) {
      log.error("Diff keys in actual: {}", difference.entriesOnlyOnLeft());
    }
    if (!difference.entriesOnlyOnRight().isEmpty()) {
      log.error("Diff keys in expected: {}", difference.entriesOnlyOnRight());
    }
    if (!difference.entriesDiffering().isEmpty()) {
      log.error("Diff values: {}", difference.entriesDiffering());
    }
    assertTrue(difference.entriesOnlyOnLeft().isEmpty());
    assertTrue(difference.entriesOnlyOnRight().isEmpty());
    assertTrue(difference.entriesDiffering().isEmpty());
  }
}
