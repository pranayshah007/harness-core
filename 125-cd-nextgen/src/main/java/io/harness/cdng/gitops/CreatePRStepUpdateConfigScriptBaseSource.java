package io.harness.cdng.gitops;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@OwnedBy(GITOPS)
@JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
public interface CreatePRStepUpdateConfigScriptBaseSource {
  String getType();
}
