package org.example;


import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class LombokRefactor {

    public static void main(String[] args) throws IOException {
        Path root = Paths.get("/home/victor/victor/dev/repo/eclipse/econect/Econect-CartaoPresente/econect/cartaopresente/entity/");

        Files.walk(root)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> {
                try {
                    JavaParser javaParser = new JavaParser();
                    ParseResult<CompilationUnit> parseResult = javaParser.parse(path);

                    CompilationUnit cu = parseResult.getResult().get();
                    LexicalPreservingPrinter.setup(cu);

                    Map<String, FieldDeclaration> campos = new HashMap<>();
                    Map<String, Modifier.Keyword> metodosGetter = new HashMap<>();
                    Map<String, Modifier.Keyword> metodosSetter = new HashMap<>();
                    List<String> camposComGetter = new ArrayList<>();
                    List<String> camposComSetter = new ArrayList<>();

                    if (classeComAnotacaoXML(cu) || classeComAnotacaoWeb(cu)) {
                        return;
                    }

                    // 1. Identifica todos os campos e não adiciona estáticos
                    cu.findAll(FieldDeclaration.class).forEach(f -> {
                        f.getVariables().forEach(var -> {
                            String nomeVariavel = var.getNameAsString();
                            if (!f.isStatic()) {
                                if (f.getElementType().asString().equals("boolean") && nomeVariavel.startsWith("is")) {
                                    String name = Character.toLowerCase(nomeVariavel.charAt(2)) + nomeVariavel.substring(3);
                                    var.setName(name);
                                    campos.put(name, f);
                                } else {
                                    String name = Character.toLowerCase(nomeVariavel.charAt(0)) + nomeVariavel.substring(1);
                                    campos.put(name, f);
                                }

                            }
                        });
                    });

                    cu.findAll(MethodDeclaration.class)
                            .forEach(method -> {
                                String nomeMetodo = method.getNameAsString();
                                String nomeCampo = nomeDoCampo(nomeMetodo);

                                Modifier.Keyword modificador = method.getModifiers()
                                        .isEmpty() ? null : method.getModifiers().get(0).getKeyword();

                                if (nomeMetodo.startsWith("get") || nomeMetodo.startsWith("is")  ) {
                                    metodosGetter.put(nomeCampo, modificador);
                                } else if (nomeMetodo.startsWith("set")) {
                                    metodosSetter.put(nomeCampo, modificador);
                                }

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

                                if (nomeCampo.startsWith("is")) {
                                    camposComGetter.add(nomeDoCampo(nomeCampo));
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
                                    String nomeCampo = normalizarNome(f.getVariable(0).getNameAsString(), false);
                                    Modifier.Keyword modGetter = metodosGetter.get(nomeCampo);

                                    if(modGetter != Modifier.Keyword.PUBLIC) {
                                        NormalAnnotationExpr getterAnnotation = new NormalAnnotationExpr();
                                        getterAnnotation.setName("Getter");
                                        getterAnnotation.addPair("value", "AccessLevel." + (modGetter != null ? modGetter.toString().toUpperCase() : "PACKAGE"));
                                        f.addAnnotation(getterAnnotation);
                                        cu.addImport("lombok.AccessLevel");
                                    } else {
                                        f.addAnnotation("Getter");
                                    }
                                });

                        cu.addImport("lombok.Getter");
                    }
                    if (!camposComSetter.isEmpty()) {
                        camposComSetter.stream()
                                .distinct()
                                .map(campos::get)
                                .filter(Objects::nonNull)
                                .forEach(f -> {
                                    String nomeCampo = normalizarNome(f.getVariable(0).getNameAsString(), false);
                                    Modifier.Keyword modSetter = metodosSetter.get(nomeCampo);

                                    if(modSetter != Modifier.Keyword.PUBLIC) {
                                        NormalAnnotationExpr setterAnnotation = new NormalAnnotationExpr();
                                        setterAnnotation.setName("Setter");
                                        setterAnnotation.addPair("value", "AccessLevel." + (modSetter != null ? modSetter.toString().toUpperCase() : "PACKAGE"));
                                        f.addAnnotation(setterAnnotation);
                                        cu.addImport("lombok.AccessLevel");
                                    } else {
                                        f.addAnnotation("Setter");
                                    }
                                });

                        cu.addImport("lombok.Setter");

                    }
                    String result = LexicalPreservingPrinter.print(cu);
                    result = result.replaceAll("@Setter", "\n\t@Setter");
                    result = result.replaceAll("@(\\w+)\\s+@(Getter)", "@$1\n\t@$2");
                    Files.writeString(path, result);

                    if (args.length > 1 && Objects.equals(args[1], "mostrar")) {
                        cu.getPrimaryTypeName().ifPresent(className -> {
                            System.out.println("Classe: " + className);
                        });
                    }
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
        String nomeForaPadrao = Character.toUpperCase(nomeCampo.charAt(0)) + nomeCampo.substring(1);

        if (!nomesCampos.contains(nomeCampo) || md.isStatic()) return false;

        String stmt = md.getBody().get().getStatement(0).toString();

        if (nomeMetodo.startsWith("get") ) {


            //validação do get comum
            return stmt.matches("return\\s+" + nomeCampo + "\\s*;")
                    || stmt.matches("return\\s+this\\s*\\.\\s*" + nomeCampo + "\\s*;")
                    || stmt.matches("return\\s+" + nomeForaPadrao + "\\s*;");

        } else if (nomeMetodo.startsWith("set")) {

            if (md.getParameters().size() != 1) return false;

            //validação para set com valor booleano em casos da variavel começar com is+campo
            if (Objects.equals(md.getParameter(0).getType().asString(), "boolean")) {
                var nomeCampoBooleanForaDePadrao = "is" + normalizarNome(nomeCampo, true);
                return stmt.matches("(this\\.)?"+nomeCampoBooleanForaDePadrao+"\\s*=\\s*"+md.getParameter(0).getName()+";")
                        || stmt.matches(nomeCampoBooleanForaDePadrao + "\\s*=\\s*" + md.getParameter(0).getName() + ";");
            }

            //validação dos set comum
            return stmt.matches("(this\\.)?"+nomeCampo+"\\s*=\\s*"+md.getParameter(0).getName()+";")
                    || stmt.matches(nomeCampo + "\\s*=\\s*" + md.getParameter(0).getName() + ";")
                    |stmt.matches("(this\\.)?" + nomeForaPadrao + "\\s*=\\s*" + md.getParameter(0).getName() + ";");

        } else if (nomeMetodo.startsWith("is")) {

            //validação do get com valor booleano em casos da variavel comecar com is+campo
            var nomeCampoBooleanForaDePadrao = "is" + normalizarNome(nomeCampo, true);
            return stmt.matches("return\\s+" + nomeCampoBooleanForaDePadrao + "\\s*;") || stmt.matches("return\\s+" + nomeCampo + "\\s*;");
        }
        return false;
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

    private static String nomeDoCampo(String nomeMetodo) {
        String semPrefixo;
        if (nomeMetodo.startsWith("get") || nomeMetodo.startsWith("set")) {
            semPrefixo = nomeMetodo.substring(3);
            if (Objects.equals(semPrefixo, "")) {
                return nomeMetodo;
            }
            return normalizarNome(semPrefixo, false);
        } else if (nomeMetodo.startsWith("is")) {
            semPrefixo = nomeMetodo.substring(2);
            if (Objects.equals(semPrefixo, "")) {
                return nomeMetodo;
            }
            return normalizarNome(semPrefixo, false);
        }
        return nomeMetodo;
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

    public static boolean matches(String stringValidar, List<String> matches) {
        if (stringValidar == null || matches == null) return false;

        return matches.stream()
                .anyMatch(match -> match.equalsIgnoreCase(stringValidar));
    }

    public static String normalizarNome(String nome, boolean upper) {
        return upper ? Character.toUpperCase(nome.charAt(0)) + nome.substring(1)
                : Character.toLowerCase(nome.charAt(0)) + nome.substring(1);
    }

}
