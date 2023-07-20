/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.sto.utils;

import static io.harness.common.NGExpressionUtils.matchesInputSetPattern;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.security.AquaTrivyStepInfo;
import io.harness.beans.steps.stepinfo.security.AwsEcrStepInfo;
import io.harness.beans.steps.stepinfo.security.AwsSecurityHubStepInfo;
import io.harness.beans.steps.stepinfo.security.BlackDuckStepInfo;
import io.harness.beans.steps.stepinfo.security.BurpStepInfo;
import io.harness.beans.steps.stepinfo.security.CheckmarxStepInfo;
import io.harness.beans.steps.stepinfo.security.CustomIngestStepInfo;
import io.harness.beans.steps.stepinfo.security.FortifyOnDemandStepInfo;
import io.harness.beans.steps.stepinfo.security.FossaStepInfo;
import io.harness.beans.steps.stepinfo.security.GrypeStepInfo;
import io.harness.beans.steps.stepinfo.security.MendStepInfo;
import io.harness.beans.steps.stepinfo.security.MetasploitStepInfo;
import io.harness.beans.steps.stepinfo.security.NiktoStepInfo;
import io.harness.beans.steps.stepinfo.security.NmapStepInfo;
import io.harness.beans.steps.stepinfo.security.PrismaCloudStepInfo;
import io.harness.beans.steps.stepinfo.security.ProwlerStepInfo;
import io.harness.beans.steps.stepinfo.security.SemgrepStepInfo;
import io.harness.beans.steps.stepinfo.security.SnykStepInfo;
import io.harness.beans.steps.stepinfo.security.SonarqubeStepInfo;
import io.harness.beans.steps.stepinfo.security.SysdigStepInfo;
import io.harness.beans.steps.stepinfo.security.VeracodeStepInfo;
import io.harness.beans.steps.stepinfo.security.ZapStepInfo;
import io.harness.beans.steps.stepinfo.security.shared.STOGenericStepInfo;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlAdvancedSettings;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlArgs;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlAuth;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlBlackduckToolData;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlBurpToolData;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlCheckmarxToolData;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlFODToolData;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlFossaToolData;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlImage;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlIngestion;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlInstance;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlJavaParameters;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlLog;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlMendToolData;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlPrismaCloudToolData;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlSBOM;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlSonarqubeToolData;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlTarget;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlVeracodeToolData;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlZapToolData;
import io.harness.exception.ngexception.CIStageExecutionUserException;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.sto.variables.STOYamlAuthType;
import io.harness.yaml.sto.variables.STOYamlFailOnSeverity;
import io.harness.yaml.sto.variables.STOYamlGenericConfig;
import io.harness.yaml.sto.variables.STOYamlImageType;
import io.harness.yaml.sto.variables.STOYamlLogLevel;
import io.harness.yaml.sto.variables.STOYamlLogSerializer;
import io.harness.yaml.sto.variables.STOYamlSBOMFormat;
import io.harness.yaml.sto.variables.STOYamlScanMode;
import io.harness.yaml.sto.variables.STOYamlTargetType;

import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.STO)
@Singleton
@Slf4j
public final class STOSettingsUtils {
  public static final String SECURITY_ENV_PREFIX = "SECURITY_";
  public static final String PRODUCT_PROJECT_VERSION = "product_project_version";

  public static final String AWS_ACCOUNT = "aws_account";
  public static final String CONFIGURATION_TYPE = "configuration_type";
  public static final String PRODUCT_PROJECT_KEY = "product_project_key";
  public static final String PRODUCT_PROJECT_NAME = "product_project_name";
  public static final String PRODUCT_PROJECT_TOKEN = "product_project_token";
  public static final String PRODUCT_PRODUCT_NAME = "product_product_name";
  public static final String PRODUCT_TEAM_NAME = "product_team_name";
  public static final String PRODUCT_POLICY_NAME = "product_policy_name";
  public static final String PRODUCT_PRODUCT_TOKEN = "product_product_token";
  public static final String PRODUCT_EXCLUDE = "product_exclude";
  public static final String PRODUCT_INCLUDE = "product_include";
  public static final String PRODUCT_SCAN_ID = "product_scan_id";
  public static final String PRODUCT_SITE_ID = "product_site_id";
  public static final String PRODUCT_IMAGE_NAME = "product_image_name";

