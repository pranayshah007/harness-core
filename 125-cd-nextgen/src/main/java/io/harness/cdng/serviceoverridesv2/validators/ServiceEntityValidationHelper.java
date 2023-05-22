/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serviceoverridesv2.validators;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.utils.IdentifierRefHelper.MAX_RESULT_THRESHOLD_FOR_SPLIT;

import static com.google.common.base.Preconditions.checkArgument;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import java.util.Optional;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.NotFoundException;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
public class ServiceEntityValidationHelper {
  @Inject private ServiceEntityService serviceEntityService;

  public void checkThatServiceExists(
      @NotEmpty String accountIdentifier, String orgIdentifier, String projectIdentifier, @NotEmpty String serviceRef) {
    checkArgument(isNotEmpty(accountIdentifier), "accountId must be present");

    Optional<ServiceEntity> service;
    String[] serviceRefSplit = StringUtils.split(serviceRef, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);
    // project level entity or org/account level entity with identifier
    if (serviceRefSplit == null || serviceRefSplit.length == 1) {
      service = serviceEntityService.get(accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, false);
    } else {
      // org/account level
      IdentifierRef serviceIdentifierRef = IdentifierRefHelper.getIdentifierRefOrThrowException(
          serviceRef, accountIdentifier, orgIdentifier, projectIdentifier, YAMLFieldNameConstants.SERVICE);
      service =
          serviceEntityService.get(serviceIdentifierRef.getAccountIdentifier(), serviceIdentifierRef.getOrgIdentifier(),
              serviceIdentifierRef.getProjectIdentifier(), serviceIdentifierRef.getIdentifier(), false);
    }
    if (service.isEmpty()) {
      throw new NotFoundException(String.format("Service with ref [%s] not found", serviceRef));
    }
  }
}
