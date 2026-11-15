package dan200.computer.core;

import java.io.File;
import java.util.Objects;


public class FileUtil {
	public static long calculateSize(File directory) {
		long length = 0L;
		if (directory == null || !directory.isDirectory() || directory.listFiles() == null) {
			return length;
		}
		for (File file : Objects.requireNonNull(directory.listFiles())) {
			if (file.isDirectory()) {
				length += calculateSize(file);
			}
			else {
				length += file.length();
			}
		}
		return length;
	}
}
