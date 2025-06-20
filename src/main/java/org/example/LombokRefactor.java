package org.example;


import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class LombokRefactor {

    public static void main(String[] args) throws IOException {
        Path root = Paths.get("/home/victor/victor/dev/repo/eclipse/econect/");
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
                        // Lista de métodos com a nomenclatura fora do padrão
                        List<String> nomesForaDoPadrao = new ArrayList<>();

                        if (classeComAnotacaoXML(cu) || classeComAnotacaoWeb(cu)) {
                            return;
                        }

                        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classOrInterfaceDeclaration -> {
                            if (classOrInterfaceDeclaration.isInnerClass()) {
                                processarClasseOuInterface(classOrInterfaceDeclaration, cu);
                            }
                        });

                        cu.findAll(EnumDeclaration.class).forEach(enumDeclaration -> {
                            processarEnum(enumDeclaration, cu);
                        });

                        // 1. Identifica todos os campos e não adiciona estáticos
                        cu.findAll(FieldDeclaration.class).forEach(f -> {
                            f.getVariables().forEach(var -> {
                                String nomeVariavel = var.getNameAsString();
                                String name = Character.toLowerCase(nomeVariavel.charAt(0)) + nomeVariavel.substring(1);
                                if (!f.isStatic()) {
                                    campos.put(name, f);
                                }
                            });
                        });

                        cu.findAll(MethodDeclaration.class)
                                .forEach(method -> {
                                    String nomeMetodo = method.getNameAsString();
                                    String nomeCampo = nomeDoCampo(nomeMetodo);

                                    Modifier.Keyword modificador = method.getModifiers()
                                            .isEmpty() ? null : method.getModifiers().get(0).getKeyword();

                                    if(validaNomeMetodo(method)) {
                                        if (nomeMetodo.startsWith("get") || nomeMetodo.startsWith("is")) {
                                            metodosGetter.put(nomeCampo, modificador);
                                        } else if (nomeMetodo.startsWith("set")) {
                                            metodosSetter.put(nomeCampo, modificador);
                                        }
                                    } else {
                                        //if(nomeMetodo.startsWith("get") || nomeMetodo.startsWith("is") || nomeMetodo.startsWith("set"))
                                         //   nomesForaDoPadrao.add(method.getNameAsString());
                                    }
                                });

                        // 2. Remove métodos get/set simples e marca os campos associados
                        cu.accept(new ModifierVisitor<Void>() {
                            @Override
                            public Visitable visit(MethodDeclaration md, Void arg) {
                                if (isGetterOuSetterSimples(md) && !nomesForaDoPadrao.contains(md.getNameAsString()) && campos.containsKey(nomeDoCampo(md.getNameAsString()))) {
                                    String nomeMetodo = (md.getNameAsString());

                                    if (nomeMetodo.startsWith("get") || nomeMetodo.startsWith("is")) {
                                        camposComGetter.add(nomeDoCampo(nomeMetodo));
                                    }

                                    if (nomeMetodo.startsWith("set")) {
                                        camposComSetter.add(nomeDoCampo(nomeMetodo));
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
                                        var var = f.getVariables().get(0);

//                                        if (f.getElementType().asString().equals("boolean") && var.getNameAsString().startsWith("is")) {
//                                            nomeCampo = Character.toLowerCase(nomeCampo.charAt(2)) + nomeCampo.substring(3);
//                                            var.setName(nomeCampo);
//                                        }
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

//                        if (!nomesForaDoPadrao.isEmpty()) {
//                            // Itera sobre todos os campos
//                            cu.findAll(FieldDeclaration.class).forEach(fd -> {
//                                // Itera sobre as variáveis dentro de cada campo (cada FieldDeclaration pode ter várias variáveis)
//                                fd.getVariables().forEach(var -> {
//                                    // Obtém o nome da variável
//                                    String nomeVariavel = var.getNameAsString();
//
//                                    // Verifica se o nome da variável está presente na lista de métodos fora do padrão
//                                    if (nomesForaDoPadrao.stream().anyMatch(nome -> nome.contains(nomeVariavel))) {
//                                        // Cria o comentário de linha única (comentário // TODO)
//                                        LineComment comentario = new LineComment(" TODO: Ajustar o nome do getter/setter para seguir o padrão.");
//
//                                        // Adiciona o comentário ao campo
//                                        fd.setComment(comentario);
//                                    }
//                                });
//                            });
//                        }

                        String result = LexicalPreservingPrinter.print(cu);
//                        result = result.replaceAll("@Setter", "\n\t@Setter");
//                        result = result.replaceAll("@(\\w+)\\s+@(Getter)", "@$1\n\t@$2");
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

    /**
     * Faz a verificação dos nomes dos métodos
     * @param md
     * @return
     */
    public static boolean validaNomeMetodo(MethodDeclaration md) {
        String nomeMetodo = md.getNameAsString();
        String nomeCampo = nomeDoCampo(nomeMetodo);
        nomeCampo = normalizarNome(nomeCampo, true);
        return (md.getNameAsString().startsWith("get" + nomeCampo) && !md.getType().asString().equals("boolean"))
                || (md.getNameAsString().startsWith("is" + nomeCampo) && md.getType().asString().equals("boolean"))
                || md.getNameAsString().startsWith("set" + nomeCampo);
    }

    /**
     * Faz a verificação dos retornos dos métodos
     * @param md
     * @return
     */
    private static boolean isGetterOuSetterSimples(MethodDeclaration md) {
        if (md.getBody().isPresent() && md.getBody().get().getStatements().size() == 1 && validaNomeMetodo(md) && !md.isStatic()) {

            String nomeMetodo = md.getNameAsString();
            String nomeCampo = nomeDoCampo(nomeMetodo);
            String nomeForaPadrao = Character.toUpperCase(nomeCampo.charAt(0)) + nomeCampo.substring(1);

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
//                    var nomeCampoBooleanForaDePadrao = "is" + normalizarNome(nomeCampo, true);
//                    return stmt.matches("(this\\.)?"+nomeCampoBooleanForaDePadrao+"\\s*=\\s*"+md.getParameter(0).getName()+";")
//                            || stmt.matches(nomeCampoBooleanForaDePadrao + "\\s*=\\s*" + md.getParameter(0).getName() + ";" )
//                            || stmt.matches("(this\\.)?"+nomeCampo+"\\s*=\\s*"+md.getParameter(0).getName()+";");
                }

                //validação dos set comum
                return stmt.matches("(this\\.)?"+nomeCampo+"\\s*=\\s*"+md.getParameter(0).getName()+";")
                        || stmt.matches(nomeCampo + "\\s*=\\s*" + md.getParameter(0).getName() + ";")
                        |stmt.matches("(this\\.)?" + nomeForaPadrao + "\\s*=\\s*" + md.getParameter(0).getName() + ";");

            } else if (nomeMetodo.startsWith("is")) {

                return stmt.matches("return\\s+" + nomeCampo + "\\s*;");
            }
        }
        return false;
    }

    /**
     * Metodo para log no terminal que ignora classes com anotações @Xml
     * @param cu
     * @return
     */
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

    /**
     * Metodo que utiliza o nome do metodo para extrair o nome do campo ao qual ele está se referindo
     * @param nomeMetodo
     * @return
     */
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

    /**
     * Metodo para log no terminal que ignora classes com anotações @Web
     * @param cu
     * @return
     */
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

    /**
     * Metodo que ajusta uma string com a primeira letra maiúscula ou minúscula conforme o upper
     * @param nome
     * @param upper
     * @return
     */
    public static String normalizarNome(String nome, boolean upper) {
        return upper ? Character.toUpperCase(nome.charAt(0)) + nome.substring(1)
                : Character.toLowerCase(nome.charAt(0)) + nome.substring(1);
    }

    /**
     * Metodo que processa Classes ou interfaces
     * @param clazz
     * @param cu
     */
    private static void processarClasseOuInterface(ClassOrInterfaceDeclaration clazz, CompilationUnit cu) {
        Map<String, FieldDeclaration> campos = new HashMap<>();
        Map<String, Modifier.Keyword> metodosGetter = new HashMap<>();
        Map<String, Modifier.Keyword> metodosSetter = new HashMap<>();
        List<String> camposComGetter = new ArrayList<>();
        List<String> camposComSetter = new ArrayList<>();

        // 1. Coleta os campos (ignorando static)
        clazz.findAll(FieldDeclaration.class).forEach(f -> {
            f.getVariables().forEach(var -> {
                String nome = var.getNameAsString();
                if (!f.isStatic()) {
                    String nomeCampo = nome.startsWith("is") && f.getElementType().asString().equals("boolean")
                            ? Character.toLowerCase(nome.charAt(2)) + nome.substring(3)
                            : Character.toLowerCase(nome.charAt(0)) + nome.substring(1);
                    campos.put(nomeCampo, f);
                }
            });
        });

        // 2. Coleta métodos para saber quais são getter/setter
        clazz.findAll(MethodDeclaration.class).forEach(method -> {
            String nomeMetodo = method.getNameAsString();
            String nomeCampo = nomeDoCampo(nomeMetodo);
            Modifier.Keyword mod = method.getModifiers().isEmpty() ? null : method.getModifiers().get(0).getKeyword();

            if (nomeMetodo.startsWith("get") || nomeMetodo.startsWith("is")) {
                metodosGetter.put(nomeCampo, mod);
            } else if (nomeMetodo.startsWith("set")) {
                metodosSetter.put(nomeCampo, mod);
            }
        });

        // 3. Remove métodos triviais
        clazz.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(MethodDeclaration md, Void arg) {
                if (isGetterOuSetterSimples(md)) {
                    String nomeMetodo = nomeDoCampo(md.getNameAsString());

                    if (md.getNameAsString().startsWith("get") || md.getNameAsString().startsWith("is")) {
                        camposComGetter.add(nomeMetodo);
                    } else if (md.getNameAsString().startsWith("set")) {
                        camposComSetter.add(nomeMetodo);
                    }

                    return null;
                }
                return super.visit(md, arg);
            }
        }, null);

        // 4. Adiciona anotações
        if (!camposComGetter.isEmpty()) {
            cu.addImport("lombok.Getter");
            camposComGetter.stream()
                    .distinct()
                    .map(campos::get)
                    .filter(Objects::nonNull)
                    .forEach(f -> {
                        f.addAnnotation("Getter");
                    });
        }

        if (!camposComSetter.isEmpty()) {
            cu.addImport("lombok.Setter");
            camposComSetter.stream()
                    .distinct()
                    .map(campos::get)
                    .filter(Objects::nonNull)
                    .forEach(f -> {
                        f.addAnnotation("Setter");
                    });
        }

        // 5. Processar classes ou enums internas recursivamente
        clazz.getMembers().forEach(member -> {
            if (member.isClassOrInterfaceDeclaration()) {
                processarClasseOuInterface(member.asClassOrInterfaceDeclaration(), cu);
            } else if (member.isEnumDeclaration()) {
                processarEnum(member.asEnumDeclaration(), cu);
            }
        });
    }

    /**
     * Metodo que processa o enum dentro de uma classe
     * @param enumDecl
     * @param cu
     */
    private static void processarEnum(EnumDeclaration enumDecl, CompilationUnit cu) {
        Map<String, FieldDeclaration> campos = new HashMap<>();
        List<String> camposComGetter = new ArrayList<>();
        List<String> camposComSetter = new ArrayList<>();

        enumDecl.findAll(FieldDeclaration.class).forEach(f -> {
            f.getVariables().forEach(var -> {
                String nome = var.getNameAsString();
                String nomeCampo = Character.toLowerCase(nome.charAt(0)) + nome.substring(1);
                campos.put(nomeCampo, f);
            });
        });

        enumDecl.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(MethodDeclaration md, Void arg) {
                if (isGetterOuSetterSimples(md)) {
                    String nomeMetodo = nomeDoCampo(md.getNameAsString());
                    if (md.getNameAsString().startsWith("get") || md.getNameAsString().startsWith("is")) {
                        camposComGetter.add(nomeMetodo);
                    }

                    if (md.getNameAsString().startsWith("set")) {
                        camposComSetter.add(nomeMetodo);
                    }
                    return null;
                }
                return super.visit(md, arg);
            }
        }, null);

        if (!camposComGetter.isEmpty()) {
            cu.addImport("lombok.Getter");
            camposComGetter.stream()
                    .distinct()
                    .map(campos::get)
                    .filter(Objects::nonNull)
                    .forEach(f -> f.addAnnotation("Getter"));
        }

        if (!camposComGetter.isEmpty()) {
            cu.addImport("lombok.Setter");
            camposComSetter.stream()
                    .distinct()
                    .map(campos::get)
                    .filter(Objects::nonNull)
                    .forEach(f -> f.addAnnotation("Setter"));
        }


        // Recursividade para enums internos
        enumDecl.getMembers().forEach(member -> {
            if (member.isClassOrInterfaceDeclaration()) {
                processarClasseOuInterface(member.asClassOrInterfaceDeclaration(), cu);
            } else if (member.isEnumDeclaration()) {
                processarEnum(member.asEnumDeclaration(), cu);
            }
        });
    }
}