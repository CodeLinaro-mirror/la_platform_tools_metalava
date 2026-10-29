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

"""A script to replace Javadoc/doc-tags with equivalent Java annotations.

This script parses Java source files to find Javadoc comments containing specific
doc-tags (e.g., `@hide`) and replaces them with corresponding Java annotations
(e.g., `@android.annotation.Hide`). It also updates the import statements in the
Java files to import the newly added annotations, ensuring they are placed in
the correct alphabetical order.

Usage:
    ./replace-doc-tags.py <file1> <file2> ...
"""

import dataclasses
import io
import re
import sys
import typing

# Formatted using: pyformat -s 4 --force_quote_type double -i scripts/remove-hide.py

@dataclasses.dataclass()
class Annotation:
    """Represents an API annotation that replaces a doc-tag.

    Attributes:
        qualified: The fully qualified name of the annotation (e.g., 'android.annotation.Hide').
        name: The simple name of the annotation (e.g., 'Hide'), automatically derived from qualified.
        doc_tag: The doc-tag that this annotation replaces (e.g., '@hide').
    """
    # The qualified name of the annotation.
    qualified: str

    # The simple name of the annotation.
    name: str = dataclasses.field(init=False)

    # The doc tag it is replacing.
    doc_tag: str

    def __post_init__(self):
        self.name = self.qualified.rsplit(".", 1)[-1]

annotations = [
    Annotation(
        qualified = "android.annotation.Hide",
        doc_tag = "@hide",
    ),
    Annotation(
        qualified = "android.annotation.RemovedFromApi",
        doc_tag = "@removed",
    ),
]

# Map from doc tag to Annotation.
tag_to_annotation = { x.doc_tag: x for x in annotations }

# Pattern that will match any of the doc tags in tag_to_annotation.
api_doc_tags = "|".join(tag_to_annotation.keys())

# Group of api_doc_tags
api_doc_tags_group = rf"(?:{api_doc_tags})"

ws = r"(?:(?!\n)\s)"

# Pattern that matches block tag.
api_block_tags_pattern = re.compile(rf"^{ws}*(?:\*|/\*\*)?{ws}*({api_doc_tags_group})", re.MULTILINE)

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
    rf"(?>@(?!{api_doc_tags_group})(?:\w+)\((?:[^)]|\n)+\))"
    # Match any line that starts with an annotation.
    # Uses negative lookahead and atomic groups to avoid back tracking performance issues.
    rf"|(?>@(?!{api_doc_tags_group})\w.*)"
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
    rf"{ws}*(?:@(?:{api_doc_tags_group}))"
    # Close non-capturing group for the whole expression.
    rf")"
)

# Matches a comment that looks something like this:
#   /**
#    * @<doc-tag>
#    */
comment_with_just_doc_tag_pattern = re.compile(rf"^{ws}*/\*\*\n{ws}*\*{ws}*{api_doc_tags_group}{ws}*\n{ws}*\*/\n")

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
#    * @<doc-tag> a message
#    * possibly continued on a following line.
#    */
doc_tag_with_message_only = (
    rf"({ws}*/\*\*\n{ws}*\*){ws}*{api_doc_tags_group}{ws}+(.+\n(?:{ws}*\*{ws}*[^@{ws}\n].*\n)* *\*/\n)"
)
doc_tag_with_message_only_pattern = re.compile(
    rf"^{doc_tag_with_message_only}", re.MULTILINE
)

# Matches an optional blank line.
leading_blank_line_pattern = re.compile(rf"^{ws}*\n")

# Matches a blank comment line that looks something like this:
#    *
blank_comment_line = rf"(?:{ws}*\*[{ws}]*\n)"

# Matches a @<doc-tag> comment line that looks something like this:
#    *  @<doc-tag> an optional rationale
doc_tag_line = rf"(?:{ws}*(?:\*)?[*{ws}]*{api_doc_tags_group}({ws}+.*)?\n)"
doc_tag_line_pattern = re.compile(
    rf"^{blank_comment_line}*{doc_tag_line}", re.MULTILINE
)

