/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.container;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ng.DbAliases;

import software.wings.beans.DeploymentSpecification;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;

@OwnedBy(CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "EcsServiceSpecificationKeys")
@StoreIn(DbAliases.HARNESS)
@Entity("ecsServiceSpecification")
@HarnessEntity(exportable = true)
@TargetModule(HarnessModule._955_DELEGATE_BEANS)
public class EcsServiceSpecification extends DeploymentSpecification {
  public static final String ECS_REPLICA_SCHEDULING_STRATEGY = "REPLICA";
  @NotNull private String serviceId;
  private String serviceSpecJson;
  private String schedulingStrategy;

  public static final String preamble = "# Enter your Service JSON spec below.\n"
      + "# ---\n\n";

  public static final String manifestTemplate = "{\n\"capacityProviderStrategy\":[ ],\n"
      + "\"placementConstraints\":[ ],\n"
      + "\"placementStrategy\":[ ],\n"
      + "\"healthCheckGracePeriodSeconds\":null,\n"
      + "\"tags\":[ ],\n"
      + "\"schedulingStrategy\":\"REPLICA\"\n}";

  public void resetToDefaultSpecification() {
    this.serviceSpecJson = manifestTemplate;
  }

  public EcsServiceSpecification cloneInternal() {
    EcsServiceSpecification specification = EcsServiceSpecification.builder()
                                                .serviceId(this.serviceId)
                                                .serviceSpecJson(serviceSpecJson)
                                                .schedulingStrategy(schedulingStrategy)
                                                .build();
    specification.setAccountId(this.getAccountId());
    specification.setAppId(this.getAppId());
    return specification;
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends DeploymentSpecification.Yaml {
    private String serviceSpecJson;
    private String schedulingStrategy = ECS_REPLICA_SCHEDULING_STRATEGY;

    @Builder
    public Yaml(String type, String harnessApiVersion, String serviceName, String manifestYaml, String serviceSpecJson,
        String schedulingStrategy) {
      super(type, harnessApiVersion);
      this.schedulingStrategy = schedulingStrategy;
      this.serviceSpecJson = serviceSpecJson;
    }
  }
}
