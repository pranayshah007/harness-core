/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.loginSettings.events;

import io.harness.gitsync.beans.YamlDTO;

import software.wings.beans.loginSettings.LoginSettings;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@TypeAlias("loginSettings")
public class LoginSettingsYamlDTO implements YamlDTO {
  @JsonProperty("loginSettings") LoginSettings loginSettings;
}