# Matches leading whitespace.
leading_ws = re.compile(rf"^{ws}*", re.MULTILINE)


# A pattern that matches a comment.
comment_pattern = re.compile(
    # Open capturing group for the comment being matched.
    rf"("
    # Match an optional leading blank line.
    rf"^(?:{ws}*\n)?"
    # Match a documentation comment, i.e. `  /** ...*/`
    rf"{ws}*/\*\*(?:[^*]|\*(?!/))*\*/(?:\n)?"
    # Close the capturing group.
    rf")",
    re.MULTILINE,
)

# Pattern that matches a block comment on the same line as the member it is documenting:
#     /** @<doc-tag> */ ...
block_comment_on_same_line = re.compile(
    # Match /** @<doc-tag> */.
    rf"(?:^{ws}*/\*\*{ws}*{api_doc_tags_group}{ws}*\*/{ws}*)"
)


def clean_comment(comment):
    """Cleans a Javadoc comment by removing or modifying doc-tags.

    This function identifies doc-tags like `@hide` in a comment. If found, it:
    1. Removes the doc-tag from the comment.
    2. Normalizes comment opening/closing styles.
    3. Trims empty comments or removes comment sections that only contained the tag.
    4. Prepares the equivalent Java annotation string to be inserted.

    Args:
        comment: The raw Javadoc comment block string.

    Returns:
        A tuple of (cleaned_comment, annotation_to_insert, import_to_add), where:
          - cleaned_comment: The comment block with the target doc-tags removed/cleaned.
          - annotation_to_insert: The annotation string (e.g. '@Hide' with a trailing newline) to add before the member.
          - import_to_add: The fully qualified annotation name to be imported, or None.
    """
    # # Set to True for debugging
    debug = False

    if debug:
        print(f"before:\n{comment}")

    # Check to see if the comment contains a doc tag.
    doc_tag_match = api_block_tags_pattern.search(comment)
    if not doc_tag_match:
        return comment, None, None

    doc_tag = doc_tag_match.group(1)
    annotation = tag_to_annotation[doc_tag]
    if not annotation:
        raise LookupError(f"Could not find annotation for {doc_tag}")

    # The annotation class import to add to the file.
    import_to_add = annotation.qualified

    if debug:
        print(f"annotation: {annotation}")

    # Remove an optional blank line before the comment. This will be added back by the caller. That will ensure that
    # if the whole comment is removed the item is still visually separated from the preceding item.
    comment = leading_blank_line_pattern.sub("", comment)

    if debug:
        print(f"after leading blank:\n{comment}")

    # Extract the correct indentation from the first line of the comment.
    indent = leading_ws.match(comment).group(0)

    comment_ends_with_newline = comment.endswith("\n")
    block_comment_on_same_line_matches = block_comment_on_same_line.match(comment)
    if not comment.endswith("\n") and block_comment_on_same_line.match(comment):
        # Return an empty string to remove the comment (including indent), an annotation without a newline and the
        # import to add.
        return "", f"{indent}@{annotation.name}", import_to_add

    # The annotation to insert, on its own line.
    annotation_to_insert = f"{indent}@{annotation.name}\n"

    # Normalize the comment open by moving any text following the /** onto the following line.
    # e.g.
    #     /** ... */
    # becomes:
    #     /**
    #      * ... */
    comment = normalize_comment_open.sub(r"\1/**\n\1 *\2", comment)

    if debug:
        print(f"after normalize open:\n{comment}")

    # Normalize the comment close by removing any leading * before */.
    # e.g.
    #     /**
    #      * ...
    #      **/
    # becomes:
    #     /**
    #      * ...
    #      */
    comment = normalize_comment_close1.sub(r"\1\2", comment)

    if debug:
        print(f"after normalize close1:\n{comment}")

    # Normalize the comment close by moving the */ onto its own line if it is not at the beginning of the line.
    # e.g.
    #     /**
    #      * ... */
    # becomes:
    #     /**
    #      * ...
    #      */
    comment = normalize_comment_close2.sub(r"\1\2\n\1\3", comment)

    if debug:
        print(f"after normalize close2:\n{comment}")

    # Remove the comment if just contains @<doc-tag>.
    # e.g. if it is the following then remove it.
    #     /**
    #      * @<doc-tag>
    #      */
    comment = comment_with_just_doc_tag_pattern.sub("", comment)
    if comment == "":
        return comment, annotation_to_insert, import_to_add

    # Remove the @<doc-tag> if the comment just contains @<doc-tag> and a message.
    # e.g. if it is the following then remove @<doc-tag>.
    #     /**
    #      * @<doc-tag> A message
    #      */
    # becomes:
    #     /**
    #      * A message
    #      */
    #
    # While it is possible that the message is a rationale that should be removed from along with the @<doc-tag>
    # examining uses in the source show that the message is often the description of the commented item rather than the
    # rationale for adding <doc-tag>.
    comment = doc_tag_with_message_only_pattern.sub(r"\1 \2", comment)
    #
    if debug:
        print(f"after message only:\n{comment}")

    # Remove the @<doc-tag> line, including any rationale if it is just part of a comment. Also remove any preceding
    # blank comment lines.
    # e.g.
    #     /**
    #      * Some text.
    #      *
    #      * @<doc-tag> optional rationale
    #      */
    # becomes:
    #     /**
    #      * Some text.
    #      */
    #
    # The rationale is removed because just removing the @<doc-tag> would leave text that would appear in the API
    # documentation. If it needs to be kept then it should be moved to a line comment after the documentation comment
    # to avoid that.
    comment = doc_tag_line_pattern.sub("", comment)

    if debug:
        print(f"after remove doc tag line:\n{comment}")

    return comment, annotation_to_insert, import_to_add

