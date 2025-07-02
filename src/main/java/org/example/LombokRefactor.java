package org.example;


import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LombokRefactor {

    private static final AtomicInteger arquivosProcessados = new AtomicInteger(0);
    private static final AtomicInteger arquivosModificados = new AtomicInteger(0);

    public static void main(String[] args) throws IOException {

        Path projetoPath = Paths.get("/home/victor/victor/dev/repo/eclipse/econect");
//        if (!Files.exists(projetoPath) || !Files.isDirectory(projetoPath)) {
//            System.err.println("Diretório inválido.");
//            return;
//        }

        Files.walk(projetoPath)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(LombokRefactor::processarArquivo);

        System.out.printf("Finalizado: %d arquivos analisados, %d modificados.%n",
                arquivosProcessados.get(), arquivosModificados.get());
    }

    private static void processarArquivo(Path caminho) {
        try {
            String codigoOriginal = Files.readString(caminho);
            CompilationUnit cu = StaticJavaParser.parse(codigoOriginal);

            arquivosProcessados.incrementAndGet();
            AtomicInteger modificacoesNoArquivo = new AtomicInteger(0);

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classe -> {
                List<FieldDeclaration> campos = classe.getFields().stream().filter(fieldDeclaration -> !fieldDeclaration.isStatic()).toList();

                if (campos.isEmpty()) return;

                boolean todosComGetterSetter = campos.stream().allMatch(field ->
                        (field.isAnnotationPresent("Getter") && field.isAnnotationPresent("Setter")));

                if (!todosComGetterSetter) return;

                campos.forEach(field -> {
                    field.getAnnotations().removeIf(a ->
                            a.getNameAsString().equals("Getter") || a.getNameAsString().equals("Setter"));
                });

                if (!classe.isAnnotationPresent("Getter")) {
                    classe.addMarkerAnnotation("Getter");
                }
                if (!classe.isAnnotationPresent("Setter")) {
                    classe.addMarkerAnnotation("Setter");
                }
                modificacoesNoArquivo.incrementAndGet();
            });

            if (modificacoesNoArquivo.get() > 0) {
                Files.writeString(caminho, cu.toString());
                arquivosModificados.incrementAndGet();
                System.out.printf("Modificado: %s%n", caminho);
            }

        } catch (Exception e) {
            System.err.printf("Erro ao processar %s: %s%n", caminho, e.getMessage());
        }
    }
}
