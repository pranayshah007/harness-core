/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.k8s.KubernetesHelperService.getKubernetesConfigFromDefaultKubeConfigFile;
import static io.harness.k8s.KubernetesHelperService.getKubernetesConfigFromServiceAccount;
import static io.harness.k8s.KubernetesHelperService.isRunningInCluster;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.config.CCMConfig;
import io.harness.ccm.config.CloudCostAware;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.encryption.Encrypted;
import io.harness.exception.UnexpectedException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.helper.SettingValueHelper;
import io.harness.k8s.model.KubernetesClusterAuthType;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesConfig.KubernetesConfigBuilder;
import io.harness.k8s.model.OidcGrantType;

import software.wings.annotation.EncryptableSetting;
import software.wings.audit.ResourceType;
import software.wings.jersey.JsonViews;
import software.wings.security.UsageRestrictions;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;
import software.wings.yaml.setting.CloudProviderYaml;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.reinert.jjschema.SchemaIgnore;
import dev.morphia.annotations.Transient;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;

@JsonTypeName("KUBERNETES_CLUSTER")
@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "KubernetesClusterConfigKeys")
@Data
@ToString(exclude = {"password", "caCert", "clientCert", "clientKey", "clientKeyPassphrase", "serviceAccountToken",
              "oidcClientId", "oidcSecret", "oidcPassword"})
@EqualsAndHashCode(callSuper = true)
@TargetModule(_957_CG_BEANS)
public class KubernetesClusterConfig extends SettingValue implements EncryptableSetting, CloudCostAware {
  private boolean useKubernetesDelegate;
  private String delegateName;
  private Set<String> delegateSelectors;
  private String masterUrl;
  @Encrypted(fieldName = "username", isReference = true) private char[] username;
  @Encrypted(fieldName = "password") private char[] password;
  @Encrypted(fieldName = "ca_certificate") private char[] caCert;
  @Encrypted(fieldName = "client_certificate") private char[] clientCert;
  @Encrypted(fieldName = "client_key") private char[] clientKey;
  @Encrypted(fieldName = "client_key_passphrase") private char[] clientKeyPassphrase;
  @Encrypted(fieldName = "service_account_token") private char[] serviceAccountToken;
  private String clientKeyAlgo;
  private boolean skipValidation;

  private boolean useEncryptedUsername;
  @JsonView(JsonViews.Internal.class) private String encryptedUsername;
  @JsonView(JsonViews.Internal.class) private String encryptedPassword;
  @JsonView(JsonViews.Internal.class) private String encryptedCaCert;
  @JsonView(JsonViews.Internal.class) private String encryptedClientCert;
  @JsonView(JsonViews.Internal.class) private String encryptedClientKey;
  @JsonView(JsonViews.Internal.class) private String encryptedClientKeyPassphrase;
  @JsonView(JsonViews.Internal.class) private String encryptedServiceAccountToken;

  @JsonInclude(Include.NON_NULL) private KubernetesClusterAuthType authType;

  // -- OIDC AUTH fields.
  private String oidcIdentityProviderUrl;
  private String oidcUsername;
  private OidcGrantType oidcGrantType;
  private String oidcScopes;
  @Encrypted(fieldName = "oidc_client_id") private char[] oidcClientId;
  @Encrypted(fieldName = "oidc_secret") private char[] oidcSecret;
  @Encrypted(fieldName = "oidc_password") private char[] oidcPassword;
  @JsonView(JsonViews.Internal.class) private String encryptedOidcSecret;
  @JsonView(JsonViews.Internal.class) private String encryptedOidcPassword;
  @JsonView(JsonViews.Internal.class) private String encryptedOidcClientId;
  // -- END OIDC AUTH fields.

  @NotEmpty private String accountId;

  @JsonInclude(Include.NON_NULL) @SchemaIgnore private CCMConfig ccmConfig;

  @Transient private boolean decrypted;

  public KubernetesClusterConfig() {
    super(SettingVariableTypes.KUBERNETES_CLUSTER.name());
  }

