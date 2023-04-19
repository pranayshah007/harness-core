/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.beans.UserSourceCodeManager;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerDTO;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerRequestDTO;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerResponseDTO;

@OwnedBy(HarnessTeam.PIPELINE)
public abstract class UserSourceCodeManagerMapper<D extends UserSourceCodeManagerDTO, B extends UserSourceCodeManager, R
                                                      extends UserSourceCodeManagerRequestDTO, T
                                                      extends UserSourceCodeManagerResponseDTO> {
  public B toEntity(D userSourceCodeManagerDTO) {
    B userSourceCodeManager = toEntityInternal(userSourceCodeManagerDTO);
    setCommonFieldsEntity(userSourceCodeManager, userSourceCodeManagerDTO);
    return userSourceCodeManager;
  }

  public D toDTO(B userSourceCodeManager) {
    D userSourceCodeManagerDTO = toDTOInternal(userSourceCodeManager);
    setCommonFieldsDTO(userSourceCodeManager, userSourceCodeManagerDTO);
    return userSourceCodeManagerDTO;
  }
  abstract B toEntityInternal(D userSourceCodeManagerDTO);

  abstract D toDTOInternal(B userSourceCodeManager);

  private void setCommonFieldsEntity(B scm, D scmDTO) {
    scm.setUserIdentifier(scmDTO.getUserIdentifier());
    scm.setAccountIdentifier(scmDTO.getAccountIdentifier());
    scm.setId(scmDTO.getId());
    scm.setType(scm.getType());
    scm.setCreatedAt(scmDTO.getCreatedAt());
    scm.setLastModifiedAt(scmDTO.getLastModifiedAt());
    scm.setUserEmail(scmDTO.getUserEmail());
    scm.setUserName(scmDTO.getUserName());
  }

  private void setCommonFieldsDTO(B scm, D scmDTO) {
    scmDTO.setId(scm.getId());
    scmDTO.setUserIdentifier(scm.getUserIdentifier());
    scmDTO.setCreatedAt(scm.getCreatedAt());
    scmDTO.setLastModifiedAt(scm.getLastModifiedAt());
    scmDTO.setAccountIdentifier(scm.getAccountIdentifier());
    scmDTO.setType(scm.getType());
    scmDTO.setUserEmail(scm.getUserEmail());
    scmDTO.setUserName(scm.getUserName());
  }

  public D toServiceDTO(R userSourceCodeManagerRequestDTO) {
    D dto = toServiceDTOInternal(userSourceCodeManagerRequestDTO);
    setServiceDTOCommonFields(userSourceCodeManagerRequestDTO, dto);
    return dto;
  }

  abstract D toServiceDTOInternal(R userSourceCodeManagerRequestDTO);

  private void setServiceDTOCommonFields(R requestDTO, D dto) {
    dto.setType(requestDTO.getType());
    dto.setAccountIdentifier(requestDTO.getAccountIdentifier());
    dto.setUserIdentifier(requestDTO.getUserIdentifier());
  }

  public T toResponseDTO(D dto) {
    T responseDTO = toResponseDTOInternal(dto);
    setResponseCommonFields(dto, responseDTO);
    return responseDTO;
  }

  abstract T toResponseDTOInternal(D dto);

  private void setResponseCommonFields(D dto, T responseDTO) {
    responseDTO.setType(dto.getType());
    responseDTO.setAccountIdentifier(dto.getAccountIdentifier());
    responseDTO.setUserIdentifier(dto.getUserIdentifier());
  }
}