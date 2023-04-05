/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.shell;

import io.harness.encryption.Encrypted;

import software.wings.annotation.EncryptableSetting;
import software.wings.settings.SettingVariableTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.ToString;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by anubhaw on 2/8/16.
 */
@Data
@ToString(exclude = {"password", "key", "keyPassphrase", "sudoAppPassword", "sshPassword"})
public class SshSessionConfig implements EncryptableSetting, ScriptExecutionContext {
  @NotEmpty private String accountId;
  @NotEmpty private String appId;
  @NotEmpty private ExecutorType executorType;
  @NotEmpty private String executionId;
  @NotEmpty private String commandUnitName;
  private Integer socketConnectTimeout = (int) TimeUnit.SECONDS.toMillis(60);
  private Integer sshConnectionTimeout = (int) TimeUnit.MINUTES.toMillis(2);
  private Integer sshSessionTimeout = (int) TimeUnit.MINUTES.toMillis(10);
  private Integer retryInterval;
  @NotEmpty private String host;
  private Integer port = 22;
  private String userName;
  @Encrypted(fieldName = "password") private char[] password; // used for Kerberos/Bastion host authentication
  private String keyName;
  @Encrypted(fieldName = "key") private char[] key;
  @Encrypted(fieldName = "key_passphrase") private char[] keyPassphrase;
  private String sudoAppName;
  @Encrypted(fieldName = "sudo_app_password") private char[] sudoAppPassword;
  private SshSessionConfig bastionHostConfig;

  @SchemaIgnore private String encryptedPassword;

  @SchemaIgnore private String encryptedKey;

  @SchemaIgnore private String encryptedSudoAppPassword;

  private String keyPath;

  private boolean keyLess;
  private String workingDirectory;

  private AuthenticationScheme authenticationScheme;
  private KerberosConfig kerberosConfig;
  @Encrypted(fieldName = "ssh_password") private char[] sshPassword; // used to SSH into remote host
  @SchemaIgnore private String encryptedSshPassword;
  private AccessType accessType;

  private boolean isVaultSSH;
  private String publicKey;
  private String signedPublicKey;

  // environment variables for remote session
  private Map<String, String> envVariables;
  @SchemaIgnore private boolean useSshClient;
  @SchemaIgnore private boolean useSshj;

  @Override
  public SettingVariableTypes getSettingType() {
    return SettingVariableTypes.SSH_SESSION_CONFIG;
  }

  @Override
  @JsonIgnore
  @SchemaIgnore
  public boolean isDecrypted() {
    return false;
  }

  @Override
  public void setDecrypted(boolean decrypted) {
    throw new IllegalStateException();
  }

  public static final class Builder {
    private String accountId;
    private String appId;
    private ExecutorType executorType;
    private String executionId;
    private String commandUnitName;
    private Integer socketConnectTimeout = (int) TimeUnit.SECONDS.toMillis(60);
    private Integer sshConnectionTimeout = (int) TimeUnit.MINUTES.toMillis(2);
    private Integer sshSessionTimeout = (int) TimeUnit.MINUTES.toMillis(10);
    private Integer retryInterval;
    private String host;
    private Integer port = 22;
    private String userName;
    private char[] password;
    private String keyName;
    private char[] key;
    private char[] keyPassphrase;
    private String sudoAppName;
    private char[] sudoAppPassword;
    private SshSessionConfig bastionHostConfig;
    private String keyPath;
    private boolean keyLess;
    private String workingDirectory;
    private AuthenticationScheme authenticationScheme;
    private KerberosConfig kerberosConfig;
    private char[] sshPassword;
    private AccessType accessType;
    private boolean isVaultSSH;
    private String publicKey;
    private String signedPublicKey;
    private Map<String, String> envVariables;
    private boolean useSshClient;
    private boolean useSshj;

    private Builder() {}

    public static Builder aSshSessionConfig() {
      return new Builder();
    }

    public Builder withAccountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    public Builder withPublicKey(String publicKey) {
      this.publicKey = publicKey;
      return this;
    }

    public Builder withSignedPublicKey(String signedPublicKey) {
      this.signedPublicKey = signedPublicKey;
      return this;
    }

    public Builder withVaultSSH(boolean isVaultSSH) {
      this.isVaultSSH = isVaultSSH;
      return this;
    }

    public Builder withAppId(String appId) {
      this.appId = appId;
      return this;
    }

    public Builder withExecutorType(ExecutorType executorType) {
      this.executorType = executorType;
      return this;
    }

    public Builder withExecutionId(String executionId) {
      this.executionId = executionId;
      return this;
    }

    public Builder withCommandUnitName(String commandUnitName) {
      this.commandUnitName = commandUnitName;
      return this;
    }

    public Builder withSocketConnectTimeout(Integer socketConnectTimeout) {
      this.socketConnectTimeout = socketConnectTimeout;
      return this;
    }

    public Builder withSshConnectionTimeout(Integer sshConnectionTimeout) {
      this.sshConnectionTimeout = sshConnectionTimeout;
      return this;
    }

    public Builder withSshSessionTimeout(Integer sshSessionTimeout) {
      this.sshSessionTimeout = sshSessionTimeout;
      return this;
    }

    public Builder withRetryInterval(Integer retryInterval) {
      this.retryInterval = retryInterval;
      return this;
    }

    public Builder withHost(String host) {
      this.host = host;
      return this;
    }

    public Builder withPort(Integer port) {
      this.port = port;
      return this;
    }