  @Builder
  public KubernetesClusterConfig(boolean useKubernetesDelegate, String delegateName, Set<String> delegateSelectors,
      String masterUrl, char[] username, char[] password, char[] caCert, char[] clientCert, char[] clientKey,
      char[] clientKeyPassphrase, char[] serviceAccountToken, String clientKeyAlgo, boolean skipValidation,
      boolean useEncryptedUsername, String encryptedUsername, String encryptedPassword, String encryptedCaCert,
      String encryptedClientCert, String encryptedClientKey, String encryptedClientKeyPassphrase,
      String encryptedServiceAccountToken, String accountId, CCMConfig ccmConfig, boolean decrypted,
      KubernetesClusterAuthType authType, char[] oidcClientId, char[] oidcSecret, String oidcIdentityProviderUrl,
      String oidcUsername, char[] oidcPassword, String oidcScopes, String encryptedOidcSecret,
      String encryptedOidcPassword, String encryptedOidcClientId, OidcGrantType oidcGrantType) {
    this();
    this.useKubernetesDelegate = useKubernetesDelegate;
    this.delegateName = delegateName;
    this.delegateSelectors = delegateSelectors;
    this.masterUrl = masterUrl;
    this.username = username == null ? null : username.clone();
    this.password = password == null ? null : password.clone();
    this.caCert = caCert == null ? null : caCert.clone();
    this.clientCert = clientCert == null ? null : clientCert.clone();
    this.clientKey = clientKey == null ? null : clientKey.clone();
    this.clientKeyPassphrase = clientKeyPassphrase == null ? null : clientKeyPassphrase.clone();
    this.serviceAccountToken = serviceAccountToken == null ? null : serviceAccountToken.clone();
    this.clientKeyAlgo = clientKeyAlgo;
    this.skipValidation = skipValidation;
    this.useEncryptedUsername = useEncryptedUsername;
    this.encryptedUsername = encryptedUsername;
    this.encryptedPassword = encryptedPassword;
    this.encryptedCaCert = encryptedCaCert;
    this.encryptedClientCert = encryptedClientCert;
    this.encryptedClientKey = encryptedClientKey;
    this.encryptedClientKeyPassphrase = encryptedClientKeyPassphrase;
    this.encryptedServiceAccountToken = encryptedServiceAccountToken;
    this.accountId = accountId;
    this.ccmConfig = ccmConfig;
    this.decrypted = decrypted;
    this.authType = authType;
    this.oidcClientId = oidcClientId == null ? null : oidcClientId.clone();
    this.oidcSecret = oidcSecret == null ? null : oidcSecret.clone();
    this.oidcIdentityProviderUrl = oidcIdentityProviderUrl;
    this.oidcUsername = oidcUsername;
    this.oidcPassword = oidcPassword == null ? null : oidcPassword.clone();
    this.oidcScopes = oidcScopes;
    this.encryptedOidcClientId = encryptedOidcClientId;
    this.encryptedOidcPassword = encryptedOidcPassword;
    this.encryptedOidcSecret = encryptedOidcSecret;
    this.oidcGrantType = oidcGrantType;
  }

  @Override
  @JsonIgnore
  @SchemaIgnore
  public boolean isDecrypted() {
    return decrypted || !EmptyPredicate.isEmpty(delegateSelectors);
  }

  @Override
  public String fetchResourceCategory() {
    return ResourceType.CLOUD_PROVIDER.name();
  }

  @Override
  public List<String> fetchRelevantEncryptedSecrets() {
    if (useKubernetesDelegate) {
      return Collections.emptyList();
    }

    if (authType == null) {
      return SettingValueHelper.getAllEncryptedSecrets(this);
    }

    switch (authType) {
      case NONE:
        return SettingValueHelper.getAllEncryptedSecrets(this);
      case OIDC:
        return Arrays.asList(encryptedOidcSecret, encryptedOidcClientId, encryptedOidcPassword);
      case SERVICE_ACCOUNT:
        return Arrays.asList(encryptedServiceAccountToken, encryptedCaCert);
      case CLIENT_KEY_CERT:
        return Arrays.asList(encryptedCaCert, encryptedClientCert, encryptedClientKey, encryptedClientKeyPassphrase);
      case USER_PASSWORD:
        return useEncryptedUsername ? Arrays.asList(encryptedUsername, encryptedPassword)
                                    : Collections.singletonList(encryptedPassword);
      default:
        throw new UnexpectedException("Undefined auth type: " + authType);
    }
  }

