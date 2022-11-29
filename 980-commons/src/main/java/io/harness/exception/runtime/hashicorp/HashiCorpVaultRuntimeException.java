package io.harness.exception.runtime.hashicorp;

public class HashiCorpVaultRuntimeException extends RuntimeException {
  private String message;
  public HashiCorpVaultRuntimeException(String message) {
    super(message);
    this.message = message;
  }
}