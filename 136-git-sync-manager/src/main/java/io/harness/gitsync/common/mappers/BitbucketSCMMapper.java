/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.bitbucketconnector.BitbucketApiAccess;
import io.harness.connector.entities.embedded.bitbucketconnector.BitbucketOAuth;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketOAuthDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.gitsync.common.beans.BitbucketSCM;
import io.harness.gitsync.common.dtos.BitbucketSCMDTO;
import io.harness.gitsync.common.dtos.BitbucketSCMRequestDTO;
import io.harness.gitsync.common.dtos.BitbucketSCMResponseDTO;

@OwnedBy(HarnessTeam.PIPELINE)
public class BitbucketSCMMapper extends UserSourceCodeManagerMapper<BitbucketSCMDTO, BitbucketSCM,
    BitbucketSCMRequestDTO, BitbucketSCMResponseDTO> {
  @Override
  BitbucketSCM toEntityInternal(BitbucketSCMDTO sourceCodeManagerDTO) {
    return BitbucketSCM.builder()
        .apiAccessType(sourceCodeManagerDTO.getApiAccess().getType())
        .bitbucketApiAccess(
            toApiAccess(sourceCodeManagerDTO.getApiAccess().getSpec(), sourceCodeManagerDTO.getApiAccess().getType()))
        .build();
  }

  @Override
  BitbucketSCMDTO toDTOInternal(BitbucketSCM sourceCodeManager) {
    return BitbucketSCMDTO.builder()
        .apiAccess(toApiAccessDTO(sourceCodeManager.getApiAccessType(), sourceCodeManager.getBitbucketApiAccess()))
        .build();
  }

  @Override
  BitbucketSCMResponseDTO toResponseDTOInternal(BitbucketSCMDTO dto) {
    return BitbucketSCMResponseDTO.builder().apiAccess(dto.getApiAccess()).build();
  }

  @Override
  BitbucketSCMDTO toServiceDTOInternal(BitbucketSCMRequestDTO userSourceCodeManagerRequestDTO) {
    return BitbucketSCMDTO.builder()
        .apiAccess(userSourceCodeManagerRequestDTO.getAuthentication().getApiAccessDTO())
        .build();
  }

  BitbucketApiAccess toApiAccess(BitbucketApiAccessSpecDTO spec, BitbucketApiAccessType apiAccessType) {
    switch (apiAccessType) {
      case OAUTH:
        final BitbucketOAuthDTO bitBucketOAuthDTO = (BitbucketOAuthDTO) spec;
        return BitbucketOAuth.builder()
            .tokenRef(SecretRefHelper.getSecretConfigString(bitBucketOAuthDTO.getTokenRef()))
            .refreshTokenRef(SecretRefHelper.getSecretConfigString(bitBucketOAuthDTO.getRefreshTokenRef()))
            .build();
      default:
        throw new UnknownEnumTypeException("Bitbucket Api Access Type", apiAccessType.getDisplayName());
    }
  }

  public static BitbucketApiAccessDTO toApiAccessDTO(
      BitbucketApiAccessType apiAccessType, BitbucketApiAccess bitbucketApiAccess) {
    BitbucketApiAccessSpecDTO apiAccessSpecDTO = null;
    switch (apiAccessType) {
      case OAUTH:
        final BitbucketOAuth bitbucketOAuth = (BitbucketOAuth) bitbucketApiAccess;
        apiAccessSpecDTO = BitbucketOAuthDTO.builder()
                               .tokenRef(SecretRefHelper.createSecretRef(bitbucketOAuth.getTokenRef()))
                               .refreshTokenRef(SecretRefHelper.createSecretRef(bitbucketOAuth.getRefreshTokenRef()))
                               .build();
        break;
      default:
        throw new UnknownEnumTypeException("Bitbucket Api Access Type", apiAccessType.getDisplayName());
    }
    return BitbucketApiAccessDTO.builder().type(apiAccessType).spec(apiAccessSpecDTO).build();
  }
}
