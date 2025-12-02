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

// $antlr-format alignTrailingComments true, columnLimit 150, minEmptyLines 1, maxEmptyLinesToKeep 1, reflowComments false, useTab false
// $antlr-format allowShortRulesOnASingleLine false, allowShortBlocksOnASingleLine true, alignSemicolons hanging, alignColons hanging

parser grammar AntlrJavadocParser;

@header {
package com.android.tools.metalava.model.source.javadoc;
}

options {
    tokenVocab = AntlrJavadocLexer;
}

description
    : descriptionLine (newline+ descriptionLine)* EOF
    ;

descriptionLine
    : descriptionLineElement*
    ;

descriptionLineElement
    : inlineTag
    | inlineIfTag
    | textContent
    ;

textContent
    : TEXT_CONTENT
    | SPACE
    ;

// Newline requires special handling when constructing the model.
newline
    : NEWLINE
    ;

inlineTag
    // Make BRACE_CLOSE optional to support inline tags without a closing brace.
    // TODO(b/429965593): Fix broken javadoc and make BRACE_CLOSE required.
    : INLINE_TAG_START INLINE_TAG_NAME SPACE* inlineTagContent? BRACE_CLOSE?
    ;

inlineTagContent
    : braceContent+
    ;

braceExpression
    : BRACE_OPEN braceContent* BRACE_CLOSE
    ;

braceContent
    : braceExpression
    | textContent
    | inlineTag
    | inlineIfTag
    | newline
    ;

// Inline `{@if (expr) {...} (else {...})?}` tag.
inlineIfTag
    : INLINE_IF_TAG_START PAREN_OPEN expr PAREN_CLOSE braceExpression (IF_TAG_ELSE braceExpression)? BRACE_CLOSE
    ;

// An expression, limited to a function call at the moment.
expr
    : functionCall
    ;

// A function call, limited to a single field reference argument at the moment.
functionCall
    : IDENTIFIER PAREN_OPEN fieldReference PAREN_CLOSE
    ;

// A reference to a field, possibly qualified.
fieldReference
    : (IDENTIFIER DOT)* IDENTIFIER
    ;
