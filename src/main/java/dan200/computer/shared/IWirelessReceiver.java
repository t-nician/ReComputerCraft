package dan200.computer.shared;

import net.minecraft.server.Vec3D;


public interface IWirelessReceiver {
	int getID();
    boolean isWWW();

	Vec3D getWorldPosition();

	void receive(int var1, String var2, double var3);
    void receiveGlobal(int var1, String var2, double var3);
}
