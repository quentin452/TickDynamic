package com.wildex999.tickdynamic;

import java.util.TimerTask;

//Every second, take

public class TimerTickTask extends TimerTask {

	@Override
	public void run() {
		try {
			TickDynamicMod.instance.tpsMutex.acquire();

			if (TickDynamicMod.instance.tpsList.size() >= TickDynamicMod.instance.tpsAverageSeconds)
				TickDynamicMod.instance.tpsList.removeFirst();
			TickDynamicMod.instance.tpsList.add(TickDynamicMod.instance.tickCounter);
			TickDynamicMod.instance.tickCounter = 0;

		} catch (InterruptedException e) {
			TickDynamicMod.logError("Exception during TPS Calculation:");
			e.printStackTrace();
		} finally {
			if (TickDynamicMod.instance != null && TickDynamicMod.instance.tpsMutex != null)
				TickDynamicMod.instance.tpsMutex.release();
		}

	}

}
