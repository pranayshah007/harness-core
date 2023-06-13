/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.execution;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.ADD_MANIFEST_COLLECTION_STEP;
import static io.harness.beans.FeatureName.BYPASS_HELM_FETCH;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.ExecutionCredential.ExecutionType.SSH;
import static software.wings.beans.SSHExecutionCredential.Builder.aSSHExecutionCredential;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.CreatedByType;
import io.harness.beans.ExecutionStatus;
import io.harness.data.parser.CsvParser;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.govern.Switch;

import software.wings.beans.ArtifactVariable;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.Service;
import software.wings.beans.Variable;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.deployment.DeploymentMetadata;
import software.wings.beans.deployment.DeploymentMetadata.DeploymentMetadataKeys;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.execution.input.QLArtifactIdInput;
import software.wings.graphql.schema.mutation.execution.input.QLArtifactInputType;
import software.wings.graphql.schema.mutation.execution.input.QLArtifactValueInput;
import software.wings.graphql.schema.mutation.execution.input.QLBuildNumberInput;
import software.wings.graphql.schema.mutation.execution.input.QLManifestInputType;
import software.wings.graphql.schema.mutation.execution.input.QLManifestValueInput;
import software.wings.graphql.schema.mutation.execution.input.QLParameterValueInput;
import software.wings.graphql.schema.mutation.execution.input.QLParameterizedArtifactSourceInput;
import software.wings.graphql.schema.mutation.execution.input.QLServiceInput;
import software.wings.graphql.schema.mutation.execution.input.QLStartExecutionInput;
import software.wings.graphql.schema.mutation.execution.input.QLVariableInput;
import software.wings.graphql.schema.mutation.execution.input.QLVariableValue;
import software.wings.graphql.schema.type.QLExecutionStatus;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.resources.graphql.TriggeredByType;
import software.wings.service.ArtifactStreamHelper;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactCollectionService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.applicationmanifest.HelmChartService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import graphql.GraphQLContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Deliberately having a single class to adapt both
 * workflow and workflow execution.
 * Ideally, we should have two separate adapters.
 */
