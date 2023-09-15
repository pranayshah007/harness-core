#!/bin/bash -e
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
set +e

function import_pem_file() {
  local PEM_FILE_PATH=$1
  local NUM_CERTS=$2
  # When keytool import pem bundle files, it imports only the first pem block.
  # Thus for every cert in the PEM file, extract it and import into the JKS keystore
  # awk command: step 1, if line is in the desired cert, print the line
  #              step 2, increment counter when last line of cert is found
  #              step 3, print the begin line when it's the starter line of the cert of interest
  echo $NUM_CERTS
  for N in $(seq 0 $((NUM_CERTS - 1))); do
    ALIAS="$(basename $PEM_FILE_PATH)-$N"
    cat $PEM_FILE_PATH | \
     awk "n==$N&&inzone==1 { print }; /END CERTIFICATE/ { inzone=0; n++ };/BEGIN CERTIFICATE/ { if(n==$N) print; inzone=1; }" | tee "$SHARED_CA_CERTS_PATH/$ALIAS.pem" | \
     keytool -noprompt -importcert -trustcacerts -alias $ALIAS -keystore $TRUST_STORE_FILE -storepass $PASSWORD
  done
}

function import_der_file() {
  local DER_FILE_PATH=$1
  local ALIAS="$(basename $DER_FILE_PATH)"
  keytool -noprompt -importcert -trustcacerts -alias $ALIAS -file $DER_FILE_PATH -keystore $TRUST_STORE_FILE -storepass $PASSWORD
}

if [ -z "$CA_CERTS_DIR" ]; then
  echo "CA_CERTS_DIR not set. Skip importing certificates."
  return 0
fi

if [ ! -d "$CA_CERTS_DIR" ]; then
  echo "CA_CERTS_DIR is not a directory. Skip importing certificates."
  return 0
fi

if [ -z "$(ls -A -- "$CA_CERTS_DIR")" ]; then
  echo "$CA_CERTS_DIR is empty. Skip importing certificates."
  return 0
fi

PASSWORD="changeit"
ANCHOR_PATH="/etc/pki/ca-trust/source/anchors/"

# Support certificates is PEM formats
# Only support x509 certificate. PKCS#7 chain not supported, please convert to x509 PEM format file before running the
# script.
# Multiple certificates can put in one PEM file, thus this script support ca bundle pem file
# DER format file can contain only one certificate in it.
# PEM format files includes .crt .cer and .pem
# DER format files includes .der .cer

# copy certs to /etc/pki/ca-trust/source/anchors/ to update RHEL trust system
# root user required
cp $CA_CERTS_DIR/* $ANCHOR_PATH
update-ca-trust enable

# Import custom certificates to java truststore file
TRUST_STORE_FILE=$JAVA_HOME/lib/security/cacerts

# In case, people can still use this script as it'd be a useful tool
# to unblock customers in older versions of delegate
if [ -z "$SHARED_CA_CERTS_PATH" ]; then
     echo "SHARED_CA_CERTS_PATH not set. using default path $HOME/additional_certs_pem_split"
     export SHARED_CA_CERTS_PATH=$HOME/additional_certs_pem_split
     mkdir -p $SHARED_CA_CERTS_PATH
fi

for FILE in $CA_CERTS_DIR/*; do
  NUM_PEM_BLOCKS=$(grep 'END CERTIFICATE' $FILE | wc -l)
  # if NUM_PEM_BLOCKS greater than 0, we think it's a PEM file
  if [ $NUM_PEM_BLOCKS -gt 0 ]; then
    echo "Importing PEM $FILE to java truststore..."
    import_pem_file "$FILE" $NUM_PEM_BLOCKS
  else
    # if it's not pem file, best effort to import cert
    echo "Importing $FILE to java truststore..."
    import_der_file "$FILE"
  fi
done;

