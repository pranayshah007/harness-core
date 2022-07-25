package io.harness.ci.beans.entities;

import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EncryptedDataDetails {
  List<EncryptedDataDetail> encryptedDataDetailList;
}