package dan200.computer.core;

import net.minecraft.server.mod_ComputerCraft;
import org.luaj.vm2.OrphanedThread;

import java.util.concurrent.LinkedBlockingQueue;


public class ComputerThread {
	private static final Object m_lock = new Object();
	private static final LinkedBlockingQueue<Task> m_taskQueue = new LinkedBlockingQueue<>(mod_ComputerCraft.computerThreadTaskQueueSize);
	private static Thread m_thread = null;
	private static boolean m_busy = false;
	private static boolean m_running = false;
	private static boolean m_stopped = false;
	private static ThreadGroup genericThreadGroup = new ThreadGroup("ReCC-Generic");

	public static void start() {
		synchronized (m_lock) {
			if (m_running) {
				m_stopped = false;
			}
			else {
				m_thread = new Thread(() -> {
					while (true) {
						synchronized (ComputerThread.m_lock) {
							if (ComputerThread.m_stopped) {
								ComputerThread.m_running = false;
								ComputerThread.m_thread = null;
								return;
							}
						}

						try {
							final Task task = ComputerThread.m_taskQueue.take();
							ComputerThread.m_busy = true;
							// Create named thread; allow a maximum stack of 1MB
							Thread thread = new Thread((task.getOwner() != null ? task.getOwner().getThreadGroup() : genericThreadGroup), () -> {
								try {
									task.execute();
								} catch (OrphanedThread ignored) {
								} catch (StackOverflowError e) {
									System.out.println("ReCC: Stack overflow for computer " + (task.getOwner() != null ? task.getOwner().getID() : "???"));
								} catch (OutOfMemoryError e) {
									System.out.println("ReCC: Out of memory for computer " + (task.getOwner() != null ? task.getOwner().getID() : "???"));
								} catch (Exception var2) {
									System.out.println("ReCC: Error running task for computer " + (task.getOwner() != null ? task.getOwner().getID() : "???"));
									var2.printStackTrace();
								}
							}, "ReCC-Computer-" + (task.getOwner() != null ? task.getOwner().getID() : "???"));
							thread.start();
							thread.join(5000L);
							if (thread.isAlive()) {
								Computer computer = task.getOwner();
								if (computer != null) {
									computer.abort();
									thread.join(1250L);
									if (thread.isAlive()) {
										computer.turnOff();
										thread.join(1250L);
									}
									computer.interruptThreads();
									thread.join(500L);
								}

								if (thread.isAlive()) {
									System.out.println("ReCC: Warning! Failed to abort computer " + (computer != null ? computer.getID() : "???") + "!. Dangling lua thread could cause errors.");
									thread.interrupt();
								}
							}
						} catch (InterruptedException ignored) {
						}
						catch (StackOverflowError e) {
							System.out.println("ReCC: Stack overflow for main thread");
							e.printStackTrace();
						} catch (OutOfMemoryError e) {
							System.out.println("ReCC: Out of memory for main thread");
							e.printStackTrace();
						} finally {
							ComputerThread.m_busy = false;
						}
					}
				}, "ReCC-Main");
				m_thread.start();
				m_running = true;
			}
		}
	}

	public static void stop() {
		synchronized (m_lock) {
			if (m_running) {
				m_stopped = true;
				m_thread.interrupt();
			}
		}
	}

	public static void queueTask(ComputerThread.Task task) {
		boolean flag = m_taskQueue.offer(task);
		if (!flag) {
			System.out.println("ReCC: Warning! Computer task queue overflowed. Task from computer " + (task.getOwner() != null ? task.getOwner().getID() : "???") + " rejected!");
		}
	}

	public static boolean isBusy() {
		return m_taskQueue.peek() != null || m_busy;
	}

	public interface Task {
		Computer getOwner();

		void execute();
	}
}
