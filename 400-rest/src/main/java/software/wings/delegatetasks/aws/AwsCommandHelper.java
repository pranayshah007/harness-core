/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.AwsConfig;
import software.wings.beans.command.CommandExecutionContext;
import software.wings.beans.dto.SettingAttribute;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.settings.SettingValue;

import com.google.inject.Singleton;
import java.util.List;

@Singleton
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AwsCommandHelper {
  public List<String> getAwsConfigTagsFromContext(CommandExecutionContext context) {
    SettingAttribute cloudProviderSetting = context.getCloudProviderSetting();
    if (cloudProviderSetting != null) {
      SettingValue settingValue = cloudProviderSetting.getValue();
      if (settingValue instanceof AwsConfig) {
        return nonEmptyTag((AwsConfig) settingValue);
      }
    }
    return emptyList();
  }

  public List<String> getAwsConfigTagsFromK8sConfig(K8sTaskParameters request) {
    if (request == null) {
      return emptyList();
    }
    K8sClusterConfig k8sClusterConfig = request.getK8sClusterConfig();
    if (k8sClusterConfig == null) {
      return emptyList();
    }
    SettingValue settingValue = k8sClusterConfig.getCloudProvider();
    if (settingValue instanceof AwsConfig) {
      return nonEmptyTag((AwsConfig) settingValue);
    }
    return emptyList();
  }

  public List<String> nonEmptyTag(AwsConfig awsConfig) {
    String tag = awsConfig.getTag();
    return isNotEmpty(tag) ? singletonList(tag) : null;
  }
}
