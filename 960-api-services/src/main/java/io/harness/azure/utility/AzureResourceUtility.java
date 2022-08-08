/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.utility;

import static io.harness.azure.model.AzureConstants.ASSIGNMENT_NAME_PATTERN;
import static io.harness.azure.model.AzureConstants.BLUEPRINT_ID_REGEX;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_SLOT_NAME_PREFIX_PATTERN;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_SLOT_PRODUCTION_NAME;
import static io.harness.azure.model.AzureConstants.DOCKER_CUSTOM_IMAGE_NAME_PROPERTY_NAME;
import static io.harness.azure.model.AzureConstants.DOCKER_FX_IMAGE_PREFIX;
import static io.harness.azure.model.AzureConstants.DOCKER_IMAGE_AND_TAG_PATH_PATTERN;
import static io.harness.azure.model.AzureConstants.DOCKER_IMAGE_FULL_PATH_PATTERN;
import static io.harness.azure.model.AzureConstants.DOCKER_REGISTRY_SERVER_SECRET_PROPERTY_NAME;
import static io.harness.azure.model.AzureConstants.DOCKER_REGISTRY_SERVER_URL_PROPERTY_NAME;
import static io.harness.azure.model.AzureConstants.DOCKER_REGISTRY_SERVER_USERNAME_PROPERTY_NAME;
import static io.harness.azure.model.AzureConstants.RESOURCE_SCOPE_MNG_GROUP_PATTERN;
import static io.harness.azure.model.AzureConstants.RESOURCE_SCOPE_SUBSCRIPTION_PATTERN;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNoneBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.blueprint.ResourceScopeType;
import io.harness.azure.model.blueprint.assignment.Assignment;
import io.harness.exception.InvalidArgumentsException;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.azure.CloudException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SimpleTimeZone;
import java.util.UUID;
import java.util.regex.Matcher;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class AzureResourceUtility {
  private final String DELIMITER = "__";
  private final String ISO_8601_BASIC_FORMAT = "yyyyMMdd'T'HHmmss'Z'";
  private final List<String> AUTO_SCALE_DEFAULT_PROFILE_NAMES =
      Arrays.asList("Profile1", "Auto created scale condition");
  public final List<String> DOCKER_REGISTRY_PROPERTY_NAMES =
      Arrays.asList(DOCKER_REGISTRY_SERVER_URL_PROPERTY_NAME, DOCKER_REGISTRY_SERVER_USERNAME_PROPERTY_NAME,
          DOCKER_REGISTRY_SERVER_SECRET_PROPERTY_NAME, DOCKER_CUSTOM_IMAGE_NAME_PROPERTY_NAME);

  public String dateToISO8601BasicStr(Date date) {
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat(ISO_8601_BASIC_FORMAT);
    dateTimeFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));
    return dateTimeFormat.format(date);
  }

  public Date iso8601BasicStrToDate(String strDate) {
    DateFormat format = new SimpleDateFormat(ISO_8601_BASIC_FORMAT);
    try {
      return format.parse(strDate);
    } catch (ParseException e) {
      throw new IllegalArgumentException(format("Unable to parse date: %s", strDate), e);
    }
  }

  public String getVMSSName(String scaleSetNamePrefix, Integer revision) {
    return scaleSetNamePrefix + DELIMITER + revision;
  }

  public String getRevisionTagValue(String tagValuePrefix, Integer revision) {
    return tagValuePrefix + DELIMITER + revision;
  }

  public int getRevisionFromTag(String tagValue) {
    if (tagValue != null) {
      int index = tagValue.lastIndexOf(DELIMITER);
      if (index >= 0) {
        try {
          return Integer.parseInt(tagValue.substring(index + DELIMITER.length()));
        } catch (NumberFormatException e) {
          // Ignore
        }
      }
    }
    return 0;
  }

  public boolean isDefaultAutoScaleProfile(String profileName) {
    return isNotBlank(profileName) && AUTO_SCALE_DEFAULT_PROFILE_NAMES.contains(profileName);
  }

  public String getDockerImageAndTagFullPath(String imageAndTag) {
    return String.format(DOCKER_IMAGE_FULL_PATH_PATTERN, imageAndTag);
  }

  public String getDockerImageFullNameAndTag(String imageName, String imageTag) {
    if (imageName.contains(":")) {
      return imageName;
    }
    return String.format(DOCKER_IMAGE_AND_TAG_PATH_PATTERN, imageName, imageTag);
  }

  public String getAzureCloudExceptionMessage(Exception ex) {
    String message = ex.getMessage() != null ? ex.getMessage() : EMPTY;
    if (ex.getCause() instanceof CloudException) {
      CloudException cloudException = (CloudException) ex.getCause();
      String cloudExMsg = cloudException.getMessage();
      message = format("%s, %nAzure Cloud Exception Message: %s", message, cloudExMsg);
    }
    return message;
  }

  @NotNull
  public String removeDockerFxImagePrefix(String windowsFxVersion) {
    return windowsFxVersion.replace(DOCKER_FX_IMAGE_PREFIX, EMPTY);
  }

  public String fixDeploymentSlotName(String deploymentSlotName, String appName) {
    if (isBlank(deploymentSlotName) || isBlank(appName)) {
      return deploymentSlotName;
    }

    if (deploymentSlotName.equals(appName)) {
      return DEPLOYMENT_SLOT_PRODUCTION_NAME;
    }

    return deploymentSlotName.replace(format(DEPLOYMENT_SLOT_NAME_PREFIX_PATTERN, appName), EMPTY);
  }

  public String generateAssignmentNameIfBlank(final String assignmentName, final String blueprintName) {
    if (isNoneBlank(assignmentName)) {
      return assignmentName;
    }

    return String.format(ASSIGNMENT_NAME_PATTERN, blueprintName, System.currentTimeMillis());
  }

  public String getDefinitionResourceScope(final String blueprintId) {
    Matcher matcher = BLUEPRINT_ID_REGEX.matcher(blueprintId);
    if (!matcher.matches()) {
      throw new InvalidArgumentsException(format("Parameter resourceScope not found in blueprintId: %s", blueprintId));
    }
    return matcher.group("resourceScope");
  }

  public String getVersionId(final String blueprintId) {
    Matcher matcher = BLUEPRINT_ID_REGEX.matcher(blueprintId);
    if (!matcher.matches()) {
      throw new InvalidArgumentsException(format("Parameter versionId not found in blueprintId: %s", blueprintId));
    }

    return matcher.group("versionId");
  }

  public String getBlueprintName(final String blueprintId) {
    Matcher matcher = BLUEPRINT_ID_REGEX.matcher(blueprintId);
    if (!matcher.matches()) {
      throw new InvalidArgumentsException(format("Parameter blueprintName not found in blueprintId: %s", blueprintId));
    }

    return matcher.group("blueprintName");
  }

  public String getBlueprintId(final String assignmentJson) {
    Assignment assignment = JsonUtils.asObject(assignmentJson, Assignment.class);
    return assignment.getProperties().getBlueprintId();
  }

  public Optional<String> getBlueprintNameFromBlueprintJson(final String blueprintJson) {
    JsonNode blueprintObj = JsonUtils.readTree(blueprintJson);
    return blueprintObj.get("name") != null && isNotBlank(blueprintObj.get("name").asText())
        ? Optional.of(blueprintObj.get("name").asText())
        : Optional.empty();
  }

  public Map<String, String> fixArtifactNames(final Map<String, String> artifacts) {
    Map<String, String> fixedArtifacts = new HashMap<>();
    artifacts.forEach((artifactFileName, artifactJson) -> {
      String artifactName = getArtifactName(artifactFileName, artifactJson);
      fixedArtifacts.put(artifactName, artifactJson);
    });
    return fixedArtifacts;
  }

  public String getArtifactName(final String artifactFileName, final String artifactJson) {
    JsonNode artifactObj = JsonUtils.readTree(artifactJson);
    return artifactObj.get("name") != null && isNoneBlank(artifactObj.get("name").asText())
        ? artifactObj.get("name").asText()
        : artifactFileName;
  }

  public String getRandomUUID() {
    return UUID.randomUUID().toString();
  }

  public static String getAssignmentSubscriptionId(final Assignment assignment) {
    String blueprintId = assignment.getProperties().getBlueprintId();
    if (ResourceScopeType.MANAGEMENT_GROUP == ResourceScopeType.fromBlueprintId(blueprintId)) {
      return assignment.getProperties().getScope().replace(RESOURCE_SCOPE_SUBSCRIPTION_PATTERN, EMPTY);
    } else {
      return getDefinitionResourceScope(blueprintId).replace(RESOURCE_SCOPE_SUBSCRIPTION_PATTERN, EMPTY);
    }
  }

  public static boolean isValidResourceScope(final String resourceScope) {
    return resourceScope != null
        && (resourceScope.startsWith(RESOURCE_SCOPE_MNG_GROUP_PATTERN)
            || resourceScope.startsWith(RESOURCE_SCOPE_SUBSCRIPTION_PATTERN));
  }

  public static String getAssignmentResourceScope(final Assignment assignment) {
    String blueprintId = assignment.getProperties().getBlueprintId();
    if (ResourceScopeType.MANAGEMENT_GROUP == ResourceScopeType.fromBlueprintId(blueprintId)) {
      return assignment.getProperties().getScope();
    } else {
      return getDefinitionResourceScope(blueprintId);
    }
  }
}
