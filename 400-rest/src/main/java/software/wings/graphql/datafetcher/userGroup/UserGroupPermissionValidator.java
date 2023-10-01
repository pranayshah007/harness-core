/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.userGroup;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.template.Template;
import software.wings.beans.template.Template.TemplateKeys;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.datafetcher.application.AppFilterController;
import software.wings.graphql.datafetcher.environment.EnvFilterController;
import software.wings.graphql.schema.type.QLAppFilter;
import software.wings.graphql.schema.type.QLAppFilterType;
import software.wings.graphql.schema.type.permissions.QLActions;
import software.wings.graphql.schema.type.permissions.QLAppPermission;
import software.wings.graphql.schema.type.permissions.QLExecutableElementFilterInput;
import software.wings.graphql.schema.type.permissions.QLExecutableElementFilterType;
import software.wings.graphql.schema.type.permissions.QLPermissionType;
import software.wings.graphql.schema.type.permissions.QLUserGroupPermissions;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.template.TemplateService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/*
 * Class to validate that the input given by the user is correct.
 * All the NULL checks should be done at this layer, so that we can assume that the input passed
 * to the UserController is correct
 * */

@OwnedBy(PL)
@Slf4j
@Singleton
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class UserGroupPermissionValidator {
  @Inject EnvFilterController envFilterController;
  @Inject WorkflowService workflowService;
  @Inject PipelineService pipelineService;
  @Inject ServiceResourceService serviceResourceService;
  @Inject InfrastructureProvisionerService infrastructureProvisionerService;
  @Inject TemplateService templateService;
  @Inject AppFilterController appFilterController;
  @Inject WingsPersistence wingsPersistence;

  private void checkForInvalidIds(List<String> idsInput, List<String> idsPresent) {
    idsInput.removeAll(idsPresent);
    if (isNotEmpty(idsInput)) {
      throw new InvalidRequestException(
          String.format("Invalid id/s %s provided in the request", String.join(", ", idsInput)));
    }
  }

  private void checkServiceExists(Set<String> serviceIds, String accountId) {
    if (isEmpty(serviceIds)) {
      return;
    }
    List<String> ids = new ArrayList<>(serviceIds);
    PageRequest<Service> req = aPageRequest()
                                   .addFieldsIncluded("_id")
                                   .addFilter("accountId", SearchFilter.Operator.EQ, accountId)
                                   .addFilter("_id", IN, serviceIds.toArray())
                                   .build();
    PageResponse<Service> res = serviceResourceService.list(req, false, false, false, null);
    // This Ids are wrong
    List<String> idsPresent = res.stream().map(Service::getUuid).collect(Collectors.toList());
    checkForInvalidIds(ids, idsPresent);
  }

  private void checkTemplatesExists(Set<String> templateIds, String accountId) {
    if (isEmpty(templateIds)) {
      return;
    }
    List<String> ids = new ArrayList<>(templateIds);
    final List<Template> templates = wingsPersistence.createQuery(Template.class)
                                         .filter(TemplateKeys.accountId, accountId)
                                         .field(TemplateKeys.uuid)
                                         .in(templateIds)
                                         .asList();
    // This Ids are wrong
    List<String> idsPresent = templates.stream().map(Template::getUuid).collect(Collectors.toList());
    checkForInvalidIds(ids, idsPresent);
  }

  private void checkWorkflowExists(Set<String> workflowIds, String accountId) {
    if (isEmpty(workflowIds)) {
      return;
    }
    List<String> ids = new ArrayList<>(workflowIds);
    PageRequest<Workflow> req = aPageRequest()
                                    .addFieldsIncluded("_id")
                                    .addFilter("accountId", SearchFilter.Operator.EQ, accountId)
                                    .addFilter("_id", IN, workflowIds.toArray())
                                    .build();
    PageResponse<Workflow> res = workflowService.listWorkflows(req);
    // This Ids are wrong
    List<String> idsPresent = res.stream().map(Workflow::getUuid).collect(Collectors.toList());
    checkForInvalidIds(ids, idsPresent);
  }

  private void checkPipelineExists(Set<String> pipelineIds, String accountId) {
    if (isEmpty(pipelineIds)) {
      return;
    }
    List<String> ids = new ArrayList<>(pipelineIds);
    PageRequest<Pipeline> req = aPageRequest()
                                    .addFieldsIncluded("_id")
                                    .addFilter("accountId", SearchFilter.Operator.EQ, accountId)
                                    .addFilter("_id", IN, pipelineIds.toArray())
                                    .build();
    PageResponse<Pipeline> res = pipelineService.listPipelines(req, false, accountId);
    List<String> idsPresent = res.stream().map(Pipeline::getUuid).collect(Collectors.toList());
    checkForInvalidIds(ids, idsPresent);
  }

  private void checkProvisionerExists(Set<String> provisionersIds, String accountId) {
    if (isEmpty(provisionersIds)) {
      return;
    }
    List<String> ids = new ArrayList<>(provisionersIds);
    PageRequest<InfrastructureProvisioner> req = aPageRequest()
                                                     .addFieldsIncluded("_id")
                                                     .addFilter("accountId", SearchFilter.Operator.EQ, accountId)
                                                     .addFilter("_id", IN, provisionersIds.toArray())
                                                     .build();
    PageResponse<InfrastructureProvisioner> res = infrastructureProvisionerService.list(req);
    // This Ids are wrong
    List<String> idsPresent = res.stream().map(InfrastructureProvisioner::getUuid).collect(Collectors.toList());
    checkForInvalidIds(ids, idsPresent);
  }

  private void checkWhetherIdsAreCorrect(QLAppPermission appPermissions, String accountId) {
    appFilterController.checkApplicationsExists(appPermissions.getApplications().getAppIds(), accountId);
    switch (appPermissions.getPermissionType()) {
      case SERVICE:
        checkServiceExists(appPermissions.getServices().getServiceIds(), accountId);
        break;
      case TEMPLATE:
        checkTemplatesExists(appPermissions.getTemplates().getTemplateIds(), accountId);
        break;
      case ENV:
        envFilterController.checkEnvExists(appPermissions.getEnvironments().getEnvIds(), accountId);
        break;
      case WORKFLOW:
        envFilterController.checkEnvExists(appPermissions.getWorkflows().getEnvIds(), accountId);
        checkWorkflowExists(appPermissions.getWorkflows().getWorkflowIds(), accountId);
        break;
      case PIPELINE:
        envFilterController.checkEnvExists(appPermissions.getPipelines().getEnvIds(), accountId);
        checkPipelineExists(appPermissions.getPipelines().getPipelineIds(), accountId);
        break;
      case DEPLOYMENT:
        envFilterController.checkEnvExists(appPermissions.getDeployments().getEnvIds(), accountId);
        break;
      case PROVISIONER:
        checkProvisionerExists(appPermissions.getProvisioners().getProvisionerIds(), accountId);
        break;
      default:
        break;
    }
  }

  private void validateTheActions(QLPermissionType permissionType, Set<QLActions> actions) {
    // If no actions is provided, we will ask the user to give the actions field
    if (isEmpty(actions)) {
      throw new InvalidRequestException(
          String.format("No Actions Supplied for the %s permission type", permissionType.getStringValue()));
    }
    if (permissionType != QLPermissionType.ALL) {
      if (permissionType == QLPermissionType.DEPLOYMENT) {
        if (actions.contains(QLActions.CREATE)) {
          throw new InvalidRequestException(
              String.format("Invalid action CREATE for the %s permission type", permissionType.getStringValue()));
        }
        if (actions.contains(QLActions.UPDATE)) {
          throw new InvalidRequestException(
              String.format("Invalid action UPDATE for the %s permission type", permissionType.getStringValue()));
        }
        if (actions.contains(QLActions.DELETE)) {
          throw new InvalidRequestException(
              String.format("Invalid action DELETE for the %s permission type", permissionType.getStringValue()));
        }
      } else {
        // All other PermissionType doesn't support the execute operation
        if (actions.contains(QLActions.EXECUTE) || actions.contains(QLActions.EXECUTE_PIPELINE)
            || actions.contains(QLActions.EXECUTE_WORKFLOW) || actions.contains(QLActions.ROLLBACK_WORKFLOW)
            || actions.contains(QLActions.ABORT_WORKFLOW)) {
          throw new InvalidRequestException(
              String.format("Invalid action EXECUTE  for the %s permission type", permissionType.getStringValue()));
        }
      }
    }
  }

  private void checkThePermissionFilterIsNotNull(QLAppPermission appPermission) {
    switch (appPermission.getPermissionType()) {
      case ALL:
        return;
      case SERVICE:
        if (appPermission.getServices() != null
            && (isNotEmpty(appPermission.getServices().getServiceIds())
                || appPermission.getServices().getFilterType() != null)) {
          return;
        }
        break;
      case TEMPLATE:
        if (appPermission.getTemplates() != null
            && (isNotEmpty(appPermission.getTemplates().getTemplateIds())
                || appPermission.getTemplates().getFilterType() != null)) {
          return;
        }
        break;
      case ENV:
        if (appPermission.getEnvironments() != null
            && (isNotEmpty(appPermission.getEnvironments().getEnvIds())
                || isNotEmpty(appPermission.getEnvironments().getFilterTypes()))) {
          return;
        }
        break;
      case WORKFLOW:
        if (appPermission.getWorkflows() != null
            && (isNotEmpty(appPermission.getWorkflows().getEnvIds())
                || isNotEmpty(appPermission.getWorkflows().getWorkflowIds())
                || isNotEmpty(appPermission.getWorkflows().getFilterTypes()))) {
          return;
        }
        break;
      case PIPELINE:
        if (appPermission.getPipelines() != null
            && (isNotEmpty(appPermission.getPipelines().getEnvIds())
                || isNotEmpty(appPermission.getPipelines().getPipelineIds())
                || isNotEmpty(appPermission.getPipelines().getFilterTypes()))) {
          return;
        }
        break;
      case DEPLOYMENT:
        if (appPermission.getDeployments() != null
            && (isNotEmpty(appPermission.getDeployments().getEnvIds())
                || isNotEmpty(appPermission.getDeployments().getFilterTypes()))) {
          return;
        }
        break;
      case PROVISIONER:
        if (appPermission.getProvisioners() != null
            && (isNotEmpty(appPermission.getProvisioners().getProvisionerIds())
                || appPermission.getProvisioners().getFilterType() != null)) {
          return;
        }
        break;
      default:
        throw new InvalidRequestException("Invalid PermissionType Given by the user");
    }
    throw new InvalidRequestException(String.format("No %s filter provided with the permission with type %s",
        appPermission.getPermissionType(), appPermission.getPermissionType().getStringValue()));
  }

  private void checkWhetherPermissionIsValid(QLAppPermission appPermission, String accountId) {
    // Check that the appFilter should not be NULL
    QLAppFilter application = appPermission.getApplications();
    if (appPermission.getPermissionType() == null) {
      throw new InvalidRequestException("No permission type given in the Application Permission");
    }
    appFilterController.validateAppFilter(application, accountId);
    checkThePermissionFilterIsNotNull(appPermission);
  }

  private void checkAllAppPermissionFilter(QLAppPermission appPermission) {
    // If the user has given the ids, then we won't consider the filterType thus no need to check for filterType All
    QLAppFilter application = appPermission.getApplications();
    if (isNotEmpty(application.getAppIds())) {
      return;
    }
    // If ids is empty then filterType will be there as we already did a check for it, and it can only take value ALL
    switch (appPermission.getPermissionType()) {
      case ALL:
        return;
      case SERVICE:
        if (appPermission.getServices().getServiceIds() == null) {
          return;
        }
        break;
      case TEMPLATE:
        if (appPermission.getTemplates().getTemplateIds() == null) {
          return;
        }
        break;
      case ENV:
        if (appPermission.getEnvironments().getEnvIds() == null) {
          return;
        }
        break;
      case WORKFLOW:
        if (appPermission.getWorkflows().getEnvIds() == null && appPermission.getWorkflows().getWorkflowIds() == null) {
          return;
        }
        break;
      case PIPELINE:
        if (appPermission.getPipelines().getEnvIds() == null && appPermission.getPipelines().getPipelineIds() == null) {
          return;
        }
        break;
      case DEPLOYMENT:
        if (appPermission.getDeployments().getEnvIds() == null) {
          return;
        }
        break;
      case PROVISIONER:
        if (appPermission.getProvisioners().getProvisionerIds() == null) {
          return;
        }
        break;
      default:
        log.info("Invalid PermissionType Given by the user");
    }
    throw new InvalidRequestException(
        String.format("%s Ids should not be supplied with AppFilter=\"ALL Applications\" for filterType %s",
            appPermission.getPermissionType(), appPermission.getPermissionType()));
  }

  void validateAppPermission(String accountId, List<QLAppPermission> appPermissions) {
    if (isEmpty(appPermissions)) {
      return;
    }
    for (QLAppPermission appPermission : appPermissions) {
      // Check that for a particular permissionType their filterType should also be given
      checkWhetherPermissionIsValid(appPermission, accountId);
      // Check that the action is valid for that permissionType
      validateTheActions(appPermission.getPermissionType(), appPermission.getActions());
      // Check that ids should not be supplied when filterType ALL is selected for the application
      checkAllAppPermissionFilter(appPermission);
      // Check that the user supplied the correct id
      checkWhetherIdsAreCorrect(appPermission, accountId);
      // Check for executable element filter
      checkWhetherExecutableElementFilterIsCorrect(appPermission, accountId);
    }
  }

  private void checkWhetherExecutableElementFilterIsCorrect(QLAppPermission appPermission, String accountId) {
    final QLAppFilter applications = appPermission.getApplications();
    if (applications != null && appPermission.getDeployments() != null) {
      final QLExecutableElementFilterInput executableElementFilter =
          appPermission.getDeployments().getExecutableElementFilter();
      if (QLAppFilterType.EXCLUDE_SELECTED.equals(applications.getFilterType()) && executableElementFilter != null) {
        throw new InvalidRequestException(
            "Both exclude selected and execultable element filter can't be set at same time");
      }
      if (executableElementFilter != null
          && !QLExecutableElementFilterType.ALL.equals(executableElementFilter.getExecutableElementFilterType())) {
        checkPipelineExists(executableElementFilter.getIds(), accountId);
      }
    }
  }

  // it is clear form where the error is happening
  void validatePermission(QLUserGroupPermissions permissions, String accountId) {
    if (permissions == null) {
      return;
    }
    validateAppPermission(accountId, permissions.getAppPermissions());
  }
}
