# Copyright 2023 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import csv
import re
import requests
from tabulate import tabulate

def get_newer_versions(group_id, artifact_id, current_version):
    # Construct the search URL for the Maven Central Repository
    search_url = f"https://search.maven.org/solrsearch/select?q=g:{group_id}+AND+a:{artifact_id}&core=gav&rows=50"

    # Send the HTTP GET request to the Maven Central Repository API
    response = requests.get(search_url, timeout=10)

    # Parse the JSON response
    json_data = response.json()

    # Extract the list of versions from the response
    versions = [doc["v"] for doc in json_data["response"]["docs"]]

    # Filter out versions older than the current version
    current_parsed = parse_version(current_version)
    newer_versions = [v for v in versions if compare_versions(parse_version(v), current_parsed) is not None]

    return sorted(newer_versions, reverse=True)

def parse_version(version):
    version_parts = version.split('.')
    parsed_parts = []
    for part in version_parts:
        try:
            parsed_part = int(part)
        except ValueError:
            parsed_part = part
        parsed_parts.append(parsed_part)
    return parsed_parts

def compare_versions(v1, v2):
    for i in range(min(len(v1), len(v2))):
        if isinstance(v1[i], int) and isinstance(v2[i], int):
            if v1[i] < v2[i]:
                return None
            elif v1[i] > v2[i]:
                if i == 0:
                    return "Major"
                elif i == 1:
                    return "Minor"
                else:
                    return "Patch"
        else:
            if str(v1[i]) < str(v2[i]):
                return None
            elif str(v1[i]) > str(v2[i]):
                if i == 0:
                    return "Major"
                elif i == 1:
                    return "Minor"
                else:
                    return "Patch"
    return None


def process_artifacts(artifacts):
    table_data = []
    for artifact in artifacts:
        try:
            group_id, artifact_id, current_version = artifact.split(':')
        except ValueError as e:
            print(f"Skipping artifact '{artifact}' due to invalid format: {str(e)}")
            continue

        # version_match = re.search(r'\d+\.\d+\.\d+', current_version)
        # if version_match:
        #     current_version = version_match.group()
        # else:
        #     print(f"Skipping artifact '{artifact}' due to invalid version format")
        #     continue

        try:
            newer_versions = get_newer_versions(group_id, artifact_id, current_version)
        except Exception as e:
            print(f"Error getting newer versions for artifact '{artifact}': {str(e)}")
            continue

        num_newer_versions = len(newer_versions)
        current_parsed = parse_version(current_version)
        upgrade_types = []
        for version in newer_versions:
            parsed_version = parse_version(version)
            upgrade_type = compare_versions(parsed_version, current_parsed)
            if upgrade_type:
                upgrade_types.append(upgrade_type)
        upgrade_types = list(set(upgrade_types))
        table_data.append([f"{group_id}:{artifact_id}", current_version, ', '.join(newer_versions), num_newer_versions, ', '.join(upgrade_types) if upgrade_types else "None"])

    table_data.sort(key=lambda x: x[3], reverse=True)
    return table_data


def print_to_csv(file_name, headers, data):
    with open(file_name, 'w', newline='') as csvfile:
        writer = csv.writer(csvfile)
        writer.writerow(headers)
        writer.writerows(data)

# Read the WORKSPACE file
with open('WORKSPACE') as f:
    workspace_data = f.read()

# Regular expression pattern to find the plain_artifacts list
pattern = r'plain_artifacts\s*=\s*\[\s*(.*?)\s*\]'

# Find the plain_artifacts list using the regular expression
match = re.search(pattern, workspace_data, re.DOTALL)

if match:
    # Extract the content of the plain_artifacts list
    plain_artifacts_content = match.group(1)

    # Split the content into individual artifact entries
    plain_artifacts = [entry.strip()[1:-1] for entry in plain_artifacts_content.split(',')]

    # Prepare the table data
    table_data = process_artifacts(plain_artifacts)

    # Sort the table data based on the number of newer versions
    table_data.sort(key=lambda x: x[3], reverse=True)

    # Format and print the table in CSV format
    headers = ["Artifact", "Current Version", "Newer Versions", "Num Newer Versions", "Upgrades Available"]
    print_to_csv('output.csv', headers, table_data)

    print("Output saved in output.csv")
else:
    print("plain_artifacts list not found in the WORKSPACE file.")
