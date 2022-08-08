/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.expression.ExpressionEvaluator.DEFAULT_ARTIFACT_VARIABLE_NAME;

import static software.wings.api.DeploymentType.KUBERNETES;
import static software.wings.api.DeploymentType.SSH;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.service.impl.ArtifactStreamServiceImpl.ARTIFACT_STREAM_DEBUG_LOG;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SortOrder.OrderType;
import io.harness.eraro.Level;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.EntityType;
import software.wings.beans.Service;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariable.ServiceVariableKeys;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.Variable;
import software.wings.beans.VariableType;
import software.wings.beans.Workflow;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamBinding;
import software.wings.beans.artifact.ArtifactStreamSummary;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceVariableService;
import software.wings.service.intfc.WorkflowService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@Singleton
@ValidateOnExecution
@Slf4j
@OwnedBy(CDC)
@TargetModule(_870_CG_ORCHESTRATION)
public class ArtifactStreamServiceBindingServiceImpl implements ArtifactStreamServiceBindingService {
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private ServiceVariableService serviceVariableService;
  @Inject private ArtifactService artifactService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private WorkflowService workflowService;

  @Override
  public ArtifactStreamBinding create(
      @NotEmpty String appId, @NotEmpty String serviceId, ArtifactStreamBinding artifactStreamBinding) {
    Service service = serviceResourceService.getWithDetails(appId, serviceId);
    if (service == null) {
      throw new InvalidRequestException("Service does not exist", USER);
    }
    ensureMultiArtifactSupport(service, artifactStreamBinding);

    ArtifactStreamBinding existingArtifactStreamBinding =
        getInternal(appId, serviceId, artifactStreamBinding.getName());
    if (existingArtifactStreamBinding != null) {
      throw new InvalidRequestException(
          format(
              "Artifact variable [%s] already exists. Please specify a unique name for artifact variable and try again.",
              artifactStreamBinding.getName()),
          USER);
    }

    List<String> allowedList = new ArrayList<>();
    if (artifactStreamBinding.getArtifactStreams() != null) {
      for (ArtifactStreamSummary streamSummary : artifactStreamBinding.getArtifactStreams()) {
        if (streamSummary != null && streamSummary.getArtifactStreamId() != null
            && !allowedList.contains(streamSummary.getArtifactStreamId())) {
          allowedList.add(streamSummary.getArtifactStreamId());
        }
      }
    }

    ServiceVariable variable = ServiceVariable.builder()
                                   .name(artifactStreamBinding.getName())
                                   .type(ServiceVariableType.ARTIFACT)
                                   .entityType(EntityType.SERVICE)
                                   .entityId(serviceId)
                                   .allowedList(allowedList)
                                   .build();
    serviceVariableService.saveWithChecks(appId, variable);
    return artifactStreamBinding;
  }

  @Override
  public ArtifactStreamBinding update(@NotEmpty String appId, @NotEmpty String serviceId, @NotEmpty String name,
      ArtifactStreamBinding artifactStreamBinding) {
    Service service = serviceResourceService.getWithDetails(appId, serviceId);
    if (service == null) {
      throw new InvalidRequestException("Service does not exist", USER);
    }
    ensureMultiArtifactSupport(service, artifactStreamBinding);

    // Check if artifact variable being updated exists.
    List<ServiceVariable> variables = fetchArtifactServiceVariableByName(appId, serviceId, name);
    if (isEmpty(variables)) {
      throw new InvalidRequestException("Artifact stream binding does not exist", USER);
    }

    // Check if new artifact variable name provided is unique within the service.
    if (!name.equals(artifactStreamBinding.getName())) {
      List<ServiceVariable> collidingVariables =
          fetchArtifactServiceVariableByName(appId, serviceId, artifactStreamBinding.getName());
      if (isNotEmpty(collidingVariables)) {
        throw new InvalidRequestException(
            format("Artifact variable with name [%s] already exists in service", artifactStreamBinding.getName()),
            USER);
      }
    }

    List<String> allowedList = new ArrayList<>();
    if (artifactStreamBinding.getArtifactStreams() != null) {
      for (ArtifactStreamSummary streamSummary : artifactStreamBinding.getArtifactStreams()) {
        if (streamSummary != null && streamSummary.getArtifactStreamId() != null
            && !allowedList.contains(streamSummary.getArtifactStreamId())) {
          allowedList.add(streamSummary.getArtifactStreamId());
        }
      }
    }

    ServiceVariable variable = variables.get(0);
    variable.setName(artifactStreamBinding.getName());
    variable.setAllowedList(allowedList);
    serviceVariableService.updateWithChecks(appId, variable.getUuid(), variable);
    return artifactStreamBinding;
  }

