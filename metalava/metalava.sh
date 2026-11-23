#!/bin/bash -e
# Copyright (C) 2026 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Find the script's directory using bash parameter expansion so that external
# tools (basename, dirname, expr) are not required when running inside sbox.
prog="$0"
if [[ "${prog}" == */* ]]; then
    progdir="${prog%/*}"
else
    progdir="."
fi
progdir="$(cd "${progdir}" && pwd)"
progname="${prog##*/}"

jarfile="${progname}.jar"
jardir="${progdir}"
if [ ! -r "${jardir}/${jarfile}" ]; then
    jardir="${progdir%/*}/framework"
fi
if [ ! -r "${jardir}/${jarfile}" ]; then
    jardir="${progdir}/../lib"
fi
if [ ! -r "${jardir}/${jarfile}" ]; then
    echo "${progname}: can't find ${jarfile}"
    exit 1
fi

javaOpts=()
classpath="${jardir}/${jarfile}"

# Extract the Java arguments from the command line, appending any --extra-jars
# entries to the classpath.
while true; do
    case "$1" in
        -J*)
            javaOpts+=("${1:2}")
            ;;
        --extra-jars)
            shift
            classpath="${classpath}:$1"
            ;;
        *)
            break
            ;;
    esac
    shift
done

exec java "${javaOpts[@]}" -cp "${classpath}" com.android.tools.metalava.Driver "$@"
