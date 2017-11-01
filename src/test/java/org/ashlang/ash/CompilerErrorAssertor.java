/*
 * The Ash Project
 * Copyright (C) 2017  Peter Skrypalle
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.ashlang.ash;

import org.apache.commons.lang3.tuple.Pair;
import org.ashlang.ash.ast.Token;
import org.ashlang.ash.err.ErrorHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.fail;

class CompilerErrorAssertor {

    private final TestErrorHandler errorHandler;
    private List<Pair<ErrorType, Token>> errors;

    CompilerErrorAssertor() {
        this.errorHandler = new TestErrorHandler();
        errors = null;
    }

    ErrorHandler captureErrors() {
        if (errors != null) {
            throw new IllegalStateException("Assertion has already started. " +
                "Cannot capture any more errors.");
        }

        return errorHandler;
    }

    OngoingAssertion hasError(ErrorType errorType) {
        return new OngoingAssertion(errorType);
    }

    void hasNoMoreErrors() {
        if (getErrors().isEmpty()) {
            return;
        }

        String extraneousErrors = getErrors().stream()
            .map(pair -> {
                Token pos = pair.getRight();
                return String.format("%s at %d:%d",
                    pair.getLeft(), pos.getLine() + 1, pos.getColumn() + 1);
            })
            .collect(Collectors.joining("\n"));

        fail("Expected no more errors. But found\n%s",
            extraneousErrors);
    }

    private List<Pair<ErrorType, Token>> getErrors() {
        if (errors == null) {
            errors = new ArrayList<>(errorHandler.getErrors());
        }

        return errors;
    }

    class OngoingAssertion {
        private final ErrorType errorType;

        private OngoingAssertion(ErrorType errorType) {
            this.errorType = errorType;
        }

        CompilerErrorAssertor at(int line, int column) {
            List<Pair<ErrorType, Token>> matches = getErrors().stream()
                .filter(pair -> pair.getLeft() == errorType)
                .filter(pair -> pair.getRight().getLine() + 1 == line)
                .filter(pair -> pair.getRight().getColumn() + 1 == column)
                .collect(toList());

            if (matches.size() > 1) {
                fail("Expected at most one match for error type '%s' at %d:%d.\n" +
                        "Found %d.",
                    errorType, line, column,
                    matches.size());
            }

            if (matches.isEmpty()) {
                List<String> locationsOfSameType = getErrors().stream()
                    .filter(pair -> pair.getLeft() == errorType)
                    .map(Pair::getRight)
                    .map(pos -> String.format("%d:%d",
                        pos.getLine() + 1, pos.getColumn() + 1))
                    .collect(toList());

                List<ErrorType> typesAtSameLocation = getErrors().stream()
                    .filter(pair -> pair.getRight().getLine() + 1 == line)
                    .filter(pair -> pair.getRight().getColumn() + 1 == column)
                    .map(Pair::getLeft)
                    .collect(toList());

                fail("Expected error type '%s' at %d:%d\n" +
                        "Found error type '%s' in %s\n" +
                        "Found error types %s at %d:%d",
                    errorType, line, column,
                    errorType, locationsOfSameType,
                    typesAtSameLocation, line, column);
            } else {
                // 'matches' will only contain one item at this point
                getErrors().removeAll(matches);
            }

            return CompilerErrorAssertor.this;
        }
    }

}