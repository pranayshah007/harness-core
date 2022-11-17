/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.rule.OwnerRule.ASHISHSANODIA;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class EntityTypeTest extends WingsBaseTest {
  private Map<Integer, String> entityTypeOrdinalMapping;
  private Map<String, Integer> entityTypeConstantMapping;

  @Before
  public void setUp() {
    entityTypeOrdinalMapping = new HashMap<>();
    entityTypeOrdinalMapping.put(0, "SERVICE");
    entityTypeOrdinalMapping.put(1, "PROVISIONER");
    entityTypeOrdinalMapping.put(2, "ENVIRONMENT");
    entityTypeOrdinalMapping.put(3, "HOST");
    entityTypeOrdinalMapping.put(4, "RELEASE");
    entityTypeOrdinalMapping.put(5, "ARTIFACT");
    entityTypeOrdinalMapping.put(6, "SSH_USER");
    entityTypeOrdinalMapping.put(7, "SSH_PASSWORD");
    entityTypeOrdinalMapping.put(8, "SSH_APP_ACCOUNT");
    entityTypeOrdinalMapping.put(9, "SSH_KEY_PASSPHRASE");
    entityTypeOrdinalMapping.put(10, "SSH_APP_ACCOUNT_PASSOWRD");
    entityTypeOrdinalMapping.put(11, "SIMPLE_DEPLOYMENT");
    entityTypeOrdinalMapping.put(12, "ORCHESTRATED_DEPLOYMENT");
    entityTypeOrdinalMapping.put(13, "PIPELINE");
    entityTypeOrdinalMapping.put(14, "WORKFLOW");
    entityTypeOrdinalMapping.put(15, "DEPLOYMENT");
    entityTypeOrdinalMapping.put(16, "INSTANCE");
    entityTypeOrdinalMapping.put(17, "APPLICATION");
    entityTypeOrdinalMapping.put(18, "COMMAND");
    entityTypeOrdinalMapping.put(19, "CONFIG");
    entityTypeOrdinalMapping.put(20, "SERVICE_TEMPLATE");
    entityTypeOrdinalMapping.put(21, "INFRASTRUCTURE_MAPPING");
    entityTypeOrdinalMapping.put(22, "INFRASTRUCTURE_DEFINITION");
    entityTypeOrdinalMapping.put(23, "USER");
    entityTypeOrdinalMapping.put(24, "ARTIFACT_STREAM");
    entityTypeOrdinalMapping.put(25, "APPDYNAMICS_CONFIGID");
    entityTypeOrdinalMapping.put(26, "APPDYNAMICS_APPID");
    entityTypeOrdinalMapping.put(27, "APPDYNAMICS_TIERID");
    entityTypeOrdinalMapping.put(28, "ELK_CONFIGID");
    entityTypeOrdinalMapping.put(29, "ELK_INDICES");
    entityTypeOrdinalMapping.put(30, "NEWRELIC_CONFIGID");
    entityTypeOrdinalMapping.put(31, "NEWRELIC_APPID");
    entityTypeOrdinalMapping.put(32, "SS_SSH_CONNECTION_ATTRIBUTE");
    entityTypeOrdinalMapping.put(33, "SS_WINRM_CONNECTION_ATTRIBUTE");
    entityTypeOrdinalMapping.put(34, "SUMOLOGIC_CONFIGID");
    entityTypeOrdinalMapping.put(35, "SPLUNK_CONFIGID");
    entityTypeOrdinalMapping.put(36, "NEWRELIC_MARKER_CONFIGID");
    entityTypeOrdinalMapping.put(37, "NEWRELIC_MARKER_APPID");
    entityTypeOrdinalMapping.put(38, "API_KEY");
    entityTypeOrdinalMapping.put(39, "ACCOUNT");
    entityTypeOrdinalMapping.put(40, "APPLICATION_MANIFEST");
    entityTypeOrdinalMapping.put(41, "USER_GROUP");
    entityTypeOrdinalMapping.put(42, "WHITELISTED_IP");
    entityTypeOrdinalMapping.put(43, "CF_AWS_CONFIG_ID");
    entityTypeOrdinalMapping.put(44, "VERIFICATION_CONFIGURATION");
    entityTypeOrdinalMapping.put(45, "HELM_GIT_CONFIG_ID");
    entityTypeOrdinalMapping.put(46, "NOTIFICATION_GROUP");
    entityTypeOrdinalMapping.put(47, "HELM_CHART_SPECIFICATION");
    entityTypeOrdinalMapping.put(48, "PCF_SERVICE_SPECIFICATION");
    entityTypeOrdinalMapping.put(49, "LAMBDA_SPECIFICATION");
    entityTypeOrdinalMapping.put(50, "USER_DATA_SPECIFICATION");
    entityTypeOrdinalMapping.put(51, "ECS_CONTAINER_SPECIFICATION");
    entityTypeOrdinalMapping.put(52, "ECS_SERVICE_SPECIFICATION");
    entityTypeOrdinalMapping.put(53, "K8S_CONTAINER_SPECIFICATION");
    entityTypeOrdinalMapping.put(54, "CONFIG_FILE");
    entityTypeOrdinalMapping.put(55, "SERVICE_COMMAND");
    entityTypeOrdinalMapping.put(56, "MANIFEST_FILE");
    entityTypeOrdinalMapping.put(57, "SERVICE_VARIABLE");
    entityTypeOrdinalMapping.put(58, "TRIGGER");
    entityTypeOrdinalMapping.put(59, "ROLE");
    entityTypeOrdinalMapping.put(60, "TEMPLATE");
    entityTypeOrdinalMapping.put(61, "TEMPLATE_FOLDER");
    entityTypeOrdinalMapping.put(62, "SETTING_ATTRIBUTE");
    entityTypeOrdinalMapping.put(63, "ENCRYPTED_RECORDS");
    entityTypeOrdinalMapping.put(64, "CV_CONFIGURATION");
    entityTypeOrdinalMapping.put(65, "TAG");
    entityTypeOrdinalMapping.put(66, "CUSTOM_DASHBOARD");
    entityTypeOrdinalMapping.put(67, "PIPELINE_GOVERNANCE_STANDARD");
    entityTypeOrdinalMapping.put(68, "WORKFLOW_EXECUTION");
    entityTypeOrdinalMapping.put(69, "SERVERLESS_INSTANCE");
    entityTypeOrdinalMapping.put(70, "USER_INVITE");
    entityTypeOrdinalMapping.put(71, "LOGIN_SETTINGS");
    entityTypeOrdinalMapping.put(72, "SSO_SETTINGS");
    entityTypeOrdinalMapping.put(73, "DELEGATE");
    entityTypeOrdinalMapping.put(74, "DELEGATE_SCOPE");
    entityTypeOrdinalMapping.put(75, "DELEGATE_PROFILE");
    entityTypeOrdinalMapping.put(76, "EXPORT_EXECUTIONS_REQUEST");
    entityTypeOrdinalMapping.put(77, "GCP_CONFIG");
    entityTypeOrdinalMapping.put(78, "GIT_CONFIG");
    entityTypeOrdinalMapping.put(79, "JENKINS_SERVER");
    entityTypeOrdinalMapping.put(80, "SECRETS_MANAGER");
    entityTypeOrdinalMapping.put(81, "HELM_CHART");
    entityTypeOrdinalMapping.put(82, "SECRET");
    entityTypeOrdinalMapping.put(83, "CONNECTOR");
    entityTypeOrdinalMapping.put(84, "CLOUD_PROVIDER");
    entityTypeOrdinalMapping.put(85, "GOVERNANCE_FREEZE_CONFIG");
    entityTypeOrdinalMapping.put(86, "GOVERNANCE_CONFIG");
    entityTypeOrdinalMapping.put(87, "EVENT_RULE");

    entityTypeConstantMapping =
        entityTypeOrdinalMapping.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testMappingExistsForAllEnumConstants() {
    Arrays.stream(EntityType.values()).forEach(taskType -> {
      if (!taskType.name().equals(entityTypeOrdinalMapping.get(taskType.ordinal()))) {
        Assertions.fail(String.format("Not all constants from enum [%s] mapped in test [%s].",
            EntityType.class.getCanonicalName(), this.getClass().getCanonicalName()));
      }
    });
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testEnumConstantAddedAtTheEndWithoutMapping() {
    if (EntityType.values().length > entityTypeOrdinalMapping.size()) {
      Arrays.stream(EntityType.values()).forEach(taskType -> {
        if (!taskType.name().equals(entityTypeOrdinalMapping.get(taskType.ordinal()))
            && !entityTypeConstantMapping.containsKey(taskType.name())
            && taskType.ordinal() >= entityTypeOrdinalMapping.size()) {
          Assertions.fail(String.format(
              "New constant added at the end of Enum [%s] at ordinal [%s] with name [%s]. This is expected for Kryo serialization/deserialization to work in the backward compatible manner. Please add this new enum constant mapping in test [%s].",
              EntityType.class.getCanonicalName(), taskType.ordinal(), taskType.name(),
              this.getClass().getCanonicalName()));
        }
      });
    }
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testEnumConstantNotAddedInBetween() {
    if (entityTypeOrdinalMapping.size() < EntityType.values().length) {
      Arrays.stream(EntityType.values()).forEach(taskType -> {
        if (!taskType.name().equals(entityTypeOrdinalMapping.get(taskType.ordinal()))
            && !entityTypeConstantMapping.containsKey(taskType.name())
            && taskType.ordinal() < entityTypeOrdinalMapping.size()) {
          Assertions.fail(String.format(
              "New constant added in Enum [%s] at ordinal [%s] with name [%s]. You have to add constant at the end for Kryo serialization/deserialization to work in the backward compatible manner.",
              EntityType.class.getCanonicalName(), taskType.ordinal(), taskType.name()));
        }
      });
    }
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testEnumConstantNotDeleted() {
    if (entityTypeOrdinalMapping.size() > EntityType.values().length) {
      Arrays.stream(EntityType.values()).forEach(taskType -> {
        if (!taskType.name().equals(entityTypeOrdinalMapping.get(taskType.ordinal()))
            && entityTypeConstantMapping.containsKey(taskType.name())) {
          Assertions.fail(String.format(
              "Constant deleted from Enum [%s] at ordinal [%s] with name [%s]. You should not delete constant for Kryo serialization/deserialization to work in the backward compatible manner.",
              EntityType.class.getCanonicalName(), taskType.ordinal(), taskType.name()));
        }
      });
    }
  }
}