@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class ExecutionController {
  @Inject ArtifactService artifactService;
  @Inject HelmChartService helmChartService;
  @Inject ArtifactStreamService artifactStreamService;
  @Inject @Named("AsyncArtifactCollectionService") private ArtifactCollectionService artifactCollectionServiceAsync;
  @Inject ServiceResourceService serviceResourceService;
  @Inject ArtifactStreamHelper artifactStreamHelper;
  @Inject InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject EnvironmentService environmentService;
  @Inject ApplicationManifestService applicationManifestService;
  @Inject FeatureFlagService featureFlagService;

  public static QLExecutionStatus convertStatus(ExecutionStatus status) {
    switch (status) {
      case ABORTED:
      case ABORTING:
        return QLExecutionStatus.ABORTED;
      case DISCONTINUING:
      case RUNNING:
        return QLExecutionStatus.RUNNING;
      case ERROR:
        return QLExecutionStatus.ERROR;
      case FAILED:
        return QLExecutionStatus.FAILED;
      case PAUSED:
      case PAUSING:
        return QLExecutionStatus.PAUSED;
      case QUEUED:
      case NEW:
      case SCHEDULED:
      case STARTING:
        return QLExecutionStatus.QUEUED;
      case RESUMED:
        return QLExecutionStatus.RESUMED;
      case SUCCESS:
        return QLExecutionStatus.SUCCESS;
      case WAITING:
        return QLExecutionStatus.WAITING;
      case SKIPPED:
        return QLExecutionStatus.SKIPPED;
      case REJECTED:
        return QLExecutionStatus.REJECTED;
      case EXPIRED:
        return QLExecutionStatus.EXPIRED;
      default:
        Switch.unhandled(status);
    }
    return null;
  }

  public static List<ExecutionStatus> convertStatus(List<QLExecutionStatus> statuses) {
    return statuses.stream().map(ExecutionController::convertStatus).flatMap(Collection::stream).collect(toList());
  }

  public static List<ExecutionStatus> convertStatus(QLExecutionStatus status) {
    switch (status) {
      case ABORTED:
        return asList(ExecutionStatus.ABORTED, ExecutionStatus.ABORTING);
      case RUNNING:
        return asList(ExecutionStatus.RUNNING, ExecutionStatus.DISCONTINUING);
      case ERROR:
        return asList(ExecutionStatus.ERROR);
      case FAILED:
        return asList(ExecutionStatus.FAILED);
      case PAUSED:
        return asList(ExecutionStatus.PAUSED, ExecutionStatus.PAUSING);
      case QUEUED:
        return asList(ExecutionStatus.QUEUED, ExecutionStatus.STARTING, ExecutionStatus.SCHEDULED, ExecutionStatus.NEW);
      case RESUMED:
        return asList(ExecutionStatus.RESUMED);
      case SUCCESS:
        return asList(ExecutionStatus.SUCCESS);
      case WAITING:
        return asList(ExecutionStatus.WAITING);
      case SKIPPED:
        return asList(ExecutionStatus.SKIPPED);
      case REJECTED:
        return asList(ExecutionStatus.REJECTED);
      case EXPIRED:
        return asList(ExecutionStatus.EXPIRED);
      default:
        Switch.unhandled(status);
    }
    return null;
  }

  public void setCreatedByTypeInExecutionArgs(MutationContext mutationContext, ExecutionArgs executionArgs) {
    if (mutationContext.getDataFetchingEnvironment() != null
        && mutationContext.getDataFetchingEnvironment().getContext() != null) {
      GraphQLContext graphQLContext = mutationContext.getDataFetchingEnvironment().getContext();
      TriggeredByType triggeredByType = graphQLContext.get("triggeredByType");
      if (triggeredByType == TriggeredByType.USER) {
        executionArgs.setCreatedByType(CreatedByType.USER);
      } else {
        executionArgs.setCreatedByType(CreatedByType.API_KEY);
        executionArgs.setTriggeringApiKeyId(graphQLContext.get("triggeredById"));
      }
    }
  }

  public void populateExecutionArgs(Map<String, String> variableValues, List<Artifact> artifacts,
      QLStartExecutionInput triggerExecutionInput, MutationContext mutationContext, ExecutionArgs executionArgs,
      List<HelmChart> helmCharts) {
    executionArgs.setArtifacts(artifacts);
    executionArgs.setExecutionCredential(aSSHExecutionCredential().withExecutionType(SSH).build());
    executionArgs.setExcludeHostsWithSameArtifact(triggerExecutionInput.isExcludeHostsWithSameArtifact());
    executionArgs.setNotes(triggerExecutionInput.getNotes());
    executionArgs.setTargetToSpecificHosts(triggerExecutionInput.isTargetToSpecificHosts());
    executionArgs.setHosts(triggerExecutionInput.getSpecificHosts());
    executionArgs.setWorkflowVariables(variableValues);
    executionArgs.setHelmCharts(helmCharts);
    setCreatedByTypeInExecutionArgs(mutationContext, executionArgs);
  }

  private Artifact getArtifactFromId(QLArtifactIdInput artifactId, Service service) {
    String artifactIdVal = artifactId.getArtifactId();

    if (isEmpty(artifactIdVal)) {
      throw new InvalidRequestException("Artifact Id cannot be empty for serviceInput: " + service.getName(), USER);
    }

    Artifact artifact = artifactService.get(service.getAccountId(), artifactIdVal);
    notNullCheck("Cannot find artifact for specified Id: " + artifactIdVal + ". Might be deleted", artifact, USER);
    if (!artifact.getServiceIds().contains(service.getUuid())) {
      throw new InvalidRequestException(
          "Artifact Id: " + artifactIdVal + " does not belong to specified service: " + service.getName(), USER);
    }
    return artifact;
  }

  public boolean validateVariableValue(String appId, String value, Variable variable, String envId) {
    EntityType entityType = variable.obtainEntityType();
    if (entityType != null) {
      if (isEmpty(value)) {
        throw new InvalidRequestException("Please provide a non empty value for " + variable.getName(), USER);
      }
      switch (entityType) {
        case ENVIRONMENT:
          return true;
        case SERVICE:
          if (!serviceResourceService.exist(appId, value)) {
            throw new InvalidRequestException(
                "Service [" + value + "] doesn't exist in specified application " + appId);
          }
          return true;
        case INFRASTRUCTURE_DEFINITION:
          InfrastructureDefinition infrastructureDefinition = infrastructureDefinitionService.get(appId, value);
          notNullCheck("Infrastructure Definition  [" + value + "] doesn't exist in specified application " + appId,
              infrastructureDefinition, USER);
          if (!infrastructureDefinition.getEnvId().equals(envId)) {
            throw new InvalidRequestException(
                "Infrastructure Definition  [" + value + "] doesn't exist in specified application and environment ",
                USER);
          }
          return true;
        default:
          // do nothing.
          return true;
      }
    }
    return true;
  }

  public String getEnvId(String envVarName, String appId, List<QLVariableInput> variableInputs) {
    if (!isEmpty(variableInputs)) {
      QLVariableInput envVarInput =
          variableInputs.stream().filter(t -> envVarName.equals(t.getName())).findFirst().orElse(null);
      if (envVarInput != null) {
        QLVariableValue envVarValue = envVarInput.getVariableValue();
        notNullCheck(envVarInput.getName() + " has no variable value present", envVarValue, USER);
        if (isEmpty(envVarValue.getValue())) {
          throw new InvalidRequestException("Please provide a non empty value for " + envVarInput.getName(), USER);
        }
        switch (envVarValue.getType()) {
          case ID:
            String envId = envVarValue.getValue();
            Environment environment = environmentService.get(appId, envId);
            notNullCheck(
                "Environment [" + envId + "] doesn't exist in specified application " + appId, environment, USER);
            return envId;
          case NAME:
            String envName = envVarValue.getValue();
            Environment environmentFromName = environmentService.getEnvironmentByName(appId, envName);
            notNullCheck("Environment [" + envName + "] doesn't exist in specified application " + appId,
                environmentFromName, USER);
            return environmentFromName.getUuid();
          default:
            throw new InvalidRequestException("Value Type " + envVarValue.getType() + " Not supported");
        }
      }
    }
    return null;
  }

  private Artifact getArtifactFromBuildNumber(QLBuildNumberInput buildNumber, Service service) {
    String artifactSourceName = buildNumber.getArtifactSourceName();

    if (isEmpty(artifactSourceName)) {
      throw new InvalidRequestException(
          "Artifact Source name cannot be empty for serviceInput: " + service.getName(), USER);
    }

    if (isEmpty(buildNumber.getBuildNumber())) {
      throw new InvalidRequestException("Build Number cannot be empty for serviceInput: " + service.getName(), USER);
    }

    ArtifactStream artifactStream =
        artifactStreamService.getArtifactStreamByName(service.getAppId(), service.getUuid(), artifactSourceName);
    notNullCheck("Cannot find artifact Source: " + artifactSourceName + " in specified service: " + service.getName(),
        artifactStream, USER);
    Artifact artifact = getArtifactForBuildNumber(service.getAppId(), artifactStream, buildNumber.getBuildNumber());
    notNullCheck(
        "Cannot find or collect artifact for specified Build Number: " + buildNumber.getBuildNumber(), artifact, USER);
    return artifact;
  }

  private Artifact getArtifactForBuildNumber(String appId, ArtifactStream artifactStream, String buildNumber) {
    Artifact collectedArtifactForBuildNumber =
        artifactService.getArtifactByBuildNumber(artifactStream, buildNumber, false);

    return collectedArtifactForBuildNumber != null
        ? collectedArtifactForBuildNumber
        : collectNewArtifactForBuildNumber(appId, artifactStream, buildNumber);
  }

  private Artifact collectNewArtifactForBuildNumber(String appId, ArtifactStream artifactStream, String buildNumber) {
    Artifact artifact = artifactCollectionServiceAsync.collectNewArtifacts(appId, artifactStream, buildNumber);
    if (artifact != null) {
      log.info("Artifact {} collected for the build number {} of stream id {}", artifact, buildNumber,
          artifactStream.getUuid());
    } else {
      log.warn(
          "Artifact collection invoked. However, Artifact not yet collected for the build number {} of stream id {}",
          buildNumber, artifactStream.getUuid());
    }
    return artifact;
  }
  private Artifact getArtifactFromBuildNumberAndParameters(
      QLParameterizedArtifactSourceInput parameterizedArtifactSourceInput, Service service) {
    String artifactSourceName = parameterizedArtifactSourceInput.getArtifactSourceName();

    if (isEmpty(artifactSourceName)) {
      throw new InvalidRequestException(
          "Artifact Source name cannot be empty for serviceInput: " + service.getName(), USER);
    }

    if (isEmpty(parameterizedArtifactSourceInput.getBuildNumber())) {
      throw new InvalidRequestException("Build Number cannot be empty for serviceInput: " + service.getName(), USER);
    }

    ArtifactStream artifactStream =
        artifactStreamService.getArtifactStreamByName(service.getAppId(), service.getUuid(), artifactSourceName);
    notNullCheck("Cannot find artifact Source: " + artifactSourceName + " in specified service: " + service.getName(),
        artifactStream, USER);
    if (!artifactStream.isArtifactStreamParameterized()) {
      throw new InvalidRequestException(
          "Parameterized Artifact Source valueType cannot be used for non-parameterized artifact source");
    } else {
      List<QLParameterValueInput> parameterValueInputs = parameterizedArtifactSourceInput.getParameterValueInputs();
      if (isEmpty(parameterValueInputs)) {
        throw new InvalidRequestException(
            "Artifact Source is parameterized. However, runtime values for parameters not provided", USER);
      }
      Map<String, Object> runtimeValues = new HashMap<>();
      for (QLParameterValueInput parameterValueInput : parameterValueInputs) {
        runtimeValues.put(parameterValueInput.getName(), parameterValueInput.getValue());
      }
      runtimeValues.put("buildNo", parameterizedArtifactSourceInput.getBuildNumber());
      artifactStreamHelper.resolveArtifactStreamRuntimeValues(artifactStream, runtimeValues);
      artifactStream.setSourceName(artifactStream.generateSourceName());
      Artifact collectedArtifactForBuildNumber = artifactService.getArtifactByBuildNumberAndSourceName(
          artifactStream, parameterizedArtifactSourceInput.getBuildNumber(), false, artifactStream.getSourceName());
      if (collectedArtifactForBuildNumber == null) {
        collectedArtifactForBuildNumber = collectNewArtifactForBuildNumber(artifactStream.getAppId(), artifactStream,
            parameterizedArtifactSourceInput.getBuildNumber(), runtimeValues);
      }
      notNullCheck("Cannot find or collect artifact for specified Build Number: "
              + parameterizedArtifactSourceInput.getBuildNumber(),
          collectedArtifactForBuildNumber, USER);
      return collectedArtifactForBuildNumber;
    }
  }

  private Artifact collectNewArtifactForBuildNumber(
      String appId, ArtifactStream artifactStream, String buildNumber, Map<String, Object> artifactVariables) {
    Artifact artifact =
        artifactCollectionServiceAsync.collectNewArtifacts(appId, artifactStream, buildNumber, artifactVariables);
    if (artifact != null) {
      log.info("Artifact {} collected for the build number {} of stream id {}", artifact, buildNumber,
          artifactStream.getUuid());
    } else {
      log.warn(
          "Artifact collection invoked. However, Artifact not yet collected for the build number {} of stream id {}",
          buildNumber, artifactStream.getUuid());
    }
    return artifact;
  }

  public void getArtifactsFromServiceInputs(List<QLServiceInput> serviceInputs, String appId,
      List<String> artifactNeededServiceIds, List<Artifact> artifacts, List<ArtifactVariable> artifactVariables) {
    for (String serviceId : artifactNeededServiceIds) {
      Service service = serviceResourceService.get(appId, serviceId);
      notNullCheck(
          "Something went wrong while checking required service Inputs. Associated Service might be deleted", service);
      String serviceName = service.getName();
      QLServiceInput serviceInput =
          serviceInputs.stream().filter(t -> serviceName.equals(t.getName())).findFirst().orElse(null);
      notNullCheck("ServiceInput required for service: " + serviceName, serviceInput, USER);

      QLArtifactValueInput artifactValueInput = serviceInput.getArtifactValueInput();
      notNullCheck("ArtifactValueInput is required for the service Input: " + serviceName, artifactValueInput, USER);

      QLArtifactInputType type = artifactValueInput.getValueType();
      Artifact artifact;
      switch (type) {
        case ARTIFACT_ID:
          notNullCheck(
              "ArtifactIdInput is required for the service Input: " + serviceName + "for value type as ARTIFACT_ID",
              artifactValueInput.getArtifactId(), USER);
          artifact = getArtifactFromId(artifactValueInput.getArtifactId(), service);
          artifacts.add(artifact);
          break;
        case BUILD_NUMBER:
          notNullCheck(
              "BuildNumberInput is required for the service Input: " + serviceName + "for value type as BUILD_NUMBER",
              artifactValueInput.getBuildNumber(), USER);
          artifact = getArtifactFromBuildNumber(artifactValueInput.getBuildNumber(), service);
          artifacts.add(artifact);
          break;
        case PARAMETERIZED_ARTIFACT_SOURCE:
          notNullCheck("ParameterizedArtifactSourceInput is required for the service Input: " + serviceName
                  + "for value type as PARAMETERIZED_ARTIFACT_SOURCE",
              artifactValueInput.getParameterizedArtifactSource(), USER);
          artifact =
              getArtifactFromBuildNumberAndParameters(artifactValueInput.getParameterizedArtifactSource(), service);
          artifacts.add(artifact);
          break;
        default:
          throw new InvalidRequestException("Unexpected artifact value type: " + type);
      }
      artifactVariables.add(ArtifactVariable.builder()
                                .entityType(EntityType.SERVICE)
                                .entityId(service.getUuid())
                                .value(artifact.getUuid())
                                .build());
    }
  }

  void validateRestrictedVarsHaveAllowedValues(List<QLVariableInput> variableInputs, List<Variable> workflowVariables) {
    Map<String, List<String>> allowedValuesMap =
        workflowVariables.stream()
            .filter(variable -> isNotEmpty(variable.getAllowedList()))
            .collect(Collectors.toMap(Variable::getName, Variable::getAllowedList));
    variableInputs.stream()
        .filter(input
            -> allowedValuesMap.containsKey(input.getName())
                && !allowedValuesMap.get(input.getName())
                        .containsAll(CsvParser.parse(input.getVariableValue().getValue())))
        .findAny()
        .ifPresent(input -> {
          throw new InvalidRequestException(
              "Variable " + input.getName() + " can only take values " + allowedValuesMap.get(input.getName()));
        });
  }

  private HelmChart getHelmChartFromId(String helmChartId, Service service) {
    if (isEmpty(helmChartId)) {
      throw new InvalidRequestException("Helm Chart Id cannot be empty for serviceInput: " + service.getName(), USER);
    }

    HelmChart helmChart = helmChartService.get(service.getAppId(), helmChartId);
    notNullCheck("Cannot find helm chart for specified Id: " + helmChartId + ". Might be deleted", helmChart, USER);

    return helmChart;
  }

  private HelmChart getHelmChartFromVersionNumber(String versionNumber, String appManifestName, Service service) {
    if (isEmpty(versionNumber)) {
      throw new InvalidRequestException("Version Number cannot be empty for serviceInput: " + service.getName(), USER);
    }

    ApplicationManifest applicationManifest =
        applicationManifestService.getAppManifestByName(service.getAppId(), null, service.getUuid(), appManifestName);
    notNullCheck("App manifest with name " + appManifestName + " doesn't belong to the given app and service",
        applicationManifest);

    if (featureFlagService.isEnabled(ADD_MANIFEST_COLLECTION_STEP, service.getAccountId())) {
      return HelmChart.builder().applicationManifestId(applicationManifest.getUuid()).version(versionNumber).build();
    }

    HelmChart helmChart =
        helmChartService.getByChartVersion(service.getAppId(), service.getUuid(), appManifestName, versionNumber);
    if (helmChart == null) {
      if (featureFlagService.isEnabled(BYPASS_HELM_FETCH, applicationManifest.getAccountId())) {
        return helmChartService.createHelmChartWithVersionForAppManifest(applicationManifest, versionNumber);
      }
      helmChart = helmChartService.fetchByChartVersion(
          service.getAccountId(), service.getAppId(), service.getUuid(), applicationManifest.getUuid(), versionNumber);
    }
    notNullCheck(String.format("Cannot find helm chart for specified version number: %s and service: %s", versionNumber,
                     service.getName()),
        helmChart, USER);
    return helmChart;
  }

  public void getHelmChartsFromServiceInputs(List<QLServiceInput> serviceInputs, String appId,
      List<String> manifestNeededServiceIds, List<HelmChart> helmCharts) {
    for (String serviceId : manifestNeededServiceIds) {
      Service service = serviceResourceService.get(appId, serviceId);
      notNullCheck(
          "Something went wrong while checking required service Inputs. Associated Service might be deleted", service);

      String serviceName = service.getName();
      QLServiceInput serviceInput =
          serviceInputs.stream().filter(t -> serviceName.equals(t.getName())).findFirst().orElse(null);
      notNullCheck("ServiceInput required for service: " + serviceName, serviceInput, USER);

      QLManifestValueInput manifestValueInput = serviceInput.getManifestValueInput();
      notNullCheck("ManifestValueInput is required for the service Input: " + serviceName, manifestValueInput, USER);

      QLManifestInputType type = manifestValueInput.getValueType();
      switch (type) {
        case HELM_CHART_ID:
          notNullCheck(
              "HelmChartId is required for the service Input: " + serviceName + " for value type as HELM_CHART_ID",
              manifestValueInput.getHelmChartId(), USER);
          helmCharts.add(getHelmChartFromId(manifestValueInput.getHelmChartId(), service));
          break;
        case VERSION_NUMBER:
          notNullCheck(
              "versionNumber is required for the service Input: " + serviceName + " for value type as VERSION_NUMBER",
              manifestValueInput.getVersionNumber(), USER);
          notNullCheck("appManifestName is required for the version number Input: " + serviceName
                  + " for value type as VERSION_NUMBER",
              manifestValueInput.getVersionNumber().getAppManifestName(), USER);
          notNullCheck(
              "versionNumber is required for the service Input: " + serviceName + " for value type as VERSION_NUMBER",
              manifestValueInput.getVersionNumber().getVersionNumber(), USER);
          helmCharts.add(getHelmChartFromVersionNumber(manifestValueInput.getVersionNumber().getVersionNumber(),
              manifestValueInput.getVersionNumber().getAppManifestName(), service));
          break;
        default:
          throw new UnsupportedOperationException("Unexpected manifest value type: " + type);
      }
    }
  }

  Map<String, List<String>> getRequiredServiceIds(String workflowId, DeploymentMetadata finalDeploymentMetadata) {
    Map<String, List<String>> serviceIdMap = new HashMap<>();
    serviceIdMap.put(DeploymentMetadataKeys.artifactRequiredServices, new ArrayList<>());
    serviceIdMap.put(DeploymentMetadataKeys.manifestRequiredServiceIds, new ArrayList<>());
    if (finalDeploymentMetadata != null) {
      List<String> artifactNeededServiceIds = finalDeploymentMetadata.getArtifactRequiredServiceIds();
      if (isNotEmpty(artifactNeededServiceIds)) {
        serviceIdMap.put(DeploymentMetadataKeys.artifactRequiredServices, artifactNeededServiceIds);
      }
      List<String> manifestNeededServiceIds = finalDeploymentMetadata.getManifestRequiredServiceIds();
      if (isNotEmpty(manifestNeededServiceIds)) {
        serviceIdMap.put(DeploymentMetadataKeys.manifestRequiredServiceIds, manifestNeededServiceIds);
      }
    }
    log.info("No Services requires artifact inputs for this workflow/pipeline: " + workflowId);
    return serviceIdMap;
  }
}
