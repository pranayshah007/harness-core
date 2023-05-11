/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.audit.ResourceType.CONNECTION_ATTRIBUTES;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.winrm.AuthenticationScheme;
import io.harness.encryption.Encrypted;
import io.harness.expression.ExpressionEvaluator;
import io.harness.ng.core.dto.secrets.WinRmCommandParameter;

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
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.validator.constraints.NotEmpty;

@JsonTypeName("WINRM_CONNECTION_ATTRIBUTES")
@Data
@Builder
@ToString(exclude = "password")
@EqualsAndHashCode(callSuper = false)
@OwnedBy(CDP)
@TargetModule(_957_CG_BEANS)
public class WinRmConnectionAttributes extends SettingValue implements EncryptableSetting {
  @Attributes(required = true) @NotNull private AuthenticationScheme authenticationScheme;
  private String domain;
  @Attributes(required = true) @NotEmpty private String username;
  @Attributes(required = true) @Encrypted(fieldName = "password") private char[] password;
  @Attributes(required = true) private boolean useSSL;
  @Attributes(required = true) private int port;
  @Attributes(required = true) private boolean skipCertChecks;
  @Attributes(required = true) private boolean useKeyTab;
  @Attributes private String keyTabFilePath;
  @Attributes private boolean useNoProfile;
  @SchemaIgnore @NotEmpty private String accountId;

  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedPassword;
  @Attributes private List<WinRmCommandParameter> parameters;

  // Always called inside other ExecutionCapabilityDemander Check ShellScriptParameters and
  // ConnectivityValidationDelegateRequest
  // Returning empty list here
  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return new ArrayList<>();
  }

  public WinRmConnectionAttributes() {
    super(SettingVariableTypes.WINRM_CONNECTION_ATTRIBUTES.name());
  }

  public WinRmConnectionAttributes(AuthenticationScheme authenticationScheme, String domain, String username,
      char[] password, boolean useSSL, int port, boolean skipCertChecks, boolean useKeyTab, String keyTabFilePath,
      boolean useNoProfile, String accountId, String encryptedPassword, List<WinRmCommandParameter> parameters) {
    super(SettingVariableTypes.WINRM_CONNECTION_ATTRIBUTES.name());
    this.authenticationScheme = authenticationScheme;
    this.domain = domain;
    this.username = username;
    this.password = password == null ? null : password.clone();
    this.useSSL = useSSL;
    this.port = port;
    this.skipCertChecks = skipCertChecks;
    this.accountId = accountId;
    this.encryptedPassword = encryptedPassword;
    this.useKeyTab = useKeyTab;
    this.keyTabFilePath = keyTabFilePath;
    this.useNoProfile = useNoProfile;
    this.parameters = parameters;
  }

  @Override
  public String fetchResourceCategory() {
    return CONNECTION_ATTRIBUTES.name();
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends SettingValue.Yaml {
    private AuthenticationScheme authenticationScheme;
    private String domain;
    private String userName;
    private String password;
    private boolean useSSL;
    private int port;
    private boolean skipCertChecks;
    private List<WinRmCommandParameter> parameters;

    @lombok.Builder
    public Yaml(String type, String harnessApiVersion, AuthenticationScheme authenticationScheme, String domain,
        String userName, String password, boolean useSSL, int port, boolean skipCertChecks,
        UsageRestrictions.Yaml usageRestrictions, List<WinRmCommandParameter> parameters) {
      super(type, harnessApiVersion, usageRestrictions);
      this.authenticationScheme = authenticationScheme;
      this.domain = domain;
      this.userName = userName;
      this.password = password;
      this.useSSL = useSSL;
      this.port = port;
      this.skipCertChecks = skipCertChecks;
      this.parameters = parameters;
    }
  }
}