  @Override
  public void delete(@NotEmpty String appId, @NotEmpty String serviceId, @NotEmpty String name) {
    Service service = serviceResourceService.getWithDetails(appId, serviceId);
    if (service == null) {
      throw new InvalidRequestException("Service does not exist", USER);
    }

    List<ServiceVariable> variables = fetchArtifactServiceVariableByName(appId, serviceId, name);
    if (isEmpty(variables)) {
      throw new InvalidRequestException("Artifact stream binding does not exist", USER);
    }

    serviceVariableService.deleteWithChecks(appId, variables.get(0).getUuid());
  }

  private void ensureMultiArtifactSupport(Service service, ArtifactStreamBinding artifactStreamBinding) {
    // Right now only supported for SSH and K8s V2.
    // TODO: ASR: Add support for other deployment types, especially ECS and Helm.
    if (SSH == service.getDeploymentType() || (KUBERNETES == service.getDeploymentType() && service.isK8sV2())) {
      return;
    }

    if (!DEFAULT_ARTIFACT_VARIABLE_NAME.equals(artifactStreamBinding.getName())) {
      throw new InvalidRequestException(
          format("Artifact variable name other than '%s' is only supported for SSH and K8s V2 deployments",
              DEFAULT_ARTIFACT_VARIABLE_NAME));
    }
  }

  @Override
  public List<ArtifactStreamBinding> list(@NotEmpty String appId, @NotEmpty String serviceId) {
    List<ServiceVariable> variables = fetchArtifactServiceVariables(appId, serviceId);
    return variables.stream()
        .map(variable -> {
          List<ArtifactStreamSummary> artifactStreams = new ArrayList<>();
          if (variable.getAllowedList() != null) {
            for (String artifactStreamId : variable.getAllowedList()) {
              ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
              if (artifactStream == null) {
                continue;
              }

              Artifact lastCollectedArtifact = artifactService.fetchLastCollectedArtifact(artifactStream);
              artifactStreams.add(prepareSummaryFromArtifactStream(artifactStream, lastCollectedArtifact));
            }
          }

          return ArtifactStreamBinding.builder().name(variable.getName()).artifactStreams(artifactStreams).build();
        })
        .collect(Collectors.toList());
  }

  @Override
  public ArtifactStreamBinding get(@NotEmpty String appId, @NotEmpty String serviceId, @NotEmpty String name) {
    ArtifactStreamBinding variable = getInternal(appId, serviceId, name);
    if (variable == null) {
      throw new InvalidRequestException("Artifact stream binding does not exist", USER);
    }

    return variable;
  }

  private ArtifactStreamBinding getInternal(String appId, String serviceId, String name) {
    List<ServiceVariable> variables = fetchArtifactServiceVariableByName(appId, serviceId, name);
    if (isEmpty(variables)) {
      return null;
    }

    ServiceVariable variable = variables.get(0);
    List<ArtifactStreamSummary> artifactStreams = new ArrayList<>();
    if (variable.getAllowedList() != null) {
      for (String artifactStreamId : variable.getAllowedList()) {
        ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
        if (artifactStream == null) {
          continue;
        }

        Artifact lastCollectedArtifact = artifactService.fetchLastCollectedArtifact(artifactStream);
        artifactStreams.add(prepareSummaryFromArtifactStream(artifactStream, lastCollectedArtifact));
      }
    }

    return ArtifactStreamBinding.builder().name(variable.getName()).artifactStreams(artifactStreams).build();
  }

  @Override
  public List<ServiceVariable> fetchArtifactServiceVariables(String appId, String serviceId) {
    return serviceVariableService.list(
        aPageRequest()
            .addFilter(ServiceVariableKeys.appId, Operator.EQ, appId)
            .addFilter(ServiceVariableKeys.entityType, Operator.EQ, EntityType.SERVICE)
            .addFilter(ServiceVariableKeys.entityId, Operator.EQ, serviceId)
            .addFilter(ServiceVariableKeys.type, Operator.EQ, ServiceVariableType.ARTIFACT)
            .addOrder(ServiceVariableKeys.name, OrderType.ASC)
            .build());
  }

