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
import re
import sys

# Formatted using: pyformat -s 4 --force_quote_type double -i scripts/remove-hide.py

# Matches the API annotations that are of interest.
api_annotations = "|".join([
    "SystemApi",
    "TestApi",
    "Hide",
])

api_annotation_group = rf"(?:{api_annotations})"

ws = r"(?:(?!\n)\s)"

# Match end of comment up to @SystemApi or @TestApi, allows some optional stuff in between, i.e.
# * @....(...) annotation spread across multiple lines (as long as it does not contain any `)` within the parentheses.
# * Any number of annotations on their own line.
# * Any number of line comments.
# * Blank lines.
api_annotation_use = (
    # Open non-capturing group for the whole expression.
    rf"(?:"
    # Open non-capturing group for any lines between the comment and the @SystemApi|@TestApi
    rf"(?:"
    # Skip leading white space.
    rf"{ws}*"
    # Open non-capturing group for the remaining line contents.
    rf"(?:"
    # Open non-capturing group for some special annotations that are known to have parameters that can be spread across
    # multiple lines. This only works when the parameters inside the parentheses do not contain a close parenthesis.
    # Uses negative lookahead to ensure that it never matches an api_annotation which allows use of atomic groups to
    # avoid back tracking performance issues.
    rf"(?>@(?!{api_annotation_group})(?:\w+)\((?:[^)]|\n)+\))"
    # Match any line that starts with an annotation.
    # Uses negative lookahead and atomic groups to avoid back tracking performance issues.
    rf"|(?>@(?!{api_annotation_group})\w.*)"
    # Or match any line comment; disables back tracking as this will never match an api_annotation
    rf"|(?>//.*)"
    # Or match any blank line; disables back tracking as this will never match an api_annotation
    rf"|(?> *)"
    # Or match any block comment; disables back tracking as this will never match an api_annotation
    rf"|(?>/\*(?!\*)(?:[^*]|\*(?!/))*\*/)"
    # Close non-capturing group for the line contents
    rf")"
    # Match the end of the line.
    rf"\n"
    # Close non-capturing group for any lines between the comment and the @SystemApi|@TestApi
    rf")"
    # Non-greedily match intervening lines.
    rf"*?"
    # Only match comments on items annotated with @SystemApi or @TestApi
    rf"{ws}*(?:@(?:{api_annotation_group}))"
    # Close non-capturing group for the whole expression.
    rf")"
)

# Matches a comment that looks like this:
#   /**
#    * @hide
#    */
comment_with_just_hide_pattern = re.compile(rf"^{ws}*/\*\*\n{ws}*\*{ws}*@hide{ws}*\n{ws}*\*/\n")

# Matches a comment opening that looks something like this:
#   /** some stuff
normalize_comment_open = re.compile(rf"^({ws}*)/\*\*({ws}*.+\n)", re.MULTILINE)

# Matches a comment close that looks something like this:
#   ***/
normalize_comment_close1 = re.compile(rf"^({ws}*)[*][{ws}*]*(\*/\n)", re.MULTILINE)

# Matches a comment close that looks something like this:
#   some text ***/
normalize_comment_close2 = re.compile(
    rf"^({ws}*)(\S.*?){ws}*\**(\*/\n)", re.MULTILINE
)

# Matches a comment that looks something like this:
#   /**
#    * @hide a message
#    * possibly continued on a following line.
#    */
hide_with_message_only = (
    rf"({ws}*/\*\*\n{ws}*\*){ws}*@hide{ws}+(.+\n(?:{ws}*\*{ws}*[^@{ws}\n].*\n)* *\*/\n)"
)
hide_with_message_only_pattern = re.compile(
    rf"^{hide_with_message_only}", re.MULTILINE
)

# Matches an optional blank line.
leading_blank_line_pattern = re.compile(rf"^{ws}*\n")

# Matches a blank comment line that looks something like this:
#    *
blank_comment_line = rf"(?:{ws}*\*[{ws}]*\n)"

# Matches an @hide comment line that looks something like this:
#    *  @hide an optional rationale
hide_line = rf"(?:{ws}*(?:\*)?[*{ws}]*@hide({ws}+.*)?\n)"
hide_line_pattern = re.compile(
    rf"^{blank_comment_line}*{hide_line}", re.MULTILINE
)


