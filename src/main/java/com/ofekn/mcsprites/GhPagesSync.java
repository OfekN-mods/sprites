package com.ofekn.mcsprites;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

public class GhPagesSync {

    private static final String GH_PAGES_BRANCH = "gh-pages";

    /**
     * Copies all files from the given source path into the given folder
     * on the gh-pages branch, commits, and pushes.
     *
     * @param sourcePath directory containing the new/updated icon files
     * @param targetDir  folder name inside the gh-pages branch (e.g. "items", "blocks")
     */
    public static void pushGHPages(Path sourcePath, String targetDir) {
        try {
            String originalBranch = getCurrentBranch();

            run("git", "checkout", GH_PAGES_BRANCH);

            Path targetPath = Paths.get(targetDir);
            Files.createDirectories(targetPath);

            copyDirectory(sourcePath, targetPath);

            run("git", "add", targetDir);

            int status = runAllowFail("git", "diff", "--cached", "--quiet");
            if (status == 0) {
                System.out.println("No changes to commit.");
            } else {
                run("git", "commit", "-m", "Update " + targetDir + " icons");
                run("git", "push", "origin", GH_PAGES_BRANCH);
            }

            run("git", "checkout", originalBranch);

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to push to " + GH_PAGES_BRANCH, e);
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            stream.forEach(src -> {
                try {
                    Path dest = target.resolve(source.relativize(src));
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to copy " + src, e);
                }
            });
        }
    }

    private static String getCurrentBranch() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes()).trim();
        process.waitFor();
        return output;
    }

    private static void run(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed (" + exitCode + "): " + String.join(" ", command));
        }
    }

    private static int runAllowFail(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();
        Process process = pb.start();
        return process.waitFor();
    }
}