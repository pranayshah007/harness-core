/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.dto.overview;

import io.leangen.graphql.annotations.GraphQLNonNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CCMMetaData {
  @GraphQLNonNull @Builder.Default Boolean k8sClusterConnectorPresent = false;
  @GraphQLNonNull @Builder.Default Boolean cloudDataPresent = false;
  @GraphQLNonNull @Builder.Default Boolean awsConnectorsPresent = false;
  @GraphQLNonNull @Builder.Default Boolean gcpConnectorsPresent = false;
  @GraphQLNonNull @Builder.Default Boolean azureConnectorsPresent = false;
  @GraphQLNonNull @Builder.Default Boolean applicationDataPresent = false;
  @GraphQLNonNull @Builder.Default Boolean inventoryDataPresent = false;
  @GraphQLNonNull @Builder.Default Boolean clusterDataPresent = false;
  @GraphQLNonNull @Builder.Default Boolean isSampleClusterPresent = false;
  @GraphQLNonNull @Builder.Default Boolean showCostOverview = false;
  String defaultAzurePerspectiveId;
  String defaultAwsPerspectiveId;
  String defaultGcpPerspectiveId;
  String defaultClusterPerspectiveId;
}