def clean_comment(comment):
    if "@hide" not in comment:
        return comment

    # Set to True for debugging
    debug = False

    if debug:
        print(f"before:\n{comment}")

    # Remove an optional blank line before the comment. This will be added back by the caller. That will ensure that
    # if the whole comment is removed the item is still visually separated from the preceding item.
    comment = leading_blank_line_pattern.sub("", comment)

    if debug:
        print(f"after leading blank:\n{comment}")


    # Normalize the comment open by moving any text following the /** onto the following line.
    # e.g.
    #     /** @hide */
    # becomes:
    #     /**
    #      * @hide */
    comment = normalize_comment_open.sub(r"\1/**\n\1 *\2", comment)

    if debug:
        print(f"after normalize open:\n{comment}")

    # Normalize the comment close by removing any leading * before */.
    # e.g.
    #     /**
    #      * @hide
    #      **/
    # becomes:
    #     /**
    #      * @hide
    #      */
    comment = normalize_comment_close1.sub(r"\1\2", comment)

    if debug:
        print(f"after normalize close1:\n{comment}")

    # Normalize the comment close by moving the */ onto its own line if it is not at the beginning of the line.
    # e.g.
    #     /**
    #      * @hide */
    # becomes:
    #     /**
    #      * @hide
    #      */
    comment = normalize_comment_close2.sub(r"\1\2\n\1\3", comment)

    if debug:
        print(f"after normalize close2:\n{comment}")

    # Remove the comment if just contains @hide.
    # e.g. if it is the following then remove it.
    #     /**
    #      * @hide
    #      */
    comment = comment_with_just_hide_pattern.sub("", comment)
    if comment == "":
        return comment

    # Remove the @hide if the comment just contains @hide and a message.
    # e.g. if it is the following then remove it @hide.
    #     /**
    #      * @hide A message
    #      */
    # becomes:
    #     /**
    #      * A message
    #      */
    #
    # While it is possible that the message is a rationale that should be removed from along with the @hide examining
    # uses in the source show that the message is often the description of the commented item rather than the rationale
    # for hiding.
    comment = hide_with_message_only_pattern.sub(r"\1 \2", comment)

    if debug:
        print(f"after message only:\n{comment}")

    # Remove the @hide line, including any rationale if it is just part of a comment. Also remove any preceding blank
    # comment lines.
    # e.g.
    #     /**
    #      * Some text.
    #      *
    #      * @hide optional rationale
    #      */
    # becomes:
    #     /**
    #      * Some text.
    #      */
    #
    # The rationale is removed because just removing the @hide would leave text that would appear in the API
    # documentation. If it needs to be kept then it should be moved to a line comment after the documentation comment
    # to avoid that.
    comment = hide_line_pattern.sub("", comment)

    if debug:
        print(f"after hide line:\n{comment}")

    return comment


# A pattern that matches a comment.
comment_pattern = re.compile(
    # Open capturing group for the comment being matched.
    rf"("
    # Match an optional leading blank line.
    rf"^(?:{ws}*\n)?"
    # Match a documentation comment, i.e. `  /** ...*/`
    rf"{ws}*/\*\*(?:[^*]|\*(?!/))*\*/\n"
    # Close the capturing group.
    rf")"
    # Match an API annotation use after the comment. This is not included in the capturing group.
    rf"{api_annotation_use}",
    re.MULTILINE,
)

# Special case pattern that matches cases like:
#     /** @hide */ @SystemApi
special_pattern = re.compile(
    # Match /** @hide */.
    rf"(?:/\*\*{ws}*@hide{ws}*\*/{ws}*)"
    # Capture the api annotation.
    rf"(@{api_annotation_group})"
)

def clean_javadoc(file_path):
    with open(file_path, "r", newline="") as f:
        content = f.read()

    content = special_pattern.sub(r"\1", content)

    with open(file_path, "w", newline="") as f:
        start = 0
        for match in comment_pattern.finditer(content):
            # Write everything between the last match and this one.
            f.write(content[start : match.start(1)])

            # Write the cleaned comment preceding by a blank line.
            comment = match[1]
            cleaned_comment = clean_comment(comment)
            if cleaned_comment == comment:
                f.write(comment)
            else:
                f.write("\n")
                f.write(cleaned_comment)

            # Move to the end of the comment.
            start = match.end(1)

        # Write the remainder.
        f.write(content[start:])


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: ./remove-hide.py <file1> <file2> ...")
        sys.exit(1)

    for file_path in sys.argv[1:]:
        clean_javadoc(file_path)
        print(f"Processed: {file_path}")
