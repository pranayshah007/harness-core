/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.shell.AuthenticationScheme.SSH_KEY;

import static software.wings.audit.ResourceType.CONNECTION_ATTRIBUTES;
import static software.wings.settings.SettingVariableTypes.HOST_CONNECTION_ATTRIBUTES;

import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.encryption.Encrypted;
import io.harness.expression.ExpressionEvaluator;
import io.harness.shell.AccessType;
import io.harness.shell.AuthenticationScheme;
import io.harness.shell.KerberosConfig;

import software.wings.annotation.EncryptableSetting;
import software.wings.jersey.JsonViews;
import software.wings.security.UsageRestrictions;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Created by anubhaw on 5/16/16.
 */
@JsonTypeName("HOST_CONNECTION_ATTRIBUTES")
@Data
@ToString(exclude = {"key", "passphrase", "sshPassword", "kerberosPassword"})
@EqualsAndHashCode(callSuper = false)
@TargetModule(_957_CG_BEANS)
public class HostConnectionAttributes extends SettingValue implements EncryptableSetting {
  public static final String KEY_KEY = "key";
  public static final String KEY_PASSPHRASE = "passphrase";
  public static final String KEY_SSH_PASSWORD = "sshPassword";
  public static final String KEY_KERBEROS_PASSWORD = "kerberosPassword";

  @Attributes(title = "Connection Type", required = true) @NotNull private ConnectionType connectionType;
  @Attributes(title = "Access Type", required = true) @NotNull private AccessType accessType;

  @Attributes(title = "User Name") private String userName;
  @Attributes(title = "SSH Password") @Encrypted(fieldName = "ssh_password") private char[] sshPassword;
  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedSshPassword;
  @Attributes(title = "SSH Port") private Integer sshPort = 22;
  @Attributes(title = "Key") @Encrypted(fieldName = "key") private char[] key;
  @SchemaIgnore @NotNull private String accountId;

  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedKey;

  private boolean keyless;
  private String keyPath;
  @Attributes(title = "Pass Phrase") @Encrypted(fieldName = "passphrase") private char[] passphrase;
  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedPassphrase;

  @Attributes(title = "Auth Scheme") private AuthenticationScheme authenticationScheme = SSH_KEY;

  private boolean isVaultSSH;
  private String role;
  private String publicKey;
  private String signedPublicKey;
  private String sshVaultConfigId;
  private SSHVaultConfig sshVaultConfig;

  @Override
  public String fetchResourceCategory() {
    return CONNECTION_ATTRIBUTES.name();
  }

