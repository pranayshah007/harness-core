# Copyright 2023 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import os
import hashlib

def get_file_hash(file_path):
    return hashlib.md5(file_path).hexdigest()

def find_duplicate_files(dir_list):
    # Dictionary to store file hashes and paths
    files = {}
    duplicates = {}

    for starting_directory in dir_list:
        # Recursively traverse the directory
        for dirpath, dirnames, filenames in os.walk(starting_directory):
            for filename in filenames:
                path = os.path.join(dirpath, filename)

                # Check if file hash already exists in the dictionary
                if filename in duplicates:
                    duplicates[filename].append(path)
                else:
                    duplicates[filename] = []
                    duplicates[filename].append(path)

    return duplicates

# Example usage
if __name__ == '__main__':
    dir_list = ['./ci', './security', './cd', './pie', './cvng']
    duplicates = find_duplicate_files(dir_list)
    if duplicates:
        print("Found duplicates:")
        for hash_value, file_paths in duplicates.items():
            if len(duplicates[hash_value]) > 1:
                for file_path in file_paths:
                    print(file_path)
    else:
        print("No duplicates found.")
