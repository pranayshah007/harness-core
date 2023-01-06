/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.rules;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Security;
import org.apache.commons.lang3.StringUtils;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.scp.server.ScpCommandFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.AbstractGeneratorHostKeyProvider;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.rules.MethodRule;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

/**
 * Created by peeyushaggarwal on 7/27/16.
 */
@OwnedBy(CDP)
public class SshRule implements MethodRule {
  private SshServer sshd;
  private TemporaryFolder temporaryFolder;
  private String username = System.getProperty("user.name");
  private String password = "password";
  private Path keyPath;

  /**
   * Instantiates a new Ssh rule.
   *
   * @param temporaryFolder the temporary folder
   */
  public SshRule(TemporaryFolder temporaryFolder) {
    this.temporaryFolder = temporaryFolder;
  }

  @Override
  public Statement apply(Statement statement, FrameworkMethod frameworkMethod, Object o) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        SshRule.this.before();
        try {
          statement.evaluate();
        } finally {
          SshRule.this.after();
        }
      }
    };
  }

  /**
   * Gets port.
   *
   * @return the port
   */
  public int getPort() {
    return sshd.getPort();
  }

  /**
   * Getter for property 'username'.
   *
   * @return Value for property 'username'.
   */
  public String getUsername() {
    return username;
  }

  /**
   * Setter for property 'username'.
   *
   * @param username Value to set for property 'username'.
   */
  public void setUsername(String username) {
    this.username = username;
  }

  /**
   * Getter for property 'password'.
   *
   * @return Value for property 'password'.
   */
  public String getPassword() {
    return password;
  }

  /**
   * Setter for property 'password'.
   *
   * @param password Value to set for property 'password'.
   */
  public void setPassword(String password) {
    this.password = password;
    sshd.setPasswordAuthenticator(
        (username, pwd,
            session) -> StringUtils.equals(this.username, username) && StringUtils.equals(this.password, pwd));
  }

  /**
   * Getter for property 'keyPath'.
   *
   * @return Value for property 'keyPath'.
   */
  public Path getKeyPath() {
    return keyPath;
  }

  private void before() throws IOException, GeneralSecurityException {
    this.keyPath = new File(temporaryFolder.getRoot(), "key.pem").toPath();

    if (!SecurityUtils.isBouncyCastleRegistered()) {
      Security.addProvider(new BouncyCastleProvider());
    }
    sshd = SshServer.setUpDefaultServer();
    sshd.setPort(0);
    AbstractGeneratorHostKeyProvider generatorHostKeyProvider = SecurityUtils.createGeneratorHostKeyProvider(keyPath);
    generatorHostKeyProvider.setAlgorithm("RSA");
    sshd.setKeyPairProvider(generatorHostKeyProvider);

    sshd.setPasswordAuthenticator(
        (username, password,
            session) -> StringUtils.equals(this.username, username) && StringUtils.equals(this.password, password));
    sshd.setFileSystemFactory(new VirtualFileSystemFactory(temporaryFolder.getRoot().toPath()));
    sshd.setPublickeyAuthenticator((username, key, session) -> StringUtils.equals("ssh_user", username));
    sshd.setCommandFactory(
        new ScpCommandFactory.Builder()
            .withDelegate((session, command) -> new FileSystemAwareProcessShellFactory(command).createShell(session))
            .build());

    sshd.start();
  }

  private void after() throws IOException {
    sshd.stop(true);
  }
}
