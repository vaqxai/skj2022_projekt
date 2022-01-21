package io.github.vaqxai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

public class ResourceRequest {

	static ArrayList<ResourceRequest> all = new ArrayList<>();

	/**
	 * 
	 * @return all requests ever made from this node.
	 */
	public static ArrayList<ResourceRequest> getAll(){
		return all;
	}
	
	/**
	 * Global incrementer that ensures each order on this node has an unique ID (IDs outside of a node are used in conjunction with the address of the node that handles a particular request, so there is no possibility for IDs to be non unique)
	 */
	public static int lastOrderID = 1;

	/**
    * An identifier of this particular order, retrieved from the global identifier.
	 */
	public int orderId = 0;

	/**
	 * The name of the client that created (sent) this request
	 */
	public String originator = null;

	/**
	 * A TCP server handler that contains the socket necessary for communication with the client
	 */
	ClientHandler originatorHandler = null;

	/**
	 * Contains the original composition of this request
	 */
	HashMap<String, Integer> order = new HashMap<>(); 

	/**
	 * Contains the parts of the request that are yet to be locked
	 */
	HashMap<String, Integer> remaining = new HashMap<>();

	/**
	 * Contains a list of nodes that do not have any of the requested resources (filled on the first scan)
	 */
	ArrayList<String> failedNodes = new ArrayList<>();

	/**
	 * Key = node address
	 * Value = map of key: locked type, val: locked amount
	 */
	HashMap<String, HashMap<String, Integer>> locked = new HashMap<>();

	/**
	 * Diagnostic information regarding the current status of this request.
	 */
	public RequestStatus status = RequestStatus.RECEIVED; // Initial status is 'received'

	/**
	 * Can be used to contact with the client that made this order (e.g. to tell them it's failed or succeeded)
	 * @return a client handler that can send data to them and holds an open TCP socket
	 */
	public ClientHandler getClientHandler(){
		return this.originatorHandler;
	}

	/**
	 * This is helpful when determining what nodes should not be asked for resources they do not have. Only contains useful info after a first network scan.
	 * @return node addresses of nodes that failed to lock any of the requested resources
	 */
	public ArrayList<String> getFailedNodes(){
		return failedNodes;
	}

	/**
	 * Makes and gives a unique identifier for a particular request.
	 * @return an identifier of this request (client name:order id)
	 */
	public String getIdentifier(){
		return this.originator + ":" + this.orderId;
	}

	/**
	 * Return a map representing various nodes that locked resources for this request.
	 * @return HashMap where the key is a node address and the value is a map where the key is a resource type (name) and the value is the amount locked on a particular node
	 */
	public HashMap<String, HashMap<String, Integer>> getLocked(){
		return this.locked;
	}

	/**
	 * Tells what more resources need to be locked for this request
	 * @return a HashMap where the key is a resource name and the value is the amount.
	 */
	public HashMap<String, Integer> getOrderRemaining(){
		return this.remaining;
	}

	/**
	 * Method that checks if this request has locked all resources that it required.
	 * @return true if all requested resources are locked
	 */
	public boolean isFullyLocked(){
		return this.remaining.values().stream().reduce(0, (a, b) -> a + b) > 0;
	}

	/**
	 * Method that returns the original composition of this request
	 * @return Hash Map where the key is a resource type (name) and the value is the count
	 */
	public HashMap<String, Integer> getOrder(){
		return this.order;
	}

	/**
	 * Logs that a resource has been locked on a node.
	 * @param nodeAddress The address of the node that has this resource locked
	 * @param resourceType The type (name) of the resource that this request locked
	 * @param resourceAmount The amount that this request has locked on that node
	 */
	public void addLockedResource(String nodeAddress, String resourceType, int resourceAmount){
		if(!this.getLocked().containsKey(nodeAddress))
			this.getLocked().put(nodeAddress, new HashMap<String, Integer>());
		
		HashMap<String, Integer> resourceMap = this.getLocked().get(nodeAddress);
		if(!resourceMap.containsKey(resourceType))
			resourceMap.put(resourceType, 0);
		
		resourceMap.replace(resourceType, resourceMap.get(resourceType) + resourceAmount);
	}

	/**
	 * Internal method that sends a <COMMAND> [<RES1>:<RESVAL1> <RES2>:<RESVAL2> ...] command to all nodes that have resources that this request has locked.
	 * @param server The UDP Server that will send the messages
	 * @param command The command to be sent (usually ULK or RES)
	 */
	private void sendToAllLockedNodes(UDPServer server, String command){
		for(Entry<String, HashMap<String, Integer>> resourceMap : this.getLocked().entrySet()){
			String nodeAddress = resourceMap.getKey();
			String resourcesToRelease = "";
			for(Entry<String, Integer> resourceToRelease : resourceMap.getValue().entrySet()){
				resourcesToRelease += resourceToRelease.getKey() + ":" + resourceToRelease.getValue() + " ";
			}
			server.send(command + " " + resourcesToRelease, nodeAddress.split(":")[0], Integer.parseInt(nodeAddress.split(":")[1]));
		}
	}


	/**
	 * Releases all held locks (Unlocks) across all nodes that have them locked.
	 * @param server The UDP Server that will send the messages.
	 */
	public void release(UDPServer server){
		sendToAllLockedNodes(server, "ULK");
	}

	/**
	 * Requests to reserve all held locks acrosss all nodes that have them locked.
	 * @param server The UDP Server that will send the messages.
	 */
	public void finalizeOrder(UDPServer server){
		originatorHandler.send("ALLOCATED");
		for(Entry<String, HashMap<String, Integer>> locked : locked.entrySet()){
			locked.getValue().entrySet().stream().forEach(x -> originatorHandler.send(x.getKey() + ":" + x.getValue() + ":" + locked.getKey()));
		}
		sendToAllLockedNodes(server, "RES " + originator);
	}

	/**
	 * Put in a request with this node for the following resources
	 * @param originator The client that originally placed the order
	 * @param order Identifier, Amount hash map
	 */
	public ResourceRequest(String originator, ClientHandler originatorHandler, HashMap<String, Integer> order){
		this.originator = originator;
		this.order = order;
		this.remaining = new HashMap<String,Integer>(order);
		this.orderId = lastOrderID;
		this.originatorHandler = originatorHandler;
		lastOrderID++;
	}

	/**
	 * Put in a request with this node for the following resource strings
	 * @param originator The client that originally placed the order
	 * @param orderStrings Formatted order string "X:# Y:# Z:#"
	 */
	public ResourceRequest(String originator, ClientHandler originatorHandler, String ... orderStrings){
		this.originator = originator;

		for(String orderString : orderStrings){
			String resourceIdentifier = orderString.split(":")[0];
			int resourceAmount = Integer.parseInt(orderString.split(":")[1]);

			this.order.put(resourceIdentifier, resourceAmount);
		}

		this.remaining = new HashMap<String,Integer>(this.order);
		this.orderId = lastOrderID;
		this.originatorHandler = originatorHandler;
		lastOrderID++;
	}

}