    public Builder withUserName(String userName) {
      this.userName = userName;
      return this;
    }

    public Builder withPassword(char[] password) {
      this.password = password == null ? null : password.clone();
      return this;
    }

    public Builder withKeyName(String keyName) {
      this.keyName = keyName;
      return this;
    }

    public Builder withKey(char[] key) {
      this.key = key == null ? null : key.clone();
      return this;
    }

    public Builder withKeyPassphrase(char[] keyPassphrase) {
      this.keyPassphrase = keyPassphrase == null ? null : keyPassphrase.clone();
      return this;
    }

    public Builder withSudoAppName(String sudoAppName) {
      this.sudoAppName = sudoAppName;
      return this;
    }

    public Builder withSudoAppPassword(char[] sudoAppPassword) {
      this.sudoAppPassword = sudoAppPassword == null ? null : sudoAppPassword.clone();
      return this;
    }

    public Builder withBastionHostConfig(SshSessionConfig bastionHostConfig) {
      this.bastionHostConfig = bastionHostConfig;
      return this;
    }

    public Builder withKeyPath(String keyPath) {
      this.keyPath = keyPath;
      return this;
    }

    public Builder withKeyLess(boolean keyLess) {
      this.keyLess = keyLess;
      return this;
    }

    public Builder withWorkingDirectory(String workingDirectory) {
      this.workingDirectory = workingDirectory;
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

    public Builder withSshPassword(char[] sshPassword) {
      this.sshPassword = sshPassword == null ? null : sshPassword.clone();
      return this;
    }

    public Builder withAccessType(AccessType accessType) {
      this.accessType = accessType;
      return this;
    }

    public Builder withEnvVariables(Map<String, String> envVariables) {
      this.envVariables = envVariables;
      return this;
    }

    public Builder withUseSshClient(boolean useSshClient) {
      this.useSshClient = useSshClient;
      return this;
    }

    public Builder withUseSshj(boolean useSshj) {
      this.useSshj = useSshj;
      return this;
    }

    public Builder but() {
      return aSshSessionConfig()
          .withAccountId(accountId)
          .withAppId(appId)
          .withExecutorType(executorType)
          .withExecutionId(executionId)
          .withCommandUnitName(commandUnitName)
          .withSocketConnectTimeout(socketConnectTimeout)
          .withSshConnectionTimeout(sshConnectionTimeout)
          .withSshSessionTimeout(sshSessionTimeout)
          .withRetryInterval(retryInterval)
          .withHost(host)
          .withPort(port)
          .withUserName(userName)
          .withPassword(password)
          .withKeyName(keyName)
          .withKey(key)
          .withKeyPassphrase(keyPassphrase)
          .withSudoAppName(sudoAppName)
          .withSudoAppPassword(sudoAppPassword)
          .withBastionHostConfig(bastionHostConfig)
          .withKeyPath(keyPath)
          .withKeyLess(keyLess)
          .withWorkingDirectory(workingDirectory)
          .withAuthenticationScheme(authenticationScheme)
          .withKerberosConfig(kerberosConfig)
          .withSshPassword(sshPassword)
          .withAccessType(accessType)
          .withPublicKey(publicKey)
          .withSignedPublicKey(signedPublicKey)
          .withVaultSSH(isVaultSSH)
          .withEnvVariables(envVariables)
          .withUseSshClient(useSshClient)
          .withUseSshj(useSshj);
    }

    public SshSessionConfig build() {
      SshSessionConfig sshSessionConfig = new SshSessionConfig();
      sshSessionConfig.setAccountId(accountId);
      sshSessionConfig.setAppId(appId);
      sshSessionConfig.setExecutorType(executorType);
      sshSessionConfig.setExecutionId(executionId);
      sshSessionConfig.setCommandUnitName(commandUnitName);
      sshSessionConfig.setSocketConnectTimeout(socketConnectTimeout);
      sshSessionConfig.setSshConnectionTimeout(sshConnectionTimeout);
      sshSessionConfig.setSshSessionTimeout(sshSessionTimeout);
      sshSessionConfig.setRetryInterval(retryInterval);
      sshSessionConfig.setHost(host);
      sshSessionConfig.setPort(port);
      sshSessionConfig.setUserName(userName);
      sshSessionConfig.setPassword(password);
      sshSessionConfig.setKeyName(keyName);
      sshSessionConfig.setKey(key);
      sshSessionConfig.setKeyPassphrase(keyPassphrase);
      sshSessionConfig.setSudoAppName(sudoAppName);
      sshSessionConfig.setSudoAppPassword(sudoAppPassword);
      sshSessionConfig.setBastionHostConfig(bastionHostConfig);
      sshSessionConfig.setKeyPath(keyPath);
      sshSessionConfig.setKeyLess(keyLess);
      sshSessionConfig.setWorkingDirectory(workingDirectory);
      sshSessionConfig.setAuthenticationScheme(authenticationScheme);
      sshSessionConfig.setKerberosConfig(kerberosConfig);
      sshSessionConfig.setSshPassword(sshPassword);
      sshSessionConfig.setAccessType(accessType);
      sshSessionConfig.setVaultSSH(isVaultSSH);
      sshSessionConfig.setSignedPublicKey(signedPublicKey);
      sshSessionConfig.setPublicKey(publicKey);
      sshSessionConfig.setEnvVariables(envVariables);
      sshSessionConfig.setUseSshClient(useSshClient);
      sshSessionConfig.setUseSshj(useSshj);
      return sshSessionConfig;
    }
  }
}
