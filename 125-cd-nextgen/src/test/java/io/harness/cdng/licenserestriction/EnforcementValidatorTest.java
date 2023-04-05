/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.licenserestriction;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cd.CDLicenseType.SERVICES;
import static io.harness.rule.OwnerRule.ARVIND;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.PipelineSetupUsageUtils;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.enforcement.beans.metadata.AvailabilityRestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.RateLimitRestrictionMetadataDTO;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.enforcement.constants.RestrictionType;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.licensing.usage.beans.ReferenceDTO;
import io.harness.licensing.usage.beans.UsageDataDTO;
import io.harness.licensing.usage.beans.cd.CDLicenseUsageDTO;
import io.harness.licensing.usage.interfaces.LicenseUsageInterface;
import io.harness.licensing.usage.params.CDUsageRequestParams;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.utils.FullyQualifiedIdentifierHelper;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;

@RunWith(MockitoJUnitRunner.class)
@Slf4j
@OwnedBy(CDP)
public class EnforcementValidatorTest extends CategoryTest {
  @Mock private LicenseUsageInterface licenseUsageInterface;
  @Mock private EntitySetupUsageClient entitySetupUsageClient;
  @Mock private EnforcementClientService enforcementClientService;
  @InjectMocks private EnforcementValidator enforcementValidator;

