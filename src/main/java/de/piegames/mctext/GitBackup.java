package de.piegames.mctext;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

public class GitBackup {

	public GitBackup() {
	}

	private void unzip(Path compressed, Path destination) throws IOException {
		byte[] buffer = new byte[1024];
		ZipInputStream zis = new ZipInputStream(Files.newInputStream(compressed));
		ZipEntry zipEntry = zis.getNextEntry();
		while (zipEntry != null) {
			String fileName = zipEntry.getName();
			Path path = destination.resolve(fileName);
			System.out.println("Unpacking " + fileName + " to " + path);
			Files.createDirectories(path.getParent());
			OutputStream fos = Files.newOutputStream(path);
			int len;
			while ((len = zis.read(buffer)) > 0) {
				fos.write(buffer, 0, len);
			}
			fos.close();
			zipEntry = zis.getNextEntry();
		}
		zis.closeEntry();
		zis.close();
	}

	static Git openOrCreate(Path gitDirectory) throws IOException, GitAPIException {
		Git git;
		FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
		repositoryBuilder.addCeilingDirectory(gitDirectory.resolve(".git").toFile());
		repositoryBuilder.findGitDir(gitDirectory.resolve(".git").toFile());
		if (repositoryBuilder.getGitDir() == null) {
			git = Git.init().setDirectory(gitDirectory.toFile()).call();
		} else {
			git = new Git(repositoryBuilder.build());
		}
		Ref headRef = git.getRepository().findRef(Constants.HEAD);
		if (headRef == null || headRef.getObjectId() == null) {
			git.commit().setMessage("Initial commit").call();
		}
		return git;
	}

	public void onBackup(File backupZip)
			throws IOException, RefAlreadyExistsException, RefNotFoundException, InvalidRefNameException, CheckoutConflictException, GitAPIException {
		Path path = backupZip.toPath();
		Path gitPath = path.getParent().resolve("backup-git");
		if (!Files.exists(gitPath))
			Files.createDirectory(gitPath);
		System.out.println(path + " -> " + gitPath);

		try (Git git = openOrCreate(gitPath)) {
			Path unpacked = Files.createTempDirectory(backupZip.getName());

			System.out.println("Check out branch");
			try {
				git.checkout().setName("backup").setCreateBranch(true).call();
			} catch (org.eclipse.jgit.api.errors.RefAlreadyExistsException e) {
				git.checkout().setName("backup").call();
			}

			System.err.println("Unpacking to " + unpacked);
			unzip(path, unpacked);

			System.out.println("Convert world");
			BackupHelper backup = new BackupHelper(true, false, false, 1, false, true, false, false, true);
			Path world = gitPath.resolve("world");
			if (!Files.exists(world))
				Files.createDirectory(world);
			try {
				backup.backupWorld(unpacked, world);
			} catch (IOException | RuntimeException e) {
				e.printStackTrace();
				return;
			}
			System.out.println("Staging files");
			git.add().addFilepattern(".").call();
			System.out.println("Committing");
			git.commit().setMessage("Backup " + backupZip.getName()).call();
		}
	}

	public static void main(String[] args) throws Exception {
		new GitBackup().onBackup(new File("/run/media/piegames/STEAM/backup/Backup--world--2018-6-14--22-4.zip"));
		System.out.println("Graceful exit");
	}
}