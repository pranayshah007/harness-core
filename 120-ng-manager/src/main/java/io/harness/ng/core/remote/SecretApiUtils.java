/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.spec.server.ng.v1.model.SecretSpec.TypeEnum.SECRETFILE;
import static io.harness.spec.server.ng.v1.model.SecretSpec.TypeEnum.SECRETTEXT;
import static io.harness.spec.server.ng.v1.model.SecretSpec.TypeEnum.SSHKERBEROSTGTKEYTABFILE;
import static io.harness.spec.server.ng.v1.model.SecretSpec.TypeEnum.SSHKERBEROSTGTPASSWORD;
import static io.harness.spec.server.ng.v1.model.SecretSpec.TypeEnum.SSHKEYPATH;
import static io.harness.spec.server.ng.v1.model.SecretSpec.TypeEnum.SSHKEYREFERENCE;
import static io.harness.spec.server.ng.v1.model.SecretSpec.TypeEnum.SSHPASSWORD;
import static io.harness.spec.server.ng.v1.model.SecretSpec.TypeEnum.WINRMNTLM;
import static io.harness.spec.server.ng.v1.model.SecretSpec.TypeEnum.WINRMTGTKEYTABFILE;
import static io.harness.spec.server.ng.v1.model.SecretSpec.TypeEnum.WINRMTGTPASSWORD;
import static io.harness.spec.server.ng.v1.model.SecretTextSpec.ValueTypeEnum.fromValue;

import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.secrets.KerberosConfigDTO;
import io.harness.ng.core.dto.secrets.KerberosWinRmConfigDTO;
import io.harness.ng.core.dto.secrets.NTLMConfigDTO;
import io.harness.ng.core.dto.secrets.SSHAuthDTO;
import io.harness.ng.core.dto.secrets.SSHConfigDTO;
import io.harness.ng.core.dto.secrets.SSHCredentialType;
import io.harness.ng.core.dto.secrets.SSHKeyPathCredentialDTO;
import io.harness.ng.core.dto.secrets.SSHKeyReferenceCredentialDTO;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SSHPasswordCredentialDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretDTOV2.SecretDTOV2Builder;
import io.harness.ng.core.dto.secrets.SecretFileSpecDTO;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.SecretSpecDTO;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ng.core.dto.secrets.TGTGenerationMethod;
import io.harness.ng.core.dto.secrets.TGTGenerationSpecDTO;
import io.harness.ng.core.dto.secrets.TGTKeyTabFilePathSpecDTO;
import io.harness.ng.core.dto.secrets.TGTPasswordSpecDTO;
import io.harness.ng.core.dto.secrets.WinRmAuthDTO;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;
import io.harness.secretmanagerclient.SSHAuthScheme;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;
import io.harness.secretmanagerclient.WinRmAuthScheme;
import io.harness.spec.server.ng.v1.model.SSHKerberosTGTKeyTabFileSpec;
import io.harness.spec.server.ng.v1.model.SSHKerberosTGTPasswordSpec;
import io.harness.spec.server.ng.v1.model.SSHKeyPathSpec;
import io.harness.spec.server.ng.v1.model.SSHKeyReferenceSpec;
import io.harness.spec.server.ng.v1.model.SSHPasswordSpec;
import io.harness.spec.server.ng.v1.model.Secret;
import io.harness.spec.server.ng.v1.model.SecretFileSpec;
import io.harness.spec.server.ng.v1.model.SecretResponse;
import io.harness.spec.server.ng.v1.model.SecretTextSpec;
import io.harness.spec.server.ng.v1.model.WinRmNTLMSpec;
import io.harness.spec.server.ng.v1.model.WinRmTGTKeyTabFileSpec;
import io.harness.spec.server.ng.v1.model.WinRmTGTPasswordSpec;

import com.google.inject.Inject;
import io.dropwizard.jersey.validation.JerseyViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;

public class SecretApiUtils {
  private Validator validator;

  @Inject
  public SecretApiUtils(Validator validator) {
    this.validator = validator;
  }