package_statement_pattern = re.compile(rf"^{ws}*package{ws}+(\S+){ws}*;{ws}*$", re.MULTILINE)

# This intentionally ignores static imports.
import_statement_pattern = re.compile(rf"^{ws}*import{ws}+(\S+){ws}*;{ws}*\n", re.MULTILINE)

@dataclasses.dataclass
class Import:
    """Represents an existing import statement in a Java file.

    Attributes:
        qualified: The fully qualified package/class name being imported.
        start: The start character index of the import statement in the file content.
        end: The end character index of the import statement in the file content.
    """
    # The qualified name being imported.
    qualified: str

    # The position of the start of the line containing this import.
    start: int

    # The position of the end of the line containing this import.
    end: int

@dataclasses.dataclass
class Insert:
    """Represents a text insertion to be performed in the file content.

    Attributes:
        text: The text string to insert (e.g., 'import android.annotation.Hide;').
        position: The character index in the file content where the text should be inserted.
    """
    # The text to insert
    text: str

    # The position at which it should be inserted.
    position: int

def parse_imports(content: str) -> typing.Dict[str, Import]:
    """Parses existing non-static import statements from Java file content.

    Args:
        content: The text content of the Java file.

    Returns:
        A dictionary mapping the qualified imported name (e.g., 'java.util.List')
        to its corresponding Import metadata object.
    """
    # Extract all the import statements from the contents.
    imports = []
    for match in import_statement_pattern.finditer(content):
        imports.append(Import(match.group(1), match.start(), match.end()))
    return {x.qualified: x for x in imports}


