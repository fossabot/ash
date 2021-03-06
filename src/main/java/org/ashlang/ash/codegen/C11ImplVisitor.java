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
import org.ashlang.ash.ast.visitor.ASTSingleBaseVisitor;
import org.ashlang.ash.symbol.Function;
import org.ashlang.ash.symbol.Symbol;
import org.ashlang.ash.type.Type;

import java.math.BigInteger;
import java.util.stream.Collectors;

import static org.ashlang.ash.codegen.CommonValues.FUNC_PREFIX;

class C11ImplVisitor extends ASTSingleBaseVisitor<String> {

    private final C11TypeMap typeMap;

    C11ImplVisitor(C11TypeMap typeMap) {
        this.typeMap = typeMap;
    }

    @Override
    protected String
    visitFuncDeclarationNode(FuncDeclarationNode node) {
        String body = visit(node.getBody());
        String cType = typeMap.getType(node.getType());
        String identifier = node.getIdentifierToken().getText();

        String params = node.getParams().stream()
            .map(this::visit)
            .collect(Collectors.joining(","));

        String func = String.format(
            "%s %s%s(%s)%s",
            cType, FUNC_PREFIX, identifier, params, body
        );

        if ("main".equals(identifier)) {
            return String.join("\n",
                "static inline " + func,
                "int main(int argc, char **argv) {",
                "    (void) argc;",
                "    (void) argv;",
                "",
                "    " + FUNC_PREFIX + "main();",
                "",
                "    return 0;",
                "}"
            );
        }

        return func;
    }

    @Override
    protected String
    visitParamDeclarationNode(ParamDeclarationNode node) {
        Symbol symbol = node.getSymbol();
        String cType = typeMap.getType(symbol.getType());
        return cType + " " + symbol.getIdentifier();
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
    visitVarDeclAssignNode(VarDeclAssignNode node) {
        Symbol symbol = node.getSymbol();
        String cType = typeMap.getType(symbol.getType());
        String expression = visit(node.getExpression());
        return cType + " " + symbol.getIdentifier() + " = " + expression;
    }

    @Override
    protected String
    visitBlockNode(BlockNode node) {
        return "{\n" + visitChildren(node) + "}\n";
    }

    @Override
    protected String
    visitFuncCallNode(FuncCallNode node) {
        String args = node.getArguments().stream()
            .map(arg -> visit(arg.getExpression()))
            .collect(Collectors.joining(", "));
        Function func = node.getFunction();
        return FUNC_PREFIX + func.getIdentifier() + "(" + args + ")";
    }

    @Override
    protected String
    visitBranchNode(BranchNode node) {
        String expression = visit(node.getExpression());
        String onTrue = visit(node.getOnTrue());
        String onFalse = visit(node.getOnFalse());

        return "if(" + expression + ")" + onTrue + " else " + onFalse;
    }

    @Override
    protected String
    visitWhileLoopNode(WhileLoopNode node) {
        String expression = visit(node.getExpression());
        String body = visit(node.getBody());

        return "while (" + expression + ")" + body;
    }

    @Override
    protected String
    visitForLoopNode(ForLoopNode node) {
        String declaration = visit(node.getDeclaration());
        String condition = visit(node.getCondition());
        String action = visit(node.getAction());
        String body = visit(node.getBody());

        return "for (" + declaration + ";" + condition + ";" + action + ")" + body;
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
    visitVarDeclAssignStatementNode(VarDeclAssignStatementNode node) {
        return visitChildren(node) + ";\n";
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
        String expr = typeMap.formatExpression(type, expression);
        return "printf(\"" + fmt + "\\n\", " + expr + ");\n";
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
        return visitBinaryExpression(node, "+");
    }

    @Override
    protected String
    visitSubExpressionNode(SubExpressionNode node) {
        return visitBinaryExpression(node, "-");
    }

    @Override
    protected String
    visitMulExpressionNode(MulExpressionNode node) {
        return visitBinaryExpression(node, "*");
    }

    @Override
    protected String
    visitDivExpressionNode(DivExpressionNode node) {
        return visitBinaryExpression(node, "/");
    }

    @Override
    protected String
    visitModExpressionNode(ModExpressionNode node) {
        return visitBinaryExpression(node, "%");
    }

    @Override
    protected String
    visitEqualsExpressionNode(EqualsExpressionNode node) {
        return visitBinaryExpression(node, "==");
    }

    @Override
    protected String
    visitNotEqualsExpressionNode(NotEqualsExpressionNode node) {
        return visitBinaryExpression(node, "!=");
    }

    @Override
    protected String
    visitLtExpressionNode(LtExpressionNode node) {
        return visitBinaryExpression(node, "<");
    }

    @Override
    protected String
    visitGtExpressionNode(GtExpressionNode node) {
        return visitBinaryExpression(node, ">");
    }

    @Override
    protected String
    visitLtEqExpressionNode(LtEqExpressionNode node) {
        return visitBinaryExpression(node, "<=");
    }

    @Override
    protected String
    visitGtEqExpressionNode(GtEqExpressionNode node) {
        return visitBinaryExpression(node, ">=");
    }

    @Override
    protected String
    visitBoolLiteralExpressionNode(BoolLiteralExpressionNode node) {
        return node.getValue().toString();
    }

    @Override
    protected String
    visitIdExpressionNode(IdExpressionNode node) {
        return node.getValueToken().getText();
    }

    @Override
    protected String
    visitIntExpressionNode(IntExpressionNode node) {
        BigInteger value = (BigInteger) node.getValue();
        Type type = node.getType();
        String cType = typeMap.getType(type);
        return "((" + cType + ")" + value.toString() + "ull)";
    }

    private String
    visitBinaryExpression(BinaryExpressionNode node, String op) {
        String lhs = visit(node.getLhs());
        String rhs = visit(node.getRhs());
        return "(" + lhs + op + rhs + ")";
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
