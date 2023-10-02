/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_UPDATE_PERMISSION;

import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.configfile.ConfigFile;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideRequestDTOV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesSpec;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.scope.ScopeHelper;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.core.variables.NGVariable;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@Singleton
@Slf4j
public class ServiceOverrideValidatorServiceImpl implements ServiceOverrideValidatorService {
  @Inject private ServiceOverrideValidatorFactory overrideValidatorFactory;
  @Inject private OverrideV2AccessControlCheckHelper overrideV2AccessControlCheckHelper;
  @Inject private EnvironmentValidationHelper environmentValidationHelper;
  @Inject private OrgAndProjectValidationHelper orgAndProjectValidationHelper;

  @Override
  public void validateRequestOrThrow(@NonNull ServiceOverrideRequestDTOV2 requestDTOV2, @NonNull String accountId) {
    validateServiceOverrideRequestBasicChecksOrThrow(requestDTOV2, accountId);
    validateEnvWithRBACOrThrow(accountId, requestDTOV2.getOrgIdentifier(), requestDTOV2.getProjectIdentifier(),
        requestDTOV2.getEnvironmentRef());
    ServiceOverrideTypeBasedRequestParamsHandler validator =
        overrideValidatorFactory.getTypeBasedValidator(requestDTOV2.getType());
    validator.validateRequest(requestDTOV2, accountId);
  }

  @Override
  public void validateEnvWithRBACOrThrow(
      @NonNull String accountId, String orgId, String projectId, @NonNull String environmentRef) {
    Environment environment = checkIfEnvExistAndReturn(accountId, orgId, projectId, environmentRef);
    validateEnvironmentRBACOrThrow(environment);
  }

  @Override
  public void checkForImmutablePropertiesOrThrow(
      NGServiceOverridesEntity existingEntity, NGServiceOverridesEntity requestedEntity) {
    List<String> mismatchedProperties = new ArrayList<>();
    List<String> requestedFields = new ArrayList<>();
    List<String> existingFields = new ArrayList<>();

    if (requestedEntity.getType() != null
        && !requestedEntity.getType().toString().equals(existingEntity.getType().toString())) {
      mismatchedProperties.add("type");
      requestedFields.add(requestedEntity.getType().toString());
      existingFields.add(existingEntity.getType().toString());
    }

    if (isNotEmpty(requestedEntity.getEnvironmentRef())
        && !requestedEntity.getEnvironmentRef().equals(existingEntity.getEnvironmentRef())) {
      mismatchedProperties.add("EnvironmentRef");
      requestedFields.add(requestedEntity.getEnvironmentRef());
      existingFields.add(existingEntity.getEnvironmentRef());
    }

    if ((isEmpty(existingEntity.getServiceRef()) && isNotEmpty(requestedEntity.getServiceRef()))
        || (isNotEmpty(requestedEntity.getServiceRef())
            && !requestedEntity.getServiceRef().equals(existingEntity.getServiceRef()))) {
      mismatchedProperties.add("ServiceRef");
      requestedFields.add(requestedEntity.getServiceRef());
      existingFields.add(existingEntity.getServiceRef());
    }

    if ((isEmpty(existingEntity.getInfraIdentifier()) && isNotEmpty(requestedEntity.getInfraIdentifier()))
        || (isNotEmpty(requestedEntity.getInfraIdentifier())
            && !existingEntity.getInfraIdentifier().equals(requestedEntity.getInfraIdentifier()))) {
      mismatchedProperties.add("InfraId");
      requestedFields.add(requestedEntity.getInfraIdentifier());
      existingFields.add(existingEntity.getInfraIdentifier());
    }

    if (isNotEmpty(mismatchedProperties)) {
      throw new InvalidRequestException(String.format(
          "Following fields: %s in requested entity %s does not match those values in existing entity %s for override Identifier: [%s], ProjectIdentifier: [%s] ,OrgIdentifier : [%s]",
          mismatchedProperties, requestedFields, existingFields, requestedEntity.getIdentifier(),
          requestedEntity.getProjectIdentifier(), existingEntity.getOrgIdentifier()));
    }
  }

