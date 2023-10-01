/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.customsecretmanager;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.validator.NGRegexValidatorConstants;

import software.wings.beans.NameValuePairWithDefault;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("delegate.beans.connector.customsecretmanager.TemplateLinkConfig")
@OwnedBy(PL)
@RecasterAlias("io.harness.delegate.beans.connector.customsecretmanager.TemplateLinkConfigForCustomSecretManager")
public class TemplateLinkConfigForCustomSecretManager {
  @NotNull String templateRef;
  @NotNull @Pattern(regexp = NGRegexValidatorConstants.VERSION_LABEL_PATTERN) String versionLabel;
  Map<String, List<NameValuePairWithDefault>> templateInputs;
}
