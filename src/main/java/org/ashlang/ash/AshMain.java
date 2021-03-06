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

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.ashlang.ash.ast.ASTNode;
import org.ashlang.ash.ast.ASTPrinter;
import org.ashlang.ash.codegen.CodeGenerators;
import org.ashlang.ash.err.ConsoleErrorHandler;
import org.ashlang.ash.err.ErrorHandler;
import org.ashlang.ash.lang.*;
import org.ashlang.ash.pass.CompilerPassChain;
import org.ashlang.ash.pass.CompilerPasses;
import org.ashlang.ash.util.ExecResult;
import org.ashlang.ash.util.IOUtil;
import org.ashlang.ash.util.Version;
import org.ashlang.gen.AshParser.FileContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class AshMain {

    private AshMain() { /**/ }

    public static void
    main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: ashc <file>");
            return;
        }

        Path inFile = Paths.get(args[0]).normalize();
        if (Files.isDirectory(inFile)) {
            System.err.println("Usage: ashc <file>");
            return;
        }

        CharStream in = CharStreams.fromPath(inFile, StandardCharsets.UTF_8);
        ErrorHandler errorHandler = new ConsoleErrorHandler().withDebugEnabled();

        ASTNode rootNode = buildAST(in, errorHandler);
        if (errorHandler.hasErrors()) {
            errorHandler.flush();
            return;
        }
        ASTPrinter.print(rootNode);

        compileAndRunC11Target(inFile, rootNode);
        Thread.sleep(100L);
        compileAndRunJava8Target(inFile, rootNode);
        Thread.sleep(100L);
    }

    private static void compileAndRunC11Target(Path inFile, ASTNode rootNode) {
        String c11Src = translateToC11(rootNode);
        c11Src = IOUtil.tryIndent(c11Src);
        System.out.println("C11 source:\n==============");
        System.out.println(c11Src);
        Path dir = inFile.getParent();
        if (dir == null) {
            throw new IllegalStateException();
        }
        Path out = dir.resolve("out");
        compileToNative(c11Src, out, dir);
        ExecResult exec = IOUtil.exec(out);
        System.out.println("C11 output:\n==============");
        System.out.println(exec.getOut());
        System.out.println("C11 error:\n==============");
        System.out.println(exec.getErr());
    }

    private static void compileAndRunJava8Target(Path inFile, ASTNode rootNode) {
        String java8Src = translateToJava8(rootNode);
        System.out.println("Java8 source:\n==============");
        System.out.println(java8Src);
        Path dir = inFile.getParent();
        if (dir == null) {
            throw new IllegalStateException();
        }

        Path out = dir.resolve("Main.class");
        compileToJVM(java8Src, out, dir);
        ExecResult exec = IOUtil.execInDir(dir, "java", "Main");
        System.out.println("Java8 output:\n==============");
        System.out.println(exec.getOut());
        System.out.println("Java8 error:\n==============");
        System.out.println(exec.getErr());
    }

    static ASTNode
    buildAST(String ashSrc, ErrorHandler errorHandler) {
        return buildAST(CharStreams.fromString(ashSrc), errorHandler);
    }

    private static ASTNode
    buildAST(CharStream charStream, ErrorHandler errorHandler) {
        AshLexer lexer = new AshLexer(charStream);
        AshParser parser = new AshParser(new CommonTokenStream(lexer));

        lexer.removeErrorListeners();
        lexer.addErrorListener(new LexerErrorListener(errorHandler));
        parser.removeErrorListeners();
        parser.addErrorListener(new ParserErrorListener(errorHandler));

        FileContext fileCtx = parser.file();

        if (errorHandler.hasErrors()) {
            return null;
        }

        ASTNode rootNode = ASTBuilder.buildAST(fileCtx, parser);
        CompilerPassChain
            .withErrorHandler(errorHandler)
            .appendPass(CompilerPasses.SYMBOL_CHECK_PASS)
            .appendPass(CompilerPasses.TYPE_ASSIGN_PASS)
            .appendPass(CompilerPasses.CONSTANT_RESOLVE_PASS)
            .appendPass(CompilerPasses.TYPE_CHECK_PASS)
            .applyTo(rootNode);

        if (errorHandler.hasErrors()) {
            return null;
        }

        return rootNode;
    }

    //region compile to native (C11)

    static void
    compileToNative(ASTNode rootNode, Path outFile) {
        String c11Src = translateToC11(rootNode);
        compileToNative(c11Src, outFile);
    }

    private static String
    translateToC11(ASTNode rootNode) {
        return CodeGenerators.C_11.generate(rootNode);
    }

    private static void
    compileToNative(String c11Src, Path outFile) {
        compileToNative(c11Src, outFile, outFile.getParent());
    }

    private static void
    compileToNative(String c11Src, Path outFile, Path workDir) {
        Path outDir = outFile.getParent();
        if (outDir == null) {
            throw new IllegalArgumentException(String.format(
                "Could not get parent directory of file %s",
                outFile.toAbsolutePath()));
        }
        Path tmpFile = outDir.resolve("main.c");
        IOUtil.writeUTF8(tmpFile, c11Src);

        Version gccVersion = IOUtil.gccVersion();
        if (gccVersion == null) {
            throw new IllegalStateException("gcc not found in path.");
        }
        if (gccVersion.lessThan(4, 8, 0)) {
            throw new IllegalStateException(
                "gcc version 4.8 or greater required."
            );
        }

        ExecResult gcc = IOUtil.execInDir(
            workDir,
            "gcc",
            "-std=c99",
            "-Wall",
            "-Wextra",
            "-Werror",
            "-pedantic",
            "-Wno-pedantic-ms-format",
            "-Wno-tautological-compare",
            "--save-temps",
            "-o",
            outFile.toAbsolutePath(),
            tmpFile.toAbsolutePath()
        );

        if (gcc.getExitCode() != 0) {
            throw new IllegalStateException(
                "ASH -> Native(C11) Compilation failed!\n" + gcc.getErr());
        }
    }

    //endregion compile to native (C11)

    //region compile to JVM (Java8)

    static void
    compileToJVM(ASTNode rootNode, Path outFile) {
        String java8Src = translateToJava8(rootNode);
        compileToJVM(java8Src, outFile);
    }

    private static String
    translateToJava8(ASTNode rootNode) {
        return CodeGenerators.JAVA_8.generate(rootNode);
    }

    private static void
    compileToJVM(String java8Src, Path outFile) {
        compileToJVM(java8Src, outFile, outFile.getParent());
    }

    private static void
    compileToJVM(String java8Src, Path classFile, Path workDir) {
        Path outDir = classFile.getParent();
        if (outDir == null) {
            throw new IllegalArgumentException(String.format(
                "Could not get parent directory of file %s",
                classFile.toAbsolutePath()));
        }

        Path fileName = classFile.getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException(String.format(
                "Could not get file name from file %s",
                classFile.toAbsolutePath()
            ));
        }
        String classFileName = fileName.toString();
        String javaFileName = classFileName.replaceAll(".class", ".java");

        Path tmpFile = outDir.resolve(javaFileName);
        IOUtil.writeUTF8(tmpFile, java8Src);

        Version javacVersion = IOUtil.javacVersion();
        if (javacVersion == null) {
            throw new IllegalStateException("javac not found in path.");
        }
        if (javacVersion.lessThan(1, 8, 0)) {
            throw new IllegalStateException(
                "javac version 1.8 or greater required."
            );
        }

        ExecResult javc = IOUtil.execInDir(
            workDir,
            "javac",
            tmpFile.toAbsolutePath()
        );

        if (javc.getExitCode() != 0) {
            throw new IllegalStateException(
                "ASH -> JVM(Java8) Compilation failed!\n" + javc.getErr());
        }
    }

    //endregion compile to JVM (Java8)

}
