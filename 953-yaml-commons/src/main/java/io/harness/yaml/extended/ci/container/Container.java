/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.extended.ci.container;

import io.harness.beans.WithIdentifier;

import java.beans.ConstructorProperties;
import java.util.Optional;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@TypeAlias("container")
public class Container implements WithIdentifier {
  public static final int MEM_RESERVE_DEFAULT = 9000;
  public static final int MEM_LIMIT_DEFAULT = 9000;
  public static final int CPU_MILLI_RESERVE_DEFAULT = 2000;
  public static final int CPU_MILLI_LIMIT_DEFAULT = 2000;

  @NotNull private String identifier;
  @NotNull private String connector;
  @NotNull private String imagePath;
  @NotNull Resources resources;

  @Builder
  @ConstructorProperties({"identifier", "connector", "imagePath", "resources"})
  public Container(String identifier, String connector, String imagePath, Resources resources) {
    this.identifier = identifier;
    this.connector = connector;
    this.imagePath = imagePath;
    this.resources = Optional.ofNullable(resources).orElse(Resources.builder().build());
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @TypeAlias("container_resources")
  public static class Resources {
    @Builder.Default Limit limit = Limit.builder().build();
    @Builder.Default Reserve reserve = Reserve.builder().build();
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @TypeAlias("container_limit")
  public static class Limit {
    @Builder.Default @Min(0) private int memory = MEM_LIMIT_DEFAULT;
    @Builder.Default @Min(0) private int cpu = CPU_MILLI_LIMIT_DEFAULT;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @TypeAlias("container_reserve")
  public static class Reserve {
    @Builder.Default @Min(0) private int memory = MEM_RESERVE_DEFAULT;
    @Builder.Default @Min(0) private int cpu = CPU_MILLI_RESERVE_DEFAULT;
  }
}