  @Override
  public List<ServiceVariable> fetchArtifactServiceVariableByName(String appId, String serviceId, String name) {
    return serviceVariableService.list(
        aPageRequest()
            .addFilter(ServiceVariableKeys.appId, Operator.EQ, appId)
            .addFilter(ServiceVariableKeys.entityType, Operator.EQ, EntityType.SERVICE)
            .addFilter(ServiceVariableKeys.entityId, Operator.EQ, serviceId)
            .addFilter(ServiceVariableKeys.type, Operator.EQ, ServiceVariableType.ARTIFACT)
            .addFilter(ServiceVariableKeys.name, Operator.EQ, name)
            .build());
  }

  @Override
  public List<ServiceVariable> fetchArtifactServiceVariableByArtifactStreamId(
      String accountId, String artifactStreamId) {
    return getServiceVariablesByArtifactStreamId(accountId, artifactStreamId);
  }

  private List<ServiceVariable> getServiceVariablesByArtifactStreamId(
      String appId, String accountId, String artifactStreamId) {
    return serviceVariableService.list(aPageRequest()
                                           .addFilter(ServiceVariableKeys.accountId, Operator.EQ, accountId)
                                           .addFilter(ServiceVariableKeys.appId, Operator.EQ, appId)
                                           .addFilter(ServiceVariableKeys.entityType, Operator.EQ, EntityType.SERVICE)
                                           .addFilter(ServiceVariableKeys.allowedList, Operator.EQ, artifactStreamId)
                                           .build());
  }

  private List<ServiceVariable> getServiceVariablesByArtifactStreamId(String accountId, String artifactStreamId) {
    return serviceVariableService.list(aPageRequest()
                                           .addFilter(ServiceVariableKeys.accountId, Operator.EQ, accountId)
                                           .addFilter(ServiceVariableKeys.entityType, Operator.EQ, EntityType.SERVICE)
                                           .addFilter(ServiceVariableKeys.allowedList, Operator.EQ, artifactStreamId)
                                           .build());
  }

  @Override
  public ArtifactStream createOld(String appId, String serviceId, String artifactStreamId) {
    log.info(ARTIFACT_STREAM_DEBUG_LOG + "Linking artifact stream id: {} to service {}", artifactStreamId, serviceId);
    Service service = serviceResourceService.get(appId, serviceId, false);
    if (service == null) {
      throw new InvalidRequestException("Service does not exist", USER);
    }

    ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
    if (artifactStream == null) {
      throw new InvalidRequestException("Artifact stream does not exist", USER);
    }

    serviceResourceService.addArtifactStreamId(service, artifactStreamId);
    return artifactStream;
  }

  @Override
  public boolean deleteOld(String appId, String serviceId, String artifactStreamId) {
    Service service = serviceResourceService.get(appId, serviceId, false);
    if (service == null) {
      throw new InvalidRequestException("Service does not exist", USER);
    }

    return deleteOld(service, artifactStreamId);
  }

  @Override
  public List<String> listArtifactStreamIds(String appId, String serviceId) {
    if (GLOBAL_APP_ID.equals(appId)) {
      return listArtifactStreamIds(serviceId);
    }

    Service service = serviceResourceService.get(appId, serviceId, false);
    if (service == null) {
      return new ArrayList<>();
    }

    return listArtifactStreamIds(service);
  }

  @Override
  public List<String> listArtifactStreamIds(String serviceId) {
    Service service = serviceResourceService.get(serviceId);
    if (service == null) {
      return new ArrayList<>();
    }

    return listArtifactStreamIds(service);
  }

  @Override
  public List<String> listArtifactStreamIds(Service service) {
    if (!hasFeatureFlag(service.getAccountId())) {
      if (service.getArtifactStreamIds() == null) {
        return new ArrayList<>();
      }

      return service.getArtifactStreamIds();
    }

    List<ServiceVariable> serviceVariables = fetchArtifactServiceVariables(service.getAppId(), service.getUuid());
    if (isEmpty(serviceVariables)) {
      return new ArrayList<>();
    }

    // list artifact stream ids and remove duplicates
    return serviceVariables.stream()
        .flatMap(serviceVariable -> {
          if (serviceVariable.getAllowedList() == null) {
            return Stream.empty();
          }

          return serviceVariable.getAllowedList().stream();
        })
        .distinct()
        .collect(Collectors.toList());
  }

  @Override
  public List<ArtifactStream> listArtifactStreams(String appId, String serviceId) {
    return listArtifactStreams(listArtifactStreamIds(appId, serviceId));
  }

