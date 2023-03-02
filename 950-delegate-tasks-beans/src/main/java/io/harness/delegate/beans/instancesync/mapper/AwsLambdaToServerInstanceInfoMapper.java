/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.instancesync.mapper;

import static java.util.stream.Collectors.toSet;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.AwsLambdaServerInstanceInfo;
import io.harness.delegate.task.aws.lambda.AwsLambda;
import io.harness.delegate.task.aws.lambda.AwsLambda.AwsLambdaBuilder;
import io.harness.delegate.task.aws.lambda.AwsLambdaFunctionWithActiveVersions;

import software.wings.beans.Tag;

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class AwsLambdaToServerInstanceInfoMapper {
  public List<ServerInstanceInfo> toServerInstanceInfoList(
      AwsLambdaFunctionWithActiveVersions awsLambdaFunctionWithActiveVersions, String region,
      String infraStructureKey) {
    List<ServerInstanceInfo> serverInstanceInfoList = new ArrayList<>();

    if (CollectionUtils.isNotEmpty(awsLambdaFunctionWithActiveVersions.getVersions())) {
      serverInstanceInfoList =
          awsLambdaFunctionWithActiveVersions.getVersions()
              .stream()
              .map(version
                  -> toServerInstanceInfo(
                      convertToAwsLambda(awsLambdaFunctionWithActiveVersions, version), region, infraStructureKey))
              .collect(Collectors.toList());
    }
    return serverInstanceInfoList;
  }

  public ServerInstanceInfo toServerInstanceInfo(AwsLambda awsLambdaFunction, String region, String infraStructureKey) {
    return AwsLambdaServerInstanceInfo.builder()
        .functionName(awsLambdaFunction.getFunctionName())
        .region(region)
        .version(awsLambdaFunction.getVersion())
        .tags(MapUtils.emptyIfNull(awsLambdaFunction.getTags())
                  .entrySet()
                  .stream()
                  .map(entry -> Tag.builder().key(entry.getKey()).value(entry.getValue()).build())
                  .collect(toSet()))
        .aliases(ImmutableSet.copyOf(emptyIfNull(awsLambdaFunction.getAliases())))
        .runtime(awsLambdaFunction.getRuntime())
        .handler(awsLambdaFunction.getHandler())
        .infrastructureKey(infraStructureKey)
        .memorySize(awsLambdaFunction.getMemorySize())
        .updatedTime(awsLambdaFunction.getLastModified())
        .description(awsLambdaFunction.getDescription())
        .build();
  }

  public static AwsLambda convertToAwsLambda(AwsLambdaFunctionWithActiveVersions activeVersions, String version) {
    final AwsLambdaBuilder builder = AwsLambda.builder()
                                         .functionArn(activeVersions.getFunctionArn())
                                         .functionName(activeVersions.getFunctionName())
                                         .runtime(activeVersions.getRuntime())
                                         .handler(activeVersions.getHandler())
                                         .description(activeVersions.getDescription())
                                         .memorySize(activeVersions.getMemorySize())
                                         .version(version)
                                         .lastModified(activeVersions.getLastModified())
                                         .aliases(activeVersions.getAliases())
                                         .tags(activeVersions.getTags());

    return builder.build();
  }
}
