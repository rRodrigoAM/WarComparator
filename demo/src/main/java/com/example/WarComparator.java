package com.example;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class WarComparator {

    public static void main(String[] args) {
        try {
            String war1Path = "C:\\Users\\rodrigo.machado\\teste\\Nova pasta\\prod.war";
            String war2Path = "C:\\Users\\rodrigo.machado\\teste\\Nova pasta (2)\\dev.war";

            Path dir1 = extractWar(war1Path, "temp_war1");
            Path dir2 = extractWar(war2Path, "temp_war2");

            Map<String, String> differences = compareDirectories(dir1, dir2);

            if (differences.isEmpty()) {
                System.out.println("Os WARs são iguais!");
            } else {
                System.out.println("\n=== Diferenças encontradas ===");

                Map<String, List<String>> categorizedDifferences = new HashMap<>();
                differences.forEach((file, diff) -> {
                    categorizedDifferences.computeIfAbsent(diff, k -> new ArrayList<>()).add(file);
                });

                categorizedDifferences.forEach((category, files) -> {
                    String color = category.contains("ausente") ? "\u001B[31m" : "\u001B[34m";
                    System.out.println("\n" + color + category + ":\u001B[0m");
                    files.forEach(file -> System.out.println("  - " + file));
                });

                System.out.println("\n\u001B[31m Total de diferenças: " + differences.size() + "\u001B[0m");
            }

            deleteDirectory(dir1);
            deleteDirectory(dir2);
        } catch (Exception e) {
            System.err.println("Erro durante a execução: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Path extractWar(String warPath, String tempDirName) throws IOException {
        Path tempDir = Files.createTempDirectory(tempDirName);
        try (ZipFile zipFile = new ZipFile(warPath)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File entryFile = new File(tempDir.toFile(), entry.getName());
                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                } else {
                    entryFile.getParentFile().mkdirs(); // Garante que os diretórios sejam criados
                    try (InputStream is = zipFile.getInputStream(entry);
                         OutputStream os = new FileOutputStream(entryFile)) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = is.read(buffer)) > 0) {
                            os.write(buffer, 0, length);
                        }
                    }
                }
            }
        }
        return tempDir;
    }

    private static Map<String, String> compareDirectories(Path dir1, Path dir2) throws IOException, NoSuchAlgorithmException {
        Map<String, String> differences = new HashMap<>();

        Files.walk(dir1).forEach(path1 -> {
            Path relativePath = dir1.relativize(path1);
            Path path2 = dir2.resolve(relativePath);

            if (!Files.exists(path2)) {
                differences.put(relativePath.toString(), "Arquivo ausente no WAR2");
                return;
            }

            if (Files.isDirectory(path1) != Files.isDirectory(path2)) {
                differences.put(relativePath.toString(), "Tipo de arquivo diferente");
                return;
            }

            try {
                String hash1 = getFileChecksum(path1);
                String hash2 = getFileChecksum(path2);

                if (!hash1.equals(hash2)) {
                    differences.put(relativePath.toString(), "Conteúdo diferente");
                }
            } catch (IOException | NoSuchAlgorithmException e) {
                System.err.println("Erro ao calcular checksum: " + e.getMessage());
                differences.put(relativePath.toString(), "Erro ao verificar arquivo");
            }
        });

        Files.walk(dir2).forEach(path2 -> {
            Path relativePath = dir2.relativize(path2);
            Path path1 = dir1.resolve(relativePath);
            if (!Files.exists(path1)) {
                differences.put(relativePath.toString(), "Arquivo ausente no WAR1");
            }
        });

        return differences;
    }

    private static String getFileChecksum(Path file) throws IOException, NoSuchAlgorithmException {
        if (Files.isDirectory(file)) return ""; // Ignora diretórios

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        }
        byte[] digest = md.digest();
        return bytesToHex(digest);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private static void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(file -> {
                    if (!file.delete()) {
                        System.err.println("Falha ao deletar: " + file.getAbsolutePath());
                    }
                });
        }
    }
}