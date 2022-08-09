/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.bitbucketconnectormapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.bitbucketconnector.BitbucketAuthentication;
import io.harness.connector.entities.embedded.bitbucketconnector.BitbucketConnector;
import io.harness.connector.entities.embedded.bitbucketconnector.BitbucketHttpAuth;
import io.harness.connector.entities.embedded.bitbucketconnector.BitbucketHttpAuthentication;
import io.harness.connector.entities.embedded.bitbucketconnector.BitbucketOauth;
import io.harness.connector.entities.embedded.bitbucketconnector.BitbucketSshAuthentication;
import io.harness.connector.entities.embedded.bitbucketconnector.BitbucketUsernamePassword;
import io.harness.connector.entities.embedded.bitbucketconnector.BitbucketUsernamePasswordApiAccess;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpCredentialsSpecDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketOauthDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernameTokenApiAccessDTO;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.govern.Switch;

@OwnedBy(HarnessTeam.DX)
public class BitbucketEntityToDTO implements ConnectorEntityToDTOMapper<BitbucketConnectorDTO, BitbucketConnector> {
  @Override
  public BitbucketConnectorDTO createConnectorDTO(BitbucketConnector connector) {
    BitbucketAuthenticationDTO bitbucketAuthenticationDTO =
        buildBitbucketAuthentication(connector.getAuthType(), connector.getAuthenticationDetails());
    BitbucketApiAccessDTO bitbucketApiAccess = null;
    if (connector.isHasApiAccess()) {
      bitbucketApiAccess = buildApiAccess(connector);
    }
    return BitbucketConnectorDTO.builder()
        .apiAccess(bitbucketApiAccess)
        .connectionType(connector.getConnectionType())
        .authentication(bitbucketAuthenticationDTO)
        .url(connector.getUrl())
        .validationRepo(connector.getValidationRepo())
        .build();
  }

  public static BitbucketAuthenticationDTO buildBitbucketAuthentication(
      GitAuthType authType, BitbucketAuthentication authenticationDetails) {
    BitbucketCredentialsDTO bitbucketCredentialsDTO = null;
    switch (authType) {
      case SSH:
        final BitbucketSshAuthentication bitbucketSshAuthentication =
            (BitbucketSshAuthentication) authenticationDetails;
        bitbucketCredentialsDTO =
            BitbucketSshCredentialsDTO.builder()
                .sshKeyRef(SecretRefHelper.createSecretRef(bitbucketSshAuthentication.getSshKeyRef()))
                .build();
        break;
      case HTTP:
        final BitbucketHttpAuthentication bitbucketHttpAuthentication =
            (BitbucketHttpAuthentication) authenticationDetails;
        final BitbucketHttpAuthenticationType type = bitbucketHttpAuthentication.getType();
        final BitbucketHttpAuth auth = bitbucketHttpAuthentication.getAuth();
        BitbucketHttpCredentialsSpecDTO bitbucketHttpCredentialsSpecDTO = getHttpCredentialsSpecDTO(type, auth);
        bitbucketCredentialsDTO = BitbucketHttpCredentialsDTO.builder()
                                      .type(type)
                                      .httpCredentialsSpec(bitbucketHttpCredentialsSpecDTO)
                                      .build();
        break;
      default:
        throw new UnknownEnumTypeException("Bitbucket Auth Type", String.valueOf(authType.getDisplayName()));
    }
    return BitbucketAuthenticationDTO.builder().authType(authType).credentials(bitbucketCredentialsDTO).build();
  }

  private static BitbucketHttpCredentialsSpecDTO getHttpCredentialsSpecDTO(
      BitbucketHttpAuthenticationType type, Object auth) {
    BitbucketHttpCredentialsSpecDTO bitbucketHttpCredentialsSpecDTO = null;
    switch (type) {
      case USERNAME_AND_PASSWORD:
        final BitbucketUsernamePassword bitbucketUsernamePassword = (BitbucketUsernamePassword) auth;
        SecretRefData usernameRef = null;
        if (bitbucketUsernamePassword.getUsernameRef() != null) {
          usernameRef = SecretRefHelper.createSecretRef(bitbucketUsernamePassword.getUsernameRef());
        }
        bitbucketHttpCredentialsSpecDTO =
            BitbucketUsernamePasswordDTO.builder()
                .passwordRef(SecretRefHelper.createSecretRef(bitbucketUsernamePassword.getPasswordRef()))
                .username(bitbucketUsernamePassword.getUsername())
                .usernameRef(usernameRef)
                .build();
        break;
      default:
        throw new UnknownEnumTypeException("Bitbucket Http Auth Type", String.valueOf(type.getDisplayName()));
    }
    return bitbucketHttpCredentialsSpecDTO;
  }

  private BitbucketApiAccessDTO buildApiAccess(BitbucketConnector connector) {
    switch (connector.getApiAccessType()) {
      case USERNAME_AND_TOKEN:
        final BitbucketUsernamePasswordApiAccess bitbucketApiAccessType =
            (BitbucketUsernamePasswordApiAccess) connector.getBitbucketApiAccess();
        SecretRefData usernameRef = bitbucketApiAccessType.getUsernameRef() != null
            ? SecretRefHelper.createSecretRef(bitbucketApiAccessType.getUsernameRef())
            : null;
        final BitbucketApiAccessSpecDTO bitbucketTokenSpecDTO =

            BitbucketUsernameTokenApiAccessDTO.builder()
                .username(bitbucketApiAccessType.getUsername())
                .usernameRef(usernameRef)
                .tokenRef(SecretRefHelper.createSecretRef(bitbucketApiAccessType.getTokenRef()))
                .build();
        return BitbucketApiAccessDTO.builder()
            .type(BitbucketApiAccessType.USERNAME_AND_TOKEN)
            .spec(bitbucketTokenSpecDTO)
            .build();
      case OAUTH:
        final BitbucketOauth bitbucketApiAccess = (BitbucketOauth) connector.getBitbucketApiAccess();

        final BitbucketApiAccessSpecDTO bitbucketOauthDTO =
            BitbucketOauthDTO.builder()
                .tokenRef(SecretRefHelper.createSecretRef(bitbucketApiAccess.getTokenRef()))
                .refreshTokenRef(SecretRefHelper.createSecretRef(bitbucketApiAccess.getRefreshTokenRef()))
                .build();

        return BitbucketApiAccessDTO.builder().type(BitbucketApiAccessType.OAUTH).spec(bitbucketOauthDTO).build();
      default:
        Switch.unhandled(connector.getApiAccessType());
        return null;
    }
  }
}
