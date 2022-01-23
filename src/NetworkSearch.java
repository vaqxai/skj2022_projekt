import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

/**
 * Utility class that represents an ongoing network search
 */
public class NetworkSearch {

	/** 
	 * All network searches ever conducted on this node (or all nodes if nodes ran on threads derived from same main thread)
	*/
	public static HashMap<String, NetworkSearch> all = new HashMap<>();

	/**
	 * Returns a search conducted from the thread this method is called from, found in all searches ever conducted
	 * @param searchID the search's identifier
	 * @return the search object if found, null otherwise
	 */
	public static NetworkSearch getByID(String searchID){
		return all.get(Thread.currentThread().getId() + searchID);
	}
	
	/** The identifier of this search (ClientID:sequentialNumber) */
	private String searchID = "N/A";

	/** How many searches are to be performed */
	private int searchesLeft = 0;

	/** What is this searching for? */
	private HashMap<String, Integer> manifest = new HashMap<>();

	/** What is this still searching for? */
	private HashMap<String, Integer> remaining = new HashMap<>();

	/** Which addresses don't have any of what it's seaching for */
	private ArrayList<String> failedHosts = new ArrayList<>();

	/** Is this the first or the second search (each 'search' request can be ran twice) in the series */
	private boolean firstSearch;

	/**
	 * Primary constructor for a network search
	 * @param ID assigned ID for this search
	 * @param requestAmount amount of requests that will be sent by this search
	 * @param manifest resources that this search is looking for
	 */
	public NetworkSearch(String ID, int requestAmount, HashMap<String, Integer> manifest){
		this.searchID = ID;
		this.searchesLeft = requestAmount;
		this.manifest = new HashMap<>(manifest);
		this.remaining = new HashMap<>(manifest);

		NetworkSearch.all.put(Thread.currentThread().getId() + ID, this);
	}

	/**
	 * The algorithm conducts searches in a two-staged manner (if sent directly by a request originator)
	 * @return current status of this search
	 */
	public boolean isFirstSearch(){
		return firstSearch;
	}

	/**
	 * Marks this search as being on its second stage
	 */
	public void makeSecond(){
		this.firstSearch = false;
	}

	/**
	 * Marks this search as being on its first stage
	 */
	public void makeFirst(){
		this.firstSearch = true;
	}

	/**
	 * 
	 * @return node-wide search identifier (clientName:sequentialNumber)
	 */
	public String getID(){
		return this.searchID;
	}

	/**
	 * 
	 * @return amount of searches left to perform for this operation to complete
	 */
	public int getAmountLeft(){
		return this.searchesLeft;
	}

	/**
	 * Changes the amount of requests left in this search
	 * @param newAmount the amount of requests still left to be performed
	 */
	public void setAmountLeft(int newAmount){
		this.searchesLeft = newAmount;
	}

	/**
	 * Offsets this searches amount of searches left by a given amount
	 * @param offset amount to offset by
	 */
	public void incAmountLeft(int offset){
		this.searchesLeft += offset;
	}
	
	/**
	 * 
	 * @param resName the name of the resource
	 * @return amount that is still to be locked
	 */
	public int getResourceLeft(String resName){
		return remaining.get(resName);
	}

	/**
	 * 
	 * @param resName the name of the resource
	 * @return amount of this resource in search manifest
	 */
	public int getResourceTotal(String resName){
		return manifest.get(resName);
	}

	/**
	 * 
	 * @return entire manifest key: resource name value: amount
	 */
	public HashMap<String, Integer> getManifest(){
		return this.manifest;
	}

	/**
	 * 
	 * @return remaining parts of manifest key: resource name value: amount
	 */
	public HashMap<String, Integer> getRemaining(){
		return this.remaining;
	}

	/**
	 * Checks if a provided address has any of the resources this search is looking for
	 * @param address string address formatted as ip:port
	 * @return true if there are no resources this search needs on that host
	 */
	public boolean isFailedHost (String address){
		return this.failedHosts.contains(address);
	}

	/**
	 * Marks a host as failed, that it has none of the resources this search is looking for.
	 * @param address address:port string of the failed host
	 */
	public void addFailedHost (String address){
		this.failedHosts.add(address);
	}

	/**
	 * Converts a hashmap entry to a formatted string
	 * @param entry entry to convert to a key:value string
	 * @return key:value string
	 */
	private String entryToString(Entry<String, Integer> entry){
		return " " + entry.getKey() + ":" + entry.getValue();
	}

	/**
	 * Returns the manifest as a space-separated entry-string
	 * @return converted manifest
	 */
	public String getManifestString(){
		String manifestString = "";
		for(Entry<String, Integer> manifestEntry : this.getManifest().entrySet()){
			manifestString += entryToString(manifestEntry);
		}
		return manifestString;
	}

	/**
	 * Returns the remaining items as a space-separated entry-string
	 * @return converted remaining items
	 */
	public String getRemainingString(){
		String remainingString = "";
		for(Entry<String, Integer> remainingEntry : this.getRemaining().entrySet()){
			remainingString += entryToString(remainingEntry);
		}
		return remainingString;
	}

	/**
	 * Assigns a new value to a remaining entry
	 * @param identifier key to the value
	 * @param newValue new value to be assigned
	 */
	public void modifyRemainingEntry(String identifier, int newValue){
		this.remaining.replace(identifier, newValue);
	}

	/**
	 * Increments a remaining entry by a given value
	 * @param identifier key to the value
	 * @param amount amount by which to increment the value
	 */
	public void incrementRemainingEntry(String identifier, int amount){
		this.remaining.replace(identifier, this.remaining.get(identifier) + amount);
	}

	public String toString(){
		return "[Search: " + searchID + " Manifest: " + getManifestString() + " Remaining: " + getRemainingString() + " Failed Hosts: " + failedHosts + "]";
	}

}
