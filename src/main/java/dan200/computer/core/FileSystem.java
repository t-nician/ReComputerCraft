package dan200.computer.core;

import net.minecraft.server.mod_ComputerCraft;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;


public class FileSystem {
	static final boolean $assertionsDisabled;

	static {
		$assertionsDisabled = !FileSystem.class.desiredAssertionStatus();
	}

	private final Mount m_rootMount;
	private final Map<String, Mount> m_mounts;

	public FileSystem(File file, boolean flag) throws FileSystemException {
		if (file.exists() && file.isDirectory()) {
			this.m_rootMount = new Mount("", file, flag, mod_ComputerCraft.maxComputerFolderSize);
			this.m_mounts = new HashMap<String, Mount>();
			return;
		}
		throw new FileSystemException("No such directory");
	}

	public synchronized void mount(String s, File file, boolean flag) throws FileSystemException {
		if (!file.exists()) {
			throw new FileSystemException("No such path");
		}
		s = sanitizePath(s);
		if (s.contains("..")) {
			throw new FileSystemException("Cannot mount below the root");
		}
		this.m_mounts.remove(s);
		this.m_mounts.put(s, new Mount(s, file, flag, mod_ComputerCraft.maxComputerFolderSize));
	}

	public synchronized void unmount(String s) {
		s = sanitizePath(s);
		this.m_mounts.remove(s);
	}

	public synchronized String combine(String s, String s1) {
		s = sanitizePath(s);
		s1 = sanitizePath(s1);
		if (s.isEmpty()) {
			return s1;
		}
		if (s1.isEmpty()) {
			return s;
		}
		return sanitizePath(s + '/' + s1);
	}

	public synchronized boolean contains(String s, String s1) {
		return _contains(sanitizePath(s), sanitizePath(s1));
	}

	public synchronized String getName(String s) {
		s = sanitizePath(s);
		if (s.isEmpty()) {
			return "root";
		}
		int i = s.lastIndexOf('/');
		if (i >= 0) {
			return s.substring(i + 1);
		}
		return s;
	}

	public synchronized long getSize(String s) throws FileSystemException {
		File file = getRealPath(sanitizePath(s));
		if (file.exists() && !file.isDirectory()) {
			return file.length();
		}
		throw new FileSystemException("No such file");
	}

	public synchronized String[] list(String s) throws FileSystemException {
		s = sanitizePath(s);
		File file = getRealPath(s);
		if (!file.exists() || !file.isDirectory()) {
			throw new FileSystemException("Not a directory");
		}
		String[] as = file.list();
		if (as == null) {
			return new String[0];
		}
		ArrayList<String> arraylist = new ArrayList<>(as.length);
		for (String a : as) {
			File file2 = new File(file, a);
			if (file2.exists()) {
				arraylist.add(a);
			}
		}
		for (Mount mount1 : this.m_mounts.values()) {
			if (mount1.parentLocation.equals(s)) {
				arraylist.add(mount1.name);
			}
		}
		return arraylist.toArray(new String[0]);
	}

	public synchronized boolean exists(String s) throws FileSystemException {
		File file = getRealPath(sanitizePath(s));
		return file.exists();
	}

	public synchronized boolean isDir(String s) throws FileSystemException {
		File file = getRealPath(sanitizePath(s));
		return (file.exists() && file.isDirectory());
	}

	public synchronized boolean isReadOnly(String s) throws FileSystemException {
		Mount mount1 = getMount(sanitizePath(s));
		if (mount1 == null) {
			throw new FileSystemException("Invalid path");
		}
		return !(!s.equals(mount1.location) && !mount1.readOnly);
	}

	public synchronized void makeDir(String s) throws FileSystemException {
		if (isReadOnly(s)) {
			throw new FileSystemException("Access denied");
		}
		File file = getRealPath(sanitizePath(s));
		if (file.exists()) {
			if (file.isDirectory()) {
				return;
			}
			throw new FileSystemException("File exists");
		}
		doSizeCheck(s);
		boolean flag = file.mkdirs();
		if (!flag) {
			throw new FileSystemException("Access denied");
		}
	}

