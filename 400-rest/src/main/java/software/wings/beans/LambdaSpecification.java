/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.apache.commons.lang3.StringUtils.trim;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.yaml.BaseYaml;

import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "LambdaSpecificationKeys")
@StoreIn(DbAliases.HARNESS)
@Entity("lambdaSpecifications")
@HarnessEntity(exportable = true)
public class LambdaSpecification extends DeploymentSpecification implements AccountAccess {
  @NotEmpty @FdUniqueIndex private String serviceId;
  private DefaultSpecification defaults;
  @Valid private List<FunctionSpecification> functions;

  public LambdaSpecification cloneInternal() {
    List<FunctionSpecification> clonedFunctions = new ArrayList<>();
    if (isNotEmpty(this.getFunctions())) {
      for (FunctionSpecification functionSpecification : this.getFunctions()) {
        clonedFunctions.add(functionSpecification.cloneInternal());
      }
    }

    DefaultSpecification clonedDefaults = null;
    if (this.getDefaults() != null) {
      clonedDefaults = this.getDefaults().cloneInternal();
    }

    LambdaSpecification specification = LambdaSpecification.builder()
                                            .functions(clonedFunctions)
                                            .defaults(clonedDefaults)
                                            .serviceId(this.serviceId)
                                            .build();
    specification.setAccountId(this.getAccountId());
    specification.setAppId(this.getAppId());

    return specification;
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends DeploymentSpecification.Yaml {
    private DefaultSpecification.Yaml defaults;
    private List<FunctionSpecification.Yaml> functions;

    @Builder
    public Yaml(String type, String harnessApiVersion, DefaultSpecification.Yaml defaults,
        List<FunctionSpecification.Yaml> functions) {
      super(type, harnessApiVersion);
      this.defaults = defaults;
      this.functions = functions;
    }
  }

  @Data
  @Builder
  public static class DefaultSpecification {
    private String runtime;
    private Integer memorySize = 128;
    private Integer timeout = 3;
    public String getRuntime() {
      return trim(runtime);
    }

    public DefaultSpecification cloneInternal() {
      return DefaultSpecification.builder()
          .runtime(this.getRuntime())
          .memorySize(this.getMemorySize())
          .timeout(this.getTimeout())
          .build();
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static final class Yaml extends BaseYaml {
      private String runtime;
      private Integer memorySize = 128;
      private Integer timeout = 3;

      @Builder
      public Yaml(String runtime, Integer memorySize, Integer timeout) {
        this.runtime = runtime;
        this.memorySize = memorySize;
        this.timeout = timeout;
      }
    }
  }

  @Data
  @Builder
  public static class FunctionSpecification {
    private String runtime;
    private Integer memorySize = 128;
    private Integer timeout = 3;
    private String functionName;
    private String handler;

    public String getRuntime() {
      return trim(runtime);
    }
    public String getFunctionName() {
      return trim(functionName);
    }
    public String getHandler() {
      return trim(handler);
    }

    public FunctionSpecification cloneInternal() {
      return FunctionSpecification.builder()
          .runtime(this.getRuntime())
          .memorySize(this.getMemorySize())
          .timeout(this.getTimeout())
          .functionName(this.getFunctionName())
          .handler(this.getHandler())
          .build();
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static final class Yaml extends BaseYaml {
      private String runtime;
      private Integer memorySize = 128;
      private Integer timeout = 3;
      private String functionName;
      private String handler;

      @Builder
      public Yaml(String runtime, Integer memorySize, Integer timeout, String functionName, String handler) {
        this.runtime = runtime;
        this.memorySize = memorySize;
        this.timeout = timeout;
        this.functionName = functionName;
        this.handler = handler;
      }
    }
  }
}