  // Just Returning [] here as id the task type is HostValidation adding an additional parameter which is a
  // combination of hosts and port instance type of ConnectivityCapabilityDemander
  // The implementation is in DelegateServiceImpl.addMergedParamsForCapabilityCheck
  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return new ArrayList<>();
  }

  @Attributes private KerberosConfig kerberosConfig;
  @Attributes(title = "Kerberos Password") @Encrypted(fieldName = "kerberos_password") private char[] kerberosPassword;
  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedKerberosPassword;

  /**
   * Instantiates a new host connection attributes.
   */
  public HostConnectionAttributes() {
    super(HOST_CONNECTION_ATTRIBUTES.name());
  }

  /**
   * Instantiates a new host connection attributes.
   *
   * @param type the type
   */
  public HostConnectionAttributes(SettingVariableTypes type) {
    super(type.name());
  }

  /**
   * The Enum ConnectionType.
   */
  public enum ConnectionType {
    /**
     * Ssh connection type.
     */
    SSH
  }

  public static final class Builder {
    private ConnectionType connectionType;
    private AccessType accessType;
    private char[] key;
    private String accountId;
    private String userName;
    private Integer sshPort = 22;
    private String encryptedKey;
    private boolean keyless;
    private String keyPath;
    private AuthenticationScheme authenticationScheme = SSH_KEY;
    private KerberosConfig kerberosConfig;
    private char[] passphrase;
    private String encryptedPassphrase;
    private char[] sshPassword;
    private char[] kerberosPassword;
    private String encryptedSshPassword;
    private boolean isVaultSSH;
    private String role;
    private String publicKey;
    private String signedPublicKey;
    private String sshVaultConfigId;
    private SSHVaultConfig sshVaultConfig;

    private Builder() {}

    public static Builder aHostConnectionAttributes() {
      return new Builder();
    }

    public Builder withConnectionType(ConnectionType connectionType) {
      this.connectionType = connectionType;
      return this;
    }

    public Builder withVaultSSH(boolean isVaultSSH) {
      this.isVaultSSH = isVaultSSH;
      return this;
    }

    public Builder withAccessType(AccessType accessType) {
      this.accessType = accessType;
      return this;
    }

    public Builder withUserName(String userName) {
      this.userName = userName;
      return this;
    }

    public Builder withSshPort(Integer sshPort) {
      this.sshPort = sshPort;
      return this;
    }

    public Builder withKey(char[] key) {
      this.key = key == null ? null : key.clone();
      return this;
    }

    public Builder withAccountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    public Builder withEncryptedKey(String encryptedKey) {
      this.encryptedKey = encryptedKey;
      return this;
    }

    public Builder withKeyless(boolean keyless) {
      this.keyless = keyless;
      return this;
    }

    public Builder withKeyPath(String keyPath) {
      this.keyPath = keyPath;
      return this;
    }

    public Builder withAuthenticationScheme(AuthenticationScheme authenticationScheme) {
      this.authenticationScheme = authenticationScheme;
      return this;
    }

    public Builder withKerberosConfig(KerberosConfig kerberosConfig) {
      this.kerberosConfig = kerberosConfig;
      return this;
    }

    public Builder withPassphrase(char[] passphrase) {
      this.passphrase = passphrase == null ? null : passphrase.clone();
      return this;
    }

    public Builder withEncryptedPassphrase(String encryptedPassphrase) {
      this.encryptedPassphrase = encryptedPassphrase;
      return this;
    }

    public Builder withSshPassword(char[] sshPassword) {
      this.sshPassword = sshPassword == null ? null : sshPassword.clone();
      return this;
    }

    public Builder withKerberosPassword(char[] kerberosPassword) {
      this.kerberosPassword = kerberosPassword == null ? null : kerberosPassword.clone();
      return this;
    }

    public Builder withEncryptedSshPassword(String encryptedSshPassword) {
      this.encryptedSshPassword = encryptedSshPassword;
      return this;
    }

    public Builder but() {
      return aHostConnectionAttributes()
          .withConnectionType(connectionType)
          .withAccessType(accessType)
          .withUserName(userName)
          .withSshPort(sshPort)
          .withKey(key)
          .withAccountId(accountId)
          .withEncryptedKey(encryptedKey)
          .withKeyless(keyless)
          .withKeyPath(keyPath)
          .withAuthenticationScheme(authenticationScheme)
          .withKerberosConfig(kerberosConfig)
          .withPassphrase(passphrase)
          .withEncryptedPassphrase(encryptedPassphrase)
          .withSshPassword(sshPassword)
          .withKerberosPassword(kerberosPassword)
          .withEncryptedSshPassword(encryptedSshPassword)
          .withVaultSSH(isVaultSSH);
    }

    public HostConnectionAttributes build() {
      HostConnectionAttributes hostConnectionAttributes = new HostConnectionAttributes();
      hostConnectionAttributes.setConnectionType(connectionType);
      hostConnectionAttributes.setAccessType(accessType);
      hostConnectionAttributes.setUserName(userName);
      hostConnectionAttributes.setSshPort(sshPort);
      hostConnectionAttributes.setKey(key);
      hostConnectionAttributes.setAccountId(accountId);
      hostConnectionAttributes.setEncryptedKey(encryptedKey);
      hostConnectionAttributes.setKeyless(keyless);
      hostConnectionAttributes.setKeyPath(keyPath);
      hostConnectionAttributes.setAuthenticationScheme(authenticationScheme);
      hostConnectionAttributes.setKerberosConfig(kerberosConfig);
      hostConnectionAttributes.setPassphrase(passphrase);
      hostConnectionAttributes.setEncryptedPassphrase(encryptedPassphrase);
      hostConnectionAttributes.setSshPassword(sshPassword);
      hostConnectionAttributes.setEncryptedSshPassword(encryptedSshPassword);
      hostConnectionAttributes.setKerberosPassword(kerberosPassword);
      hostConnectionAttributes.setVaultSSH(isVaultSSH);
      return hostConnectionAttributes;
    }
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends SettingValue.Yaml {
    private String connectionType;
    private String accessType;
    private String userName;
    private String key;

    @lombok.Builder
    public Yaml(String type, String harnessApiVersion, String connectionType, String accessType, String userName,
        String key, UsageRestrictions.Yaml usageRestrictions) {
      super(type, harnessApiVersion, usageRestrictions);
      this.connectionType = connectionType;
      this.accessType = accessType;
      this.userName = userName;
      this.key = key;
    }
  }
}
