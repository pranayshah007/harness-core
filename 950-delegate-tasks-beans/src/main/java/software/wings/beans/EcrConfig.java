/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.encryption.Encrypted;
import io.harness.expression.ExpressionEvaluator;

import software.wings.annotation.EncryptableSetting;
import software.wings.audit.ResourceType;
import software.wings.jersey.JsonViews;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.Arrays;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * ECR Artifact Server / Connector has been deprecated.
 * Instead, we use AWS cloud provider to fetch all the connection details.
 * This class is not deleted since there might be existing configs in the mongo db.
 * We can only delete this class when the entries are migrated to use cloud provider.
 * Created by brett on 7/16/17
 */
@OwnedBy(CDC)
@JsonTypeName("ECR")
@Data
@Builder
@ToString(exclude = "secretKey")
@EqualsAndHashCode(callSuper = false)
@Deprecated
public class EcrConfig extends SettingValue implements EncryptableSetting {
  @Attributes(title = "Amazon ECR Registry URL", required = true) @NotEmpty private String ecrUrl;
  @Attributes(title = "Access Key", required = true) @NotEmpty private String accessKey;
  @Attributes(title = "Secret Key", required = true) @Encrypted(fieldName = "secret_key") private char[] secretKey;
  @Attributes(title = "Region", required = true) private String region;
  @SchemaIgnore @NotEmpty private String accountId;

  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedSecretKey;

  /**
   * Instantiates a new ECR registry config.
   */
  public EcrConfig() {
    super(SettingVariableTypes.ECR.name());
  }

  public EcrConfig(
      String ecrUrl, String accessKey, char[] secretKey, String region, String accountId, String encryptedSecretKey) {
    this();
    this.ecrUrl = ecrUrl;
    this.accessKey = accessKey;
    this.secretKey = secretKey == null ? null : secretKey.clone();
    this.region = region;
    this.accountId = accountId;
    this.encryptedSecretKey = encryptedSecretKey;
  }

  @Override
  public String fetchResourceCategory() {
    return ResourceType.ARTIFACT_SERVER.name();
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return Arrays.asList(
        HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(ecrUrl, maskingEvaluator));
  }
}