	private synchronized void recurseDelete(File file, Mount mount) throws FileSystemException {
		if (file.isDirectory()) {
			String[] as = file.list();
			if (as == null) {
				return;
			}
			for (String a : as) {
				recurseDelete(new File(file, a), mount);
			}
		}
		long length = file.length();
		boolean flag = file.delete();
		if (!flag) {
			throw new FileSystemException("Access denied");
		}
		synchronized (mount) {
			mount.subCurrentSize(length);
		}
	}

	public synchronized void delete(String s) throws FileSystemException {
		s = sanitizePath(s);
		if (isReadOnly(s)) {
			throw new FileSystemException("Access denied");
		}
		File file = getRealPath(s);
		Mount mount = getMount(s);
		if (file.exists()) {
			recurseDelete(file, mount);
		}
	}

	public synchronized void move(String s, String s1) throws FileSystemException {
		s = sanitizePath(s);
		s1 = sanitizePath(s1);
		if (isReadOnly(s) || isReadOnly(s1)) {
			throw new FileSystemException("Access denied");
		}
		File file = getRealPath(s);
		File file2 = getRealPath(s1);
		if (!file.exists()) {
			throw new FileSystemException("No such file");
		}
		if (file2.exists()) {
			throw new FileSystemException("File exists");
		}
		if (_contains(s, s1)) {
			throw new FileSystemException("Can't move a directory inside itself");
		}
		Mount mountOne = getMount(s);
		Mount mountTwo = getMount(s1);
		if (mountOne == null || mountTwo == null) {
			throw new FileSystemException("Invalid directory move operation");
		}
		if (!mountOne.equals(mountTwo)) {
			doSizeCheck(
					mountTwo,
					file.isDirectory() ? FileUtil.calculateSize(file) : file.length());
		}
		long length = file.length();
		boolean flag = file.renameTo(file2);
		if (!flag) {
			throw new FileSystemException("Access denied");
		}
		if (!mountOne.equals(mountTwo)) {
			synchronized (mountOne) {
				mountOne.subCurrentSize(length);
			}
			synchronized (mountTwo) {
				mountTwo.addCurrentSize(length);
			}
		}
	}

	private synchronized void recurseCopy(File file, File file1, Mount mount) throws FileSystemException {
		if (!$assertionsDisabled && !file.exists()) {
			throw new AssertionError();
		}
		if (mount == null) {
			mount = getMount(file1.toString());
		}
		if (file.isDirectory()) {
			boolean flag = file1.mkdirs();
			if (!flag) {
				throw new FileSystemException("Access denied");
			}
			String[] as = file.list();
			if (as != null) {
				for (String a : as) {
					recurseCopy(new File(file, a), new File(file1, a), mount);
				}
			}
		}
		else {
			if (mount == null) {
				throw new FileSystemException("Invalid mount operation");
			}
			boolean atMax;
			long amount = file.length();
			synchronized (mount) {
				atMax = mount.atMaximum(amount);
			}
			if (atMax) {
				throw new FileSystemException("You have ran out of storage.");
			}
			FileChannel sourceFileChannel = null;
			FileChannel destinationFileChannel = null;
			try {
				sourceFileChannel = (new FileInputStream(file)).getChannel();
				destinationFileChannel = (new FileOutputStream(file1)).getChannel();
				synchronized (mount) {
					mount.addCurrentSize(amount);
				}
				destinationFileChannel.transferFrom(sourceFileChannel, 0L, sourceFileChannel.size());
			} catch (IOException e) {
				throw new FileSystemException("Access denied");
			} finally {
				try {
					if (sourceFileChannel != null) {
						sourceFileChannel.close();
					}
					if (destinationFileChannel != null) {
						destinationFileChannel.close();
					}
				} catch (IOException ignored) {
				}
			}
			try {
				if (sourceFileChannel != null) {
					sourceFileChannel.close();
				}
				if (destinationFileChannel != null) {
					destinationFileChannel.close();
				}
			} catch (IOException ignored) {
			}
		}
	}

