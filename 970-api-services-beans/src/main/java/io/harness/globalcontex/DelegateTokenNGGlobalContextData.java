package io.harness.globalcontex;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.context.GlobalContextData;

import lombok.Builder;
import lombok.Value;

@OwnedBy(DEL)
@Value
@Builder
public class DelegateTokenNGGlobalContextData implements GlobalContextData {
  public static final String TOKEN_NAME_NG = "TOKEN_NAME_NG";
  private String tokenName;

  @Override
  public String getKey() {
    return TOKEN_NAME_NG;
  }
}