  private static final String PROJECT_ID = "PROJECT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String PIPELINE_ID = "PIPELINE_ID";
  private static final String YAML = "YAML_CONTENT";
  private static final String EXECUTION_ID = "EXECUTION_ID";

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    doReturn(true).when(enforcementClientService).isEnforcementEnabled();
    doReturn(Optional.of(RateLimitRestrictionMetadataDTO.builder().restrictionType(RestrictionType.RATE_LIMIT).build()))
        .when(enforcementClientService)
        .getRestrictionMetadata(FeatureRestrictionName.SERVICES, ACCOUNT_ID);
    FieldUtils.writeField(
        enforcementValidator, "newServiceCache", CacheBuilder.newBuilder().maximumSize(2).build(), true);
  }

  private Cache<String, Integer> getCache() {
    try {
      return (Cache<String, Integer>) FieldUtils.readField(enforcementValidator, "newServiceCache", true);
    } catch (IllegalAccessException e) {
      return null;
    }
  }

  private ReferenceDTO getRefDTO(String serviceId) {
    return ReferenceDTO.builder()
        .identifier(serviceId)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .accountIdentifier(ACCOUNT_ID)
        .build();
  }

  private CDLicenseUsageDTO getServiceUsageDto(String... serviceIds) {
    List<ReferenceDTO> references = new ArrayList<>();
    for (String serviceId : serviceIds) {
      references.add(getRefDTO(serviceId));
    }

    UsageDataDTO usageDataDTO = UsageDataDTO.builder().references(references).build();
    return CDLicenseUsageDTO.builder().activeServices(usageDataDTO).build();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testValidateWithCache() throws Exception {
    Cache<String, Integer> cache = getCache();
    cache.put(EXECUTION_ID, 4);
    enforcementValidator.validate(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, YAML, EXECUTION_ID);
    verify(enforcementClientService).checkAvailabilityWithIncrement(FeatureRestrictionName.SERVICES, ACCOUNT_ID, 4);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testValidateWithCacheEnforcementDisabled() throws Exception {
    doReturn(false).when(enforcementClientService).isEnforcementEnabled();
    Cache<String, Integer> cache = getCache();
    cache.put(EXECUTION_ID, 4);
    enforcementValidator.validate(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, YAML, EXECUTION_ID);
    verify(enforcementClientService, times(0))
        .checkAvailabilityWithIncrement(FeatureRestrictionName.SERVICES, ACCOUNT_ID, 4);
    verify(enforcementClientService, times(1)).isEnforcementEnabled();
    verify(enforcementClientService, times(0)).getRestrictionMetadata(FeatureRestrictionName.SERVICES, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testValidateWithCacheEnforcementDisabled2() throws Exception {
    doReturn(true).when(enforcementClientService).isEnforcementEnabled();
    doReturn(
        Optional.of(AvailabilityRestrictionMetadataDTO.builder().restrictionType(RestrictionType.AVAILABILITY).build()))
        .when(enforcementClientService)
        .getRestrictionMetadata(FeatureRestrictionName.SERVICES, ACCOUNT_ID);
    Cache<String, Integer> cache = getCache();
    cache.put(EXECUTION_ID, 4);
    enforcementValidator.validate(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, YAML, EXECUTION_ID);
    verify(enforcementClientService, times(0))
        .checkAvailabilityWithIncrement(FeatureRestrictionName.SERVICES, ACCOUNT_ID, 4);
    verify(enforcementClientService, times(1)).isEnforcementEnabled();
    verify(enforcementClientService, times(1)).getRestrictionMetadata(FeatureRestrictionName.SERVICES, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testValidateWithoutCache() throws Exception {
    Cache<String, Integer> cache = getCache();
    assertThat(cache.asMap().containsKey(EXECUTION_ID)).isFalse();
    doReturn(getServiceUsageDto("S1", "S2", "S3"))
        .when(licenseUsageInterface)
        .getLicenseUsage(eq(ACCOUNT_ID), eq(ModuleType.CD), anyLong(),
            eq(CDUsageRequestParams.builder().cdLicenseType(SERVICES).build()));

    MockedStatic<NGRestUtils> ngRestUtilsMockedStatic = Mockito.mockStatic(NGRestUtils.class);
    List<EntitySetupUsageDTO> allReferredUsages = getReferredUsages("S3", "S4");
    Call<ResponseDTO<List<EntitySetupUsageDTO>>> responseDTOCallMock = Mockito.mock(Call.class);
    doReturn(responseDTOCallMock)
        .when(entitySetupUsageClient)
        .listAllReferredUsages(anyInt(), anyInt(), eq(ACCOUNT_ID),
            eq(FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID)),
            eq(EntityType.SERVICE), eq(null));
    ngRestUtilsMockedStatic.when(() -> NGRestUtils.getResponse(any(), any())).thenReturn(allReferredUsages);
    MockedStatic<PipelineSetupUsageUtils> pipelineSetupUsageUtilsMockedStatic =
        Mockito.mockStatic(PipelineSetupUsageUtils.class);
    pipelineSetupUsageUtilsMockedStatic
        .when(()
                  -> PipelineSetupUsageUtils.extractInputReferredEntityFromYaml(
                      eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(YAML), eq(allReferredUsages)))
        .thenReturn(
            allReferredUsages.stream().map(EntitySetupUsageDTO::getReferredEntity).collect(Collectors.toList()));

    enforcementValidator.validate(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, YAML, EXECUTION_ID);
    verify(enforcementClientService).checkAvailabilityWithIncrement(FeatureRestrictionName.SERVICES, ACCOUNT_ID, 1);
    assertThat(cache.asMap().containsKey(EXECUTION_ID)).isTrue();
    verify(enforcementClientService, times(1)).isEnforcementEnabled();
    verify(enforcementClientService, times(1)).getRestrictionMetadata(FeatureRestrictionName.SERVICES, ACCOUNT_ID);
  }

  private List<EntitySetupUsageDTO> getReferredUsages(String... services) {
    List<EntitySetupUsageDTO> usages = new ArrayList<>();
    for (String service : services) {
      usages.add(EntitySetupUsageDTO.builder()
                     .referredEntity(EntityDetail.builder()
                                         .entityRef(IdentifierRef.builder()
                                                        .accountIdentifier(ACCOUNT_ID)
                                                        .projectIdentifier(PROJECT_ID)
                                                        .orgIdentifier(ORG_ID)
                                                        .identifier(service)
                                                        .build())
                                         .build())
                     .build());
    }

    return usages;
  }
}
