/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.changeSourceSpec;

import static io.harness.cvng.CVConstants.DATA_SOURCE_TYPE;

import io.harness.cvng.beans.change.ChangeCategory;
import io.harness.cvng.beans.change.ChangeSourceType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.ws.rs.BadRequestException;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomChangeSourceSpec extends ChangeSourceSpec {
  String name;
  @JsonProperty(DATA_SOURCE_TYPE) ChangeCategory type;
  String webhookUrl;
  String webhookCurlCommand;
  String authorizationToken;

  @Override
  public ChangeSourceType getType() {
    switch (this.type) {
      case DEPLOYMENT:
        return ChangeSourceType.CUSTOM_DEPLOY;
      case INFRASTRUCTURE:
        return ChangeSourceType.CUSTOM_INFRA;
      case ALERTS:
        return ChangeSourceType.CUSTOM_INCIDENT;
      case FEATURE_FLAG:
        return ChangeSourceType.CUSTOM_FF;
      default:
        throw new BadRequestException("Unknown Category for custom change source");
    }
  }

  @Override
  public boolean connectorPresent() {
    return false;
  }
}
