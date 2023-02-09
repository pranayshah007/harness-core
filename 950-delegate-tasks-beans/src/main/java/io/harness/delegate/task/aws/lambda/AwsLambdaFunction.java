/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.lambda;

import com.amazonaws.services.lambda.model.AliasConfiguration;
import com.amazonaws.services.lambda.model.FunctionConfiguration;
import com.amazonaws.services.lambda.model.GetFunctionResult;
import com.amazonaws.services.lambda.model.ListAliasesResult;
import com.google.common.collect.ImmutableMap;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.logging.log4j.util.Strings;
import software.wings.service.impl.aws.model.embed.AwsLambdaDetails.AwsLambdaDetailsBuilder;

import javax.annotation.Nonnull;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;

@Data
@Builder
@Slf4j
@OwnedBy(CDP)
public class AwsLambdaFunction {
  private String functionName;
  private String functionArn;
  private String runtime;
  private String role;
  private String handler;
  private Long codeSize;
  private String description;
  private Integer timeout;
  private Integer memorySize;
  private Date lastModified;
  private String codeSha256;
  private String version;
  private String kMSKeyArn;
  private String masterArn;
  private String revisionId;
  private Map<String, String> tags;
  private List<String> aliases;

  public static AwsLambdaFunction from(GetFunctionResult result, ListAliasesResult listAliasesResult) {
    final FunctionConfiguration config = result.getConfiguration();
    final AwsLambdaFunction.AwsLambdaFunctionBuilder builder = AwsLambdaFunction.builder()
            .functionArn(config.getFunctionArn())
            .functionName(config.getFunctionName())
            .runtime(config.getRuntime())
            .role(config.getRole())
            .handler(config.getHandler())
            .codeSize(config.getCodeSize())
            .description(config.getDescription())
            .timeout(config.getTimeout())
            .memorySize(config.getMemorySize())
            .codeSha256(config.getCodeSha256())
            .version(config.getVersion())
            .kMSKeyArn(config.getKMSKeyArn())
            .masterArn(config.getMasterArn())
            .revisionId(config.getRevisionId());

    if (Strings.isNotEmpty(config.getLastModified())) {
      try {
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

        builder.lastModified(simpleDateFormat.parse(config.getLastModified()));
      } catch (ParseException e) {
        log.warn("Unable to parse date [{}]", config.getLastModified());
      }
    }

    if (MapUtils.isNotEmpty(result.getTags())) {
      builder.tags(ImmutableMap.copyOf(result.getTags()));
    }

    if (listAliasesResult != null) {
      builder.aliases(
              emptyIfNull(listAliasesResult.getAliases()).stream().map(AliasConfiguration::getName).collect(toList()));
    }
    return builder.build();
  }

  public static AwsLambdaFunction from(AwsLambdaFunctionWithActiveVersions activeVersions, String version) {
    final AwsLambdaFunction.AwsLambdaFunctionBuilder builder = AwsLambdaFunction.builder()
            .functionArn(activeVersions.getFunctionArn())
            .functionName(activeVersions.getFunctionName())
            .runtime(activeVersions.getRuntime())
            .role(activeVersions.getRole())
            .handler(activeVersions.getHandler())
            .codeSize(activeVersions.getCodeSize())
            .description(activeVersions.getDescription())
            .timeout(activeVersions.getTimeout())
            .memorySize(activeVersions.getMemorySize())
            .codeSha256(activeVersions.getCodeSha256())
            .version(version)
            .kMSKeyArn(activeVersions.getKMSKeyArn())
            .masterArn(activeVersions.getMasterArn())
            .revisionId(activeVersions.getRevisionId())
            .lastModified(activeVersions.getLastModified())
            .aliases(activeVersions.getAliases())
            .tags(activeVersions.getTags());

    return builder.build();
  }
}
