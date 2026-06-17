package com.ofekn.mcsprites;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

public class GhPagesSync {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String GH_PAGES_BRANCH = "gh-pages";

    // Repo root is the parent of the gitignored "run" directory.
    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    // Worktree checked out to gh-pages, kept inside the repo (gitignored).
    public static final Path DIR = REPO_ROOT.resolve(".gh-pages-worktree");

    public static void prepare() {
        try {
            ensureWorktreeExists();

            // checkout if in the wrong branch
            String currentBranch = currentBranchOf(DIR);
            if (!GH_PAGES_BRANCH.equals(currentBranch)) {
                run("git", "checkout", GH_PAGES_BRANCH);
            }

            // Remove everything tracked so stale files get cleaned up.
            run("git", "rm", "-rf", "--quiet", "--ignore-unmatch", ".");
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Failed to prepare", e);
        }
    }

    /**
     * Commits, and pushes the gh-pages worktree.
     * Does not touch the main repo's working tree or current branch.
     */
    public static void pushGHPages() {
        try {
            run("git", "add", "-A");

            int status = runAllowFail("git", "diff", "--cached", "--quiet");
            if (status == 0) {
                System.out.println("No changes to commit.");
            } else {
                run("git", "commit", "-m", "Update gh-pages");
                run("git", "push", "origin", GH_PAGES_BRANCH);
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Failed to push", e);
        }
    }

    private static void ensureWorktreeExists() throws IOException, InterruptedException {
        if (Files.isDirectory(DIR)) {
            return;
        }

        if (!branchExists(GH_PAGES_BRANCH)) {
            // Create an orphan gh-pages branch with an empty initial commit,
            // without disturbing the main repo's current checkout.
            // Use a temp worktree pointed at a new orphan branch.
            runInRepoRoot("worktree", "add", "--detach", DIR.toString());
            runInWorktree(DIR, "checkout", "--orphan", GH_PAGES_BRANCH);
            runInWorktree(DIR, "rm", "-rf", "--quiet", ".");
            Files.writeString(DIR.resolve(".gitkeep"), "");
            runInWorktree(DIR, "add", ".gitkeep");
            runInWorktree(DIR, "commit", "-m", "Initial gh-pages branch");
            runInWorktree(DIR, "push", "-u", "origin", GH_PAGES_BRANCH);
        } else {
            runInRepoRoot("worktree", "add", DIR.toString(), GH_PAGES_BRANCH);
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

    private static void run(String... gitArgs) throws IOException, InterruptedException {
        runIn(DIR, gitArgs);
    }

    private static int runAllowFail(String... gitArgs) throws IOException, InterruptedException {
        return runAllowFailIn(DIR, gitArgs);
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