  public static final String TOOL_PROJECT_NAME = "tool.project_name";
  public static final String TOOL_PROJECT_KEY = "tool.project_key";
  public static final String TOOL_PROJECT_TOKEN = "tool.project_token";
  public static final String TOOL_PRODUCT_NAME = "tool.product_name";
  public static final String TOOL_PRODUCT_TOKEN = "tool.product_token";
  public static final String TOOL_TEAM_NAME = "tool.team_name";
  public static final String TOOL_POLICY_NAME = "tool.policy_name";
  public static final String TOOL_EXCLUDE = "tool.exclude";
  public static final String TOOL_INCLUDE = "tool.include";
  public static final String TOOL_SCAN_ID = "tool.scan_id";
  public static final String TOOL_SITE_ID = "tool.site_id";
  public static final String TOOL_IMAGE_NAME = "tool.image_name";

  private STOSettingsUtils() {
    throw new IllegalStateException("Utility class");
  }

  private static boolean resolveBooleanParameter(ParameterField<Boolean> booleanParameterField, Boolean defaultValue) {
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

  private static Integer resolveIntegerParameter(ParameterField<Integer> parameterField, Integer defaultValue) {
    if (parameterField == null || parameterField.isExpression() || parameterField.getValue() == null) {
      return defaultValue;
    } else {
      try {
        return Integer.parseInt(parameterField.fetchFinalValue().toString());
      } catch (Exception exception) {
        log.info("Handling exception: {}", exception.getMessage());
        throw new CIStageExecutionUserException(
            format("Invalid value %s, Value should be number", parameterField.fetchFinalValue().toString()));
      }
    }
  }

  private static String resolveStringParameter(String fieldName, String stepType, String stepIdentifier,
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
        return null;
      }
    }

