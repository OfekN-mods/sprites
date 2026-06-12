package com.ofekn.mcsprites;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

public class GhPagesSync {

    private static final String GH_PAGES_BRANCH = "gh-pages";

    // Repo root is the parent of the gitignored "run" directory.
    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    // Worktree checked out to gh-pages, kept inside the repo (gitignored).
    private static final Path GH_PAGES_WORKTREE = REPO_ROOT.resolve(".gh-pages-worktree");

    /**
     * Copies all files from the given source path (e.g. run/gh-pages/items)
     * into the given folder inside the gh-pages worktree, commits, and pushes.
     * Does not touch the main repo's working tree or current branch.
     *
     * @param sourcePath directory containing the new/updated icon files
     */
    public static void pushGHPages(Path sourcePath) {
        try {
            ensureWorktreeExists();
            Path absoluteSource = sourcePath.toAbsolutePath().normalize();

            String currentBranch = currentBranchOf(GH_PAGES_WORKTREE);
            if (!GH_PAGES_BRANCH.equals(currentBranch)) {
                run("git", "checkout", GH_PAGES_BRANCH);
            }

            // Remove everything tracked so stale files get cleaned up.
            run("git", "rm", "-rf", "--quiet", "--ignore-unmatch", ".");

            copyDirectory(absoluteSource, GH_PAGES_WORKTREE);

            run("git", "add", "-A");

            int status = runAllowFail("git", "diff", "--cached", "--quiet");
            if (status == 0) {
                System.out.println("No changes to commit.");
            } else {
                run("git", "commit", "-m", "Update gh-pages");
                run("git", "push", "origin", GH_PAGES_BRANCH);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to push to " + GH_PAGES_BRANCH, e);
        }
    }

    private static void ensureWorktreeExists() throws IOException, InterruptedException {
        if (Files.isDirectory(GH_PAGES_WORKTREE)) {
            return;
        }

        if (!branchExists(GH_PAGES_BRANCH)) {
            // Create an orphan gh-pages branch with an empty initial commit,
            // without disturbing the main repo's current checkout.
            // Use a temp worktree pointed at a new orphan branch.
            runInRepoRoot("worktree", "add", "--detach", GH_PAGES_WORKTREE.toString());
            runInWorktree(GH_PAGES_WORKTREE, "checkout", "--orphan", GH_PAGES_BRANCH);
            runInWorktree(GH_PAGES_WORKTREE, "rm", "-rf", "--quiet", ".");
            Files.writeString(GH_PAGES_WORKTREE.resolve(".gitkeep"), "");
            runInWorktree(GH_PAGES_WORKTREE, "add", ".gitkeep");
            runInWorktree(GH_PAGES_WORKTREE, "commit", "-m", "Initial gh-pages branch");
            runInWorktree(GH_PAGES_WORKTREE, "push", "-u", "origin", GH_PAGES_BRANCH);
        } else {
            runInRepoRoot("worktree", "add", GH_PAGES_WORKTREE.toString(), GH_PAGES_BRANCH);
        }
    }

    private static String currentBranchOf(Path dir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD");
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes()).trim();
        process.waitFor();
        return output;
    }

    private static boolean branchExists(String branch) throws IOException, InterruptedException {
        // Check local branches and remote branches.
        if (runAllowFailIn(REPO_ROOT, "git", "show-ref", "--verify", "--quiet", "refs/heads/" + branch) == 0) {
            return true;
        }
        runAllowFailIn(REPO_ROOT, "git", "fetch", "origin", branch);
        return runAllowFailIn(REPO_ROOT, "git", "show-ref", "--verify", "--quiet", "refs/remotes/origin/" + branch) == 0;
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

    private static void run(String... gitArgs) throws IOException, InterruptedException {
        runIn(GH_PAGES_WORKTREE, gitArgs);
    }

    private static int runAllowFail(String... gitArgs) throws IOException, InterruptedException {
        return runAllowFailIn(GH_PAGES_WORKTREE, gitArgs);
    }

    private static void runInRepoRoot(String... args) throws IOException, InterruptedException {
        runIn(REPO_ROOT, prependGit(args));
    }

    private static void runInWorktree(Path dir, String... args) throws IOException, InterruptedException {
        runIn(dir, prependGit(args));
    }

    private static String[] prependGit(String... args) {
        String[] full = new String[args.length + 1];
        full[0] = "git";
        System.arraycopy(args, 0, full, 1, args.length);
        return full;
    }

    private static void runIn(Path dir, String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(dir.toFile());
        pb.inheritIO();
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed (" + exitCode + "): " + String.join(" ", command));
        }
    }

    private static int runAllowFailIn(Path dir, String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(dir.toFile());
        pb.inheritIO();
        Process process = pb.start();
        return process.waitFor();
    }
}