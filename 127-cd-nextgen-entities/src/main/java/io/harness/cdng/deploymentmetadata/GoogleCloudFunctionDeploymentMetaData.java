/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.deploymentmetadata;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.visitor.helpers.deploymentmetadata.GoogleCloudFunctionDeploymentMetaDataVisitorHelper;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@JsonTypeName(ServiceSpecType.GOOGLE_CLOUD_FUNCTIONS)
@SimpleVisitorHelper(helperClass = GoogleCloudFunctionDeploymentMetaDataVisitorHelper.class)
@TypeAlias("googleCloudFunctionDeploymentMetaData")
@RecasterAlias("io.harness.cdng.deploymentmetadata.GoogleCloudFunctionDeploymentMetaData")
public class GoogleCloudFunctionDeploymentMetaData implements DeploymentMetaData, Visitable {
  String environmentType;
}
