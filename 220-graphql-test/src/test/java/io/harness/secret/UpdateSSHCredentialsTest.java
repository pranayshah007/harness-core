/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secret;

import static io.harness.rule.OwnerRule.DEEPAK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.GraphQLTest;
import io.harness.beans.SecretText;
import io.harness.category.element.UnitTests;
import io.harness.category.layer.GraphQLTests;
import io.harness.generator.AccountGenerator;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer;
import io.harness.generator.SecretGenerator;
import io.harness.rule.Owner;
import io.harness.scm.SecretName;
import io.harness.serializer.JsonUtils;
import io.harness.testframework.graphql.QLTestObject;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class UpdateSSHCredentialsTest extends GraphQLTest {
  @Inject private AccountGenerator accountGenerator;
  @Inject private OwnerManager ownerManager;
  @Inject private SecretManager secretManager;
  @Inject private SecretGenerator secretGenerator;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject SSHCredentialHelper sshCredentialHelper;
  private String updatedSecretName = "updatedSecretName";
  private String updatedUserName = "updatedUserName";
  private int updatedPort = 222;
  private String accountId;
  private String secretId;
  private String sshKeySecretId;
  private String updatedPrincipal = "updatedPrincipal";
  private String updatedRealm = "updatedRealm";

  @Before
  public void setup() {
    final OwnerManager.Owners owners = ownerManager.create();
    final Randomizer.Seed seed = new Randomizer.Seed(0);
    final Application application =
        applicationGenerator.ensurePredefined(seed, owners, ApplicationGenerator.Applications.GENERIC_TEST);
    Account account = accountGenerator.ensurePredefined(seed, owners, AccountGenerator.Accounts.GENERIC_TEST);
    accountId = account.getUuid();
    secretId = sshCredentialHelper.createSSHCredential("secretName");
    sshKeySecretId = secretGenerator.ensureStored(owners, SecretName.builder().value("pcf_password").build());
  }

  private String updateMutationInput(String variable) {
    String query = $GQL(/*
  mutation{
      updateSecret(input:%s){
          clientMutationId
          secret{
          ... on WinRMCredential{
            name
            id
            port
            skipCertCheck
            useSSL
            userName
            authenticationScheme
        }
        ... on SSHCredential{
            id
            name
            authenticationType{
              ... on SSHAuthentication{
                  userName
                  port
             }
              ... on KerberosAuthentication{
                  port
                  realm
                  principal
              }
             }
        }
      }
    }
  }
 */ variable);
    return query;
  }

  private String getUpdateSSHCredentialInput() {
    String queryVariable = $GQL(/*
  {
      secretType: SSH_CREDENTIAL,
      secretId: "%s",
      sshCredential: {
      name: "%s",
      authenticationScheme: SSH,
      sshAuthentication: {
          port: %d,
          userName: "%s",
          sshAuthenticationMethod: {
          sshCredentialType: SSH_KEY,
          inlineSSHKey: {
              sshKeySecretFileId: "%s"
            }
          }
      }
      }
  }

  */ secretId, updatedSecretName, updatedPort, updatedUserName, sshKeySecretId);
    return queryVariable;
  }

  private void verifySSHResult(SSHCredentialHelper.SSHResult result) {
    SSHCredentialHelper.SSHCredentialResult ssh = result.getSecret();
    assertThat(ssh.getId()).isNotNull();
    assertThat(ssh.getName()).isEqualTo(updatedSecretName);
    SSHCredentialHelper.SSHAuthenticationType authenticationType = ssh.getAuthenticationType();
    assertThat(authenticationType.getUserName()).isEqualTo(updatedUserName);
    assertThat(authenticationType.getPort()).isEqualTo(updatedPort);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testUpdatingSSHCred() {
    String query = updateMutationInput(getUpdateSSHCredentialInput());
    final QLTestObject qlTestObject = qlExecute(query, accountId);
    final SSHCredentialHelper.SSHResult result =
        JsonUtils.convertValue(qlTestObject.getMap(), SSHCredentialHelper.SSHResult.class);
    verifySSHResult(result);
  }

  private String getkerberosUpdateInput() {
    String passwordSecretId = secretManager.saveSecretText(
        accountId, SecretText.builder().name("kerberosPasswordSecretId").value("abc").build(), false);
    String queryVariable = $GQL(/*

    {
      secretType: SSH_CREDENTIAL,
      secretId: "%s",
      sshCredential: {
        name: "%s",
        authenticationScheme: KERBEROS,
          kerberosAuthentication: {
          port: %d,
          principal: "%s",
          realm: "%s",
          tgtGenerationMethod: {
            kerberosPassword: {
              passwordSecretId: "%s"
            },
            tgtGenerationUsing: PASSWORD
          }
        }
      }
    }

*/ secretId, updatedSecretName, updatedPort, updatedPrincipal, updatedRealm, passwordSecretId);
    return queryVariable;
  }

  private void verifyKerberosResult(SSHCredentialHelper.KerberosResult result) {
    SSHCredentialHelper.KerberosCredentialResult ssh = result.getSecret();
    assertThat(ssh.getId()).isNotNull();
    assertThat(ssh.getName()).isEqualTo(updatedSecretName);
    SSHCredentialHelper.KerberosAuthenticationType authenticationType = ssh.getAuthenticationType();
    assertThat(authenticationType.getPrincipal()).isEqualTo(updatedPrincipal);
    assertThat(authenticationType.getPort()).isEqualTo(updatedPort);
    assertThat(authenticationType.getRealm()).isEqualTo(updatedRealm);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testCreatingKerberosCredWithKeyTabFile() {
    String query = updateMutationInput(getkerberosUpdateInput());
    final QLTestObject qlTestObject = qlExecute(query, accountId);
    final SSHCredentialHelper.KerberosResult result =
        JsonUtils.convertValue(qlTestObject.getMap(), SSHCredentialHelper.KerberosResult.class);
    verifyKerberosResult(result);
  }
}
