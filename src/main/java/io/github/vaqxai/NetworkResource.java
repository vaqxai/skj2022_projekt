package io.github.vaqxai;

public class NetworkResource {

	private String owner = null;
	private String identifier = "?";
	private int lockedAmount = 0;
	private int availableAmount = 0;

	public NetworkResource(String identifier, int amount){
		this.identifier = identifier;
		this.availableAmount = amount;
	}

	public void lock(int amount){
		if(this.availableAmount > amount && amount > 0){
			this.lockedAmount += amount;
			this.availableAmount -= amount;
		} else {
			throw new RuntimeException("Tried to lock wrong amount of resources");
		}
	}

	public void reserve(int amount){
		if(this.lockedAmount >= amount){ // Reserve means delete
			this.lockedAmount -= amount;
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

	public void setOwner(String newOwner){
		this.owner = newOwner;
	}

	public String getOwner(){
		return this.owner;
	}
	
}
