/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.serviceaccounts.service.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ServiceAccountFilterDTO;
import io.harness.ng.serviceaccounts.dto.ServiceAccountAggregateDTO;
import io.harness.ng.serviceaccounts.entities.ServiceAccount;
import io.harness.serviceaccount.ServiceAccountDTO;

import java.util.List;
import org.springframework.data.domain.Pageable;

@OwnedBy(PL)
public interface ServiceAccountService {
  ServiceAccountDTO createServiceAccount(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, ServiceAccountDTO requestDTO);
  List<ServiceAccount> listServiceAccounts(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> identifiers);
  ServiceAccountDTO updateServiceAccount(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String identifier, ServiceAccountDTO requestDTO);
  boolean deleteServiceAccount(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier);
  void deleteBatch(String accountIdentifier, String orgIdentifier, String projectIdentifier);
  ServiceAccountDTO getServiceAccountDTO(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier);
  ServiceAccountDTO getServiceAccountDTO(String accountIdentifier, String identifier);
  PageResponse<ServiceAccountAggregateDTO> listAggregateServiceAccounts(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<String> identifiers, Pageable pageable, ServiceAccountFilterDTO filterDTO);

  ServiceAccountAggregateDTO getServiceAccountAggregateDTO(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier);
  Long countServiceAccounts(String accountIdentifier);

  List<ServiceAccount> getPermittedServiceAccounts(List<ServiceAccount> serviceAccounts);
}
