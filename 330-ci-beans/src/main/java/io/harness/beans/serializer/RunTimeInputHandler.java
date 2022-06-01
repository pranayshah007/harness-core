/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.serializer;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.common.NGExpressionUtils.matchesInputSetPattern;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.yaml.YamlNode.UUID_FIELD_NAME;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.ArchiveFormat;
import io.harness.beans.yaml.extended.CIShellType;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.beans.yaml.extended.TIBuildTool;
import io.harness.beans.yaml.extended.TIDotNetBuildEnvName;
import io.harness.beans.yaml.extended.TIDotNetVersion;
import io.harness.beans.yaml.extended.TILanguage;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.k8.Toleration;
import io.harness.encryption.SecretRefData;
import io.harness.exception.ngexception.CIStageExecutionUserException;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.extended.ci.codebase.Build;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@OwnedBy(CI)
public class RunTimeInputHandler {
  public static String UNRESOLVED_PARAMETER = "UNRESOLVED_PARAMETER";
  public boolean resolveGitClone(ParameterField<Boolean> cloneRepository) {
    if (cloneRepository == null || cloneRepository.isExpression() || cloneRepository.getValue() == null) {
      return true;
    } else {
      return (boolean) cloneRepository.fetchFinalValue();
    }
  }

  public Build resolveBuild(ParameterField<Build> buildDetails) {
    if (buildDetails == null || buildDetails.isExpression() || buildDetails.getValue() == null) {
      return null;
    } else {
      return buildDetails.getValue();
    }
  }

  public List<Toleration> resolveTolerations(ParameterField<List<Toleration>> tolerations) {
    if (tolerations == null || tolerations.isExpression() || tolerations.getValue() == null) {
      return null;
    } else {
      return tolerations.getValue();
    }
  }

  public ArchiveFormat resolveArchiveFormat(ParameterField<ArchiveFormat> archiveFormat) {
    if (archiveFormat == null || archiveFormat.isExpression() || archiveFormat.getValue() == null) {
      return ArchiveFormat.TAR;
    } else {
      return ArchiveFormat.fromString(archiveFormat.fetchFinalValue().toString());
    }
  }

  public CIShellType resolveShellType(ParameterField<CIShellType> shellType) {
    if (shellType == null || shellType.isExpression() || shellType.getValue() == null) {
      return CIShellType.SH;
    } else {
      return CIShellType.fromString(shellType.fetchFinalValue().toString());
    }
  }

  public OSType resolveOSType(ParameterField<OSType> osType) {
    if (osType == null || osType.isExpression() || osType.getValue() == null) {
      return OSType.Linux;
    } else {
      return OSType.fromString(osType.fetchFinalValue().toString());
    }
  }

  public String resolveImagePullPolicy(ParameterField<ImagePullPolicy> pullPolicy) {
    if (pullPolicy == null || pullPolicy.isExpression() || pullPolicy.getValue() == null) {
      return null;
    } else {
      return ImagePullPolicy.fromString(pullPolicy.fetchFinalValue().toString()).getYamlName();
    }
  }

  public String resolveBuildTool(ParameterField<TIBuildTool> buildTool) {
    if (buildTool == null || buildTool.isExpression() || buildTool.getValue() == null) {
      return null;
    } else {
      return TIBuildTool.fromString(buildTool.fetchFinalValue().toString()).getYamlName();
    }
  }

  public String resolveLanguage(ParameterField<TILanguage> language) {
    if (language == null || language.isExpression() || language.getValue() == null) {
      return null;
    } else {
      return TILanguage.fromString(language.fetchFinalValue().toString()).getYamlName();
    }
  }

  public String resolveDotNetBuildEnvName(ParameterField<TIDotNetBuildEnvName> buildEnv) {
    if (buildEnv == null || buildEnv.isExpression() || buildEnv.getValue() == null) {
      return null;
    } else {
      return TIDotNetBuildEnvName.fromString(buildEnv.fetchFinalValue().toString()).getYamlName();
    }
  }

