/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.Encrypted;
import io.harness.encryption.EncryptionReflectUtils;
import io.harness.reflection.util.ReflectionUtils;

import software.wings.annotation.EncryptableSetting;
import software.wings.jersey.JsonViews;
import software.wings.settings.SettingVariableTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;

@JsonTypeName("PCF")
@Data
@Builder
@ToString(exclude = "password")
@EqualsAndHashCode(callSuper = false)
@OwnedBy(CDP)
public class CfInternalConfig implements EncryptableSetting {
  @Attributes(title = "Endpoint URL", required = true) @NotEmpty private String endpointUrl;
  @Attributes(title = "Username", required = true)
  @Encrypted(fieldName = "username", isReference = true)
  private char[] username;
  @Attributes(title = "Password", required = true) @Encrypted(fieldName = "password") private char[] password;
  @SchemaIgnore @NotEmpty private String accountId;

  @Attributes(title = "Use Encrypted Username") private boolean useEncryptedUsername;
  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedUsername;
  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedPassword;
  private boolean skipValidation;

  @Override
  public SettingVariableTypes getSettingType() {
    return SettingVariableTypes.PCF;
  }

  @Override
  @JsonIgnore
  @SchemaIgnore
  public List<Field> getEncryptedFields() {
    return EncryptionReflectUtils.getEncryptedFields(this.getClass())
        .stream()
        .filter(field -> {
          if (EncryptionReflectUtils.isSecretReference(field)) {
            String flagFiledName = "useEncrypted" + StringUtils.capitalize(field.getName());

            List<Field> declaredAndInheritedFields =
                ReflectionUtils.getDeclaredAndInheritedFields(this.getClass(), f -> f.getName().equals(flagFiledName));
            if (isNotEmpty(declaredAndInheritedFields)) {
              Object flagFieldValue = ReflectionUtils.getFieldValue(this, declaredAndInheritedFields.get(0));
              return flagFieldValue != null && (Boolean) flagFieldValue;
            }
          }

          return true;
        })
        .collect(Collectors.toList());
  }
}