  public KubernetesConfig createKubernetesConfig(String namespace) {
    String namespaceNotBlank = isNotBlank(namespace) ? namespace : "default";

    if (isUseKubernetesDelegate()) {
      if (isRunningInCluster()) {
        return getKubernetesConfigFromServiceAccount(namespaceNotBlank);
      } else {
        return getKubernetesConfigFromDefaultKubeConfigFile(namespaceNotBlank);
      }
    }

    KubernetesConfigBuilder kubernetesConfig = KubernetesConfig.builder()
                                                   .accountId(getAccountId())
                                                   .masterUrl(masterUrl)
                                                   .clientKeyAlgo(clientKeyAlgo)
                                                   .namespace(namespaceNotBlank);

    // Set fields needed by OIDC Auth Type
    if (KubernetesClusterAuthType.OIDC == authType) {
      return initWithOidcAuthDetails(kubernetesConfig);
    }

    if (EmptyPredicate.isNotEmpty(username)) {
      kubernetesConfig.username(username);
    }

    if (EmptyPredicate.isNotEmpty(password)) {
      kubernetesConfig.password(password);
    }

    if (EmptyPredicate.isNotEmpty(caCert)) {
      kubernetesConfig.caCert(caCert);
    }

    if (EmptyPredicate.isNotEmpty(clientCert)) {
      kubernetesConfig.clientCert(clientCert);
    }
    if (EmptyPredicate.isNotEmpty(clientKey)) {
      kubernetesConfig.clientKey(clientKey);
    }

    if (EmptyPredicate.isNotEmpty(clientKeyPassphrase)) {
      kubernetesConfig.clientKeyPassphrase(clientKeyPassphrase);
    }

    if (EmptyPredicate.isNotEmpty(serviceAccountToken)) {
      kubernetesConfig.serviceAccountTokenSupplier(() -> new String(serviceAccountToken));
    }

    return kubernetesConfig.build();
  }

  private KubernetesConfig initWithOidcAuthDetails(KubernetesConfigBuilder kubernetesConfig) {
    kubernetesConfig.oidcClientId(oidcClientId);
    kubernetesConfig.oidcSecret(oidcSecret);
    kubernetesConfig.oidcUsername(oidcUsername);
    kubernetesConfig.oidcPassword(oidcPassword);
    kubernetesConfig.oidcGrantType(oidcGrantType);
    kubernetesConfig.oidcIdentityProviderUrl(oidcIdentityProviderUrl);
    kubernetesConfig.authType(authType);
    kubernetesConfig.oidcScopes(oidcScopes);

    return kubernetesConfig.build();
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    if (useKubernetesDelegate) {
      if (!EmptyPredicate.isEmpty(delegateSelectors)) {
        return singletonList(SelectorCapability.builder().selectors(delegateSelectors).build());
      }
      return emptyList();
    }

    return Collections.singletonList(
        HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(masterUrl, maskingEvaluator));
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = false)
  public static class Yaml extends CloudProviderYaml {
    private boolean useKubernetesDelegate;
    private String delegateName;
    private List<String> delegateSelectors;
    private String masterUrl;
    private String username;
    private String usernameSecretId;
    private String password;
    private String caCert;
    private String clientCert;
    private String clientKey;
    private String clientKeyPassphrase;
    private String serviceAccountToken;
    private String clientKeyAlgo;
    private boolean skipValidation;
    private KubernetesClusterAuthType authType;
    private String oidcIdentityProviderUrl;
    private String oidcUsername;
    private OidcGrantType oidcGrantType;
    private String oidcScopes;
    private String oidcSecret;
    private String oidcPassword;
    private String oidcClientId;
    private CCMConfig.Yaml continuousEfficiencyConfig;

    @lombok.Builder
    public Yaml(boolean useKubernetesDelegate, String delegateName, List<String> delegateSelectors, String type,
        String harnessApiVersion, String masterUrl, String username, String usernameSecretId, String password,
        String caCert, String clientCert, String clientKey, String clientKeyPassphrase, String serviceAccountToken,
        String clientKeyAlgo, boolean skipValidation, UsageRestrictions.Yaml usageRestrictions,
        CCMConfig.Yaml ccmConfig, KubernetesClusterAuthType authType, String oidcIdentityProviderUrl,
        String oidcUsername, OidcGrantType oidcGrantType, String oidcScopes, String oidcSecret, String oidcPassword,
        String oidcClientId) {
      super(type, harnessApiVersion, usageRestrictions);
      this.useKubernetesDelegate = useKubernetesDelegate;
      this.delegateName = delegateName;
      this.delegateSelectors = delegateSelectors;
      this.masterUrl = masterUrl;
      this.username = username;
      this.usernameSecretId = usernameSecretId;
      this.password = password;
      this.caCert = caCert;
      this.clientCert = clientCert;
      this.clientKey = clientKey;
      this.clientKeyPassphrase = clientKeyPassphrase;
      this.serviceAccountToken = serviceAccountToken;
      this.clientKeyAlgo = clientKeyAlgo;
      this.skipValidation = skipValidation;
      this.continuousEfficiencyConfig = ccmConfig;
      this.authType = authType;
      this.oidcIdentityProviderUrl = oidcIdentityProviderUrl;
      this.oidcUsername = oidcUsername;
      this.oidcPassword = oidcPassword;
      this.oidcClientId = oidcClientId;
      this.oidcGrantType = oidcGrantType;
      this.oidcSecret = oidcSecret;
    }
  }
}
