/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package software.wings.graphql.datafetcher.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_APPLICATIONS;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.pcf.model.CfCliVersion;

import software.wings.api.DeploymentType;
import software.wings.beans.Application;
import software.wings.beans.Service;
import software.wings.graphql.datafetcher.BaseMutatorDataFetcher;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.datafetcher.application.ApplicationController;
import software.wings.graphql.schema.mutation.service.input.QLUpdateServiceMetadataInput;
import software.wings.graphql.schema.mutation.service.payload.QLUpdateServiceMetadataPayload;
import software.wings.graphql.schema.type.QLApplication;
import software.wings.graphql.schema.type.QLService;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ServiceResourceService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class UpdateServiceMetadataDataFetcher
    extends BaseMutatorDataFetcher<QLUpdateServiceMetadataInput, QLUpdateServiceMetadataPayload> {
  private final ServiceResourceService serviceResourceService;
  private final FeatureFlagService featureFlagService;
  private final AppService appService;

  @Inject
  public UpdateServiceMetadataDataFetcher(
      ServiceResourceService serviceResourceService, FeatureFlagService featureFlagService, AppService appService) {
    super(QLUpdateServiceMetadataInput.class, QLUpdateServiceMetadataPayload.class);
    this.serviceResourceService = serviceResourceService;
    this.featureFlagService = featureFlagService;
    this.appService = appService;
  }

  @Override
  @AuthRule(permissionType = MANAGE_APPLICATIONS, action = PermissionAttribute.Action.UPDATE)
  protected QLUpdateServiceMetadataPayload mutateAndFetch(
      QLUpdateServiceMetadataInput parameter, MutationContext mutationContext) {
    if (isEmpty(parameter.getApplicationId())) {
      throw new InvalidRequestException("Application ID list is not provided");
    }

    if (isNull(parameter.getCfCliVersion())) {
      throw new InvalidRequestException("CfCli version is not provided");
    }

    List<QLService> updatedServices = new ArrayList<>();
    List<String> excludeServiceIds = new ArrayList<>();
    List<QLApplication> applicationList = new ArrayList<>();

    if (parameter.getExcludeServices().isPresent()) {
      excludeServiceIds = parameter.getExcludeServices().getValue().orElse(Collections.emptyList());
    }
    final Application applicationForFFCheck = appService.get(parameter.getApplicationId().get(0));

    if (!featureFlagService.isEnabled(FeatureName.CF_CLI7, applicationForFFCheck.getAccountId())) {
      throw new InvalidRequestException("CF_CLI7 feature flag is not enabled");
    }

    for (String applicationId : parameter.getApplicationId()) {
      Application application = appService.get(applicationId);
      applicationList.add(ApplicationController.populateQLApplication(application, QLApplication.builder()).build());
      updateServicesForApplication(applicationId, updatedServices, excludeServiceIds, parameter.getCfCliVersion());
    }
    return QLUpdateServiceMetadataPayload.builder()
        .clientMutationId(parameter.getClientMutationId())
        .application(applicationList)
        .cfCliVersion(parameter.getCfCliVersion())
        .updatedService(updatedServices)
        .build();
  }

  private void updateServicesForApplication(String applicationId, List<QLService> updatedServices,
      List<String> excludeServiceIds, CfCliVersion cfCliVersion) {
    final List<Service> services = serviceResourceService.findServicesByApp(applicationId);
    services.forEach(service -> updateAndSaveService(service, updatedServices, excludeServiceIds, cfCliVersion));
  }

  private void updateAndSaveService(
      Service service, List<QLService> updatedServices, List<String> excludeServiceIds, CfCliVersion cfCliVersion) {
    if (DeploymentType.PCF.equals(service.getDeploymentType()) && !excludeServiceIds.contains(service.getUuid())) {
      service.setCfCliVersion(cfCliVersion);
      serviceResourceService.update(service);
      updatedServices.add(prepareQLService(service));
    }
  }

  private QLService prepareQLService(Service updatedService) {
    return ServiceController.buildQLService(updatedService);
  }
}