    return (String) parameterField.fetchFinalValue();
  }

  public static String getSTOKey(String value) {
    return SECURITY_ENV_PREFIX + value.toUpperCase(Locale.ROOT);
  }

  private static Map<String, String> processSTOAuthFields(
      STOYamlAuth authData, STOYamlTarget target, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    if (authData != null) {
      STOYamlAuthType authType = authData.getType();

      map.put(getSTOKey("product_auth_type"),
          authType != null ? authType.getYamlName() : STOYamlAuthType.API_KEY.getYamlName());

      Boolean authSsl = resolveBooleanParameter(authData.getSsl(), Boolean.TRUE);

      String authFieldPrefix = "product";

      if (target != null && target.getType() == STOYamlTargetType.CONFIGURATION) {
        authFieldPrefix = "configuration";
      }

      map.put(getSTOKey("bypass_ssl_check"), String.valueOf(!authSsl));
      map.put(getSTOKey("product_domain"),
          resolveStringParameter("auth.domain", stepType, identifier, authData.getDomain(), false));
      map.put(getSTOKey("product_api_version"),
          resolveStringParameter("auth.version", stepType, identifier, authData.getVersion(), false));
      map.put(getSTOKey(authFieldPrefix + "_access_id"),
          resolveStringParameter("auth.accessId", stepType, identifier, authData.getAccessId(), false));
      map.put(getSTOKey(authFieldPrefix + "_access_token"),
          resolveStringParameter("auth.accessToken", stepType, identifier, authData.getAccessToken(), false));
      map.put(getSTOKey(authFieldPrefix + "_region"),
          resolveStringParameter("auth.region", stepType, identifier, authData.getRegion(), false));
    }

    return map;
  }

  private static Map<String, String> processSTOSBOMFields(STOYamlSBOM sbom) {
    Map<String, String> map = new HashMap<>();

    if (sbom != null) {
      Boolean sbomGenerate = resolveBooleanParameter(sbom.getGenerate(), Boolean.FALSE);
      map.put(getSTOKey("generate_sbom"), String.valueOf(sbomGenerate));

      ParameterField<STOYamlSBOMFormat> sbomFormat = sbom.getFormat();

      map.put(getSTOKey("sbom_type"),
          sbomFormat != null ? sbomFormat.fetchFinalValue().toString() : STOYamlSBOMFormat.SPDX.toString());
    }
    return map;
  }

  private static Map<String, String> processSTOImageFields(STOYamlImage imageData, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    if (imageData != null) {
      STOYamlImageType imageType = imageData.getType();

      map.put(getSTOKey("container_type"),
          imageType != null ? imageType.getYamlName() : STOYamlImageType.DOCKER_V2.getYamlName());
      map.put(getSTOKey("container_domain"),
          resolveStringParameter("image.domain", stepType, identifier, imageData.getDomain(), false));
      map.put(getSTOKey("container_region"),
          resolveStringParameter("image.region", stepType, identifier, imageData.getRegion(), false));
      map.put(getSTOKey("container_access_id"),
          resolveStringParameter("image.access_id", stepType, identifier, imageData.getAccessId(), false));
      map.put(getSTOKey("container_access_token"),
          resolveStringParameter("image.access_token", stepType, identifier, imageData.getAccessToken(), false));
      map.put(getSTOKey("container_project"),
          resolveStringParameter("image.name", stepType, identifier, imageData.getName(), false));
      map.put(getSTOKey("container_tag"),
          resolveStringParameter("image.tag", stepType, identifier, imageData.getTag(), false));
    }

    return map;
  }

  private static Map<String, String> processSTOInstanceFields(
      STOYamlInstance instanceData, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    if (instanceData != null) {
      map.put(getSTOKey("instance_domain"),
          resolveStringParameter("instance.domain", stepType, identifier, instanceData.getDomain(), false));
      map.put(getSTOKey("instance_path"),
          resolveStringParameter("instance.path", stepType, identifier, instanceData.getPath(), false));
      map.put(getSTOKey("instance_protocol"),
          resolveStringParameter("instance.protocol", stepType, identifier, instanceData.getProtocol(), false));
      map.put(getSTOKey("instance_username"),
          resolveStringParameter("instance.username", stepType, identifier, instanceData.getUsername(), false));
      map.put(getSTOKey("instance_password"),
          resolveStringParameter("instance.password", stepType, identifier, instanceData.getPassword(), false));

      Integer port = resolveIntegerParameter(instanceData.getPort(), null);
      if (port != null) {
        map.put(getSTOKey("instance_port"), String.valueOf(port));
      }
    }

    return map;
  }

  private static Map<String, String> processSTOTargetFields(STOYamlTarget target, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    if (target != null) {
      map.put(getSTOKey("workspace"),
          resolveStringParameter("target.workspace", stepType, identifier, target.getWorkspace(), false));
      STOYamlTargetType targetType = target.getType();
      map.put(getSTOKey("scan_type"),
          targetType != null ? targetType.getYamlName() : STOYamlTargetType.REPOSITORY.getYamlName());

      String targetName = resolveStringParameter("target.name", stepType, identifier, target.getName(), true);
      String targetVariant = resolveStringParameter("target.variant", stepType, identifier, target.getVariant(), true);

      map.put(getSTOKey("target_name"), targetName);
      map.put(getSTOKey("target_variant"), targetVariant);

      switch (target.getType()) {
        case INSTANCE:
          map.put(getSTOKey("instance_identifier"), targetName);
          map.put(getSTOKey("instance_environment"), targetVariant);
          break;
        case REPOSITORY:
          map.put(getSTOKey("repository_project"), targetName);
          map.put(getSTOKey("repository_branch"), targetVariant);
          break;
        case CONFIGURATION:
          map.put(getSTOKey("configuration_environment"), targetVariant);
          break;
        default:
          break;
      }
    }

    return map;
  }

  private static Map<String, String> processSTOAdvancedSettings(
      STOYamlAdvancedSettings advancedSettings, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    if (advancedSettings != null) {
      STOYamlLog logData = advancedSettings.getLog();
      if (logData != null) {
        STOYamlLogLevel logLevel = logData.getLevel();
        STOYamlLogSerializer logSerializer = logData.getSerializer();

        map.put(getSTOKey("log_level"), logLevel != null ? logLevel.getYamlName() : STOYamlLogLevel.INFO.getYamlName());
        map.put(getSTOKey("log_serializer"),
            logSerializer != null ? logSerializer.getYamlName() : STOYamlLogSerializer.SIMPLE_ONPREM.getYamlName());
      }

      STOYamlArgs argsData = advancedSettings.getArgs();
      if (argsData != null) {
        map.put(
            getSTOKey("tool_args"), resolveStringParameter("args.cli", stepType, identifier, argsData.getCli(), false));
        map.put(getSTOKey("tool_passthrough"),
            resolveStringParameter("args.passthrough", stepType, identifier, argsData.getPassthrough(), false));
      }

      STOYamlFailOnSeverity failOnSeverity = advancedSettings.getFailOnSeverity();

      map.put(getSTOKey("fail_on_severity"),
          failOnSeverity != null ? failOnSeverity.getYamlName() : STOYamlFailOnSeverity.NONE.getYamlName());
      map.put(getSTOKey("include_raw"),
          String.valueOf(resolveBooleanParameter(advancedSettings.getIncludeRaw(), Boolean.TRUE)));
    }

    return map;
  }

  private static Map<String, String> processSTOIngestionFields(
      STOYamlIngestion ingestion, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    if (ingestion != null) {
      map.put(getSTOKey("ingestion_file"),
          resolveStringParameter("ingestion.file", stepType, identifier, ingestion.getFile(), false));
    }

    return map;
  }

  private static Map<String, String> processSTOBlackDuckFields(
      BlackDuckStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOAuthFields(stepInfo.getAuth(), stepInfo.getTarget(), stepType, identifier));
    map.putAll(processSTOImageFields(stepInfo.getImage(), stepType, identifier));
    map.putAll(processSTOSBOMFields(stepInfo.getSbom()));

    STOYamlBlackduckToolData toolData = stepInfo.getTool();

    if (toolData != null) {
      map.put(getSTOKey(PRODUCT_PROJECT_NAME),
          resolveStringParameter(TOOL_PROJECT_NAME, stepType, identifier, toolData.getProjectName(), false));
      map.put(getSTOKey(PRODUCT_PROJECT_VERSION),
          resolveStringParameter("tool.project_version", stepType, identifier, toolData.getProjectVersion(), false));
    }

    return map;
  }

  private static Map<String, String> processSTOBurpFields(BurpStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOAuthFields(stepInfo.getAuth(), stepInfo.getTarget(), stepType, identifier));
    map.putAll(processSTOInstanceFields(stepInfo.getInstance(), stepType, identifier));

    STOYamlBurpToolData toolData = stepInfo.getTool();

    if (toolData != null) {
      map.put(getSTOKey(PRODUCT_SITE_ID),
          resolveStringParameter(TOOL_SITE_ID, stepType, identifier, toolData.getSiteId(), false));
      map.put(getSTOKey(PRODUCT_SCAN_ID),
          resolveStringParameter(TOOL_SCAN_ID, stepType, identifier, toolData.getScanId(), false));
    }

    return map;
  }

  private static Map<String, String> processSTOCheckmarxFields(
      CheckmarxStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOAuthFields(stepInfo.getAuth(), stepInfo.getTarget(), stepType, identifier));
    map.putAll(processSTOImageFields(stepInfo.getImage(), stepType, identifier));

    STOYamlCheckmarxToolData toolData = stepInfo.getTool();

    if (toolData != null) {
      map.put(getSTOKey(PRODUCT_TEAM_NAME),
          resolveStringParameter(TOOL_TEAM_NAME, stepType, identifier, toolData.getTeamName(), false));
      map.put(getSTOKey(PRODUCT_PROJECT_NAME),
          resolveStringParameter(TOOL_PROJECT_NAME, stepType, identifier, toolData.getProjectName(), false));
    }

    return map;
  }

  private static Map<String, String> processSTOFODFields(
      FortifyOnDemandStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOAuthFields(stepInfo.getAuth(), stepInfo.getTarget(), stepType, identifier));
    map.putAll(processSTOImageFields(stepInfo.getImage(), stepType, identifier));

    STOYamlFODToolData toolData = stepInfo.getTool();

    if (toolData != null) {
      map.put(getSTOKey("product_app_name"),
          resolveStringParameter("tool.app_name", stepType, identifier, toolData.getAppName(), false));
      map.put(getSTOKey("product_audit_type"),
          resolveStringParameter("tool.audit_type", stepType, identifier, toolData.getAuditType(), false));
      map.put(getSTOKey("product_data_center"),
          resolveStringParameter("tool.data_center", stepType, identifier, toolData.getDataCenter(), false));
      map.put(getSTOKey("product_lookup_type"),
          resolveStringParameter("tool.lookup_type", stepType, identifier, toolData.getLookupType(), false));
      map.put(getSTOKey("product_release_name"),
          resolveStringParameter("tool.release_name", stepType, identifier, toolData.getReleaseName(), false));
      map.put(getSTOKey("product_entitlement"),
          resolveStringParameter("tool.entitlement", stepType, identifier, toolData.getEntitlement(), false));
      map.put(getSTOKey("product_owner_id"),
          resolveStringParameter("tool.owner_id", stepType, identifier, toolData.getOwnerId(), false));
      map.put(getSTOKey("product_scan_settings"),
          resolveStringParameter("tool.scan_settings", stepType, identifier, toolData.getScanSettings(), false));
      map.put(getSTOKey("product_scan_type"),
          resolveStringParameter("tool.scan_type", stepType, identifier, toolData.getScanType(), false));
      map.put(getSTOKey("product_target_language"),
          resolveStringParameter("tool.target_language", stepType, identifier, toolData.getTargetLanguage(), false));
      map.put(getSTOKey("product_target_language_version"),
          resolveStringParameter(
              "tool.target_language_version", stepType, identifier, toolData.getTargetLanguageVersion(), false));
    }

    return map;
  }

  private static Map<String, String> processSTOPrismaCloudFields(
      PrismaCloudStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOAuthFields(stepInfo.getAuth(), stepInfo.getTarget(), stepType, identifier));
    map.putAll(processSTOImageFields(stepInfo.getImage(), stepType, identifier));

    STOYamlPrismaCloudToolData toolData = stepInfo.getTool();

    if (toolData != null) {
      map.put(getSTOKey(PRODUCT_IMAGE_NAME),
          resolveStringParameter(TOOL_IMAGE_NAME, stepType, identifier, toolData.getImageName(), false));
    }

    return map;
  }

  private static Map<String, String> processSTOSonarqubeFields(
      SonarqubeStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOAuthFields(stepInfo.getAuth(), stepInfo.getTarget(), stepType, identifier));

    STOYamlSonarqubeToolData toolData = stepInfo.getTool();

    if (toolData != null) {
      map.put(getSTOKey(PRODUCT_PROJECT_KEY),
          resolveStringParameter(TOOL_PROJECT_KEY, stepType, identifier, toolData.getProjectKey(), false));
      map.put(getSTOKey(PRODUCT_EXCLUDE),
          resolveStringParameter(TOOL_EXCLUDE, stepType, identifier, toolData.getExclude(), false));
      map.put(getSTOKey(PRODUCT_INCLUDE),
          resolveStringParameter(TOOL_INCLUDE, stepType, identifier, toolData.getInclude(), false));

      STOYamlJavaParameters javaParameters = toolData.getJava();

      if (javaParameters != null) {
        map.put(getSTOKey("product_java_binaries"),
            resolveStringParameter("tool.java.binaries", stepType, identifier, javaParameters.getBinaries(), false));
        map.put(getSTOKey("product_java_libraries"),
            resolveStringParameter("tool.java.libraries", stepType, identifier, javaParameters.getLibraries(), false));
      }
    }

    return map;
  }

  private static Map<String, String> processSTOSnykFields(SnykStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOAuthFields(stepInfo.getAuth(), stepInfo.getTarget(), stepType, identifier));
    map.putAll(processSTOImageFields(stepInfo.getImage(), stepType, identifier));

    return map;
  }

  private static Map<String, String> processSTOSysdigFields(
      SysdigStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOAuthFields(stepInfo.getAuth(), stepInfo.getTarget(), stepType, identifier));
    map.putAll(processSTOImageFields(stepInfo.getImage(), stepType, identifier));

    return map;
  }
  private static Map<String, String> processSTOFossaFields(FossaStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOAuthFields(stepInfo.getAuth(), stepInfo.getTarget(), stepType, identifier));

    STOYamlFossaToolData toolData = stepInfo.getTool();

    if (toolData != null) {
      map.put(getSTOKey(PRODUCT_PROJECT_NAME),
          resolveStringParameter(TOOL_PROJECT_NAME, stepType, identifier, toolData.getProjectName(), false));
      map.put(getSTOKey(PRODUCT_TEAM_NAME),
          resolveStringParameter(TOOL_TEAM_NAME, stepType, identifier, toolData.getTeamName(), false));
      map.put(getSTOKey(PRODUCT_POLICY_NAME),
          resolveStringParameter(TOOL_POLICY_NAME, stepType, identifier, toolData.getPolicyName(), false));
    }

    return map;
  }

  private static Map<String, String> processSTOAquaTrivyFields(
      AquaTrivyStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOImageFields(stepInfo.getImage(), stepType, identifier));

    return map;
  }

  private static Map<String, String> processSTOGrypeFields(GrypeStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOImageFields(stepInfo.getImage(), stepType, identifier));

    return map;
  }

  private static Map<String, String> processSTOMendFields(MendStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOAuthFields(stepInfo.getAuth(), stepInfo.getTarget(), stepType, identifier));
    map.putAll(processSTOImageFields(stepInfo.getImage(), stepType, identifier));

    STOYamlMendToolData toolData = stepInfo.getTool();

    if (toolData != null) {
      map.put(getSTOKey(PRODUCT_PROJECT_NAME),
          resolveStringParameter(TOOL_PROJECT_NAME, stepType, identifier, toolData.getProjectName(), false));
      map.put(getSTOKey(PRODUCT_PROJECT_TOKEN),
          resolveStringParameter(TOOL_PROJECT_TOKEN, stepType, identifier, toolData.getProjectToken(), false));
      map.put(getSTOKey(PRODUCT_PRODUCT_NAME),
          resolveStringParameter(TOOL_PRODUCT_NAME, stepType, identifier, toolData.getProductName(), false));
      map.put(getSTOKey(PRODUCT_PRODUCT_TOKEN),
          resolveStringParameter(TOOL_PRODUCT_TOKEN, stepType, identifier, toolData.getProductToken(), false));
      map.put(getSTOKey(PRODUCT_EXCLUDE),
          resolveStringParameter(TOOL_EXCLUDE, stepType, identifier, toolData.getExclude(), false));
      map.put(getSTOKey(PRODUCT_INCLUDE),
          resolveStringParameter(TOOL_INCLUDE, stepType, identifier, toolData.getInclude(), false));
    }

    return map;
  }

  private static Map<String, String> processSTOSemgrepFields(
      SemgrepStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOAuthFields(stepInfo.getAuth(), stepInfo.getTarget(), stepType, identifier));

    return map;
  }

  private static Map<String, String> processSTOVeracodeFields(
      VeracodeStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOAuthFields(stepInfo.getAuth(), stepInfo.getTarget(), stepType, identifier));

    STOYamlVeracodeToolData toolData = stepInfo.getTool();

    if (toolData != null) {
      map.put(getSTOKey("product_app_id"),
          resolveStringParameter("tool.app_id", stepType, identifier, toolData.getAppId(), false));
      map.put(getSTOKey(PRODUCT_PROJECT_NAME),
          resolveStringParameter(TOOL_PROJECT_NAME, stepType, identifier, toolData.getProjectName(), false));
    }

    return map;
  }
  private static Map<String, String> processSTOAwsEcrFields(
      AwsEcrStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOImageFields(stepInfo.getImage(), stepType, identifier));
    map.putAll(processSTOAuthFields(stepInfo.getAuth(), stepInfo.getTarget(), stepType, identifier));

    return map;
  }

  private static Map<String, String> processSTOAwsSecurityHubFields(
      AwsSecurityHubStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.put(getSTOKey(CONFIGURATION_TYPE), AWS_ACCOUNT);
    map.putAll(processSTOAuthFields(stepInfo.getAuth(), stepInfo.getTarget(), stepType, identifier));

    return map;
  }

  private static Map<String, String> processSTONmapFields(NmapStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOInstanceFields(stepInfo.getInstance(), stepType, identifier));

    return map;
  }

  private static Map<String, String> processSTONiktoFields(NiktoStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOInstanceFields(stepInfo.getInstance(), stepType, identifier));

    return map;
  }

  private static Map<String, String> processSTOMetasploitFields(
      MetasploitStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOInstanceFields(stepInfo.getInstance(), stepType, identifier));

    return map;
  }

  private static Map<String, String> processSTOProwlerFields(
      ProwlerStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.put(getSTOKey(CONFIGURATION_TYPE), AWS_ACCOUNT);
    map.putAll(processSTOAuthFields(stepInfo.getAuth(), stepInfo.getTarget(), stepType, identifier));

    return map;
  }

  private static Map<String, String> processSTOZapFields(ZapStepInfo stepInfo, String stepType, String identifier) {
    Map<String, String> map = new HashMap<>();

    map.putAll(processSTOInstanceFields(stepInfo.getInstance(), stepType, identifier));

    STOYamlZapToolData toolData = stepInfo.getTool();

    if (toolData != null) {
      map.put(getSTOKey("product_context"),
          resolveStringParameter("tool.context", stepType, identifier, toolData.getContext(), false));

      Integer port = resolveIntegerParameter(toolData.getPort(), null);
      if (port != null) {
        map.put(getSTOKey("zap_custom_port"), String.valueOf(port));
      }
    }

    return map;
  }

  public static String getProductConfigName(STOGenericStepInfo stepInfo) {
    String defaultConfig = STOYamlGenericConfig.DEFAULT.getYamlName();

    switch (stepInfo.getSTOStepType()) {
      case CUSTOM_INGEST:
        return ((CustomIngestStepInfo) stepInfo).getConfig().getYamlName();
      case BURP:
        return ((BurpStepInfo) stepInfo).getConfig().getYamlName();
      case METASPLOIT:
        return ((MetasploitStepInfo) stepInfo).getConfig().getYamlName();
      case NMAP:
        return ((NmapStepInfo) stepInfo).getConfig().getYamlName();
      case PROWLER:
        return ((ProwlerStepInfo) stepInfo).getConfig().getYamlName();
      case ZAP:
        return ((ZapStepInfo) stepInfo).getConfig().getYamlName();
      default:
        return defaultConfig;
    }
  }

  private static String getPolicyType(STOYamlScanMode scanMode) {
    if (scanMode != null) {
      return scanMode.getPluginName();
    }
    return STOYamlScanMode.ORCHESTRATION.getPluginName();
  }

  public static Map<String, String> getSTOPluginEnvVariables(STOGenericStepInfo stepInfo, String identifier) {
    Map<String, String> map = new HashMap<>();
    String stepType = stepInfo.getStepType().getType();

    STOYamlScanMode scanMode = stepInfo.getMode();

    map.put(getSTOKey("product_name"), stepInfo.getProductName());
    map.put(getSTOKey("product_config_name"), getProductConfigName(stepInfo));
    map.put(getSTOKey("policy_type"), getPolicyType(scanMode));

    map.putAll(processSTOTargetFields(stepInfo.getTarget(), stepType, identifier));
    map.putAll(processSTOAdvancedSettings(stepInfo.getAdvanced(), stepType, identifier));
    map.putAll(processSTOIngestionFields(stepInfo.getIngestion(), stepType, identifier));

    switch (stepInfo.getSTOStepType()) {
      case AWS_ECR:
        map.putAll(processSTOAwsEcrFields((AwsEcrStepInfo) stepInfo, stepType, identifier));
        break;
      case AWS_SECURITY_HUB:
        map.putAll(processSTOAwsSecurityHubFields((AwsSecurityHubStepInfo) stepInfo, stepType, identifier));
        break;
      case AQUA_TRIVY:
        map.putAll(processSTOAquaTrivyFields((AquaTrivyStepInfo) stepInfo, stepType, identifier));
        break;
      case BLACKDUCK:
        map.putAll(processSTOBlackDuckFields((BlackDuckStepInfo) stepInfo, stepType, identifier));
        break;
      case BURP:
        map.putAll(processSTOBurpFields((BurpStepInfo) stepInfo, stepType, identifier));
        break;
      case CHECKMARX:
        map.putAll(processSTOCheckmarxFields((CheckmarxStepInfo) stepInfo, stepType, identifier));
        break;
      case GRYPE:
        map.putAll(processSTOGrypeFields((GrypeStepInfo) stepInfo, stepType, identifier));
        break;
      case FORTIFY_ON_DEMAND:
        map.putAll(processSTOFODFields((FortifyOnDemandStepInfo) stepInfo, stepType, identifier));
        break;
      case FOSSA:
        map.putAll(processSTOFossaFields((FossaStepInfo) stepInfo, stepType, identifier));
        break;
      case MEND:
        map.putAll(processSTOMendFields((MendStepInfo) stepInfo, stepType, identifier));
        break;
      case NMAP:
        map.putAll(processSTONmapFields((NmapStepInfo) stepInfo, stepType, identifier));
        break;
      case NIKTO:
        map.putAll(processSTONiktoFields((NiktoStepInfo) stepInfo, stepType, identifier));
        break;
      case METASPLOIT:
        map.putAll(processSTOMetasploitFields((MetasploitStepInfo) stepInfo, stepType, identifier));
        break;
      case PRISMA_CLOUD:
        map.putAll(processSTOPrismaCloudFields((PrismaCloudStepInfo) stepInfo, stepType, identifier));
        break;
      case PROWLER:
        map.putAll(processSTOProwlerFields((ProwlerStepInfo) stepInfo, stepType, identifier));
        break;
      case SEMGREP:
        map.putAll(processSTOSemgrepFields((SemgrepStepInfo) stepInfo, stepType, identifier));
        break;
      case SONARQUBE:
        map.putAll(processSTOSonarqubeFields((SonarqubeStepInfo) stepInfo, stepType, identifier));
        break;
      case SNYK:
        map.putAll(processSTOSnykFields((SnykStepInfo) stepInfo, stepType, identifier));
        break;
      case SYSDIG:
        map.putAll(processSTOSysdigFields((SysdigStepInfo) stepInfo, stepType, identifier));
        break;
      case VERACODE:
        map.putAll(processSTOVeracodeFields((VeracodeStepInfo) stepInfo, stepType, identifier));
        break;
      case ZAP:
        map.putAll(processSTOZapFields((ZapStepInfo) stepInfo, stepType, identifier));
        break;
      default:
        break;
    }
    map.values().removeAll(Collections.singleton(null));

    return map;
  }
}
