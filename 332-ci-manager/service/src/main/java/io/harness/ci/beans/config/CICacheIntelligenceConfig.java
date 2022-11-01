package io.harness.ci.config;

import io.harness.annotation.RecasterAlias;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@TypeAlias("ciCacheIntelligenceConfig")
@RecasterAlias("io.harness.ci.config.CICacheIntelligenceConfig")
public class CICacheIntelligenceConfig {
  String bucket;
  String serviceKey;
}
