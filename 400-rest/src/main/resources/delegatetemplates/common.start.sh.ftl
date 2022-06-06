<#include "common.sh.ftl">

function jar_app_version() {
  JAR=$1
  if unzip -l $JAR | grep -q io/harness/versionInfo.yaml
  then
    VERSION=$(unzip -c $JAR io/harness/versionInfo.yaml | grep "^version " | cut -d ":" -f2 | tr -d " " | tr -d "\r" | tr -d "\n")
  fi

  if [ -z "$VERSION" ]
  then
    if unzip -l $JAR | grep -q main/resources-filtered/versionInfo.yaml
    then
      VERSION=$(unzip -c $JAR main/resources-filtered/versionInfo.yaml | grep "^version " | cut -d ":" -f2 | tr -d " " | tr -d "\r" | tr -d "\n")
    fi
  fi

  if [ -z "$VERSION" ]
  then
    if unzip -l $JAR | grep -q BOOT-INF/classes/main/resources-filtered/versionInfo.yaml
    then
      VERSION=$(unzip -c $JAR BOOT-INF/classes/main/resources-filtered/versionInfo.yaml | grep "^version " | cut -d ":" -f2 | tr -d " " | tr -d "\r" | tr -d "\n")
    fi
  fi

  if [ -z "$VERSION" ]
  then
    VERSION=$(unzip -c $JAR META-INF/MANIFEST.MF | grep Application-Version | cut -d "=" -f2 | tr -d " " | tr -d "\r" | tr -d "\n")
  fi
  echo $VERSION
}

# url-encodes a given input string - used to encode the proxy password for curl commands.
# Note:
#   - We implement the functionality ourselves to avoid dependencies on new packages.
#   - We encode a superset of the characters defined in the specification, which is explicitly
#     allowed: https://www.ietf.org/rfc/rfc1738.txt
<#noparse>
url_encode () {
    local input=$1
    for (( i=0; i<${#input}; i++ )); do
        local c=${input:$i:1}
        case $c in
            [a-zA-Z0-9-_\.\!\*]) printf "$c" ;;
            *) printf '%%%02X' "'$c"
        esac
    done
}

function jar_auth_token() {
  secret=$DELEGATE_TOKEN
  accountId=$ACCOUNT_ID
  issuer='Harness Delegates'

  if [ -z "$secret" ]
  then
    echo "Missing DELEGATE_TOKEN in env"
    exit 1
  fi
  if [ -z "$accountId" ]
  then
    echo "Missing ACCOUNT_ID in env"
    exit 1
  fi
  if [ -z "$issuer" ]
  then
    echo "Missing ISSUER in env"
    exit 1
  fi
  # Static header fields.
  header='{
    "typ": "JWT",
    "alg":"HS256"
  }'

  payload_template='{"sub":"%s",
    "issuer":"%s",
    "iat":%s,
    "exp":%s}'

  # `iat` is set to now, and `exp` is now + 600 seconds.
  iat=$(date +%s)
  exp=$(($iat + 600))

  payload=$(printf "$payload_template" "$accountId" "$issuer" "$iat" "$exp")

  base64_encode() {
  declare input=${1:-$(</dev/stdin)}
  # Use `tr` to URL encode the output from base64.
  printf '%s' "${input}" | base64 | tr -d '=' | tr '/+' '_-' | tr -d '\n'
  }

  json() {
  declare input=${1:-$(</dev/stdin)}
  printf '%s' "${input}" | tr -d [:space:]
  }

  hmacsha256_sign() {
  declare input=${1:-$(</dev/stdin)}
  printf '%s' "${input}" | openssl dgst -binary -sha256 -hmac "${secret}"
  }

  header_base64=$(echo "${header}" | json | base64_encode)
  payload_base64=$(echo "${payload}" | json | base64_encode)
  header_payload=$(echo "${header_base64}.${payload_base64}")
  signature=$(echo "${header_payload}" | hmacsha256_sign | base64_encode)
  JWT_TOKEN="${header_payload}.${signature}"
  echo $JWT_TOKEN
}
</#noparse>