/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.container;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;

import software.wings.beans.DeploymentSpecification;

import javax.validation.constraints.NotNull;
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
@FieldNameConstants(innerTypeName = "HelmChartSpecificationKeys")
@StoreIn(DbAliases.HARNESS)
@Entity("helmChartSpecifications")
@HarnessEntity(exportable = true)
@OwnedBy(CDP)
@TargetModule(HarnessModule._957_CG_BEANS)
public class HelmChartSpecification extends DeploymentSpecification implements AccountAccess {
  @NotEmpty @FdUniqueIndex private String serviceId;
  @NotNull private String chartUrl;
  @NotNull private String chartName;
  @NotNull private String chartVersion;

  public HelmChartSpecification cloneInternal() {
    HelmChartSpecification specification = HelmChartSpecification.builder()
                                               .chartName(this.chartName)
                                               .chartUrl(this.getChartUrl())
                                               .chartVersion(this.getChartVersion())
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
    private String chartUrl;
    private String chartName;
    private String chartVersion;

    @Builder
    public Yaml(String type, String harnessApiVersion, String chartUrl, String chartName, String chartVersion) {
      super(type, harnessApiVersion);
      this.chartUrl = chartUrl;
      this.chartName = chartName;
      this.chartVersion = chartVersion;
    }
  }
}
