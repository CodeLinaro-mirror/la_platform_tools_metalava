/*
 [The "BSD licence"]
 Copyright (c) 2016 Pascal Gruen
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:
 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.
 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.
 3. The name of the author may not be used to endorse or promote products
    derived from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

// $antlr-format alignTrailingComments true, columnLimit 150, maxEmptyLinesToKeep 1, reflowComments false, useTab false
// $antlr-format allowShortRulesOnASingleLine true, allowShortBlocksOnASingleLine true, minEmptyLines 0, alignSemicolons ownLine
// $antlr-format alignColons trailing, singleLineOverrulesHangingColon true, alignLexerCommands true, alignLabels true, alignTrailers true

lexer grammar AntlrJavadocLexer;

@header {
package com.android.tools.metalava.model.source.javadoc;
}

// This document is split into sections one for each mode. It is important that rules are added in
// the correct section otherwise they will be in the wrong mode and not behave as expected. Also,
// order of rules matters when determining matches. Usually the longest match wins but if two
// rules match the same text then the rule listed first wins.

// ============================== BEGIN DEFAULT_MODE ==============================
// This is the default mode that will be used when the lexer first starts. Any rule added to this
// mode should have a matching rule added in the same order in BALANCED_BRACE_MODE.

NEWLINE:
    '\n' (SPACE? '*'+)?
    | '\r\n' (SPACE? '*'+)?
    | '\r' (SPACE? '*'+)?
;

SPACE: (' ' | '\t')+;

// Although `{` are generally treated as TEXT_CONTENT in this mode they have to be matched as
// separate tokens to avoid TEXT_CONTENT matching `{@` instead of INLINE_TAG_START.
TEXTUAL_BRACE_OPEN: '{' -> type(TEXT_CONTENT);

// The start of an inline tag.
INLINE_TAG_START: '{@' ->
    // Start a special mode for processing the INLINE_TAG_NAME. That avoids having to exclude
    // characters in the INLINE_TAG_NAME from TEXT_CONTENT.
    pushMode(INLINE_TAG_MODE);

// The start of an inline if tag.
INLINE_IF_TAG_START: '{@if' ->
    // Start a special mode for processing the contents of the `{@if (expr) {...} (else {...})?}` tag.
    pushMode(INLINE_IF_TAG_MODE);

// General text content. Excludes characters that are handled by one of the other
// tokens above.
TEXT_CONTENT: ~[\n\r\t {]+;

// ============================== END DEFAULT_MODE ==============================

// ============================== BEGIN INLINE_TAG_MODE ==============================
// This mode is in use after `{@`. It switches to INLINE_TAG_CONTENT_MODE after seeing an
// INLINE_TAG_NAME.
mode INLINE_TAG_MODE;

// The inline tag name.
INLINE_TAG_NAME: [a-zA-Z]+ ->
    // Switch to the balanced brace mode. This sets the mode rather than pushes the mode so that
    // when the `}` that closes this tag is encountered it does not come back to this mode but
    // instead goes back to the mode from which this mode was entered, i.e. the default mode.
    mode(BALANCED_BRACE_MODE);

// ============================== END INLINE_TAG_MODE ==============================

// ============================== BEGIN BALANCED_BRACE_MODE ==============================
// This mode is identical to the default mode except that this requires that braces, i.e. `{` and
// `}` are balanced. It must include a matching rule for every rule in the default mode.
//
// This is switched to after seeing an open `{` in some form and it switches back to the
// originating mode after seeing the matching `}`.
mode BALANCED_BRACE_MODE;

// Treat this as the default NEWLINE token
BALANCED_BRACE_NEWLINE: NEWLINE -> type(NEWLINE);

// Treat this as the default SPACE token
BALANCED_BRACE_SPACE: SPACE -> type(SPACE);

// A `{` that must be matched by a following `}`.
BRACE_OPEN: '{' ->
    // Repush balanced mode. That ensures that when the matching `}` pops the mode it is still in
    // balanced mode.
    pushMode(BALANCED_BRACE_MODE);

// A `}` that must match a preceding `{`.
BRACE_CLOSE: '}' ->
    // Pop the mode. If this matches a `{` matched by BALANCED_BRACE_OPEN then it will stay in
    // balanced mode. Otherwise, if this matches the `{` that caused entry to this mode then it
    // will switch back to the original mode.
    popMode;

// The start of an inline tag. Needed to ensure inline tags can contain other inline tags.
BALANCED_INLINE_TAG_START: '{@' ->
    // Start a special mode for processing the INLINE_TAG_NAME. That avoids having to exclude
    // characters in the INLINE_TAG_NAME from TEXT_CONTENT.
    pushMode(INLINE_TAG_MODE),
    // Treat this as the default INLINE_TAG_START token as the parser does not need to be aware of
    // this token.
    type(INLINE_TAG_START);

// The start of an inline if tag. Needed to ensure inline tags can contain if tags.
BALANCED_INLINE_IF_TAG_START: '{@if' ->
    // Start a special mode for processing the contents of the `{@if (expr) {...} (else {...})?}` tag.
    pushMode(INLINE_IF_TAG_MODE),
    // Treat this as the default INLINE_IF_TAG_START token as the parser does not need to be aware of
    // this token.
    type(INLINE_IF_TAG_START);

// Balanced brace text content. Excludes characters that are handled by one of the other
// tokens above.
BALANCED_BRACE_TEXT_CONTENT: ~[\n\r\t {}]+ ->
    // Treat this as the default TEXT_CONTENT token as the parser does not need to be aware of
    // this token.
    type(TEXT_CONTENT);

// ============================== END BALANCED_BRACE_MODE ==============================

// ============================== BEGIN INLINE_IF_TAG_MODE ==============================
mode INLINE_IF_TAG_MODE;

// Ignore any space between tokens in this mode.
IF_TAG_SPACE: (SPACE | NEWLINE)+ -> skip;

// The `(` that begins the conditional expression. The matching `)` is matched in EXPR_MODE.
IF_TAG_PAREN_OPEN: '(' ->
    // Start a special mode for processing expressions.
    pushMode(EXPR_MODE),
    // Treat this as the expression PAREN_OPEN token as the parser does not need to be aware of
    // this token.
    type(PAREN_OPEN);

// The `{` that begins nested content. The matching `}` is matched in BALANCED_BRACE_MODE.
IF_TAG_BRACE_OPEN: '{' ->
    // Start a special mode for processing balanced braces.
    pushMode(BALANCED_BRACE_MODE),
    // Treat this as the default BRACE_OPEN token as the parser does not need to be aware of
    // this token.
    type(BRACE_OPEN);

// The `}` that matches the `{` in `{@if`.
IF_TAG_BRACE_CLOSE: '}' ->
    // Switch back to the original mode.
    popMode,
    // Treat this as the default BRACE_CLOSE token as the parser does not need to be aware of
    // this token.
    type(BRACE_CLOSE);

// The `else` in `{@if (expr) {...} (else {...})?}`.
IF_TAG_ELSE: 'else';

// ============================== END INLINE_IF_TAG_MODE ==============================

// ============================== BEGIN EXPR_MODE ==============================
mode EXPR_MODE;

// Ignore any space between tokens in this mode.
EXPR_SPACE: (SPACE | NEWLINE)+ -> skip;

// An identifier.
IDENTIFIER: [a-zA-Z_][a-zA-Z_0-9]*;

// A . seperator in a qualified name.
DOT: '.';

PAREN_OPEN: '(' -> pushMode(EXPR_MODE);
PAREN_CLOSE: ')' -> popMode;

// ============================== END INLINE_IF_TAG_MODE ==============================

// Add new modes before this line.
// ============================== END OF FILE ==============================
// Do not add any more rules below here as they will appear in whatever mode was created last.
