/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.extended.ci.codebase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.LinkedList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Build {
  @NotNull private BuildType type;
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
  @NotNull
  private BuildSpec spec;

  @Builder
  public Build(BuildType type, BuildSpec spec) {
    this.type = type;
    this.spec = spec;
  }

  public static List<String> getExpressionsAvailable() {
    List<String> list = new LinkedList<>();
    list.add("build.type");
    list.add("build.spec.branch");
    list.add("build.spec.tag");
    list.add("build.spec.number");
    return list;
  }
}
