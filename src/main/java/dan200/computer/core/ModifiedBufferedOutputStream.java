package dan200.computer.core;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;


public class ModifiedBufferedOutputStream extends BufferedOutputStream {
	private final FileSystem.Mount mount;

	public ModifiedBufferedOutputStream(OutputStream os, FileSystem.Mount mount) {
		super(os);
		this.mount = mount;
	}

	public synchronized void write(int b) throws IOException {
		synchronized (this.mount) {
			if (this.mount.atMaximum(1L)) {
				close();
				throw new FileSystemException("You have ran out of storage.");
			}
		}
		super.write(b);
		synchronized (this.mount) {
			this.mount.addCurrentSize(1L);
		}
	}
}
