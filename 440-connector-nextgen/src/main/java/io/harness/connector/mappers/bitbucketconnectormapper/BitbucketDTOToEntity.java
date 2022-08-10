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
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccess;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketOauthDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernameTokenApiAccessDTO;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.UnknownEnumTypeException;

@OwnedBy(HarnessTeam.DX)
public class BitbucketDTOToEntity implements ConnectorDTOToEntityMapper<BitbucketConnectorDTO, BitbucketConnector> {
  @Override
  public BitbucketConnector toConnectorEntity(BitbucketConnectorDTO configDTO) {
    GitAuthType gitAuthType = getAuthType(configDTO.getAuthentication());
    BitbucketAuthentication bitbucketAuthentication =
        buildAuthenticationDetails(gitAuthType, configDTO.getAuthentication().getCredentials());
    boolean hasApiAccess = hasApiAccess(configDTO.getApiAccess());
    BitbucketApiAccessType apiAccessType = null;
    BitbucketApiAccess bitbucketApiAccess = null;
    if (hasApiAccess) {
      apiAccessType = getApiAccessType(configDTO.getApiAccess());
      bitbucketApiAccess = getApiAccessByType(configDTO.getApiAccess().getSpec(), apiAccessType);
    }
    return BitbucketConnector.builder()
        .connectionType(configDTO.getConnectionType())
        .authType(gitAuthType)
        .hasApiAccess(hasApiAccess)
        .authenticationDetails(bitbucketAuthentication)
        .bitbucketApiAccess(bitbucketApiAccess)
        .url(configDTO.getUrl())
        .validationRepo(configDTO.getValidationRepo())
        .build();
  }

  public static BitbucketAuthentication buildAuthenticationDetails(
      GitAuthType gitAuthType, BitbucketCredentialsDTO credentialsDTO) {
    switch (gitAuthType) {
      case SSH:
        final BitbucketSshCredentialsDTO sshCredentialsDTO = (BitbucketSshCredentialsDTO) credentialsDTO;
        return BitbucketSshAuthentication.builder()
            .sshKeyRef(SecretRefHelper.getSecretConfigString(sshCredentialsDTO.getSshKeyRef()))
            .build();
      case HTTP:
        final BitbucketHttpCredentialsDTO httpCredentialsDTO = (BitbucketHttpCredentialsDTO) credentialsDTO;
        final BitbucketHttpAuthenticationType type = httpCredentialsDTO.getType();
        return BitbucketHttpAuthentication.builder().type(type).auth(getHttpAuth(type, httpCredentialsDTO)).build();
      default:
        throw new UnknownEnumTypeException("Bitbucket Auth Type", String.valueOf(gitAuthType.getDisplayName()));
    }
  }

  private static BitbucketHttpAuth getHttpAuth(
      BitbucketHttpAuthenticationType type, BitbucketHttpCredentialsDTO httpCredentialsDTO) {
    switch (type) {
      case USERNAME_AND_PASSWORD:
        final BitbucketUsernamePasswordDTO usernamePasswordDTO =
            (BitbucketUsernamePasswordDTO) httpCredentialsDTO.getHttpCredentialsSpec();
        String usernameRef = getStringSecretForNullableSecret(usernamePasswordDTO.getUsernameRef());
        return BitbucketUsernamePassword.builder()
            .passwordRef(SecretRefHelper.getSecretConfigString(usernamePasswordDTO.getPasswordRef()))
            .username(usernamePasswordDTO.getUsername())
            .usernameRef(usernameRef)
            .build();
      case OAUTH:
        final BitbucketOauthDTO bitbucketOauthDTO = (BitbucketOauthDTO) httpCredentialsDTO.getHttpCredentialsSpec();
        String tokenRef = getStringSecretForNullableSecret(bitbucketOauthDTO.getTokenRef());
        String refreshTokenRef = getStringSecretForNullableSecret(bitbucketOauthDTO.getRefreshTokenRef());
        return BitbucketOauth.builder().tokenRef(tokenRef).refreshTokenRef(refreshTokenRef).build();
      default:
        throw new UnknownEnumTypeException("Bitbucket Http Auth Type", String.valueOf(type.getDisplayName()));
    }
  }
  private static String getStringSecretForNullableSecret(SecretRefData secretRefData) {
    return SecretRefHelper.getSecretConfigString(secretRefData);
  }

  private BitbucketApiAccess getApiAccessByType(
      BitbucketApiAccessSpecDTO spec, BitbucketApiAccessType apiAccessType) {
    switch (apiAccessType) {
      case USERNAME_AND_TOKEN:
        final BitbucketUsernameTokenApiAccessDTO apiAccessDTO = (BitbucketUsernameTokenApiAccessDTO) spec;
        return BitbucketUsernamePasswordApiAccess.builder()
            .username(apiAccessDTO.getUsername())
            .usernameRef(getStringSecretForNullableSecret(apiAccessDTO.getUsernameRef()))
            .tokenRef(getStringSecretForNullableSecret(apiAccessDTO.getTokenRef()))
            .build();
      case OAUTH:
        final BitbucketOauthDTO oauthAccessDTO = (BitbucketOauthDTO) spec;
        return BitbucketOauth.builder()
            .tokenRef(getStringSecretForNullableSecret(oauthAccessDTO.getTokenRef()))
            .refreshTokenRef(getStringSecretForNullableSecret(oauthAccessDTO.getRefreshTokenRef()))
            .build();
      default:
        throw new UnknownEnumTypeException("Bitbucket api type", String.valueOf(apiAccessType)) ;
    }
  }

  private BitbucketApiAccessType getApiAccessType(BitbucketApiAccessDTO apiAccess) {
    return apiAccess.getType();
  }

  private boolean hasApiAccess(BitbucketApiAccessDTO apiAccess) {
    return apiAccess != null;
  }

  private GitAuthType getAuthType(BitbucketAuthenticationDTO authentication) {
    return authentication.getAuthType();
  }
}
