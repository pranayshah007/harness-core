/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.KARAN;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.SHUBHAM_MAHESHWARI;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.SettingAttribute.SettingCategory.CLOUD_PROVIDER;
import static software.wings.beans.SettingAttribute.SettingCategory.CONNECTOR;
import static software.wings.beans.StringValue.Builder.aStringValue;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.UnauthorizedUsageRestrictionsException;
import io.harness.helper.SettingValueHelper;
import io.harness.k8s.model.KubernetesClusterAuthType;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.APMVerificationConfig;
import software.wings.beans.AppDynamicsConfig;
import software.wings.beans.BugsnagConfig;
import software.wings.beans.CGConstants;
import software.wings.beans.DatadogConfig;
import software.wings.beans.DockerConfig;
import software.wings.beans.DynaTraceConfig;
import software.wings.beans.ElkConfig;
import software.wings.beans.GitConfig;
import software.wings.beans.InstanaConfig;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.NewRelicConfig;
import software.wings.beans.PcfConfig;
import software.wings.beans.PrometheusConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SplunkConfig;
import software.wings.beans.SumoConfig;
import software.wings.beans.config.LogzConfig;
import software.wings.beans.settings.azureartifacts.AzureArtifactsPATConfig;
import software.wings.beans.settings.helm.GCSHelmRepoConfig;
import software.wings.beans.settings.helm.HttpHelmRepoConfig;
import software.wings.helpers.ext.mail.SmtpConfig;
import software.wings.security.UsageRestrictions;
import software.wings.service.intfc.UsageRestrictionsService;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.settings.SettingValue;

import com.google.inject.Inject;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDC)
public class SettingsServiceHelperTest extends WingsBaseTest {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String PAT = "PAT";
  private static final String RANDOM = "RANDOM";
  private static final String DOCKER_REGISTRY_URL = "DOCKER_REGISTRY_URL";

  @Mock private SecretManager secretManager;
  @Mock private ManagerDecryptionService managerDecryptionService;
  @Mock private UsageRestrictionsService usageRestrictionsService;