  public SecretDTOV2 toSecretDto(Secret secret) {
    SecretDTOV2Builder secretDTOV2Builder = SecretDTOV2.builder()
                                                .identifier(secret.getIdentifier())
                                                .name(secret.getName())
                                                .orgIdentifier(secret.getOrg())
                                                .projectIdentifier(secret.getProject())
                                                .description(secret.getDescription())
                                                .tags(secret.getTags());
    switch (secret.getSpec().getType()) {
      case SSHKEYPATH:
        secretDTOV2Builder.type(SecretType.SSHKey).spec(fromSSHKeyPathSpec(secret));
        break;
      case SSHKEYREFERENCE:
        secretDTOV2Builder.type(SecretType.SSHKey).spec(fromSSHKeyReferenceSpec(secret));
        break;
      case SSHPASSWORD:
        secretDTOV2Builder.type(SecretType.SSHKey).spec(fromSSHKeyPasswordSpec(secret));
        break;
      case SSHKERBEROSTGTKEYTABFILE:
        secretDTOV2Builder.type(SecretType.SSHKey).spec(fromSSHKerberosTGTKeyTabFile(secret));
        break;
      case SSHKERBEROSTGTPASSWORD:
        secretDTOV2Builder.type(SecretType.SSHKey).spec(fromSSHKerberosTGTPassword(secret));
        break;
      case SECRETTEXT:
        secretDTOV2Builder.type(SecretType.SecretText).spec(fromSecretText(secret));
        break;
      case SECRETFILE:
        secretDTOV2Builder.type(SecretType.SecretFile).spec(fromSecretFileSpec(secret));
        break;
      case WINRMNTLM:
        secretDTOV2Builder.type(SecretType.WinRmCredentials).spec(fromWinRmNTLSpec(secret));
        break;
      case WINRMTGTKEYTABFILE:
        secretDTOV2Builder.type(SecretType.WinRmCredentials).spec(fromWinRmTGTKeyTabFile(secret));
        break;
      case WINRMTGTPASSWORD:
        secretDTOV2Builder.type(SecretType.WinRmCredentials).spec(fromWinRmPasswordSpec(secret));
        break;
      default:
        throw new InvalidRequestException(
            String.format("Invalid request, secret spec type [%s] is not supported", secret.getSpec().getType()));
    }

    SecretDTOV2 secretDTOV2 = secretDTOV2Builder.build();

    Set<ConstraintViolation<SecretDTOV2>> violations = validator.validate(secretDTOV2);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }

