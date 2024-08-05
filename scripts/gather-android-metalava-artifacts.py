#!/usr/bin/env -S python3 -u
#  Copyright (C) 2024 The Android Open Source Project
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
import os
from pathlib import Path
import shutil
import subprocess
import sys

# Formatted using: pyformat -s 4 --force_quote_type double -i scripts/gather-android-metalava-artifacts.py


def main(args):
    top = os.environ.get("ANDROID_BUILD_TOP")
    if not top:
        raise Exception("ANDROID_BUILD_TOP not specified")
    os.chdir(top)

    args_parser = argparse.ArgumentParser(
        description=(
            "Gather Android artifacts created by Metalava. This will build and"
            " then copy a set of targets to the output directory. If no custom"
            " targets are provided then a set of default ones will be provided"
            " that covers stub generation, signature to JDiff conversion and"
            " api-versions.xml file generation. The intent is that this would"
            " be run this before and after making the change to build and copy"
            " the artifacts into two separate directories that can then be"
            " compared to see what, if any, changes have happened. This does"
            " not check signature file generation as that can be easily checked"
            " by running `m checkapi`."
        ),
    )
    args_parser.add_argument(
        "directory",
        help="Output directory into which artifacts will be copied.",
    )
    args_parser.add_argument(
        "--stub-src-jar",
        action="append",
        help="Additional stub jar to gather",
    )
    args = args_parser.parse_args(args)

    output_dir = Path(args.directory)
    if output_dir.exists():
        raise Exception(f"{output_dir} exists, please delete or change")

    targets = []

    # If any custom options have been provided then build them.
    if args.stub_src_jar:
        targets += args.stub_src_jar

    # If no custom targets have been provided then use the default targets.
    if not targets:
        stub_files = (
            args.stub_src_jar
            if args.stub_src_jar
            else [
                f"out/target/common/docs/{x}-stubs.srcjar"
                for x in [
                    "api-stubs-docs-non-updatable",
                    "system-api-stubs-docs-non-updatable",
                    "test-api-stubs-docs-non-updatable",
                    "module-lib-api-stubs-docs-non-updatable",
                ]
            ]
        )

        targets += stub_files

        jdiff_files = [
            "out/target/common/obj/api.xml",
            "out/target/common/obj/system-api.xml",
            "out/target/common/obj/module-lib-api.xml",
            "out/target/common/obj/system-server-api.xml",
            "out/target/common/obj/test-api.xml",
        ]

        targets += jdiff_files

        api_version_files = [
            "out/soong/lint/api_versions_public.xml",
            "out/soong/lint/api_versions_system.xml",
            "out/soong/lint/api_versions_module_lib.xml",
            "out/soong/lint/api_versions_system_server.xml",
        ]

        targets += api_version_files

    print()
    print("Building the following targets:")
    for t in targets:
        print(f"    {t}")
    print()

    subprocess.run(
        ["build/soong/soong_ui.bash", "--make-mode"] + targets, check=True
    )
    print()

    print(f"Making output directory: '{output_dir}'")
    os.mkdir(output_dir)
    print()

    print(f"Copying the following targets into '{output_dir}':")
    for t in targets:
        print(f"    {t}")
        shutil.copy(t, output_dir)
    print()


if __name__ == "__main__":
    main(sys.argv[1:])