  @Override
  public List<ArtifactStream> listArtifactStreams(String serviceId) {
    return listArtifactStreams(listArtifactStreamIds(serviceId));
  }

  @Override
  public List<ArtifactStream> listArtifactStreams(Service service) {
    return listArtifactStreams(listArtifactStreamIds(service));
  }

  private List<ArtifactStream> listArtifactStreams(List<String> artifactStreamIds) {
    if (isEmpty(artifactStreamIds)) {
      return new ArrayList<>();
    }
    return artifactStreamService.listByIds(artifactStreamIds);
  }

  // TODO: ASR: most invocations of the methods below will use setting instead of service after refactoring.

  @Override
  public List<String> listServiceIds(String appId, String artifactStreamId) {
    ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
    if (artifactStream == null) {
      return new ArrayList<>();
    }

    if (!hasFeatureFlag(artifactStream.getAccountId())) {
      return listServices(appId, artifactStreamId).stream().map(Service::getUuid).collect(Collectors.toList());
    }

    List<ServiceVariable> serviceVariables =
        getServiceVariablesByArtifactStreamId(appId, artifactStream.getAccountId(), artifactStreamId);
    if (isEmpty(serviceVariables)) {
      return new ArrayList<>();
    }

    return serviceVariables.stream().map(ServiceVariable::getEntityId).distinct().collect(Collectors.toList());
  }

  @Override
  public List<String> listServiceIds(String artifactStreamId) {
    ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
    if (artifactStream == null) {
      return new ArrayList<>();
    }

    if (!hasFeatureFlag(artifactStream.getAccountId())) {
      return listServices(artifactStreamId).stream().map(Service::getUuid).collect(Collectors.toList());
    }

    List<ServiceVariable> serviceVariables =
        getServiceVariablesByArtifactStreamId(artifactStream.getAccountId(), artifactStreamId);
    if (isEmpty(serviceVariables)) {
      return new ArrayList<>();
    }

    return serviceVariables.stream().map(ServiceVariable::getEntityId).distinct().collect(Collectors.toList());
  }

  @Override
  public List<Service> listServices(String appId, String artifactStreamId) {
    if (GLOBAL_APP_ID.equals(appId)) {
      return listServices(artifactStreamId);
    }

    ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
    if (artifactStream == null) {
      return new ArrayList<>();
    }

    if (!hasFeatureFlag(artifactStream.getAccountId())) {
      String serviceId = artifactStream.getServiceId();
      Service service = serviceResourceService.get(serviceId);
      return service == null ? null : Collections.singletonList(service);
    }

    List<ServiceVariable> serviceVariables =
        getServiceVariablesByArtifactStreamId(appId, artifactStream.getAccountId(), artifactStreamId);
    if (isEmpty(serviceVariables)) {
      return new ArrayList<>();
    }

    List<String> serviceIds =
        serviceVariables.stream().map(ServiceVariable::getEntityId).distinct().collect(Collectors.toList());
    if (isEmpty(serviceIds)) {
      return new ArrayList<>();
    }

    return serviceResourceService.fetchServicesByUuids(appId, serviceIds);
  }

  @Override
  public List<Service> listServices(String artifactStreamId) {
    ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
    if (artifactStream == null) {
      return new ArrayList<>();
    }

    if (!hasFeatureFlag(artifactStream.getAccountId())) {
      return serviceResourceService.listByArtifactStreamId(artifactStreamId);
    }

    List<ServiceVariable> serviceVariables =
        getServiceVariablesByArtifactStreamId(artifactStream.getAccountId(), artifactStreamId);
    if (isEmpty(serviceVariables)) {
      return new ArrayList<>();
    }

    List<String> serviceIds =
        serviceVariables.stream().map(ServiceVariable::getEntityId).distinct().collect(Collectors.toList());
    if (isEmpty(serviceIds)) {
      return new ArrayList<>();
    }

    return serviceResourceService.fetchServicesByUuidsByAccountId(artifactStream.getAccountId(), serviceIds);
  }

  @Override
  public List<Workflow> listWorkflows(String artifactStreamId) {
    ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
    if (artifactStream == null) {
      return new ArrayList<>();
    }
    return workflowService.listWorkflows(artifactStreamId, artifactStream.getAccountId());
  }

  // TODO: ASR: make sure throwException is false after refactor to connector level artifact

