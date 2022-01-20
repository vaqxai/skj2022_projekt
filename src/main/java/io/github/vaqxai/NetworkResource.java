package io.github.vaqxai;

import java.util.HashMap;

public class NetworkResource {

	private HashMap<String, Integer> owners = new HashMap<>(); // X is owner of 5 counts of this resource, etc...
	private String identifier = "?"; // Resource's name/identifier
	private String holder = null; // Address of the resource's holding node TODO: Is this necessary?
	private int lockedAmount = 0; // How much of the resource is currently locked by other nodes or for processing
	private int availableAmount = 0; // How much of this resource is currently available for locking


	public HashMap<String, Integer> getOwners(){
		return owners;
	}

	public NetworkResource(String identifier, String holder, int amount){
		this.identifier = identifier;
		this.holder = holder;
		this.availableAmount = amount;
	}

	public int lock(int amount){
		if(this.availableAmount >= amount && amount > 0){
			this.lockedAmount += amount;
			this.availableAmount -= amount;
			return amount;
		} else if (this.availableAmount < amount) {
			int partiallyLocked = this.availableAmount;
			this.lockedAmount += this.availableAmount;
			this.availableAmount = 0;
			return partiallyLocked;
		} else {
			throw new RuntimeException("Tried to lock wrong amount of resources");
		}
	}

	public boolean reserve(String owner, int amount){
		if(this.lockedAmount >= amount){
			this.lockedAmount -= amount;
			this.owners.put(owner, amount); // X now owns y amount of this resource on this node.
			return true;
		} else {
			return false;
		}
	}

	public void unlock(int amount){
		if(this.lockedAmount >= amount && amount > 0){
			this.lockedAmount -= amount;
			this.availableAmount += amount;
		} else { // Unlock everything
			int unlockedAmount = this.lockedAmount;
			this.lockedAmount -= unlockedAmount;
			this.availableAmount += unlockedAmount;
		}
	}

	public String getIdentifier(){
		return identifier;
	}

	public int getAvailable(){
		return availableAmount;
	}

	public int getLocked(){
		return lockedAmount;
	}
	
}