  public String resolveDotNetVersion(ParameterField<TIDotNetVersion> version) {
    if (version == null || version.isExpression() || version.getValue() == null) {
      return null;
    } else {
      return TIDotNetVersion.fromString(version.fetchFinalValue().toString()).getYamlName();
    }
  }

  public boolean resolveBooleanParameter(ParameterField<Boolean> booleanParameterField, Boolean defaultValue) {
    if (booleanParameterField == null || booleanParameterField.isExpression()
        || booleanParameterField.getValue() == null) {
      if (defaultValue != null) {
        return defaultValue;
      } else {
        return false;
      }
    } else {
      return (boolean) booleanParameterField.fetchFinalValue();
    }
  }

  public Integer resolveIntegerParameter(ParameterField<Integer> parameterField, Integer defaultValue) {
    if (parameterField == null || parameterField.isExpression() || parameterField.getValue() == null) {
      return defaultValue;
    } else {
      try {
        return Integer.parseInt(parameterField.fetchFinalValue().toString());
      } catch (Exception exception) {
        throw new CIStageExecutionUserException(
            format("Invalid value %s, Value should be number", parameterField.fetchFinalValue().toString()));
      }
    }
  }

  public String resolveStringParameter(String fieldName, String stepType, String stepIdentifier,
      ParameterField<String> parameterField, boolean isMandatory) {
    if (parameterField == null) {
      if (isMandatory) {
        throw new CIStageExecutionUserException(
            format("Failed to resolve mandatory field %s in step type %s with identifier %s", fieldName, stepType,
                stepIdentifier));
      } else {
        return null;
      }
    }

    // It only checks input set pattern. Variable can be resolved on lite engine.
    if (parameterField.isExpression() && matchesInputSetPattern(parameterField.getExpressionValue())) {
      if (isMandatory) {
        throw new CIStageExecutionUserException(
            format("Failed to resolve mandatory field %s in step type %s with identifier %s", fieldName, stepType,
                stepIdentifier));
      } else {
        log.warn(format("Failed to resolve optional field %s in step type %s with identifier %s", fieldName, stepType,
            stepIdentifier));
        return null;
      }
    }

    return (String) parameterField.fetchFinalValue();
  }

  public SecretRefData resolveSecretRefWithDefaultValue(String fieldName, String stepType, String stepIdentifier,
      ParameterField<SecretRefData> parameterField, boolean isMandatory) {
    if (parameterField == null || parameterField.getValue() == null) {
      if (isMandatory) {
        throw new CIStageExecutionUserException(
            format("Failed to resolve mandatory field %s in step type %s with identifier %s", fieldName, stepType,
                stepIdentifier));
      } else {
        return null;
      }
    }

    return parameterField.getValue();
  }

  public String resolveStringParameterWithDefaultValue(String fieldName, String stepType, String stepIdentifier,
      ParameterField<String> parameterField, boolean isMandatory, String defaultValue) {
    if (parameterField == null) {
      if (isMandatory && isEmpty(defaultValue)) {
        throw new CIStageExecutionUserException(
            format("Failed to resolve mandatory field %s in step type %s with identifier %s", fieldName, stepType,
                stepIdentifier));
      } else {
        if (defaultValue != null) {
          return defaultValue;
        }
        return "";
      }
    }

    // It only checks input set pattern. Variable can be resolved on lite engine.
    if (parameterField.isExpression() && matchesInputSetPattern(parameterField.getExpressionValue())) {
      if (isMandatory && isEmpty(defaultValue)) {
        throw new CIStageExecutionUserException(
            format("Failed to resolve mandatory field %s in step type %s with identifier %s", fieldName, stepType,
                stepIdentifier));
      } else {
        log.warn(format("Failed to resolve optional field %s in step type %s with identifier %s", fieldName, stepType,
            stepIdentifier));
        return defaultValue;
      }
    }

    return (String) parameterField.fetchFinalValue();
  }

