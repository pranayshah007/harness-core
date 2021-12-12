package io.harness.cdng.licenserestriction;

import static io.harness.data.structure.CollectionUtils.collectionToStream;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.licensing.beans.modules.types.CDLicenseType.SERVICES;
import static io.harness.remote.client.NGRestUtils.getResponseWithRetry;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.PipelineSetupUsageUtils;
import io.harness.cdng.usage.beans.CDLicenseUsageDTO;
import io.harness.common.EntityReference;
import io.harness.enforcement.beans.metadata.RestrictionMetadataDTO;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.enforcement.constants.RestrictionType;
import io.harness.enforcement.exceptions.EnforcementServiceConnectionException;
import io.harness.enforcement.exceptions.WrongFeatureStateException;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.licensing.usage.interfaces.LicenseUsageInterface;
import io.harness.licensing.usage.params.CDUsageRequestParams;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.utils.FullyQualifiedIdentifierHelper;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class EnforcementValidator {
  @Inject private LicenseUsageInterface licenseUsageInterface;
  @Inject private EntitySetupUsageClient entitySetupUsageClient;
  @Inject private EnforcementClientService enforcementClientService;

  private static final int PAGE = 0;
  private static final int SIZE = 100;

  private Cache<String, Integer> newServiceCache =
      CacheBuilder.newBuilder().maximumSize(2000).expireAfterWrite(1, TimeUnit.MINUTES).build();

  private Set<BaseNGAccess> getActiveServices(String accountIdentifier) {
    CDLicenseUsageDTO licenseUsage = (CDLicenseUsageDTO) licenseUsageInterface.getLicenseUsage(accountIdentifier,
        ModuleType.CD, new Date().getTime(), CDUsageRequestParams.builder().cdLicenseType(SERVICES).build());
    Set<BaseNGAccess> services = new HashSet<>();
    if (licenseUsage != null && null != licenseUsage.getActiveServices()
        && isNotEmpty(licenseUsage.getActiveServices().getReferences())) {
      services = licenseUsage.getActiveServices()
                     .getReferences()
                     .stream()
                     .map(referenceDTO
                         -> BaseNGAccess.builder()
                                .accountIdentifier(referenceDTO.getAccountIdentifier())
                                .orgIdentifier(referenceDTO.getOrgIdentifier())
                                .projectIdentifier(referenceDTO.getProjectIdentifier())
                                .identifier(referenceDTO.getIdentifier())
                                .build())
                     .collect(Collectors.toSet());
    }
    return services;
  }

  private Set<BaseNGAccess> getServicesBeingCreated(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineId, String yaml) {
    List<EntitySetupUsageDTO> allReferredUsages =
        getResponseWithRetry(entitySetupUsageClient.listAllReferredUsages(PAGE, SIZE, accountIdentifier,
                                 FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
                                     accountIdentifier, orgIdentifier, projectIdentifier, pipelineId),
                                 EntityType.SERVICE, null),
            "Could not extract setup usage of pipeline with id " + pipelineId + " after {} attempts.");
    List<EntityDetail> entityDetails = PipelineSetupUsageUtils.extractInputReferredEntityFromYaml(
        accountIdentifier, orgIdentifier, projectIdentifier, yaml, allReferredUsages);
    return collectionToStream(entityDetails)
        .map(entityDetail -> {
          EntityReference entityRef = entityDetail.getEntityRef();
          return BaseNGAccess.builder()
              .accountIdentifier(entityRef.getAccountIdentifier())
              .orgIdentifier(entityRef.getOrgIdentifier())
              .projectIdentifier(entityRef.getProjectIdentifier())
              .identifier(entityRef.getIdentifier())
              .build();
        })
        .collect(Collectors.toSet());
  }

  private int getAdditionalServiceLicenseCount(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineId, String yaml) {
    Set<BaseNGAccess> activeServices = getActiveServices(accountIdentifier);
    Set<BaseNGAccess> newServices =
        getServicesBeingCreated(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, yaml);
    Set<BaseNGAccess> difference = Sets.difference(newServices, activeServices);
    return difference.size();
  }

  public void validate(String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineId,
      String yaml, String executionId) {
    if (!checkRequired(accountIdentifier)) {
      return;
    }

    Integer newServiceCount = newServiceCache.getIfPresent(executionId);
    if (null == newServiceCount) {
      newServiceCount =
          getAdditionalServiceLicenseCount(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, yaml);
      newServiceCache.put(executionId, newServiceCount);
    }

    enforcementClientService.checkAvailabilityWithIncrement(
        FeatureRestrictionName.SERVICES, accountIdentifier, newServiceCount);
  }

  private boolean checkRequired(String accountIdentifier) {
    boolean result = enforcementClientService.isEnforcementEnabled();
    if (result) {
      try {
        Optional<RestrictionMetadataDTO> restrictionMetadata =
            enforcementClientService.getRestrictionMetadata(FeatureRestrictionName.SERVICES, accountIdentifier);
        result = restrictionMetadata.isPresent()
            && !restrictionMetadata.get().getRestrictionType().equals(RestrictionType.AVAILABILITY);
      } catch (WrongFeatureStateException | EnforcementServiceConnectionException e) {
        log.error("Error while getting enforcement response: {}", e.getMessage(), e);
      }
    }
    return result;
  }
}
