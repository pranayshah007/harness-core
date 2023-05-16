/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.serializer;

import static java.lang.String.format;

import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.yaml.extended.CIShellType;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SerializerUtils {
  private static Pattern pattern = Pattern.compile("\\$\\{ngSecretManager\\.obtain[^\\}]*\\}");

  public static List<String> getEntrypoint(ParameterField<CIShellType> parametrizedShellType) {
    List<String> entrypoint;
    CIShellType shellType = RunTimeInputHandler.resolveShellType(parametrizedShellType);
    if (shellType == CIShellType.SH) {
      entrypoint = Arrays.asList("sh", "-c");
    } else if (shellType == CIShellType.BASH) {
      entrypoint = Arrays.asList("bash", "-c");
    } else if (shellType == CIShellType.POWERSHELL) {
      entrypoint = Arrays.asList("powershell", "-Command");
    } else if (shellType == CIShellType.PWSH) {
      entrypoint = Arrays.asList("pwsh", "-Command");
    } else if (shellType == CIShellType.PYTHON) {
      entrypoint = Arrays.asList("python3", "-c");
    } else {
      throw new CIStageExecutionException(format("Invalid shell type: %s", shellType));
    }
    return entrypoint;
  }

  public static String getK8sDebugCommand(String accountId, int timeoutSeconds) {
    return String.format("remote_debug() %n  { %n  if [ "
            + " \"$?\" -ne \"0\" ]; then %n"
            + " %n echo \"set -g tmate-server-host ssh.harness.io\"  >> tmate.conf;"
            + " %n echo \"set -g tmate-server-port 22\" >>  tmate.conf ;"
            + " %n echo \"set -g tmate-server-rsa-fingerprint SHA256:qipNUtbscEcff+dGOs5cChUigjwN1nAmsx48Em/uBgo\"  >>  tmate.conf ;"
            + " %n echo \"set -g tmate-server-ed25519-fingerprint SHA256:eGCUzSOn6vtcPVVNEGWis7G4cVBUiI/ZWAw+SrptaNg\" >>  tmate.conf ;"
            + " %n echo \"set -g tmate-user %s\" >>  tmate.conf ;"
            + " timeout " + Integer.toString(timeoutSeconds) + "s /addon/bin/tmate -f tmate.conf -F;  "
            + " %n fi %n } %n trap remote_debug EXIT",
        accountId);
  }

  public static String getVmDebugCommand(String accountId, int timeoutSeconds) {
    return String.format("remote_debug() %n  { %n  if [ "
            + " \"$?\" -ne \"0\" ]; then %n"
            + " %n echo \"set -g tmate-server-host ssh.harness.io\"  >> tmate.conf;"
            + " %n echo \"set -g tmate-server-port 22\" >>  tmate.conf ;"
            + " %n echo \"set -g tmate-server-rsa-fingerprint SHA256:qipNUtbscEcff+dGOs5cChUigjwN1nAmsx48Em/uBgo\"  >>  tmate.conf ;"
            + " %n echo \"set -g tmate-server-ed25519-fingerprint SHA256:eGCUzSOn6vtcPVVNEGWis7G4cVBUiI/ZWAw+SrptaNg\" >>  tmate.conf ;"
            + " %n echo \"set -g tmate-user %s\" >>  tmate.conf ;"
            + "timeout " + Integer.toString(timeoutSeconds) + "s  /addon/tmate -f tmate.conf -F; "
            + " %n fi %n } %n trap remote_debug EXIT",
        accountId);
  }

  public static String getEarlyExitCommand(ParameterField<CIShellType> parametrizedShellType) {
    String cmd;
    CIShellType shellType = RunTimeInputHandler.resolveShellType(parametrizedShellType);
    if (shellType == CIShellType.SH || shellType == CIShellType.BASH) {
      cmd = "set -xe; ";
    } else if (shellType == CIShellType.POWERSHELL || shellType == CIShellType.PWSH) {
      cmd = "$ErrorActionPreference = 'Stop' \n";
    } else if (shellType == CIShellType.PYTHON) {
      cmd = "";
    } else {
      throw new CIStageExecutionException(format("Invalid shell type: %s", shellType));
    }
    return cmd;
  }

  public static String convertJsonNodeToString(String key, JsonNode jsonNode) {
    try {
      YamlUtils.removeUuid(jsonNode);
      if (jsonNode.isValueNode()) {
        return jsonNode.asText("");
      } else if (jsonNode.isArray() && isPrimitiveArray(jsonNode)) {
        ArrayNode arrayNode = (ArrayNode) jsonNode;
        List<String> strValues = new ArrayList<>();
        for (JsonNode node : arrayNode) {
          strValues.add(node.asText(""));
        }

        return String.join(",", strValues);
      } else {
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        return ow.writeValueAsString(jsonNode);
      }
    } catch (Exception ex) {
      throw new CIStageExecutionException(String.format("Invalid setting attribute %s value", key));
    }
  }

  public static String convertMapToJsonString(Map<String, String> m) {
    Map<String, String> o = new HashMap<>();
    for (Map.Entry<String, String> entry : m.entrySet()) {
      o.put(entry.getKey(), replaceDoubleQuoteWithSingleInSecretResolver(entry.getValue()));
    }

    try {
      ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
      return ow.writeValueAsString(o);
    } catch (Exception ex) {
      throw new CIStageExecutionException(String.format("Invalid setting %s", m));
    }
  }

  private static String replaceDoubleQuoteWithSingleInSecretResolver(String input) {
    Matcher matcher = pattern.matcher(input);

    String out = input;
    int count = 0;
    while (matcher.find() && count < 50) {
      String match = matcher.group();
      String replacedVal = match.replace("\"", "'");
      out = out.replace(match, replacedVal);
      count++;
    }
    return out;
  }

  // Return whether array contains only value node or not.
  private static boolean isPrimitiveArray(JsonNode jsonNode) {
    ArrayNode arrayNode = (ArrayNode) jsonNode;
    for (JsonNode e : arrayNode) {
      if (!e.isValueNode()) {
        return false;
      }
    }
    return true;
  }

  public static String getSafeGitDirectoryCmd(
      CIShellType shellType, String accountId, CIFeatureFlagService featureFlagService) {
    // This adds the safe directory to the end of .gitconfig file

    String safeDirScript;
    if (shellType == CIShellType.SH || shellType == CIShellType.BASH) {
      safeDirScript = "set +x\n"
          + "if [ -x \"$(command -v git)\" ]; then\n"
          + "  git config --global --add safe.directory '*' || true \n"
          + "fi\n"
          + "set -x\n";
    } else if (shellType == CIShellType.PYTHON) {
      safeDirScript = "import subprocess\n"
          + "try:\n"
          + "\tsubprocess.run(['git', 'config', '--global', '--add', 'safe.directory', '*'])\n"
          + "except:\n"
          + "\tpass\n";
    } else {
      safeDirScript = "try\n"
          + "{\n"
          + "    git config --global --add safe.directory '*' | Out-Null\n"
          + "}\n"
          + "catch [System.Management.Automation.CommandNotFoundException]\n"
          + "{\n }\n";
    }
    return safeDirScript;
  }

  public static String getTestSplitStrategy(String splitStrategy) {
    switch (splitStrategy) {
      case "TestCount":
        return "test_count";
      case "ClassTiming":
        return "class_timing";
      default:
        return "";
    }
  }

  public static Map<String, String> getPortBindingMap(List<String> ports) {
    if (EmptyPredicate.isEmpty(ports)) {
      return Collections.emptyMap();
    }
    Map<String, String> portMapping = new HashMap<>();
    ports.forEach(p -> {
      if (EmptyPredicate.isEmpty(p)) {
        throw new CIStageExecutionException("Port value cannot be empty");
      }
      String[] portList = p.split(":");
      if (EmptyPredicate.isEmpty(portList) || portList.length < 2) {
        throw new CIStageExecutionException(format("Port mapping is invalid: %s", p));
      }
      portMapping.put(portList[portList.length - 2], portList[portList.length - 1]);
    });
    return portMapping;
  }

  public static ParameterField<Boolean> getBooleanFieldFromJsonNodeMap(Map<String, JsonNode> map, String key) {
    if (EmptyPredicate.isEmpty(map)) {
      return ParameterField.ofNull();
    }
    JsonNode booleanJsonNode = map.get(key);
    if (booleanJsonNode != null) {
      if (booleanJsonNode.isTextual() && NGExpressionUtils.isExpressionField(booleanJsonNode.asText())) {
        return ParameterField.createExpressionField(true, booleanJsonNode.asText(), null, false);
      } else if (booleanJsonNode.isBoolean()) {
        return ParameterField.createValueField(booleanJsonNode.asBoolean());
      }
    }
    return ParameterField.ofNull();
  }

  public static ParameterField<String> getStringFieldFromJsonNodeMap(Map<String, JsonNode> map, String key) {
    if (EmptyPredicate.isEmpty(map)) {
      return ParameterField.ofNull();
    }
    JsonNode stringJsonNode = map.get(key);
    if (stringJsonNode != null && stringJsonNode.isTextual()) {
      if (NGExpressionUtils.isExpressionField(stringJsonNode.asText())) {
        return ParameterField.createExpressionField(true, stringJsonNode.asText(), null, true);
      }
      return ParameterField.createValueField(stringJsonNode.asText());
    }
    return ParameterField.ofNull();
  }

  public static ParameterField<String> getListAsStringFromJsonNodeMap(Map<String, JsonNode> map, String key) {
    if (EmptyPredicate.isEmpty(map)) {
      return ParameterField.ofNull();
    }
    JsonNode arrayJsonNode = map.get(key);
    if (arrayJsonNode != null && arrayJsonNode.isArray()) {
      List<String> list = new ArrayList<>();
      Iterator<JsonNode> elements = arrayJsonNode.elements();
      while (elements.hasNext()) {
        JsonNode element = elements.next();
        list.add(element.asText());
      }
      String finalString = String.join(",", list);
      if (NGExpressionUtils.isExpressionField(finalString)) {
        return ParameterField.createExpressionField(true, finalString, null, true);
      }
      return ParameterField.createValueField(finalString);
    }
    return ParameterField.ofNull();
  }
}