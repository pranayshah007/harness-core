/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.serviceaccounts.service;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.serviceaccounts.entities.ServiceAccount;
import io.harness.serviceaccount.ServiceAccountDTO;
import io.harness.serviceaccount.ServiceAccountDTOInternal;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public class ServiceAccountDTOMapper {
  public ServiceAccountDTO getDTOFromServiceAccount(ServiceAccount serviceAccount) {
    return ServiceAccountDTO.builder()
        .identifier(serviceAccount.getIdentifier())
        .uniqueId(serviceAccount.getUniqueId())
        .name(serviceAccount.getName())
        .email(serviceAccount.getEmail())
        .description(serviceAccount.getDescription())
        .tags(TagMapper.convertToMap(serviceAccount.getTags()))
        .accountIdentifier(serviceAccount.getAccountIdentifier())
        .orgIdentifier(serviceAccount.getOrgIdentifier())
        .projectIdentifier(serviceAccount.getProjectIdentifier())
        .build();
  }

  public ServiceAccountDTOInternal getDTOFromServiceAccountInternal(ServiceAccountDTO serviceAccountDTO) {
    return (ServiceAccountDTOInternal) ServiceAccountDTOInternal.builder()
        .uniqueId(serviceAccountDTO.getUniqueId())
        .accountIdentifier(serviceAccountDTO.getAccountIdentifier())
        .name(serviceAccountDTO.getName())
        .identifier(serviceAccountDTO.getIdentifier())
        .orgIdentifier(serviceAccountDTO.getOrgIdentifier())
        .projectIdentifier(serviceAccountDTO.getProjectIdentifier())
        .email(serviceAccountDTO.getEmail())
        .tags(serviceAccountDTO.getTags())
        .description(serviceAccountDTO.getDescription())
        .build();
  }

  public ServiceAccount getServiceAccountFromDTO(ServiceAccountDTO serviceAccountDTO) {
    return ServiceAccount.builder()
        .accountIdentifier(serviceAccountDTO.getAccountIdentifier())
        .uniqueId(serviceAccountDTO.getUniqueId())
        .orgIdentifier(serviceAccountDTO.getOrgIdentifier())
        .projectIdentifier(serviceAccountDTO.getProjectIdentifier())
        .name(serviceAccountDTO.getName())
        .identifier(serviceAccountDTO.getIdentifier())
        .description(serviceAccountDTO.getDescription())
        .email(serviceAccountDTO.getEmail().toLowerCase())
        .tags(TagMapper.convertToList(serviceAccountDTO.getTags()))
        .build();
  }
}
