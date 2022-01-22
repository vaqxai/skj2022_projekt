package io.github.vaqxai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

/**
 * Utility class that represents an ongoing network search
 */
public class NetworkSearch {
	
	private String searchID = "N/A";
	private int searchesLeft = 0;
	private HashMap<String, Integer> manifest = new HashMap<>();
	private HashMap<String, Integer> remaining = new HashMap<>();
	private ArrayList<String> failedHosts = new ArrayList<>();

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
	 * @param newValue amount by which to increment the value
	 */
	public void incrementRemainingEntry(String identifier, int amount){
		this.remaining.replace(identifier, this.remaining.get(identifier) + amount);
	}

}