  @Override
  public void validateServiceOverrideRequestBasicChecksOrThrow(
      @NonNull ServiceOverrideRequestDTOV2 serviceOverrideRequestDTOV2, @NonNull String accountId) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        serviceOverrideRequestDTOV2.getOrgIdentifier(), serviceOverrideRequestDTOV2.getProjectIdentifier(), accountId);
    throwExceptionForRequiredFields(serviceOverrideRequestDTOV2);
    validateServiceOverrideScopeAndThrow(serviceOverrideRequestDTOV2, accountId);
    validateSubFieldsDuplicationsAndThrow(serviceOverrideRequestDTOV2.getSpec());
  }

  private void validateSubFieldsDuplicationsAndThrow(@NonNull ServiceOverridesSpec spec) {
    checkDuplicateVariablesAndThrow(spec);
    checkDuplicateManifestAndThrow(spec);
    checkDuplicateConfigFilesAndThrow(spec);
  }

  private static void checkDuplicateConfigFilesAndThrow(@NotNull ServiceOverridesSpec spec) {
    if (isNotEmpty(spec.getConfigFiles())) {
      Set<String> existingUniqueIdentifier = new HashSet<>();
      List<String> duplicateIdentifiers = spec.getConfigFiles()
                                              .stream()
                                              .map(ConfigFileWrapper::getConfigFile)
                                              .map(ConfigFile::getIdentifier)
                                              .filter(identifier -> !existingUniqueIdentifier.add(identifier))
                                              .collect(Collectors.toList());
      if (isNotEmpty(duplicateIdentifiers)) {
        throw new InvalidRequestException(
            String.format("found duplicate config files %s in override request", duplicateIdentifiers.toString()));
      }
    }
  }

  private static void checkDuplicateManifestAndThrow(@NotNull ServiceOverridesSpec spec) {
    if (isNotEmpty(spec.getManifests())) {
      Set<String> existingUniqueIdentifier = new HashSet<>();
      List<String> duplicateIdentifiers = spec.getManifests()
                                              .stream()
                                              .map(ManifestConfigWrapper::getManifest)
                                              .map(ManifestConfig::getIdentifier)
                                              .filter(identifier -> !existingUniqueIdentifier.add(identifier))
                                              .collect(Collectors.toList());
      if (isNotEmpty(duplicateIdentifiers)) {
        throw new InvalidRequestException(
            String.format("found duplicate manifests %s in override request", duplicateIdentifiers.toString()));
      }
    }
  }

  private static void checkDuplicateVariablesAndThrow(@NotNull ServiceOverridesSpec spec) {
    if (isNotEmpty(spec.getVariables())) {
      Set<String> existingUniqueIdentifier = new HashSet<>();
      List<String> duplicateIdentifiers = spec.getVariables()
                                              .stream()
                                              .map(NGVariable::getName)
                                              .filter(identifier -> !existingUniqueIdentifier.add(identifier))
                                              .collect(Collectors.toList());
      if (isNotEmpty(duplicateIdentifiers)) {
        throw new InvalidRequestException(
            String.format("found duplicate vars %s in override request", duplicateIdentifiers.toString()));
      }
    }
  }

  @Override
  public void validateEnvironmentRBACOrThrow(@NonNull Environment environment) {
    overrideV2AccessControlCheckHelper.checkForEnvAndAttributesAccessOrThrow(
        ResourceScope.of(
            environment.getAccountId(), environment.getOrgIdentifier(), environment.getProjectIdentifier()),
        environment.getIdentifier(), ENVIRONMENT_UPDATE_PERMISSION, environment.getType().toString());
  }

  @Override
  @NonNull
  public String generateServiceOverrideIdentifier(@NonNull NGServiceOverridesEntity serviceOverridesEntity) {
    ServiceOverrideTypeBasedRequestParamsHandler validator =
        overrideValidatorFactory.getTypeBasedValidator(serviceOverridesEntity.getType());
    return validator.generateServiceOverrideIdentifier(serviceOverridesEntity);
  }

  @NonNull
  private Environment checkIfEnvExistAndReturn(
      String accountId, String orgId, String projectId, String environmentRef) {
    return environmentValidationHelper.checkThatEnvExists(accountId, orgId, projectId, environmentRef);
  }

  private void throwExceptionForRequiredFields(ServiceOverrideRequestDTOV2 dto) {
    if (dto == null) {
      throw new InvalidRequestException("No request body for Service overrides");
    }
    if (isEmpty(dto.getEnvironmentRef())) {
      throw new InvalidRequestException("No environment identifier provided in Service Overrides request");
    }
    if (dto.getType() == null) {
      throw new InvalidRequestException("Override type is not provided in request");
    }
    if (dto.getSpec() == null) {
      throw new InvalidRequestException("Override spec is not provided in request");
    }
    final ServiceOverridesSpec overrideSpec = dto.getSpec();
    if (isOverrideSpecEmpty(overrideSpec)) {
      throw new InvalidRequestException("Override spec is empty in request");
    }
  }

  private boolean isOverrideSpecEmpty(ServiceOverridesSpec overrideSpec) {
    return isEmpty(overrideSpec.getVariables()) && isEmpty(overrideSpec.getManifests())
        && isEmpty(overrideSpec.getConfigFiles()) && overrideSpec.getApplicationSettings() == null
        && overrideSpec.getConnectionStrings() == null;
  }

  private void validateServiceOverrideScopeAndThrow(ServiceOverrideRequestDTOV2 requestDTO, String accountId) {
    if (isNotEmpty(requestDTO.getProjectIdentifier()) && isEmpty(requestDTO.getOrgIdentifier())) {
      throw new InvalidRequestException("org identifier must be specified when project identifier is specified.");
    }
    Scope requestScope =
        ScopeHelper.getScope(accountId, requestDTO.getOrgIdentifier(), requestDTO.getProjectIdentifier());

    if (Scope.ORG == requestScope) {
      if (Scope.PROJECT == IdentifierRefHelper.getScopeFromScopedRef(requestDTO.getEnvironmentRef())) {
        throw new InvalidRequestException(
            "For an org level override, project level environment can not be used. If you want to use environment at org/account level you might be missing prefix(org./account.) in environmentRef");
      }
    }
    if (Scope.ACCOUNT == requestScope) {
      if (Scope.ACCOUNT != IdentifierRefHelper.getScopeFromScopedRef(requestDTO.getEnvironmentRef())) {
        throw new InvalidRequestException(
            "For an account level override, project/org level environment can not be used. If you want to use environment at account level you might be missing prefix(account.) in environmentRef");
      }
    }
  }
}
