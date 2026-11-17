#!/usr/bin/env python3
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

import os
import re
import subprocess

# Formatted using: pyformat -s 4 --force_quote_type double -i scripts/split-commit.py


change_id_line = re.compile(r"Change-Id:.*(?:\n)?", re.MULTILINE)

def split_commit():
    # 1. Save the current branch
    original_branch = (
        subprocess.check_output(["git", "rev-parse", "--abbrev-ref", "HEAD"])
        .decode()
        .strip()
    )

    try:
        # Get commit message
        commit_msg = (
            subprocess.check_output(["git", "log", "-1", "--pretty=%B"])
            .decode()
            .strip()
        )

        # Remove Change-Id to ensure each commit created has a different one.
        commit_msg = change_id_line.sub("", commit_msg)

        # Get files using git show
        files = (
            subprocess.check_output([
                "git",
                "show",
                "--name-only",
                "--pretty=format:",
                original_branch,
            ])
            .decode()
            .splitlines()
        )

        # Identify unique top-level directories
        dirs = {f.split("/")[0] for f in files if "/" in f}

        for d in dirs:
            branch_name = f"split-{d}"
            # Create branch from the parent of the original branch
            subprocess.run(["repo", "start", branch_name, "."])
            # Stage only files in this directory from the original branch tip
            subprocess.run(["git", "checkout", original_branch, "--", d])
            subprocess.run(["git", "commit", "-m", f"[{d}] {commit_msg}"])
            print(f"Created branch {branch_name} for {d}")
    finally:
        # 2. Check the original branch back out at the end
        print(f"Returning to {original_branch}")
        subprocess.run(["git", "checkout", original_branch])


if __name__ == "__main__":
    split_commit()
