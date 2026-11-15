package dan200.computer.core;

import javax.annotation.Nonnull;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;


public class ModifiedBufferedWriter extends BufferedWriter {
	private final FileSystem.Mount mount;

	public ModifiedBufferedWriter(Writer out, FileSystem.Mount mount) {
		super(out);
		this.mount = mount;
	}

	public void write(@Nonnull String s, int off, int len) throws IOException {
		int amount = s.getBytes().length;
		synchronized (this.mount) {
			if (this.mount.atMaximum(amount)) {
				close();
				throw new FileSystemException("You have ran out of storage.");
			}
		}
		super.write(s, off, len);
		synchronized (this.mount) {
			this.mount.addCurrentSize(amount);
		}
	}
}
