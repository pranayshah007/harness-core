echo "Test script"

export SERVICE_NAME="platform-service"
MATCHING_TAGS=$SERVICE_NAME

NEWEST_TAG=$(git for-each-ref --sort=-taggerdate --format '%(refname)' refs/tags | grep "$MATCHING_TAGS" | head -n 2 | tail -1)
LATEST_TAG=${NEWEST_TAG#refs/tags/}

echo "LATEST_TAG is: $LATEST_TAG"

PREVIOUS_TAG_REF=$(git for-each-ref --sort=-taggerdate --format '%(refname)' refs/tags | grep "$MATCHING_TAGS" | head -n 3 | tail -1)
PREVIOUS_TAG=${PREVIOUS_TAG_REF#refs/tags/}

echo "PREVIOUS_TAG is: $PREVIOUS_TAG"

VERSION="${LATEST_TAG##*/v}"

export VERSION



FIX_PLS_VERSION=${SERVICE_NAME}_$VERSION

echo $FIX_PLS_VERSION

export FIX_PLS_VERSION

# chmod +x platform-service/release/release-branch-create-pls-versions-v2.sh
# platform-service/release/release-branch-create-pls-versions-v2.sh

# Create JIRA tags

PROJECTS="PL"
KEYS=$(git log --pretty=oneline --abbrev-commit $PREVIOUS_TAG..$LATEST_TAG |grep -o -iE '('$PROJECTS')-[0-9]+' | sort | uniq)

echo $KEYS

# for KEY in ${KEYS}
#   do
#     echo "$KEY"
#     response=$(curl -q -X PUT https://harness.atlassian.net/rest/api/2/issue/${KEY} --write-out '%{http_code}' --user ${JIRA_USERNAME}:${JIRA_PASSWORD} -H "Content-Type: application/json" -d '{
#     "update": {
#     "fixVersions": [
#       {"add":
#         {"name": "'"$FIX_PLS_VERSION"'" }
#       }]}}')
#     if [[ "$response" -eq 204 ]] ; then
#       echo "$KEY fixVersion set to $FIX_PLS_VERSION"
#     elif [[ "$response" -eq 400 ]] ; then
#       echo "Could not set fixVersion on $KEY - field hidden for the issue type"
#     fi
#   done



