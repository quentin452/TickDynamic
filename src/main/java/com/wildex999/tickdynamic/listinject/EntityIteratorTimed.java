package com.wildex999.tickdynamic.listinject;

import com.wildex999.tickdynamic.TickDynamicMod;

import java.util.*;


/*
 * Iterator will continue from each group at the given offset and only iterate for the given number
 * of Entities as dictated by the time manager.
 * It will also take care of timing each group.
 */

public class EntityIteratorTimed implements Iterator<EntityObject> {

	private ListManager list;
	private int currentAge; //Used to verify if iterator is still valid(Concurrent modification)

	private int remainingCount; //Number of entities left to update for this group
	private int currentOffset; //Offset in current entity list
	private int updateCount;
	private boolean startedTimer;

	private EntityGroup currentGroup;
	private EntityObject currentObject;
	private Iterator<EntityGroup> groupIterator;
	private List<EntityObject> entityList;

	public EntityIteratorTimed(ListManager list, int currentAge) {
		this.list = list;
		this.currentAge = currentAge;
		this.groupIterator = list.getGroupIterator();
		this.remainingCount = 0;
		this.startedTimer = false;
		this.entityList = null;
	}

	private void handleConcurrentModification() {
		TickDynamicMod.logDebug("Concurrent modification detected in age");
	}

	private void startTimerIfNeeded() {
		if (!startedTimer) {
			startedTimer = true;
			currentGroup.timedGroup.startTimer();
		}
	}

	@Override
	public boolean hasNext() {
		if (!ageMatches(list.age)) {
			handleConcurrentModification();
		}

		if (remainingCount > 0 && entityList != null && !entityList.isEmpty() && currentOffset < entityList.size()) {
			return true;
		}

		if (startedTimer && currentGroup != null) {
			currentGroup.timedGroup.endUpdateObjects(updateCount);
			currentGroup.timedGroup.endTimer();
		}

		updateCount = 0;
		currentGroup = null;
		startedTimer = false;
		entityList = null;

		while (entityList == null) {
			if (!groupIterator.hasNext()) {
				return false;
			}
			currentGroup = groupIterator.next();
			entityList = currentGroup.entities;

			if (entityList.isEmpty()) {
				entityList = null;
			} else {
				currentOffset = currentGroup.timedGroup.startUpdateObjects();
				remainingCount = currentGroup.timedGroup.getUpdateCount();
				updateCount = 0;
				startTimerIfNeeded();
			}
		}

		return true;
	}

	@Override
	public EntityObject next() {
			if (!ageMatches(list.age)) {
				handleConcurrentModification();
			}

			if (entityList == null) {
				throw new NoSuchElementException("No valid entity list available.");
			}

			if (currentOffset < entityList.size()) {
				currentObject = entityList.get(currentOffset);
				remainingCount--;
				currentOffset++;
				updateCount++;
				startTimerIfNeeded();
				return currentObject;
			} else {
				throw new NoSuchElementException("Reached the end of the entity list.");
			}
	}

	@Override
	public void remove() {
		if (currentObject != null) {
			if (list.remove(currentObject)) {
				currentAge++;
				currentOffset--;
			} else {
				TickDynamicMod.logError("Failed to remove: " + currentObject + " from the loaded entity list!");
			}

			if (currentOffset < 0) {
				currentOffset = 0;
			}
		}
	}

	public boolean ageMatches(int age) {
		return currentAge == age;
	}
}