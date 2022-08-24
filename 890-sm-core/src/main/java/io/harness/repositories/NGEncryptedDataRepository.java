package io.harness.repositories;

import io.harness.ng.core.entities.NGEncryptedData;

import java.util.Optional;

public interface NGEncryptedDataRepository {
  Optional<NGEncryptedData> findNGEncryptedDataByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier);

  Long deleteNGEncryptedDataByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier);

  String getCollectionName();

  NGEncryptedData save(NGEncryptedData ngEncryptedData);
}