  public Map<String, String> resolveMapParameter(String fieldName, String stepType, String stepIdentifier,
      ParameterField<Map<String, String>> parameterField, boolean isMandatory) {
    if (parameterField == null || parameterField.getValue() == null) {
      if (isMandatory) {
        throw new CIStageExecutionUserException(
            format("Failed to resolve mandatory field %s in step type %s with identifier %s", fieldName, stepType,
                stepIdentifier));
      } else {
        return null;
      }
    }

    if (parameterField.isExpression()) {
      if (isMandatory) {
        throw new CIStageExecutionUserException(
            format("Failed to resolve mandatory field %s in step type %s with identifier %s", fieldName, stepType,
                stepIdentifier));
      } else {
        log.warn(format("Failed to resolve optional field %s in step type %s with identifier %s", fieldName, stepType,
            stepIdentifier));
        return null;
      }
    }

    Map<String, String> m = parameterField.getValue();
    if (isNotEmpty(m)) {
      m.remove(UUID_FIELD_NAME);
    }
    return m;
  }

  public Map<String, JsonNode> resolveJsonNodeMapParameter(String fieldName, String stepType, String stepIdentifier,
      ParameterField<Map<String, JsonNode>> parameterField, boolean isMandatory) {
    if (parameterField == null || parameterField.getValue() == null) {
      if (isMandatory) {
        throw new CIStageExecutionUserException(
            format("Failed to resolve mandatory field %s in step type %s with identifier %s", fieldName, stepType,
                stepIdentifier));
      } else {
        return null;
      }
    }

    if (parameterField.isExpression()) {
      if (isMandatory) {
        throw new CIStageExecutionUserException(
            format("Failed to resolve mandatory field %s in step type %s with identifier %s", fieldName, stepType,
                stepIdentifier));
      } else {
        log.warn(format("Failed to resolve optional field %s in step type %s with identifier %s", fieldName, stepType,
            stepIdentifier));
        return null;
      }
    }

    Map<String, JsonNode> m = parameterField.getValue();
    if (isNotEmpty(m)) {
      m.remove(UUID_FIELD_NAME);
    }
    return m;
  }

  public List<String> resolveListParameter(String fieldName, String stepType, String stepIdentifier,
      ParameterField<List<String>> parameterField, boolean isMandatory) {
    if (parameterField == null || parameterField.getValue() == null) {
      if (isMandatory) {
        throw new CIStageExecutionUserException(
            format("Failed to resolve mandatory field %s in step type %s with identifier %s", fieldName, stepType,
                stepIdentifier));
      }
      return null;
    }

    if (parameterField.isExpression()) {
      if (isMandatory) {
        throw new CIStageExecutionUserException(
            format("Failed to resolve mandatory field %s in step type %s with identifier %s", fieldName, stepType,
                stepIdentifier));
      } else {
        log.warn(format("Failed to resolve optional field %s in step type %s with identifier %s", fieldName, stepType,
            stepIdentifier));
        return new ArrayList<>();
      }
    }

    return parameterField.getValue();
  }

  public <T> List<T> resolveGenericListParameter(String fieldName, String stepType, String stepIdentifier,
      ParameterField<List<T>> parameterField, boolean isMandatory) {
    if (parameterField == null || parameterField.getValue() == null) {
      if (isMandatory) {
        throw new CIStageExecutionUserException(
            format("Failed to resolve mandatory field %s in step type %s with identifier %s", fieldName, stepType,
                stepIdentifier));
      }
    }

    if (parameterField.isExpression()) {
      if (isMandatory) {
        throw new CIStageExecutionUserException(
            format("Failed to resolve mandatory field %s in step type %s with identifier %s", fieldName, stepType,
                stepIdentifier));
      } else {
        log.warn(format("Failed to resolve optional field %s in step type %s with identifier %s", fieldName, stepType,
            stepIdentifier));
        return new ArrayList<>();
      }
    }

    return parameterField.getValue();
  }
}
