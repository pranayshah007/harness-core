/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.settings.helm;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ChartMuseumCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.HelmInstallationCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.k8s.model.HelmVersion;

import software.wings.audit.ResourceType;
import software.wings.security.UsageRestrictions;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;
import software.wings.yaml.setting.HelmRepoYaml;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(CDC)
@JsonTypeName("AMAZON_S3_HELM_REPO")
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@TargetModule(HarnessModule._957_CG_BEANS)

public class AmazonS3HelmRepoConfig extends SettingValue implements HelmRepoConfig {
  private static final String AWS_URL = "https://aws.amazon.com/";
  @SchemaIgnore @NotEmpty private String accountId;

  @NotEmpty private String connectorId;
  @NotEmpty private String bucketName;
  private String folderPath;
  @NotEmpty private String region;
  private boolean useLatestChartMuseumVersion;

  public AmazonS3HelmRepoConfig() {
    super(SettingVariableTypes.AMAZON_S3_HELM_REPO.name());
  }

  public AmazonS3HelmRepoConfig(String accountId, String connectorId, String bucketName, String folderPath,
      String region, boolean useLatestChartMuseumVersion) {
    super(SettingVariableTypes.AMAZON_S3_HELM_REPO.name());
    this.accountId = accountId;
    this.connectorId = connectorId;
    this.bucketName = bucketName;
    this.folderPath = folderPath;
    this.region = region;
    this.useLatestChartMuseumVersion = useLatestChartMuseumVersion;
  }

  @Override
  public String fetchResourceCategory() {
    return ResourceType.ARTIFACT_SERVER.name();
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilityList = new ArrayList<>();
    executionCapabilityList.add(HelmInstallationCapability.builder()
                                    .version(HelmVersion.V3)
                                    .criteria("AMAZON_S3_HELM_REPO: " + getBucketName() + ":" + getRegion())
                                    .build());
    executionCapabilityList.add(
        HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(AWS_URL, maskingEvaluator));
    executionCapabilityList.add(
        ChartMuseumCapability.builder().useLatestChartMuseumVersion(this.useLatestChartMuseumVersion).build());
    return executionCapabilityList;
  }

  @Override
  public String getBucketName() {
    return bucketName;
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends HelmRepoYaml {
    private String cloudProvider;
    private String bucket;
    private String region;

    @Builder
    public Yaml(String type, String harnessApiVersion, String cloudProvider, String bucket, String region,
        UsageRestrictions.Yaml usageRestrictions) {
      super(type, harnessApiVersion, usageRestrictions);
      this.cloudProvider = cloudProvider;
      this.bucket = bucket;
      this.region = region;
    }
  }
}
