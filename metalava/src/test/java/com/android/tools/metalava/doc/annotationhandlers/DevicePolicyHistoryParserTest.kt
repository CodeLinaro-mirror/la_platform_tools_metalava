/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.metalava.doc.annotationhandlers

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.stream.XMLStreamException
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DevicePolicyHistoryParserTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun validateAgainstSchema(xml: String) {
        val schemaUrl = DevicePolicyHistory::class.java.getResource("/schemas/policy-history.xsd")
        assertThat(schemaUrl).isNotNull()
        val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
        val schema = schemaFactory.newSchema(schemaUrl)
        schema.newValidator().validate(StreamSource(StringReader(xml)))
    }

    @Test
    fun `Test EMPTY singleton returns null for any policy id`() {
        assertThat(DevicePolicyHistory.EMPTY.getPolicyHistory("")).isNull()
        assertThat(DevicePolicyHistory.EMPTY.getPolicyHistory("CAMERA_DISABLED")).isNull()
        assertThat(DevicePolicyHistory.EMPTY.getPolicyHistory("LOCK_SCREEN_MESSAGE")).isNull()
    }

    @Test
    fun `Test fromFile with nonexistent file returns empty history`() {
        val missingFile = File(temporaryFolder.root, "does-not-exist-policy-history.xml")
        assertThat(missingFile.exists()).isFalse()

        val history = DevicePolicyHistory.fromFile(missingFile)

        assertThat(history).isSameInstanceAs(DevicePolicyHistory.EMPTY)
        assertThat(history.getPolicyHistory("ANY_POLICY")).isNull()
    }

    @Test
    fun `Test fromFile with zero byte empty file returns empty history`() {
        val emptyFile = temporaryFolder.newFile("empty-policy-history.xml")
        assertThat(emptyFile.exists()).isTrue()
        assertThat(emptyFile.length()).isEqualTo(0L)

        val history = DevicePolicyHistory.fromFile(emptyFile)

        assertThat(history).isSameInstanceAs(DevicePolicyHistory.EMPTY)
        assertThat(history.getPolicyHistory("ANY_POLICY")).isNull()
    }

    @Test
    fun `Test fromFile with whitespace only non empty file throws XMLStreamException`() {
        val whitespaceFile = temporaryFolder.newFile("whitespace-policy-history.xml")
        whitespaceFile.writeText("   \n\t  ")
        assertThat(whitespaceFile.length()).isGreaterThan(0L)

        assertThrows(XMLStreamException::class.java) {
            DevicePolicyHistory.fromFile(whitespaceFile)
        }
    }

    @Test
    fun `Test fromString with empty string returns EMPTY`() {
        val history = DevicePolicyHistory.fromString("")

        assertThat(history).isSameInstanceAs(DevicePolicyHistory.EMPTY)
        assertThat(history.getPolicyHistory("POLICY")).isNull()
    }

    @Test
    fun `Test fromString with spaces tabs and newlines returns EMPTY`() {
        val history = DevicePolicyHistory.fromString("  \n\t \r\n  ")

        assertThat(history).isSameInstanceAs(DevicePolicyHistory.EMPTY)
        assertThat(history.getPolicyHistory("POLICY")).isNull()
    }

    @Test
    fun `Test fromReader with empty StringReader throws XMLStreamException`() {
        assertThrows(XMLStreamException::class.java) {
            DevicePolicyHistory.fromReader(StringReader(""))
        }
    }

    @Test
    fun `Test self closing root policy changes element parses empty history`() {
        val xml = "<policy-changes/>"
        validateAgainstSchema(xml)

        val history = DevicePolicyHistory.fromString(xml)

        assertThat(history.getPolicyHistory("MISSING")).isNull()
        assertThat(history.getPolicyHistory("")).isNull()
    }

    @Test
    fun `Test empty root policy changes element with closing tag parses empty history`() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!-- Empty policy history -->
            <policy-changes>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val history = DevicePolicyHistory.fromString(xml)

        assertThat(history.getPolicyHistory("CAMERA_DISABLED")).isNull()
    }

    @Test
    fun `Test policy element with no deltas parses empty flagged and empty released list`() {
        val xml =
            """
            <policy-changes>
              <policy id="EMPTY_POLICY"/>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val history = DevicePolicyHistory.fromString(xml)
        val policy = history.getPolicyHistory("EMPTY_POLICY")

        assertThat(policy).isNotNull()
        assertThat(policy?.id).isEqualTo("EMPTY_POLICY")
        assertThat(policy?.flaggedDelta).isNull()
        assertThat(policy?.releasedDeltas).isEmpty()
        assertThat(history.getPolicyHistory("empty_policy")).isNull()
        assertThat(history.getPolicyHistory("EMPTY_POLICY ")).isNull()
    }

    @Test
    fun `Test getPolicyHistory is case sensitive and does not match substrings`() {
        val xml =
            """
            <policy-changes>
              <policy id="MY_POLICY">
                <released since="36.0"/>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val history = DevicePolicyHistory.fromString(xml)

        assertThat(history.getPolicyHistory("MY_POLICY")).isNotNull()
        assertThat(history.getPolicyHistory("my_policy")).isNull()
        assertThat(history.getPolicyHistory("My_Policy")).isNull()
        assertThat(history.getPolicyHistory("MY_POLIC")).isNull()
        assertThat(history.getPolicyHistory("MY_POLICY_2")).isNull()
        assertThat(history.getPolicyHistory(" MY_POLICY")).isNull()
        assertThat(history.getPolicyHistory("MY_POLICY ")).isNull()
    }

    @Test
    fun `Test allowedScope USER change`() {
        val xml =
            """
            <policy-changes>
              <policy id="SCOPE_POLICY_USER">
                <released since="35.0">
                  <change field="allowedScope" previous="">USER</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val history = DevicePolicyHistory.fromString(xml)
        val policy = history.getPolicyHistory("SCOPE_POLICY_USER")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "SCOPE_POLICY_USER",
                    flaggedDelta = null,
                    releasedDeltas =
                        listOf(
                            PolicyDelta.Released(
                                since = "35.0",
                                changes = listOf(PolicyChange.AllowedScope(PolicyScope.USER)),
                            )
                        ),
                )
            )
    }

    @Test
    fun `Test allowedScope DEVICE change`() {
        val xml =
            """
            <policy-changes>
              <policy id="SCOPE_POLICY_DEVICE">
                <released since="36.0">
                  <change field="allowedScope" previous="">DEVICE</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val history = DevicePolicyHistory.fromString(xml)
        val policy = history.getPolicyHistory("SCOPE_POLICY_DEVICE")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "SCOPE_POLICY_DEVICE",
                    flaggedDelta = null,
                    releasedDeltas =
                        listOf(
                            PolicyDelta.Released(
                                since = "36.0",
                                changes = listOf(PolicyChange.AllowedScope(PolicyScope.DEVICE)),
                            )
                        ),
                )
            )
    }

    @Test
    fun `Test allowedScope PARENT_USER change`() {
        val xml =
            """
            <policy-changes>
              <policy id="SCOPE_POLICY_PARENT_USER">
                <released since="36.1">
                  <change field="allowedScope" previous="">PARENT_USER</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val history = DevicePolicyHistory.fromString(xml)
        val policy = history.getPolicyHistory("SCOPE_POLICY_PARENT_USER")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "SCOPE_POLICY_PARENT_USER",
                    flaggedDelta = null,
                    releasedDeltas =
                        listOf(
                            PolicyDelta.Released(
                                since = "36.1",
                                changes =
                                    listOf(PolicyChange.AllowedScope(PolicyScope.PARENT_USER)),
                            )
                        ),
                )
            )
    }

    @Test
    fun `Test allowedScope LOGIN_SCREEN change`() {
        val xml =
            """
            <policy-changes>
              <policy id="SCOPE_POLICY_LOGIN_SCREEN">
                <flagged flag="android.app.admin.flags.login_screen_policy">
                  <change field="allowedScope" previous="">LOGIN_SCREEN</change>
                </flagged>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val history = DevicePolicyHistory.fromString(xml)
        val policy = history.getPolicyHistory("SCOPE_POLICY_LOGIN_SCREEN")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "SCOPE_POLICY_LOGIN_SCREEN",
                    flaggedDelta =
                        PolicyDelta.Flagged(
                            flag = "android.app.admin.flags.login_screen_policy",
                            changes = listOf(PolicyChange.AllowedScope(PolicyScope.LOGIN_SCREEN)),
                        ),
                    releasedDeltas = emptyList(),
                )
            )
    }

    @Test
    fun `Test allowedDpcType DEVICE_OWNER change`() {
        val xml =
            """
            <policy-changes>
              <policy id="DPC_DEVICE_OWNER">
                <released since="35.0">
                  <change field="allowedDpcType" previous="">DEVICE_OWNER</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val policy = DevicePolicyHistory.fromString(xml).getPolicyHistory("DPC_DEVICE_OWNER")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "DPC_DEVICE_OWNER",
                    flaggedDelta = null,
                    releasedDeltas =
                        listOf(
                            PolicyDelta.Released(
                                since = "35.0",
                                changes =
                                    listOf(PolicyChange.AllowedDpc(AllowedDpcType.DEVICE_OWNER)),
                            )
                        ),
                )
            )
    }

    @Test
    fun `Test allowedDpcType MANAGED_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE change`() {
        val xml =
            """
            <policy-changes>
              <policy id="DPC_COPE">
                <released since="35.0">
                  <change field="allowedDpcType" previous="">MANAGED_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val policy = DevicePolicyHistory.fromString(xml).getPolicyHistory("DPC_COPE")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "DPC_COPE",
                    flaggedDelta = null,
                    releasedDeltas =
                        listOf(
                            PolicyDelta.Released(
                                since = "35.0",
                                changes =
                                    listOf(
                                        PolicyChange.AllowedDpc(
                                            AllowedDpcType
                                                .MANAGED_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE
                                        )
                                    ),
                            )
                        ),
                )
            )
    }

    @Test
    fun `Test allowedDpcType MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE change`() {
        val xml =
            """
            <policy-changes>
              <policy id="DPC_BYOD">
                <released since="35.0">
                  <change field="allowedDpcType" previous="">MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val policy = DevicePolicyHistory.fromString(xml).getPolicyHistory("DPC_BYOD")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "DPC_BYOD",
                    flaggedDelta = null,
                    releasedDeltas =
                        listOf(
                            PolicyDelta.Released(
                                since = "35.0",
                                changes =
                                    listOf(
                                        PolicyChange.AllowedDpc(
                                            AllowedDpcType
                                                .MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE
                                        )
                                    ),
                            )
                        ),
                )
            )
    }

    @Test
    fun `Test allowedDpcType UNAFFILIATED_FULL_USER_PROFILE_OWNER change`() {
        val xml =
            """
            <policy-changes>
              <policy id="DPC_UNAFFILIATED_FUPO">
                <released since="35.0">
                  <change field="allowedDpcType" previous="">UNAFFILIATED_FULL_USER_PROFILE_OWNER</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val policy = DevicePolicyHistory.fromString(xml).getPolicyHistory("DPC_UNAFFILIATED_FUPO")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "DPC_UNAFFILIATED_FUPO",
                    flaggedDelta = null,
                    releasedDeltas =
                        listOf(
                            PolicyDelta.Released(
                                since = "35.0",
                                changes =
                                    listOf(
                                        PolicyChange.AllowedDpc(
                                            AllowedDpcType.UNAFFILIATED_FULL_USER_PROFILE_OWNER
                                        )
                                    ),
                            )
                        ),
                )
            )
    }

    @Test
    fun `Test allowedDpcType FINANCED_DEVICE_OWNER change`() {
        val xml =
            """
            <policy-changes>
              <policy id="DPC_FINANCED">
                <released since="35.0">
                  <change field="allowedDpcType" previous="">FINANCED_DEVICE_OWNER</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val policy = DevicePolicyHistory.fromString(xml).getPolicyHistory("DPC_FINANCED")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "DPC_FINANCED",
                    flaggedDelta = null,
                    releasedDeltas =
                        listOf(
                            PolicyDelta.Released(
                                since = "35.0",
                                changes =
                                    listOf(
                                        PolicyChange.AllowedDpc(
                                            AllowedDpcType.FINANCED_DEVICE_OWNER
                                        )
                                    ),
                            )
                        ),
                )
            )
    }

    @Test
    fun `Test allowedDpcType PROFILE_OWNER_ON_USER_0 change`() {
        val xml =
            """
            <policy-changes>
              <policy id="DPC_PO_USER_0">
                <released since="35.0">
                  <change field="allowedDpcType" previous="">PROFILE_OWNER_ON_USER_0</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val policy = DevicePolicyHistory.fromString(xml).getPolicyHistory("DPC_PO_USER_0")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "DPC_PO_USER_0",
                    flaggedDelta = null,
                    releasedDeltas =
                        listOf(
                            PolicyDelta.Released(
                                since = "35.0",
                                changes =
                                    listOf(
                                        PolicyChange.AllowedDpc(
                                            AllowedDpcType.PROFILE_OWNER_ON_USER_0
                                        )
                                    ),
                            )
                        ),
                )
            )
    }

    @Test
    fun `Test allowedDpcType AFFILIATED_FULL_USER_PROFILE_OWNER change`() {
        val xml =
            """
            <policy-changes>
              <policy id="DPC_AFFILIATED_FUPO">
                <released since="35.0">
                  <change field="allowedDpcType" previous="">AFFILIATED_FULL_USER_PROFILE_OWNER</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val policy = DevicePolicyHistory.fromString(xml).getPolicyHistory("DPC_AFFILIATED_FUPO")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "DPC_AFFILIATED_FUPO",
                    flaggedDelta = null,
                    releasedDeltas =
                        listOf(
                            PolicyDelta.Released(
                                since = "35.0",
                                changes =
                                    listOf(
                                        PolicyChange.AllowedDpc(
                                            AllowedDpcType.AFFILIATED_FULL_USER_PROFILE_OWNER
                                        )
                                    ),
                            )
                        ),
                )
            )
    }

    @Test
    fun `Test requiredCrossUserPermission added with empty previous attribute`() {
        val xml =
            """
            <policy-changes>
              <policy id="PERM_ADDED_EMPTY_PREV">
                <released since="36.0">
                  <change field="requiredCrossUserPermission" previous="">android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val policy = DevicePolicyHistory.fromString(xml).getPolicyHistory("PERM_ADDED_EMPTY_PREV")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "PERM_ADDED_EMPTY_PREV",
                    flaggedDelta = null,
                    releasedDeltas =
                        listOf(
                            PolicyDelta.Released(
                                since = "36.0",
                                changes =
                                    listOf(
                                        PolicyChange.CrossUserPermission(
                                            previous = null,
                                            newValue =
                                                "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS",
                                        )
                                    ),
                            )
                        ),
                )
            )
    }

    @Test
    fun `Test requiredCrossUserPermission added with omitted previous attribute`() {
        val xml =
            """
            <policy-changes>
              <policy id="PERM_ADDED_NO_PREV">
                <released since="36.0">
                  <change field="requiredCrossUserPermission">android.permission.MANAGE_DEVICE_POLICY_CAMERA</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val policy = DevicePolicyHistory.fromString(xml).getPolicyHistory("PERM_ADDED_NO_PREV")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "PERM_ADDED_NO_PREV",
                    flaggedDelta = null,
                    releasedDeltas =
                        listOf(
                            PolicyDelta.Released(
                                since = "36.0",
                                changes =
                                    listOf(
                                        PolicyChange.CrossUserPermission(
                                            previous = null,
                                            newValue =
                                                "android.permission.MANAGE_DEVICE_POLICY_CAMERA",
                                        )
                                    ),
                            )
                        ),
                )
            )
    }

    @Test
    fun `Test requiredCrossUserPermission replaced from previous permission`() {
        val xml =
            """
            <policy-changes>
              <policy id="PERM_REPLACED">
                <released since="36.0">
                  <change field="requiredCrossUserPermission" previous="android.permission.OLD_PERM">android.permission.NEW_PERM</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val policy = DevicePolicyHistory.fromString(xml).getPolicyHistory("PERM_REPLACED")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "PERM_REPLACED",
                    flaggedDelta = null,
                    releasedDeltas =
                        listOf(
                            PolicyDelta.Released(
                                since = "36.0",
                                changes =
                                    listOf(
                                        PolicyChange.CrossUserPermission(
                                            previous = "android.permission.OLD_PERM",
                                            newValue = "android.permission.NEW_PERM",
                                        )
                                    ),
                            )
                        ),
                )
            )
    }

    @Test
    fun `Test requiredCrossUserPermission removed with self closing change tag`() {
        val xml =
            """
            <policy-changes>
              <policy id="PERM_REMOVED_SELF_CLOSING">
                <released since="36.0">
                  <change field="requiredCrossUserPermission" previous="android.permission.OLD_PERM"/>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val policy =
            DevicePolicyHistory.fromString(xml).getPolicyHistory("PERM_REMOVED_SELF_CLOSING")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "PERM_REMOVED_SELF_CLOSING",
                    flaggedDelta = null,
                    releasedDeltas =
                        listOf(
                            PolicyDelta.Released(
                                since = "36.0",
                                changes =
                                    listOf(
                                        PolicyChange.CrossUserPermission(
                                            previous = "android.permission.OLD_PERM",
                                            newValue = null,
                                        )
                                    ),
                            )
                        ),
                )
            )
    }

    @Test
    fun `Test requiredCrossUserPermission removed with whitespace only body becomes null`() {
        val xml =
            """
            <policy-changes>
              <policy id="PERM_REMOVED_WHITESPACE">
                <released since="36.0">
                  <change field="requiredCrossUserPermission" previous="android.permission.OLD_PERM">
                    </change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val policy = DevicePolicyHistory.fromString(xml).getPolicyHistory("PERM_REMOVED_WHITESPACE")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "PERM_REMOVED_WHITESPACE",
                    flaggedDelta = null,
                    releasedDeltas =
                        listOf(
                            PolicyDelta.Released(
                                since = "36.0",
                                changes =
                                    listOf(
                                        PolicyChange.CrossUserPermission(
                                            previous = "android.permission.OLD_PERM",
                                            newValue = null,
                                        )
                                    ),
                            )
                        ),
                )
            )
    }

    @Test
    fun `Test requiredCrossUserPermission with both previous and new value empty`() {
        val xml =
            """
            <policy-changes>
              <policy id="PERM_BOTH_EMPTY">
                <released since="36.0">
                  <change field="requiredCrossUserPermission" previous=""></change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val policy = DevicePolicyHistory.fromString(xml).getPolicyHistory("PERM_BOTH_EMPTY")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "PERM_BOTH_EMPTY",
                    flaggedDelta = null,
                    releasedDeltas =
                        listOf(
                            PolicyDelta.Released(
                                since = "36.0",
                                changes =
                                    listOf(
                                        PolicyChange.CrossUserPermission(
                                            previous = null,
                                            newValue = null,
                                        )
                                    ),
                            )
                        ),
                )
            )
    }

    @Test
    fun `Test enumValue positive zero negative max and min integer bounds`() {
        val xml =
            """
            <policy-changes>
              <policy id="ENUM_VALUES">
                <released since="36.0">
                  <change field="enumValue" previous="">0</change>
                  <change field="enumValue" previous="">1</change>
                  <change field="enumValue" previous="">-1</change>
                  <change field="enumValue" previous="">2147483647</change>
                  <change field="enumValue" previous="">-2147483648</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val policy = DevicePolicyHistory.fromString(xml).getPolicyHistory("ENUM_VALUES")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "ENUM_VALUES",
                    flaggedDelta = null,
                    releasedDeltas =
                        listOf(
                            PolicyDelta.Released(
                                since = "36.0",
                                changes =
                                    listOf(
                                        PolicyChange.EnumValue(0),
                                        PolicyChange.EnumValue(1),
                                        PolicyChange.EnumValue(-1),
                                        PolicyChange.EnumValue(Int.MAX_VALUE),
                                        PolicyChange.EnumValue(Int.MIN_VALUE),
                                    ),
                            )
                        ),
                )
            )
    }

    @Test
    fun `Test change element text whitespace is trimmed across all field types`() {
        val xml =
            """
            <policy-changes>
              <policy id="TRIM_POLICY">
                <flagged flag="test.flag">
                  <change field="allowedScope" previous="">
                    USER
                  </change>
                  <change field="allowedDpcType" previous="">  DEVICE_OWNER	</change>
                  <change field="requiredCrossUserPermission" previous="android.permission.A">
                    android.permission.B
                  </change>
                  <change field="enumValue" previous="">  42  </change>
                </flagged>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val policy = DevicePolicyHistory.fromString(xml).getPolicyHistory("TRIM_POLICY")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "TRIM_POLICY",
                    flaggedDelta =
                        PolicyDelta.Flagged(
                            flag = "test.flag",
                            changes =
                                listOf(
                                    PolicyChange.AllowedScope(PolicyScope.USER),
                                    PolicyChange.AllowedDpc(AllowedDpcType.DEVICE_OWNER),
                                    PolicyChange.CrossUserPermission(
                                        previous = "android.permission.A",
                                        newValue = "android.permission.B",
                                    ),
                                    PolicyChange.EnumValue(42),
                                ),
                        ),
                    releasedDeltas = emptyList(),
                )
            )
    }

    @Test
    fun `Test change element supports CDATA sections and comments between elements`() {
        val xml =
            """
            <policy-changes>
              <!-- First policy comment -->
              <policy id="CDATA_POLICY">
                <!-- Flagged block -->
                <flagged flag="test.cdata.flag">
                  <!-- Scope change -->
                  <change field="allowedScope" previous=""><![CDATA[DEVICE]]></change>
                  <change field="enumValue" previous=""><![CDATA[7]]></change>
                </flagged>
                <!-- Released block -->
                <released since="36.0">
                  <change field="requiredCrossUserPermission" previous=""><![CDATA[android.permission.TEST]]></change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val policy = DevicePolicyHistory.fromString(xml).getPolicyHistory("CDATA_POLICY")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "CDATA_POLICY",
                    flaggedDelta =
                        PolicyDelta.Flagged(
                            flag = "test.cdata.flag",
                            changes =
                                listOf(
                                    PolicyChange.AllowedScope(PolicyScope.DEVICE),
                                    PolicyChange.EnumValue(7),
                                ),
                        ),
                    releasedDeltas =
                        listOf(
                            PolicyDelta.Released(
                                since = "36.0",
                                changes =
                                    listOf(
                                        PolicyChange.CrossUserPermission(
                                            previous = null,
                                            newValue = "android.permission.TEST",
                                        )
                                    ),
                            )
                        ),
                )
            )
    }

    @Test
    fun `Test attributes in reversed order still parse accurately`() {
        val xml =
            """
            <policy-changes>
              <policy id="ATTR_ORDER">
                <released since="36.0">
                  <change previous="old.perm" field="requiredCrossUserPermission">new.perm</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val policy = DevicePolicyHistory.fromString(xml).getPolicyHistory("ATTR_ORDER")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "ATTR_ORDER",
                    flaggedDelta = null,
                    releasedDeltas =
                        listOf(
                            PolicyDelta.Released(
                                since = "36.0",
                                changes =
                                    listOf(
                                        PolicyChange.CrossUserPermission(
                                            previous = "old.perm",
                                            newValue = "new.perm",
                                        )
                                    ),
                            )
                        ),
                )
            )
    }

    @Test
    fun `Test policy with empty flagged and empty released blocks`() {
        val xml =
            """
            <policy-changes>
              <policy id="EMPTY_DELTAS">
                <flagged flag="android.app.admin.flags.empty_flag"/>
                <released since="36.0"/>
                <released since="35.0"></released>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val policy = DevicePolicyHistory.fromString(xml).getPolicyHistory("EMPTY_DELTAS")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "EMPTY_DELTAS",
                    flaggedDelta =
                        PolicyDelta.Flagged(
                            flag = "android.app.admin.flags.empty_flag",
                            changes = emptyList(),
                        ),
                    releasedDeltas =
                        listOf(
                            PolicyDelta.Released(since = "36.0", changes = emptyList()),
                            PolicyDelta.Released(since = "35.0", changes = emptyList()),
                        ),
                )
            )
    }

    @Test
    fun `Test policy with flagged and multiple released deltas preserves release order`() {
        val xml =
            """
            <policy-changes>
              <policy id="MULTI_RELEASE_POLICY">
                <flagged flag="android.app.admin.flags.latest">
                  <change field="allowedScope" previous="">LOGIN_SCREEN</change>
                  <change field="enumValue" previous="">5</change>
                </flagged>
                <released since="37.0">
                  <change field="allowedDpcType" previous="">FINANCED_DEVICE_OWNER</change>
                </released>
                <released since="36.1">
                  <change field="requiredCrossUserPermission" previous="perm.v1">perm.v2</change>
                </released>
                <released since="36.0">
                  <change field="allowedScope" previous="">USER</change>
                  <change field="allowedDpcType" previous="">DEVICE_OWNER</change>
                </released>
                <released since="35.0">
                  <change field="enumValue" previous="">2</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val policy = DevicePolicyHistory.fromString(xml).getPolicyHistory("MULTI_RELEASE_POLICY")

        assertThat(policy).isNotNull()
        assertThat(policy?.id).isEqualTo("MULTI_RELEASE_POLICY")
        assertThat(policy?.flaggedDelta)
            .isEqualTo(
                PolicyDelta.Flagged(
                    flag = "android.app.admin.flags.latest",
                    changes =
                        listOf(
                            PolicyChange.AllowedScope(PolicyScope.LOGIN_SCREEN),
                            PolicyChange.EnumValue(5),
                        ),
                )
            )
        assertThat(policy?.releasedDeltas)
            .containsExactly(
                PolicyDelta.Released(
                    since = "37.0",
                    changes = listOf(PolicyChange.AllowedDpc(AllowedDpcType.FINANCED_DEVICE_OWNER)),
                ),
                PolicyDelta.Released(
                    since = "36.1",
                    changes =
                        listOf(
                            PolicyChange.CrossUserPermission(
                                previous = "perm.v1",
                                newValue = "perm.v2",
                            )
                        ),
                ),
                PolicyDelta.Released(
                    since = "36.0",
                    changes =
                        listOf(
                            PolicyChange.AllowedScope(PolicyScope.USER),
                            PolicyChange.AllowedDpc(AllowedDpcType.DEVICE_OWNER),
                        ),
                ),
                PolicyDelta.Released(since = "35.0", changes = listOf(PolicyChange.EnumValue(2))),
            )
            .inOrder()
    }

    @Test
    fun `Test multiple policies parsed fromFile and fromReader`() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <policy-changes>
              <policy id="CAMERA_DISABLED">
                <flagged flag="android.app.admin.flags.camera_scope">
                  <change field="allowedScope" previous="">PARENT_USER</change>
                </flagged>
                <released since="36.0">
                  <change field="allowedDpcType" previous="">PROFILE_OWNER_ON_USER_0</change>
                </released>
              </policy>
              <policy id="LOCK_SCREEN_MESSAGE">
                <released since="35.0">
                  <change field="requiredCrossUserPermission" previous="">android.permission.LOCK</change>
                </released>
              </policy>
              <policy id="AUTO_TIME_ZONE">
                <released since="36.0">
                  <change field="enumValue" previous="">3</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val file = temporaryFolder.newFile("policy-history.xml")
        file.writeText(xml)

        val fromFileHistory = DevicePolicyHistory.fromFile(file)
        val fromReaderHistory = DevicePolicyHistory.fromReader(StringReader(xml))

        for (history in listOf(fromFileHistory, fromReaderHistory)) {
            assertThat(history.getPolicyHistory("CAMERA_DISABLED"))
                .isEqualTo(
                    PolicyHistory(
                        id = "CAMERA_DISABLED",
                        flaggedDelta =
                            PolicyDelta.Flagged(
                                flag = "android.app.admin.flags.camera_scope",
                                changes =
                                    listOf(PolicyChange.AllowedScope(PolicyScope.PARENT_USER)),
                            ),
                        releasedDeltas =
                            listOf(
                                PolicyDelta.Released(
                                    since = "36.0",
                                    changes =
                                        listOf(
                                            PolicyChange.AllowedDpc(
                                                AllowedDpcType.PROFILE_OWNER_ON_USER_0
                                            )
                                        ),
                                )
                            ),
                    )
                )
            assertThat(history.getPolicyHistory("LOCK_SCREEN_MESSAGE"))
                .isEqualTo(
                    PolicyHistory(
                        id = "LOCK_SCREEN_MESSAGE",
                        flaggedDelta = null,
                        releasedDeltas =
                            listOf(
                                PolicyDelta.Released(
                                    since = "35.0",
                                    changes =
                                        listOf(
                                            PolicyChange.CrossUserPermission(
                                                previous = null,
                                                newValue = "android.permission.LOCK",
                                            )
                                        ),
                                )
                            ),
                    )
                )
            assertThat(history.getPolicyHistory("AUTO_TIME_ZONE"))
                .isEqualTo(
                    PolicyHistory(
                        id = "AUTO_TIME_ZONE",
                        flaggedDelta = null,
                        releasedDeltas =
                            listOf(
                                PolicyDelta.Released(
                                    since = "36.0",
                                    changes = listOf(PolicyChange.EnumValue(3)),
                                )
                            ),
                    )
                )
            assertThat(history.getPolicyHistory("OTHER_POLICY")).isNull()
        }
    }

    @Test
    fun `Test duplicate policy id overwrites earlier policy entry`() {
        val xml =
            """
            <policy-changes>
              <policy id="DUPLICATE_POLICY">
                <flagged flag="old.flag">
                  <change field="allowedScope" previous="">USER</change>
                </flagged>
                <released since="35.0">
                  <change field="enumValue" previous="">1</change>
                </released>
              </policy>
              <policy id="DUPLICATE_POLICY">
                <released since="36.0">
                  <change field="allowedScope" previous="">DEVICE</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()

        val policy = DevicePolicyHistory.fromString(xml).getPolicyHistory("DUPLICATE_POLICY")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "DUPLICATE_POLICY",
                    flaggedDelta = null,
                    releasedDeltas =
                        listOf(
                            PolicyDelta.Released(
                                since = "36.0",
                                changes = listOf(PolicyChange.AllowedScope(PolicyScope.DEVICE)),
                            )
                        ),
                )
            )
    }

    @Test
    fun `Test duplicate flagged elements within one policy keeps last flagged delta`() {
        val xml =
            """
            <policy-changes>
              <policy id="DUP_FLAGGED">
                <flagged flag="first.flag">
                  <change field="allowedScope" previous="">USER</change>
                </flagged>
                <flagged flag="second.flag">
                  <change field="allowedScope" previous="">DEVICE</change>
                </flagged>
              </policy>
            </policy-changes>
            """
                .trimIndent()

        val policy = DevicePolicyHistory.fromString(xml).getPolicyHistory("DUP_FLAGGED")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "DUP_FLAGGED",
                    flaggedDelta =
                        PolicyDelta.Flagged(
                            flag = "second.flag",
                            changes = listOf(PolicyChange.AllowedScope(PolicyScope.DEVICE)),
                        ),
                    releasedDeltas = emptyList(),
                )
            )
    }

    @Test
    fun `Test duplicate released elements with same since are both preserved in order`() {
        val xml =
            """
            <policy-changes>
              <policy id="DUP_RELEASED">
                <released since="36.0">
                  <change field="allowedScope" previous="">USER</change>
                </released>
                <released since="36.0">
                  <change field="allowedScope" previous="">DEVICE</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val policy = DevicePolicyHistory.fromString(xml).getPolicyHistory("DUP_RELEASED")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "DUP_RELEASED",
                    flaggedDelta = null,
                    releasedDeltas =
                        listOf(
                            PolicyDelta.Released(
                                since = "36.0",
                                changes = listOf(PolicyChange.AllowedScope(PolicyScope.USER)),
                            ),
                            PolicyDelta.Released(
                                since = "36.0",
                                changes = listOf(PolicyChange.AllowedScope(PolicyScope.DEVICE)),
                            ),
                        ),
                )
            )
    }

    @Test
    fun `Test duplicate identical change elements inside one delta are both preserved`() {
        val xml =
            """
            <policy-changes>
              <policy id="DUP_CHANGE">
                <released since="36.0">
                  <change field="enumValue" previous="">1</change>
                  <change field="enumValue" previous="">1</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()
        validateAgainstSchema(xml)

        val policy = DevicePolicyHistory.fromString(xml).getPolicyHistory("DUP_CHANGE")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "DUP_CHANGE",
                    flaggedDelta = null,
                    releasedDeltas =
                        listOf(
                            PolicyDelta.Released(
                                since = "36.0",
                                changes =
                                    listOf(PolicyChange.EnumValue(1), PolicyChange.EnumValue(1)),
                            )
                        ),
                )
            )
    }

    @Test
    fun `Test unknown elements inside policy and delta are ignored`() {
        val xml =
            """
            <policy-changes>
              <ignored-top-level>
                <other/>
              </ignored-top-level>
              <policy id="WITH_UNKNOWN_CHILDREN">
                <unknown-in-policy foo="bar">
                  <nested>text</nested>
                </unknown-in-policy>
                <flagged flag="test.flag">
                  <unknown-in-flagged>
                    <inner/>
                  </unknown-in-flagged>
                  <change field="allowedScope" previous="">USER</change>
                </flagged>
                <released since="36.0">
                  <unknown-in-released/>
                  <change field="enumValue" previous="">9</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()

        val history = DevicePolicyHistory.fromString(xml)

        assertThat(history.getPolicyHistory("WITH_UNKNOWN_CHILDREN"))
            .isEqualTo(
                PolicyHistory(
                    id = "WITH_UNKNOWN_CHILDREN",
                    flaggedDelta =
                        PolicyDelta.Flagged(
                            flag = "test.flag",
                            changes = listOf(PolicyChange.AllowedScope(PolicyScope.USER)),
                        ),
                    releasedDeltas =
                        listOf(
                            PolicyDelta.Released(
                                since = "36.0",
                                changes = listOf(PolicyChange.EnumValue(9)),
                            )
                        ),
                )
            )
    }

    @Test
    fun `Test missing id flag and since attributes default to empty strings`() {
        val xml =
            """
            <policy-changes>
              <policy>
                <flagged>
                  <change field="allowedScope">USER</change>
                </flagged>
                <released>
                  <change field="enumValue">4</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()

        val policy = DevicePolicyHistory.fromString(xml).getPolicyHistory("")

        assertThat(policy)
            .isEqualTo(
                PolicyHistory(
                    id = "",
                    flaggedDelta =
                        PolicyDelta.Flagged(
                            flag = "",
                            changes = listOf(PolicyChange.AllowedScope(PolicyScope.USER)),
                        ),
                    releasedDeltas =
                        listOf(
                            PolicyDelta.Released(
                                since = "",
                                changes = listOf(PolicyChange.EnumValue(4)),
                            )
                        ),
                )
            )
    }

    @Test
    fun `Test unexpected change field name throws IllegalStateException`() {
        val xml =
            """
            <policy-changes>
              <policy id="BAD_FIELD">
                <released since="36.0">
                  <change field="affectedResource" previous="">DEVICE_WIDE</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()

        val exception =
            assertThrows(IllegalStateException::class.java) { DevicePolicyHistory.fromString(xml) }
        assertThat(exception).hasMessageThat().isEqualTo("Unexpected field affectedResource")
    }

    @Test
    fun `Test missing field attribute on change throws IllegalStateException`() {
        val xml =
            """
            <policy-changes>
              <policy id="MISSING_FIELD">
                <released since="36.0">
                  <change previous="">USER</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()

        val exception =
            assertThrows(IllegalStateException::class.java) { DevicePolicyHistory.fromString(xml) }
        assertThat(exception).hasMessageThat().isEqualTo("Unexpected field ")
    }

    @Test
    fun `Test invalid allowedScope value throws IllegalArgumentException`() {
        val xml =
            """
            <policy-changes>
              <policy id="BAD_SCOPE">
                <released since="36.0">
                  <change field="allowedScope" previous="">INVALID_SCOPE</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()

        assertThrows(IllegalArgumentException::class.java) { DevicePolicyHistory.fromString(xml) }
    }

    @Test
    fun `Test lowercase allowedScope value throws IllegalArgumentException`() {
        val xml =
            """
            <policy-changes>
              <policy id="LOWERCASE_SCOPE">
                <released since="36.0">
                  <change field="allowedScope" previous="">user</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()

        assertThrows(IllegalArgumentException::class.java) { DevicePolicyHistory.fromString(xml) }
    }

    @Test
    fun `Test invalid allowedDpcType value throws IllegalArgumentException`() {
        val xml =
            """
            <policy-changes>
              <policy id="BAD_DPC">
                <released since="36.0">
                  <change field="allowedDpcType" previous="">PROFILE_OWNER_ON_USER0</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()

        assertThrows(IllegalArgumentException::class.java) { DevicePolicyHistory.fromString(xml) }
    }

    @Test
    fun `Test non numeric enumValue throws NumberFormatException`() {
        val xml =
            """
            <policy-changes>
              <policy id="BAD_ENUM_INT">
                <released since="36.0">
                  <change field="enumValue" previous="">NOT_AN_INT</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()

        assertThrows(NumberFormatException::class.java) { DevicePolicyHistory.fromString(xml) }
    }

    @Test
    fun `Test overflowing enumValue throws NumberFormatException`() {
        val xml =
            """
            <policy-changes>
              <policy id="OVERFLOW_ENUM_INT">
                <released since="36.0">
                  <change field="enumValue" previous="">99999999999999999999</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()

        assertThrows(NumberFormatException::class.java) { DevicePolicyHistory.fromString(xml) }
    }

    @Test
    fun `Test empty enumValue throws NumberFormatException`() {
        val xml =
            """
            <policy-changes>
              <policy id="EMPTY_ENUM_INT">
                <released since="36.0">
                  <change field="enumValue" previous=""></change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()

        assertThrows(NumberFormatException::class.java) { DevicePolicyHistory.fromString(xml) }
    }

    @Test
    fun `Test malformed unclosed tag throws XMLStreamException`() {
        val xml =
            """
            <policy-changes>
              <policy id="UNCLOSED">
                <released since="36.0">
                  <change field="enumValue" previous="">1</change>
                </released>
            </policy-changes>
            """
                .trimIndent()

        assertThrows(XMLStreamException::class.java) { DevicePolicyHistory.fromString(xml) }
    }

    @Test
    fun `Test DTD declaration is rejected when SUPPORT_DTD is false`() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE policy-changes [
              <!ENTITY test "USER">
            ]>
            <policy-changes>
              <policy id="XXE_TEST">
                <released since="36.0">
                  <change field="allowedScope" previous="">&test;</change>
                </released>
              </policy>
            </policy-changes>
            """
                .trimIndent()

        assertThrows(XMLStreamException::class.java) { DevicePolicyHistory.fromString(xml) }
    }
}