  @Override
  public Service getService(String appId, String artifactStreamId, boolean throwException) {
    List<Service> services = listServices(appId, artifactStreamId);
    if (isEmpty(services)) {
      if (throwException) {
        log.info(ARTIFACT_STREAM_DEBUG_LOG + "Deleting artifact stream {} from getService", artifactStreamId);
        artifactStreamService.delete(appId, artifactStreamId);
        throw new InvalidArtifactServerException(
            format("Artifact stream %s is a zombie.", artifactStreamId), Level.INFO, USER);
      }
      return null;
    }

    return services.get(0);
  }

  @Override
  public String getServiceId(String appId, String artifactStreamId, boolean throwException) {
    return getServiceId(appId, listServiceIds(appId, artifactStreamId), artifactStreamId, throwException);
  }

  private String getServiceId(String appId, List<String> serviceIds, String artifactStreamId, boolean throwException) {
    if (isEmpty(serviceIds)) {
      if (throwException) {
        artifactStreamService.delete(appId, artifactStreamId);
        throw new InvalidArtifactServerException(
            format("Artifact stream %s is a zombie.", artifactStreamId), Level.INFO, USER);
      }
      return null;
    }

    return serviceIds.get(0);
  }

  @Override
  public void deleteByArtifactStream(String artifactStreamId, boolean syncFromGit) {
    // Only applicable for feature flag off as after the refactor artifact streams are unrelated to services.
    // We can't fetch the artifact stream here as it is most likely already deleted from the DB.
    List<Service> services = serviceResourceService.listByArtifactStreamId(artifactStreamId);
    if (isEmpty(services)) {
      return;
    }

    services.forEach(service -> {
      service.setSyncFromGit(syncFromGit);
      deleteOld(service, artifactStreamId);
    });
  }

  private boolean deleteOld(Service service, String artifactStreamId) {
    serviceResourceService.removeArtifactStreamId(service, artifactStreamId);
    return true;
  }

  private boolean hasFeatureFlag(String accountId) {
    if (accountId == null) {
      return false;
    }
    return featureFlagService.isEnabled(FeatureName.ARTIFACT_STREAM_REFACTOR, accountId);
  }

  @Override
  public void processServiceVariables(List<ServiceVariable> serviceVariables) {
    if (isNotEmpty(serviceVariables)) {
      for (ServiceVariable serviceVariable : serviceVariables) {
        List<ArtifactStreamSummary> artifactStreams = new ArrayList<>();
        if (ServiceVariableType.ARTIFACT == serviceVariable.getType()) {
          if (isNotEmpty(serviceVariable.getAllowedList())) {
            for (String artifactStreamId : serviceVariable.getAllowedList()) {
              ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
              if (artifactStream == null) {
                continue;
              }
              Artifact lastCollectedArtifact = artifactService.fetchLastCollectedArtifact(artifactStream);
              artifactStreams.add(prepareSummaryFromArtifactStream(artifactStream, lastCollectedArtifact));
            }
            serviceVariable.setArtifactStreamSummaries(artifactStreams);
          }
        }
      }
    }
  }

  @Override
  public void processVariables(List<Variable> variables) {
    if (isNotEmpty(variables)) {
      for (Variable variable : variables) {
        List<ArtifactStreamSummary> artifactStreams = new ArrayList<>();
        if (VariableType.ARTIFACT == variable.getType()) {
          if (isNotEmpty(variable.getAllowedList())) {
            for (String artifactStreamId : variable.getAllowedList()) {
              ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
              if (artifactStream == null) {
                continue;
              }
              Artifact lastCollectedArtifact = artifactService.fetchLastCollectedArtifact(artifactStream);
              artifactStreams.add(prepareSummaryFromArtifactStream(artifactStream, lastCollectedArtifact));
            }
            variable.setArtifactStreamSummaries(artifactStreams);
          }
        }
      }
    }
  }

  public static ArtifactStreamSummary prepareSummaryFromArtifactStream(
      ArtifactStream artifactStream, Artifact lastCollectedArtifact) {
    if (artifactStream == null) {
      return null;
    }

    String lastCollectedArtifactName = null;
    if (lastCollectedArtifact != null) {
      lastCollectedArtifactName = lastCollectedArtifact.getBuildNo();
    }
    return ArtifactStreamSummary.builder()
        .artifactStreamId(artifactStream.getUuid())
        .settingId(artifactStream.getSettingId())
        .displayName(artifactStream.getName())
        .lastCollectedArtifact(lastCollectedArtifactName)
        .name(artifactStream.getName())
        .build();
  }
}
