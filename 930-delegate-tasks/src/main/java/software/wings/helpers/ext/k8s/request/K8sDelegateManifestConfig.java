/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.k8s.request;

import static io.harness.annotations.dev.HarnessModule._950_DELEGATE_TASKS_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.helm.HelmCommandFlag;
import io.harness.expression.Expression;
import io.harness.manifest.CustomManifestSource;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.dto.ManifestFile;
import software.wings.helpers.ext.helm.request.HelmChartConfigParams;
import software.wings.helpers.ext.kustomize.KustomizeConfig;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(CDP)
@TargetModule(_950_DELEGATE_TASKS_BEANS)
public class K8sDelegateManifestConfig implements NestedAnnotationResolver {
  StoreType manifestStoreTypes;
  List<ManifestFile> manifestFiles;
  List<EncryptedDataDetail> encryptedDataDetails;

  // Applies to GitFileConfig
  private GitFileConfig gitFileConfig;
  private GitConfig gitConfig;

  // Applies only to HelmRepoConfig
  private HelmChartConfigParams helmChartConfigParams;

  // Applies only to Kustomize
  private KustomizeConfig kustomizeConfig;

  // Applies only to Custom/CustomOpenshiftTemplate
  @Expression(ALLOW_SECRETS) private CustomManifestSource customManifestSource;

  @Expression(ALLOW_SECRETS) private HelmCommandFlag helmCommandFlag;

  private boolean customManifestEnabled;

  private boolean bindValuesAndManifestFetchTask;

  private boolean optimizedFilesFetch;

  private boolean shouldSaveManifest;
  private boolean skipApplyHelmDefaultValues;
  private boolean secretManagerCapabilitiesEnabled;
}