def add_imports(content: str, imports_to_add: typing.List[str]) -> str:
    """Inserts new import statements into the Java file content.

    The new imports are placed in the correct alphabetical order relative to
    existing imports. If no imports exist, they are placed immediately after the
    package declaration. Imports belonging to the same package as the file are ignored.

    Args:
        content: The text content of the Java file.
        imports_to_add: A list of fully qualified annotation names to import.

    Returns:
        The updated Java file content with the new imports added.
    """
    # Ignore any annotations from the same package as the file.
    package_statement_match = package_statement_pattern.search(content)
    package = package_statement_match.group(1)

    imports_to_add = [x for x in imports_to_add if x.rpartition(".")[0] != package]

    # If there are no imports to add then return.
    if not imports_to_add:
        return content

    # Get the map from qualified name to Import for existing imports.
    existing_imports = parse_imports(content)

    # Ignore any existing imports.
    imports_to_add = [x for x in imports_to_add if x not in existing_imports]

    # If there are no imports to add then return.
    if not imports_to_add:
        return content

    inserts = []

    existing_imports = list(existing_imports.values())
    if existing_imports:
        first_position = existing_imports[0].start
        first_prefix = ""
        first_suffix = "\n"
    else:
        first_position = package_statement_match.end() + 1
        first_prefix = "\n"
        first_suffix = ""


    for import_to_add in sorted(imports_to_add):
        import_parts = import_to_add.split(".")
        insert_position = -1
        preceding_separator = ""
        following_separator = ""

        if existing_imports:
            for existing in existing_imports:
                existing_parts = existing.qualified.split(".")

                insert_position_found = False
                for part_index, import_part in enumerate(import_parts):
                    existing_part = existing_parts[part_index]
                    if import_part == existing_part:
                        continue
                    elif import_part < existing_part:
                        insert_position = existing.start
                        insert_position_found = True
                        if part_index == 0:
                            following_separator = "\n"
                        break
                    else:
                        insert_position = existing.end
                        break

                if insert_position_found:
                    break
        else:
            insert_position = first_position
            preceding_separator = first_prefix
            following_separator = first_suffix

        inserts.append(Insert(f"{preceding_separator}import {import_to_add};\n{following_separator}", insert_position))

    # Sort inserts into order.
    inserts = sorted(inserts, key=lambda x: x.position)

    with io.StringIO() as f:
        start = 0
        for insert in inserts:
            insertion_point = insert.position
            # Write everything between the start and this insert.
            f.write(content[start: insertion_point])

            # Write the insert.
            f.write(insert.text)

            # Move to the insertion point.
            start = insertion_point

        # Write the remainder.
        f.write(content[start:])

        return f.getvalue()


def clean_javadoc(file_path):
    """Processes a single Java file, replacing doc-tags and updating imports.

    This function reads the file, finds all Javadoc blocks, cleans them to replace
    doc-tags with annotations, inserts the annotations, and updates the file's imports.

    Args:
        file_path: The path of the Java file to clean.
    """
    with open(file_path, "r", newline="") as f:
        content = f.read()

    content = clean_file_content(content)

    with open(file_path, "w", newline="") as f:
        f.write(content)


def clean_file_content(content) -> str:
    # The set of annotations added.
    annotations_to_import = set()

    with io.StringIO() as f:
        start = 0
        for match in comment_pattern.finditer(content):
            # Write everything between the last match and this one.
            f.write(content[start: match.start(1)])

            # Write the cleaned comment preceding by a blank line.
            comment = match[1]
            (cleaned_comment, insert, import_to_add) = clean_comment(comment)
            if cleaned_comment == comment:
                f.write(comment)
            else:
                f.write("\n")
                f.write(cleaned_comment)
                if insert:
                    f.write(insert)
                if import_to_add:
                    annotations_to_import.add(import_to_add)

            # Move to the end of the comment.
            start = match.end(1)

        # Write the remainder.
        f.write(content[start:])

        content = f.getvalue()

    # If any annotations needed importing then import them.
    if annotations_to_import:
        content = add_imports(content, annotations_to_import)
    return content


def process_files(file_paths):
    """Processes a list of Java files to replace doc-tags with annotations.

    Args:
        file_paths: A list of file paths to process.
    """
    for file_path in file_paths:
        print(f"Processing: {file_path}")
        clean_javadoc(file_path)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: ./replace-doc-tags.py <file1> <file2> ...")
        sys.exit(1)

    process_files(sys.argv[1:])
