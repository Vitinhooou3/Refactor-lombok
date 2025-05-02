package org.example;


import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class LombokRefactor {

    public static void main(String[] args) throws IOException {
        Path root = Paths.get("/home/victor/victor/dev/repo/eclipse/econect/Econect-RestauranteAPI/src/econect/restaurante");
        Files.walk(root)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> {
                try {
                    JavaParser javaParser = new JavaParser();
                    ParseResult<CompilationUnit> parseResult = javaParser.parse(path);

                    CompilationUnit cu = parseResult.getResult().get();

                    Map<String, FieldDeclaration> campos = new HashMap<>();
                    List<String> camposComGetter = new ArrayList<>();
                    List<String> camposComSetter = new ArrayList<>();

                    if (classeComAnotacaoXML(cu) || classeComAnotacaoWeb(cu)) {
                        return;
                    }

                    // 1. Identifica todos os campos
                    cu.findAll(FieldDeclaration.class).forEach(f -> {
                        f.getVariables().forEach(var -> campos.put(var.getNameAsString(), f));
                    });

                    // 2. Remove métodos get/set simples e marca os campos associados
                    cu.accept(new ModifierVisitor<Void>() {
                        @Override
                        public Visitable visit(MethodDeclaration md, Void arg) {
                            if (ehGetterOuSetterSimples(md, campos.keySet())) {
                                String nomeCampo = (md.getNameAsString());
                                if (nomeCampo.startsWith("get")) {
                                    camposComGetter.add(nomeDoCampo(nomeCampo));
                                }

                                if (nomeCampo.startsWith("set")) {
                                    camposComSetter.add(nomeDoCampo(nomeCampo));
                                }

                                return null; // remove method
                            }
                            return super.visit(md, arg);
                        }
                    }, null);

                    if (!camposComGetter.isEmpty()) {
                        camposComGetter.stream()
                                .distinct()
                                .map(campos::get)
                                .filter(Objects::nonNull)
                                .forEach(f -> {
                                    f.addAnnotation(new MarkerAnnotationExpr("Getter"));
                                });

                        cu.addImport("lombok.Getter");
                    }

                    if (!camposComSetter.isEmpty()) {
                        camposComSetter.stream()
                                .distinct()
                                .map(campos::get)
                                .filter(Objects::nonNull)
                                .forEach(f -> {
                                    f.addAnnotation(new MarkerAnnotationExpr("Setter"));
                                });

                        cu.addImport("lombok.Setter");

                    }
                    //TODO: colocar get/set para booleanos.
                    Files.write(path, cu.toString().getBytes());

                } catch (Exception e) {
                    System.err.println("Erro em " + path + ": " + e.getMessage());
                }
            });
    }

    private static boolean ehGetterOuSetterSimples(MethodDeclaration md, Set<String> nomesCampos) {
        if (!md.getBody().isPresent() || md.getBody().get().getStatements().size() != 1)
            return false;

        String nomeMetodo = md.getNameAsString();
        String nomeCampo = nomeDoCampo(nomeMetodo);

        if (!nomesCampos.contains(nomeCampo)) return false;

        var stmt = md.getBody().get().getStatement(0).toString();

        if (nomeMetodo.startsWith("get")) {
            // Deve ser: return campo;
            return stmt.matches("return\\s+\\.?"+nomeCampo+";");

        } else if (nomeMetodo.startsWith("set")) {
            // Deve ser: this.campo = param; ou campo = param;
            if (md.getParameters().size() != 1) return false;
            return stmt.matches("(this\\.)?"+nomeCampo+"\\s*=\\s*"+md.getParameter(0).getName()+";");
        }

        return false;
    }

    private static String nomeDoCampo(String nomeMetodo) {

        if (nomeMetodo.startsWith("get") || nomeMetodo.startsWith("set")) {
            String semPrefixo = nomeMetodo.substring(3);
            return Character.toLowerCase(semPrefixo.charAt(0)) + semPrefixo.substring(1);
        }

        return nomeMetodo;
    }

    private static boolean classeComAnotacaoXML(CompilationUnit cu) {
        boolean result = cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .anyMatch(classe ->
                        classe.getAnnotations().stream()
                                .anyMatch(annot -> annot.getNameAsString().startsWith("Xml"))
                );
        if (result) {
            System.out.println("Classe ignorada por anotação @Xml: " + cu.getType(0).getNameAsString());
        }
        return result;
    }

    private static boolean classeComAnotacaoWeb(CompilationUnit cu) {
        boolean result = cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .anyMatch(classe ->
                        classe.getAnnotations().stream()
                                .anyMatch(annotationExpr -> annotationExpr.getNameAsString().startsWith("Web"))
                );
        if (result) {
            System.out.println("Classe ignorada por anotação @Web: " + cu.getType(0).getNameAsString());
        }
        return result;
    }

}
