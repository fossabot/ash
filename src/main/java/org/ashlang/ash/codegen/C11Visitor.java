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

package org.ashlang.ash.codegen;

import org.ashlang.ash.ast.*;
import org.ashlang.ash.ast.visitor.ASTSingleVisitor;
import org.ashlang.ash.symbol.Symbol;
import org.ashlang.ash.type.Type;

import java.math.BigInteger;

class C11Visitor extends ASTSingleVisitor<String> {

    private final C11TypeMap typeMap;

    C11Visitor() {
        typeMap = new C11TypeMap();
    }

    @Override
    protected String
    visitFileNode(FileNode node) {
        return String.join("\n",
            "#include <stdio.h>",
            "#include <stdint.h>",
            "#include <inttypes.h>",
            "",
            visitChildren(node),
            "");
    }

    @Override
    protected String
    visitFuncDeclarationNode(FuncDeclarationNode node) {
        String body = visit(node.getBody());
        String cType = typeMap.getType(node.getType());
        String identifier = node.getIdentifierToken().getText();

        if ("main".equals(identifier)) {
            return String.join("\n",
                "static inline " + cType + " __ash_main()" + body,
                "int main(int argc, char **argv) {",
                "    (void) argc;",
                "    (void) argv;",
                "",
                "    __ash_main();",
                "",
                "    return 0;",
                "}"
            );
        }

        return cType + " " + identifier + "()" + body;
    }

    @Override
    protected String
    visitVarDeclarationNode(VarDeclarationNode node) {
        Symbol symbol = node.getSymbol();
        String cType = typeMap.getType(symbol.getType());
        return cType + " " + symbol.getIdentifier();
    }

    @Override
    protected String
    visitVarAssignNode(VarAssignNode node) {
        Symbol symbol = node.getSymbol();
        String expression = visit(node.getExpression());
        return symbol.getIdentifier() + " = " + expression;
    }

    @Override
    protected String
    visitBlockNode(BlockNode node) {
        return "{\n" + visitChildren(node) + "}\n";
    }

    //region statement nodes

    @Override
    protected String
    visitVarDeclarationStatementNode(VarDeclarationStatementNode node) {
        return visitChildren(node) + ";\n";
    }

    @Override
    protected String
    visitVarAssignStatementNode(VarAssignStatementNode node) {
        return visitChildren(node) + ";\n";
    }

    @Override
    protected String
    visitBlockStatementNode(BlockStatementNode node) {
        return visitChildren(node);
    }

    @Override
    protected String
    visitExpressionStatementNode(ExpressionStatementNode node) {
        return visitChildren(node) + ";\n";
    }

    @Override
    protected String
    visitDumpStatementNode(DumpStatementNode node) {
        String expression = visitChildren(node);
        Type type = node.getExpression().getType();
        String fmt = typeMap.getFormat(type);
        return "printf(\"%\"" + fmt + ", " + expression + ");\n";
    }

    @Override
    protected String
    visitReturnStatementNode(ReturnStatementNode node) {
        return "return " + visitChildren(node) + ";\n";
    }

    //endregion statement nodes

    //region expression nodes

    @Override
    protected String
    visitParenExpressionNode(ParenExpressionNode node) {
        return "(" + visitChildren(node) + ")";
    }

    @Override
    protected String
    visitAddExpressionNode(AddExpressionNode node) {
        String lhs = visit(node.getLhs());
        String rhs = visit(node.getRhs());
        return "(" + lhs + "+" + rhs + ")";
    }

    @Override
    protected String
    visitSubExpressionNode(SubExpressionNode node) {
        String lhs = visit(node.getLhs());
        String rhs = visit(node.getRhs());
        return "(" + lhs + "-" + rhs + ")";
    }

    @Override
    protected String
    visitMulExpressionNode(MulExpressionNode node) {
        String lhs = visit(node.getLhs());
        String rhs = visit(node.getRhs());
        return "(" + lhs + "*" + rhs + ")";
    }

    @Override
    protected String
    visitDivExpressionNode(DivExpressionNode node) {
        String lhs = visit(node.getLhs());
        String rhs = visit(node.getRhs());
        return "(" + lhs + "/" + rhs + ")";
    }

    @Override
    protected String
    visitModExpressionNode(ModExpressionNode node) {
        String lhs = visit(node.getLhs());
        String rhs = visit(node.getRhs());
        return "(" + lhs + "%" + rhs + ")";
    }

    @Override
    protected String
    visitIdExpressionNode(IdExpressionNode node) {
        return node.getValue().getText();
    }

    @Override
    protected String
    visitIntExpressionNode(IntExpressionNode node) {
        BigInteger value = (BigInteger) node.getValue();
        Type type = node.getType();
        String cType = typeMap.getType(type);
        return "((" + cType + ")" + value.toString() + "ull)";
    }

    //endregion expression nodes

    @Override
    public String
    aggregate(String aggregate, String next) {
        if (aggregate == null) {
            return next;
        }
        if (next == null) {
            return aggregate;
        }
        return aggregate + next;
    }

    @Override
    public String
    defaultResult() {
        return "";
    }

}
