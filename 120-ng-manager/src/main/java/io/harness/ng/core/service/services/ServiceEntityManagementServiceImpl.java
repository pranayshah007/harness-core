/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.services;

import static io.harness.beans.FeatureName.NG_SETTINGS;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Long.parseLong;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.CollectionUtils;
import io.harness.dtos.InstanceDTO;
import io.harness.eraro.ErrorMessageConstants;
import io.harness.exception.InvalidRequestException;
import io.harness.mappers.InstanceMapper;
import io.harness.ng.core.service.services.exception.ActiveServiceInstancesPresentException;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.instance.InstanceRepository;
import io.harness.service.instance.InstanceService;

import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ServiceEntityManagementServiceImpl implements ServiceEntityManagementService {
  private final InstanceService instanceService;
  private final ServiceEntityService serviceEntityService;
  private final InstanceRepository instanceRepository;
  private final AccountClient accountClient;
  private final ServiceSequenceService serviceSequenceService;

  NGSettingsClient settingsClient;

  @Override
  public boolean deleteService(String accountId, String orgIdentifier, String projectIdentifier,
      String serviceIdentifier, String ifMatch, boolean forceDelete) {
    if (forceDelete && !isForceDeleteEnabled(accountId)) {
      throw new InvalidRequestException(ErrorMessageConstants.FORCE_DELETE_SETTING_NOT_ENABLED, USER);
    }

    List<InstanceDTO> instanceInfoNGList =
        InstanceMapper.toDTO(CollectionUtils.emptyIfNull(instanceRepository.getInstancesCreatedBefore(
            accountId, orgIdentifier, projectIdentifier, serviceIdentifier, System.currentTimeMillis())));
    if (!forceDelete && isNotEmpty(instanceInfoNGList)) {
      throw new ActiveServiceInstancesPresentException(String.format(
          "Service [%s] under Project[%s], Organization [%s] couldn't be deleted since there are currently %d active instances for the service",
          serviceIdentifier, projectIdentifier, orgIdentifier, instanceInfoNGList.size()));
    }

    boolean success = serviceEntityService.delete(accountId, orgIdentifier, projectIdentifier, serviceIdentifier,
        isNumeric(ifMatch) ? parseLong(ifMatch) : null, forceDelete);

    if (success && forceDelete) {
      instanceService.deleteAll(instanceInfoNGList);
    }
    try {
      serviceSequenceService.delete(accountId, orgIdentifier, projectIdentifier, serviceIdentifier);
    } catch (Exception e) {
      log.warn("Failed to delete service sequence for service {}", serviceIdentifier);
    }

    return success;
  }
  private boolean isForceDeleteEnabled(String accountIdentifier) {
    return isNgSettingsFFEnabled(accountIdentifier) && isForceDeleteFFEnabledViaSettings(accountIdentifier);
  }

  protected boolean isForceDeleteFFEnabledViaSettings(String accountIdentifier) {
    return parseBoolean(NGRestUtils
                            .getResponse(settingsClient.getSetting(
                                SettingIdentifiers.ENABLE_FORCE_DELETE, accountIdentifier, null, null))
                            .getValue());
  }

  protected boolean isNgSettingsFFEnabled(String accountIdentifier) {
    return CGRestUtils.getResponse(accountClient.isFeatureFlagEnabled(NG_SETTINGS.name(), accountIdentifier));
  }
}