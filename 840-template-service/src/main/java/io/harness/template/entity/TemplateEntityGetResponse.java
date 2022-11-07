package io.harness.template.entity;

import io.harness.gitsync.sdk.EntityGitDetails;

import com.mongodb.lang.Nullable;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TemplateEntityGetResponse {
  TemplateEntity templateEntity;
  @Nullable EntityGitDetails entityGitDetails;
}
