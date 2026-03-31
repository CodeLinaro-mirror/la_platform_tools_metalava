#!/usr/bin/env -S python3 -u
#  Copyright (C) 2026 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import argparse
import dataclasses
from glob import glob
import os
from pathlib import Path
import shutil
import subprocess
import sys
from typing import List

# Formatted using: pyformat -s 4 --force_quote_type double -i scripts/migrate-signature-files.py


def parse_command_line_args(args):
    """Define the command line options and the parse the command line arguments with them.

    :param args: the command line arguments :return: Return the result of
    parsing the command line arguments.
    """
    args_parser = argparse.ArgumentParser(
        description="Migrate signature files from one version to another.",
    )
    args_parser.add_argument(
        "--format",
        help="Target format for migration.",
    )
    args_parser.add_argument(
        "--format-defaults",
        help=(
            "Additional properties to apply to the current file's format to"
            " match how they are built."
        ),
    )
    args_parser.add_argument(
        "--sdk-library-dir",
        help="Directory containing signature files from a java_sdk_library.",
        action="append",
    )
    args_parser.add_argument(
        "--title-prefix",
        help="Prefix to add to the title of each commit",
    )
    args_parser.add_argument(
        "--bug",
        help="Bug to use in the commit messages",
        required=True,
    )
    args_parser.add_argument(
        "filegroups",
        help="Colon separated file groups.",
        nargs="*",
    )

    return args_parser.parse_args(args)


def main(args):
    top = os.environ.get("ANDROID_BUILD_TOP")
    if not top:
        raise Exception("ANDROID_BUILD_TOP not specified")

    # Parse command line arguments.
    args = parse_command_line_args(args)

    file_groups_to_migrate = []

    sdk_library_dirs = args.sdk_library_dir
    if sdk_library_dirs:
        for sdk_library_dir in sdk_library_dirs:
            scan_sdk_library_dir_for_file_groups(
                file_groups_to_migrate, sdk_library_dir
            )

    if args.filegroups:
        file_groups_to_migrate += args.filegroups

    print("Migrating the following groups:")
    for file_group in file_groups_to_migrate:
        print(f"    {file_group}")
    print()

    command = ["metalava", "signature-migrate"]
    if args.format:
        command += ["--format", args.format]
    if args.format_defaults:
        command += ["--format-defaults", args.format_defaults]
    if args.title_prefix:
        command += ["--title-prefix", args.title_prefix]

    command += ["--initial-title", "Migrate signature files to 6.0:style=java"]

    epilog = [
        "FCRS_CODE: sdkExempt",
        "Flag: EXEMPT PURE_REFACTOR",
        f"Bug: {args.bug}",
        f"API-Coverage-Bug: {args.bug}",
        "Test: m checkapi",
    ]

    command += [
        "--commit-prolog",
        "Preparation for adding support for record and sealed classes.",
    ]
    command += ["--commit-epilog", "\n".join(epilog)]

    command += [
        "--regenerate-command",
        "m BUILD_FROM_SOURCE_STUB=true update-api",
    ]

    command += file_groups_to_migrate

    subprocess.run(command)

    return args


def scan_sdk_library_dir_for_file_groups(
    file_groups_to_migrate: List[str], sdk_library_dir: str
):
    """Scan a sdk library directory and add any file groups that need migrating to `file_groups_to_migrate`.

    :param file_groups_to_migrate: the list of file groups to be migrated.
    :param sdk_library_dir: the sdk library directory to scan.
    """
    for path in Path(sdk_library_dir).rglob("*current.txt"):
        file_group = []
        with open(path, "r") as file:
            line = file.readline()
            if line.startswith("// Signature format: "):
                file_group.append(str(path))
                removed_path = path.parent / path.name.replace(
                    "current", "removed"
                )
                if removed_path.is_file():
                    file_group.append(str(removed_path))
        if file_group:
            file_groups_to_migrate.append(":".join(file_group))


if __name__ == "__main__":
    main(sys.argv[1:])
