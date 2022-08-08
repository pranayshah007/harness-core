package io.harness.delegate.task.scm;

import io.harness.delegate.beans.DelegateResponseData;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ScmRefreshTokenTaskResponse implements DelegateResponseData {
  String accessToken;
  String refreshToken;
}