    return secretDTOV2;
  }

  private SecretSpecDTO fromWinRmPasswordSpec(Secret secret) {
    WinRmTGTPasswordSpec winRmTGTPasswordSpec = (WinRmTGTPasswordSpec) secret.getSpec();

    TGTGenerationSpecDTO tGTKeyTabFilePathSpecDTO =
        TGTPasswordSpecDTO.builder()
            .password(winRmTGTPasswordSpec.getPassword() != null ? new SecretRefData(winRmTGTPasswordSpec.getPassword())
                                                                 : null)
            .build();

    KerberosWinRmConfigDTO kerberosWinRmConfigDTO = KerberosWinRmConfigDTO.builder()
                                                        .principal(winRmTGTPasswordSpec.getPrincipal())
                                                        .realm(winRmTGTPasswordSpec.getRealm())
                                                        .spec(tGTKeyTabFilePathSpecDTO)
                                                        .useSSL(winRmTGTPasswordSpec.isUseSsl())
                                                        .skipCertChecks(winRmTGTPasswordSpec.isSkipCertChecks())
                                                        .useNoProfile(winRmTGTPasswordSpec.isUseNoProfile())
                                                        .build();

    WinRmAuthDTO winRmAuthDTO =
        WinRmAuthDTO.builder().type(WinRmAuthScheme.Kerberos).spec(kerberosWinRmConfigDTO).build();

    return WinRmCredentialsSpecDTO.builder().port(winRmTGTPasswordSpec.getPort()).auth(winRmAuthDTO).build();
  }

  private SecretSpecDTO fromWinRmTGTKeyTabFile(Secret secret) {
    WinRmTGTKeyTabFileSpec winRmTGTKeyTabFileSpec = (WinRmTGTKeyTabFileSpec) secret.getSpec();

    TGTGenerationSpecDTO tGTKeyTabFilePathSpecDTO =
        TGTKeyTabFilePathSpecDTO.builder().keyPath(winRmTGTKeyTabFileSpec.getKeyPath()).build();

    KerberosWinRmConfigDTO kerberosWinRmConfigDTO = KerberosWinRmConfigDTO.builder()
                                                        .principal(winRmTGTKeyTabFileSpec.getPrincipal())
                                                        .realm(winRmTGTKeyTabFileSpec.getRealm())
                                                        .spec(tGTKeyTabFilePathSpecDTO)
                                                        .useSSL(winRmTGTKeyTabFileSpec.isUseSsl())
                                                        .skipCertChecks(winRmTGTKeyTabFileSpec.isSkipCertChecks())
                                                        .useNoProfile(winRmTGTKeyTabFileSpec.isUseNoProfile())
                                                        .build();

    WinRmAuthDTO winRmAuthDTO =
        WinRmAuthDTO.builder().type(WinRmAuthScheme.Kerberos).spec(kerberosWinRmConfigDTO).build();

    return WinRmCredentialsSpecDTO.builder().port(winRmTGTKeyTabFileSpec.getPort()).auth(winRmAuthDTO).build();
  }

  private SecretSpecDTO fromWinRmNTLSpec(Secret secret) {
    WinRmNTLMSpec winRmNTLMSpec = (WinRmNTLMSpec) secret.getSpec();

    NTLMConfigDTO nTLMConfigDTO = NTLMConfigDTO.builder()
                                      .domain(winRmNTLMSpec.getDomain())
                                      .username(winRmNTLMSpec.getUsername())
                                      .password(new SecretRefData(winRmNTLMSpec.getPassword()))
                                      .useSSL(winRmNTLMSpec.isUseSsl())
                                      .skipCertChecks(winRmNTLMSpec.isSkipCertChecks())
                                      .useNoProfile(winRmNTLMSpec.isUseNoProfile())
                                      .build();

    WinRmAuthDTO winRmAuthDTO = WinRmAuthDTO.builder().type(WinRmAuthScheme.NTLM).spec(nTLMConfigDTO).build();

    return WinRmCredentialsSpecDTO.builder().port(winRmNTLMSpec.getPort()).auth(winRmAuthDTO).build();
  }

  private SecretSpecDTO fromSecretFileSpec(Secret secret) {
    SecretFileSpec secretFileSpec = (SecretFileSpec) secret.getSpec();

    return SecretFileSpecDTO.builder().secretManagerIdentifier(secretFileSpec.getSecretManagerIdentifier()).build();
  }

  private SecretSpecDTO fromSecretText(Secret secret) {
    SecretTextSpec secretTextSpec = (SecretTextSpec) secret.getSpec();

    return SecretTextSpecDTO.builder()
        .secretManagerIdentifier(secretTextSpec.getSecretManagerIdentifier())
        .valueType(ValueType.valueOf(secretTextSpec.getValueType().value()))
        .value(secretTextSpec.getValue())
        .build();
  }

  private SecretSpecDTO fromSSHKerberosTGTPassword(Secret secret) {
    SSHKerberosTGTPasswordSpec sshKerberosTGTPasswordSpec = (SSHKerberosTGTPasswordSpec) secret.getSpec();

    TGTPasswordSpecDTO tgtPasswordSpecDTO =
        TGTPasswordSpecDTO.builder().password(new SecretRefData(sshKerberosTGTPasswordSpec.getPassword())).build();
    KerberosConfigDTO kerberosConfigDTO = KerberosConfigDTO.builder()
                                              .principal(sshKerberosTGTPasswordSpec.getPrincipal())
                                              .realm(sshKerberosTGTPasswordSpec.getRealm())
                                              .spec(tgtPasswordSpecDTO)
                                              .build();

    return new SSHKeySpecDTO(
        sshKerberosTGTPasswordSpec.getPort(), new SSHAuthDTO(SSHAuthScheme.Kerberos, kerberosConfigDTO));
  }

  private SecretSpecDTO fromSSHKerberosTGTKeyTabFile(Secret secret) {
    SSHKerberosTGTKeyTabFileSpec sshKerberosTGTKeyTabFileSpec = (SSHKerberosTGTKeyTabFileSpec) secret.getSpec();

    TGTKeyTabFilePathSpecDTO tgtKeyTabFilePathSpecDTO =
        TGTKeyTabFilePathSpecDTO.builder().keyPath(sshKerberosTGTKeyTabFileSpec.getKeyPath()).build();

    KerberosConfigDTO kerberosConfigDTO = KerberosConfigDTO.builder()
                                              .principal(sshKerberosTGTKeyTabFileSpec.getPrincipal())
                                              .realm(sshKerberosTGTKeyTabFileSpec.getRealm())
                                              .spec(tgtKeyTabFilePathSpecDTO)
                                              .build();

    return new SSHKeySpecDTO(
        sshKerberosTGTKeyTabFileSpec.getPort(), new SSHAuthDTO(SSHAuthScheme.Kerberos, kerberosConfigDTO));
  }

  private SecretSpecDTO fromSSHKeyPasswordSpec(Secret secret) {
    SSHPasswordSpec sshPasswordSpec = (SSHPasswordSpec) secret.getSpec();
    SSHPasswordCredentialDTO sshPasswordCredentialDTO = SSHPasswordCredentialDTO.builder()
                                                            .userName(sshPasswordSpec.getUsername())
                                                            .password(new SecretRefData(sshPasswordSpec.getPassword()))
                                                            .build();
    SSHConfigDTO sshSpec = new SSHConfigDTO(SSHCredentialType.Password, sshPasswordCredentialDTO);

    return new SSHKeySpecDTO(sshPasswordSpec.getPort(), new SSHAuthDTO(SSHAuthScheme.SSH, sshSpec));
  }

  private SecretSpecDTO fromSSHKeyReferenceSpec(Secret secret) {
    SSHKeyReferenceSpec sshKeyReferenceSpec = (SSHKeyReferenceSpec) secret.getSpec();
    SSHKeyReferenceCredentialDTO sshKeyReferenceCredentialDTO =
        SSHKeyReferenceCredentialDTO.builder()
            .userName(sshKeyReferenceSpec.getUsername())
            .key(new SecretRefData(sshKeyReferenceSpec.getKey()))
            .encryptedPassphrase(new SecretRefData(sshKeyReferenceSpec.getEncryptedPassphrase()))
            .build();
    SSHConfigDTO sshSpec = new SSHConfigDTO(SSHCredentialType.KeyReference, sshKeyReferenceCredentialDTO);

    return new SSHKeySpecDTO(sshKeyReferenceSpec.getPort(), new SSHAuthDTO(SSHAuthScheme.SSH, sshSpec));
  }

  private SecretSpecDTO fromSSHKeyPathSpec(Secret secret) {
    SSHKeyPathSpec sshKeyPathSpec = (SSHKeyPathSpec) secret.getSpec();
    SSHKeyPathCredentialDTO sshKeyPathCredentialDTO =
        SSHKeyPathCredentialDTO.builder()
            .userName(sshKeyPathSpec.getUsername())
            .keyPath(sshKeyPathSpec.getKeyPath())
            .encryptedPassphrase(new SecretRefData(sshKeyPathSpec.getEncryptedPassphrase()))
            .build();
    SSHConfigDTO sshSpec = new SSHConfigDTO(SSHCredentialType.KeyPath, sshKeyPathCredentialDTO);

    return new SSHKeySpecDTO(sshKeyPathSpec.getPort(), new SSHAuthDTO(SSHAuthScheme.SSH, sshSpec));
  }

  public SecretResponse toSecretResponse(SecretResponseWrapper secretResponseWrapper) {
    SecretResponse secretResponse = new SecretResponse();
    secretResponse.setSecret(toSecret(secretResponseWrapper.getSecret()));
    secretResponse.created(secretResponseWrapper.getCreatedAt());
    secretResponse.updated(secretResponseWrapper.getUpdatedAt());
    secretResponse.setDraft(secretResponseWrapper.isDraft());
    secretResponse.setGovernanceMetadata(secretResponseWrapper.getGovernanceMetadata());
    return secretResponse;
  }

  public Secret toSecret(SecretDTOV2 secretDTOV2) {
    Secret secret = new Secret()
                        .identifier(secretDTOV2.getIdentifier())
                        .name(secretDTOV2.getName())
                        .org(secretDTOV2.getOrgIdentifier())
                        .project(secretDTOV2.getProjectIdentifier())
                        .description(secretDTOV2.getDescription())
                        .tags(secretDTOV2.getTags());

    switch (secretDTOV2.getType()) {
      case SSHKey:
        SSHKeySpecDTO sshKeySpecDTO = (SSHKeySpecDTO) secretDTOV2.getSpec();

        if (SSHAuthScheme.SSH.equals(sshKeySpecDTO.getAuth().getAuthScheme())) {
          SSHConfigDTO sshConfigDTO = (SSHConfigDTO) sshKeySpecDTO.getAuth().getSpec();
          if (SSHCredentialType.KeyPath.equals(sshConfigDTO.getCredentialType())) {
            SSHKeyPathSpec sshKeyPathSpec = getSshKeyPathSpec(sshKeySpecDTO, sshConfigDTO);

            secret.setSpec(sshKeyPathSpec);
          } else if (SSHCredentialType.KeyReference.equals(sshConfigDTO.getCredentialType())) {
            SSHKeyReferenceSpec sshKeyReferenceSpec = getSshKeyReferenceSpec(sshKeySpecDTO, sshConfigDTO);

            secret.setSpec(sshKeyReferenceSpec);
          } else if (SSHCredentialType.Password.equals(sshConfigDTO.getCredentialType())) {
            SSHPasswordSpec sshPasswordSpec = getSshPasswordSpec(sshKeySpecDTO, sshConfigDTO);

            secret.setSpec(sshPasswordSpec);
          }
        } else if (SSHAuthScheme.Kerberos.equals(sshKeySpecDTO.getAuth().getAuthScheme())) {
          KerberosConfigDTO kerberosConfigDTO = (KerberosConfigDTO) sshKeySpecDTO.getAuth().getSpec();

          if (TGTGenerationMethod.KeyTabFilePath.equals(kerberosConfigDTO.getTgtGenerationMethod())) {
            SSHKerberosTGTKeyTabFileSpec sshKerberosTGTKeyTabFileSpec =
                getSshKerberosTGTKeyTabFileSpec(sshKeySpecDTO, kerberosConfigDTO);

            secret.setSpec(sshKerberosTGTKeyTabFileSpec);
          } else if (TGTGenerationMethod.Password.equals(kerberosConfigDTO.getTgtGenerationMethod())) {
            SSHKerberosTGTPasswordSpec sshKerberosTGTPasswordSpec =
                getSshKerberosTGTPasswordSpec(sshKeySpecDTO, kerberosConfigDTO);

            secret.setSpec(sshKerberosTGTPasswordSpec);
          }
        }
        break;

      case SecretText:
        SecretTextSpecDTO secretTextSpecDTO = (SecretTextSpecDTO) secretDTOV2.getSpec();

        SecretTextSpec secretTextSpec = getSecretTextSpec(secretTextSpecDTO);

        secret.setSpec(secretTextSpec);
        break;

      case SecretFile:
        SecretFileSpecDTO secretFileSpecDTO = (SecretFileSpecDTO) secretDTOV2.getSpec();

        SecretFileSpec secretFileSpec = getSecretFileSpec(secretFileSpecDTO);

        secret.setSpec(secretFileSpec);
        break;
      case WinRmCredentials:
        WinRmCredentialsSpecDTO winRmCredentialsSpecDTO = (WinRmCredentialsSpecDTO) secretDTOV2.getSpec();
        if (WinRmAuthScheme.Kerberos.equals(winRmCredentialsSpecDTO.getAuth().getAuthScheme())) {
          KerberosWinRmConfigDTO kerberosWinRmConfigDTO =
              (KerberosWinRmConfigDTO) winRmCredentialsSpecDTO.getAuth().getSpec();

          if (TGTGenerationMethod.KeyTabFilePath.equals(kerberosWinRmConfigDTO.getTgtGenerationMethod())) {
            WinRmTGTKeyTabFileSpec winRmTGTKeyTabFileSpec =
                getWinRmTGTKeyTabFileSpec(winRmCredentialsSpecDTO, kerberosWinRmConfigDTO);

            secret.setSpec(winRmTGTKeyTabFileSpec);
          } else if (TGTGenerationMethod.Password.equals(kerberosWinRmConfigDTO.getTgtGenerationMethod())) {
            WinRmTGTPasswordSpec winRmTGTPasswordSpec =
                getWinRmTGTPasswordSpec(winRmCredentialsSpecDTO, kerberosWinRmConfigDTO);

            secret.setSpec(winRmTGTPasswordSpec);
          }
        } else if (WinRmAuthScheme.NTLM.equals(winRmCredentialsSpecDTO.getAuth().getAuthScheme())) {
          WinRmNTLMSpec winRmNTLMSpec = getWinRmNTLMSpec(winRmCredentialsSpecDTO);

          secret.setSpec(winRmNTLMSpec);
        }
        break;
      default:
        throw new InvalidRequestException(String.format("Unable to map secret type [%s]", secretDTOV2.getType()));
    }
    return secret;
  }

  private WinRmNTLMSpec getWinRmNTLMSpec(WinRmCredentialsSpecDTO winRmCredentialsSpecDTO) {
    NTLMConfigDTO ntlmConfigDTO = (NTLMConfigDTO) winRmCredentialsSpecDTO.getAuth().getSpec();
    WinRmNTLMSpec winRmNTLMSpec = new WinRmNTLMSpec();
    winRmNTLMSpec.setType(WINRMNTLM);
    winRmNTLMSpec.setPort(winRmCredentialsSpecDTO.getPort());
    winRmNTLMSpec.setDomain(ntlmConfigDTO.getDomain());
    winRmNTLMSpec.setUsername(ntlmConfigDTO.getUsername());
    winRmNTLMSpec.setUseSsl(ntlmConfigDTO.isUseSSL());
    winRmNTLMSpec.setSkipCertChecks(ntlmConfigDTO.isSkipCertChecks());
    winRmNTLMSpec.setUseNoProfile(ntlmConfigDTO.isUseNoProfile());
    winRmNTLMSpec.setPassword(ntlmConfigDTO.getPassword().toSecretRefStringValue());
    return winRmNTLMSpec;
  }

  private WinRmTGTPasswordSpec getWinRmTGTPasswordSpec(
      WinRmCredentialsSpecDTO winRmCredentialsSpecDTO, KerberosWinRmConfigDTO kerberosWinRmConfigDTO) {
    WinRmTGTPasswordSpec winRmTGTPasswordSpec = new WinRmTGTPasswordSpec();
    winRmTGTPasswordSpec.setType(WINRMTGTPASSWORD);
    winRmTGTPasswordSpec.setPort(winRmCredentialsSpecDTO.getPort());

    winRmTGTPasswordSpec.setPrincipal(kerberosWinRmConfigDTO.getPrincipal());
    winRmTGTPasswordSpec.setRealm(kerberosWinRmConfigDTO.getRealm());

    TGTPasswordSpecDTO tgtPasswordSpecDTO = (TGTPasswordSpecDTO) kerberosWinRmConfigDTO.getSpec();
    winRmTGTPasswordSpec.setPassword(tgtPasswordSpecDTO.getPassword().toSecretRefStringValue());
    winRmTGTPasswordSpec.setUseSsl(kerberosWinRmConfigDTO.isUseSSL());
    winRmTGTPasswordSpec.setSkipCertChecks(kerberosWinRmConfigDTO.isSkipCertChecks());
    winRmTGTPasswordSpec.setUseNoProfile(kerberosWinRmConfigDTO.isUseNoProfile());
    return winRmTGTPasswordSpec;
  }

  private WinRmTGTKeyTabFileSpec getWinRmTGTKeyTabFileSpec(
      WinRmCredentialsSpecDTO winRmCredentialsSpecDTO, KerberosWinRmConfigDTO kerberosWinRmConfigDTO) {
    WinRmTGTKeyTabFileSpec winRmTGTKeyTabFileSpec = new WinRmTGTKeyTabFileSpec();
    winRmTGTKeyTabFileSpec.setType(WINRMTGTKEYTABFILE);
    winRmTGTKeyTabFileSpec.setPort(winRmCredentialsSpecDTO.getPort());

    winRmTGTKeyTabFileSpec.setPrincipal(kerberosWinRmConfigDTO.getPrincipal());
    winRmTGTKeyTabFileSpec.setRealm(kerberosWinRmConfigDTO.getRealm());

    TGTKeyTabFilePathSpecDTO tgtKeyTabFilePathSpecDTO = (TGTKeyTabFilePathSpecDTO) kerberosWinRmConfigDTO.getSpec();
    winRmTGTKeyTabFileSpec.setKeyPath(tgtKeyTabFilePathSpecDTO.getKeyPath());
    winRmTGTKeyTabFileSpec.setUseSsl(kerberosWinRmConfigDTO.isUseSSL());
    winRmTGTKeyTabFileSpec.setSkipCertChecks(kerberosWinRmConfigDTO.isSkipCertChecks());
    winRmTGTKeyTabFileSpec.setUseNoProfile(kerberosWinRmConfigDTO.isUseNoProfile());
    return winRmTGTKeyTabFileSpec;
  }

  private SecretFileSpec getSecretFileSpec(SecretFileSpecDTO secretFileSpecDTO) {
    SecretFileSpec secretFileSpec = new SecretFileSpec();
    secretFileSpec.setType(SECRETFILE);
    secretFileSpec.setSecretManagerIdentifier(secretFileSpecDTO.getSecretManagerIdentifier());
    return secretFileSpec;
  }

  private SecretTextSpec getSecretTextSpec(SecretTextSpecDTO secretTextSpecDTO) {
    SecretTextSpec secretTextSpec = new SecretTextSpec();
    secretTextSpec.setType(SECRETTEXT);
    secretTextSpec.secretManagerIdentifier(secretTextSpecDTO.getSecretManagerIdentifier());
    secretTextSpec.setValueType(fromValue(secretTextSpecDTO.getValueType().name()));
    secretTextSpec.setValue(secretTextSpecDTO.getValue());
    return secretTextSpec;
  }

  private SSHKerberosTGTPasswordSpec getSshKerberosTGTPasswordSpec(
      SSHKeySpecDTO sshKeySpecDTO, KerberosConfigDTO kerberosConfigDTO) {
    SSHKerberosTGTPasswordSpec sshKerberosTGTPasswordSpec = new SSHKerberosTGTPasswordSpec();
    sshKerberosTGTPasswordSpec.setPort(sshKeySpecDTO.getPort());
    sshKerberosTGTPasswordSpec.setType(SSHKERBEROSTGTPASSWORD);
    sshKerberosTGTPasswordSpec.setPrincipal(kerberosConfigDTO.getPrincipal());
    sshKerberosTGTPasswordSpec.setRealm(kerberosConfigDTO.getRealm());
    TGTPasswordSpecDTO tgtPasswordSpecDTO = (TGTPasswordSpecDTO) kerberosConfigDTO.getSpec();
    sshKerberosTGTPasswordSpec.setPassword(tgtPasswordSpecDTO.getPassword().toSecretRefStringValue());
    return sshKerberosTGTPasswordSpec;
  }

  private SSHKerberosTGTKeyTabFileSpec getSshKerberosTGTKeyTabFileSpec(
      SSHKeySpecDTO sshKeySpecDTO, KerberosConfigDTO kerberosConfigDTO) {
    SSHKerberosTGTKeyTabFileSpec sshKerberosTGTKeyTabFileSpec = new SSHKerberosTGTKeyTabFileSpec();
    sshKerberosTGTKeyTabFileSpec.setPort(sshKeySpecDTO.getPort());
    sshKerberosTGTKeyTabFileSpec.setType(SSHKERBEROSTGTKEYTABFILE);
    sshKerberosTGTKeyTabFileSpec.setPrincipal(kerberosConfigDTO.getPrincipal());
    sshKerberosTGTKeyTabFileSpec.setRealm(kerberosConfigDTO.getRealm());
    TGTKeyTabFilePathSpecDTO tgtGenerationSpecDTO = (TGTKeyTabFilePathSpecDTO) kerberosConfigDTO.getSpec();
    sshKerberosTGTKeyTabFileSpec.setKeyPath(tgtGenerationSpecDTO.getKeyPath());
    return sshKerberosTGTKeyTabFileSpec;
  }

  private SSHPasswordSpec getSshPasswordSpec(SSHKeySpecDTO sshKeySpecDTO, SSHConfigDTO sshConfigDTO) {
    SSHPasswordSpec sshPasswordSpec = new SSHPasswordSpec();
    sshPasswordSpec.setPort(sshKeySpecDTO.getPort());
    SSHPasswordCredentialDTO sshPasswordCredentialDTO = (SSHPasswordCredentialDTO) sshConfigDTO.getSpec();
    sshPasswordSpec.setType(SSHPASSWORD);
    sshPasswordSpec.setUsername(sshPasswordCredentialDTO.getUserName());
    sshPasswordSpec.setPassword(sshPasswordCredentialDTO.getPassword().toSecretRefStringValue());
    return sshPasswordSpec;
  }

  private SSHKeyReferenceSpec getSshKeyReferenceSpec(SSHKeySpecDTO sshKeySpecDTO, SSHConfigDTO sshConfigDTO) {
    SSHKeyReferenceSpec sshKeyReferenceSpec = new SSHKeyReferenceSpec();
    sshKeyReferenceSpec.setPort(sshKeySpecDTO.getPort());
    SSHKeyReferenceCredentialDTO sshKeyReferenceCredentialDTO = (SSHKeyReferenceCredentialDTO) sshConfigDTO.getSpec();
    sshKeyReferenceSpec.setType(SSHKEYREFERENCE);
    sshKeyReferenceSpec.setUsername(sshKeyReferenceCredentialDTO.getUserName());
    sshKeyReferenceSpec.setKey(sshKeyReferenceCredentialDTO.getKey().toSecretRefStringValue());
    if (sshKeyReferenceCredentialDTO.getEncryptedPassphrase() != null) {
      sshKeyReferenceSpec.setEncryptedPassphrase(
          sshKeyReferenceCredentialDTO.getEncryptedPassphrase().toSecretRefStringValue());
    }
    return sshKeyReferenceSpec;
  }

  private SSHKeyPathSpec getSshKeyPathSpec(SSHKeySpecDTO sshKeySpecDTO, SSHConfigDTO sshConfigDTO) {
    SSHKeyPathSpec sshKeyPathSpec = new SSHKeyPathSpec();
    sshKeyPathSpec.setPort(sshKeySpecDTO.getPort());
    SSHKeyPathCredentialDTO sshKeyPathCredentialDTO = (SSHKeyPathCredentialDTO) sshConfigDTO.getSpec();
    sshKeyPathSpec.setType(SSHKEYPATH);
    sshKeyPathSpec.setUsername(sshKeyPathCredentialDTO.getUserName());
    sshKeyPathSpec.setKeyPath(sshKeyPathCredentialDTO.getKeyPath());
    if (sshKeyPathCredentialDTO.getEncryptedPassphrase() != null) {
      sshKeyPathSpec.setEncryptedPassphrase(sshKeyPathCredentialDTO.getEncryptedPassphrase().toSecretRefStringValue());
    }
    return sshKeyPathSpec;
  }

  public List<SecretType> toSecretTypes(List<String> type) {
    if (isEmpty(type)) {
      return new ArrayList<>();
    }
    return type.stream().map(SecretApiUtils::toSecretType).collect(Collectors.toList());
  }

  public static SecretType toSecretType(String type) {
    switch (type) {
      case "SSHKeyPath":
      case "SSHKeyReference":
      case "SSHPassword":
      case "SSHKerberosTGTKeyTabFile":
      case "SSHKerberosTGTPassword":
        return SecretType.SSHKey;
      case "SecretFile":
        return SecretType.SecretFile;
      case "SecretText":
        return SecretType.SecretText;
      case "WinRmTGTKeyTabFile":
      case "WinRmTGTPassword":
      case "WinRmNTLM":
        return SecretType.WinRmCredentials;
      default:
        return null;
    }
  }
}
