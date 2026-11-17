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

import importlib
import textwrap
import unittest

# Import the script with hyphens in the name
replace_doc_tags = importlib.import_module("scripts.replace-doc-tags")
add_imports = replace_doc_tags.add_imports
clean_file_content = replace_doc_tags.clean_file_content


class ReplaceDocTagsTest(unittest.TestCase):

    def check_add_imports(
        self, content: str, imports_to_add: list[str], expected_output: str
    ):
        output = add_imports(textwrap.dedent(content).strip(), imports_to_add)
        self.assertEqual(textwrap.dedent(expected_output).strip(), output)

    def test_add_imports_same_package(self):
        # Should ignore imports from the same package
        content = """
            package com.android.tools.metalava;

            import android.annotation.NonNull;

            public class Test {}
        """
        self.check_add_imports(
            content=content,
            imports_to_add=["com.android.tools.metalava.Something"],
            expected_output=content,
        )

    def test_add_imports_no_imports(self):
        self.check_add_imports(
            content="""
                package com.android.tools.metalava;

                public class Test {}
            """,
            imports_to_add=["android.annotation.Hide"],
            expected_output="""
                package com.android.tools.metalava;

                import android.annotation.Hide;

                public class Test {}
            """,
        )

    def test_add_imports_existing(self):
        self.check_add_imports(
            content="""
                package com.android.tools.metalava;

                import android.annotation.Hide;

                public class Test {}
            """,
            imports_to_add=["android.annotation.Hide"],
            expected_output="""
                package com.android.tools.metalava;

                import android.annotation.Hide;

                public class Test {}
            """,
        )

    def test_add_imports_new_group(self):
        self.check_add_imports(
            content="""
                package com.android.tools.metalava;

                import java.util.List;

                public class Test {}
            """,
            imports_to_add=["android.annotation.Hide"],
            expected_output="""
                package com.android.tools.metalava;

                import android.annotation.Hide;

                import java.util.List;

                public class Test {}
            """,
        )

    def test_add_imports_before_existing(self):
        self.check_add_imports(
            content="""
                package com.android.tools.metalava;

                import android.annotation.NonNull;

                public class Test {}
            """,
            imports_to_add=["android.annotation.Hide"],
            expected_output="""
                package com.android.tools.metalava;

                import android.annotation.Hide;
                import android.annotation.NonNull;

                public class Test {}
            """,
        )

    def test_add_imports_after_existing(self):
        self.check_add_imports(
            content="""
                package com.android.tools.metalava;

                import android.annotation.NonNull;

                public class Test {}
            """,
            imports_to_add=["android.annotation.Removed"],
            expected_output="""
                package com.android.tools.metalava;

                import android.annotation.NonNull;
                import android.annotation.Removed;

                public class Test {}
            """,
        )

    def test_add_imports_between_existing(self):
        self.check_add_imports(
            content="""
                package com.android.tools.metalava;

                import android.annotation.NonNull;
                import android.annotation.SystemApi;

                public class Test {}
            """,
            imports_to_add=["android.annotation.Removed"],
            expected_output="""
                package com.android.tools.metalava;

                import android.annotation.NonNull;
                import android.annotation.Removed;
                import android.annotation.SystemApi;

                public class Test {}
            """,
        )

    def check_clean_content(
            self, content: str, expected_output: str
    ):
        output = clean_file_content(textwrap.dedent(content).strip())
        self.assertEqual(textwrap.dedent(expected_output).strip(), output)

    def test_single_line_block_comment(self):
        self.check_clean_content(
            content="""
                package com.android.tools.metalava;

                import android.annotation.Hide;

                /** @hide */
                public class Test {}
            """,
            expected_output="""
                package com.android.tools.metalava;

                import android.annotation.Hide;

                @Hide
                public class Test {}
            """,
        )

    def test_multi_line_block_comment(self):
        self.check_clean_content(
            content="""
                package com.android.tools.metalava;

                import android.annotation.Hide;

                /**
                 * @hide
                 */
                public class Test {
                    /**
                     *@hide
                     */
                    public static final int FIELD = 1;
                }
            """,
            expected_output="""
                package com.android.tools.metalava;

                import android.annotation.Hide;

                @Hide
                public class Test {

                    @Hide
                    public static final int FIELD = 1;
                }
            """,
        )


if __name__ == "__main__":
    unittest.main()