	public synchronized void copy(String s, String s1) throws FileSystemException {
		s = sanitizePath(s);
		s1 = sanitizePath(s1);
		if (isReadOnly(s1)) {
			throw new FileSystemException("Access denied");
		}
		File file = getRealPath(s);
		File file2 = getRealPath(s1);
		if (!file.exists()) {
			throw new FileSystemException("No such file");
		}
		if (file2.exists()) {
			throw new FileSystemException("File exists");
		}
		if (_contains(s, s1)) {
			throw new FileSystemException("Can't copy a directory inside itself");
		}
		Mount mount = getMount(s1);
		doSizeCheck(mount);
		recurseCopy(file, file2, mount);
	}

	public synchronized BufferedReader openForRead(String s) throws FileSystemException {
		File file = getRealPath(sanitizePath(s));
		if (!file.exists()) {
			throw new FileSystemException("File not found");
		}
		if (file.isDirectory()) {
			throw new FileSystemException("Cannot read from directory");
		}
		try {
			return new BufferedReader(new FileReader(file));
		} catch (IOException e) {
			throw new FileSystemException("Access denied");
		}
	}

	public synchronized BufferedWriter openForWrite(String s, boolean flag) throws FileSystemException {
		s = sanitizePath(s);
		if (isReadOnly(s)) {
			throw new FileSystemException("Access denied");
		}
		File file = getRealPath(s);
		if (file.exists() && file.isDirectory()) {
			throw new FileSystemException("Cannot write to directory");
		}
		Mount mount = getMount(s);
		doSizeCheck(mount);
		try {
			return new ModifiedBufferedWriter(new FileWriter(file.toString(), flag), mount);
		} catch (IOException ioexception) {
			throw new FileSystemException("Access denied");
		}
	}

	public synchronized BufferedInputStream openForBinaryRead(String s) throws FileSystemException {
		File file = getRealPath(sanitizePath(s));
		if (!file.exists()) {
			throw new FileSystemException("File not found");
		}
		if (file.isDirectory()) {
			throw new FileSystemException("Cannot read from directory");
		}
		try {
			return new BufferedInputStream(Files.newInputStream(file.toPath()));
		} catch (IOException e) {
			throw new FileSystemException("Access denied");
		}
	}

	public synchronized BufferedOutputStream openForBinaryWrite(String s, boolean flag) throws FileSystemException {
		s = sanitizePath(s);
		if (isReadOnly(s)) {
			throw new FileSystemException("Access denied");
		}
		File file = getRealPath(s);
		if (file.exists() && file.isDirectory()) {
			throw new FileSystemException("Cannot write to directory");
		}
		Mount mount = getMount(s);
		doSizeCheck(mount);
		try {
			return new ModifiedBufferedOutputStream(new FileOutputStream(file.toString(), flag), mount);
		} catch (IOException e) {
			throw new FileSystemException("Access denied");
		}
	}

	private String sanitizePath(String s) {
		s = s.replace('\\', '/');
		String[] as = s.split("/");
		Stack<String> stack = new Stack<String>();
		for (String s2 : as) {
			if (!s2.isEmpty() &&
					!s2.equals(".")) {
				if (s2.equals("..")) {
					if (!stack.empty()) {
						String s3 = stack.peek();
						if (!s3.equals("..")) {
							stack.pop();
						}
						else {
							stack.push("..");
						}
					}
					else {
						stack.push("..");
					}
				}
				else {
					stack.push(s2);
				}
			}
		}
		StringBuilder stringbuilder = new StringBuilder();
		Iterator<String> iterator = stack.iterator();
		while (iterator.hasNext()) {
			String s4 = iterator.next();
			stringbuilder.append(s4);
			if (iterator.hasNext()) {
				stringbuilder.append('/');
			}
		}
		return stringbuilder.toString();
	}

	private Mount getMount(String s) {
		Iterator<Mount> iterator = this.m_mounts.values().iterator();
		Mount mount1 = null;
		int i = 99;
		while (iterator.hasNext()) {
			Mount mount2 = iterator.next();
			if (_contains(mount2.location, s)) {
				int j = mount2.toLocal(s).length();
				if (mount1 != null && j >= i) {
					continue;
				}
				mount1 = mount2;
				i = j;
			}
		}
		if (mount1 != null) {
			return mount1;
		}
		if (_contains(this.m_rootMount.location, s)) {
			return this.m_rootMount;
		}
		return null;
	}

