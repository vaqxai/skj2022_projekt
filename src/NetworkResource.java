import java.util.HashMap;

/**
 * Represents a network resource on a given node.
 */
public class NetworkResource {

	/**
	 * Represents the ownership of reserved/allocated resources
	 */
	private HashMap<String, Integer> owners = new HashMap<>(); // X is owner of 5 counts of this resource, etc...

	/**
	 * The resource's name
	 */
	private String identifier = "?"; // Resource's name/identifier

	/**
	 * The amount of the resource that is locked but not yet allocated
	 */
	private int lockedAmount = 0; // How much of the resource is currently locked by other nodes or for processing

	/**
	 * The amount of the resource that is available to be locked.
	 */
	private int availableAmount = 0; // How much of this resource is currently available for locking


	/**
	 * 
	 * @return HashMap <OwnerID, Amount> representing who owns what amount of the resource.
	 */
	public HashMap<String, Integer> getOwners(){
		return owners;
	}

	/**
	 * Primary constructor for a network resource.
	 * @param identifier the name of the resource
	 * @param amount the amount of the resource.
	 */
	public NetworkResource(String identifier, int amount){
		this.identifier = identifier;
		this.availableAmount = amount;
	}

	/**
	 * Locks a specified amount of the resource if it is available,
	 * checks if there is enough of the resource and
	 * verifies if the requested amount is positive
	 * @param amount the amount of the resource to lock
	 * @return the amount of the resource that was successfully locked
	 */
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

	/**
	 * Removes a resource lock and reserves the specified amount.
	 * @param owner the client that owns the resource
	 * @param amount the amount of the resource to reserve
	 * @return true if resource was successfully reserved
	 */
	public boolean reserve(String owner, int amount){
		if(this.lockedAmount >= amount){
			this.lockedAmount -= amount;
			this.owners.put(owner, amount); // X now owns y amount of this resource on this node.
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Removes a resource lock
	 * @param amount the amount of the resource to unlock
	 */
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

	/**
	 * 
	 * @return this resource's identifier/name
	 */
	public String getIdentifier(){
		return identifier;
	}

	/**
	 * 
	 * @return the amount of this resource that is available
	 */
	public int getAvailable(){
		return availableAmount;
	}

	/**
	 * 
	 * @return the amount of this resource that is locked
	 */
	public int getLocked(){
		return lockedAmount;
	}

	/**
	 * Diagnostic information about this resource
	 */
	public String toString(){
		return "Name: " + identifier + "; Available: " + availableAmount + "; Locked " + lockedAmount;
	}
	
}