  @InjectMocks @Inject private SettingServiceHelper settingServiceHelper;

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testHasReferencedSecrets() {
    assertThat(settingServiceHelper.hasReferencedSecrets(null)).isFalse();
    SettingAttribute settingAttribute = aSettingAttribute().withCategory(CONNECTOR).build();
    assertThat(settingServiceHelper.hasReferencedSecrets(settingAttribute)).isFalse();
    settingAttribute.setValue(AzureArtifactsPATConfig.builder().build());
    assertThat(settingServiceHelper.hasReferencedSecrets(settingAttribute)).isFalse();

    settingAttribute.setAccountId(ACCOUNT_ID);
    assertThat(settingServiceHelper.hasReferencedSecrets(settingAttribute)).isTrue();

    settingAttribute.setValue(HttpHelmRepoConfig.builder().build());
    assertThat(settingServiceHelper.hasReferencedSecrets(settingAttribute)).isTrue();

    settingAttribute.setValue(GitConfig.builder().build());
    assertThat(settingServiceHelper.hasReferencedSecrets(settingAttribute)).isTrue();

    settingAttribute.setValue(SmtpConfig.builder().build());
    assertThat(settingServiceHelper.hasReferencedSecrets(settingAttribute)).isTrue();

    settingAttribute.setCategory(CLOUD_PROVIDER);
    assertThat(settingServiceHelper.hasReferencedSecrets(settingAttribute)).isTrue();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testHasReferencedSecretsVerificatonConnectors() {
    SettingAttribute settingAttribute = aSettingAttribute().withCategory(CONNECTOR).withAccountId(ACCOUNT_ID).build();
    settingAttribute.setValue(AppDynamicsConfig.builder().build());
    assertThat(settingServiceHelper.hasReferencedSecrets(settingAttribute)).isTrue();

    settingAttribute.setValue(NewRelicConfig.builder().build());
    assertThat(settingServiceHelper.hasReferencedSecrets(settingAttribute)).isTrue();

    settingAttribute.setValue(InstanaConfig.builder().build());
    assertThat(settingServiceHelper.hasReferencedSecrets(settingAttribute)).isTrue();

    settingAttribute.setValue(DynaTraceConfig.builder().build());
    assertThat(settingServiceHelper.hasReferencedSecrets(settingAttribute)).isTrue();

    settingAttribute.setValue(PrometheusConfig.builder().build());
    assertThat(settingServiceHelper.hasReferencedSecrets(settingAttribute)).isTrue();

    settingAttribute.setValue(DatadogConfig.builder().build());
    assertThat(settingServiceHelper.hasReferencedSecrets(settingAttribute)).isTrue();

    settingAttribute.setValue(BugsnagConfig.builder().build());
    assertThat(settingServiceHelper.hasReferencedSecrets(settingAttribute)).isTrue();

    settingAttribute.setValue(ElkConfig.builder().build());
    assertThat(settingServiceHelper.hasReferencedSecrets(settingAttribute)).isTrue();

    settingAttribute.setValue(SplunkConfig.builder().build());
    assertThat(settingServiceHelper.hasReferencedSecrets(settingAttribute)).isTrue();

    settingAttribute.setValue(SumoConfig.builder().build());
    assertThat(settingServiceHelper.hasReferencedSecrets(settingAttribute)).isTrue();

    settingAttribute.setValue(LogzConfig.builder().build());
    assertThat(settingServiceHelper.hasReferencedSecrets(settingAttribute)).isTrue();

    settingAttribute.setValue(new APMVerificationConfig());
    assertThat(settingServiceHelper.hasReferencedSecrets(settingAttribute)).isTrue();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testUpdateSettingAttributeBeforeResponse() {
    UsageRestrictions usageRestrictions = mock(UsageRestrictions.class);
    SettingAttribute settingAttribute = aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withCategory(CONNECTOR)
                                            .withUsageRestrictions(usageRestrictions)
                                            .build();
    // Should not throw an exception.
    settingServiceHelper.updateSettingAttributeBeforeResponse(settingAttribute, true);
    assertThat(settingAttribute.getUsageRestrictions()).isEqualTo(usageRestrictions);

    AzureArtifactsPATConfig azureArtifactsPATConfig =
        AzureArtifactsPATConfig.builder().pat(RANDOM.toCharArray()).encryptedPat(PAT).build();
    settingAttribute.setValue(azureArtifactsPATConfig);
    settingServiceHelper.updateSettingAttributeBeforeResponse(settingAttribute, true);
    assertThat(azureArtifactsPATConfig.getEncryptedPat()).isNull();
    assertThat(azureArtifactsPATConfig.getPat()).isEqualTo(PAT.toCharArray());
    assertThat(settingAttribute.getUsageRestrictions()).isNull();

    settingAttribute.setUsageRestrictions(usageRestrictions);
    SplunkConfig splunkConfig = SplunkConfig.builder().password(RANDOM.toCharArray()).encryptedPassword(PAT).build();
    settingAttribute.setValue(splunkConfig);
    settingServiceHelper.updateSettingAttributeBeforeResponse(settingAttribute, true);
    assertThat(splunkConfig.getEncryptedPassword()).isNull();
    assertThat(splunkConfig.getPassword()).isEqualTo(PAT.toCharArray());
    assertThat(settingAttribute.getUsageRestrictions()).isNull();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testUpdateReferencedSecrets() {
    SettingAttribute settingAttribute = aSettingAttribute().withAccountId(ACCOUNT_ID).withCategory(CONNECTOR).build();
    // Early return without exceptions.
    settingServiceHelper.updateReferencedSecrets(settingAttribute);

    AzureArtifactsPATConfig azureArtifactsPATConfig =
        AzureArtifactsPATConfig.builder().pat(RANDOM.toCharArray()).encryptedPat(PAT).build();
    settingAttribute.setValue(azureArtifactsPATConfig);
    settingServiceHelper.updateReferencedSecrets(settingAttribute);
    verify(managerDecryptionService, times(1)).decrypt(eq(azureArtifactsPATConfig), any());
    assertThat(azureArtifactsPATConfig.isDecrypted()).isFalse();

    azureArtifactsPATConfig.setDecrypted(true);
    settingServiceHelper.updateReferencedSecrets(settingAttribute);
    verify(managerDecryptionService, times(2)).decrypt(eq(azureArtifactsPATConfig), any());
    assertThat(azureArtifactsPATConfig.isDecrypted()).isTrue();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testResetEncryptedFields() {
    AzureArtifactsPATConfig azureArtifactsPATConfig =
        AzureArtifactsPATConfig.builder().pat(RANDOM.toCharArray()).encryptedPat(PAT).build();
    settingServiceHelper.resetEncryptedFields(azureArtifactsPATConfig);
    assertThat(azureArtifactsPATConfig.getPat()).isNull();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCopyToEncryptedRefFields() {
    AzureArtifactsPATConfig azureArtifactsPATConfig =
        AzureArtifactsPATConfig.builder().pat(null).encryptedPat(PAT).build();
    settingServiceHelper.copyToEncryptedRefFields(azureArtifactsPATConfig);
    assertThat(azureArtifactsPATConfig.getPat()).isNull();
    assertThat(azureArtifactsPATConfig.getEncryptedPat()).isEqualTo(PAT);

    azureArtifactsPATConfig = AzureArtifactsPATConfig.builder().pat(RANDOM.toCharArray()).encryptedPat(PAT).build();
    settingServiceHelper.copyToEncryptedRefFields(azureArtifactsPATConfig);
    assertThat(azureArtifactsPATConfig.getPat()).isNull();
    assertThat(azureArtifactsPATConfig.getEncryptedPat()).isEqualTo(RANDOM);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCopyFromEncryptedRefFields() {
    AzureArtifactsPATConfig azureArtifactsPATConfig =
        AzureArtifactsPATConfig.builder().pat(RANDOM.toCharArray()).encryptedPat(PAT).build();
    settingServiceHelper.copyFromEncryptedRefFields(azureArtifactsPATConfig);
    assertThat(azureArtifactsPATConfig.getPat()).isEqualTo(PAT.toCharArray());
    assertThat(azureArtifactsPATConfig.getEncryptedPat()).isNull();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetAllEncryptedSecrets() {
    // Not an EncryptableSetting.
    List<String> encryptedSecrets = SettingValueHelper.getAllEncryptedSecrets(null);
    assertThat(encryptedSecrets).isNotNull();
    assertThat(encryptedSecrets).isEmpty();

    // Does not contain any @Encrypted annotation.
    encryptedSecrets = SettingValueHelper.getAllEncryptedSecrets(GCSHelmRepoConfig.builder().build());
    assertThat(encryptedSecrets).isNotNull();
    assertThat(encryptedSecrets).isEmpty();

    String secret = "secret";
    String password = "password";
    String clientId = "clientId";
    KubernetesClusterConfig kubernetesClusterConfig = KubernetesClusterConfig.builder()
                                                          .authType(KubernetesClusterAuthType.OIDC)
                                                          .encryptedOidcSecret(secret)
                                                          .encryptedOidcPassword(password)
                                                          .encryptedOidcClientId(clientId)
                                                          .build();
    encryptedSecrets = SettingValueHelper.getAllEncryptedSecrets(kubernetesClusterConfig);
    assertThat(encryptedSecrets).isNotNull();
    assertThat(encryptedSecrets).contains(secret, password, clientId);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetUsageRestrictions() {
    UsageRestrictions usageRestrictions = mock(UsageRestrictions.class);
    SettingAttribute settingAttribute = prepareSettingAttributeWithoutSecrets();
    settingAttribute.setUsageRestrictions(usageRestrictions);
    assertThat(settingServiceHelper.getUsageRestrictions(settingAttribute)).isEqualTo(usageRestrictions);

    settingAttribute = prepareSettingAttributeWithSecrets();
    assertThat(settingServiceHelper.getUsageRestrictions(settingAttribute)).isNull();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testValidateUsageRestrictionsOnEntitySave() {
    UsageRestrictions usageRestrictions = mock(UsageRestrictions.class);
    when(secretManager.hasUpdateAccessToSecrets(any(), eq(ACCOUNT_ID))).thenReturn(true);
    SettingAttribute settingAttribute = prepareSettingAttributeWithoutSecrets();
    settingServiceHelper.validateUsageRestrictionsOnEntitySave(settingAttribute, ACCOUNT_ID, usageRestrictions, false);
    verify(secretManager, never()).hasUpdateAccessToSecrets(any(), eq(ACCOUNT_ID));
    verify(usageRestrictionsService, times(1))
        .validateUsageRestrictionsOnEntitySave(eq(ACCOUNT_ID), any(), eq(usageRestrictions), eq(Boolean.FALSE));

    settingAttribute = prepareSettingAttributeWithSecrets();
    settingServiceHelper.validateUsageRestrictionsOnEntitySave(settingAttribute, ACCOUNT_ID, usageRestrictions, false);
    verify(secretManager, times(1)).hasUpdateAccessToSecrets(any(), eq(ACCOUNT_ID));
    verify(usageRestrictionsService, times(1))
        .validateUsageRestrictionsOnEntitySave(eq(ACCOUNT_ID), any(), eq(usageRestrictions), eq(Boolean.FALSE));

    SettingAttribute settingAttributeFinal = settingAttribute;
    when(secretManager.hasUpdateAccessToSecrets(any(), eq(ACCOUNT_ID))).thenReturn(false);
    assertThatThrownBy(()
                           -> settingServiceHelper.validateUsageRestrictionsOnEntitySave(
                               settingAttributeFinal, ACCOUNT_ID, usageRestrictions, false))
        .isInstanceOf(UnauthorizedUsageRestrictionsException.class);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testValidateUsageRestrictionsOnEntityUpdate() {
    UsageRestrictions usageRestrictions1 = mock(UsageRestrictions.class);
    UsageRestrictions usageRestrictions2 = mock(UsageRestrictions.class);
    when(secretManager.hasUpdateAccessToSecrets(any(), eq(ACCOUNT_ID))).thenReturn(true);
    SettingAttribute settingAttribute = prepareSettingAttributeWithoutSecrets();
    settingServiceHelper.validateUsageRestrictionsOnEntityUpdate(
        settingAttribute, ACCOUNT_ID, usageRestrictions1, usageRestrictions2, false);
    verify(secretManager, never()).hasUpdateAccessToSecrets(any(), eq(ACCOUNT_ID));
    verify(usageRestrictionsService, times(1))
        .validateUsageRestrictionsOnEntityUpdate(
            eq(ACCOUNT_ID), any(), eq(usageRestrictions1), eq(usageRestrictions2), eq(Boolean.FALSE));

    settingAttribute = prepareSettingAttributeWithSecrets();
    settingServiceHelper.validateUsageRestrictionsOnEntityUpdate(
        settingAttribute, ACCOUNT_ID, usageRestrictions1, usageRestrictions2, false);
    verify(secretManager, times(1)).hasUpdateAccessToSecrets(any(), eq(ACCOUNT_ID));
    verify(usageRestrictionsService, times(1))
        .validateUsageRestrictionsOnEntityUpdate(
            eq(ACCOUNT_ID), any(), eq(usageRestrictions1), eq(usageRestrictions2), eq(Boolean.FALSE));

    SettingAttribute settingAttributeFinal = settingAttribute;
    when(secretManager.hasUpdateAccessToSecrets(any(), eq(ACCOUNT_ID))).thenReturn(false);
    assertThatThrownBy(()
                           -> settingServiceHelper.validateUsageRestrictionsOnEntityUpdate(
                               settingAttributeFinal, ACCOUNT_ID, usageRestrictions1, usageRestrictions2, false))
        .isInstanceOf(UnauthorizedUsageRestrictionsException.class);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testUserHasPermissionsToChangeEntity() {
    UsageRestrictions usageRestrictions = mock(UsageRestrictions.class);
    SettingAttribute settingAttribute = prepareSettingAttributeWithoutSecrets();
    settingServiceHelper.userHasPermissionsToChangeEntity(settingAttribute, ACCOUNT_ID, usageRestrictions, false);
    verify(secretManager, never()).hasUpdateAccessToSecrets(any(), eq(ACCOUNT_ID));
    verify(usageRestrictionsService, times(1))
        .userHasPermissionsToChangeEntity(eq(ACCOUNT_ID), any(), eq(usageRestrictions), eq(Boolean.FALSE));

    settingAttribute = prepareSettingAttributeWithSecrets();
    settingServiceHelper.userHasPermissionsToChangeEntity(settingAttribute, ACCOUNT_ID, usageRestrictions, false);
    verify(secretManager, times(1)).hasUpdateAccessToSecrets(any(), eq(ACCOUNT_ID));
    verify(usageRestrictionsService, times(1))
        .userHasPermissionsToChangeEntity(eq(ACCOUNT_ID), any(), eq(usageRestrictions), eq(Boolean.FALSE));
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testUpdateUsageRestrictions() {
    UsageRestrictions usageRestrictions = mock(UsageRestrictions.class);
    SettingAttribute settingAttribute = prepareSettingAttributeWithoutSecrets();
    settingAttribute.setUsageRestrictions(usageRestrictions);
    settingServiceHelper.updateUsageRestrictions(settingAttribute);
    assertThat(settingAttribute.getUsageRestrictions()).isEqualTo(usageRestrictions);

    settingAttribute = prepareSettingAttributeWithPlaceholderSecrets();
    settingServiceHelper.updateUsageRestrictions(settingAttribute);
    assertThat(settingAttribute.getUsageRestrictions()).isNull();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetUsedSecretIds() {
    SettingAttribute settingAttribute = aSettingAttribute().build();
    assertThat(settingServiceHelper.getUsedSecretIds(settingAttribute)).isNullOrEmpty();

    settingAttribute = prepareSettingAttributeWithoutSecrets();
    assertThat(settingServiceHelper.getUsedSecretIds(settingAttribute)).isNullOrEmpty();

    settingAttribute = prepareSettingAttributeWithPlaceholderSecrets();
    assertThat(settingServiceHelper.getUsedSecretIds(settingAttribute)).isNullOrEmpty();

    settingAttribute = prepareSettingAttributeWithSecrets();
    assertThat(settingServiceHelper.getUsedSecretIds(settingAttribute)).containsExactly(PAT);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetAllEncryptedFields() {
    // Encrypted annotated field is used to store plain text value
    PcfConfig pcfConfig = PcfConfig.builder()
                              .useEncryptedUsername(false)
                              .username(RANDOM.toCharArray())
                              .encryptedPassword(RANDOM)
                              .build();

    assertThat(SettingValueHelper.getAllEncryptedFields(pcfConfig).stream().map(Field::getName))
        .containsExactlyInAnyOrder("password");

    // Both fields stores encrypted value
    pcfConfig =
        PcfConfig.builder().useEncryptedUsername(true).encryptedUsername(RANDOM).encryptedPassword(RANDOM).build();

    assertThat(SettingValueHelper.getAllEncryptedFields(pcfConfig).stream().map(Field::getName))
        .containsExactlyInAnyOrder("username", "password");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void setResetTransientFields() {
    GitConfig config = null;
    settingServiceHelper.resetTransientFields(config);

    config = GitConfig.builder().sshSettingAttribute(new SettingAttribute().toDTO()).build();
    settingServiceHelper.resetTransientFields(config);
    assertThat(config.getSshSettingAttribute()).isNull();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testSkipUsageRestrictionsValidation() {
    assertFalse(settingServiceHelper.skipUsageRestrictionsValidation(false, null));

    assertFalse(settingServiceHelper.skipUsageRestrictionsValidation(false, aSettingAttribute().build()));
    SettingValue settingValue = aStringValue().build();
    settingValue.setType(null);
    assertFalse(settingServiceHelper.skipUsageRestrictionsValidation(
        false, aSettingAttribute().withValue(settingValue).build()));
    settingValue = aStringValue().build();
    assertFalse(settingServiceHelper.skipUsageRestrictionsValidation(
        false, aSettingAttribute().withValue(settingValue).build()));
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testSkipUsageRestrictionsValidationIsAccountAdminTrue() {
    assertFalse(settingServiceHelper.skipUsageRestrictionsValidation(true, null));
    assertFalse(settingServiceHelper.skipUsageRestrictionsValidation(true, aSettingAttribute().build()));
    SettingValue settingValue = aStringValue().build();
    settingValue.setType(null);
    assertFalse(settingServiceHelper.skipUsageRestrictionsValidation(
        true, aSettingAttribute().withValue(settingValue).build()));
    settingValue = aStringValue().build();
    assertFalse(settingServiceHelper.skipUsageRestrictionsValidation(
        true, aSettingAttribute().withValue(settingValue).withAppId("appId").build()));
    assertTrue(settingServiceHelper.skipUsageRestrictionsValidation(
        true, aSettingAttribute().withValue(settingValue).withAppId(GLOBAL_APP_ID).build()));

    settingValue = SmtpConfig.builder().build();
    assertFalse(settingServiceHelper.skipUsageRestrictionsValidation(
        true, aSettingAttribute().withValue(settingValue).build()));
  }

  @Test
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testUpdateReferencedSecretsWithSkipValidation() {
    SettingAttribute settingAttribute = aSettingAttribute().withAccountId(ACCOUNT_ID).withCategory(CONNECTOR).build();
    // Early return without exceptions.
    settingServiceHelper.updateReferencedSecrets(settingAttribute);

    String secret = "secret";
    String password = "password";
    String clientId = "clientId";
    KubernetesClusterConfig kubernetesClusterConfig = KubernetesClusterConfig.builder()
                                                          .authType(KubernetesClusterAuthType.OIDC)
                                                          .encryptedOidcSecret(secret)
                                                          .encryptedOidcPassword(password)
                                                          .encryptedOidcClientId(clientId)
                                                          .skipValidation(true)
                                                          .build();
    settingAttribute.setValue(kubernetesClusterConfig);
    settingServiceHelper.updateReferencedSecrets(settingAttribute);
    verify(managerDecryptionService, times(0)).decrypt(eq(kubernetesClusterConfig), any());
    assertThat(kubernetesClusterConfig.isDecrypted()).isFalse();

    kubernetesClusterConfig.setDecrypted(true);
    settingServiceHelper.updateReferencedSecrets(settingAttribute);
    verify(managerDecryptionService, times(0)).decrypt(eq(kubernetesClusterConfig), any());
    assertThat(kubernetesClusterConfig.isDecrypted()).isTrue();
  }

  private SettingAttribute prepareSettingAttributeWithoutSecrets() {
    return aSettingAttribute()
        .withAccountId(ACCOUNT_ID)
        .withValue(DockerConfig.builder().dockerRegistryUrl(DOCKER_REGISTRY_URL).build())
        .build();
  }

  private SettingAttribute prepareSettingAttributeWithSecrets() {
    return aSettingAttribute()
        .withAccountId(ACCOUNT_ID)
        .withValue(DockerConfig.builder().dockerRegistryUrl(DOCKER_REGISTRY_URL).encryptedPassword(PAT).build())
        .build();
  }

  private SettingAttribute prepareSettingAttributeWithPlaceholderSecrets() {
    return aSettingAttribute()
        .withAccountId(ACCOUNT_ID)
        .withValue(DockerConfig.builder()
                       .dockerRegistryUrl(DOCKER_REGISTRY_URL)
                       .encryptedPassword(CGConstants.ENCRYPTED_VALUE_STR)
                       .build())
        .build();
  }
}