	private File getRealPath(String s) throws FileSystemException {
		Mount mount1 = getMount(s);
		if (mount1 == null) {
			throw new FileSystemException("Invalid path.");
		}
		return new File(mount1.realPath, mount1.toLocal(s));
	}

	private boolean _contains(String s, String s1) {
		return (!s1.contains("..") && (s1.equals(s) || s.isEmpty() || s1.startsWith(s + "/")));
	}

	private synchronized void doSizeCheck(String file) throws FileSystemException {
		Mount mount = getMount(file);
		doSizeCheck(mount);
	}

	private synchronized void doSizeCheck(Mount mount) throws FileSystemException {
		doSizeCheck(mount, 0L);
	}

	private synchronized void doSizeCheck(Mount mount, long amount) throws FileSystemException {
		boolean atMax;
		if (mount == null) {
			throw new FileSystemException("Unexpected mount error. Patch problem. Please report this.");
		}
		synchronized (mount) {
			atMax = mount.atMaximum(amount);
		}
		if (atMax) {
			throw new FileSystemException("You have ran out of storage.");
		}
	}

	public class Mount {
		private final long maxSize;
		String name;
		String location;
		String parentLocation;
		File realPath;
		boolean readOnly;
		private long currentSize;

		Mount(String s, File file, boolean flag, long maxSize) {
			this.location = s;
			this.realPath = file;
			this.readOnly = flag;
			int i = this.location.lastIndexOf('/');
			if (i >= 0) {
				this.name = this.location.substring(i + 1);
				this.parentLocation = this.location.substring(0, i);
			}
			else {
				this.name = this.location;
				this.parentLocation = "";
			}
			this.maxSize = maxSize;
			this.currentSize = FileUtil.calculateSize(this.realPath);
		}

		public long getMaxSize() {
			return this.maxSize;
		}

		public long getCurrentSize() {
			return this.currentSize;
		}

		public void addCurrentSize(long amount) {
			this.currentSize += amount;
		}

		public void subCurrentSize(long amount) {
			this.currentSize -= amount;
		}

		public boolean atMaximum() {
			return atMaximum(0L);
		}

		public boolean atMaximum(long amount) {
			return (getCurrentSize() + amount > getMaxSize());
		}

		private String toLocal(String s) {
			if (!FileSystem.this._contains(this.location, s)) {
				throw new AssertionError();
			}
			String s2 = s.substring(this.location.length());
			if (s2.startsWith("/")) {
				return s2.substring(1);
			}
			return s2;
		}

		public int hashCode() {
			int result = 1;
			result = 31 * result + getOuterType().hashCode();
			result = 31 * result + (int) (this.currentSize ^ this.currentSize >>> 32L);
			result = 31 * result + ((this.location == null) ? 0 : this.location.hashCode());
			result = 31 * result + (int) (this.maxSize ^ this.maxSize >>> 32L);
			result = 31 * result + ((this.name == null) ? 0 : this.name.hashCode());
			result = 31 * result + ((this.parentLocation == null) ? 0 : this.parentLocation.hashCode());
			result = 31 * result + (this.readOnly ? 1231 : 1237);
			result = 31 * result + ((this.realPath == null) ? 0 : this.realPath.hashCode());
			return result;
		}

		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			Mount other = (Mount) obj;
			if (!getOuterType().equals(other.getOuterType())) {
				return false;
			}
			if (this.currentSize != other.currentSize) {
				return false;
			}
			if (this.location == null) {
				if (other.location != null) {
					return false;
				}
			}
			else if (!this.location.equals(other.location)) {
				return false;
			}
			if (this.maxSize != other.maxSize) {
				return false;
			}
			if (this.name == null) {
				if (other.name != null) {
					return false;
				}
			}
			else if (!this.name.equals(other.name)) {
				return false;
			}
			if (this.parentLocation == null) {
				if (other.parentLocation != null) {
					return false;
				}
			}
			else if (!this.parentLocation.equals(other.parentLocation)) {
				return false;
			}
			if (this.readOnly != other.readOnly) {
				return false;
			}
			if (this.realPath == null) {
				return other.realPath == null;
			}
			else {
				return this.realPath.equals(other.realPath);
			}
		}

		private FileSystem getOuterType() {
			return FileSystem.this;
		}
	}
}
